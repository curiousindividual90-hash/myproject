package com.satmeter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Segmented signal bar with a label and a percentage. */
public class BarView extends View {
    private static final int SEGMENTS = 25;

    private final String label;
    private int value;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public BarView(Context context, String label) {
        super(context);
        this.label = label;
    }

    public void setValue(int v) {
        value = Math.max(0, Math.min(100, v));
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), (int) dp(84));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(16));
        c.drawText(label, 0, dp(18), paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(dp(22));
        c.drawText(value + "%", w, dp(22), paint);
        paint.setTextAlign(Paint.Align.LEFT);

        float top = dp(36);
        float bottom = dp(80);
        float gap = dp(3);
        float segW = (w - gap * (SEGMENTS - 1)) / SEGMENTS;
        int filled = Math.round(value * SEGMENTS / 100f);
        for (int i = 0; i < SEGMENTS; i++) {
            float left = i * (segW + gap);
            rect.set(left, top, left + segW, bottom);
            if (i < filled) {
                if (i < SEGMENTS * 0.3) {
                    paint.setColor(0xFFFF5252);
                } else if (i < SEGMENTS * 0.6) {
                    paint.setColor(0xFFFFC107);
                } else {
                    paint.setColor(0xFF3DDC84);
                }
            } else {
                paint.setColor(0xFF2A3038);
            }
            c.drawRoundRect(rect, dp(3), dp(3), paint);
        }
    }
}
