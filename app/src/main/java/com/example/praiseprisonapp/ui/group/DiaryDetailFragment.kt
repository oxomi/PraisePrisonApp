package com.example.praiseprisonapp.ui.group

import android.os.Bundle
import android.view.View
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class DiaryDetailFragment : Fragment(R.layout.diary_detail) {
    private lateinit var diaryData: DiaryData
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<CommentData>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvCommentsCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            diaryData = it.getParcelable("diary_data", DiaryData::class.java)
                ?: throw IllegalArgumentException("Diary data is required")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 툴바 설정
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }

        // 일기 내용 표시
        setupDiaryContent(view)

        // 댓글 목록 설정
        setupComments(view)

        // 댓글 작성 설정
        setupCommentInput(view)

        // 댓글 데이터 로드
        loadComments(view)
    }

    private fun setupDiaryContent(view: View) {
        // 작성자 닉네임 설정
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (diaryData.authorId == currentUserId) {
            view.findViewById<TextView>(R.id.tvNickname).text = "나"
        } else {
            // 작성자의 닉네임 가져오기
            db.collection("users").document(diaryData.authorId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val nickname = document.getString("nickname") ?: diaryData.authorName
                        view.findViewById<TextView>(R.id.tvNickname).text = nickname
                    }
                }
        }

        // 닉네임과 날짜 설정
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvDate).text = dateFormat.format(diaryData.createdAt.toDate())

        // 날씨 아이콘 설정
        val weatherIcon = view.findViewById<ImageView>(R.id.weatherIcon)
        val iconResId = when (diaryData.weatherType) {
            1 -> R.drawable.ic_weather_sunny
            2 -> R.drawable.ic_weather_cloudy
            3 -> R.drawable.ic_weather_rain
            4 -> R.drawable.ic_weather_snow
            5 -> R.drawable.ic_weather_thunderstorm
            else -> R.drawable.ic_weather_sunny
        }
        weatherIcon.setImageResource(iconResId)
        weatherIcon.visibility = View.VISIBLE

        // 감정 칩 설정
        view.findViewById<Chip>(R.id.moodChip).text = diaryData.mood

        // 내용
        view.findViewById<TextView>(R.id.tvContent).text = diaryData.content

        // 이미지
        val ivDiaryImage = view.findViewById<ImageView>(R.id.ivDiaryImage)
        if (diaryData.imageUrl.isNotEmpty()) {
            ivDiaryImage.visibility = View.VISIBLE
            Glide.with(ivDiaryImage)
                .load(diaryData.imageUrl)
                .into(ivDiaryImage)
        }

        // 리액션 상태 및 카운트 설정
        val btnReaction = view.findViewById<ImageButton>(R.id.btnReaction)
        val tvReactionCount = view.findViewById<TextView>(R.id.tvReactionCount)

        // 현재 사용자의 리액션 상태 확인
        auth.currentUser?.let { user ->
            db.collection("diaries").document(diaryData.id)
                .collection("reactions")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    val hasUserReacted = document.exists()
                    btnReaction.setImageResource(
                        if (hasUserReacted) R.drawable.ic_reaction_on
                        else R.drawable.ic_reaction_off
                    )
                }
        }

        // 전체 리액션 수 표시
        db.collection("diaries").document(diaryData.id)
            .collection("reactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val reactionCount = snapshot?.documents?.size ?: 0
                tvReactionCount.text = reactionCount.toString()
            }

        // 리액션 버튼 클릭 처리
        btnReaction.setOnClickListener {
            auth.currentUser?.let { user ->
                val diaryRef = db.collection("diaries").document(diaryData.id)
                val userReactionRef = diaryRef.collection("reactions").document(user.uid)

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
                            "userId" to user.uid
                        ))
                            .addOnSuccessListener {
                                btnReaction.setImageResource(R.drawable.ic_reaction_on)
                            }
                    }
                }
            }
        }

        // 댓글 수 표시
        tvCommentsCount = view.findViewById(R.id.tvCommentsCount)
        tvCommentsCount.text = "댓글 ${diaryData.commentCount}개"

    }

    private fun setupComments(view: View) {
        val rvComments = view.findViewById<RecyclerView>(R.id.rvComments)
        commentAdapter = CommentAdapter(commentList)
        rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
        }
    }

    private fun setupCommentInput(view: View) {
        val etComment = view.findViewById<EditText>(R.id.etComment)
        view.findViewById<ImageButton>(R.id.btnSendComment).setOnClickListener {
            val content = etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val comment = hashMapOf(
                        "diaryId" to diaryData.id,
                        "authorId" to currentUser.uid,
                        "authorName" to currentUser.displayName,
                        "content" to content,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("comments")
                        .add(comment)
                        .addOnSuccessListener { documentRef ->
                            etComment.text.clear()

                            val currentUserId = currentUser.uid

//
                            // Firestore에서 닉네임 가져오기
                            db.collection("users").document(currentUserId)
                                .get()
                                .addOnSuccessListener { userDoc ->
                                    val nickname = userDoc.getString("nickname") ?: currentUser.displayName ?: "익명"

                                    val newComment = CommentData(
                                        id = documentRef.id,
                                        diaryId = diaryData.id,
                                        authorId = currentUserId,
                                        authorName = nickname,
                                        content = content,
                                        createdAt = com.google.firebase.Timestamp.now()
                                    )

                                    commentList.add(newComment)
                                    commentAdapter.notifyItemInserted(commentList.size - 1)
                                    view.findViewById<RecyclerView>(R.id.rvComments)
                                        .smoothScrollToPosition(commentList.size - 1)

                                    db.collection("diaries").document(diaryData.id)
                                        .update("commentCount", FieldValue.increment(1))
                                    // UI 상 댓글 개수도 갱신
                                    val currentCount = tvCommentsCount.text.toString()
                                        .replace("댓글", "")
                                        .replace("개", "")
                                        .trim()
                                        .toIntOrNull() ?: 0

                                    tvCommentsCount.text = "댓글 ${currentCount + 1}개"
                                }
                        }
                }
            }
        }
    }

    private fun loadComments(view: View) {
        db.collection("comments")
            .whereEqualTo("diaryId", diaryData.id)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // 에러 처리
                    return@addSnapshotListener
                }

                snapshot?.let { documents ->
                    commentList.clear()
                    for (document in documents) {
                        val comment = document.toObject(CommentData::class.java)

                        // 댓글 작성자의 닉네임을 가져오기
                        val authorId = document.getString("authorId") ?: ""
                        if (authorId.isNotEmpty()) {
                            db.collection("users").document(authorId)
                                .get()
                                .addOnSuccessListener { userDocument ->
                                    val authorName = userDocument.getString("nickname") ?: "알 수 없음"
                                    commentList.add(comment.copy(
                                        id = document.id,
                                        authorName = authorName
                                    ))
                                    commentAdapter.notifyDataSetChanged()
                                }
                        } else {
                            commentList.add(comment)
                        }
                    }
                    commentAdapter.notifyDataSetChanged()
                }
            }
    }

    companion object {
        fun newInstance(diaryData: DiaryData) = DiaryDetailFragment().apply {
            arguments = Bundle().apply {
                putParcelable("diary_data", diaryData)
            }
        }
    }
}
