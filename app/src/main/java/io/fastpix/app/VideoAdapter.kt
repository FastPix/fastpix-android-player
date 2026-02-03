package io.fastpix.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.fastpix.app.databinding.VideoRowBinding

class VideoAdapter() :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
    private var videos: List<DummyData>? = null
    var onVideoClick: ((DummyData?) -> Unit)? = null

    inner class VideoViewHolder(val binding: VideoRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindDataToUI(model: DummyData?) {
            binding.tvTitle.text = model?.id
            binding.tvPlaybackId.text = model?.url

            binding.root.setOnClickListener {
                onVideoClick?.invoke(model)
            }
        }
    }

    fun passDataToAdapter(videos: List<DummyData>) {
        this.videos = videos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = VideoRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos?.get(position)
        holder.bindDataToUI(video)
    }

    override fun getItemCount(): Int = videos?.size ?: 0
}