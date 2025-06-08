package com.example.praiseprisonapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.praiseprisonapp.databinding.ProfileFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment() {
    private var _binding: ProfileFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var prefs: SharedPreferences
    private val writtenDates = HashSet<LocalDate>()
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한이 허용되면 알림 설정 복원
            restorePreviousSettings()
        } else {
            Toast.makeText(requireContext(), "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            binding.reminderSwitch.isChecked = false
        }
    }

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

        // 알림 권한 확인 및 요청
        checkNotificationPermission()
        
        // 켜자마자 이전에 설정해뒀던 알림 시간 보이게
        updateReminderTimeText()

        lifecycleScope.launch {
            launch { loadUserInfo() }
            launch { setupCalendar() }
        }

        setupNotificationSettings()

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    private suspend fun loadUserInfo() = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext
            val doc = db.collection("users").document(user.uid).get().await()
            withContext(Dispatchers.Main) {
                binding.emailText.text = user.email
                binding.nicknameText.text = doc.getString("nickname") ?: "닉네임 없음"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                binding.nicknameText.text = "닉네임 로드 실패"
            }
        }
    }

    private suspend fun setupCalendar() = withContext(Dispatchers.Main) {
        val user = auth.currentUser ?: return@withContext
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(12)
        val endMonth = currentMonth.plusMonths(12)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        binding.attendanceCalendar.apply {
            dayViewResource = R.layout.profile_calendar_day
            monthHeaderResource = R.layout.profile_calendar_month
            monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
                override fun create(view: View) = MonthViewContainer(view)
                override fun bind(container: MonthViewContainer, data: CalendarMonth) {}
            }
            dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(view)
                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    container.textView.text = data.date.dayOfMonth.toString()
                    container.textView.setTextColor(
                        if (writtenDates.contains(data.date)) Color.RED else Color.BLACK
                    )
                    container.view.setOnClickListener {
                        if (writtenDates.contains(data.date))
                            Toast.makeText(requireContext(), "이미 작성된 날짜입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            monthScrollListener = { month ->
                binding.monthYearText.text = monthTitleFormatter.format(month.yearMonth)
            }
            setup(startMonth, endMonth, firstDayOfWeek)
            scrollToMonth(currentMonth)
        }

        // Load diary dates
        withContext(Dispatchers.IO) {
            try {
                val docs = db.collection("diaries")
                    .whereEqualTo("authorId", user.uid)
                    .get().await()
                val dates = docs.mapNotNull { it.getTimestamp("createdAt")?.toDate()?.let { d ->
                    Calendar.getInstance().apply { time = d }.let { cal ->
                        LocalDate.of(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                }}.toSet()
                withContext(Dispatchers.Main) {
                    writtenDates.clear()
                    writtenDates.addAll(dates)
                    binding.attendanceCalendar.notifyCalendarChanged()
                    binding.attendanceDays.text = "${writtenDates.size}일 작성"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    restorePreviousSettings()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(requireContext(), "알림 기능을 사용하려면 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            restorePreviousSettings()
        }
    }

    private fun restorePreviousSettings() {
        val enabled = prefs.getBoolean("reminder_enabled", false)
        binding.reminderSwitch.isChecked = enabled
        
        if (enabled) {
            val hour = prefs.getInt("reminder_hour", -1)
            val minute = prefs.getInt("reminder_minute", -1)
            if (hour >= 0 && minute >= 0) {
                scheduleDailyReminder(hour, minute)
            }
        }
    }

    private fun setupNotificationSettings() {
        binding.reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    binding.reminderSwitch.isChecked = false
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                
                showHourPicker { hour, minute ->
                    prefs.edit().apply {
                        putBoolean("reminder_enabled", true)
                        putInt("reminder_hour", hour)
                        putInt("reminder_minute", minute)
                        apply()
                    }
                    scheduleDailyReminder(hour, minute)
                    updateReminderTimeText()
                    Toast.makeText(requireContext(), "${hour}시 ${minute}분에 알림이 설정되었습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                prefs.edit().putBoolean("reminder_enabled", false).apply()
                cancelDailyReminder()
                updateReminderTimeText()
            }
        }
    }

    private fun updateReminderTimeText() {
        val enabled = prefs.getBoolean("reminder_enabled", false)
        val hour = prefs.getInt("reminder_hour", -1)
        val minute = prefs.getInt("reminder_minute", -1)
        if (enabled && hour >= 0 && minute >= 0) {
            binding.reminderTimeText.apply {
                text = String.format("설정된 시간: %02d시 %02d분", hour, minute)
                visibility = View.VISIBLE
            }
        } else {
            binding.reminderTimeText.visibility = View.GONE
        }
    }

    private fun showHourPicker(onTimeSelected: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(), { _, h, m -> onTimeSelected(h, m) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).apply {
            setTitle("알림 시간 선택")
            show()   // 이제 이 show()는 TimePickerDialog의 메서드로 정상 인식
        }
    }

    private fun scheduleDailyReminder(hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val first = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        
        val delay = first.timeInMillis - now.timeInMillis
        val work = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("daily_reminder")
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "daily_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            work
        )
    }

    private fun cancelDailyReminder() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("daily_reminder")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
        _binding = null
    }

    class NotificationWorker(
        ctx: Context, params: WorkerParameters
    ) : Worker(ctx, params) {
        override fun doWork(): Result {
            val title = inputData.getString("title") ?: ""
            val msg = inputData.getString("message") ?: ""
            NotificationHelper(applicationContext).createNotification(title, msg)
            return Result.success()
        }
    }

    class NotificationHelper(private val context: Context) {
        private val channelId = "reminder_channel"
        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(
                    channelId, "Reminder", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "매일 알림 채널" }
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(chan)
            }
        }

        fun createNotification(title: String, message: String) {
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(
                System.currentTimeMillis().toInt(), notif
            )
        }
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView = view.findViewById<android.widget.TextView>(R.id.calendarDayText)
    }
    class MonthViewContainer(view: View) : ViewContainer(view)
}
