package com.adc.notificationrate.tester

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import com.adc.notificationrate.BgApplication
import com.adc.notificationrate.Logger
import com.adc.notificationrate.ble.AdcSocketEvent
import com.adc.notificationrate.ble.BleCentralService
import com.adc.notificationrate.ble.BleScanner
import com.adc.notificationrate.ble.BleSocket
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class BleTester(private val application: Application) {

    var bleScanner = BleScanner(application)

    var bleCentralService = BleCentralService(application)

    var bleSocketDisposable: Disposable? = null

    var bleTimerDisposable: Disposable? = null

    var isTesting = false

    private var bleSocket: BleSocket? = null

    private var lastSubscribed: FlowableEmitter<BleTestEvent>? = null

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var repeatMillis = 5000L

    private var bleCount = 10

    private var successCount = 0

    private var failureCount = 0


    fun eventPipe(): Flowable<BleTestEvent> {

        return Flowable.create(
                { emitter ->

                    lastSubscribed = emitter

                    // When the emitter un-subscribes clear its reference
                    emitter.setCancellable { lastSubscribed = null }

                },
                BackpressureStrategy.BUFFER
        )

    }

    fun startTest(
            deviceInTest: BluetoothDevice,
            repeatMillis: Long
    ) {

        this.repeatMillis = repeatMillis

        if (isTesting) {

            return

        }

        isTesting = true

        lastSubscribed?.onNext(BleTestEvent.Start)

        val socket = bleCentralService.createSocket(deviceInTest)

        bleSocket = socket

        socket.socketProcessor
                .subscribeOn(Schedulers.io())
                .subscribe(newSocketObserver(socket))

    }

    fun stopTest() {

        bleSocket?.let {

            isTesting = false

            it.close()

            bleSocket = null

            bleSocketDisposable?.dispose()

            bleTimerDisposable?.dispose()

            lastSubscribed?.onNext(BleTestEvent.End)

            lastSubscribed?.onComplete()

            lastSubscribed = null
        }

    }

    private fun newSocketObserver(bleSocket: BleSocket): Observer<AdcSocketEvent> {

        return object : Observer<AdcSocketEvent> {

            override fun onSubscribe(disposable: Disposable) {

                bleSocketDisposable = disposable

                // Little delay to avoid an the PublishSubject inside BleSocket be ready to emmit.
                Handler(Looper.getMainLooper()).postDelayed({ bleSocket.open() }, 2)

            }

            override fun onNext(event: AdcSocketEvent) {
                reduceSocketEvent(bleSocket, event)
            }

            override fun onError(th: Throwable) {
                stopTest()
            }

            override fun onComplete() {
                stopTest()
            }

        }

    }

    private fun reduceSocketEvent(bleSocket: BleSocket, event: AdcSocketEvent) {

        when (event) {

            AdcSocketEvent.Connecting -> {

                Logger.log("Connecting ...")

                lastSubscribed?.onNext(
                        BleTestEvent.Update(
                                "Connecting ...",
                                0,
                                0,
                                "0")
                )

            }

            AdcSocketEvent.ServiceDiscovery -> {

                lastSubscribed?.onNext(
                        BleTestEvent.Update(
                                "Service Discovery ...",
                                0,
                                0,
                                "0")
                )

            }

            AdcSocketEvent.Connected -> {

                lastSubscribed?.onNext(
                        BleTestEvent.Update(
                                "Connected",
                                0,
                                0,
                                "0")
                )

                BgApplication.instance.bleTester.startTest(bleSocket.bleDevice, repeatMillis)

                bleTimerDisposable = Observable
                        .interval(2, 5, TimeUnit.SECONDS)
                        .map {

                        }
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                {
                                    bleCount ++

                                    if (bleCount >= 100) { bleCount = 10 }

                                    bleSocket.send(bleCount.toString(10))


                                },
                                {
                                    // ignore
                                }
                        )

            }

            AdcSocketEvent.Disconnected -> {

                lastSubscribed?.onNext(
                        BleTestEvent.Update(
                                "Disconnected",
                                0,
                                0,
                                "0")
                )

                stopTest()

            }

            is AdcSocketEvent.Data -> {

                when (event) {

                    is AdcSocketEvent.Data.Read -> {

                        lastSubscribed?.onNext(
                                BleTestEvent.Update(
                                        "Read success: ${event.data}",
                                        0,
                                        0,
                                        "0")
                        )

                    }

                    is AdcSocketEvent.Data.ErrorRead -> {

                        lastSubscribed?.onNext(
                                BleTestEvent.Update(
                                        "Error Reading",
                                        0,
                                        0,
                                        "0")
                        )

                    }

                    is AdcSocketEvent.Data.Write -> {

                        // if event.postedValue == writeValue

                        lastSubscribed?.onNext(
                                BleTestEvent.Update(
                                        "Write success",
                                        0,
                                        0,
                                        "0")
                        )

                    }

                    is AdcSocketEvent.Data.ErrorWrite -> {

                        lastSubscribed?.onNext(
                                BleTestEvent.Update(
                                        "Error Writing",
                                        0,
                                        0,
                                        "0")
                        )

                    }

                }

            }

        }

    }

}

sealed class BleTestEvent {

    object Start: BleTestEvent()

    data class Update(
            val connectionStatus: String,
            val successCount: Int,
            val failureCount: Int,
            val timeStamp: String
    ): BleTestEvent()

    object End: BleTestEvent()

    object Error: BleTestEvent()

}
