package com.adc.notificationrate.ble

import android.bluetooth.*
import android.content.Context
import com.adc.notificationrate.Constants
import com.adc.notificationrate.Logger
import io.reactivex.subjects.PublishSubject


class BleSocket(
        private val context: Context,
        val bleDevice: BluetoothDevice
) {

    private enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected
    }

    private var connectionState = ConnectionState.Disconnected

    private var bleGatt: BluetoothGatt? = null

    private var writeChar: BluetoothGattCharacteristic? = null

    private var readChar: BluetoothGattCharacteristic? = null

    var socketProcessor = PublishSubject.create<AdcSocketEvent>().toSerialized()


    fun open() {

        // Previously connected device.  Try to reconnect.
        /*if (bleGatt != null) {

            if (bleGatt?.connect()) {
                Logger.log("Attempting to reconnect to remote device")
                mConnectionState = ConnectionState.Connecting

            } else {
                mConnectionState = ConnectionState.Idle
                mListener.onConnectionEvent(Connection(false))
                return
            }
        }*/

        if (connectionState == ConnectionState.Connecting) {

            socketProcessor.onNext(AdcSocketEvent.Connecting)

            return
        }
        else if (connectionState == ConnectionState.Connected) {

            socketProcessor.onNext(AdcSocketEvent.Connected)

            return
        }

        connectionState = ConnectionState.Connecting

        socketProcessor.onNext(AdcSocketEvent.Connecting)

        // We want to directly connect to the device, so we are setting the autoConnect parameter to false.
        bleGatt = bleDevice.connectGatt(context, false, gattCallback)

    }

    fun send(value: String) {

        val sensorInputChannel = writeChar

        if (sensorInputChannel == null) {

            socketProcessor.onNext(AdcSocketEvent.Data.ErrorWrite)

            return
        }

        sensorInputChannel.setValue(value)

        bleGatt?.writeCharacteristic(sensorInputChannel)

    }

    fun close() {

        connectionState = ConnectionState.Disconnected

        socketProcessor.onNext(AdcSocketEvent.Disconnected)

        // Refresh the socket event subject
        socketProcessor = PublishSubject.create<AdcSocketEvent>().toSerialized()

        bleGatt?.close()

        bleGatt = null

        writeChar = null

        readChar = null

    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt,
                                             status: Int,
                                             newState: Int) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                val serviceDiscoveryStarted = bleGatt?.discoverServices() ?: false

                if (serviceDiscoveryStarted) {

                    socketProcessor.onNext(AdcSocketEvent.ServiceDiscovery)

                } else {

                    Logger.log("Attempting to start service discovery fail")

                    connectionState = ConnectionState.Disconnected

                    socketProcessor.onNext(AdcSocketEvent.Disconnected)

                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                Logger.log("Connection Dropped")

                connectionState = ConnectionState.Disconnected

                socketProcessor.onNext(AdcSocketEvent.Disconnected)

            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                Logger.log("onServicesDiscovered() success -> status: $status")

                val settingReadNotification = setupSensorCharacteristics(true)

                if (! settingReadNotification) {

                    Logger.log("Error setting read notification")

                    connectionState = ConnectionState.Disconnected

                    socketProcessor.onNext(AdcSocketEvent.Disconnected)

                }

            } else {

                Logger.log("onServicesDiscovered() failed -> status: $status")

                connectionState = ConnectionState.Disconnected

                socketProcessor.onNext(AdcSocketEvent.Disconnected)
            }

        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {

            Logger.log("onCharacteristicRead() -> status: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {

                socketProcessor.onNext(
                        AdcSocketEvent.Data.Read(characteristic.getStringValue(0))
                )

            } else {

                socketProcessor.onNext(AdcSocketEvent.Data.ErrorRead)

            }
        }

        /**
         * Callback triggered as a result of a remote characteristic notification.
         *
         * @param gatt GATT client the characteristic is associated with
         * @param characteristic Characteristic that has been updated as a result of a remote
         * notification event.
         */
        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic) {

            Logger.log("onCharacteristicChanged()")

            socketProcessor.onNext(
                    AdcSocketEvent.Data.Read(characteristic.getStringValue(0))
            )

        }

        /**
         * Callback indicating the result of a characteristic write operation.
         *
         * <p>If this callback is invoked while a reliable write transaction is
         * in progress, the value of the characteristic represents the value
         * reported by the remote device. An application should compare this
         * value to the desired value to be written. If the values don't match,
         * the application must abort the reliable write transaction.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
         * @param characteristic Characteristic that was written to the associated remote device.
         * @param status The result of the write operation {@link BluetoothGatt#GATT_SUCCESS} if the
         * operation succeeds.
         */
        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int) {

            Logger.log("onCharacteristicWrite() -> status: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {

                socketProcessor.onNext(
                        AdcSocketEvent.Data.Write(characteristic.getStringValue(0))
                )

            } else {

                socketProcessor.onNext(AdcSocketEvent.Data.ErrorWrite)

            }

        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {

            Logger.log("onReliableWriteCompleted() -> status: $status")

        }

        override fun onDescriptorRead(gatt: BluetoothGatt,
                                      descriptor: BluetoothGattDescriptor,
                                      status: Int) {

            Logger.log("onDescriptorRead() -> status: $status")

        }

        override fun onDescriptorWrite(gatt: BluetoothGatt,
                                       descriptor: BluetoothGattDescriptor,
                                       status: Int) {

            Logger.log("onDescriptorWrite() -> status: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {

                //if (connectionState == ConnectionState.Connecting) {

                    connectionState = ConnectionState.Connected

                    socketProcessor.onNext(AdcSocketEvent.Connected)
                //}

            } else {

                connectionState = ConnectionState.Disconnected

                socketProcessor.onNext(AdcSocketEvent.Disconnected)

            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {

            Logger.log("onMtuChanged() -> status: $status")

        }

    }

    private fun setupSensorCharacteristics(enabled: Boolean): Boolean {

        val bleGattLocal = bleGatt ?: return false

        val adcSensorService = bleGattLocal.getService(Constants.ADC_SENSOR_SERVICE)

        if (adcSensorService == null) {

            Logger.log("Setup Notifications fail: Sensor Service not found!")

            return false
        }

        writeChar = adcSensorService.getCharacteristic(Constants.SENSOR_INPUT_CHAR)

        if (writeChar == null) {

            Logger.log("Setup Notifications fail: SENSOR_INPUT_CHAR not found!")

            return false
        }

        val readCharLocal = adcSensorService.getCharacteristic(Constants.SENSOR_OUTPUT_CHAR)

        if (readCharLocal == null) {

            Logger.log("Setup Notifications fail: SENSOR_OUTPUT_CHAR not found!")

            return false
        }

        readChar = readCharLocal

        bleGattLocal.setCharacteristicNotification(readCharLocal, enabled)

        val descriptor = readCharLocal.getDescriptor(Constants.SENSOR_OUTPUT_CLIENT_CONF_DESC)

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        return bleGattLocal.writeDescriptor(descriptor)
    }

}

sealed class AdcSocketEvent {

    object Connecting: AdcSocketEvent()

    object ServiceDiscovery: AdcSocketEvent()

    object Connected: AdcSocketEvent()

    object Disconnected: AdcSocketEvent()

    sealed class Data: AdcSocketEvent() {

        class Read(val data: String) : Data()

        class Write(val postedValue: String) : Data()

        object ErrorRead : Data()

        object ErrorWrite: Data()
    }

}
