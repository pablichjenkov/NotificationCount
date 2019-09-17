package com.adc.notificationrate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import kotlinx.android.synthetic.main.activity_main.*
import android.content.Intent
import android.provider.Settings
import com.adc.notificationrate.tester.NotificationTestCallback
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers


class MainActivity : AppCompatActivity() {

    private var disposables = CompositeDisposable()

    private var isSubscribedToNetworkTest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initNotificationCenter()

        renderNotificationTestView()

        renderNetworkTestView()

    }

    override fun onResume() {
        super.onResume()

        BgApplication.instance.startFgService()

    }

    override fun onDestroy() {
        super.onDestroy()

        disposables.clear()

        isSubscribedToNetworkTest = false

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

                disposables.clear()

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

                disposables.add(subscription)

                isSubscribedToNetworkTest = true

                renderNetworkTestView()
            }

        }

    }

}
