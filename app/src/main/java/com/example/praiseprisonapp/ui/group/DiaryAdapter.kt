package com.example.praiseprisonapp.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.CommentData
import com.example.praiseprisonapp.data.model.DiaryData
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class DiaryAdapter(private val diaryList: List<DiaryData>) : 
    RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    class DiaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.dateText)
        val tvContent: TextView = itemView.findViewById(R.id.contentText)
        val ivDiaryImage: ImageView = itemView.findViewById(R.id.diaryImage)
        val existingReactions: ChipGroup = itemView.findViewById(R.id.existingReactions)
        val btnAddReaction: ImageButton = itemView.findViewById(R.id.btnAddReaction)
        val tvCommentsCount: TextView = itemView.findViewById(R.id.tvCommentsCount)
        val rvComments: RecyclerView = itemView.findViewById(R.id.rvComments)
        val etComment: EditText = itemView.findViewById(R.id.etComment)
        val btnSendComment: ImageButton = itemView.findViewById(R.id.btnSendComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_diary, parent, false)
        return DiaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        val diary = diaryList[position]
        val context = holder.itemView.context
        
        // 제목 (작성자 + 날짜)
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
        holder.tvTitle.text = "${diary.authorName}의 ${dateFormat.format(diary.createdAt.toDate())}"
        
        // 내용
        holder.tvContent.text = diary.content
        
        // 이미지
        if (diary.imageUrl.isNotEmpty()) {
            holder.ivDiaryImage.visibility = View.VISIBLE
            Glide.with(holder.ivDiaryImage)
                .load(diary.imageUrl)
                .into(holder.ivDiaryImage)
        } else {
            holder.ivDiaryImage.visibility = View.GONE
        }
        
        // 리액션 표시
        holder.existingReactions.removeAllViews()
        diary.reactions.forEach { (reaction, count) ->
            val chip = Chip(context).apply {
                text = "$reaction $count"
                isCheckable = false
            }
            holder.existingReactions.addView(chip)
        }
        
        // 리액션 추가 버튼
        holder.btnAddReaction.setOnClickListener {
            // TODO: 이모지 선택기 표시
        }
        
        // 댓글 목록 설정
        holder.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CommentAdapter(emptyList()) // TODO: 실제 댓글 데이터 연결
        }
        
        // 댓글 작성
        holder.btnSendComment.setOnClickListener {
            val content = holder.etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val comment = hashMapOf(
                        "diaryId" to diary.id,
                        "authorId" to currentUser.uid,
                        "authorName" to currentUser.displayName,
                        "content" to content,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    
                    db.collection("comments")
                        .add(comment)
                        .addOnSuccessListener {
                            holder.etComment.text.clear()
                            // TODO: 댓글 목록 새로고침
                        }
                }
            }
        }
        
        // 댓글 수
        holder.tvCommentsCount.text = "댓글 ${diary.commentCount}개"
        
        // 클릭 리스너
        holder.itemView.setOnClickListener {
            val fragment = DiaryDetailFragment.newInstance(diary)
            val fragmentManager = (context as androidx.fragment.app.FragmentActivity).supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun getItemCount() = diaryList.size
} 