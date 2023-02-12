package cj.instawall.plus;

import android.graphics.Bitmap;
import android.util.Log;

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

    static float colorDistance(int c1, int c2) {
        int[] c1a = argb(c1);
        int[] c2a = argb(c2);
        return Math.abs(c1a[0] - c2a[0]) +
                Math.abs(c1a[1] - c2a[1]) +
                Math.abs(c1a[2] - c2a[2]) +
                Math.abs(c1a[3] - c2a[3]);
    }

    static final float falsePixelCutoff = 0.05f;
    static final int colorDistanceCutoff = 30;
    static final int borderEscapePadding = 10;

    static boolean rowHasSameColor(Bitmap img, Integer color, int y) {
        if (color == null) color = img.getPixel(0, y);
        int odd = 0;
        for (int i = 0; i < img.getWidth(); i++) {
            if (colorDistance(img.getPixel(i, y), color) > colorDistanceCutoff) {
                odd++;
                if ((float) odd / img.getWidth() > falsePixelCutoff) return false;
            }
        }
        return true;
    }

    static boolean colHasSameColor(Bitmap img, Integer color, int x) {
        if (color == null) color = img.getPixel(x, 0);
        int odd = 0;
        for (int i = 0; i < img.getHeight(); i++) {
            if (colorDistance(img.getPixel(x, i), color) > colorDistanceCutoff) {
                odd++;
                if ((float) odd / img.getHeight() > falsePixelCutoff) return false;
            }
        }
        return true;
    }

    static int firstFalse(int s, int e, IntPredicate f) {
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

    static int topBound(Bitmap img, IntPredicate f) {
        if (!f.test(0)) return 0;
        return firstFalse(0, img.getHeight() / 2, f) + borderEscapePadding;
    }

    static int bottomBound(Bitmap img, IntPredicate f) {
        if (!f.test(img.getHeight() - 1)) return img.getHeight() - 1;
        return firstFalse(img.getHeight() / 2, img.getHeight() - 1, y -> !f.test(y)) - 1 - borderEscapePadding;
    }

    static int leftBound(Bitmap img, IntPredicate f) {
        if (!f.test(0)) return 0;
        return firstFalse(0, img.getWidth() / 2, f) + borderEscapePadding;
    }

    static int rightBound(Bitmap img, IntPredicate f) {
        if (!f.test(img.getWidth() - 1)) return img.getWidth() - 1;
        return firstFalse(img.getWidth() / 2, img.getWidth() - 1, x -> !f.test(x)) - 1 - borderEscapePadding;
    }

    public static Bitmap removeWhiteBorder(Bitmap img) {
        int top = topBound(img, y -> rowHasSameColor(img, -1, y));
        int bottom = bottomBound(img, y -> rowHasSameColor(img, -1, y));
        int left = leftBound(img, x -> colHasSameColor(img, -1, x));
        int right = rightBound(img, x -> colHasSameColor(img, -1, x));
        if (top == 0 && left == 0 && right == img.getWidth() - 1 && bottom == img.getHeight() - 1) {
            return img;
        } else {
            Log.d(TAG, "cropped border, bounds: [ltrb] : " + left + " " + top + " " + right + " " + bottom + " from " + img.getWidth() + "x" + img.getHeight());
        }
        return Bitmap.createBitmap(img, left, top, right - left, bottom - top);
    }
    public static Bitmap scaleToWidth(Bitmap b, int w){
        int sh = (int) (w / (float) b.getWidth() * (float) b.getHeight());
        return Bitmap.createScaledBitmap(b, w, sh, true);
    }
}
