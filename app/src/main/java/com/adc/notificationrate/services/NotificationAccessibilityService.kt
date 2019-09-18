package com.adc.notificationrate.services

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import com.adc.notificationrate.Logger
import com.adc.notificationrate.execution.BgScheduledExecutor
import com.adc.notificationrate.execution.PeriodicRunnable


class NotificationAccessibilityService : AccessibilityService() {

    private var notificationCounter = 0

    private var lastEventTimeStamp = 0L

    private var isClientReceiverRegistered = false

    private val clientRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            intent?.run {

                if (INTENT_ACTION_NOTIFICATION_TEST_RESET.equals(action, true)) {

                    notificationCounter = 0

                }

            }

        }
    }

    companion object {

        val INTENT_ACTION_NOTIFICATION_TEST_RESET = "com.adc.notificationrate.action.notification.test.reset"

        val INTENT_ACTION_NOTIFICATION_COUNT_UPDATE = "com.adc.notificationrate.action.notification.test.count"

    }

    override fun onCreate() {
        super.onCreate()

        IntentFilter()
                .apply {

                    addAction(INTENT_ACTION_NOTIFICATION_TEST_RESET)

                }.also {

                    registerReceiver(clientRequestReceiver, it)

                    isClientReceiverRegistered = true

                }

    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Logger.log("========== AccessibilityService Connected")

        val info = AccessibilityServiceInfo()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.notificationTimeout = 1
        info.packageNames = arrayOf("com.adc.notificationrate")

        serviceInfo = info

        if (isEmulator()) {

            PeriodicRunnable(
                    BgScheduledExecutor.instance,// MainScheduledExecutor.instance,
                    {
                        increaseCounter()
                        broadcastCounter()
                    },
                    3000
            ).also { it.start() }

        }

    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {

        Logger.log("========== onAccessibilityEvent: ${e.eventType}")

        if (e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {

            val now = SystemClock.elapsedRealtime()

            val elapsedTime = now - lastEventTimeStamp

            lastEventTimeStamp = now

            Logger.log("========== elapsedTime = $elapsedTime")

            notificationCounter ++

            broadcastCounter()

        }

    }

    override fun onInterrupt() {
        Logger.log("========== NotificationAccessibilityService::onInterrupt()")
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isClientReceiverRegistered) {
            unregisterReceiver(clientRequestReceiver)
        }

    }

    private fun increaseCounter() {

        if (notificationCounter == 100) {
            notificationCounter = 0
        }

        notificationCounter ++

    }

    private fun broadcastCounter() {

        Intent().also { intent ->
            intent.action = INTENT_ACTION_NOTIFICATION_COUNT_UPDATE
            intent.putExtra("notificationCount", notificationCounter)
            sendBroadcast(intent)
        }

    }

    private fun isEmulator(): Boolean {

        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);

    }

}
