package cj.instawall.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Arrays;

public class ImageViewer extends View {
    public static final String TAG = "CJ";
    Bitmap cur;
    Paint paint = new Paint();
    Bitmap background;

    public ImageViewer(Context context) {
        super(context);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void loadBitmap(Bitmap b) {
        if (this.getWidth() == 0 && this.getHeight() == 0) return;
        int w = this.getWidth();
        int h = this.getHeight();
        int sh = (int) ((float) w / (float) b.getWidth() * (float) b.getHeight());
        cur = Bitmap.createScaledBitmap(b, w, sh, true);
        this.invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        int clr[] = new int[h * w];
        Arrays.fill(clr, -1 << 24);
        background = Bitmap.createBitmap(clr, w, h, Bitmap.Config.ARGB_8888);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (background == null) return;
        Log.d(TAG, "onDraw: " + this.getWidth() + " " + this.getHeight());
        canvas.drawBitmap(background, 0, 0, paint);
        canvas.drawBitmap(cur, 0, (int) ((background.getHeight() - cur.getHeight()) / 2.0), paint);
    }
}
