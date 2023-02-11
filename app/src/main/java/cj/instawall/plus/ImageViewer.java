package cj.instawall.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Arrays;

public class ImageViewer extends View {
    public static final String TAG = "CJ";
    Bitmap cur;
    CJImage imgCenter, imgRight, imgLeft, imgBottom;
    Paint paint = new Paint();
    Bitmap background;
    private Handler handler;

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
        this.handler = new Handler(Looper.getMainLooper());
    }

    CJImage imageFromBitmap(Bitmap b) {
        int w = this.getWidth();
        int sh = (int) ((float) w / (float) b.getWidth() * (float) b.getHeight());
        cur = Bitmap.createScaledBitmap(b, w, sh, true);
        return new CJImage(cur, new Point(0, (int) ((background.getHeight() - cur.getHeight()) / 2.0)));
    }

    public void loadBitmap(Bitmap b, Bitmap b2) {
        imgCenter = imageFromBitmap(b);
        imgRight = imageFromBitmap(b2);
        imgRight.transform.translateX = this.getWidth();
        imgRight.transform.opacity = 0;
        imgLeft = imageFromBitmap(b2);
        imgLeft.transform.translateX = -this.getWidth();
        imgLeft.transform.opacity = 0;
        imgBottom = imageFromBitmap(b2);
        imgBottom.transform.translateY = -imgBottom.position.y + imgCenter.position.y + imgCenter.bitmap.getHeight();
        imgBottom.transform.opacity = 0;
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
        canvas.drawBitmap(background, 0, 0, paint);
        imgLeft.drawOnCanvas(canvas, paint);
        imgRight.drawOnCanvas(canvas, paint);
        imgCenter.drawOnCanvas(canvas, paint);
    }

    private boolean scaling = false;
    private float startScale = 0;
    private float startAngle = 0;

    private boolean sliding = false;
    private float startX = 0, startY = 0;
    // 0 = undecided, 1 = x, 2 = y
    private int slideDirection = 0;
    private float slideThreshold = 10;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            if (!sliding) {
                startX = e.getX();
                startY = e.getY();
                sliding = true;
            }
            float delX = e.getX() - startX;
            float delY = e.getY() - startY;
            if (slideDirection == 0) {
                if (Math.abs(delX) > slideThreshold) slideDirection = 1;
                else if (Math.abs(delY) > slideThreshold) slideDirection = 2;
            } else {
                if (slideDirection == 1) {
                    imgCenter.transform.translateX = delX;
                    imgCenter.transform.opacity = 1 - Math.abs(delX / this.getWidth());
                    if (delX < 0) {
                        imgRight.transform.translateX = this.getWidth() + delX;
                        imgRight.transform.opacity = Math.abs(delX / this.getWidth());
                    } else {
                        imgLeft.transform.translateX = -this.getWidth() + delX;
                        imgLeft.transform.opacity = Math.abs(delX / this.getWidth());
                    }
                } else if (slideDirection == 2) imgCenter.transform.translateY = delY;
                invalidate();
            }
//            Log.d(TAG, "onTouchEvent: " + (e.getX()-startX) + " " + (e.getY()-startY));
        } else if (e.getPointerCount() >= 2) {
            float x1 = e.getX(0), x2 = e.getX(1);
            float y1 = e.getY(0), y2 = e.getY(1);
            float twoFingerDist = (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
            float deg = (float) (Math.atan2((y2 - y1), (x2 - x1)) * 180 / Math.PI);
            int mx = (int) ((x1 + x2) / 2);
            int my = (int) ((y1 + y2) / 2);
            if (!scaling) {
                startScale = twoFingerDist;
                startAngle = deg;
                imgCenter.transform.pivotX = mx;
                imgCenter.transform.pivotY = my;
                scaling = true;
            }
            imgCenter.transform.scaleFactor = twoFingerDist / startScale;
            imgCenter.transform.rotation = deg - startAngle;
            imgCenter.transform.translateX = mx - imgCenter.transform.pivotX;
            imgCenter.transform.translateY = my - imgCenter.transform.pivotY;
            invalidate();
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            sliding = false;
            scaling = false;
            slideDirection = 0;
            invalidate();
            lastUpdate = System.currentTimeMillis();
            imgCenter.transform.target = new CJImage.Transform();
            CJImage.Transform rt = new CJImage.Transform();
            rt.translateX = this.getWidth();
            imgRight.transform.target = rt;
            CJImage.Transform lt = new CJImage.Transform();
            lt.translateX = -this.getWidth();
            imgLeft.transform.target = lt;
            restore();
        }
        return super.onTouchEvent(e);
    }

    float velocity = 0.02f;
    long lastUpdate = 0;
    long frequency = 60;
    final float ZERO = 0.05f;

    void restore() {
        new Thread(() -> {
            try {
                for (; ; ) {
                    imgCenter.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));
                    imgRight.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));
                    imgLeft.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));

                    if (imgCenter.transform.distanceToTarget() < ZERO && imgRight.transform.distanceToTarget() < ZERO && imgLeft.transform.distanceToTarget() < ZERO)
                        break;
                    lastUpdate = System.currentTimeMillis();
                    postInvalidate();
                    Thread.sleep(1000 / frequency);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
