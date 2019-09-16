package com.adc.notificationrate.tester

import android.app.Application
import io.reactivex.Observable
import io.reactivex.functions.Function
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class NetworkTester(private val application: Application) {

    private val TEN_MINUTES = 10*60*1000

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val httpClient = OkHttpClient()

    private var successCount = 0

    private var failureCount = 0

    fun startTest(
            repeatMillis: Long
    ): Observable<RequestResp>  {

        return Observable
                .interval(0, repeatMillis, TimeUnit.MILLISECONDS)
                .map {

                    val request = Request.Builder()
                            .url("https://google.com")
                            .get()
                            //.header("Authorization", "token abcd")
                            .build()

                    val response = httpClient.newCall(request).execute()

                    val timestamp = simpleDateFormat.format(Date())

                    successCount ++

                    RequestResp(successCount, failureCount, timestamp, response.code)

                }.onErrorResumeNext(
                        Function {

                            val timestamp = simpleDateFormat.format(Date())

                            failureCount ++

                            Observable.just(
                                    RequestResp(successCount, failureCount, timestamp, -1)
                            )

                        }
                )

    }

    fun stopTest() {

    }

}

data class RequestResp(
        val successCount: Int,
        val failureCount: Int,
        val timeStamp: String,
        val code: Int
)
