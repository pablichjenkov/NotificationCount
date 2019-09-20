package com.adc.notificationrate.tester

import android.app.Application
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class NetworkTester(private val application: Application) {

    var isRunning = false

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val httpClient = OkHttpClient()

    private var successCount = 0

    private var failureCount = 0

    private var outboundEventSink: FlowableEmitter<NetworkTestEvent>? = null

    private var networkDisposable: CompositeDisposable = CompositeDisposable()


    fun eventPipe(): Flowable<NetworkTestEvent> {

        return Flowable.create(
                { emitter ->

                    outboundEventSink = emitter

                    // When the emitter un-subscribes clear its reference
                    emitter.setCancellable { outboundEventSink = null }

                },
                BackpressureStrategy.BUFFER
        )

    }

    fun startTest(
            repeatMillis: Long
    ) {

        if (isRunning) {

            return

        }

        isRunning = true

        val disposable = Observable
                .interval(1, repeatMillis, TimeUnit.MILLISECONDS)
                .map {

                    val request = Request.Builder()
                            .url("https://google.com")
                            .get()
                            //.header("Authorization", "token abcd")
                            .build()

                    val response = httpClient.newCall(request).execute()

                    val timestamp = simpleDateFormat.format(Date())

                    successCount++

                    NetworkTestEvent(successCount, failureCount, timestamp, response.code)

                }.onErrorResumeNext(
                        Function {

                            val timestamp = simpleDateFormat.format(Date())

                            failureCount++

                            Observable.just(
                                    NetworkTestEvent(successCount, failureCount, timestamp, -1)
                            )

                        }
                ).subscribe(
                        {

                            outboundEventSink?.onNext(it)

                        },
                        { th ->
                            outboundEventSink?.onError(th)

                            stopTest()
                        },
                        {
                            stopTest()
                        }
                )

        networkDisposable.add(disposable)

    }

    fun stopTest() {

        isRunning = false

        networkDisposable.clear()

        outboundEventSink?.onComplete()

        outboundEventSink = null

    }

}

data class NetworkTestEvent(
        val successCount: Int,
        val failureCount: Int,
        val timeStamp: String,
        val code: Int
)
