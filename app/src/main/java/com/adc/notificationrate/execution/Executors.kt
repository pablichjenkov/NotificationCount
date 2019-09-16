package com.adc.notificationrate.execution

import android.os.Handler
import android.os.Looper
import java.util.concurrent.*


interface DelayExecutor {
    fun postNow(runnable: Runnable)
    fun postDelayed(runnable: Runnable, delayInMillis: Long)
    fun remove(runnable: Runnable?): Boolean
}

class BgScheduledExecutor private constructor(
        corePoolSize: Int
): ScheduledThreadPoolExecutor(corePoolSize),
        DelayExecutor
{

    override fun postNow(runnable: Runnable) {

        submit(runnable)

    }

    override fun postDelayed(runnable: Runnable, delayInMillis: Long) {

        schedule(runnable, delayInMillis, TimeUnit.MILLISECONDS)

    }

    override fun remove(runnable: Runnable?): Boolean {
        return super.remove(runnable)
    }

    companion object {

        private val CORE_POOL_SIZE = 8
        private val MAX_POOL_SIZE = 18
        private val KEEP_ALIVE_TIME = 50L

        val instance = BgScheduledExecutor(CORE_POOL_SIZE)

    }

}

class MainScheduledExecutor private constructor(
        private val handler: Handler
): DelayExecutor {

    override fun postNow(runnable: Runnable) {

        handler.post(runnable)

    }

    override fun postDelayed(runnable: Runnable, delayInMillis: Long) {

        handler.postDelayed(runnable, delayInMillis)

    }

    override fun remove(runnable: Runnable?): Boolean {

        runnable?.let {

            handler.removeCallbacks(it)

            return@remove true
        }

        return false

    }

    companion object {

        val instance = MainScheduledExecutor(Handler(Looper.getMainLooper()))

    }

}