package io.fastpix.app.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A minimal image loader for the demos' thumbnails, so the sample needs no image library: an
 * in-memory LRU of decoded bitmaps, filled off the main thread. FastPix sizes the images on the
 * server (`?width=`), so nothing is decoded larger than it is shown.
 */
object ThumbnailLoader {

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        bitmap?.let { memory.put(url, it) }
        return bitmap
    }
}

/** An image from [url], cropped to fill; a dark placeholder until it arrives. */
@Composable
fun Thumbnail(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(ThumbnailLoader.cached(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) bitmap = ThumbnailLoader.load(url)
    }
    Box(modifier.background(Color(0xFF1E1E1E))) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
