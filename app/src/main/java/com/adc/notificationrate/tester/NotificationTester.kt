package com.adc.notificationrate.tester

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adc.notificationrate.Constants
import com.adc.notificationrate.Logger
import com.adc.notificationrate.R
import com.adc.notificationrate.execution.BgScheduledExecutor
import com.adc.notificationrate.execution.PeriodicRunnable
import com.adc.notificationrate.services.NotificationAccessibilityService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil


class NotificationTester(private val application: Application) {

    private val TEN_MINUTES = 10*60*1000

    var isTestRunning = AtomicBoolean(false)

    var startTestTimeStamp = 0L

    var batchCap = 0

    var intervalMillis = 0L

    var repeatMillis = 0L

    var notificationTestCallback: NotificationTestCallback? = null

    var intervalCallsCounter = 0

    var notificationPostedCount = 0

    private val notificationManagerCompat = NotificationManagerCompat.from(application)

    private val mainLoopHandler = Handler(Looper.getMainLooper())

    private var intervalTimer: PeriodicRunnable? = null

    private var repeatTimer: PeriodicRunnable? = null

    private var isSubscribedToNotificationUpdates = false

    private var notificationSentCount = 0

    private val notificationUpdatesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            intent?.let {

                notificationPostedCount = it.getIntExtra("notificationCount", -1)

                mainLoopHandler.post { notificationTestCallback?.onCounterUpdate(notificationPostedCount) }

            }

        }

    }

    fun startTest(
            batchCap: Int,
            intervalMillis: Long,
            repeatMillis: Long,
            notificationTestCallback: NotificationTestCallback
    ) {

        if (isTestRunning.get()) {
            return
        }

        if (! isAccessibilityServiceEnabled()) {

            notificationTestCallback.onServiceDisabled()

            return
        }

        startTestTimeStamp = System.currentTimeMillis()

        this.batchCap = batchCap

        this.intervalMillis = intervalMillis

        this.repeatMillis = repeatMillis

        this.notificationTestCallback = notificationTestCallback

        application.sendBroadcast(
                Intent(NotificationAccessibilityService.INTENT_ACTION_NOTIFICATION_TEST_RESET)
        )

        subscribeToNotificationCounterUpdates()

        startRepeatTimer()

        isTestRunning.set(true)

    }

    fun stopTest() {

        isTestRunning.set(false)

        intervalTimer?.stop()

        repeatTimer?.stop()

        unSubscribeToNotificationCounterUpdates()

        startTestTimeStamp = 0L

        batchCap = 0

        intervalMillis = 0L

        repeatMillis = 0L

        intervalCallsCounter = 0

        notificationPostedCount = 0

        notificationSentCount = 0

        notificationTestCallback?.onTestStop()

        notificationTestCallback = null
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val accessibilityManager
                = application.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager

        val accessibilityServices =
                accessibilityManager
                        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        Logger.log("Accessibility Service List size: " + accessibilityServices.size)

        var serviceFoundCount = 0

        for (info in accessibilityServices) {

            Logger.log("Accessibility Service: " + info.id)

            if (info.id.contains(application.packageName, true)) {
                serviceFoundCount ++
            }

        }

        return serviceFoundCount > 0
    }

    private fun startRepeatTimer() {

        mainLoopHandler.post { notificationTestCallback?.onTestStart() }

        repeatTimer = PeriodicRunnable(
                BgScheduledExecutor.instance,// MainScheduledExecutor.instance,
                { repeatCB() },
                repeatMillis
        ).also { it.start() }

    }

    private fun startIntervalTimer() {

        intervalTimer = PeriodicRunnable(
                BgScheduledExecutor.instance,// MainScheduledExecutor.instance,
                { intervalCB() },
                intervalMillis
        ).also { it.start() }

    }

    private fun repeatCB() {

        val now = System.currentTimeMillis()

        val elapsedTime = now - startTestTimeStamp

        val timeLeft = (TEN_MINUTES - elapsedTime) / 1000.00

        mainLoopHandler.post { notificationTestCallback?.onRepeatBurst(ceil(timeLeft).toInt()) }

        intervalCallsCounter = 0

        startIntervalTimer()

        if (elapsedTime >= TEN_MINUTES) { // 10 minutes since start return

            repeatTimer?.stop()

            isTestRunning.set(false)

            mainLoopHandler.post { notificationTestCallback?.onTestStop() }

        }

    }

    private fun intervalCB() {

        if (intervalCallsCounter >= batchCap) {

            intervalTimer?.stop()

            return

        }

        intervalCallsCounter ++

        notificationSentCount ++

        postNotification(notificationSentCount, notificationSentCount, startTestTimeStamp)

    }

    private fun postNotification(counter: Int, notificationId: Int, downloadStartTime: Long) {

        val notificationBuilder = NotificationCompat.Builder(application, Constants.ChannelIdTestLimit)
                .setContentTitle("Notification Counter")
                .setContentText("$counter%")
                //.setSmallIcon(android.R.drawable.stat_sys_download)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setWhen(downloadStartTime)
                .setColor(ContextCompat.getColor(application, R.color.colorAccent))
                //.setProgress(100 /* max */, progress, false /* indeterminateProgress */)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)

        if (Build.VERSION.SDK_INT >= 21) {
            notificationBuilder.setVibrate(LongArray(0))
        }

        notificationManagerCompat.notify(notificationId + 1000, notificationBuilder.build())

    }

    private fun subscribeToNotificationCounterUpdates() {

        application.registerReceiver(
                notificationUpdatesReceiver,
                IntentFilter(NotificationAccessibilityService.INTENT_ACTION_NOTIFICATION_COUNT_UPDATE)
        )

        isSubscribedToNotificationUpdates = true

    }

    private fun unSubscribeToNotificationCounterUpdates() {

        if (isSubscribedToNotificationUpdates) {

            application.unregisterReceiver(notificationUpdatesReceiver)

            isSubscribedToNotificationUpdates = false

        }

    }

}


interface NotificationTestCallback {

    fun onServiceDisabled()

    fun onTestStart()

    fun onRepeatBurst(timeLeft: Int)

    fun onCounterUpdate(count: Int)

    fun onTestStop()

}