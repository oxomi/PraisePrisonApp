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
        // 닉네임과 날짜 설정
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

        // 현재 사용자의 닉네임 가져오기
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val nickname = document.getString("nickname") ?: currentUser.displayName
                        view.findViewById<TextView>(R.id.tvNickname).text = nickname ?: ""
                        view.findViewById<TextView>(R.id.tvDate).text = dateFormat.format(diaryData.createdAt.toDate())
                    }
                }
        }

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

        // 리액션
        val existingReactions = view.findViewById<ChipGroup>(R.id.existingReactions)
        diaryData.reactions.forEach { (reaction, count) ->
            val chip = Chip(requireContext()).apply {
                text = "$reaction $count"
                isCheckable = false
            }
            existingReactions.addView(chip)
        }

        // 리액션 추가 버튼
        view.findViewById<ImageButton>(R.id.btnAddReaction).setOnClickListener {
            // TODO: 이모지 선택기 표시
        }
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
                        .addOnSuccessListener {
                            etComment.text.clear()
                            // 댓글 카운트 증가
                            db.collection("diaries").document(diaryData.id)
                                .update("commentCount", diaryData.commentCount + 1)
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

                    // 댓글 개수 업데이트
                    view.findViewById<TextView>(R.id.tvCommentsLabel)?.text =
                        "댓글 (${commentList.size})"
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
