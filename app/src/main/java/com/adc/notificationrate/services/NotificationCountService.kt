package com.adc.notificationrate.services

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.adc.notificationrate.Logger
import android.provider.Settings


class NotificationCountService : NotificationListenerService() {

    private var notificationCounter = 0

    private var lastEventTimeStamp = 0L

    private var isClientRequestReceiverRegistered = false


    companion object {

        val INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT =
                "com.adc.notificationrate.NotificationCountService.action.resetCount"

        val INTENT_ACTION_NOTIFICATION_TEST_COUNT_UPDATE =
                "com.adc.notificationrate.NotificationCountService.event.countUpdate"

        fun getAccessToNotificationSettingsIntent(): Intent {

            val action = if (Build.VERSION.SDK_INT >= 22) {

                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS

            } else {

                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

            }

            return Intent(action)
        }

    }

    private val clientRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            intent?.run {

                if (INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT.equals(action, true)) {

                    notificationCounter = 0

                }

            }

        }
    }

    override fun onCreate() {
        super.onCreate()

        Logger.log("========== NotificationCountService::onCreate()")

        IntentFilter()
                .apply {

                    addAction(INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT)

                }.also {

                    registerReceiver(clientRequestReceiver, it)

                    isClientRequestReceiverRegistered = true

                }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Logger.log("NotificationCountService::onStartCommand()")

        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        Logger.log("NotificationCountService::onListenerConnected()")

    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        Logger.log("NotificationCountService::onListenerDisconnected()")

    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        Logger.log("NotificationCountService::onNotificationPosted() -> ${sbn?.packageName}")

        if (sbn == null) return

        if (sbn.packageName != packageName) {
            return
        }

        if (Build.VERSION.SDK_INT >= 24 && sbn.isGroup) {
            return
        }

        if (sbn.isOngoing) {
            return
        }

        val now = SystemClock.elapsedRealtime()

        val elapsedTime = now - lastEventTimeStamp

        lastEventTimeStamp = now

        Logger.log("========== elapsedTime = $elapsedTime")

        notificationCounter++

        broadcastCounter()

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)

        Logger.log("NotificationCountService::onNotificationRemoved()")

    }

    override fun onNotificationChannelModified(
            pkg: String?,
            user: UserHandle?,
            channel: NotificationChannel?,
            modificationType: Int
    ) {
        super.onNotificationChannelModified(pkg, user, channel, modificationType)

        Logger.log("NotificationCountService::onNotificationChannelModified()")

    }

    override fun onNotificationChannelGroupModified(
            pkg: String?,
            user: UserHandle?,
            group: NotificationChannelGroup?,
            modificationType: Int
    ) {
        super.onNotificationChannelGroupModified(pkg, user, group, modificationType)

        Logger.log("NotificationCountService::onNotificationChannelGroupModified()")

    }

    override fun onDestroy() {
        super.onDestroy()

        Logger.log("========== NotificationCountService::onDestroy()")

        if (isClientRequestReceiverRegistered) {
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