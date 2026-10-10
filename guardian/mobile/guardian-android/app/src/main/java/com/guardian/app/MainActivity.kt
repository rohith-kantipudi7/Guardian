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
            initializer { GuardianViewModel(container.repository, container.device, container.settings, container.notifier, container.phoneSensing, container.onDeviceAi, container.conversationCapture, container.dualCameraCapture, container.continuousBlackBox, container.alarmSiren) }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.connect() }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startGuardianService() }

    /** Foreground service keeps crash detection + capture + streaming alive in the background. */
    private fun startGuardianService() {
        val granted = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) com.guardian.app.service.GuardianService.start(this)
    }

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
        val media = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (media.isNotEmpty()) audioPermissionLauncher.launch(media.toTypedArray()) else startGuardianService()
        setContent {
            GuardianTheme {
                GuardianNavHost(vm, onPair = ::pair)
            }
        }
    }
}
