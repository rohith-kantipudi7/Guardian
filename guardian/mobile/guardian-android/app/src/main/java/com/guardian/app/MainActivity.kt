package com.guardian.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.guardian.app.data.ble.BlePermissions
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.ui.GuardianNavHost
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.theme.GuardianTheme
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as GuardianApp).container }

    private val vm: GuardianViewModel by viewModels {
        viewModelFactory {
            initializer { GuardianViewModel(container.repository, container.device, container.settings, container.notifier, container.phoneSensing, container.onDeviceAi, container.conversationCapture) }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.connect() }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun pair() {
        val needsBle = container.settings.settings.value.deviceSource == DeviceSource.BLE && !BlePermissions.granted(this)
        if (needsBle) {
            val perms = BlePermissions.required().toMutableList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            vm.connect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent {
            GuardianTheme {
                GuardianNavHost(vm, onPair = ::pair)
            }
        }
    }
}
