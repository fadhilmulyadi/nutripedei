package com.surendramaran.yolov8tflite

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.surendramaran.yolov8tflite.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewPager = binding.viewPager
        val tabLayout = binding.tabLayout

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 3 // Keep fragments in memory

        // Menghubungkan TabLayout dengan ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Gunakan layout khusus untuk tab Scan
            val customView = if (position == 1) {
                LayoutInflater.from(this).inflate(R.layout.custom_tab_item_scan, null)
            } else {
                LayoutInflater.from(this).inflate(R.layout.custom_tab_item, null)
            }

            val tabIcon = customView.findViewById<ImageView>(R.id.tab_icon)
            val tabText = customView.findViewById<TextView?>(R.id.tab_text)

            when (position) {
                0 -> {
                    tabIcon.setImageResource(R.drawable.ic_history)
                    tabText?.text = "Riwayat"
                }
                1 -> {
                    // Ikon dan background sudah diatur di layout custom_tab_item_scan
                }
                2 -> {
                    tabIcon.setImageResource(R.drawable.ic_settings)
                    tabText?.text = "Pengaturan"
                }
            }
            tab.customView = customView
        }.attach()

        // Listener for tab selection changes
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val customView = tab.customView
                val tabIcon = customView?.findViewById<ImageView>(R.id.tab_icon)
                val tabText = customView?.findViewById<TextView>(R.id.tab_text)

                if (tab.position != 1) {
                    tabIcon?.setColorFilter(ContextCompat.getColor(this@HomeActivity, android.R.color.white))
                    tabText?.visibility = View.VISIBLE
                    customView?.setBackgroundResource(R.drawable.tab_selected_background)
                }

                viewPager.setCurrentItem(tab.position, true) // Set smooth transition
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                val customView = tab.customView
                val tabIcon = customView?.findViewById<ImageView>(R.id.tab_icon)
                val tabText = customView?.findViewById<TextView>(R.id.tab_text)

                if (tab.position != 1) {
                    tabIcon?.setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.green_primary))
                    tabText?.visibility = View.GONE
                    customView?.setBackgroundResource(0)
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // Do nothing or add specific re-selection logic
            }
        })

        // Set initial selected tab (Scan in the middle)
        tabLayout.getTabAt(1)?.select()
    }

    // Adapter untuk ViewPager2
    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> RiwayatFragment() // Riwayat
                1 -> ScanFragment()    // Scan
                2 -> PengaturanFragment() // Pengaturan
                else -> throw IllegalStateException("Unexpected position $position")
            }
        }
    }
}
