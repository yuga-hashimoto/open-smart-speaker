package com.opensmarthome.speaker.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opensmarthome.speaker.ui.devices.DevicesScreen
import com.opensmarthome.speaker.ui.home.ConnectionBadge
import com.opensmarthome.speaker.ui.home.ConnectionStatus
import com.opensmarthome.speaker.ui.home.HomeScreen
import com.opensmarthome.speaker.ui.home.NightClockOverlay
import com.opensmarthome.speaker.ui.settings.SettingsScreen
import com.opensmarthome.speaker.ui.theme.SpeakerBackground
import com.opensmarthome.speaker.ui.theme.SpeakerOnPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import com.opensmarthome.speaker.ui.theme.SpeakerTextTertiary
import com.opensmarthome.speaker.ui.theme.VoiceListening
import com.opensmarthome.speaker.ui.voice.VoiceOverlay
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState
import kotlinx.coroutines.delay

@Composable
fun ModeScaffold(
    viewModel: ModeScaffoldViewModel = hiltViewModel()
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val sttText by viewModel.partialText.collectAsState()
    val responseText by viewModel.lastResponse.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    var showSettings by remember { mutableStateOf(false) }
    var showNightClock by remember { mutableStateOf(false) }
    var showControlDrawer by remember { mutableStateOf(false) }
    val showOverlay = voiceState !is VoicePipelineState.Idle &&
            voiceState !is VoicePipelineState.WakeWordListening

    // Auto-return to Home after 30s inactivity on Devices page
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) {
            delay(30_000L)
            pagerState.animateScrollToPage(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SpeakerBackground)) {
        // Main pages: Home ↔ Devices (swipe)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen()
                1 -> DevicesScreen()
            }
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .background(
                            if (pagerState.currentPage == index) SpeakerTextSecondary else SpeakerTextTertiary,
                            CircleShape
                        )
                )
            }
        }

        // Connection badge (top-left) — reflects real connectivity now.
        ConnectionBadge(
            status = if (isOnline) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            providerCount = 0,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .alpha(0.7f)
        )

        // Settings gear icon (top-right)
        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.5f)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Control drawer (swipe down from top)
        ControlDrawer(
            visible = showControlDrawer,
            onNightMode = {
                showControlDrawer = false
                showNightClock = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Mic FAB (bottom center)
        MicFab(
            isListening = voiceState is VoicePipelineState.Listening,
            onClick = { viewModel.startVoiceInput() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Voice Overlay
        if (showOverlay) {
            VoiceOverlay(
                voiceState = voiceState,
                sttText = sttText,
                responseText = responseText
            )
        }

        // Settings overlay
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it }
        ) {
            SettingsScreen(onBack = { showSettings = false })
        }

        // Night clock overlay
        if (showNightClock) {
            NightClockOverlay(onDismiss = { showNightClock = false })
        }
    }
}

@Composable
private fun MicFab(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fabSize by animateDpAsState(if (isListening) 72.dp else 56.dp, label = "fabSize")
    val glowAlpha by animateFloatAsState(if (isListening) 0.3f else 0f, label = "glow")
    val transition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(fabSize + 20.dp)
                    .background(VoiceListening.copy(alpha = glowAlpha), CircleShape)
            )
        }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(fabSize).scale(breatheScale),
            containerColor = SpeakerPrimary,
            shape = CircleShape
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Voice",
                tint = SpeakerOnPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
