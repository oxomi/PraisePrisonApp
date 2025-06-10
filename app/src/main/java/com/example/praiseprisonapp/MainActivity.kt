package com.example.praiseprisonapp

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.praiseprisonapp.ui.home_tab.fragment.MyGroupFragment
import com.example.praiseprisonapp.ui.home_tab.fragment.AllGroupFragment
import com.example.praiseprisonapp.ui.home_tab.fragment.GroupCreateFragment
import com.google.android.material.button.MaterialButton
import android.graphics.Color


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 시스템 바 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        setContentView(R.layout.activity_main)

        // 시스템 바 패딩 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        // Initialize bottom navigation
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Set initial fragment
        if (savedInstanceState == null) {
            loadFragment(HomeMainFragment())
        }

        // Handle bottom navigation item selection
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeMainFragment())
                    true
                }
                R.id.navigation_diary -> {
                    loadFragment(MyDiaryMainFragment())
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

// Fragment classes for each main screen
class HomeMainFragment : Fragment(R.layout.home_main) {
    private lateinit var viewPager: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toggleGroup= view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        val btnMyGroup = view.findViewById<MaterialButton>(R.id.btnMyGroup)
        val btnAllGroup = view.findViewById<MaterialButton>(R.id.btnAllGroup)

        viewPager = view.findViewById(R.id.viewPager)

        fun updateToggleStyle(checkedId: Int) {
            val white = ContextCompat.getColor(requireContext(), android.R.color.white)
            val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)

            // 모든 버튼을 먼저 투명하게 설정
            btnMyGroup.backgroundTintList = ColorStateList.valueOf(transparent)
            btnAllGroup.backgroundTintList = ColorStateList.valueOf(transparent)

            // 선택된 버튼만 흰색으로 설정
            when (checkedId) {
                R.id.btnMyGroup -> btnMyGroup.backgroundTintList = ColorStateList.valueOf(white)
                R.id.btnAllGroup -> btnAllGroup.backgroundTintList = ColorStateList.valueOf(white)
            }
        }

        // 그룹 생성 버튼 클릭 리스너 추가
        view.findViewById<View>(R.id.btnCreateGroup).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GroupCreateFragment())
                .addToBackStack(null)  // 백 스택에 추가하여 뒤로가기 가능하게 함
                .commit()
        }

        // Set up ViewPager
        viewPager.adapter = HomePagerAdapter(this)
        viewPager.isUserInputEnabled = false  // Disable swipe

        // Set up ToggleGroup and handle button checks
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateToggleStyle(checkedId)
                when (checkedId) {
                    R.id.btnMyGroup -> viewPager.currentItem = 0
                    R.id.btnAllGroup -> viewPager.currentItem = 1
                }
            }
        }

        // Set initial state
        toggleGroup.check(R.id.btnMyGroup)
        updateToggleStyle(R.id.btnMyGroup)
    }

    private inner class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MyGroupFragment()
                1 -> AllGroupFragment()
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }
    }
}


