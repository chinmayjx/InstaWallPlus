package cj.instawall.plus;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

public class CJImage {
    public static class Transform {
        public float pivotX, pivotY, translateX, translateY, scaleFactor, rotation;
        public static Transform DEFAULT = new Transform();
        public Transform target;

        public Transform(float pivotX, float pivotY, float translateX, float translateY, float scaleFactor, float rotation) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.translateX = translateX;
            this.translateY = translateY;
            this.scaleFactor = scaleFactor;
            this.rotation = rotation;
        }

        public Transform() {
            this(0, 0, 0, 0, 1, 0);
        }

        public void absoluteToTarget(float len) {
            rotation += (target.rotation - rotation) * len;
            translateX += (target.translateX - translateX) * len;
            translateY += (target.translateY - translateY) * len;
            scaleFactor += (target.scaleFactor - scaleFactor) * len;
        }

        public float distanceToTarget() {
            return (float) Math.sqrt(
                    (rotation - target.rotation) * (rotation - target.rotation)
                            + (translateX - target.translateX) * (translateX - target.translateX)
                            + (translateY - target.translateY) * (translateY - target.translateY)
                            + (scaleFactor - target.scaleFactor) * (scaleFactor - target.scaleFactor)
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
        this.bitmap = bitmap;
        this.position = position;
    }

    public void drawOnCanvas(Canvas canvas, Paint paint) {
        canvas.save();
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
    }
}
