package com.example.praiseprisonapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.databinding.MydiaryItemBinding
import com.example.praiseprisonapp.model.Diary
import java.text.SimpleDateFormat
import java.util.Locale

class MyDiaryListAdapter : ListAdapter<Diary, MyDiaryListAdapter.DiaryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = MydiaryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DiaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiaryViewHolder(
        private val binding: MydiaryItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)

        fun bind(diary: Diary) {
            binding.apply {
                dateText.text = dateFormat.format(diary.createdAt.toDate())
                groupChip.text = diary.groupId
                moodChip.text = diary.mood
                contentText.text = diary.content

                diary.imageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        Glide.with(diaryImage)
                            .load(url)
                            .centerCrop()
                            .into(diaryImage)
                    }
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Diary>() {
        override fun areItemsTheSame(oldItem: Diary, newItem: Diary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Diary, newItem: Diary): Boolean {
            return oldItem == newItem
        }
    }
} 