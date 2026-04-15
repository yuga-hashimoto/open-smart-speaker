package com.opensmarthome.speaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import com.opensmarthome.speaker.ui.common.ModeScaffold
import com.opensmarthome.speaker.ui.theme.OpenSmartSpeakerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            OpenSmartSpeakerTheme {
                ModeScaffold()
            }
        }
    }
}
