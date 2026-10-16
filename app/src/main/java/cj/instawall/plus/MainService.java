package cj.instawall.plus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Foreground service for wallpaper actions started by the widget tap and shared links. */
public class MainService extends Service {
    public static final String TAG = "CJ";
    public static final String SET_RANDOM_WALLPAPER = "cj.instawall.plus.random_wallpaper";
    public static final String WALLPAPER_FROM_CODE = "cj.instawall.plus.wallpaper_from_code";

    void startForeground() {
        String channelId = "wallpaper";
        NotificationChannel channel = new NotificationChannel(channelId, "Wallpaper changes", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_wallpaper)
                .setContentTitle("Changing wallpaper")
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        Log.d(TAG, "onStartCommand: " + action);
        Core core = Core.get(this);
        if (SET_RANDOM_WALLPAPER.equals(action)) {
            core.setRandomWallpaper();
        } else if (WALLPAPER_FROM_CODE.equals(action)) {
            core.setWallpaperFromInstagramCode(intent.getStringExtra(Intent.EXTRA_TEXT));
        }
        // done once the wallpaper is set, don't keep the notification around
        core.afterQueuedWork(() -> stopSelf(startId));
        return START_NOT_STICKY;
    }
}
