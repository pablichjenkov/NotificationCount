package com.adc.notificationrate.tester

import android.app.Application
import android.bluetooth.BluetoothDevice
import com.adc.notificationrate.ble.BleCentralService
import com.adc.notificationrate.ble.BleScanner
import io.reactivex.Observable
import io.reactivex.functions.Function
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class BleTester(private val application: Application) {

    var bleScanner = BleScanner(application)

    var bleCentralService = BleCentralService(application)

    var deviceInTest: BluetoothDevice? = null

    var isTesting = false

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var successCount = 0

    private var failureCount = 0



    fun startTest(
            deviceInTest: BluetoothDevice,
            repeatMillis: Long
    )/*: Observable<BleRequestResp> */ {

        /*return Observable
                .interval(0, repeatMillis, TimeUnit.MILLISECONDS)
                .map {

                    val timestamp = simpleDateFormat.format(Date())

                    BleRequestResp(successCount, failureCount, timestamp)

                }.onErrorResumeNext(
                        Function {

                            val timestamp = simpleDateFormat.format(Date())

                            failureCount ++

                            Observable.just(
                                    BleRequestResp(successCount, failureCount, timestamp)
                            )

                        }
                )*/

        this.deviceInTest = deviceInTest

        isTesting = true

    }

    fun stopTest() {

        deviceInTest?.let {

            isTesting = false

            val bleSocket = bleCentralService.getExistingSocket(it)

            bleSocket?.stop()

            deviceInTest = null

        }

    }

}

class BleRequestResp(
        val successCount: Int,
        val failureCount: Int,
        val timeStamp: String
)
