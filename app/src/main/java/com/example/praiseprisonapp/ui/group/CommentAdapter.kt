package com.example.praiseprisonapp.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.CommentData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CommentAdapter(
    private val commentList: List<CommentData>,
    private val diaryAuthorId: String, // 일기 작성자 ID 추가
    private val onDeleteComment: (CommentData) -> Unit // 삭제 콜백 추가
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val db = FirebaseFirestore.getInstance()

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        
        // 내가 쓴 댓글인지 확인
        if (comment.authorId == currentUserId) {
            // 내가 쓴 댓글은 항상 "나"로 표시
            holder.tvAuthor.text = "나"
            // 삭제 버튼 표시
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                onDeleteComment(comment)
            }
        } else {
            // 다른 사람이 쓴 댓글인 경우
            if (comment.authorId == diaryAuthorId) {
                // 다른 사람이 자신의 일기에 쓴 댓글
                holder.tvAuthor.text = "글쓴이"
            } else {
                // 다른 사람이 다른 사람의 일기에 쓴 댓글
                db.collection("users").document(comment.authorId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val nickname = document.getString("nickname") ?: "익명"
                            holder.tvAuthor.text = nickname
                        } else {
                            holder.tvAuthor.text = "익명"
                        }
                    }
                    .addOnFailureListener {
                        holder.tvAuthor.text = "익명"
                    }
            }
            // 삭제 버튼 숨김
            holder.btnDelete.visibility = View.GONE
        }
        
        holder.tvContent.text = comment.content
    }

    override fun getItemCount() = commentList.size
} 