package com.adc.notificationrate

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.os.SystemClock


class NotificationAccessibilityService : AccessibilityService() {

    private val ONE_SECOND = 1000L

    private var notificationCounter = 0

    private var lastEventTimeStamp = 0L


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

        if (e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {

            val now = SystemClock.elapsedRealtime()

            val elapsedTime = now - lastEventTimeStamp

            lastEventTimeStamp = now

            Logger.log("========== elapsedTime = $elapsedTime")

            if (elapsedTime >= ONE_SECOND) {

                notificationCounter = 0

            } else {
                Logger.log("========== Notification Count = $notificationCounter")
                // It means we are getting active events from the notification stream
                notificationCounter ++

            }


        }

    }

    override fun onInterrupt() {
        // TODO Auto-generated method stub
    }

}
