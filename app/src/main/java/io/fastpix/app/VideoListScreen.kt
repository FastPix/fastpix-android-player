package io.fastpix.app

import android.content.Intent
import android.os.Bundle
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

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        videoAdapter.passDataToAdapter(dummyData)
        binding.recyclerView.adapter = videoAdapter

        videoAdapter.onVideoClick = { video ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("video_model", video)
            startActivity(intent)
        }
    }


    companion object {
        val viewerId = UUID.randomUUID().toString()
    }
}