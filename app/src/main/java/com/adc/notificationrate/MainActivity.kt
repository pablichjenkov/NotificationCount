package com.adc.notificationrate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityManager: AccessibilityManager

    private lateinit var notificationManagerCompat: NotificationManagerCompat


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initNotificationCenter()

        accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        printEnabledAccessibilityServices()

        setupView()

    }

    override fun onResume() {
        super.onResume()

        BgApplication.instance.startFgService()

    }

    private fun initNotificationCenter() {

        notificationManagerCompat = NotificationManagerCompat.from(this)

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

    private fun setupView() {

        val isTestRunning = BgApplication.instance.notificationPoster.isTestRunning.get()

        if (isTestRunning) {

            startTestBtn.isEnabled = false

            with(BgApplication.instance.notificationPoster) {

                batchCapInput.setText(batchCap.toString())

                intervalInput.setText(intervalMillis.toString(10))

                repeatInput.setText(repeatMillis.toString(10))

                notificationTestCallback = notificationTestCB

            }

        } else {

            startTestBtn.isEnabled = true

            startTestBtn.setOnClickListener {

                val batchCap = batchCapInput.text.toString().toInt()

                val intervalMillis = intervalInput.text.toString().toLong()

                val repeatMillis = repeatInput.text.toString().toInt().times(1000L)

                BgApplication
                        .instance
                        .notificationPoster
                        .startTest(
                                batchCap,
                                intervalMillis,
                                repeatMillis,
                                notificationTestCB
                        )

            }

        }

    }

    var notificationTestCB = object : NotificationTestCallback {

        override fun onTestStart() {

            startTestBtn.isEnabled = false

        }

        override fun onRepeatBurst(timeLeft: Int) {

            timeLeftText.text = timeLeft.toString(10)

        }

        override fun onLastBurst() {

            startTestBtn.isEnabled = true

        }

    }

    private fun printEnabledAccessibilityServices() {

        val accessibilityServices =
                accessibilityManager
                        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        Logger.log("Accessibility Service List size: " + accessibilityServices.size)

        for (info in accessibilityServices) {

            Logger.log("Accessibility Service: " + info.id)

        }

    }

}
