package com.savedata.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.savedata.app.databinding.ActivityMainBinding
import com.savedata.app.vpn.SaveDataVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vpnActive = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            doStartVpn()
        } else {
            Snackbar.make(binding.root, "Разрешение VPN не предоставлено", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHostFragment.navController)

        binding.fabVpn.setOnClickListener {
            if (vpnActive) doStopVpn() else requestVpnPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        vpnActive = SaveDataVpnService.isRunning
        updateFabState(vpnActive)
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            doStartVpn()
        }
    }

    private fun doStartVpn() {
        startForegroundService(Intent(this, SaveDataVpnService::class.java).apply {
            action = SaveDataVpnService.ACTION_START
        })
        vpnActive = true
        updateFabState(true)
        Snackbar.make(binding.root, "VPN активирован", Snackbar.LENGTH_SHORT).show()
    }

    private fun doStopVpn() {
        startService(Intent(this, SaveDataVpnService::class.java).apply {
            action = SaveDataVpnService.ACTION_STOP
        })
        vpnActive = false
        updateFabState(false)
        Snackbar.make(binding.root, "VPN отключён", Snackbar.LENGTH_SHORT).show()
    }

    private fun updateFabState(active: Boolean) {
        if (active) {
            binding.fabVpn.text = getString(R.string.vpn_on)
            binding.fabVpn.backgroundTintList =
                ColorStateList.valueOf(getColor(R.color.success))
        } else {
            binding.fabVpn.text = getString(R.string.vpn_off)
            binding.fabVpn.backgroundTintList =
                ColorStateList.valueOf(getColor(R.color.error))
        }
    }
}
