package com.adc.notificationrate.ble

import android.app.Application
import android.bluetooth.BluetoothDevice


class BleCentralService(val application: Application) {

    private val currentConnections = mutableMapOf<String, BleSocket>()

    fun createSocket(bleDevice: BluetoothDevice): BleSocket {

        var connection = getExistingSocket(bleDevice)

        if (connection == null) {

            connection = BleSocket(application, bleDevice)

            currentConnections[bleDevice.address] = connection

        }

        return connection

    }

    fun getExistingSocket(bleDevice: BluetoothDevice): BleSocket? {

        return currentConnections[bleDevice.address]

    }

}