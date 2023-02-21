package cj.instawall.plus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoadingView extends View {
    public static final String TAG = "CJ";
    int sqw = 0, sqh = 0;

    public LoadingView(Context context) {
        super(context);
    }

    public LoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LoadingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LoadingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    int rx, ry;
    Paint paint;

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (h == 0) return;
        paint = new Paint();
        sqw = h;
        sqh = h;
        ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
        se.scheduleAtFixedRate(this::postInvalidate, 0, 100, TimeUnit.MILLISECONDS);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < getWidth(); i += sqw) {
            for (int j = 0; j < getHeight(); j += sqh) {
                paint.setColor(Math.random() < 0.5 ? Color.WHITE : Color.BLACK);
                canvas.drawRect(i, j, i + sqw, j + sqh, paint);
            }
        }
    }
}
