package cj.instawall.plus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
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

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap f;
        if (scaleFactor >= 1) f = Bitmap.createBitmap(
                cur,
                (int) ((scaleFactor - 1)/2 * pivotX),
                (int) ((scaleFactor - 1)/2 * pivotY),
                (int) (cur.getWidth() / scaleFactor),
                (int) (cur.getHeight() / scaleFactor));
        else f = cur;
        int w = this.getWidth();
        int sh = (int) ((float) w / (float) f.getWidth() * (float) f.getHeight());
        f = Bitmap.createScaledBitmap(f, w, sh, true);
        if (background == null) return;
        canvas.drawBitmap(background, 0, 0, paint);
        canvas.drawBitmap(f, 0, (int) ((background.getHeight() - f.getHeight()) / 2.0), paint);
    }

    private boolean scaling = false;
    private float startScale = 0;
    private float scaleFactor = 1;
    private int pivotX = 0, pivotY = 0;

    public float twoFingerDistance(MotionEvent e) {
        float x1 = e.getX(0), x2 = e.getX(1);
        float y1 = e.getY(0), y2 = e.getY(1);
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
//        Log.d(TAG, "onTouchEvent: " + event.getPointerCount());
        if (e.getPointerCount() >= 2) {
            if (!scaling) {
                startScale = twoFingerDistance(e);
                pivotX=(int)((e.getX(0)+e.getX(1))/2);
                pivotY=(int)((e.getY(0)+e.getY(1))/2);
            }
            scaling = true;
            scaleFactor = twoFingerDistance(e) / startScale;
            Log.d(TAG, "onTouchEvent: " + scaleFactor);
            invalidate();
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            scaling = false;
            scaleFactor = 1;
            invalidate();
        }
        return super.onTouchEvent(e);
    }
}
