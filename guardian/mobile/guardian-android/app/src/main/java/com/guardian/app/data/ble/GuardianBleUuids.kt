package com.guardian.app.data.ble

import java.util.UUID

/** Must match device/guardian-device/include/ble_uuids.h. "4755 4152 4449 414e" = "GUARDIAN". */
object GuardianBleUuids {
    const val DEVICE_NAME_PREFIX = "Guardian-"
    private fun uuid(n: Int): UUID = UUID.fromString("4755%04x-4152-4449-414e-000000000000".format(n))

    val SERVICE: UUID = uuid(0)
    val DEVICE_STATUS: UUID = uuid(1)
    val BATTERY: UUID = uuid(2)
    val GPS_STATUS: UUID = uuid(3)
    val CELLULAR_STATUS: UUID = uuid(4)
    val VEHICLE_STATUS: UUID = uuid(5)
    val CRASH_STATUS: UUID = uuid(6)
    val EVENT: UUID = uuid(7)
    val COMMAND: UUID = uuid(8)

    val NOTIFY_CHARACTERISTICS = listOf(DEVICE_STATUS, BATTERY, GPS_STATUS, CELLULAR_STATUS, VEHICLE_STATUS, CRASH_STATUS, EVENT)

    /** Client Characteristic Configuration Descriptor. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
