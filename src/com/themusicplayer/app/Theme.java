package com.themusicplayer.app;

import android.graphics.Color;
import android.graphics.Typeface;

/** Palettes for the two UI moods: Obsidian (default) and Ivory. */
public final class Theme {
    public static final int OBSIDIAN = 0;
    public static final int IVORY = 1;

    private static int mode = OBSIDIAN;

    private Theme() {}

    public static void set(int value) { mode = value == IVORY ? IVORY : OBSIDIAN; }

    public static int mode() { return mode; }

    public static int next() { return isLight() ? OBSIDIAN : IVORY; }

    public static boolean isLight() { return mode == IVORY; }

    public static String name() { return isLight() ? "Ivory" : "Obsidian"; }

    /** Deepen a cover-art colour so it stays legible on light surfaces. */
    public static int accent(int fromArt) {
        if (!isLight() || fromArt == 0) return fromArt;
        float[] hsv = new float[3];
        Color.colorToHSV(fromArt, hsv);
        return Color.HSVToColor(0xFF, new float[]{
                hsv[0], Math.min(hsv[1], 0.42f), Math.min(hsv[2], 0.68f)});
    }

    public static int baseAccent() { return isLight() ? 0xFF9A7642 : 0xFF5B8CFF; }

    public static int root() { return isLight() ? 0xFFF4F0E8 : 0xFF08090E; }

    public static int gradientStart() { return isLight() ? 0xFFF9F5EE : 0xFF08090E; }

    public static int gradientEnd() { return isLight() ? 0xFFE7DDCC : 0xFF121423; }

    public static int overlay() { return isLight() ? 0xDFF4F0E8 : 0xC6080A12; }

    public static int cardFill() {
        return isLight() ? 0xE6FFFFFF : Color.argb(34, 255, 255, 255);
    }

    public static int cardStroke() {
        return isLight() ? 0x332B2118 : Color.argb(46, 255, 255, 255);
    }

    public static int accentCardFill(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(isLight() ? 30 : 24, r, g, b);
    }

    public static int accentCardStroke(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(isLight() ? 95 : 60, r, g, b);
    }

    public static int chipFill(boolean selected, int accent) {
        if (selected) return accent;
        return isLight() ? 0xD9FFFFFF : Color.argb(26, 255, 255, 255);
    }

    public static int chipStroke(boolean selected, int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (selected) return Color.argb(220, r, g, b);
        return isLight() ? 0x442B2118 : Color.argb(40, 255, 255, 255);
    }

    public static int chipText(boolean selected) {
        if (isLight()) return selected ? 0xFFFFFFFF : 0xFF4A4136;
        return selected ? 0xFF0B0D16 : 0xFFE4EAFF;
    }

    public static int circleFill(boolean filled, int accent) {
        if (filled) return accent;
        return isLight() ? 0xCFFFFFFF : Color.argb(30, 255, 255, 255);
    }

    public static int circleStroke(boolean filled, int accent) {
        if (filled) return 0;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(isLight() ? 110 : 70, r, g, b);
    }

    public static int onAccent() { return isLight() ? 0xFFFFFFFF : 0xFF0B0D16; }

    public static int textPrimary() { return isLight() ? 0xFF25211C : 0xFFFFFFFF; }

    public static int sub() { return isLight() ? 0xFF6C6155 : 0xA9B8D8; }

    public static int muted() { return isLight() ? 0xFF988C7D : 0x7182A8; }

    public static int section() { return isLight() ? 0xFF8A6E3D : 0x99C6D2FF; }

    public static int icon() { return isLight() ? 0xFF9A7642 : 0x9FB4FF; }

    public static int hint() { return isLight() ? 0xFF9C9183 : 0x8C9BBF; }

    public static int lyrics() { return isLight() ? 0xFF4A4136 : 0xFFE8ECFF; }

    public static int sheetFill() { return isLight() ? 0xFFFBF8F2 : 0xF2121420; }

    public static int sheetStroke() { return isLight() ? 0x332B2118 : 0x22FFFFFF; }

    public static int handle() { return isLight() ? 0x668A6E3D : 0x66FFFFFF; }

    public static int rule() { return isLight() ? 0x338A6E3D : 0x14FFFFFF; }

    public static int orbAlpha(int index) {
        if (isLight()) return index == 0 ? 80 : 62;
        return index == 0 ? 150 : 120;
    }

    public static int visualizerAlpha() { return isLight() ? 175 : 210; }

    public static float backdropAlpha() { return isLight() ? 0.12f : 0.20f; }

    public static float blurAlpha() { return isLight() ? 0.14f : 0.26f; }

    public static Typeface display() {
        return Typeface.create(isLight() ? Typeface.SERIF : Typeface.DEFAULT, Typeface.BOLD);
    }
}
