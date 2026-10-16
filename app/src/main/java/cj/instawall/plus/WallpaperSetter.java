package cj.instawall.plus;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.nio.file.Path;

/** Fits an image to the screen and sets it as the wallpaper. */
public final class WallpaperSetter {
    static final String TAG = "CJ";

    private WallpaperSetter() {
    }

    public static boolean set(Context context, Path image) {
        try {
            DisplayMetrics met = new DisplayMetrics();
            ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(met);
            int w = Math.min(met.widthPixels, met.heightPixels), h = Math.max(met.widthPixels, met.heightPixels);

            Bitmap bitmap = decode(image, w, h);
            if (bitmap == null) throw new IllegalStateException("can't decode " + image);
            bitmap = CJImageUtil.removeWhiteBorder(bitmap);

            Bitmap screen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(screen);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            if (bitmap.getWidth() > bitmap.getHeight()) {
                // landscape (most reddit wallpapers): fill the screen, crop the sides
                float scale = (float) h / bitmap.getHeight();
                int srcW = Math.round(w / scale);
                int left = (bitmap.getWidth() - srcW) / 2;
                canvas.drawBitmap(bitmap, new Rect(left, 0, left + srcW, bitmap.getHeight()), new Rect(0, 0, w, h), paint);
            } else {
                // portrait/square (instagram): fit the width, centered
                int sh = Math.round((float) w / bitmap.getWidth() * bitmap.getHeight());
                canvas.drawBitmap(bitmap, null, new Rect(0, (h - sh) / 2, w, (h - sh) / 2 + sh), paint);
            }
            WallpaperManager.getInstance(context).setBitmap(screen);
            Log.d(TAG, "Wallpaper set successfully: " + image.getFileName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set wallpaper " + Log.getStackTraceString(e));
            return false;
        }
    }

    // 4k images are big, decode them at about the size we need
    static Bitmap decode(Path image, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.toString(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(image.toString(), o);
    }
}
