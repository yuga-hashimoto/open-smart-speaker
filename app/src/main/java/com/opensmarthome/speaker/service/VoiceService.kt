package com.opensmarthome.speaker.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.wakeword.VoskModelDownloader
import com.opensmarthome.speaker.voice.wakeword.VoskWakeWordDetector
import com.opensmarthome.speaker.voice.wakeword.WakeWordConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Always-on voice service managing wake word detection and voice pipeline.
 *
 * Reference: OpenClaw Assistant HotwordService.kt
 * Key pattern: Pause/Resume wake word via broadcast when STT session starts/ends.
 */
@AndroidEntryPoint
class VoiceService : Service() {

    @Inject lateinit var voicePipeline: VoicePipeline
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var batteryMonitor: com.opensmarthome.speaker.util.BatteryMonitor
    @Inject lateinit var thermalMonitor: com.opensmarthome.speaker.util.ThermalMonitor
    @Inject lateinit var multicastDiscovery: com.opensmarthome.speaker.util.MulticastDiscovery
    @Inject lateinit var announcementServer: com.opensmarthome.speaker.multiroom.AnnouncementServer
    @Inject lateinit var peerLivenessTracker: com.opensmarthome.speaker.multiroom.PeerLivenessTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val multiroomController = com.opensmarthome.speaker.multiroom.MultiroomLifecycleController(
        onStart = { startMultiroom() },
        onStop = { stopMultiroom() },
    )
    private var wakeWordDetector: VoskWakeWordDetector? = null
    @Volatile
    private var isSessionActive = false
    private var resumeJob: kotlinx.coroutines.Job? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    /**
     * Broadcast receiver for pause/resume hotword detection.
     * Reference: OpenClaw Assistant controlReceiver pattern.
     */
    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE_HOTWORD -> {
                    Timber.d("Pause signal received — stopping wake word for STT")
                    isSessionActive = true
                    resumeJob?.cancel() // Cancel any pending resume
                    resumeJob = null
                    wakeWordDetector?.stop()
                    wakeWordDetector = null // OpenClaw: destroy + recreate
                    acquireWakeLock()
                }
                ACTION_RESUME_HOTWORD -> {
                    Timber.d("Resume signal received — restarting wake word")
                    isSessionActive = false
                    releaseWakeLock()
                    resumeJob?.cancel()
                    resumeJob = scope.launch {
                        delay(500) // Brief delay to ensure mic is fully released
                        if (!isSessionActive) startWakeWord()
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = pm.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "OpenSmartSpeaker:VoiceSession"
                )
            }
            wakeLock?.takeIf { !it.isHeld }?.acquire(5 * 60 * 1000L)
        } catch (e: Exception) {
            Timber.w(e, "WakeLock acquire failed")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Timber.w(e, "WakeLock release failed")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("VoiceService created")

        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE_HOTWORD)
            addAction(ACTION_RESUME_HOTWORD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }

        // Opt-in multi-room: advertise via mDNS AND start listening on the
        // advertised port for NDJSON envelopes. The server only dispatches
        // a message after HMAC verification (P17.2), so a missing secret
        // means the port accepts connections but drops every envelope —
        // safe default for users who enabled broadcast before setting the
        // shared secret.
        //
        // Observe the preference for the service's lifetime so toggling the
        // Settings switch at runtime starts or tears down the subsystem
        // without requiring a service restart.
        scope.launch {
            preferences.observe(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED)
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    multiroomController.setEnabled(enabled ?: false)
                }
        }
    }

    private suspend fun startMultiroom() {
        multicastDiscovery.register()
        multicastDiscovery.start()
        announcementServer.start()
        // Liveness tracker relies on the broadcaster fan-out + the
        // dispatcher's onHeartbeat callback. Start it after the server
        // is listening so the very first inbound heartbeat has a place
        // to land.
        peerLivenessTracker.start()
    }

    private fun stopMultiroom() {
        // Teardown mirrors startMultiroom in reverse. Each step is
        // best-effort — we do not want a stray ServerSocket close
        // failure to leave the mDNS registration stranded.
        runCatching { peerLivenessTracker.stop() }
        runCatching { announcementServer.stop() }
        runCatching { multicastDiscovery.unregister() }
        runCatching { multicastDiscovery.stop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = VoiceServiceNotification.create(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        when (intent?.action) {
            ACTION_START_LISTENING -> {
                Timber.d("VoiceService: trigger listening")
                // Pause wake word and recreate (OpenClaw destroy+recreate)
                isSessionActive = true
                resumeJob?.cancel()
                resumeJob = null
                wakeWordDetector?.stop()
                wakeWordDetector = null
                acquireWakeLock()
                scope.launch {
                    delay(500) // Wait for mic release
                    voicePipeline.startListening()
                }
            }
            ACTION_STOP_LISTENING -> {
                voicePipeline.stopSpeaking()
            }
            else -> {
                scope.launch { initializeWakeWord() }
            }
        }

        Timber.d("VoiceService started in foreground")
        return START_STICKY
    }

    private suspend fun initializeWakeWord() {
        // Check HOTWORD_ENABLED preference (default true)
        val hotwordEnabled = preferences.observe(PreferenceKeys.HOTWORD_ENABLED).first() ?: true
        if (!hotwordEnabled) {
            Timber.d("Hotword disabled via preference, skipping wake word init")
            return
        }

        try {
            Class.forName("org.vosk.Model")
        } catch (e: ClassNotFoundException) {
            Timber.w("Vosk library not available, wake word disabled.")
            return
        }

        try {
            val downloader = VoskModelDownloader(this)
            if (!downloader.isModelDownloaded()) {
                Timber.d("Vosk model not found, downloading...")
                downloader.downloadModel()
            }

            if (downloader.isModelDownloaded()) {
                startWakeWord()
            } else {
                Timber.w("Vosk model unavailable, wake word disabled")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize wake word")
        }
    }

    private suspend fun startWakeWord() {
        if (isSessionActive) return

        // Re-check HOTWORD_ENABLED at runtime (user may have toggled it off)
        val hotwordEnabled = preferences.observe(PreferenceKeys.HOTWORD_ENABLED).first() ?: true
        if (!hotwordEnabled) {
            Timber.d("Hotword disabled at runtime, not starting detector")
            return
        }

        // Battery + thermal saver: skip wake word when either the battery is
        // low (and the device is unplugged) or the chassis is reporting a
        // thermal throttle. Opt-in policy — defaults off so users who keep
        // the tablet plugged in continuously aren't affected.
        val batterySaverEnabled = preferences.observe(PreferenceKeys.BATTERY_SAVER_ENABLED).first() ?: false
        if (batterySaverEnabled) {
            if (batteryMonitor.status.value.isLow) {
                Timber.d("Battery saver active (level=${batteryMonitor.status.value.level}%), skipping wake word")
                return
            }
            val thermal = thermalMonitor.status.value
            if (thermal.shouldThrottle) {
                Timber.d("Thermal throttle active (level=$thermal), skipping wake word")
                return
            }
        }

        try {
            val downloader = VoskModelDownloader(this)
            if (!downloader.isModelDownloaded()) return

            val savedWakeWord = preferences.observe(PreferenceKeys.WAKE_WORD).first() ?: "hey speaker"
            val savedSensitivity = preferences.observe(PreferenceKeys.WAKE_WORD_SENSITIVITY).first() ?: 0.6f
            val config = WakeWordConfig(keyword = savedWakeWord, sensitivity = savedSensitivity)

            // Always create fresh detector (OpenClaw pattern: destroy + recreate)
            wakeWordDetector?.stop()
            wakeWordDetector = VoskWakeWordDetector(config, downloader.getModelDir())
            wakeWordDetector?.start {
                Timber.d("Wake word detected! Pausing hotword and starting STT...")
                isSessionActive = true
                wakeWordDetector?.stop()
                scope.launch {
                    delay(300) // Wait for mic release
                    voicePipeline.startListening()
                }
            }
            Timber.d("Wake word detection active for: '$savedWakeWord'")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start wake word detection")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(controlReceiver)
        } catch (e: Exception) { /* ignore */ }
        wakeWordDetector?.stop()
        voicePipeline.stopSpeaking()
        voicePipeline.destroy()
        // Safe to call even if we never registered — controller is idempotent
        // and each teardown step is already wrapped in runCatching.
        stopMultiroom()
        super.onDestroy()
        Timber.d("VoiceService destroyed")
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_LISTENING = "com.opensmarthome.speaker.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.opensmarthome.speaker.STOP_LISTENING"
        const val ACTION_PAUSE_HOTWORD = "com.opensmarthome.speaker.PAUSE_HOTWORD"
        const val ACTION_RESUME_HOTWORD = "com.opensmarthome.speaker.RESUME_HOTWORD"

        fun start(context: Context) {
            val intent = Intent(context, VoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }

        fun triggerListening(context: Context) {
            val intent = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            context.startService(intent)
        }

        fun pauseHotword(context: Context) {
            try {
                context.sendBroadcast(Intent(ACTION_PAUSE_HOTWORD).setPackage(context.packageName))
            } catch (e: Exception) {
                Timber.w(e, "pauseHotword broadcast failed")
            }
        }

        fun resumeHotword(context: Context) {
            try {
                context.sendBroadcast(Intent(ACTION_RESUME_HOTWORD).setPackage(context.packageName))
            } catch (e: Exception) {
                Timber.w(e, "resumeHotword broadcast failed")
            }
        }
    }
}
