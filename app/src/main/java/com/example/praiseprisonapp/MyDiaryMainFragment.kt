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

class DiaryListFragment : Fragment() {

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
        setupToolbar()
        loadDiaries()


        Toast.makeText(requireContext(), "debug: 실행됨", Toast.LENGTH_SHORT).show()


    }

    private fun setupRecyclerView() {
        binding.diaryRecyclerView.apply {
            adapter = diaryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                else -> false
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

        // 사용자가 속한 그룹 목록 가져오기
        lifecycleScope.launch {
            val groups = getUserGroups()
            filterDialog.setGroups(groups)
            filterDialog.show(parentFragmentManager, "filter_dialog")
        }
    }

    private suspend fun getUserGroups(): List<String> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = auth.currentUser?.uid ?: return@withContext emptyList()
            val groupsSnapshot = db.collection("groups")
                .whereArrayContains("members", currentUserId)
                .get()
                .await()

            groupsSnapshot.documents.mapNotNull { it.getString("name") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateFilterInfo() {
        val filterText = buildString {
            append("현재 필터: ")
            append(if (currentGroup.isEmpty()) "모든 그룹" else currentGroup)
            append(" · ")
            if (currentStartDate != null && currentEndDate != null) {
                if (currentStartDate == currentEndDate) {
                    append(dateFormat.format(Date(currentStartDate!!)))
                } else {
                    append(dateFormat.format(Date(currentStartDate!!)))
                    append(" ~ ")
                    append(dateFormat.format(Date(currentEndDate!!)))
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

                if (currentGroup.isNotEmpty()) {
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
                // 에러 처리
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
