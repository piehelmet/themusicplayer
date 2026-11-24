package com.themusicplayer.app;

import android.graphics.Color;
import android.graphics.Typeface;

/** Palettes for the three UI moods: Obsidian, Ivory and Aurora. */
public final class Theme {
    public static final int OBSIDIAN = 0;
    public static final int IVORY = 1;
    public static final int AURORA = 2;

    private static int mode = OBSIDIAN;

    private Theme() {}

    public static void set(int value) {
        mode = value == IVORY ? IVORY : value == AURORA ? AURORA : OBSIDIAN;
    }

    public static int mode() { return mode; }

    public static int next() {
        return isLight() ? AURORA : mode == AURORA ? OBSIDIAN : IVORY;
    }

    public static boolean isLight() { return mode == IVORY; }

    public static String name() {
        return mode == IVORY ? "Ivory" : mode == AURORA ? "Aurora" : "Obsidian";
    }

    /** Keep cover-art colour readable in Ivory, and pull Aurora toward its mint signature. */
    public static int accent(int fromArt) {
        if (fromArt == 0) return baseAccent();
        float[] hsv = new float[3];
        Color.colorToHSV(fromArt, hsv);
        if (isLight()) {
            return Color.HSVToColor(0xFF, new float[]{
                    hsv[0], Math.min(hsv[1], 0.42f), Math.min(hsv[2], 0.68f)});
        }
        if (mode == AURORA) {
            float shift = ((165f - hsv[0] + 540f) % 360f) - 180f;
            float hue = (hsv[0] + shift * 0.65f + 360f) % 360f;
            return Color.HSVToColor(0xFF, new float[]{
                    hue, Math.min(hsv[1], 0.62f), Math.max(0.58f, Math.min(hsv[2], 0.92f))});
        }
        return fromArt;
    }

    public static int baseAccent() {
        if (isLight()) return 0xFF9A7642;
        if (mode == AURORA) return 0xFF5FE3C0;
        return 0xFF5B8CFF;
    }

    public static int root() {
        if (isLight()) return 0xFFF4F0E8;
        if (mode == AURORA) return 0xFF06171A;
        return 0xFF08090E;
    }

    public static int gradientStart() {
        if (isLight()) return 0xFFF9F5EE;
        if (mode == AURORA) return 0xFF051416;
        return 0xFF08090E;
    }

    public static int gradientEnd() {
        if (isLight()) return 0xFFE7DDCC;
        if (mode == AURORA) return 0xFF0B2A34;
        return 0xFF121423;
    }

    public static int overlay() {
        if (isLight()) return 0xDFF4F0E8;
        if (mode == AURORA) return 0xDC06171A;
        return 0xC6080A12;
    }

    public static int cardFill() {
        if (isLight()) return 0xE6FFFFFF;
        if (mode == AURORA) return Color.argb(30, 190, 255, 238);
        return Color.argb(34, 255, 255, 255);
    }

    public static int cardStroke() {
        if (isLight()) return 0x332B2118;
        if (mode == AURORA) return Color.argb(52, 95, 227, 192);
        return Color.argb(46, 255, 255, 255);
    }

    public static int accentCardFill(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(isLight() ? 30 : 24, r, g, b);
    }

    public static int accentCardStroke(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(isLight() ? 95 : 60, r, g, b);
    }

    /** Unmistakable tint for the currently playing row in every theme. */
    public static int activeCardFill(int accent) {
        if (isLight()) return 0xFFF0E1C4;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(mode == AURORA ? 66 : 52, r, g, b);
    }

    public static int activeCardStroke(int accent) {
        if (isLight()) return 0xFFB38643;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(mode == AURORA ? 190 : 130, r, g, b);
    }

    public static int activeText() {
        if (isLight()) return 0xFF79521A;
        if (mode == AURORA) return 0xFF9DFFE4;
        return 0xFFA6CBFF;
    }

    public static int chipFill(boolean selected, int accent) {
        if (selected) return accent;
        if (isLight()) return 0xD9FFFFFF;
        if (mode == AURORA) return Color.argb(30, 190, 255, 238);
        return Color.argb(26, 255, 255, 255);
    }

