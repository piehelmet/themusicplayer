package com.themusicplayer.app;

import android.graphics.Color;
import android.graphics.Typeface;

/** Palettes for the five UI moods: Obsidian, Ivory, Aurora, Porcelain and Glass. */
public final class Theme {
    public static final int OBSIDIAN = 0;
    public static final int IVORY = 1;
    public static final int AURORA = 2;
    public static final int PORCELAIN = 3;
    public static final int GLASS = 4;

    private static int mode = OBSIDIAN;

    private Theme() {}

    public static void set(int value) {
        if (value == IVORY) mode = IVORY;
        else if (value == AURORA) mode = AURORA;
        else if (value == PORCELAIN) mode = PORCELAIN;
        else if (value == GLASS) mode = GLASS;
        else mode = OBSIDIAN;
    }

    public static int mode() { return mode; }

    public static int next() {
        if (mode == OBSIDIAN) return IVORY;
        if (mode == IVORY) return AURORA;
        if (mode == AURORA) return PORCELAIN;
        if (mode == PORCELAIN) return GLASS;
        return OBSIDIAN;
    }

    private static boolean warmLight() { return mode == IVORY; }
    private static boolean coolLight() { return mode == PORCELAIN; }
    private static boolean glass() { return mode == GLASS; }

    public static boolean isLight() { return warmLight() || coolLight(); }

    public static String name() {
        if (warmLight()) return "Ivory";
        if (mode == AURORA) return "Aurora";
        if (coolLight()) return "Porcelain";
        if (glass()) return "Glass";
        return "Obsidian";
    }

    /** Keep cover art readable in light themes; pull Aurora toward its mint signature. */
    public static int accent(int fromArt) {
        if (fromArt == 0) return baseAccent();
        float[] hsv = new float[3];
        Color.colorToHSV(fromArt, hsv);
        if (glass()) {
            // Pull cover colours toward the cool iOS blue used by this theme.
            float shift = ((210f - hsv[0] + 540f) % 360f) - 180f;
            float hue = (hsv[0] + shift * 0.45f + 360f) % 360f;
            return Color.HSVToColor(0xFF, new float[]{
                    hue, Math.min(hsv[1], 0.58f), Math.max(0.74f, Math.min(hsv[2], 1f))});
        }
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
        if (glass()) return 0xFF0A84FF;
        if (coolLight()) return 0xFF2E7BFF;
        if (warmLight()) return 0xFF9A7642;
        if (mode == AURORA) return 0xFF5FE3C0;
        return 0xFF5B8CFF;
    }

    public static int root() {
        if (glass()) return 0xFF0A0F18;
        if (coolLight()) return 0xFFF5F7FA;
        if (warmLight()) return 0xFFF4F0E8;
        if (mode == AURORA) return 0xFF06171A;
        return 0xFF08090E;
    }

    public static int gradientStart() {
        if (glass()) return 0xFF08111C;
        if (coolLight()) return 0xFFFCFDFF;
        if (warmLight()) return 0xFFF9F5EE;
        if (mode == AURORA) return 0xFF051416;
        return 0xFF08090E;
    }

    public static int gradientEnd() {
        if (glass()) return 0xFF111C2B;
        if (coolLight()) return 0xFFE6ECF5;
        if (warmLight()) return 0xFFE7DDCC;
        if (mode == AURORA) return 0xFF0B2A34;
        return 0xFF121423;
    }

    public static int overlay() {
        if (glass()) return 0xE608111C;
        if (coolLight()) return 0xEFF5F7FA;
        if (warmLight()) return 0xDFF4F0E8;
        if (mode == AURORA) return 0xDC06171A;
        return 0xC6080A12;
    }

    public static int cardFill() {
        if (glass()) return Color.argb(38, 226, 240, 255);
        if (coolLight()) return 0xE8FFFFFF;
        if (warmLight()) return 0xE6FFFFFF;
        if (mode == AURORA) return Color.argb(30, 190, 255, 238);
        return Color.argb(34, 255, 255, 255);
    }

    public static int cardStroke() {
        if (glass()) return Color.argb(96, 224, 240, 255);
        if (coolLight()) return 0x2A2E7BFF;
        if (warmLight()) return 0x332B2118;
        if (mode == AURORA) return Color.argb(52, 95, 227, 192);
        return Color.argb(46, 255, 255, 255);
    }

