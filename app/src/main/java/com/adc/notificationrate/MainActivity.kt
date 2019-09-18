package com.adc.notificationrate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.adc.notificationrate.ble.AdcScanError
import com.adc.notificationrate.ble.AdcScanEvent
import com.adc.notificationrate.ble.BleScanner
import com.adc.notificationrate.tester.BleTestEvent
import com.adc.notificationrate.tester.BleTester
import com.adc.notificationrate.tester.NotificationTestCallback
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var networkDisposables = CompositeDisposable()

    private var isSubscribedToNetworkTest = false

    private var bleScanDisposables = CompositeDisposable()

    private var isSubscribedToBleScan = false

    private var bleSocketDisposables = CompositeDisposable()

    private var isSubscribedToBleSocket = false

    private var inRangeDevices = listOf<ScanResult>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initNotificationCenter()

        renderNotificationTestView()

        renderNetworkTestView()

        renderBleScanButtonView()

        renderBleConnectButtonView()

    }

    override fun onResume() {
        super.onResume()

        BgApplication.instance.startFgService()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == Constants.REQ_CODE_ENABLE_BT) {

            refreshBleScanObserver(BgApplication.instance.bleTester.bleScanner)

        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == Constants.REQ_CODE_PERMISSION_LOCATION) {

            refreshBleScanObserver(BgApplication.instance.bleTester.bleScanner)

        }

    }

    override fun onDestroy() {
        super.onDestroy()

        networkDisposables.clear()

        isSubscribedToNetworkTest = false

        bleScanDisposables.clear()

        isSubscribedToBleScan = false

        bleSocketDisposables.clear()

        isSubscribedToBleSocket = false

    }

    private fun initNotificationCenter() {

        val notificationManagerCompat = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT >= 26) {

            var notificationChannel = notificationManagerCompat.getNotificationChannel(Constants.ChannelIdTestLimit)

            if (notificationChannel == null) {

                notificationChannel = NotificationChannel(
                        Constants.ChannelIdTestLimit,
                        "Channel to post high priority notifications to test the Notification Rate limit",
                        NotificationManager.IMPORTANCE_HIGH
                )

                notificationChannel.setSound(null, null)

                notificationManagerCompat.createNotificationChannel(notificationChannel)

            }

        }

    }

    private fun renderNotificationTestView() {

        val isTestRunning = BgApplication.instance.notificationTester.isTestRunning.get()

        if (isTestRunning) {

            with(BgApplication.instance.notificationTester) {

                batchCapInput.setText(batchCap.toString(10))

                intervalInput.setText(intervalMillis.toString(10))

                repeatInput.setText(repeatMillis.div(1000).toInt().toString(10))

                notificationPostedText.text = notificationPostedCount.toString(10)

                notificationTestCallback = notificationTestCB

            }

            startNotificationTestBtn.text = "Stop"

            startNotificationTestBtn.setOnClickListener {

                BgApplication
                        .instance
                        .notificationTester
                        .stopTest()

            }

        } else {

            startNotificationTestBtn.text = "Start"

            startNotificationTestBtn.setOnClickListener {

                val batchCap = batchCapInput.text.toString().toInt()

                val intervalMillis = intervalInput.text.toString().toLong()

                val repeatMillis = repeatInput.text.toString().toInt().times(1000L)

                notificationPostedText.text = "0"

                BgApplication
                        .instance
                        .notificationTester
                        .startTest(
                                batchCap,
                                intervalMillis,
                                repeatMillis,
                                notificationTestCB
                        )

            }

        }

    }

    private var notificationTestCB = object : NotificationTestCallback {

        override fun onServiceDisabled() {

            AlertDialog.Builder(this@MainActivity)
                    .setMessage(R.string.accessibility_service_disabled_description)
                    .setPositiveButton(
                            "Go",
                            { dialog, _ ->

                                dialog.dismiss()

                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).also {

                                    startActivity(it)

                                }

                            }
                    )
                    .create()
                    .show()

        }

        override fun onTestStart() {

            renderNotificationTestView()

        }

        override fun onRepeatBurst(timeLeft: Int) {

            timeLeftText.text = timeLeft.toString(10)

        }

        override fun onCounterUpdate(count: Int) {

            notificationPostedText.text = count.toString(10)

        }

        override fun onTestStop() {

            renderNotificationTestView()

        }

    }

    private fun renderNetworkTestView() {

        if (isSubscribedToNetworkTest) {

            startNetworkTestBtn.text = "Stop"

            startNetworkTestBtn.setOnClickListener {

                networkDisposables.clear()

                isSubscribedToNetworkTest = false

                renderNetworkTestView()

            }

        } else {

            startNetworkTestBtn.text = "Start"

            startNetworkTestBtn.setOnClickListener {

                val subscription
                        = BgApplication
                        .instance
                        .networkTester
                        .startTest(10*1000)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe (
                                {

                                    networkRequestText.text = it.toString()

                                },
                                {

                                    networkRequestText.text = it.message

                                }
                        )

                networkDisposables.add(subscription)

                isSubscribedToNetworkTest = true

                renderNetworkTestView()
            }

        }

    }

    private fun renderBleScanButtonView() {

        val bleScanner = BgApplication
                .instance
                .bleTester
                .bleScanner

        if (bleScanner.isScanning.get()) {

            startScanTestBtn.text = "Stop Scan"

            if (! isSubscribedToBleScan) {

                refreshBleScanObserver(bleScanner)
            }

            startScanTestBtn.setOnClickListener {

                bleScanner.stopScan()

            }

        } else {

            startScanTestBtn.text = "Start Scan"

            scanResultText.text = "-"

            startScanTestBtn.setOnClickListener {

                refreshBleScanObserver(bleScanner)

            }

        }

    }

    private fun refreshBleScanObserver(bleScanner: BleScanner) {

        isSubscribedToBleScan = true

        // Remove previous pipes instances
        bleScanDisposables.clear()

        val disposable = bleScanner
                .scan()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { adcScan ->

                            when (adcScan) {

                                is AdcScanEvent.ScanStart -> {

                                    scanResultText.text = "Scanning ..."

                                    renderBleScanButtonView()

                                }

                                is AdcScanEvent.Update -> {

                                    inRangeDevices = adcScan.currentScans

                                    var currentAddressList = ""

                                    for (scan in adcScan.currentScans) {

                                        currentAddressList =
                                                currentAddressList
                                                        .plus(scan.device.address)
                                                        .plus(",")
                                    }

                                    scanResultText.text = currentAddressList

                                }

                                is AdcScanEvent.Error -> {

                                    scanResultText.text = adcScan.scanError.info

                                    val scanError = adcScan.scanError

                                    when (scanError) {

                                        is AdcScanError.PermissionLocation -> {

                                            requestLocationPermission()

                                        }

                                        is AdcScanError.BluetoothOff -> {

                                            requestBluetoothOn()

                                        }

                                        is AdcScanError.BleNotAvailable -> {
                                            // Nothing we can do
                                        }

                                        is AdcScanError.Internal -> {
                                            // Nothing we can do
                                        }

                                    }

                                }

                            }

                        },
                        { th ->

                            scanResultText.text = th.message

                        },
                        {
                            bleScanDisposables.clear()

                            renderBleScanButtonView()
                        }
                )

        bleScanDisposables.add(disposable)

    }

    private fun requestLocationPermission() {

        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                Constants.REQ_CODE_PERMISSION_LOCATION)

    }

    private fun requestBluetoothOn() {

        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

        startActivityForResult(enableBtIntent, Constants.REQ_CODE_ENABLE_BT)

    }

    private fun renderBleConnectButtonView() {

        val bleTester = BgApplication.instance.bleTester

        val isTesting = bleTester.isTesting

        if (isTesting) {

            startBleTestBtn.text = "Stop Test"

            startBleTestBtn.setOnClickListener {

                bleTester.stopTest()

            }

            if (! isSubscribedToBleSocket) {

                subscribeToBleSocketEvents(bleTester)

            }

        } else {

            startBleTestBtn.text = "Start Test"

            startBleTestBtn.setOnClickListener {

                if (inRangeDevices.isNotEmpty()) {

                    val sensorDevice = inRangeDevices[0]

                    subscribeToBleSocketEvents(bleTester)

                    bleTester.startTest(sensorDevice.device, 5000)

                } else {

                    Toast.makeText(
                            this,
                            "No device with UART Service UUID has been scanned",
                            Toast.LENGTH_SHORT
                    ).show()

                }

            }

        }

    }

    private fun subscribeToBleSocketEvents(bleTester: BleTester) {

        val disposable
                = bleTester
                .eventPipe()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { event ->

                            when (event) {

                                is BleTestEvent.Start -> {

                                    bleResultText.text = "Ble Test Starting ..."

                                    renderBleConnectButtonView()

                                }

                                is BleTestEvent.Update -> {

                                    bleResultText.text = event.connectionStatus

                                }

                                is BleTestEvent.End -> {

                                    bleResultText.text = "-"

                                }

                            }

                        },
                        {
                            th -> bleResultText.text = th.message
                        },
                        {
                            renderBleConnectButtonView()
                        }
                )


        bleSocketDisposables.add(disposable)

    }

}
