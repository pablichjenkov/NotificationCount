package com.adc.notificationrate

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil


class NotificationPoster(private val application: Application) {

    private val TEN_MINUTES = 10*60*1000

    private lateinit var accessibilityManager: AccessibilityManager

    private val notificationManagerCompat = NotificationManagerCompat.from(application)

    private val mainLoopHandler = Handler(Looper.getMainLooper())

    private var intervalTimer: PeriodicRunnable? = null

    private var repeatTimer: PeriodicRunnable? = null

    var isTestRunning = AtomicBoolean(false)

    var startTestTimeStamp = 0L

    var batchCap = 0

    var intervalMillis = 0L

    var repeatMillis = 0L

    var notificationTestCallback: NotificationTestCallback? = null

    var intervalCallsCounter = 0

    var notificationCounter = 0

    fun startTest(
            batchCap: Int,
            intervalMillis: Long,
            repeatMillis: Long,
            notificationTestCallback: NotificationTestCallback
    ) {

        if (isTestRunning.getAndSet(true)) {
            return
        }

        startTestTimeStamp = System.currentTimeMillis()

        this.batchCap = batchCap

        this.intervalMillis = intervalMillis

        this.repeatMillis = repeatMillis

        this.notificationTestCallback = notificationTestCallback

        startRepeatTimer()

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

    fun repeatCB() {

        val now = System.currentTimeMillis()

        val elapsedTime = now - startTestTimeStamp

        val timeLeft = (TEN_MINUTES - elapsedTime) / 1000.00

        mainLoopHandler.post { notificationTestCallback?.onRepeatBurst(ceil(timeLeft).toInt()) }

        intervalCallsCounter = 0

        startIntervalTimer()

        if (elapsedTime >= TEN_MINUTES) { // 10 minutes since start return

            repeatTimer?.stop()

            isTestRunning.set(false)

            mainLoopHandler.post { notificationTestCallback?.onLastBurst() }

        }

    }

    fun intervalCB() {

        if (intervalCallsCounter >= batchCap) {

            intervalTimer?.stop()

            return

        }

        intervalCallsCounter ++

        notificationCounter ++

        postNotification(notificationCounter, notificationCounter, startTestTimeStamp)

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

}

interface NotificationTestCallback {

    fun onTestStart()

    fun onRepeatBurst(timeLeft: Int)

    fun onLastBurst()

}