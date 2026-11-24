package com.themusicplayer.app;

import android.graphics.drawable.GradientDrawable;

/** Factory for frosted-glass style drawables in either UI theme. */
public final class Glass {
    private Glass() {}

    public static GradientDrawable card(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(Theme.cardFill());
        d.setStroke(1, Theme.cardStroke());
        return d;
    }

    public static GradientDrawable card(int accent, float radius) {
        GradientDrawable d = card(radius);
        d.setColor(Theme.accentCardFill(accent));
        d.setStroke(1, Theme.accentCardStroke(accent));
        return d;
    }

    /** Stronger card for the currently playing row. */
    public static GradientDrawable activeCard(int accent, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(Theme.activeCardFill(accent));
        d.setStroke(1, Theme.activeCardStroke(accent));
        return d;
    }

    public static GradientDrawable pill(int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(Theme.circleFill(false, accent));
        d.setStroke(1, Theme.circleStroke(false, accent));
        return d;
    }

    /** Selected/unselected filter chip. */
    public static GradientDrawable chip(boolean selected, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(Theme.chipFill(selected, accent));
        d.setStroke(1, Theme.chipStroke(selected, accent));
        return d;
    }

    /** Circular transport button, filled for the primary action. */
    public static GradientDrawable circle(boolean filled, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Theme.circleFill(filled, accent));
        d.setStroke(filled ? 0 : 1, Theme.circleStroke(filled, accent));
        return d;
    }

    /** Bottom sheet with rounded top corners. */
    public static GradientDrawable sheet(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        d.setColor(Theme.sheetFill());
        d.setStroke(1, Theme.sheetStroke());
        return d;
    }

    /** Thin rounded handle at the top of the sheet. */
    public static GradientDrawable handle() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(Theme.handle());
        return d;
    }
}
