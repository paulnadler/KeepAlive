package com.nstudio.keepalive;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
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

    public static final String FILE = "http://www.paulnadler.com/nstudio/ping";

    private static final int WAIT_TIME = 1000 * 60 * 1;

    private PendingIntent stopIntent;

    public KeepAliveService() {
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Log.d(TAG, "action - " + intent.getAction());

            if (intent.getAction().equals(ACTION_START)) {
                Intent closeIntent = new Intent(this, getClass());
                closeIntent.setAction(ACTION_CLOSE);
                stopIntent = PendingIntent.getService(this, NOTIFICATION_ID, closeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_icon)
                        .setContentTitle("Keep Alive: Connecting...")
                        .setContentText("Press to close")
                        .setOngoing(true)
                        .setContentIntent(stopIntent);

                startForeground(NOTIFICATION_ID, builder.build());
                setAlarm(1000, intent);
            } else if (intent.getAction().equals(ACTION_UPDATE)) {
                download(intent);
                setAlarm(WAIT_TIME, intent);
            } else if (intent.getAction().equals(ACTION_CLOSE)) {
                cancelAlarm(intent);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void setAlarm(int interval, Intent intent) {
        intent.setAction(ACTION_UPDATE);
        PendingIntent updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long uptime = SystemClock.elapsedRealtime();
        alarm.set(AlarmManager.ELAPSED_REALTIME, uptime + interval, updateIntent);
    }

    private void cancelAlarm(Intent intent) {
        intent.setAction(ACTION_UPDATE);
        PendingIntent updateIntent = PendingIntent.getService(this, NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(updateIntent);
    }

    private void download(final Intent intent) {
        new Thread(new Runnable() {
            public void run() {
                String text = getWebPage(FILE);
                String status = "Connected";
                if (text == null)
                    status = "Not Connected";
                else if (!text.contains(HASH))
                    status = "Restricted";

                NotificationCompat.Builder builder = new NotificationCompat.Builder(KeepAliveService.this)
                        .setSmallIcon(R.drawable.ic_stat_icon)
                        .setContentTitle("Keep Alive: " + status)
                        .setContentText("Press to close")
                        .setOngoing(true)
                        .setContentIntent(stopIntent);

                NotificationManager note = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                note.notify(NOTIFICATION_ID, builder.build());
            }
        }).start();
    }

    public static String getWebPage(String webAddress) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(webAddress).openConnection();
            connection.setConnectTimeout(1000 * 30);
            connection.setReadTimeout(1000 * 30);
            BufferedReader br = null;
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                html.append(line+"\n");
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
