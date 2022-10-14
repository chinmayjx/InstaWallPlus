package cj.instawall.plus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;

import java.io.IOException;

public class MainService extends Service {
    public static final String TAG = "CJ";
    public static final String SET_RANDOM_WALLPAPER = "cj.instawall.plus.random_wallpaper";
    public static final String WALLPAPER_FROM_CODE = "cj.instawall.plus.wallpaper_from_code";
    InstaClient instaClient;

    void launchForeG() {
        String CHANNEL_ID = "my_channel_01";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT);

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build();

        startForeground(1, notification);
    }

    public void createInstaClient() {
        try {
            instaClient = new InstaClient(this);
            Log.d(TAG, "Updated InstaClient");
        } catch (Exception e) {
            Log.e(TAG, "MainService: Failed to get InstaClient " + Log.getStackTraceString(e));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "InstaWall MainService Created");
        launchForeG();
        createInstaClient();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "MainService destroyed");
        instaClient.saveFiles();
        super.onDestroy();
    }

    class MainBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }

    public IBinder mainBinder = new MainBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mainBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent.getAction());
        try {
            switch (intent.getAction()) {
                case SET_RANDOM_WALLPAPER:
                    if (instaClient != null) {
                        instaClient.act(InstaClient.RANDOM_WALLPAPER);
                    }
                    break;
                case WALLPAPER_FROM_CODE:
                    instaClient.executor.execute(() -> {
                        try {
                            instaClient.setWallpaperFromCode(intent.getStringExtra(Intent.EXTRA_TEXT));
                        } catch (Exception e) {
                            Log.e(TAG, "can't set wallpaper from code" + Log.getStackTraceString(e));
                        }
                    });
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand: " + Log.getStackTraceString(e));
        }
        return super.onStartCommand(intent, flags, startId);
    }
}
