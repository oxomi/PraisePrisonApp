package com.example.praiseprisonapp.ui.group

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import com.google.firebase.Timestamp
import com.example.praiseprisonapp.util.SentimentAnalyzer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog

class DiaryDetailFragment : Fragment(R.layout.diary_detail) {
    private lateinit var diaryData: DiaryData
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<CommentData>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvCommentsCount: TextView
    private lateinit var sentimentAnalyzer: SentimentAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            diaryData = it.getParcelable<DiaryData>("diary_data")
                ?: throw IllegalArgumentException("Diary data is required")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sentimentAnalyzer = SentimentAnalyzer.getInstance(requireContext())

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
        val commentInput = view.findViewById<EditText>(R.id.etComment)
        val sendButton = view.findViewById<ImageButton>(R.id.btnSendComment)

        sendButton.setOnClickListener {
            Log.d(TAG, "댓글 전송 버튼 클릭됨")
            val comment = commentInput.text.toString().trim()
            if (comment.isEmpty()) {
                Toast.makeText(context, "댓글을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 코루틴으로 감정 분석 수행
            viewLifecycleOwner.lifecycleScope.launch {
                Log.d(TAG, "댓글 감정 분석 시작")
                val result = withContext(Dispatchers.Default) {
                    sentimentAnalyzer.analyze(comment)
                }
                
                when {
                    // 기쁨 그룹이 아니거나 신뢰도가 낮은 경우 부정적으로 처리
                    result.confidence < 0.1 || result.emotionGroup != SentimentAnalyzer.EmotionGroup.JOY -> {
                        Log.d(TAG, "부정적 감정 또는 낮은 신뢰도 감지됨 (감정: ${result.emotion}, 신뢰도: ${String.format("%.1f", result.confidence * 100)}%) - 확인 다이얼로그 표시")
                        showSentimentConfirmationDialog(comment, commentInput)
                    }
                    else -> {
                        Log.d(TAG, "긍정적 감정 감지됨 - 댓글 저장 진행")
                        saveComment(comment, commentInput)
                    }
                }
            }
        }
    }

    private fun showSentimentConfirmationDialog(
        comment: String,
        commentInput: EditText
    ) {
        if (!isAdded) {
            Log.w(TAG, "Fragment가 아직 추가되지 않음. 다이얼로그 표시 스킵")
            return
        }

        val message = "부정적인 감정이 감지되었어요.\n긍정적인 말로 수정하는건 어떨까요?"
        
        activity?.let { activity ->
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("네") { _, _ ->
                    Log.d(TAG, "사용자가 텍스트 수정하기로 선택")
                    // 댓글 입력창에 그대로 남아있게 됨 (수정 가능)
                }
                .setNegativeButton("아니오") { _, _ ->
                    Log.d(TAG, "사용자가 수정하지 않고 게시하기로 선택")
                    saveComment(comment, commentInput)
                }
                .show()
        } ?: run {
            Log.w(TAG, "Activity가 null임. 다이얼로그 표시 스킵하고 바로 저장")
            saveComment(comment, commentInput)
        }
    }

    private fun saveComment(comment: String, commentInput: EditText) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val commentData = CommentData(
            authorId = currentUser.uid,
            authorName = currentUser.displayName ?: "익명",
            content = comment,
            createdAt = Timestamp.now(),
            diaryId = diaryData.id
        )

        db.collection("diaries").document(diaryData.id)
            .collection("comments")
            .add(commentData)
            .addOnSuccessListener {
                // 댓글 수 업데이트
                db.collection("diaries").document(diaryData.id)
                    .update("commentCount", FieldValue.increment(1))
                    .addOnSuccessListener {
                        // 댓글 저장 성공 시 UI 업데이트
                        commentInput.text.clear()
                        loadComments(requireView())  // 댓글 목록 새로고침
                        // 댓글 수 UI 업데이트
                        val newCount = (diaryData.commentCount ?: 0) + 1
                        tvCommentsCount.text = "댓글 ${newCount}개"
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "댓글 작성에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadComments(view: View) {
        db.collection("diaries").document(diaryData.id)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "댓글 로드 중 오류 발생", e)
                    Toast.makeText(context, "댓글을 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { documents ->
                    commentList.clear()
                    for (document in documents) {
                        try {
                            val comment = document.toObject(CommentData::class.java).copy(id = document.id)
                            commentList.add(comment)
                        } catch (e: Exception) {
                            Log.e(TAG, "댓글 파싱 중 오류 발생", e)
                        }
                    }
                    commentList.sortBy { it.createdAt }
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

        private const val TAG = "DiaryDetailFragment"
    }
}
