package com.adc.notificationrate.execution

import com.adc.notificationrate.Logger
import java.util.concurrent.atomic.AtomicBoolean


class PeriodicRunnable(
        private val delayExecutor: DelayExecutor,
        private val block: () -> Unit,
        private val delayInMillis: Long
) {

    private var isStarted: AtomicBoolean = AtomicBoolean(false)

    private var isActive = false

    fun start() {

        if (isStarted.getAndSet(true)) {
            return
        }

        val runnable = object: Runnable {

            override fun run() {

                if (isActive) {

                    Logger.log("PeriodicRunnable::run() on ${Thread.currentThread().name}")

                    block.invoke()

                    delayExecutor.postDelayed(this, delayInMillis)

                    return

                }

            }

        }

        isActive = true

        delayExecutor.postNow(runnable)

    }

    fun stop() {

        isActive = false

        isStarted.set(false)

    }

}