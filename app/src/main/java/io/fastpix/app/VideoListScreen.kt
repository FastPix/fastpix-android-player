package io.fastpix.app

import io.fastpix.app.feed.ViewPagerFeedActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import io.fastpix.app.databinding.ActivityVideoListScreenBinding
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixPreCacher
import io.fastpix.media3.cache.PreCacheConfig
import io.fastpix.media3.cache.PreCacheListener
import java.util.UUID
import kotlin.jvm.java

class VideoListScreen : AppCompatActivity() {
    private lateinit var binding: ActivityVideoListScreenBinding
    private val videoAdapter by lazy {
        VideoAdapter()
    }

    private var selectedDefaultAudio: String = AUDIO_DEFAULT_NONE
    private var selectedDefaultSubtitle: String = SUBTITLE_DEFAULT_OFF
    private val token = "playback_token"

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDefaultLanguageDropdowns()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        videoAdapter.passDataToAdapter(dummyData)
        binding.recyclerView.adapter = videoAdapter

        // Warm the start of every video so TestActivity can begin playback from disk.
        if (PRECACHE_ENABLED) {
            TestPreCacher.preCache(this, dummyData.map { it.url })
           // No targetBitrateBps: warm the rendition the player will start on for this network.
           val preCacher =  FastPixPreCacher.create(this, CacheConfig.enabled(),
               PreCacheConfig(enableLogging = true)
           )
            preCacher?.preCache(dummyData.map { it.url })

            preCacher?.setListener(object : PreCacheListener {
                override fun onPreCacheFailed(url: String, error: Throwable) {
                    super.onPreCacheFailed(url, error)
                    Log.e("TAG", "onPreCacheFailed: $url $error", )
                }

                override fun onPreCached(url: String, bytesWritten: Long) {
                    super.onPreCached(url, bytesWritten)
                    Log.e("TAG", "onPreCached: $url ", )
                }
            })
        }

        videoAdapter.onVideoClick = { video ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(VIDEO_MODEL, video)
            intent.putExtra(AUTO_PLAY, binding.sAutoPlay.isChecked)
            intent.putExtra(LOOP, binding.sLoop.isChecked)
            intent.putExtra(DEFAULT_AUDIO_NAME, selectedDefaultAudio)
            intent.putExtra(DEFAULT_SUBTITLE_NAME, selectedDefaultSubtitle)
            if (video?.id?.contains("DRM") == true) {
                intent.putExtra(TOKEN, token)
            }
            if (binding.sPlaylist.isChecked) {
                // Play the whole list as one playlist, starting at the tapped row.
                intent.putStringArrayListExtra(PLAYLIST_URLS, ArrayList(dummyData.map { it.url }))
                intent.putExtra(PLAYLIST_START_INDEX, dummyData.indexOf(video).coerceAtLeast(0))
            }
            startActivity(intent)
        }

        binding.btnReelFeed.setOnClickListener {
            startActivity(ReelFeedActivity.newIntent(this, turboEnabled = true))
        }

        binding.btnEpisodeFeed.setOnClickListener {
            startActivity(EpisodeFeedActivity.newIntent(this, preloadEnabled = true))
        }

        binding.btnViewpagerFeed.setOnClickListener {
            startActivity(ViewPagerFeedActivity.newIntent(this))
        }

        binding.btnComposePlayer.setOnClickListener {
            val firstVideo = dummyData[1]
            val intent = Intent(this, ComposePlayerActivity::class.java)
            intent.putExtra(VIDEO_MODEL, firstVideo)
            intent.putExtra(AUTO_PLAY, binding.sAutoPlay.isChecked)
            intent.putExtra(LOOP, binding.sLoop.isChecked)
            intent.putExtra(DEFAULT_AUDIO_NAME, selectedDefaultAudio)
            intent.putExtra(DEFAULT_SUBTITLE_NAME, selectedDefaultSubtitle)
            startActivity(intent)
        }
    }

    private fun setupDefaultLanguageDropdowns() {
        val audioOptions = listOf(
            AUDIO_DEFAULT_NONE,
            "English",
            "Russian",
            "French",
            "Hindi",
            "German"
        )
        val subtitleOptions = listOf(
            SUBTITLE_DEFAULT_OFF,
            "French",
            "English",
            "Hindi",
            "German"
        )

        binding.spDefaultAudio.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            audioOptions
        )
        binding.spDefaultSubtitle.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            subtitleOptions
        )

        // Defaults
        binding.spDefaultAudio.setSelection(0, false)
        binding.spDefaultSubtitle.setSelection(0, false)

        binding.spDefaultAudio.setOnItemSelectedListener(SimpleItemSelectedListener { value ->
            selectedDefaultAudio = value
        })
        binding.spDefaultSubtitle.setOnItemSelectedListener(SimpleItemSelectedListener { value ->
            selectedDefaultSubtitle = value
        })
    }

    companion object {
        const val VIDEO_MODEL = "video_model"
        const val AUTO_PLAY = "auto_play"
        const val LOOP = "loop"
        const val DEFAULT_AUDIO_NAME = "default_audio_name"
        const val DEFAULT_SUBTITLE_NAME = "default_subtitle_name"
        const val TOKEN = "token"
        const val PLAYLIST_URLS = "playlist_urls"
        const val PLAYLIST_START_INDEX = "playlist_start_index"

        /** Flip to false (and clear app cache) to measure cold, network-only starts in TestActivity. */
        private const val PRECACHE_ENABLED = true

        private const val AUDIO_DEFAULT_NONE = "Auto"
        private const val SUBTITLE_DEFAULT_OFF = "Off"
    }
}