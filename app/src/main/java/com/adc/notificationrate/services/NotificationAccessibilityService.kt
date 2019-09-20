package com.adc.notificationrate.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.adc.notificationrate.Logger


class NotificationAccessibilityService : AccessibilityService() {

    private var notificationCounter = 0

    private var lastEventTimeStamp = 0L

    private var isClientReceiverRegistered = false

    private val clientRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            intent?.run {

                if (INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT.equals(action, true)) {

                    notificationCounter = 0

                }

            }

        }
    }

    companion object {

        val INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT =
                "com.adc.notificationrate.NotificationAccessibilityService.action.resetCount"

        val INTENT_ACTION_NOTIFICATION_TEST_COUNT_UPDATE =
                "com.adc.notificationrate.NotificationAccessibilityService.event.countUpdate"

    }

    override fun onCreate() {
        super.onCreate()

        IntentFilter()
                .apply {

                    addAction(INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT)

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

        Logger.log("========== NotificationAccessibilityService::onDestroy()")

        if (isClientReceiverRegistered) {
            unregisterReceiver(clientRequestReceiver)
        }

    }

    private fun broadcastCounter() {

        Intent().also { intent ->
            intent.action = INTENT_ACTION_NOTIFICATION_TEST_COUNT_UPDATE
            intent.putExtra("notificationCount", notificationCounter)
            sendBroadcast(intent)
        }

    }

}
