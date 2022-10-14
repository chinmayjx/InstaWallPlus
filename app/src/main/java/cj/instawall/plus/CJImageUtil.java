package cj.instawall.plus;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;

public class CJImageUtil {
    public static final String TAG = "CJ";

    public CJImageUtil() {

    }

    public static int[] argb(int x) {
        int[] color = new int[4];
        color[3] = x & 255;
        x >>= 8;
        color[2] = x & 255;
        x >>= 8;
        color[1] = x & 255;
        x >>= 8;
        color[0] = x & 255;
        x >>= 8;
        return color;
    }

    static boolean rowHasSameColor(Bitmap img, Integer color, int y) {
        if (color == null) color = img.getPixel(0, y);
        for (int i = 0; i < img.getWidth(); i++) {
            if (img.getPixel(i, y) != color) {
                return false;
            }
        }
        return true;
    }

    static boolean colHasSameColor(Bitmap img, Integer color, int x) {
        if (color == null) color = img.getPixel(x, 0);
        for (int i = 0; i < img.getWidth(); i++) {
            if (img.getPixel(x, i) != color) {
                return false;
            }
        }
        return true;
    }

    static int firstNotSatisfying(int s, int e, IntPredicate f) {
        int m = (s + e) / 2;
        while (s < e) {
            if (f.test(m)) {
                s = m + 1;
            } else {
                e = m - 1;
            }
            m = (s + e) / 2;
        }
        return s;
    }

    public static Bitmap removeWhiteBorder(Bitmap img) {
        int top = firstNotSatisfying(0, img.getHeight() / 2, y -> rowHasSameColor(img, -1, y));
        int bottom = firstNotSatisfying(img.getHeight() / 2, img.getHeight() - 1, y -> !rowHasSameColor(img, -1, y));
        Log.d(TAG, "top " + top);
        Log.d(TAG, "bottom " + bottom);
        return Bitmap.createBitmap(img, 0, top, img.getWidth(), bottom - top);
    }
}
