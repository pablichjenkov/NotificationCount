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
import com.adc.notificationrate.tester.BleTester
import com.adc.notificationrate.tester.NetworkTester


class BgApplication : Application() {

    var mainActivityLastStopTimestamp: Long = 0

    var isForegroundStarted = false

    lateinit var notificationTester: NotificationTester

    lateinit var networkTester: NetworkTester

    lateinit var bleTester: BleTester

    override fun onCreate() {
        super.onCreate()

        Logger.log("BgApplication::onCreate()")

        instance = this

        notificationTester = NotificationTester(this)

        networkTester = NetworkTester(this)

        bleTester = BleTester(this)

    }

    override fun onLowMemory() {
        super.onLowMemory()

        Logger.log("BgApplication::onLowMemory()")

    }

    override fun onTerminate() {
        super.onTerminate()

        Logger.log("BgApplication::onTerminate()")

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
