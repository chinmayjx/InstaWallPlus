package cj.instawall.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    CJImage imgCenter, imgRight, imgLeft, imgBottom;
    Paint paint = new Paint();
    Bitmap background;
    InstaClient instaClient;


    public ImageViewer(Context context) {
        this(context, null);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ImageViewer(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        try {
            instaClient = InstaClient.getInstance(this.getContext());
        } catch (Exception e) {
            Log.e(TAG, "ImageViewer: F from InstaClient");
            e.printStackTrace();
        }
    }

    Bitmap scaleBitmapToWidth(Bitmap b) {
        int w = this.getWidth();
        int sh = (int) ((float) w / (float) b.getWidth() * (float) b.getHeight());
        return Bitmap.createScaledBitmap(b, w, sh, true);
    }

    CJImage imageFromBitmap(Bitmap b) {
        Bitmap sb = scaleBitmapToWidth(b);
        return new CJImage(sb, new Point(0, (int) ((background.getHeight() - sb.getHeight()) / 2.0)));
    }

    public void getRandomImageBottom() {
        imgBottom = imageFromBitmap(background);
        imgBottom.transform.translateY = -imgBottom.position.y + imgCenter.position.y + imgCenter.bitmap.getHeight();
        imgBottom.transform.opacity = 0;
        CJImage ref = imgBottom;
        instaClient.act_getRandomImageAsync((path) -> {
            ref.changeBitmap(BitmapFactory.decodeFile(path.toString()), this.getWidth(), this.getHeight());
            postInvalidate();
        });
    }

    public void getRandomImageLeft() {
        imgLeft = imageFromBitmap(background);
        imgLeft.transform.translateX = -this.getWidth();
        imgLeft.transform.opacity = 0;
        CJImage ref = imgLeft;
        instaClient.act_getRandomImageAsync((path) -> {
            ref.changeBitmap(BitmapFactory.decodeFile(path.toString()), this.getWidth(), this.getHeight());
            postInvalidate();
        });
    }

    public void getRandomImageRight() {
        imgRight = imageFromBitmap(background);
        imgRight.transform.translateX = this.getWidth();
        imgRight.transform.opacity = 0;
        CJImage ref = imgRight;
        instaClient.act_getRandomImageAsync((path) -> {
            ref.changeBitmap(BitmapFactory.decodeFile(path.toString()), this.getWidth(), this.getHeight());
            postInvalidate();
        });
    }

    public CJImage.Transform rightTransform() {
        CJImage.Transform rt = new CJImage.Transform();
        rt.translateX = this.getWidth();
        rt.opacity = 0;
        return rt;
    }

    CJImage.Transform leftTransform() {
        CJImage.Transform lt = new CJImage.Transform();
        lt.translateX = -this.getWidth();
        lt.opacity = 0;
        return lt;
    }

    CJImage.Transform bottomTransform() {
        CJImage.Transform bt = new CJImage.Transform();
        bt.translateY = -imgBottom.position.y + imgCenter.position.y + imgCenter.bitmap.getHeight();
        bt.opacity = 0;
        return bt;
    }

    public void loadBitmap(Bitmap b) {
        imgCenter = imageFromBitmap(b);
        getRandomImageBottom();
        getRandomImageLeft();
        getRandomImageRight();

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
        imgBottom.drawOnCanvas(canvas, paint);
        imgCenter.drawOnCanvas(canvas, paint);
    }

    private boolean scaling = false;
    private float startScale = 0;
    private float startAngle = 0;

    private boolean sliding = false;
    private float startX = 0, startY = 0;
    // 0 = undecided, 1 = x, 2 = y
    private int slideDirection = 0;
    private final float slideThreshold = 10, slideSwitchDistance = 500, slideSwitchVelocity = 1.75f;
    long slideStartTime = 0;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            if (!sliding) {
                startX = e.getX();
                startY = e.getY();
                sliding = true;
                slideStartTime = System.currentTimeMillis();
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
                } else if (slideDirection == 2) {
                    imgCenter.transform.translateY = delY;
                    imgCenter.transform.opacity = Math.max(1 - Math.abs(delY / imgCenter.bitmap.getHeight()), 0);
                    if (delY < 0) {
                        imgBottom.transform.translateY = -imgBottom.position.y + imgCenter.position.y + imgCenter.bitmap.getHeight() + delY;
                        imgBottom.transform.opacity = Math.min(Math.abs(delY / imgCenter.bitmap.getHeight()), 1);
                    }
                }
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
            scaling = false;
            lastUpdate = System.currentTimeMillis();

            if (sliding) {
                float delX = e.getX() - startX;
                float delY = e.getY() - startY;
                if (slideDirection == 2) {
                    float vel = Math.abs(delY) / (Math.max(System.currentTimeMillis() - slideStartTime, 1));
                    if (delY < -slideSwitchDistance || vel > slideSwitchVelocity) {
                        imgCenter = imgBottom;
                        getRandomImageBottom();
                    }
                } else if (slideDirection == 1) {
                    float vel = Math.abs(delX) / (Math.max(System.currentTimeMillis() - slideStartTime, 1));
                    if (delX > slideSwitchDistance || (delX > 0 && vel > slideSwitchVelocity)) {
                        imgCenter = imgLeft;
                        getRandomImageLeft();
                    } else if (delX < -slideSwitchDistance || (delX < 0 && vel > slideSwitchVelocity)) {
                        imgCenter = imgRight;
                        getRandomImageRight();
                    }
                }
            }

            imgCenter.transform.target = new CJImage.Transform();

            imgRight.transform.target = rightTransform();
            imgLeft.transform.target = leftTransform();
            imgBottom.transform.target = bottomTransform();
            invalidate();
            restore();
            slideDirection = 0;
            sliding = false;
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
                    imgBottom.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));


                    if (imgCenter.transform.distanceToTarget() < ZERO && imgRight.transform.distanceToTarget() < ZERO && imgLeft.transform.distanceToTarget() < ZERO && imgBottom.transform.distanceToTarget() < ZERO)
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
