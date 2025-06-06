package com.example.praiseprisonapp.ui.home_tab.fragment

import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.GroupData
import com.example.praiseprisonapp.data.repository.GroupRepository
import com.example.praiseprisonapp.ui.home_tab.adapter.GroupAdapter
import kotlinx.coroutines.launch

class MyGroupFragment : Fragment(R.layout.home_my_group) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var groupAdapter: GroupAdapter
    private val groupRepository = GroupRepository()
    private val groupList = mutableListOf<GroupData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvMyGroups)
        emptyView = view.findViewById(R.id.emptyView)
        
        setupRecyclerView()
        loadMyGroups()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // 오버스크롤 효과 제거
        recyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return EdgeEffect(view.context).apply {
                    color = requireContext().getColor(R.color.background)
                }
            }
        }

        groupAdapter = GroupAdapter(groupList)
        recyclerView.adapter = groupAdapter
    }

    private fun loadMyGroups() {
        lifecycleScope.launch {
            try {
                val result = groupRepository.getMyGroups()
                result.onSuccess { groups ->
                    groupList.clear()
                    groupList.addAll(groups)
                    groupAdapter.notifyDataSetChanged()
                    
                    // 그룹 유무에 따라 빈 화면 표시
                    updateEmptyView(groups.isEmpty())
                }.onFailure { exception ->
                    if (exception is IllegalStateException && exception.message == "User not logged in") {
                        // 로그인되지 않은 상태는 조용히 처리
                        updateEmptyView(true)
                    } else {
                        // 실제 오류 발생 시에만 토스트 메시지 표시
                        Toast.makeText(requireContext(), "그룹 목록을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 그룹 목록 새로고침
        loadMyGroups()
    }

    // 새 그룹 추가 메서드
    fun addNewGroup(group: GroupData) {
        groupList.add(group)
        groupAdapter.notifyItemInserted(groupList.size - 1)
        // 그룹이 추가되면 빈 화면 숨기기
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}