    public static int chipStroke(boolean selected, int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (selected) return Color.argb(220, r, g, b);
        if (isLight()) return 0x442B2118;
        if (mode == AURORA) return Color.argb(70, 95, 227, 192);
        return Color.argb(40, 255, 255, 255);
    }

    public static int chipText(boolean selected) {
        if (isLight()) return selected ? 0xFFFFFFFF : 0xFF4A4136;
        if (mode == AURORA) return selected ? 0xFF06231E : 0xFFD9FFF6;
        return selected ? 0xFF0B0D16 : 0xFFE4EAFF;
    }

    public static int circleFill(boolean filled, int accent) {
        if (filled) return accent;
        if (isLight()) return 0xCFFFFFFF;
        if (mode == AURORA) return Color.argb(34, 190, 255, 238);
        return Color.argb(30, 255, 255, 255);
    }

    public static int circleStroke(boolean filled, int accent) {
        if (filled) return 0;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (isLight()) return Color.argb(110, r, g, b);
        if (mode == AURORA) return Color.argb(90, 95, 227, 192);
        return Color.argb(70, r, g, b);
    }

    public static int onAccent() {
        if (isLight() || mode == AURORA) return 0xFF06231E;
        return 0xFF0B0D16;
    }

    public static int textPrimary() {
        if (isLight()) return 0xFF25211C;
        if (mode == AURORA) return 0xFFEAFFF9;
        return 0xFFFFFFFF;
    }

    public static int sub() {
        if (isLight()) return 0xFF6C6155;
        if (mode == AURORA) return 0xFF9FD8CC;
        return 0xA9B8D8;
    }

    public static int muted() {
        if (isLight()) return 0xFF988C7D;
        if (mode == AURORA) return 0xFF6F9E96;
        return 0x7182A8;
    }

    public static int section() {
        if (isLight()) return 0xFF8A6E3D;
        if (mode == AURORA) return 0xFF7FE9CE;
        return 0x99C6D2FF;
    }

    public static int icon() {
        if (isLight()) return 0xFF9A7642;
        if (mode == AURORA) return 0xFF7FE9CE;
        return 0x9FB4FF;
    }

    public static int hint() {
        if (isLight()) return 0xFF9C9183;
        if (mode == AURORA) return 0xFF6F9E96;
        return 0x8C9BBF;
    }

    public static int lyrics() {
        if (isLight()) return 0xFF4A4136;
        if (mode == AURORA) return 0xFFDFF8F1;
        return 0xFFE8ECFF;
    }

    public static int sheetFill() {
        if (isLight()) return 0xFFFBF8F2;
        if (mode == AURORA) return 0xF2092327;
        return 0xF2121420;
    }

    public static int sheetStroke() {
        if (isLight()) return 0x332B2118;
        if (mode == AURORA) return 0x335FE3C0;
        return 0x22FFFFFF;
    }

    public static int handle() {
        if (isLight()) return 0x668A6E3D;
        if (mode == AURORA) return 0x665FE3C0;
        return 0x66FFFFFF;
    }

    public static int rule() { return 0x338A6E3D; }

    public static int orbAlpha(int index) {
        if (isLight()) return index == 0 ? 80 : 62;
        return index == 0 ? 150 : 120;
    }

    public static int visualizerAlpha() { return isLight() ? 175 : 210; }

    public static float backdropAlpha() {
        if (isLight()) return 0.12f;
        if (mode == AURORA) return 0.18f;
        return 0.20f;
    }

    public static float blurAlpha() {
        if (isLight()) return 0.14f;
        if (mode == AURORA) return 0.24f;
        return 0.26f;
    }

    public static float brandSpacing() {
        if (isLight()) return 0.03f;
        if (mode == AURORA) return 0.12f;
        return 0f;
    }

    public static Typeface display() {
        if (isLight()) return Typeface.create(Typeface.SERIF, Typeface.BOLD);
        if (mode == AURORA) return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    }
}
