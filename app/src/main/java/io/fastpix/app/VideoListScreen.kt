package io.fastpix.app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import io.fastpix.app.databinding.ActivityVideoListScreenBinding
import java.util.UUID
import kotlin.jvm.java

class VideoListScreen : AppCompatActivity() {
    private lateinit var binding: ActivityVideoListScreenBinding
    private val videoAdapter by lazy {
        VideoAdapter()
    }

    private var selectedDefaultAudio: String = AUDIO_DEFAULT_NONE
    private var selectedDefaultSubtitle: String = SUBTITLE_DEFAULT_OFF

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDefaultLanguageDropdowns()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        videoAdapter.passDataToAdapter(dummyData)
        binding.recyclerView.adapter = videoAdapter

        videoAdapter.onVideoClick = { video ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(VIDEO_MODEL, video)
            intent.putExtra(AUTO_PLAY, binding.sAutoPlay.isChecked)
            intent.putExtra(LOOP, binding.sLoop.isChecked)
            intent.putExtra(DEFAULT_AUDIO_NAME, selectedDefaultAudio)
            intent.putExtra(DEFAULT_SUBTITLE_NAME, selectedDefaultSubtitle)
            startActivity(intent)
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

        private const val AUDIO_DEFAULT_NONE = "Auto"
        private const val SUBTITLE_DEFAULT_OFF = "Off"
    }
}