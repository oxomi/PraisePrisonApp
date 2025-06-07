package com.example.praiseprisonapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.praiseprisonapp.databinding.ProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ProfileFragment : Fragment() {
    private var _binding: ProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        prefs = requireContext().getSharedPreferences("notifications", Context.MODE_PRIVATE)

        // 현재 사용자 정보 표시
        loadUserInfo()
        
        // 캘린더 설정
        setupCalendar()
        
        // 알림 설정 초기화 및 리스너 설정
        setupNotificationSettings()

        // 로그아웃 버튼 클릭 리스너
        binding.logoutButton.setOnClickListener {
            auth.signOut()
            // 로그인 화면으로 이동
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            // 이메일 표시
            binding.emailText.text = user.email

            // Firestore에서 사용자 정보 가져오기
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val nickname = document.getString("nickname")
                        binding.nicknameText.text = nickname ?: "닉네임 없음"
                    } else {
                        binding.nicknameText.text = "사용자 정보 없음"
                    }
                }
                .addOnFailureListener {
                    binding.nicknameText.text = "닉네임 로드 실패"
                }
        }
    }

    private fun setupCalendar() {
        val user = auth.currentUser ?: return
        
        // 일기 작성 날짜 데이터 가져오기
        db.collection("diaries")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                val writtenDates = documents.map { doc ->
                    doc.getTimestamp("createdAt")?.toDate()
                }.filterNotNull()

                // 캘린더 날짜 선택 리스너
                binding.attendanceCalendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
                    val selectedDate = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth)
                    }.time
                    
                    // 선택한 날짜에 일기가 있는지 확인
                    val hasEntry = writtenDates.any { date ->
                        isSameDay(date, selectedDate)
                    }
                    
                    if (hasEntry) {
                        Toast.makeText(requireContext(), "이 날짜에 작성한 일기가 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun setupNotificationSettings() {
        // 저장된 알림 설정 불러오기
        binding.reminderSwitch.isChecked = prefs.getBoolean("reminder_enabled", true)
        binding.interactionSwitch.isChecked = prefs.getBoolean("interaction_enabled", true)

        // 알림 설정 변경 리스너
        binding.reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("reminder_enabled", isChecked).apply()
            updateNotificationSettings()
        }

        binding.interactionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("interaction_enabled", isChecked).apply()
            updateNotificationSettings()
        }
    }

    private fun updateNotificationSettings() {
        // TODO: FCM 토큰 업데이트 및 서버에 알림 설정 전송
        val user = auth.currentUser ?: return
        val reminderEnabled = binding.reminderSwitch.isChecked
        val interactionEnabled = binding.interactionSwitch.isChecked

        db.collection("users").document(user.uid)
            .update(mapOf(
                "reminderEnabled" to reminderEnabled,
                "interactionEnabled" to interactionEnabled
            ))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "알림 설정이 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "알림 설정 업데이트 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}