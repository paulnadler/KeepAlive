package com.nstudio.keepalive;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class KeepAliveService extends Service {

    public static final String TAG = "KeepAliveService";

    public static final int NOTIFICATION_ID = 42;
    private static final String HASH =
            "ed443caad83f10c8c03d5c3fd94d21128a190c353e46e04f9e63390119e3246d";

    public static final String ACTION_START = "com.nstudio.keepalive.action.START";
    public static final String ACTION_CLOSE = "com.nstudio.keepalive.action.CLOSE";
    public static final String ACTION_UPDATE = "com.nstudio.keepalive.action.UPDATE";

    public static final String FILE = "https://granite-apps.appspot.com/keep_alive/ping-v1.1.2";

    // ping every 10 minutes
    private static final int WAIT_TIME = 1000 * 60 * 10;
    private static final String NOTE_CHANNEL_ID = "ongoing_note_channel";

    private PendingIntent stopIntent;

    public KeepAliveService() {
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Log.d(TAG, "action - " + intent.getAction());

            if (ACTION_START.equals(intent.getAction())) {
                Intent closeIntent = new Intent(this, getClass());
                closeIntent.setAction(ACTION_CLOSE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    stopIntent = PendingIntent.getService(this, NOTIFICATION_ID, closeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                } else {
                    stopIntent = PendingIntent.getService(this, NOTIFICATION_ID, closeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    NotificationChannel notificationChannel = new NotificationChannel(
                            NOTE_CHANNEL_ID,
                            "KeepAlive Status",
                            NotificationManager.IMPORTANCE_LOW);
                    if (notificationManager != null) {
                        notificationManager.createNotificationChannel(notificationChannel);
                    }
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(
                        this, NOTE_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_icon)
                        .setContentTitle("Keep Alive: Connecting...")
                        .setContentText("Press to close")
                        .setOngoing(true)
                        .setContentIntent(stopIntent);

                startForeground(NOTIFICATION_ID, builder.build());
                setAlarm(1000, intent);
            } else if (ACTION_UPDATE.equals(intent.getAction())) {
                download();
                setAlarm(WAIT_TIME, intent);
            } else if (ACTION_CLOSE.equals(intent.getAction())) {
                cancelAlarm(intent);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void setAlarm(int interval, Intent intent) {
        intent.setAction(ACTION_UPDATE);
        PendingIntent updateIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long uptime = SystemClock.elapsedRealtime();
        alarm.set(AlarmManager.ELAPSED_REALTIME, uptime + interval, updateIntent);
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void cancelAlarm(Intent intent) {
        intent.setAction(ACTION_UPDATE);
        PendingIntent updateIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(updateIntent);
    }

    private void download() {
        new Thread(() -> {
            String text = getWebPage(FILE);
            String status = "Connected";
            if (text == null)
                status = "Not Connected";
            else if (!text.contains(HASH))
                status = "Restricted";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    KeepAliveService.this, NOTE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_icon)
                    .setContentTitle("Keep Alive: " + status)
                    .setContentText("Press to close")
                    .setOngoing(true)
                    .setContentIntent(stopIntent);

            NotificationManager note = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            note.notify(NOTIFICATION_ID, builder.build());
        }).start();
    }

    public static String getWebPage(String webAddress) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(webAddress).openConnection();
            connection.setConnectTimeout(1000 * 30);
            connection.setReadTimeout(1000 * 30);
            BufferedReader br;
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                html.append(line).append("\n");
            br.close();
            connection.disconnect();
            Log.d(TAG, html.length() / 1024 + "K - HTTP:" + webAddress);
            return html.toString();
        } catch (Exception e) {
            Log.d(TAG, "Error Parsing: " + webAddress);
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            Log.d(TAG, "Error Parsing: " + webAddress);
            e.printStackTrace();
        } finally {
            if (connection != null)
                try { connection.disconnect();}
                catch (Exception e) {e.printStackTrace();}
        }
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "service destroyed, releasing resources");
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
