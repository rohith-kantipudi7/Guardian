package com.guardian.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.data.settings.EmergencyContact
import com.guardian.app.domain.LinkState
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.SectionCard
import com.guardian.app.ui.theme.Muted

@Composable
fun SettingsScreen(vm: GuardianViewModel, onPair: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    var backendUrl by remember(s.backendUrl) { mutableStateOf(s.backendUrl) }
    var virtualUrl by remember(s.virtualDeviceUrl) { mutableStateOf(s.virtualDeviceUrl) }
    var deviceId by remember(s.deviceId) { mutableStateOf(s.deviceId) }
    var vehicleName by remember(s.vehicleName) { mutableStateOf(s.vehicleName) }
    var registration by remember(s.vehicleRegistration) { mutableStateOf(s.vehicleRegistration) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SectionCard("Vehicle device") {
            listOf(DeviceSource.NONE, DeviceSource.CLOUD, DeviceSource.VIRTUAL).forEach { src ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(deviceSource = src) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.deviceSource == src, onClick = { vm.updateSettings { it.copy(deviceSource = src) } })
                    Column(Modifier.weight(1f)) {
                        Text(src.label, fontWeight = FontWeight.Bold)
                        Text(src.detail, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Button(onClick = onPair, Modifier.fillMaxWidth()) { Text("Connect") }
        }

        SectionCard("Servers") {
            OutlinedTextField(backendUrl, { backendUrl = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(virtualUrl, { virtualUrl = it }, label = { Text("Virtual device URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Emulator: 10.0.2.2 is the laptop. Real phone: use the laptop's Wi-Fi IP.", color = Muted, fontSize = 12.sp)
            Button(onClick = { vm.updateSettings { it.copy(backendUrl = backendUrl, virtualDeviceUrl = virtualUrl) } }) { Text("Save") }
        }

        SectionCard("Vehicle") {
            if (link == LinkState.CONNECTED) {
                OutlinedTextField(vehicleName, { vehicleName = it }, label = { Text("Vehicle name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(registration, { registration = it }, label = { Text("Registration") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    vm.updateSettings { it.copy(vehicleName = vehicleName, vehicleRegistration = registration, deviceId = deviceId) }
                }) { Text("Save") }
            } else {
                Text("Connect your Guardian device to add vehicle details.", color = Muted, fontSize = 13.sp)
            }
        }

        SectionCard("Emergency contacts") {
            if (s.contacts.isEmpty()) Text("Family members who get the crash alert with your location.", color = Muted, fontSize = 13.sp)
            s.contacts.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.name} · ${c.phone}", Modifier.weight(1f))
                    TextButton(onClick = { vm.updateSettings { st -> st.copy(contacts = st.contacts.filterIndexed { j, _ -> j != i }) } }) {
                        Text("Remove")
                    }
                }
            }
            OutlinedTextField(contactName, { contactName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(contactPhone, { contactPhone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                enabled = contactName.isNotBlank() && contactPhone.isNotBlank(),
                onClick = {
                    vm.updateSettings { it.copy(contacts = it.contacts + EmergencyContact(contactName.trim(), contactPhone.trim())) }
                    contactName = ""
                    contactPhone = ""
                },
            ) { Text("Add contact") }
            Text(
                "Guardian alerts them automatically with your location and crash details.",
                color = Muted,
                fontSize = 12.sp,
            )
        }

        SectionCard("App") {
            ToggleRow("Crash alert notifications", s.notifications) { v -> vm.updateSettings { it.copy(notifications = v) } }
            ToggleRow("Show device test controls (demo)", s.demoMode) { v -> vm.updateSettings { it.copy(demoMode = v) } }
            TextButton(onClick = { vm.clearHistory() }) { Text("Clear local history") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
