package com.radio.codec2talkie.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.radio.codec2talkie.MainActivity;
import com.radio.codec2talkie.R;
import com.radio.codec2talkie.settings.PreferenceKeys;
import com.radio.codec2talkie.tracker.Tracker;
import com.radio.codec2talkie.tracker.TrackerFactory;
import com.radio.codec2talkie.transport.TransportFactory;

import java.io.IOException;

public class AppService extends Service {

    private static final String TAG = AppService.class.getSimpleName();

    private final int NOTIFICATION_ID = 1;

    private AppWorker _appWorker;
    private Messenger _callbackMessenger;
    private Tracker _tracker;
    private NotificationManager _notificationManager;

    private final IBinder _binder  = new AppServiceBinder();

    public class AppServiceBinder extends Binder {
        public AppService getService() {
            return AppService.this;
        }
    }

    public void startTransmit() {
        _appWorker.startTransmit();
    }

    public void startReceive() {
        _appWorker.startReceive();
    }

    public void sendPosition() {
        _tracker.sendPosition();
    }

    public void startTracking() {
        showNotification(R.string.app_service_notif_text_tracking);
        _tracker.startTracking();
    }

    public void stopTracking() {
        showNotification(R.string.app_service_notif_text_ptt_ready);
        _tracker.stopTracking();
    }

    public boolean isTracking() {
        return _tracker.isTracking();
    }

    public void stopRunning() {
        if (_appWorker != null) {
            _appWorker.stopRunning();
        }
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "onRebind()");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind()");
        _callbackMessenger = null;
        return true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return _binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Staring app service");

        // update callback from new intent
        Bundle extras = intent.getExtras();
        _callbackMessenger = (Messenger) extras.get("callback");

        // create if not running
        if (_appWorker == null) {
            _notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert _notificationManager != null;
            SharedPreferences _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

            String trackerName = _sharedPreferences.getString(PreferenceKeys.APRS_LOCATION_SOURCE, "manual");
            _tracker = TrackerFactory.create(trackerName);
            _tracker.initialize(getApplicationContext(), position -> _appWorker.sendPosition(position));

            TransportFactory.TransportType transportType = (TransportFactory.TransportType) extras.get("transportType");
            startAppWorker(transportType);

            Notification notification = buildNotification(getString(R.string.app_service_notif_text_ptt_ready));
            startForeground(NOTIFICATION_ID, notification);

            Log.i(TAG, "App service started");
        } else {
            Log.i(TAG, "App service is already running");
        }

        return START_REDELIVER_INTENT;
    }

    private void startAppWorker(TransportFactory.TransportType transportType) {

        try {
            Log.i(TAG, "Started app worker: " + transportType.toString());

            _appWorker = new AppWorker(transportType,
                    onAudioProcessorStateChanged,
                    getApplicationContext());
            _appWorker.start();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(AppService.this, R.string.worker_failed_to_start_processing, Toast.LENGTH_LONG).show();
        }
    }

    private void deliverToParent(Message msg) throws RemoteException {
        if (_callbackMessenger != null) {
            Message sendMsg = new Message();
            sendMsg.copyFrom(msg);
            _callbackMessenger.send(sendMsg);
        }
    }

    private final Handler onAudioProcessorStateChanged = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            try {
                deliverToParent(msg);

                switch (AppMessage.values()[msg.what]) {
                    case EV_DISCONNECTED:
                        _appWorker = null;
                        break;
                    default:
                        break;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private void showNotification(int resId) {
        String text = getString(resId);
        _notificationManager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        String channelId = "alpha";
        String channelName = "Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.setImportance(NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        _notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    private Notification buildNotification(String text) {
        String channelId = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            channelId = createNotificationChannel();

        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                this, channelId);
        return notificationBuilder
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_app_action)
                .setContentTitle(getString(R.string.app_service_notif_title))
                .setContentText(text)
                .setPriority(NotificationManagerCompat.IMPORTANCE_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setChannelId(channelId)
                .setContentIntent(intent)
                .setOnlyAlertOnce(true)
                .build();
    }
}
