package com.adc.notificationrate

import android.util.Log

object Logger {

    val TAG = "ADC_NOTIFICATION"

    @JvmStatic
    fun log(text: String) {

        Log.d(TAG, text)

    }

}