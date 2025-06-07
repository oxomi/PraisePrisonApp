package com.example.praiseprisonapp.ui.home_tab.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EdgeEffect
import android.widget.EditText
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

class AllGroupFragment : Fragment(R.layout.home_all_group) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var searchEditText: EditText
    private lateinit var emptyView: TextView
    private val groupRepository = GroupRepository()

    private var allGroups = mutableListOf<GroupData>()
    private var filteredGroups = mutableListOf<GroupData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvAllGroups)
        emptyView = view.findViewById(R.id.emptyView)
        
        setupRecyclerView()
        setupSearch()
        loadAllGroups()
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

        groupAdapter = GroupAdapter(filteredGroups)
        recyclerView.adapter = groupAdapter
    }

    private fun setupSearch() {
        searchEditText = requireView().findViewById(R.id.etSearch)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterGroups(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    fun loadAllGroups() {
        lifecycleScope.launch {
            try {
                val result = groupRepository.getAllGroups()
                result.onSuccess { groups ->
                    allGroups.clear()
                    allGroups.addAll(groups)
                    filterGroups(searchEditText.text.toString())
                }.onFailure { exception ->
                    if (exception is IllegalStateException && exception.message == "User not logged in") {
                        updateEmptyView(true)
                    } else {
                        updateEmptyView(true)
                    }
                }
            } catch (e: Exception) {
                updateEmptyView(true)
            }
        }
    }

    private fun filterGroups(query: String) {
        filteredGroups.clear()
        if (query.isEmpty()) {
            filteredGroups.addAll(allGroups)
        } else {
            val searchQuery = query.lowercase()
            filteredGroups.addAll(allGroups.filter { group ->
                group.name.lowercase().contains(searchQuery) ||
                group.description.lowercase().contains(searchQuery)
            })
        }
        groupAdapter.notifyDataSetChanged()
        
        // 검색 결과에 따라 빈 화면 표시
        if (query.isEmpty()) {
            updateEmptyView(allGroups.isEmpty(), "아직 생성된 그룹이 없습니다.")
        } else {
            updateEmptyView(filteredGroups.isEmpty(), "검색 결과가 없습니다.")
        }
    }

    private fun updateEmptyView(isEmpty: Boolean, message: String? = null) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            message?.let { emptyView.text = it }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 그룹 목록 새로고침
        loadAllGroups()
    }
}