package com.example.praiseprisonapp.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
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
        val test1: TextView = itemView.findViewById(R.id.test1)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val moodChip: Chip = itemView.findViewById(R.id.moodChip)
        val tvContent: TextView = itemView.findViewById(R.id.contentText)
        val ivDiaryImage: ImageView = itemView.findViewById(R.id.diaryImage)
        val btnReaction: ImageButton = itemView.findViewById(R.id.btnReaction)
        val tvReactionCount: TextView = itemView.findViewById(R.id.tvReactionCount)
        val tvCommentsCount: TextView = itemView.findViewById(R.id.tvCommentsCount)
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
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        
        // 작성자의 닉네임 가져오기
        db.collection("users").document(diary.authorId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: diary.authorName
                    holder.test1.text = nickname
                    holder.dateText.text = dateFormat.format(diary.createdAt.toDate())
                }
            }
        
        // 감정
        holder.moodChip.text = diary.mood
        
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
        
        // 리액션 상태 및 카운트 설정
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 현재 사용자의 리액션 상태 확인
            db.collection("diaries").document(diary.id)
                .collection("reactions")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    val hasUserReacted = document.exists()
                    holder.btnReaction.setImageResource(
                        if (hasUserReacted) R.drawable.ic_reaction_on
                        else R.drawable.ic_reaction_off
                    )
                }

            // 전체 리액션 수 표시
            db.collection("diaries").document(diary.id)
                .collection("reactions")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val reactionCount = snapshot?.documents?.size ?: 0
                    holder.tvReactionCount.text = reactionCount.toString()
                }

            // 리액션 버튼 클릭 처리
            holder.btnReaction.setOnClickListener {
                val diaryRef = db.collection("diaries").document(diary.id)
                val userReactionRef = diaryRef.collection("reactions").document(currentUser.uid)

                userReactionRef.get().addOnSuccessListener { document ->
                    if (document.exists()) {
                        // 리액션 제거
                        userReactionRef.delete()
                            .addOnSuccessListener {
                                holder.btnReaction.setImageResource(R.drawable.ic_reaction_off)
                            }
                    } else {
                        // 리액션 추가
                        userReactionRef.set(hashMapOf(
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "userId" to currentUser.uid
                        ))
                            .addOnSuccessListener {
                                holder.btnReaction.setImageResource(R.drawable.ic_reaction_on)
                            }
                    }
                }
            }
        }
        
        // 댓글 수 표시
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