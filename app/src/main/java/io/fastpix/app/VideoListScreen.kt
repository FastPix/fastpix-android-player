package io.fastpix.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import io.fastpix.app.databinding.ActivityVideoListScreenBinding

class VideoListScreen : AppCompatActivity() {
    private lateinit var binding: ActivityVideoListScreenBinding
    private val videoAdapter by lazy {
        VideoAdapter()
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        videoAdapter.passDataToAdapter(dummyData)
        binding.recyclerView.adapter = videoAdapter

        videoAdapter.onVideoClick = { video ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(VIDEO_MODEL, video)
            intent.putExtra(AUTO_PLAY, binding.sAutoPlay.isChecked)
            intent.putExtra(LOOP, binding.sLoop.isChecked)
            startActivity(intent)
        }
    }


    companion object {
        const val VIDEO_MODEL = "video_model"
        const val AUTO_PLAY = "auto_play"
        const val LOOP = "loop"
    }
}