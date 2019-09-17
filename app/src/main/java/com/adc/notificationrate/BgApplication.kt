package com.adc.notificationrate

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.adc.notificationrate.tester.NotificationTester
import com.adc.notificationrate.services.FgService
import com.adc.notificationrate.tester.NetworkTester


class BgApplication : Application() {

    var mainActivityLastStopTimestamp: Long = 0

    var isForegroundStarted = false

    lateinit var notificationTester: NotificationTester

    lateinit var networkTester: NetworkTester

    override fun onCreate() {
        super.onCreate()

        instance = this

        notificationTester = NotificationTester(this)

        networkTester = NetworkTester(this)

        Logger.log("BgApplication::onCreate()")

    }

    override fun onLowMemory() {
        super.onLowMemory()

        Logger.log("BgApplication::onLowMemory()")

    }

    override fun onTerminate() {
        super.onTerminate()

        Logger.log("BgApplication::onTerminate()")

    }

    fun postNotification(notificationId: Int, notification: Notification) {

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationId, notification)
        }

    }

    fun getNotificationData(): String {

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

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

            "isIgnoringBatteryOptimizations = ${powerManager.isIgnoringBatteryOptimizations(packageName)} \n"

        } else "isIgnoringBatteryOptimizations = N/A \n"


        return dozeInfo + batterySaverInfo + batteryIgnoreOptInfo + getDataSaverInfo()

    }

    private fun getDataSaverInfo(): String {

        var result = "N/A"

        if (Build.VERSION.SDK_INT >= 24) {

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

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

    fun startFgService() {

        if (isForegroundStarted) {
            return
        }

        isForegroundStarted = true

        val fgServiceIntent = Intent(this, FgService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            startForegroundService(fgServiceIntent)

        } else {

            Logger.log("========== SDK < 26, starting FgService as normal service ==========")

            startService(fgServiceIntent)

        }

    }


    companion object {

        lateinit var instance: BgApplication

    }

}
