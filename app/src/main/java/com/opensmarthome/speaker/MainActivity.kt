package com.opensmarthome.speaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.opensmarthome.speaker.assistant.provider.ProviderManager
import com.opensmarthome.speaker.assistant.provider.embedded.ModelDownloadState
import com.opensmarthome.speaker.assistant.provider.embedded.ModelDownloader
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.service.VoiceService
import com.opensmarthome.speaker.ui.common.ModeScaffold
import com.opensmarthome.speaker.ui.onboarding.OnboardingScreen
import com.opensmarthome.speaker.ui.setup.ModelSetupScreen
import com.opensmarthome.speaker.ui.theme.OpenSmartSpeakerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var providerManager: ProviderManager
    @javax.inject.Inject lateinit var appPreferences: AppPreferences
    @javax.inject.Inject lateinit var cameraProviderHolder: com.opensmarthome.speaker.tool.system.CameraProviderHolder
    @javax.inject.Inject lateinit var screenRecorderHolder: com.opensmarthome.speaker.tool.system.ScreenRecorderHolder
    @javax.inject.Inject lateinit var toolExecutor: com.opensmarthome.speaker.tool.ToolExecutor
    @javax.inject.Inject lateinit var routineShortcutPublisher: com.opensmarthome.speaker.shortcuts.RoutineShortcutPublisher

    private lateinit var intentCameraProvider: com.opensmarthome.speaker.tool.system.IntentCameraProvider
    private lateinit var screenRecorder: com.opensmarthome.speaker.tool.system.MediaProjectionScreenRecorder

    private var voiceServiceStarted = false
    private var providerInitialized = false
    private lateinit var modelDownloader: ModelDownloader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingHotwordStart = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            startVoiceService()
            if (pendingHotwordStart) {
                pendingHotwordStart = false
                // Wake word can now start since permission granted
            }
        } else {
            Timber.w("RECORD_AUDIO permission denied")
            if (pendingHotwordStart) {
                pendingHotwordStart = false
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setupImmersiveMode()

        // ActivityResult launchers must be registered before onStart(), so
        // construct the providers in onCreate().
        intentCameraProvider = com.opensmarthome.speaker.tool.system.IntentCameraProvider(this)
        cameraProviderHolder.setProvider(intentCameraProvider)

        screenRecorder = com.opensmarthome.speaker.tool.system.MediaProjectionScreenRecorder(this)
        screenRecorderHolder.setRecorder(screenRecorder)

        modelDownloader = ModelDownloader(this)

        setContent {
            OpenSmartSpeakerTheme {
                val downloadState by modelDownloader.state.collectAsState()
                val setupCompleted by appPreferences
                    .observe(PreferenceKeys.SETUP_COMPLETED)
                    .collectAsState(initial = null)
                var onboardingDismissed by remember { mutableStateOf(false) }

                when (downloadState) {
                    is ModelDownloadState.Ready -> {
                        if (!providerInitialized) {
                            providerInitialized = true
                            providerManager.initialize()
                        }
                        val needsOnboarding = setupCompleted == false && !onboardingDismissed
                        if (needsOnboarding) {
                            OnboardingScreen(onDone = { onboardingDismissed = true })
                        } else {
                            ModeScaffold()
                        }
                    }
                    else -> {
                        val models by modelDownloader.availableModels.collectAsState()
                        val selected by modelDownloader.selectedModel.collectAsState()

                        ModelSetupScreen(
                            downloadState = modelDownloader.state,
                            selectedModel = selected,
                            availableModels = models,
                            onSelectModel = { modelDownloader.selectModel(it) },
                            onStartDownload = { scope.launch { modelDownloader.downloadSelectedModel() } },
                            onRetry = { scope.launch { modelDownloader.downloadSelectedModel() } }
                        )
                    }
                }
            }
        }

        requestPermissionsAndStart()
        scope.launch {
            if (modelDownloader.isModelDownloaded()) {
                modelDownloader.ensureModelAvailable()
            } else {
                modelDownloader.fetchAvailableModels()
            }
        }

        // Handle voice trigger from VoiceInteractionService
        if (intent?.getBooleanExtra("trigger_voice", false) == true) {
            VoiceService.triggerListening(this)
        }
        intent?.let { handleRoutineShortcut(it) }
        scope.launch { routineShortcutPublisher.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("trigger_voice", false)) {
            VoiceService.triggerListening(this)
        }
        handleRoutineShortcut(intent)
    }

    private fun handleRoutineShortcut(intent: Intent) {
        val id = intent.getStringExtra(
            com.opensmarthome.speaker.shortcuts.RoutineShortcutPublisher.EXTRA_ROUTINE_ID
        )?.takeIf { it.isNotBlank() } ?: return
        // Clear so configuration changes don't re-fire.
        intent.removeExtra(com.opensmarthome.speaker.shortcuts.RoutineShortcutPublisher.EXTRA_ROUTINE_ID)
        Timber.d("Launching routine from shortcut: $id")
        scope.launch {
            try {
                toolExecutor.execute(
                    com.opensmarthome.speaker.tool.ToolCall(
                        id = "shortcut_${java.util.UUID.randomUUID()}",
                        name = "run_routine",
                        arguments = mapOf("id" to id)
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Shortcut routine dispatch failed")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!voiceServiceStarted && hasAudioPermission()) {
            startVoiceService()
        }
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startVoiceService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Check if microphone is blocked by Android 12+ sensor privacy toggle.
     * Reference: OpenClaw Assistant isMicPrivacyBlocked()
     */
    fun isMicPrivacyBlocked(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val spm = getSystemService(SensorPrivacyManager::class.java) ?: return false
        val supportsMicToggle = spm.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
        if (!supportsMicToggle) return false
        val audioManager = getSystemService(AudioManager::class.java)
        return audioManager?.isMicrophoneMute == true
    }

    /**
     * Check if this app is set as the default digital assistant.
     * Reference: OpenClaw Assistant isAssistantActive()
     */
    fun isAssistantActive(): Boolean {
        return try {
            Settings.Secure.getString(contentResolver, "assistant")?.contains(packageName) == true
        } catch (e: Exception) { false }
    }

    /**
     * Open the system's digital assistant settings page.
     * Reference: OpenClaw Assistant openAssistantSettings()
     */
    fun openAssistantSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Could not open assistant settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun showPermissionSettingsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Microphone Permission Required")
            .setMessage("Microphone permission was permanently denied. Please enable it in app settings to use voice features.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVoiceService() {
        if (voiceServiceStarted) return
        voiceServiceStarted = true
        VoiceService.start(this)
        Timber.d("VoiceService started")
    }

    override fun onDestroy() {
        if (isFinishing) {
            VoiceService.stop(this)
        }
        if (::intentCameraProvider.isInitialized) {
            cameraProviderHolder.clear()
        }
        if (::screenRecorder.isInitialized) {
            screenRecorderHolder.clear()
        }
        super.onDestroy()
    }
}