    /** Subtle top-lit gradient used by the Glass theme's translucent surfaces. */
    public static int glassTop() { return Color.argb(54, 244, 249, 255); }
    public static int glassBottom() { return Color.argb(20, 178, 204, 242); }
    public static int glassSheetTop() { return Color.argb(92, 190, 215, 255); }
    public static int glassSheetBottom() { return 0xF20C1521; }

    public static int accentCardFill(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(glass() ? 36 : (isLight() ? 30 : 24), r, g, b);
    }

    public static int accentCardStroke(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(glass() ? 150 : (isLight() ? 95 : 60), r, g, b);
    }

    /** Unmistakable tint for the currently playing row in every theme. */
    public static int activeCardFill(int accent) {
        if (glass()) {
            int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
            return Color.argb(80, r, g, b);
        }
        if (warmLight()) return 0xFFF0E1C4;
        if (coolLight()) return 0xFFDCEAFF;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(mode == AURORA ? 66 : 52, r, g, b);
    }

    public static int activeCardStroke(int accent) {
        if (glass()) {
            int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
            return Color.argb(220, r, g, b);
        }
        if (warmLight()) return 0xFFB38643;
        if (coolLight()) return 0xFF2E7BFF;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        return Color.argb(mode == AURORA ? 190 : 130, r, g, b);
    }

    public static int activeText() {
        if (glass()) return 0xFF8CCBFF;
        if (warmLight()) return 0xFF79521A;
        if (coolLight()) return 0xFF1452A8;
        if (mode == AURORA) return 0xFF9DFFE4;
        return 0xFFA6CBFF;
    }

    public static int chipFill(boolean selected, int accent) {
        if (selected) return accent;
        if (glass()) return Color.argb(34, 255, 255, 255);
        if (coolLight()) return 0xE6FFFFFF;
        if (warmLight()) return 0xD9FFFFFF;
        if (mode == AURORA) return Color.argb(30, 190, 255, 238);
        return Color.argb(26, 255, 255, 255);
    }

    public static int chipStroke(boolean selected, int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (selected) return Color.argb(glass() ? 230 : 220, r, g, b);
        if (glass()) return Color.argb(95, 224, 240, 255);
        if (coolLight()) return Color.argb(70, 46, 123, 255);
        if (warmLight()) return 0x442B2118;
        if (mode == AURORA) return Color.argb(70, 95, 227, 192);
        return Color.argb(40, 255, 255, 255);
    }

    public static int chipText(boolean selected) {
        if (glass()) return selected ? 0xFF06121F : 0xFFE8F2FF;
        if (coolLight()) return selected ? 0xFFFFFFFF : 0xFF2E3948;
        if (warmLight()) return selected ? 0xFFFFFFFF : 0xFF4A4136;
        if (mode == AURORA) return selected ? 0xFF06231E : 0xFFD9FFF6;
        return selected ? 0xFF0B0D16 : 0xFFE4EAFF;
    }

    public static int circleFill(boolean filled, int accent) {
        if (filled) return accent;
        if (glass()) return Color.argb(30, 255, 255, 255);
        if (coolLight()) return 0xE8FFFFFF;
        if (warmLight()) return 0xCFFFFFFF;
        if (mode == AURORA) return Color.argb(34, 190, 255, 238);
        return Color.argb(30, 255, 255, 255);
    }

    public static int circleStroke(boolean filled, int accent) {
        if (filled) return 0;
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        if (glass()) return Color.argb(120, r, g, b);
        if (coolLight()) return Color.argb(90, 46, 123, 255);
        if (warmLight()) return Color.argb(110, r, g, b);
        if (mode == AURORA) return Color.argb(90, 95, 227, 192);
        return Color.argb(70, r, g, b);
    }

    public static int onAccent() {
        if (glass() || coolLight()) return 0xFFFFFFFF;
        if (warmLight() || mode == AURORA) return 0xFF06231E;
        return 0xFF0B0D16;
    }

    public static int textPrimary() {
        if (glass()) return 0xFFFFFFFF;
        if (coolLight()) return 0xFF1C2026;
        if (warmLight()) return 0xFF25211C;
        if (mode == AURORA) return 0xFFEAFFF9;
        return 0xFFFFFFFF;
    }

