package com.adc.notificationrate.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.adc.notificationrate.Constants
import com.adc.notificationrate.Logger
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null

    val isScanning = AtomicBoolean(false)

    fun scan(): Flowable<AdcScanEvent> {

        return Flowable.create (
                { emitter ->

                    when (val readyResult = checkReady()) {

                        is AdcScanError.NoError -> {

                            bleScanner = bluetoothAdapter?.bluetoothLeScanner

                        }

                        else -> {

                            emitter.onNext(AdcScanEvent.Error(readyResult))

                            emitter.onComplete()

                            return@create
                        }

                    }

                    emitter.setCancellable {

                        Logger.log("Unsubscribing emmiter: $emitter")

                        adcScanCallback.removeSubscriber(emitter)
                    }

                    adcScanCallback.addSubscriber(emitter)

                    // Guard against duplicate call to scan while it is scanning already
                    if (isScanning.getAndSet(true)) {

                        emitter.onNext(AdcScanEvent.ScanStart)

                        return@create
                    }

                    bleScanner?.startScan(
                            buildScanFilters(),
                            buildScanSettings(),
                            adcScanCallback)

                    emitter.onNext(AdcScanEvent.ScanStart)

                    Flowable
                            .timer(40, TimeUnit.SECONDS)
                            .subscribe(
                                    {
                                        stopScan()
                                    }
                                    ,{
                                        // ignore
                                    }
                            )

                },
                BackpressureStrategy.BUFFER
        )

    }

    fun stopScan() {

        if (isScanning.getAndSet(false)) {

            bleScanner?.stopScan(adcScanCallback)

            adcScanCallback.complete()

        }

    }

    fun checkReady(): AdcScanError {

        val locationPermission
                = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {

            return AdcScanError.PermissionLocation

        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {

            return AdcScanError.BluetoothOff

        }

        val bleAvailable
                = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        if (! bleAvailable) {

            return AdcScanError.BleNotAvailable

        }

        return AdcScanError.NoError
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val scanFilters = ArrayList<ScanFilter>()

        val builder = ScanFilter.Builder()
        // TODO(Pablo): Creating a Configuration param to pass the desired UUID to connect
        // Comment out the below line to see all BLE devices around you
        builder.setServiceUuid(ParcelUuid(Constants.ADC_SENSOR_SERVICE))
        scanFilters.add(builder.build())

        return scanFilters
    }

    private fun buildScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setReportDelay(0)
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        return builder.build()
    }

    private val adcScanCallback = object : ScanCallback() {

        private var currentScans = mutableListOf<ScanResult>()

        private var subscriberList = mutableListOf<FlowableEmitter<AdcScanEvent>>()


        override fun onScanResult(callbackType: Int, result: ScanResult?) {

            if (result == null) {
                return
            }

            if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {

                val newScanList = currentScans.filter { prevResult ->

                    val filterIn = prevResult.device.address != result.device.address

                    if (! filterIn) {
                        Logger.log("Device: ${result.device.address} went away.")
                    }

                    filterIn

                }

                currentScans = newScanList.toMutableList()

            } else {

                var sameAddressCount = 0

                for (prevScan in currentScans) {

                    if (prevScan.device.address == result.device.address) {

                        Logger.log("Device: ${result.device.address} already in.")

                        sameAddressCount ++

                        break

                    }

                }

                if (sameAddressCount == 0) {

                    currentScans.add(result)

                }

            }

            subscriberList.forEach { emitter ->
                emitter.onNext(AdcScanEvent.Update(currentScans))
            }

        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {

            Logger.log("onBatchScanResults()")

            results?.let {

                currentScans = it

                subscriberList.forEach { emitter ->
                    emitter.onNext(AdcScanEvent.Update(results))
                }

            }

        }

        override fun onScanFailed(errorCode: Int) {

            Logger.log("onBatconScanFailed: $errorCode")

            subscriberList.forEach { emitter ->

                emitter.onNext(
                        AdcScanEvent.Error(
                                AdcScanError.Internal(errorCode)
                        )
                )

            }

            complete()

        }

        fun addSubscriber(emitter: FlowableEmitter<AdcScanEvent>) {
            subscriberList.add(emitter)
        }

        fun removeSubscriber(emitter: FlowableEmitter<AdcScanEvent>) {
            subscriberList.remove(emitter)
        }

        fun complete() {

            subscriberList.forEach { emitter ->
                emitter.onComplete()
            }

            subscriberList = mutableListOf()

            currentScans = mutableListOf()

        }

    }

}

sealed class AdcScanEvent {

    object ScanStart: AdcScanEvent()

    class Update(val currentScans: List<ScanResult>): AdcScanEvent()

    class Error(val scanError: AdcScanError): AdcScanEvent()

}

sealed class AdcScanError(val info: String) {

    object NoError: AdcScanError("No Error")

    object PermissionLocation: AdcScanError("Need Location Permission")

    object BleNotAvailable: AdcScanError("Bluetooth Low Energy not supported")

    object BluetoothOff: AdcScanError("Bluetooth Adapter is off")

    class Internal(val scanFailedCode: Int): AdcScanError("Internal Error: $scanFailedCode")

}