package com.example.praiseprisonapp.ui.group

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.DiaryData
import com.example.praiseprisonapp.data.model.GroupData
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue

class GroupDetailFragment : Fragment(R.layout.group_detail) {
    private lateinit var groupData: GroupData
    private lateinit var adapter: DiaryAdapter
    private val diaryList = mutableListOf<DiaryData>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            groupData = it.getParcelable("group_data") as? GroupData
                ?: throw IllegalArgumentException("Group data is required")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 현재 사용자가 그룹의 멤버인지 확인
        val currentUser = auth.currentUser
        if (currentUser != null && !groupData.members.contains(currentUser.uid)) {
            showJoinGroupDialog()
        }

        setupUI(view)
        loadDiaries()
    }

    private fun setupUI(view: View) {
        // 툴바 설정
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = groupData.name
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }

        // 수정 버튼은 그룹 생성자만 볼 수 있도록 설정
        val editButton = view.findViewById<ImageButton>(R.id.btnEdit)
        editButton.apply {
            visibility = if (auth.currentUser?.uid == groupData.createdBy) View.VISIBLE else View.GONE
            setOnClickListener {
                val editFragment = GroupEditFragment().apply {
                    arguments = Bundle().apply {
                        putString("groupId", groupData.id)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, editFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        // RecyclerView 설정
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDiaries)
        adapter = DiaryAdapter(diaryList)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GroupDetailFragment.adapter
        }

        // FAB은 그룹 멤버만 볼 수 있도록 설정
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddDiary)
        fab.apply {
            visibility = if (groupData.isMyGroup) View.VISIBLE else View.GONE
            setOnClickListener {
                val diaryWriteFragment = DiaryWriteFragment.newInstance(groupData.id)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, diaryWriteFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun showJoinGroupDialog() {
        if (groupData.visibility == "전체공개") {
            // 전체 공개 그룹 → 일반 가입 다이얼로그
            AlertDialog.Builder(requireContext())
                .setTitle("전체공개 그룹 가입")
                .setMessage("이 그룹에 가입하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    joinGroup()
                }
                .setNegativeButton("아니오") { _, _ ->
                    parentFragmentManager.popBackStack()
                }
                .setCancelable(false)
                .show()

        } else if (groupData.visibility == "일부공개") {
            // 일부 공개 그룹 → 비밀번호 입력
            val input = android.widget.EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "비밀번호 입력"
            }

            AlertDialog.Builder(requireContext())
                .setTitle("일부공개 그룹 가입")
                .setMessage("비밀번호를 입력해주세요.")
                .setView(input)
                .setPositiveButton("확인") { _, _ ->
                    val enteredPassword = input.text.toString()
                    if (enteredPassword == groupData.password) {
                        joinGroup()
                    } else {
                        Toast.makeText(requireContext(), "비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                }
                .setNegativeButton("취소") { _, _ ->
                    parentFragmentManager.popBackStack()
                }
                .setCancelable(false)
                .show()
        } else {
            Toast.makeText(requireContext(), "알 수 없는 그룹 공개 설정입니다.", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun joinGroup() {
        val currentUser = auth.currentUser ?: return
        val groupRef = db.collection("groups").document(groupData.id)
        val userRef = db.collection("users").document(currentUser.uid)

        db.runTransaction { transaction ->
            val userDoc = transaction.get(userRef)
            transaction.update(groupRef, "members", FieldValue.arrayUnion(currentUser.uid))

            if (userDoc.exists()) {
                transaction.update(userRef, "groups", FieldValue.arrayUnion(groupData.id))
            } else {
                transaction.set(userRef, hashMapOf("groups" to listOf(groupData.id)))
            }
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "그룹에 가입되었습니다.", Toast.LENGTH_SHORT).show()
            // 그룹 데이터 업데이트
            groupData = groupData.copy(members = groupData.members + currentUser.uid)
            // UI 업데이트
            view?.findViewById<FloatingActionButton>(R.id.fabAddDiary)?.visibility = View.VISIBLE
        }.addOnFailureListener { e ->
            Log.e("GroupDetailFragment", "그룹 가입에 실패했습니다: ${e.message}", e)
            Toast.makeText(requireContext(), "그룹 가입에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }


    private fun loadDiaries() {
        db.collection("diaries")
            .whereEqualTo("groupId", groupData.id)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(context, "일기 목록을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { documents ->
                    diaryList.clear()
                    for (document in documents) {
                        val rawDiary = document.toObject(DiaryData::class.java)
                        val authorName = document.getString("authorName") ?: "알 수 없음"
                        val diary = rawDiary.copy(
                            id = document.id,
                            authorName = authorName
                        )
                        diaryList.add(diary)

                        // 각 일기의 댓글 수를 실시간으로 가져오기
                        db.collection("comments")
                            .whereEqualTo("diaryId", diary.id)
                            .addSnapshotListener { commentsSnapshot, commentsError ->
                                if (commentsError != null) return@addSnapshotListener

                                val commentCount = commentsSnapshot?.documents?.size ?: 0

                                // Firestore에 댓글 수 업데이트
                                db.collection("diaries")
                                    .document(diary.id)
                                    .update("commentCount", commentCount)
                                    .addOnSuccessListener {
                                        // 해당 일기의 댓글 수 업데이트
                                        val position = diaryList.indexOfFirst { it.id == diary.id }
                                        if (position != -1) {
                                            diaryList[position] = diary.copy(commentCount = commentCount)
                                            adapter.notifyItemChanged(position)
                                        }
                                    }
                            }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    companion object {
        fun newInstance(group: GroupData) = GroupDetailFragment().apply {
            arguments = Bundle().apply {
                putParcelable("group_data", group)
            }
        }
    }
}