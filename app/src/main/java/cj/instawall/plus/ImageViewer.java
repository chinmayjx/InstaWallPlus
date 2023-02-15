package cj.instawall.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImageViewer extends View {
    public static final String TAG = "CJ";
    CJImage imgCenter, imgRight, imgLeft, imgBottom;
    Paint paint = new Paint();
    Bitmap background;
    ExecutorService executor = Executors.newCachedThreadPool();
    ScheduledExecutorService singleExecutor = Executors.newSingleThreadScheduledExecutor();
    ScheduledFuture<?> singleTask;
    InstaClient instaClient;
    JSONObject currentPostInfo = null;
    int currentImageIndex = 0;
    int nImagesInPost = 0;
    CJImageProvider bottomImageProvider = new CJImageProvider() {
        @Override
        public CJImage getNextImage() {
            CJImage ref = new CJImage(background, getWidth(), getHeight());
            ref.transform.translateY = -ref.position.y + imgCenter.position.y + imgCenter.bitmap.getHeight();
            ref.transform.opacity = 0;
            ref.startLoading(ImageViewer.this::postInvalidate);
            instaClient.act_getRandomImageAsync((path) -> {
                ref.stopLoading();
                ref.changeImage(path, getWidth(), getHeight());
                postInvalidate();
            });
            return ref;
        }

        @Override
        public CJImage getPrevImage() {
            return getNextImage();
        }
    };

    CJImage getImageAtIndexInCurrentPost(int ix) {
        if (currentPostInfo == null || ix < 0 || ix > instaClient.numberOfImagesInPost(currentPostInfo) - 1)
            return null;
        CJImage ref = new CJImage(background, getWidth(), getHeight());
        ref.transform.translateX = getWidth();
        ref.transform.opacity = 0;
        ref.startLoading(ImageViewer.this::postInvalidate);
        executor.execute(() -> {
            try {
                Path pt = Paths.get(InstaClient.imagePath, instaClient.getImageInPost(currentPostInfo, instaClient.getImageAtIndexInPost(currentPostInfo, ix)));
                ref.stopLoading();
                ref.changeImage(pt, getWidth(), getHeight());
                postInvalidate();
            } catch (Exception e) {
                Log.e(TAG, "getNextImage: " + Log.getStackTraceString(e));
            }
        });
        return ref;
    }

    CJImageProvider sideImageProvider = new CJImageProvider() {

        @Override
        public CJImage getNextImage() {
            if (currentPostInfo == null || currentImageIndex >= instaClient.numberOfImagesInPost(currentPostInfo) - 1)
                return null;
            currentImageIndex++;
            return getImageAtIndexInCurrentPost(currentImageIndex + 1);
        }

        @Override
        public CJImage getPrevImage() {
            if (currentImageIndex <= 0)
                return null;
            currentImageIndex--;
            return getImageAtIndexInCurrentPost(currentImageIndex - 1);
        }
    };


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

    CJImage.Transform rightTransform() {
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

    void loadPostInfo(String postID) {
        currentImageIndex = InstaClient.PostInfo.imageIndexInPost(currentPostInfo, postID);
        nImagesInPost = currentPostInfo == null ? 0 : instaClient.numberOfImagesInPost(currentPostInfo);
        imgLeft = getImageAtIndexInCurrentPost(currentImageIndex - 1);
        imgRight = getImageAtIndexInCurrentPost(currentImageIndex + 1);
        if (imgLeft != null) imgLeft.transform.target = leftTransform();
        if (imgRight != null) imgRight.transform.target = rightTransform();
        postInvalidate();
    }

    public void setPostByPath(Path p) {
        imgLeft = null;
        imgRight = null;
        nImagesInPost = 0;
        currentImageIndex = 0;
        invalidate();
        if (singleTask != null && !singleTask.isCancelled()) singleTask.cancel(true);
        if (p == null) return;
        String[] a = p.getFileName().toString().split("\\.")[0].split("_");
        currentPostInfo = instaClient.postInfoFromCache(a[0]);
        if (currentPostInfo == null) {
            singleTask = singleExecutor.schedule(() -> {
                try {
                    if (currentPostInfo == null) currentPostInfo = instaClient.getPostInfo(a[0]);
                    loadPostInfo(a[1]);
                } catch (Exception ex) {
                    Log.e(TAG, "setPostByPath: " + p.getFileName() + " " + Log.getStackTraceString(ex));
                }
            }, 500, TimeUnit.MILLISECONDS);
        } else loadPostInfo(a[1]);
    }

    public void loadImage(Path p) {
        setPostByPath(p);
        imgCenter = new CJImage(p, getWidth(), getHeight());
        imgBottom = bottomImageProvider.getNextImage();
        imgLeft = null;
        imgRight = null;

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

    Paint dotPaint = new Paint();
    float dotOpacity = 1;

    void drawDots(Canvas canvas) {
        dotPaint.setColor(Color.WHITE);
        dotPaint.setAlpha((int) (dotOpacity * 255));
        float r = 20;
        int nd = nImagesInPost;
        if (nd == 0) return;
        float dotGap = 20;
        canvas.save();
        canvas.translate((int) ((getWidth() - (2 * r * nd - 2 * r + (nd - 1) * dotGap)) / 2), 0);
        for (int i = 0; i < nd; i++) {
            float cx = i * r * 2 + i * dotGap;
            float cy = getHeight() - 2 * r - 10;
            if (currentImageIndex == i) {
                canvas.drawCircle(cx, cy, r, dotPaint);
                dotPaint.setColor(Color.BLACK);
                canvas.drawCircle(cx, cy, r / 2, dotPaint);
                dotPaint.setColor(Color.WHITE);
                dotPaint.setAlpha((int) (dotOpacity * 255));
            } else canvas.drawCircle(cx, cy, r / 2, dotPaint);
        }
        dotPaint.setAlpha(255);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(background, 0, 0, paint);
        if (imgLeft != null) imgLeft.drawOnCanvas(canvas, paint);
        if (imgRight != null) imgRight.drawOnCanvas(canvas, paint);
        imgBottom.drawOnCanvas(canvas, paint);
        imgCenter.drawOnCanvas(canvas, paint);
        drawDots(canvas);
    }

    private boolean scaling = false;
    private float startScale = 0;
    private float startAngle = 0;

    private boolean sliding = false;
    private float startX = 0, startY = 0;
    // 0 = undecided, 1 = x, 2 = y
    private int slideDirection = 0;
    private final float slideThreshold = 10, slideSwitchDistance = 500, slideSwitchVelocity = 1.5f;
    long slideStartTime = 0;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            if (scaling) {
                scaling = false;
                startX = e.getX();
                startY = e.getY();
                slideStartTime = System.currentTimeMillis();
            }
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
                    if (imgRight != null && delX < 0) {
                        imgRight.transform.translateX = this.getWidth() + delX;
                        imgRight.transform.opacity = Math.abs(delX / this.getWidth());
                    } else if (imgLeft != null) {
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
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (restoreThread != null && restoreThread.isAlive()) {
                restoreThread.interrupt();
                restoreThread = null;
            }
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            lastUpdate = System.currentTimeMillis();

            if (!scaling && sliding) {
                float delX = e.getX() - startX;
                float delY = e.getY() - startY;
                if (slideDirection == 2) {
                    float vel = Math.abs(delY) / (Math.max(System.currentTimeMillis() - slideStartTime, 1));
                    if (delY < -slideSwitchDistance || vel > slideSwitchVelocity) {
                        imgCenter.destroy();
                        imgCenter = imgBottom;
                        imgBottom = bottomImageProvider.getNextImage();
                        setPostByPath(imgCenter.path);
                    }
                } else if (slideDirection == 1) {
                    float vel = Math.abs(delX) / (Math.max(System.currentTimeMillis() - slideStartTime, 1));
                    if (imgLeft != null && (delX > slideSwitchDistance || (delX > 0 && vel > slideSwitchVelocity))) {
                        if (imgRight != null) imgRight.destroy();
                        imgRight = imgCenter;
                        imgCenter = imgLeft;
                        imgLeft = sideImageProvider.getPrevImage();
                    } else if (imgRight != null && (delX < -slideSwitchDistance || (delX < 0 && vel > slideSwitchVelocity))) {
                        if (imgLeft != null) imgLeft.destroy();
                        imgLeft = imgCenter;
                        imgCenter = imgRight;
                        imgRight = sideImageProvider.getNextImage();
                    }
                }
            }
            imgCenter.transform.target = new CJImage.Transform();

            if (imgRight != null) imgRight.transform.target = rightTransform();
            if (imgLeft != null) imgLeft.transform.target = leftTransform();
            imgBottom.transform.target = bottomTransform();
            invalidate();
            restore();
            slideDirection = 0;
            scaling = false;
            sliding = false;
        }
        return super.onTouchEvent(e);
    }

    float velocity = 0.02f;
    long lastUpdate = 0;
    long frequency = 60;
    final float ZERO = 0.05f;
    Thread restoreThread;

    void restore() {
        restoreThread = new Thread(() -> {
            try {
                for (; ; ) {
                    imgCenter.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));
                    if (imgRight != null)
                        imgRight.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));
                    if (imgLeft != null)
                        imgLeft.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));
                    imgBottom.transform.absoluteToTarget(velocity * (System.currentTimeMillis() - lastUpdate));


                    if (imgCenter.transform.distanceToTarget() < ZERO && (imgRight == null || imgRight.transform.distanceToTarget() < ZERO) && (imgLeft == null || imgLeft.transform.distanceToTarget() < ZERO) && imgBottom.transform.distanceToTarget() < ZERO)
                        break;
                    lastUpdate = System.currentTimeMillis();
                    postInvalidate();
                    Thread.sleep(1000 / frequency);
                }
                restoreThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        restoreThread.start();
    }
}
