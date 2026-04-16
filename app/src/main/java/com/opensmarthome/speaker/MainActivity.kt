package com.opensmarthome.speaker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.opensmarthome.speaker.assistant.provider.ProviderManager
import com.opensmarthome.speaker.assistant.provider.embedded.ModelDownloadState
import com.opensmarthome.speaker.assistant.provider.embedded.ModelDownloader
import com.opensmarthome.speaker.service.VoiceService
import com.opensmarthome.speaker.ui.common.ModeScaffold
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

    private var voiceServiceStarted = false
    private var providerInitialized = false
    private lateinit var modelDownloader: ModelDownloader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            startVoiceService()
        } else {
            Timber.w("RECORD_AUDIO permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setupImmersiveMode()

        modelDownloader = ModelDownloader(this)

        setContent {
            OpenSmartSpeakerTheme {
                val downloadState by modelDownloader.state.collectAsState()

                when (downloadState) {
                    is ModelDownloadState.Ready -> {
                        if (!providerInitialized) {
                            providerInitialized = true
                            providerManager.initialize()
                        }
                        ModeScaffold()
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
                // Fetch available models from HuggingFace API
                modelDownloader.fetchAvailableModels()
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
        super.onDestroy()
    }
}
