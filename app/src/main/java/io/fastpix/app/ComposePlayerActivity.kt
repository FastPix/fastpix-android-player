package io.fastpix.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi

/**
 * Activity that hosts the Compose-based FastPix player screen.
 * Launched from [VideoListScreen] with optional video data and options.
 */
@UnstableApi
class ComposePlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val videoModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(VideoListScreen.VIDEO_MODEL, DummyData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(VideoListScreen.VIDEO_MODEL) as? DummyData
        }
        val autoplay = intent.getBooleanExtra(VideoListScreen.AUTO_PLAY, false)
        val loop = intent.getBooleanExtra(VideoListScreen.LOOP, false)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ComposePlayerScreen(
                        videoModel = videoModel,
                        autoplay = autoplay,
                        loop = loop,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
