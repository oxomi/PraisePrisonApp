package com.example.praiseprisonapp.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import com.example.praiseprisonapp.GroupInfo
import com.example.praiseprisonapp.databinding.MydiaryFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import java.time.ZoneOffset
import java.util.Locale

class DiaryFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: MydiaryFilterBinding? = null
    private val binding get() = _binding!!

    private var selectedDates = mutableListOf<LocalDate>()
    private var groups: List<GroupInfo> = emptyList()
    private var groupIdMap: Map<String, String> = emptyMap() // name → id
    private var currentMonth = YearMonth.now()
    
    private val monthYearFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)

    var onFilterApplied: ((String, Long?, Long?) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MydiaryFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    fun setGroups(groups: List<GroupInfo>) {
        this.groups = groups
        groupIdMap = groups.associate { it.name to it.id } // 🔥 이름으로 문서 ID 찾아내기
        if (_binding != null) setupGroupRadioButtons()
    }

    private fun setupViews() {
        setupGroupRadioButtons()
        setupCalendar()
        setupMonthNavigation()
        setupApplyButton()
    }

    private fun setupGroupRadioButtons() {
        binding.groupRadioGroup.removeAllViews()

        // "전체 그룹" 버튼 추가
        val allGroupsRadio = RadioButton(requireContext()).apply {
            text = "전체 그룹"
            id = View.generateViewId()
            setTextColor(resources.getColor(com.example.praiseprisonapp.R.color.textPrimary))
            buttonTintList = resources.getColorStateList(com.example.praiseprisonapp.R.color.textPrimary)
        }
        binding.groupRadioGroup.addView(allGroupsRadio)

        // 실제 그룹 목록 추가
        groups.forEach { groupInfo ->
            val radioButton = RadioButton(requireContext()).apply {
                text = groupInfo.name
                id = View.generateViewId()
                setTextColor(resources.getColor(com.example.praiseprisonapp.R.color.textPrimary))
                buttonTintList = resources.getColorStateList(com.example.praiseprisonapp.R.color.textPrimary)
            }
            binding.groupRadioGroup.addView(radioButton)
        }

        // 기본 선택은 "전체 그룹"
        binding.groupRadioGroup.check(allGroupsRadio.id)
    }

    private fun setupMonthNavigation() {
        updateMonthYearText()
        
        binding.previousMonthButton.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            binding.calendarView.smoothScrollToMonth(currentMonth)
            updateMonthYearText()
        }
        
        binding.nextMonthButton.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            binding.calendarView.smoothScrollToMonth(currentMonth)
            updateMonthYearText()
        }
    }
    
    private fun updateMonthYearText() {
        binding.monthYearText.text = currentMonth.format(monthYearFormatter)
    }

    private fun setupCalendar() {
        val startMonth = YearMonth.now().minusMonths(12)
        val endMonth = YearMonth.now().plusMonths(12)
        val daysOfWeek = daysOfWeek(firstDayOfWeekFromLocale())

        binding.calendarView.setup(startMonth, endMonth, daysOfWeek.first())
        binding.calendarView.scrollToMonth(currentMonth)
        
        binding.calendarView.monthScrollListener = { month ->
            currentMonth = month.yearMonth
            updateMonthYearText()
        }
        
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                container.textView.text = data.date.dayOfMonth.toString()
                
                if (data.position == DayPosition.MonthDate) {
                    container.textView.visibility = View.VISIBLE
                    
                    val isSelected = selectedDates.contains(data.date)
                    container.textView.setBackgroundResource(
                        if (isSelected) com.example.praiseprisonapp.R.drawable.mydiary_filter_selected_day_background
                        else 0
                    )
                    
                    container.view.setOnClickListener {
                        onDateSelected(data.date)
                    }
                } else {
                    container.textView.visibility = View.INVISIBLE
                    container.view.setOnClickListener(null)
                }
            }
        }
    }

    private fun onDateSelected(date: LocalDate) {
        when {
            selectedDates.isEmpty() -> {
                selectedDates.add(date)
                binding.calendarView.notifyDateChanged(date)
            }
            selectedDates.size == 1 -> {
                if (date.isAfter(selectedDates[0])) {
                    selectedDates.add(date)
                    selectDateRange(selectedDates[0], date)
                } else {
                    clearSelection()
                    selectedDates.add(date)
                    binding.calendarView.notifyDateChanged(date)
                }
            }
            else -> {
                clearSelection()
                selectedDates.add(date)
                binding.calendarView.notifyDateChanged(date)
            }
        }
    }

    private fun clearSelection() {
        val oldSelection = selectedDates.toList() // 복사본 생성
        selectedDates.clear()
        oldSelection.forEach { date ->
            binding.calendarView.notifyDateChanged(date)
        }
    }

    private fun selectDateRange(start: LocalDate, end: LocalDate) {
        var current = start
        while (!current.isAfter(end)) {
            selectedDates.add(current)
            binding.calendarView.notifyDateChanged(current)
            current = current.plusDays(1)
        }
    }

    private fun setupApplyButton() {
        binding.applyButton.setOnClickListener {
            val selectedGroupId = binding.groupRadioGroup.checkedRadioButtonId
            val selectedGroupName = if (selectedGroupId != -1) {
                binding.groupRadioGroup.findViewById<RadioButton>(selectedGroupId).text.toString()
            } else {
                "전체 그룹"
            }

            // 선택된 그룹 이름을 ID로 변환
            val selectedGroupDocId= if (selectedGroupName == "전체 그룹") {
                ""
            } else {
                groupIdMap[selectedGroupName] ?: ""
            }

            val startDate = selectedDates.firstOrNull()
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toEpochSecond()
                ?.times(1000)

            val endDate = selectedDates.lastOrNull()
                ?.plusDays(1)
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toEpochSecond()
                ?.times(1000)

            onFilterApplied?.invoke(selectedGroupDocId, startDate, endDate)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DayViewContainer(view: View) : com.kizitonwose.calendar.view.ViewContainer(view) {
    val textView: android.widget.TextView = view.findViewById(com.example.praiseprisonapp.R.id.calendarDayText)
    lateinit var day: CalendarDay
} 