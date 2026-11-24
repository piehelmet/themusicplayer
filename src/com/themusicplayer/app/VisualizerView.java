package com.themusicplayer.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Decorative animated spectrum bars, driven by playback state. */
public class VisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator anim;
    private float phase;
    private boolean playing;
    private int accent = 0xFF6FA8FF;

    public VisualizerView(Context c) { this(c, null); }

    public VisualizerView(Context c, AttributeSet attrs) {
        super(c, attrs);
        anim = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        anim.setDuration(1400);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    public void setPlaying(boolean on) { playing = on; invalidate(); }
    public void setAccent(int color) { accent = color; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int bars = 28;
        float gap = w / (float) bars;
        for (int i = 0; i < bars; i++) {
            float base = playing
                    ? 0.25f + 0.75f * (float) Math.abs(Math.sin(phase + i * 0.55f) * Math.cos(phase * 0.6f + i * 0.21f))
                    : 0.08f + 0.05f * (float) Math.sin(i);
            float bh = Math.max(3f, base * h);
            paint.setColor(Color.argb(playing ? 210 : 90,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            c.drawRoundRect(i * gap + gap * 0.22f, h - bh, gap * 0.56f, bh, gap * 0.28f, gap * 0.28f, paint);
        }
    }

    @Override protected void onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow(); }
}