    public static int sub() {
        if (glass()) return 0xFFB7C6DA;
        if (coolLight()) return 0xFF5A6473;
        if (warmLight()) return 0xFF6C6155;
        if (mode == AURORA) return 0xFF9FD8CC;
        return 0xA9B8D8;
    }

    public static int muted() {
        if (glass()) return 0xFF8A9BB2;
        if (coolLight()) return 0xFF8B95A5;
        if (warmLight()) return 0xFF988C7D;
        if (mode == AURORA) return 0xFF6F9E96;
        return 0x7182A8;
    }

    public static int section() {
        if (glass()) return 0xFF6BB6FF;
        if (coolLight()) return 0xFF2E7BFF;
        if (warmLight()) return 0xFF8A6E3D;
        if (mode == AURORA) return 0xFF7FE9CE;
        return 0x99C6D2FF;
    }

    public static int icon() {
        if (glass()) return 0xFF64B5FF;
        if (coolLight()) return 0xFF2E7BFF;
        if (warmLight()) return 0xFF9A7642;
        if (mode == AURORA) return 0xFF7FE9CE;
        return 0x9FB4FF;
    }

    public static int hint() {
        if (glass()) return 0xFF7E90A8;
        if (coolLight()) return 0xFF9AA4B2;
        if (warmLight()) return 0xFF9C9183;
        if (mode == AURORA) return 0xFF6F9E96;
        return 0x8C9BBF;
    }

    public static int lyrics() {
        if (glass()) return 0xFFE4EEFB;
        if (coolLight()) return 0xFF2C333D;
        if (warmLight()) return 0xFF4A4136;
        if (mode == AURORA) return 0xFFDFF8F1;
        return 0xFFE8ECFF;
    }

    public static int sheetFill() {
        if (glass()) return 0xF20C1521;
        if (coolLight()) return 0xFAF7F9FC;
        if (warmLight()) return 0xFFFBF8F2;
        if (mode == AURORA) return 0xF2092327;
        return 0xF2121420;
    }

    public static int sheetStroke() {
        if (glass()) return Color.argb(120, 225, 240, 255);
        if (coolLight()) return 0x332E7BFF;
        if (warmLight()) return 0x332B2118;
        if (mode == AURORA) return 0x335FE3C0;
        return 0x22FFFFFF;
    }

    public static int handle() {
        if (glass()) return Color.argb(170, 225, 240, 255);
        if (coolLight()) return 0x662E7BFF;
        if (warmLight()) return 0x668A6E3D;
        if (mode == AURORA) return 0x665FE3C0;
        return 0x66FFFFFF;
    }

    public static int rule() {
        if (glass()) return Color.argb(60, 225, 240, 255);
        if (coolLight()) return 0x222E7BFF;
        if (warmLight()) return 0x338A6E3D;
        if (mode == AURORA) return 0x225FE3C0;
        return 0x22FFFFFF;
    }

    public static int orbAlpha(int index) {
        if (glass()) return index == 0 ? 110 : 85;
        if (coolLight()) return index == 0 ? 44 : 34;
        if (warmLight()) return index == 0 ? 80 : 62;
        return index == 0 ? 150 : 120;
    }

    public static int visualizerAlpha() {
        if (glass()) return 190;
        if (coolLight()) return 150;
        if (warmLight()) return 175;
        return 210;
    }

    public static float backdropAlpha() {
        if (glass()) return 0.16f;
        if (coolLight()) return 0.08f;
        if (warmLight()) return 0.12f;
        if (mode == AURORA) return 0.18f;
        return 0.20f;
    }

    public static float blurAlpha() {
        if (glass()) return 0.32f;
        if (coolLight()) return 0.10f;
        if (warmLight()) return 0.14f;
        if (mode == AURORA) return 0.24f;
        return 0.26f;
    }

    public static float brandSpacing() {
        if (glass()) return 0.055f;
        if (coolLight()) return 0.01f;
        if (warmLight()) return 0.03f;
        if (mode == AURORA) return 0.12f;
        return 0f;
    }

    public static Typeface display() {
        if (glass()) return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        if (coolLight()) return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        if (warmLight()) return Typeface.create(Typeface.SERIF, Typeface.BOLD);
        if (mode == AURORA) return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    }
}
