package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.surendramaran.yolov8tflite.databinding.FragmentRiwayatBinding

class RiwayatFragment : Fragment() {

    private var _binding: FragmentRiwayatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRiwayatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Muat ulang data setiap kali fragment ini ditampilkan
        loadHistoryData()
    }

    private fun loadHistoryData() {
        val historyData = HistoryManager.getHistory(requireContext())

        if (historyData.isEmpty()) {
            binding.historyRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.historyRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE

            val adapter = HistoryAdapter(historyData) { clickedItem ->
                val intent = Intent(activity, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_IMAGE_URI, clickedItem.imageUri)
                    putParcelableArrayListExtra(
                        ResultActivity.EXTRA_DETECTED_FOODS ,
                        ArrayList(clickedItem.boundingBoxes)
                    )
                    putStringArrayListExtra(
                        ResultActivity.EXTRA_QUALITY_RESULTS,
                        ArrayList(clickedItem.qualityResults ?: emptyList())
                    )
                    putExtra("IS_FROM_HISTORY", true)
                }
                startActivity(intent)
            }
            binding.historyRecyclerView.layoutManager = LinearLayoutManager(context)
            binding.historyRecyclerView.adapter = adapter
        }

        binding.btnScanNow.setOnClickListener {
            // Pindah ke tab Scan (indeks 0)
            val viewPager = activity?.findViewById<ViewPager2>(R.id.view_pager)
            viewPager?.currentItem = 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}