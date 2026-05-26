package com.savedata.app.ui.settings

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.savedata.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private val periodOptions = listOf(
        1 to "1 час",
        3 to "3 часа",
        6 to "6 часов",
        12 to "12 часов",
        24 to "24 часа (1 день)",
        48 to "2 дня",
        72 to "3 дня",
        168 to "7 дней (1 неделя)",
        720 to "30 дней (1 месяц)"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPeriodSpinner()
        setupObservers()
    }

    private fun setupPeriodSpinner() {
        val labels = periodOptions.map { it.second }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = adapter

        viewModel.periodHours.observe(viewLifecycleOwner) { hours ->
            val idx = periodOptions.indexOfFirst { it.first == hours }.coerceAtLeast(0)
            binding.spinnerPeriod.setSelection(idx)
        }

        binding.spinnerPeriod.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setPeriodHours(periodOptions[position].first)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        viewModel.showSystemApps.observe(viewLifecycleOwner) { show ->
            binding.switchSystemApps.isChecked = show
        }
        binding.switchSystemApps.setOnCheckedChangeListener { _, checked ->
            viewModel.setShowSystemApps(checked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
