package com.themusicplayer.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Animated, translucent ambient gradient backdrop driven by the current accent colour. */
public class AmbientBackground extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = 0xFF5B8CFF;
    private int accent2 = 0xFFB06BFF;
    private float phase;
    private final ValueAnimator drift;

    public AmbientBackground(Context c) { this(c, null); }

    public AmbientBackground(Context c, AttributeSet attrs) {
        super(c, attrs);
        drift = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        drift.setDuration(18000);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        drift.start();
    }

    public void setAccent(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (Theme.isLight()) {
            accent = Color.HSVToColor(0xFF,
                    new float[]{hsv[0], Math.min(hsv[1], 0.30f), 0.88f});
            accent2 = Color.HSVToColor(0xFF,
                    new float[]{(hsv[0] + 28) % 360, 0.20f, 0.82f});
        } else {
            accent = Color.HSVToColor(0xFF,
                    new float[]{(hsv[0] + 355) % 360, 0.55f, 0.34f});
            accent2 = Color.HSVToColor(0xFF,
                    new float[]{(hsv[0] + 45) % 360, 0.65f, 0.24f});
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        paint.setShader(new LinearGradient(0, 0, w, h, Theme.gradientStart(),
                Theme.gradientEnd(), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);

        float ax = w * (0.25f + 0.18f * (float) Math.sin(phase));
        float ay = h * (0.18f + 0.12f * (float) Math.cos(phase * 0.8f));
        drawOrb(c, ax, ay, Math.max(w, h) * 0.62f, Color.argb(Theme.orbAlpha(0),
                Color.red(accent), Color.green(accent), Color.blue(accent)));

        float bx = w * (0.78f + 0.15f * (float) Math.cos(phase * 0.7f));
        float by = h * (0.72f + 0.14f * (float) Math.sin(phase * 1.1f));
        drawOrb(c, bx, by, Math.max(w, h) * 0.55f, Color.argb(Theme.orbAlpha(1),
                Color.red(accent2), Color.green(accent2), Color.blue(accent2)));

        paint.setShader(null);
        paint.setColor(Theme.isLight() ? 0x05FFFFFF : 0x08000000);
        c.drawRect(0, 0, w, h, paint);
    }

    private void drawOrb(Canvas c, float x, float y, float r, int color) {
        paint.setShader(new RadialGradient(x, y, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, paint);
    }

    @Override protected void onDetachedFromWindow() {
        drift.cancel();
        super.onDetachedFromWindow();
    }
}
