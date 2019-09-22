package com.adc.notificationrate.tester

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adc.notificationrate.Constants
import com.adc.notificationrate.Logger
import com.adc.notificationrate.R
import com.adc.notificationrate.execution.BgScheduledExecutor
import com.adc.notificationrate.execution.PeriodicRunnable
import com.adc.notificationrate.services.NotificationCountService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import android.text.TextUtils
import android.content.ComponentName
import android.provider.Settings
import com.adc.notificationrate.services.NotificationAccessibilityService


class NotificationTester(private val application: Application) {

    private val useAccessibilityService = false

    private val TEN_MINUTES = 10 * 60 * 1000

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

        if (useAccessibilityService) {

            if (!isAccessibilityServiceEnabled()) {

                notificationTestCallback.onAccessibilityServiceDisabled()

                return
            }

        } else {

            if (!isNotificationListenerAccessGranted()) {

                notificationTestCallback.onNotificationServiceDisabled()

                return

            }

        }

        dispatchTalkBackStatus()

        startTestTimeStamp = System.currentTimeMillis()

        this.batchCap = batchCap

        this.intervalMillis = intervalMillis

        this.repeatMillis = repeatMillis

        this.notificationTestCallback = notificationTestCallback

        application.sendBroadcast(
                if (useAccessibilityService)
                    Intent(NotificationAccessibilityService.INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT)
                else
                    Intent(NotificationCountService.INTENT_ACTION_NOTIFICATION_TEST_RESET_COUNT)
        )

        subscribeToNotificationCounterUpdates()

        startRepeatTimer()

        isTestRunning.set(true)

    }

    private fun dispatchTalkBackStatus() {

        mainLoopHandler.post {
            notificationTestCallback?.onTextToSpeechState(isTalkBackEnabled())
        }

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

    private fun isNotificationListenerAccessGranted(): Boolean {

        var result = false

        val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 27) {

            result =
                    notificationManager
                            .isNotificationListenerAccessGranted(
                                    ComponentName(application, NotificationCountService::class.java)
                            )

        } else {

            val flat = Settings.Secure.getString(application.contentResolver, "enabled_notification_listeners")

            if (!TextUtils.isEmpty(flat)) {

                val names = flat.split(":".toRegex())

                for (name in names) {

                    val cn = ComponentName.unflattenFromString(name)

                    if (cn != null) {

                        if (TextUtils.equals(application.packageName, cn.packageName)) {

                            result = true
                        }

                    }

                }

            }

        }

        return result

    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val accessibilityManager = application.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager

        val accessibilityServices =
                accessibilityManager
                        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        Logger.log("Accessibility Service List size: " + accessibilityServices.size)

        var serviceFoundCount = 0

        for (info in accessibilityServices) {

            Logger.log("Accessibility Service: " + info.id)

            if (info.id.contains(application.packageName, true)) {
                serviceFoundCount++
            }

        }

        return serviceFoundCount > 0
    }

    private fun isTalkBackEnabled(): Boolean {

        val accessibilityManager = application.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager

        if (! accessibilityManager.isEnabled) return false

        if (! accessibilityManager.isTouchExplorationEnabled) return true

        val voiceServicesMask =
                AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_AUDIBLE or
                AccessibilityServiceInfo.FEEDBACK_BRAILLE

        val accessibilityServices =
                accessibilityManager
                        .getEnabledAccessibilityServiceList(voiceServicesMask)

        return accessibilityServices.isNotEmpty()

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

            mainLoopHandler.post { stopTest() }

        }

    }

    private fun intervalCB() {

        if (intervalCallsCounter >= batchCap) {

            intervalTimer?.stop()

            return

        }

        intervalCallsCounter++

        notificationSentCount++

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

        val intentFilter = if (useAccessibilityService) {
            IntentFilter(NotificationAccessibilityService.INTENT_ACTION_NOTIFICATION_TEST_COUNT_UPDATE)
        } else {
            IntentFilter(NotificationCountService.INTENT_ACTION_NOTIFICATION_TEST_COUNT_UPDATE)
        }

        application.registerReceiver(
                notificationUpdatesReceiver,
                intentFilter
        )

        isSubscribedToNotificationUpdates = true

    }

    private fun unSubscribeToNotificationCounterUpdates() {

        if (isSubscribedToNotificationUpdates) {

            application.unregisterReceiver(notificationUpdatesReceiver)

            isSubscribedToNotificationUpdates = false

        }

    }

    fun postNotification(notificationId: Int, notification: Notification) {

        with(NotificationManagerCompat.from(application)) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationId, notification)
        }

    }

    fun getNotificationData(): String {

        val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager

        /*
        val wl = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "com.adc.bgprocess:bgApplication")


        wl.acquire()
        ..screen will stay on during this section..
        wl.release()
        */

        val batterySaverInfo = if (Build.VERSION.SDK_INT >= 21) {

            "isPowerSaveMode(Battery Saver) = ${powerManager.isPowerSaveMode} \n"

        } else "isPowerSaveMode(Battery Saver) = N/A \n"

        val dozeInfo = if (Build.VERSION.SDK_INT >= 23) {

            "isDeviceIdleMode(Doze/StandBy) = ${powerManager.isDeviceIdleMode} \n"

        } else "isDeviceIdleMode(Doze/StandBy) = N/A \n"

        val batteryIgnoreOptInfo = if (Build.VERSION.SDK_INT >= 23) {

            "isIgnoringBatteryOptimizations = ${powerManager.isIgnoringBatteryOptimizations(application.packageName)} \n"

        } else "isIgnoringBatteryOptimizations = N/A \n"


        return dozeInfo + batterySaverInfo + batteryIgnoreOptInfo + getDataSaverInfo()

    }

    private fun getDataSaverInfo(): String {

        var result = "N/A"

        if (Build.VERSION.SDK_INT >= 24) {

            val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

            connectivityManager?.apply {

                when (restrictBackgroundStatus) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> {
                        // Background data usage is blocked for this app. Wherever possible,
                        // the app should also use less data in the foreground.
                        result = "RESTRICT_BACKGROUND_STATUS_ENABLED"
                    }
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                        // The app is whitelisted. Wherever possible,
                        // the app should use less data in the foreground and background.
                        result = "RESTRICT_BACKGROUND_STATUS_WHITELISTED"
                    }
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                        // Data Saver is disabled. Since the device is connected to a
                        // metered network, the app should use less data wherever possible.
                        result = "RESTRICT_BACKGROUND_STATUS_DISABLED"
                    }
                }

            }

        }

        return "Data Saver = $result \n"
    }

}


interface NotificationTestCallback {

    fun onNotificationServiceDisabled()

    fun onAccessibilityServiceDisabled()

    fun onTextToSpeechState(status: Boolean)

    fun onTestStart()

    fun onRepeatBurst(timeLeft: Int)

    fun onCounterUpdate(count: Int)

    fun onTestStop()

}
