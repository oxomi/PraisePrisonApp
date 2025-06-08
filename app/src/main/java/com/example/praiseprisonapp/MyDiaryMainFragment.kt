package com.example.praiseprisonapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.praiseprisonapp.adapter.MyDiaryListAdapter
import com.example.praiseprisonapp.databinding.MydiaryMainBinding
import com.example.praiseprisonapp.dialog.DiaryFilterBottomSheet
import com.example.praiseprisonapp.model.Diary
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupInfo(val id: String, val name: String)
private var groupList: List<GroupInfo> = emptyList()

class MyDiaryMainFragment : Fragment(R.layout.mydiary_main) {

    private var _binding: MydiaryMainBinding? = null
    private val binding get() = _binding!!

    private val diaryAdapter = MyDiaryListAdapter()
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentGroup: String = ""
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MydiaryMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterButton()
        loadInitialData()
    }

    private fun setupRecyclerView() {
        binding.diaryRecyclerView.apply {
            adapter = diaryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFilterButton() {
        binding.filterButton.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                groupList = getUserGroups()
                diaryAdapter.setGroups(groupList)
                loadDiaries()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFilterDialog() {
        val filterDialog = DiaryFilterBottomSheet().apply {
            onFilterApplied = { group, startDate, endDate ->
                currentGroup = group
                currentStartDate = startDate
                currentEndDate = endDate
                updateFilterInfo()
                loadDiaries()
            }
        }

        filterDialog.setGroups(groupList)
        filterDialog.show(parentFragmentManager, "filter_dialog")
    }

    private suspend fun getUserGroups(): List<GroupInfo> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = auth.currentUser?.uid ?: return@withContext emptyList()
            val groupsSnapshot = db.collection("groups")
                .whereArrayContains("members", currentUserId)
                .get()
                .await()

            groupsSnapshot.documents.map { doc ->
                GroupInfo(
                    id = doc.id,
                    name = doc.getString("name") ?: ""
                )
            }.filter { it.name.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateFilterInfo() {
        val filterText = buildString {
            append("현재 필터: ")
            val groupName = if (currentGroup.isEmpty()) {
                "모든 그룹"
            } else {
                groupList.find { it.id == currentGroup }?.name ?: "(알 수 없는 그룹)"
            }
            append(groupName)
            append(" · ")

            if (currentStartDate != null && currentEndDate != null) {
                val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREA)
                val startDate = Date(currentStartDate!!)
                val endDate = Date(currentEndDate!!)

                // 하루만 선택한 경우: endDate == startDate + 1일
                val oneDayLater = currentStartDate!! + 24 * 60 * 60 * 1000
                if (currentEndDate == oneDayLater) {
                    append(sdf.format(startDate))
                } else {
                    append("${sdf.format(startDate)} - ${sdf.format(endDate)}")
                }
            } else {
                append("모든 기간")
            }
        }

        binding.filterInfoText.text = filterText
    }

    private fun loadDiaries() {
        lifecycleScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch

                var query = db.collection("diaries")
                    .whereEqualTo("authorId", currentUserId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)

                // 전체 그룹이 아닌 경우에만 groupId 조건 추가
                if (currentGroup.isNotEmpty() && currentGroup != "전체 그룹") {
                    query = query.whereEqualTo("groupId", currentGroup)
                }

                if (currentStartDate != null && currentEndDate != null) {
                    query = query.whereGreaterThanOrEqualTo("createdAt", Timestamp(Date(currentStartDate!!)))
                        .whereLessThanOrEqualTo("createdAt", Timestamp(Date(currentEndDate!!)))
                }

                val diaries = query.get().await().toObjects(Diary::class.java)

                withContext(Dispatchers.Main) {
                    diaryAdapter.submitList(diaries)
                    binding.emptyView.visibility = if (diaries.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "일기 목록을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
