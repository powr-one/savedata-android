package com.savedata.app

import android.content.Intent
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

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpn()
        } else {
            Snackbar.make(binding.root, "Разрешение VPN не предоставлено", Snackbar.LENGTH_SHORT).show()
            binding.fabVpn.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.fabVpn.isChecked = SaveDataVpnService.isRunning

        binding.fabVpn.addOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestVpnPermission()
            } else {
                stopVpn()
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        val intent = Intent(this, SaveDataVpnService::class.java).apply {
            action = SaveDataVpnService.ACTION_START
        }
        startForegroundService(intent)
        Snackbar.make(binding.root, "VPN активирован", Snackbar.LENGTH_SHORT).show()
    }

    private fun stopVpn() {
        val intent = Intent(this, SaveDataVpnService::class.java).apply {
            action = SaveDataVpnService.ACTION_STOP
        }
        startService(intent)
        Snackbar.make(binding.root, "VPN отключён", Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.fabVpn.isChecked = SaveDataVpnService.isRunning
    }
}
