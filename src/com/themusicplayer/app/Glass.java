package com.themusicplayer.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

/** Factory for frosted-glass style drawables in the available UI themes. */
public final class Glass {
    private Glass() {}

    private static boolean glass() { return Theme.mode() == Theme.GLASS; }

    private static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int light(int c, float t) { return mix(c, 0xFFFFFFFF, t); }
    private static int dark(int c, float t) { return mix(c, 0xFF000000, t); }

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

    /** Readable, mostly opaque surface for dense controls such as the Sound panel. */
    public static GradientDrawable panel(float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        if (glass()) {
            d.setColors(new int[]{0xF617263A, 0xF50A121D});
            d.setStroke(1, Color.argb(150, 190, 220, 255));
        } else {
            d.setColors(new int[]{
                    Theme.isLight() ? 0xFBFFFFFF : 0xF41C2030,
                    Theme.isLight() ? 0xF2F2F6FB : 0xF40E1018});
            d.setStroke(1, Theme.cardStroke());
        }
        return d;
    }

    /** Bevelled groove behind a slider: darker centre, lit rim. */
    public static Drawable sliderTrack(int accent, int radius) {
        GradientDrawable bevel = new GradientDrawable();
        bevel.setShape(GradientDrawable.RECTANGLE);
        bevel.setCornerRadius(radius);
        bevel.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bevel.setColors(glass()
                ? new int[]{0xFF050A11, 0xFF26374C}
                : new int[]{0xFFB7C2D1, 0xFFF8FAFD});
        bevel.setStroke(1, glass() ? Color.argb(140, 190, 220, 255) : 0x33000000);

        GradientDrawable groove = new GradientDrawable();
        groove.setShape(GradientDrawable.RECTANGLE);
        groove.setCornerRadius(radius);
        groove.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        groove.setColors(glass()
                ? new int[]{0xFF01050A, 0xFF0D1825}
                : new int[]{0xFFC6D0DD, 0xFFEDF1F7});

        return new LayerDrawable(new Drawable[]{
                bevel, new InsetDrawable(groove, 2, 2, 2, 2)});
    }

    /** Rounded, top-lit accent fill with a specular strip and a clip for SeekBar. */
    public static Drawable sliderProgress(int accent, int radius) {
        GradientDrawable body = new GradientDrawable();
        body.setShape(GradientDrawable.RECTANGLE);
        body.setCornerRadius(radius);
        body.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        body.setColors(new int[]{light(accent, 0.45f), accent, dark(accent, 0.35f)});

        GradientDrawable shine = new GradientDrawable();
        shine.setShape(GradientDrawable.RECTANGLE);
        shine.setCornerRadius(radius);
        shine.setColors(new int[]{Color.argb(105, 255, 255, 255), Color.argb(0, 255, 255, 255)});

        LayerDrawable fill = new LayerDrawable(new Drawable[]{body, shine});
        return new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
    }

    /** Raised thumb with a shadow, dark rim, gradient body and specular dot. */
    public static Drawable sliderThumb(int accent, int size) {
        GradientDrawable shadow = new GradientDrawable();
        shadow.setShape(GradientDrawable.OVAL);
        shadow.setColor(Color.argb(110, 0, 0, 0));

        GradientDrawable rim = new GradientDrawable();
        rim.setShape(GradientDrawable.OVAL);
        rim.setColor(dark(accent, 0.68f));

        GradientDrawable body = new GradientDrawable();
        body.setShape(GradientDrawable.OVAL);
        body.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        body.setColors(new int[]{light(accent, 0.65f), accent, dark(accent, 0.28f)});
        body.setStroke(1, Color.argb(170, 255, 255, 255));

        GradientDrawable spec = new GradientDrawable();
        spec.setShape(GradientDrawable.OVAL);
        spec.setColor(Color.argb(150, 255, 255, 255));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, rim, body, spec});
        layers.setLayerInset(0, 1, 3, 1, 0);
        layers.setLayerInset(1, 1, 1, 1, 1);
        layers.setLayerInset(2, 3, 3, 3, 3);
        layers.setLayerInset(3, size / 3, size / 4, size / 3, size / 2);
        return new SizedDrawable(layers, size, size);
    }

    /** Gives a layer drawable explicit intrinsic bounds so SeekBar can size its thumb. */
    private static final class SizedDrawable extends Drawable {
        private final Drawable inner;
        private final int width, height;

        SizedDrawable(Drawable inner, int width, int height) {
            this.inner = inner;
            this.width = width;
            this.height = height;
        }

        @Override public void draw(Canvas canvas) { inner.draw(canvas); }

        @Override public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            inner.setBounds(left, top, right, bottom);
        }

        @Override public void setAlpha(int alpha) { inner.setAlpha(alpha); }

        @Override public void setColorFilter(ColorFilter filter) { inner.setColorFilter(filter); }

        @Override public int getOpacity() { return inner.getOpacity(); }

        @Override public int getIntrinsicWidth() { return width; }

        @Override public int getIntrinsicHeight() { return height; }
    }
}
