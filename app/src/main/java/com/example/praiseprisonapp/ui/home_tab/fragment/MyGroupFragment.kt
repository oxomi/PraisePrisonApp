package com.example.praiseprisonapp.ui.home_tab.fragment

import android.os.Bundle
import android.util.Log
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
    private val TAG = "MyGroupFragment"

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var groupAdapter: GroupAdapter
    private val groupRepository = GroupRepository()
    private val groupList = mutableListOf<GroupData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        recyclerView = view.findViewById(R.id.rvMyGroups)
        emptyView = view.findViewById(R.id.emptyView)
        
        setupRecyclerView()
        loadMyGroups()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            
            // 오버스크롤 효과 제거
            edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                    return EdgeEffect(view.context).apply {
                        color = requireContext().getColor(R.color.background)
                    }
                }
            }
        }

        groupAdapter = GroupAdapter(groupList)
        recyclerView.adapter = groupAdapter
        
        Log.d(TAG, "RecyclerView setup completed")
    }

    fun loadMyGroups() {
        Log.d(TAG, "Loading my groups")
        
        lifecycleScope.launch {
            try {
                val result = groupRepository.getMyGroups()
                result.onSuccess { groups ->
                    Log.d(TAG, "Successfully loaded ${groups.size} groups")
                    
                    groupList.clear()
                    groupList.addAll(groups)
                    
                    Log.d(TAG, "Group list updated, size: ${groupList.size}")
                    groupAdapter.notifyDataSetChanged()
                    
                    // 그룹 유무에 따라 빈 화면 표시
                    updateEmptyView(groups.isEmpty())
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load groups", exception)
                    updateEmptyView(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading groups", e)
                updateEmptyView(true)
            }
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        Log.d(TAG, "Updating empty view, isEmpty: $isEmpty")
        
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
        Log.d(TAG, "onResume")
        // 화면이 다시 보일 때마다 그룹 목록 새로고침
        loadMyGroups()
    }

    // 새 그룹 추가 메서드
    fun addNewGroup(group: GroupData) {
        Log.d(TAG, "Adding new group: ${group.name}")
        groupList.add(0, group) // 새 그룹을 목록 맨 앞에 추가
        groupAdapter.notifyItemInserted(0)
        updateEmptyView(false)
    }
}