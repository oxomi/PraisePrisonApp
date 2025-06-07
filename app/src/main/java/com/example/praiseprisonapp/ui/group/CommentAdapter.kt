package com.example.praiseprisonapp.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.CommentData

class CommentAdapter(private val commentList: List<CommentData>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        holder.tvAuthor.text = comment.authorName
        holder.tvContent.text = comment.content
    }

    override fun getItemCount() = commentList.size
} 