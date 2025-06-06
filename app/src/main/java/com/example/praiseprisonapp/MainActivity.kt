package com.example.praiseprisonapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        toggleGroup = view.findViewById(R.id.toggleGroup)

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

        // Set up ToggleGroup
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMyGroup -> viewPager.currentItem = 0
                    R.id.btnAllGroup -> viewPager.currentItem = 1
                }
            }
        }

        // Set initial page
        viewPager.currentItem = 0
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

class MyDiaryMainFragment : Fragment(R.layout.mydiary_main)
//class ProfileFragment : Fragment(R.layout.profile_main)