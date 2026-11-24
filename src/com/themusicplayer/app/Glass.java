package com.themusicplayer.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Factory for frosted-glass style drawables. */
public final class Glass {
    private Glass() {}

    public static GradientDrawable card(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(Color.argb(34, 255, 255, 255));
        d.setStroke(1, Color.argb(46, 255, 255, 255));
        return d;
    }

    public static GradientDrawable card(int accent, float radius) {
        GradientDrawable d = card(radius);
        d.setColor(Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent)));
        d.setStroke(1, Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return d;
    }

    public static GradientDrawable pill(int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent)));
        d.setStroke(1, Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return d;
    }

    /** Selected/unselected filter chip. */
    public static GradientDrawable chip(boolean selected, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        d.setColor(selected ? Color.rgb(r, g, b) : Color.argb(26, 255, 255, 255));
        d.setStroke(1, selected ? Color.argb(220, r, g, b) : Color.argb(40, 255, 255, 255));
        return d;
    }

    /** Circular transport button, filled for the primary action. */
    public static GradientDrawable circle(boolean filled, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        d.setColor(filled ? Color.rgb(r, g, b) : Color.argb(30, 255, 255, 255));
        d.setStroke(filled ? 0 : 1, Color.argb(70, r, g, b));
        return d;
    }

    /** Bottom sheet with rounded top corners. */
    public static GradientDrawable sheet(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        d.setColor(0xF2121420);
        d.setStroke(1, 0x22FFFFFF);
        return d;
    }

    /** Thin rounded handle at the top of the sheet. */
    public static GradientDrawable handle() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(0x66FFFFFF);
        return d;
    }
}
