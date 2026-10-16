package cj.instawall.plus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

/**
 * One photo of the gallery: pinch / double tap / double-tap-and-drag to zoom, drag and fling to
 * pan, a mini-map while zoomed in, and drag down (or up) to close when not zoomed.
 * Horizontal drags go to the pager unless the photo is zoomed in and can still pan that way.
 */
public class ZoomImageView extends View {
    public interface Listener {
        void onSingleTap();

        /** zoomed in or back to fit */
        void onZoomChanged(boolean zoomed);

        /** 0..1 while dragging to close */
        void onDismissProgress(float progress);

        void onDismiss();
    }

    Listener listener;
    private Bitmap bitmap;
    private float scale = 1, tx = 0, ty = 0;
    private float fitScale = 1;
    private final Matrix matrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private VelocityTracker velocity;
    private ValueAnimator animator;
    private final int touchSlop, minFling;
    private final float dp;

    private static final int NONE = 0, PAN = 1, DISMISS = 2, MAP = 3, PARENT = 4;
    private int mode = NONE;
    private float downX, downY, lastX, lastY;
    private boolean quickScaled = false;
    private float dismissY = 0;
    private boolean wasZoomed = false;

    public ZoomImageView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        scroller = new OverScroller(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                if (bitmap == null) return false;
                stopMotion();
                if (mode == DISMISS) resetDismiss();
                // double-tap-and-drag zooms instead of the double tap toggle
                if (gestureInDoubleTap) quickScaled = true;
                mode = NONE;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                // a little give past the limits, settled when the fingers lift
                float next = Math.max(fitScale * 0.6f, Math.min(maxScale() * 1.3f, scale * f));
                zoomAround(next / scale, d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // a tap on the mini-map only moves the view
                if (listener != null && !downInMap) listener.onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                gestureInDoubleTap = true;
                quickScaled = false;
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!quickScaled) toggleZoom(e.getX(), e.getY());
                    gestureInDoubleTap = false;
                }
                return true;
            }
        });
        bitmapPaint.setFilterBitmap(true);
        mapFrame.setStyle(Paint.Style.STROKE);
        mapFrame.setStrokeWidth(1.5f * dp);
        mapFrame.setColor(Color.WHITE);
        mapFrame.setAlpha(140);
        mapView.setStyle(Paint.Style.STROKE);
        mapView.setStrokeWidth(2f * dp);
        mapView.setColor(getResources().getColor(R.color.iw_accent, null));
        mapShade.setColor(Color.BLACK);
        mapShade.setAlpha(110);
    }

    private boolean gestureInDoubleTap = false, downInMap = false;

    public void setBitmap(Bitmap b) {
        bitmap = b;
        stopMotion();
        resetDismiss();
        fit();
        invalidate();
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public boolean isZoomed() {
        return bitmap != null && scale > fitScale * 1.02f;
    }

    /** back to the whole photo */
    public void resetZoom(boolean animate) {
        if (bitmap == null) return;
        if (animate) animateTo(fitScale, (getWidth() - bitmap.getWidth() * fitScale) / 2, (getHeight() - bitmap.getHeight() * fitScale) / 2);
        else {
            stopMotion();
            fit();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fit();
    }

    private void fit() {
        if (bitmap == null || getWidth() == 0) return;
        fitScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        scale = fitScale;
        tx = (getWidth() - bitmap.getWidth() * scale) / 2;
        ty = (getHeight() - bitmap.getHeight() * scale) / 2;
        notifyZoom();
    }

    private float maxScale() {
        // enough to see the pixels of the photo, and at least 4x the fitted size
        return Math.max(fitScale * 4, 2.5f);
    }

    /** the zoom a double tap goes to: fill the screen if that's a real change, else 2.5x */
    private float doubleTapScale() {
        float fill = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        return fill / fitScale > 1.6f ? fill : Math.min(fitScale * 2.5f, maxScale());
    }

    private void toggleZoom(float x, float y) {
        if (bitmap == null) return;
        if (isZoomed()) {
            resetZoom(true);
            return;
        }
        float s = doubleTapScale();
        // the tapped point stays under the finger
        float nx = x - (x - tx) * s / scale;
        float ny = y - (y - ty) * s / scale;
        float[] c = clamped(s, nx, ny);
        animateTo(s, c[0], c[1]);
    }

    private void zoomAround(float factor, float fx, float fy) {
        scale *= factor;
        tx = fx - (fx - tx) * factor;
        ty = fy - (fy - ty) * factor;
        if (scale >= fitScale) {
            float[] c = clamped(scale, tx, ty);
            tx = c[0];
            ty = c[1];
        }
        notifyZoom();
        invalidate();
    }

    /** translation kept inside the photo: centered on an axis where it's smaller than the view */
    private float[] clamped(float s, float x, float y) {
        float w = bitmap.getWidth() * s, h = bitmap.getHeight() * s;
        x = w <= getWidth() ? (getWidth() - w) / 2 : Math.max(getWidth() - w, Math.min(0, x));
        y = h <= getHeight() ? (getHeight() - h) / 2 : Math.max(getHeight() - h, Math.min(0, y));
        return new float[]{x, y};
    }

    private void settle() {
        if (bitmap == null) return;
        float s = Math.max(fitScale, Math.min(maxScale(), scale));
        if (s != scale) {
            // zoom back into range around the middle of the view
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float nx = cx - (cx - tx) * s / scale, ny = cy - (cy - ty) * s / scale;
            float[] c = clamped(s, nx, ny);
            animateTo(s, c[0], c[1]);
        } else {
            float[] c = clamped(s, tx, ty);
            if (c[0] != tx || c[1] != ty) animateTo(s, c[0], c[1]);
        }
    }

    private void animateTo(float s, float x, float y) {
        stopMotion();
        final float s0 = scale, x0 = tx, y0 = ty;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            scale = s0 + (s - s0) * f;
            tx = x0 + (x - x0) * f;
            ty = y0 + (y - y0) * f;
            notifyZoom();
            invalidate();
        });
        animator.start();
    }

    private void stopMotion() {
        if (animator != null) animator.cancel();
        animator = null;
        scroller.forceFinished(true);
    }

    private void notifyZoom() {
        boolean z = isZoomed();
        if (z != wasZoomed) {
            wasZoomed = z;
            if (listener != null) listener.onZoomChanged(z);
        }
    }

    // touch ----------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null) {
            gestureDetector.onTouchEvent(e);
            return true;
        }
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopMotion();
                // until we know which way this goes, the pager doesn't get it
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                downInMap = inMap(e.getX(), e.getY());
                mode = downInMap ? MAP : NONE;
                if (mode == MAP) panToMap(e.getX(), e.getY());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mode == DISMISS) resetDismiss();
                mode = NONE;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress() || e.getPointerCount() > 1 || gestureInDoubleTap) {
                    lastX = e.getX();
                    lastY = e.getY();
                    break;
                }
                float x = e.getX(), y = e.getY();
                if (mode == NONE) decide(x - downX, y - downY);
                if (mode == PAN) {
                    float[] c = clamped(scale, tx + x - lastX, ty + y - lastY);
                    tx = c[0];
                    ty = c[1];
                    invalidate();
                } else if (mode == DISMISS) {
                    dismissY = y - downY;
                    if (listener != null) listener.onDismissProgress(dismissProgress());
                    invalidate();
                } else if (mode == MAP) {
                    panToMap(x, y);
                }
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // carry on panning with the finger that stays
                int up = e.getActionIndex(), keep = up == 0 ? 1 : 0;
                lastX = e.getX(keep);
                lastY = e.getY(keep);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000);
                float vx = velocity.getXVelocity(), vy = velocity.getYVelocity();
                if (mode == PAN && Math.hypot(vx, vy) > minFling) fling(vx, vy);
                else if (mode == DISMISS) endDismiss(vy, e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                else if (!scaleDetector.isInProgress() && animator == null) settle();
                velocity.recycle();
                velocity = null;
                mode = NONE;
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) gestureInDoubleTap = false;
                break;
        }
        return true;
    }

    private void decide(float dx, float dy) {
        if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) return;
        boolean horizontal = Math.abs(dx) > Math.abs(dy);
        if (isZoomed()) {
            float w = bitmap.getWidth() * scale;
            boolean atLeft = tx >= -1, atRight = tx + w <= getWidth() + 1;
            if (horizontal && ((dx > 0 && atLeft) || (dx < 0 && atRight))) giveToParent();
            else mode = PAN;
        } else if (!horizontal) {
            mode = DISMISS;
        } else {
            giveToParent();
        }
    }

    private void giveToParent() {
        mode = PARENT;
        getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void fling(float vx, float vy) {
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        int minX = (int) Math.min(0, getWidth() - w), minY = (int) Math.min(0, getHeight() - h);
        scroller.fling((int) tx, (int) ty, (int) vx, (int) vy, minX, 0, minY, 0);
        postOnAnimation(flingStep);
    }

    private final Runnable flingStep = new Runnable() {
        @Override
        public void run() {
            if (bitmap == null || !scroller.computeScrollOffset()) return;
            float[] c = clamped(scale, scroller.getCurrX(), scroller.getCurrY());
            tx = c[0];
            ty = c[1];
            invalidate();
            postOnAnimation(this);
        }
    };

    // drag to close ----------------------------------------------------
    private float dismissProgress() {
        return Math.min(1, Math.abs(dismissY) / (getHeight() * 0.5f));
    }

    private void endDismiss(float vy, boolean cancelled) {
        boolean go = !cancelled && (Math.abs(dismissY) > getHeight() * 0.18f || (Math.abs(vy) > minFling * 8 && Math.signum(vy) == Math.signum(dismissY)));
        float from = dismissY, to = go ? Math.signum(dismissY == 0 ? 1 : dismissY) * getHeight() : 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(go ? 180 : 220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            dismissY = from + (to - from) * (float) a.getAnimatedValue();
            if (listener != null) listener.onDismissProgress(go ? 1 : dismissProgress());
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (go && listener != null) listener.onDismiss();
            }
        });
        animator.start();
    }

    private void resetDismiss() {
        if (dismissY != 0 && listener != null) listener.onDismissProgress(0);
        dismissY = 0;
    }

    // mini-map -----------------------------------------------------------
    private final Paint mapFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapView = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mapRect = new RectF();
    private final RectF viewRect = new RectF();
    private final Path shade = new Path();
    /** how far down the mini-map sits, below the gallery's top bar when it shows */
    float mapTop = 0;

    private RectF layoutMap() {
        float longSide = 112 * dp;
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float k = longSide / Math.max(bw, bh);
        float w = bw * k, h = bh * k;
        float margin = 14 * dp;
        mapRect.set(getWidth() - margin - w, mapTop + margin, getWidth() - margin, mapTop + margin + h);
        return mapRect;
    }

    private boolean inMap(float x, float y) {
        if (!isZoomed()) return false;
        RectF r = layoutMap();
        float pad = 10 * dp;
        return x > r.left - pad && x < r.right + pad && y > r.top - pad && y < r.bottom + pad;
    }

    /** dragging on the mini-map moves the view there */
    private void panToMap(float x, float y) {
        RectF r = layoutMap();
        float fx = (x - r.left) / r.width(), fy = (y - r.top) / r.height();
        float bx = fx * bitmap.getWidth() * scale, by = fy * bitmap.getHeight() * scale;
        float[] c = clamped(scale, getWidth() / 2f - bx, getHeight() / 2f - by);
        tx = c[0];
        ty = c[1];
        invalidate();
    }

    private void drawMap(Canvas canvas) {
        RectF r = layoutMap();
        float k = r.width() / bitmap.getWidth();
        canvas.save();
        float corner = 6 * dp;
        Path clip = new Path();
        clip.addRoundRect(r, corner, corner, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(bitmap, null, r, bitmapPaint);
        // the visible part, in mini-map coordinates
        float l = r.left + (-tx / scale) * k, t = r.top + (-ty / scale) * k;
        viewRect.set(l, t, l + getWidth() / scale * k, t + getHeight() / scale * k);
        viewRect.intersect(r);
        shade.reset();
        shade.setFillType(Path.FillType.EVEN_ODD);
        shade.addRect(r, Path.Direction.CW);
        shade.addRect(viewRect, Path.Direction.CW);
        canvas.drawPath(shade, mapShade);
        canvas.restore();
        canvas.drawRoundRect(r, corner, corner, mapFrame);
        canvas.drawRect(viewRect, mapView);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(tx, ty);
        if (dismissY != 0) {
            // follows the finger, shrinking a little
            float shrink = 1 - 0.25f * dismissProgress();
            matrix.postScale(shrink, shrink, getWidth() / 2f, getHeight() / 2f);
            matrix.postTranslate(0, dismissY);
        }
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        if (isZoomed() && dismissY == 0) drawMap(canvas);
    }
}
