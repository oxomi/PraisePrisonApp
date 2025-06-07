package com.example.praiseprisonapp.ui.group

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.DiaryData
import com.example.praiseprisonapp.data.model.GroupData
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GroupDetailFragment : Fragment(R.layout.group_detail) {
    private lateinit var groupData: GroupData
    private lateinit var adapter: DiaryAdapter
    private val diaryList = mutableListOf<DiaryData>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupData = it.getParcelable("group_data", GroupData::class.java)
                ?: throw IllegalArgumentException("Group data is required")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 툴바 설정
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = groupData.name
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }

        // RecyclerView 설정
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDiaries)
        adapter = DiaryAdapter(diaryList)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GroupDetailFragment.adapter
        }

        // FAB 클릭 리스너
        view.findViewById<FloatingActionButton>(R.id.fabAddDiary).setOnClickListener {
            val diaryWriteFragment = DiaryWriteFragment.newInstance(groupData.id)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, diaryWriteFragment)
                .addToBackStack(null)
                .commit()
        }

        // 일기 데이터 로드
        loadDiaries()
    }

    private fun loadDiaries() {
        db.collection("diaries")
            .whereEqualTo("groupId", groupData.id)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // 에러 처리
                    return@addSnapshotListener
                }

                snapshot?.let { documents ->
                    diaryList.clear()
                    for (document in documents) {
                        val diary = document.toObject(DiaryData::class.java).copy(id = document.id)
                        diaryList.add(diary)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    companion object {
        fun newInstance(groupData: GroupData) = GroupDetailFragment().apply {
            arguments = Bundle().apply {
                putParcelable("group_data", groupData)
            }
        }
    }
} 