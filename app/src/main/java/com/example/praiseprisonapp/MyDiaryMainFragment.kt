package com.example.praiseprisonapp

import android.os.Bundle
import android.util.Log
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
        // 필터 초기화
        currentGroup = ""
        currentStartDate = null
        currentEndDate = null
        
        lifecycleScope.launch {
            try {
                groupList = getUserGroups()
                diaryAdapter.setGroups(groupList)
                updateFilterInfo()  // 필터 정보 UI 업데이트
                loadDiaries()
            } catch (e: Exception) {
                Toast.makeText(context ?: return@launch, "데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFilterDialog() {
        val filterDialog = DiaryFilterBottomSheet().apply {
            onFilterApplied = { group, startDate, endDate ->
                // "전체 그룹" 선택 시 빈 문자열로 설정
                currentGroup = if (group == "전체 그룹") "" else group
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
                "전체 그룹"
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
        // Fragment가 attached된 상태인지 확인
        if (!isAdded) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid
                if (currentUserId == null) {
                    Log.e("MyDiaryMainFragment", "사용자 ID가 null입니다.")
                    return@launch
                }

                // 기본 쿼리: 사용자의 모든 일기
                var query = db.collection("diaries")
                    .whereEqualTo("authorId", currentUserId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)

                // 로그 추가
                Log.d("MyDiaryMainFragment", "쿼리 시작 - 사용자 ID: $currentUserId")
                Log.d("MyDiaryMainFragment", "필터 조건 - 그룹: $currentGroup, 시작일: $currentStartDate, 종료일: $currentEndDate")

                // 특정 그룹 필터가 있는 경우
                if (currentGroup.isNotEmpty()) {
                    Log.d("MyDiaryMainFragment", "그룹 필터 적용: $currentGroup")
                    query = query.whereEqualTo("groupId", currentGroup)
                }

                // 날짜 필터가 있는 경우
                if (currentStartDate != null && currentEndDate != null) {
                    val startTimestamp = Timestamp(Date(currentStartDate!!))
                    val endTimestamp = Timestamp(Date(currentEndDate!!))
                    Log.d("MyDiaryMainFragment", "날짜 필터 적용 - 시작: ${startTimestamp.toDate()}, 종료: ${endTimestamp.toDate()}")
                    query = query.whereGreaterThanOrEqualTo("createdAt", startTimestamp)
                        .whereLessThanOrEqualTo("createdAt", endTimestamp)
                }

                try {
                    val querySnapshot = query.get().await()
                    Log.d("MyDiaryMainFragment", "쿼리 결과 문서 개수: ${querySnapshot.size()}")
                    
                    // 각 문서의 데이터를 로그로 출력
                    querySnapshot.documents.forEach { doc ->
                        Log.d("MyDiaryMainFragment", "문서 ID: ${doc.id}")
                        Log.d("MyDiaryMainFragment", "문서 데이터: ${doc.data}")
                    }

                    val diaries = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            // 문서 데이터를 Map으로 가져오기
                            val data = doc.data
                            if (data != null) {
                                // authorName이 null이면 기본값 설정
                                if (data["authorName"] == null) {
                                    data["authorName"] = "알 수 없음"
                                }
                                
                                // Diary 객체 생성
                                Diary(
                                    id = doc.id,
                                    authorId = data["authorId"] as? String ?: "",
                                    authorName = data["authorName"] as? String ?: "알 수 없음",
                                    commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0,
                                    content = data["content"] as? String ?: "",
                                    createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                                    groupId = data["groupId"] as? String ?: "",
                                    imageUrl = data["imageUrl"] as? String,
                                    mood = data["mood"] as? String ?: "",
                                    weatherType = (data["weatherType"] as? Long)?.toInt() ?: 1
                                ).also {
                                    // Firestore 문서도 업데이트
                                    if (data["authorName"] == null) {
                                        doc.reference.update("authorName", it.authorName)
                                    }
                                }
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("MyDiaryMainFragment", "문서 변환 실패 - ID: ${doc.id}, 데이터: ${doc.data}", e)
                            null
                        }
                    }
                    
                    Log.d("MyDiaryMainFragment", "최종 변환된 일기 개수: ${diaries.size}")

                    // Fragment가 여전히 attached 상태인지 다시 확인
                    if (!isAdded) {
                        Log.d("MyDiaryMainFragment", "Fragment가 더 이상 attached되지 않음")
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        _binding?.let { binding ->
                            diaryAdapter.submitList(diaries)
                            binding.emptyView.visibility = if (diaries.isEmpty()) View.VISIBLE else View.GONE
                            
                            // 결과가 없을 때 사용자에게 알림
                            if (diaries.isEmpty()) {
                                Toast.makeText(requireContext(), "표시할 일기가 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MyDiaryMainFragment", "쿼리 실행 중 오류 발생", e)
                    throw e
                }
            } catch (e: Exception) {
                Log.e("MyDiaryMainFragment", "일기 목록 로드 실패", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        context?.let { safeContext ->
                            Toast.makeText(safeContext, "일기 목록을 불러오는데 실패했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
