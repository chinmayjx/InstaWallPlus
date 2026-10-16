package cj.instawall.plus;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// sets a random wallpaper in the background (android 12+ doesn't let the widget start a foreground service for it)
public class WallpaperWorker extends Worker {
    public static final String TAG = "CJ";

    public WallpaperWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("random_wallpaper", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(WallpaperWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Core.get(getApplicationContext()).setRandomWallpaperNow();
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "WallpaperWorker: " + Log.getStackTraceString(e));
            return Result.failure();
        }
    }
}
