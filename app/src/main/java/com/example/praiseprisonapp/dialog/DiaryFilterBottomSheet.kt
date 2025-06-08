package com.example.praiseprisonapp.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import com.example.praiseprisonapp.databinding.MydiaryFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: MydiaryFilterBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null
    private var selectedSpecificDate: Long? = null

    var onFilterApplied: ((String, Long?, Long?) -> Unit)? = null
    private var groups: List<String> = emptyList()

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

    fun setGroups(groups: List<String>) {
        this.groups = groups
        setupGroupRadioButtons()
    }

    private fun setupViews() {
        setupGroupRadioButtons()
        setupDateTypeSelection()
        setupDatePickers()
        setupApplyButton()
    }

    private fun setupGroupRadioButtons() {
        binding.groupRadioGroup.removeAllViews()
        groups.forEach { groupName ->
            val radioButton = RadioButton(requireContext()).apply {
                text = groupName
                id = View.generateViewId()
            }
            binding.groupRadioGroup.addView(radioButton)
        }
    }

    private fun setupDateTypeSelection() {
        binding.dateTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.specificDateRadio.id -> {
                    binding.specificDateContainer.visibility = View.VISIBLE
                    binding.dateRangeContainer.visibility = View.GONE
                }
                binding.dateRangeRadio.id -> {
                    binding.specificDateContainer.visibility = View.GONE
                    binding.dateRangeContainer.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDatePickers() {
        binding.specificDateButton.setOnClickListener {
            showDatePicker { date ->
                selectedSpecificDate = date
                binding.specificDateButton.text = dateFormat.format(Date(date))
            }
        }

        binding.startDateButton.setOnClickListener {
            showDatePicker { date ->
                selectedStartDate = date
                binding.startDateButton.text = dateFormat.format(Date(date))
            }
        }

        binding.endDateButton.setOnClickListener {
            showDatePicker { date ->
                selectedEndDate = date
                binding.endDateButton.text = dateFormat.format(Date(date))
            }
        }
    }

    private fun setupApplyButton() {
        binding.applyButton.setOnClickListener {
            val selectedGroupId = binding.groupRadioGroup.checkedRadioButtonId
            val selectedGroup = if (selectedGroupId != -1) {
                binding.groupRadioGroup.findViewById<RadioButton>(selectedGroupId).text.toString()
            } else {
                ""
            }

            val startDate = when (binding.dateTypeRadioGroup.checkedRadioButtonId) {
                binding.specificDateRadio.id -> selectedSpecificDate
                binding.dateRangeRadio.id -> selectedStartDate
                else -> null
            }

            val endDate = when (binding.dateTypeRadioGroup.checkedRadioButtonId) {
                binding.specificDateRadio.id -> selectedSpecificDate
                binding.dateRangeRadio.id -> selectedEndDate
                else -> null
            }

            onFilterApplied?.invoke(selectedGroup, startDate, endDate)
            dismiss()
        }
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("날짜 선택")
            .build()

        picker.addOnPositiveButtonClickListener { date ->
            onDateSelected(date)
        }

        picker.show(parentFragmentManager, "date_picker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 