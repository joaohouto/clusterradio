package com.joaohouto.clusterradio

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.joaohouto.clusterradio.ui.radio.RadioScreen
import com.joaohouto.clusterradio.ui.radio.RadioViewModel
import com.joaohouto.clusterradio.ui.theme.ClusterRadioTheme
import com.joaohouto.clusterradio.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterradio.ui.theme.getAccentThemeById

class MainActivity : ComponentActivity() {

    private val radioViewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setBackgroundDrawableResource(R.color.deep_metallic_background)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        if (radioViewModel.playbackState.value.isPlaying) {
            com.joaohouto.clusterradio.service.RadioPlaybackService.start(this)
        }

        setContent {
            val settings by radioViewModel.settings.collectAsState()
            val currentAccent = remember(settings.accentThemeId) {
                getAccentThemeById(settings.accentThemeId)
            }

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            BackHandler {
                moveTaskToBack(true)
            }

            ClusterRadioTheme(accentTheme = currentAccent) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepMetallicBackground)
                        .safeDrawingPadding(),
                    color = DeepMetallicBackground
                ) {
                    RadioScreen(viewModel = radioViewModel)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    radioViewModel.stepFrequency(stepUp = true)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    radioViewModel.stepFrequency(stepUp = false)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    radioViewModel.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    radioViewModel.adjustVolume(raise = true)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    radioViewModel.adjustVolume(raise = false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}