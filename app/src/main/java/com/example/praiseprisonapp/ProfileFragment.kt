package com.example.praiseprisonapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.praiseprisonapp.databinding.ProfileFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.google.firebase.Timestamp

class ProfileFragment : Fragment() {
    private var _binding: ProfileFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var prefs: SharedPreferences
    private val writtenDates = HashSet<LocalDate>()
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileFragmentBinding.inflate(inflater, container, false)
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

        // 캘린더 초기 설정
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(12)  // 12개월 전부터
        val endMonth = currentMonth.plusMonths(12)    // 12개월 후까지
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        binding.attendanceCalendar.apply {
            dayViewResource = R.layout.profile_calendar_day
            monthHeaderResource = R.layout.profile_calendar_month

            monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
                override fun create(view: View) = MonthViewContainer(view)
                override fun bind(container: MonthViewContainer, data: CalendarMonth) {
                    // 월 헤더는 calendar_month.xml에서 이미 처리됨
                }
            }

            dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(view)
                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    val date = data.date
                    container.textView.text = date.dayOfMonth.toString()

                    // 일기 작성 날짜 표시
                    if (writtenDates.contains(date)) {
                        container.textView.setTextColor(Color.RED)
                    } else {
                        container.textView.setTextColor(Color.BLACK)
                    }

                    container.view.setOnClickListener {
                        if (writtenDates.contains(date)) {
                            Toast.makeText(requireContext(), "이 날짜에 작성한 일기가 있습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            monthScrollListener = { month ->
                binding.monthYearText.text = monthTitleFormatter.format(month.yearMonth)
            }

            setup(startMonth, endMonth, firstDayOfWeek)
            scrollToMonth(currentMonth)
        }

        // 일기 작성 날짜 데이터 가져오기
        db.collection("diaries")
            .whereEqualTo("authorId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                writtenDates.clear()
                for (document in documents) {
                    val timestamp = document.getTimestamp("createdAt")
                    if (timestamp != null) {
                        val date = timestamp.toDate()
                        val calendar = Calendar.getInstance().apply { time = date }
                        val localDate = LocalDate.of(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        writtenDates.add(localDate)
                    }
                }
                binding.attendanceCalendar.notifyCalendarChanged()

                // 출석 일수 표시
                binding.monthYearText.text = "${writtenDates.size}일 작성"
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "일기 데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView = view.findViewById<android.widget.TextView>(R.id.calendarDayText)
    }

    class MonthViewContainer(view: View) : ViewContainer(view)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}