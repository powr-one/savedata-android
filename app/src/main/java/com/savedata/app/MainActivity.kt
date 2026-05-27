package com.savedata.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.savedata.app.databinding.ActivityMainBinding
import com.savedata.app.vpn.SaveDataVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vpnActive = false
    private var vpnSwitch: SwitchMaterial? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            doStartVpn()
        } else {
            setSwitch(false)
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

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                val actionView = menu.findItem(R.id.action_vpn_toggle)?.actionView
                vpnSwitch = actionView?.findViewById(R.id.switch_vpn)
                setSwitch(SaveDataVpnService.isRunning)
                wireSwitch()
            }
            override fun onMenuItemSelected(item: MenuItem) = false
        })
    }

    override fun onResume() {
        super.onResume()
        vpnActive = SaveDataVpnService.isRunning
        setSwitch(vpnActive)
        wireSwitch()
    }

    private fun wireSwitch() {
        vpnSwitch?.setOnCheckedChangeListener(null)
        vpnSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestVpnPermission() else doStopVpn()
        }
    }

    private fun setSwitch(on: Boolean) {
        vpnSwitch?.setOnCheckedChangeListener(null)
        vpnSwitch?.isChecked = on
        wireSwitch()
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
        setSwitch(true)
        Snackbar.make(binding.root, "VPN активирован", Snackbar.LENGTH_SHORT).show()
    }

    private fun doStopVpn() {
        startService(Intent(this, SaveDataVpnService::class.java).apply {
            action = SaveDataVpnService.ACTION_STOP
        })
        vpnActive = false
        setSwitch(false)
        Snackbar.make(binding.root, "VPN отключён", Snackbar.LENGTH_SHORT).show()
    }
}
