package com.themusicplayer.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Factory for frosted-glass style drawables in the available UI themes. */
public final class Glass {
    private Glass() {}

    private static boolean glass() { return Theme.mode() == Theme.GLASS; }

    /** Base frosted surface: a top-lit gradient in Glass, a flat translucent fill elsewhere. */
    private static GradientDrawable surface(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Theme.glassTop(), Theme.glassBottom()});
        } else {
            d.setColor(Theme.cardFill());
        }
        d.setStroke(1, Theme.cardStroke());
        return d;
    }

    private static GradientDrawable tinted(int accent, float radius, int alphaTop, int alphaBottom,
                                           int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Color.argb(alphaTop, r, g, b), Color.argb(alphaBottom, r, g, b)});
        } else {
            d.setColor(Color.argb(alphaBottom, r, g, b));
        }
        d.setStroke(1, stroke);
        return d;
    }

    public static GradientDrawable card(float radius) {
        return surface(radius);
    }

    public static GradientDrawable card(int accent, float radius) {
        if (glass()) return tinted(accent, radius, 52, 24, Theme.accentCardStroke(accent));
        GradientDrawable d = surface(radius);
        d.setColor(Theme.accentCardFill(accent));
        d.setStroke(1, Theme.accentCardStroke(accent));
        return d;
    }

    /** Stronger card for the currently playing row. */
    public static GradientDrawable activeCard(int accent, float radius) {
        if (glass()) return tinted(accent, radius, 112, 62, Theme.activeCardStroke(accent));
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
        if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Theme.glassTop(), Theme.glassBottom()});
        } else {
            d.setColor(Theme.circleFill(false, accent));
        }
        d.setStroke(1, Theme.circleStroke(false, accent));
        return d;
    }

    /** Selected/unselected filter chip. */
    public static GradientDrawable chip(boolean selected, int accent) {
        if (selected) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.RECTANGLE);
            d.setCornerRadius(999f);
            d.setColor(accent);
            d.setStroke(1, Theme.chipStroke(true, accent));
            return d;
        }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Theme.glassTop(), Theme.glassBottom()});
        } else {
            d.setColor(Theme.chipFill(false, accent));
        }
        d.setStroke(1, Theme.chipStroke(false, accent));
        return d;
    }

    /** Circular transport button, filled for the primary action. */
    public static GradientDrawable circle(boolean filled, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (filled) {
            d.setColor(accent);
        } else if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Theme.glassTop(), Theme.glassBottom()});
        } else {
            d.setColor(Theme.circleFill(false, accent));
        }
        d.setStroke(filled ? 0 : 1, Theme.circleStroke(filled, accent));
        return d;
    }

    /** Bottom sheet with rounded top corners. */
    public static GradientDrawable sheet(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        if (glass()) {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{Theme.glassSheetTop(), Theme.glassSheetBottom()});
        } else {
            d.setColor(Theme.sheetFill());
        }
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
