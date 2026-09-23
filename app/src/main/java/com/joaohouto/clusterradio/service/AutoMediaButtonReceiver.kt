package com.joaohouto.clusterradio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.joaohouto.clusterradio.engine.CompositeRadioManager

class AutoMediaButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "RadioMediaButton"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Received steering wheel media button: keyCode=${keyEvent.keyCode}")
                val radioManager = CompositeRadioManager.getInstance(context)

                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> radioManager.play()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> radioManager.pause()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> radioManager.togglePlayPause()
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_CHANNEL_UP -> radioManager.step(stepUp = true)
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> radioManager.step(stepUp = false)
                    KeyEvent.KEYCODE_VOLUME_UP -> radioManager.adjustSystemVolume(raise = true)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> radioManager.adjustSystemVolume(raise = false)
                }
            }
        }
    }
}
