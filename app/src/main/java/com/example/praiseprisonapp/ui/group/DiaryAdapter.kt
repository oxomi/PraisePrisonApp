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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class DiaryAdapter(private val diaries: List<DiaryData>) :
    RecyclerView.Adapter<DiaryAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val diaryCard: MaterialCardView = itemView.findViewById(R.id.diaryCard)
        val test1: TextView = itemView.findViewById(R.id.test1)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val weatherIcon: ImageView = itemView.findViewById(R.id.weatherIcon)
        val moodChip: Chip = itemView.findViewById(R.id.moodChip)
        val tvContent: TextView = itemView.findViewById(R.id.contentText)
        val ivDiaryImage: ImageView = itemView.findViewById(R.id.diaryImage)
        val btnReaction: ImageButton = itemView.findViewById(R.id.btnReaction)
        val tvReactionCount: TextView = itemView.findViewById(R.id.tvReactionCount)
        val tvCommentsCount: TextView = itemView.findViewById(R.id.tvCommentsCount)

        fun bind(diary: DiaryData) {
            // 내 다이어리인지 확인하고 배경 설정
            val isMyDiary = diary.authorId == currentUserId
            diaryCard.setCardBackgroundColor(
                itemView.context.getColor(
                    if (isMyDiary) R.color.my_diary_background
                    else R.color.other_diary_background
                )
            )

            // 제목 (작성자 + 날짜)
            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

            // 작성자의 닉네임 가져오기
            if (diary.authorId == currentUserId) {
                test1.text = "나"
                dateText.text = dateFormat.format(diary.createdAt.toDate())
            } else {
                db.collection("users").document(diary.authorId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val nickname = document.getString("nickname") ?: diary.authorName
                            test1.text = nickname
                            dateText.text = dateFormat.format(diary.createdAt.toDate())
                        }
                    }
            }

            // 날씨 아이콘 설정
            val iconResId = when (diary.weatherType) {
                1 -> R.drawable.ic_weather_sunny
                2 -> R.drawable.ic_weather_cloudy
                3 -> R.drawable.ic_weather_rain
                4 -> R.drawable.ic_weather_snow
                5 -> R.drawable.ic_weather_thunderstorm
                else -> R.drawable.ic_weather_sunny
            }
            weatherIcon.setImageResource(iconResId)
            weatherIcon.visibility = View.VISIBLE

            // 감정
            moodChip.text = diary.mood

            // 내용
            tvContent.text = diary.content

            // 이미지
            if (diary.imageUrl.isNotEmpty()) {
                ivDiaryImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(diary.imageUrl)
                    .into(ivDiaryImage)
            } else {
                ivDiaryImage.visibility = View.GONE
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
                        btnReaction.setImageResource(
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
                        tvReactionCount.text = reactionCount.toString()
                    }

                // 리액션 버튼 클릭 처리
                btnReaction.setOnClickListener {
                    val diaryRef = db.collection("diaries").document(diary.id)
                    val userReactionRef = diaryRef.collection("reactions").document(currentUser.uid)

                    userReactionRef.get().addOnSuccessListener { document ->
                        if (document.exists()) {
                            // 리액션 제거
                            userReactionRef.delete()
                                .addOnSuccessListener {
                                    btnReaction.setImageResource(R.drawable.ic_reaction_off)
                                }
                        } else {
                            // 리액션 추가
                            userReactionRef.set(hashMapOf(
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "userId" to currentUser.uid
                            ))
                                .addOnSuccessListener {
                                    btnReaction.setImageResource(R.drawable.ic_reaction_on)
                                }
                        }
                    }
                }
            }

            // 댓글 수 표시
            tvCommentsCount.text = "댓글 ${diary.commentCount}개"

            // 클릭 리스너
            itemView.setOnClickListener {
                val fragment = DiaryDetailFragment.newInstance(diary)
                val fragmentManager = (itemView.context as androidx.fragment.app.FragmentActivity).supportFragmentManager
                fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_diary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val diary = diaries[position]
        holder.bind(diary)
    }

    override fun getItemCount() = diaries.size
}