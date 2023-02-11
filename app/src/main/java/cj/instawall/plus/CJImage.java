package cj.instawall.plus;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;

public class CJImage {
    public static final String TAG = "CJ";

    public static class Transform {
        public float pivotX, pivotY, translateX, translateY, scaleFactor, rotation, opacity;
        public Transform target;

        public Transform(float pivotX, float pivotY, float translateX, float translateY, float scaleFactor, float rotation, float opacity) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.translateX = translateX;
            this.translateY = translateY;
            this.scaleFactor = scaleFactor;
            this.rotation = rotation;
            this.opacity = opacity;
        }

        public Transform() {
            this(0, 0, 0, 0, 1, 0, 1);
        }

        public void absoluteToTarget(float len) {
            rotation += (target.rotation - rotation) * len;
            translateX += (target.translateX - translateX) * len;
            translateY += (target.translateY - translateY) * len;
            scaleFactor += (target.scaleFactor - scaleFactor) * len;
            opacity += (target.opacity - opacity) * len;
        }

        public float distanceToTarget() {
            return (float) Math.sqrt(
                    (rotation - target.rotation) * (rotation - target.rotation)
                            + (translateX - target.translateX) * (translateX - target.translateX)
                            + (translateY - target.translateY) * (translateY - target.translateY)
                            + (scaleFactor - target.scaleFactor) * (scaleFactor - target.scaleFactor)
                            + (opacity - target.opacity) * (opacity - target.opacity)
            );
        }
    }

    Transform transform;
    Bitmap bitmap;
    Point position;

    public CJImage(Bitmap bitmap, Point position) {
        this(bitmap, new Transform(), position);
    }

    public CJImage(Bitmap bitmap, Transform transform, Point position) {
        this.transform = transform;
        this.transform.target = new Transform();
        this.bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        this.position = position;
    }

    public void changeBitmap(Bitmap b, float containerWidth, float containerHeight) {
        int sh = (int) (containerWidth / (float) b.getWidth() * (float) b.getHeight());
        bitmap = Bitmap.createScaledBitmap(b, (int) containerWidth, sh, true);
        position.y = (int) ((containerHeight - bitmap.getHeight()) / 2);
    }

    public void drawOnCanvas(Canvas canvas, Paint paint) {
        canvas.save();
        int oldOpacity = paint.getAlpha();
        paint.setAlpha((int) (transform.opacity * 255));
        canvas.scale(transform.scaleFactor, transform.scaleFactor, transform.pivotX, transform.pivotY);
        canvas.rotate(transform.rotation, transform.pivotX, transform.pivotY);
        float nx, ny;
        float rad = (float) Math.toRadians(transform.rotation);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        nx = (float) (transform.translateX * cos + transform.translateY * sin);
        ny = (float) (-transform.translateX * sin + transform.translateY * cos);
        canvas.translate(nx / transform.scaleFactor, ny / transform.scaleFactor);
        canvas.drawBitmap(bitmap, position.x, position.y, paint);
        canvas.restore();
        paint.setAlpha(oldOpacity);
    }

    Thread loadThread;
    boolean loading = true;
    int loadSide = 300, loadN = 10;

    public void startLoading(Runnable invalidate) {
        loadThread = new Thread(() -> {
            try {
                int tlx = bitmap.getWidth() / 2 - loadSide / 2;
                int tly = bitmap.getHeight() / 2 - loadSide / 2;
                int ss = loadSide / loadN;
                for (; ; ) {
                    for (int i = tly; i < tly + loadSide; i += ss) {
                        for (int j = tlx; j < tlx + loadSide; j += ss) {
                            int clr = Math.random() < 0.5 ? 0 : -1;
                            for (int ii = 0; ii < ss; ii++) {
                                for (int jj = 0; jj < ss; jj++) {
                                    if(!loading) return;
                                    bitmap.setPixel(j + jj, i + ii, clr);
                                }
                            }
                        }
                    }
                    Thread.sleep(1000/15);
                    invalidate.run();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        loadThread.start();
    }

    public void stopLoading() {
        loading = false;
    }
}
