package com.adc.notificationrate;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;


public class MainActivity extends AppCompatActivity {

    private static final String ChannelIdTestLimit = "testLimitChannelId";

    private static final int NotificationIdTestLimit = 123;

    private AccessibilityManager accessibilityManager;

    private NotificationManagerCompat notificationManagerCompat;

    private Disposable progressDisposable = Disposables.disposed();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        printEnabledAccessibilityServices();

        findViewById(R.id.startDownloadBtn).setOnClickListener(o -> {
            progressDisposable.dispose();

            notificationManagerCompat = NotificationManagerCompat.from(this);

            notificationManagerCompat.cancelAll();

            if (Build.VERSION.SDK_INT >= 26) {

                NotificationChannel notificationChannel
                        = notificationManagerCompat.getNotificationChannel(ChannelIdTestLimit);

                if (notificationChannel == null) {

                    notificationChannel = new NotificationChannel(
                            ChannelIdTestLimit,
                            "Channel to post high priority notifications to test the Notification Rate limit",
                            NotificationManager.IMPORTANCE_HIGH
                    );

                    notificationChannel.setSound(null, null);

                    notificationManagerCompat.createNotificationChannel(notificationChannel);

                }

            }

            long downloadStartTime = System.currentTimeMillis();

            progressDisposable = streamProgress()
                    //.sample(200, TimeUnit.MILLISECONDS, true /* emitLast */)
                    .subscribe(
                            progress -> {

                                Logger.log("========== Progress: " + progress);

                                if (progress <= 100) {

                                    updateProgressNotification(progress, NotificationIdTestLimit, downloadStartTime);

                                }


                            },
                            Throwable::printStackTrace,
                            () -> new Handler(Looper.getMainLooper())
                                    .postDelayed(
                                            () -> notificationManagerCompat.cancel(NotificationIdTestLimit),
                                            500
                                    )
                    );
        });

    }

    private Observable<Integer> streamProgress() {

        return Observable.range(0, 101)
                // Add a delay to every emission. Observable#delay() cannot be used here.
                .zipWith(
                        Observable.interval(
                                100,
                                TimeUnit.MILLISECONDS,
                                AndroidSchedulers.mainThread()
                        ),
                        (progress, delay) -> progress
                );

    }

    private void updateProgressNotification(int progress, int notificationId, long downloadStartTime) {

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, ChannelIdTestLimit)
                .setContentTitle("Saving image")
                .setContentText(progress + "%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setWhen(downloadStartTime)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setProgress(100 /* max */, progress, false /* indeterminateProgress */)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL);

        if (Build.VERSION.SDK_INT >= 21) {
            notificationBuilder.setVibrate(new long[0]);
        }

        notificationManagerCompat.notify(notificationId, notificationBuilder.build());

    }

    private void printEnabledAccessibilityServices() {

        List<AccessibilityServiceInfo> accessibilityServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        Logger.log("Accessibility Service List size: " + accessibilityServices.size());

        for (AccessibilityServiceInfo info : accessibilityServices) {

            Logger.log("Accessibility Service: " + info.getId());

        }

    }

}
