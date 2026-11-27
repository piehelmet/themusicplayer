package com.themusicplayer.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;

import java.io.File;

/** Embedded album art loader with a small memory cache and colour extraction. */
public final class Art {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(48);
    private static final LruCache<String, Drawable> ROUNDED = new LruCache<>(64);

    private Art() {}

    public static Bitmap get(Context context, Song song) {
        if (song == null || song.path == null) return null;
        Bitmap hit = CACHE.get(song.path);
        if (hit != null) return hit;
        Bitmap art = null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(song.path);
            byte[] raw = mmr.getEmbeddedPicture();
            if (raw != null && raw.length > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                art = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            }
        } catch (Exception ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        if (art == null) art = placeholder(song);
        CACHE.put(song.path, art);
        return art;
    }

    private static Bitmap placeholder(Song song) {
        int w = 320, h = 320;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int hue = Math.abs((song == null ? "?" : song.album + song.artist).hashCode()) % 360;
        int c1 = Color.HSVToColor(new float[]{hue, 0.45f, 0.36f});
        int c2 = Color.HSVToColor(new float[]{(hue + 48) % 360, 0.55f, 0.16f});
        android.graphics.LinearGradient g = new android.graphics.LinearGradient(
                0, 0, w, h, c1, c2, android.graphics.Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(g);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x88FFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(120);
        String letter = song == null ? "♫" : firstLetter(song.album);
        c.drawText(letter, w / 2f, h / 2f + 40, p);
        return b;
    }

    private static String firstLetter(String s) {
        if (s == null || s.trim().isEmpty()) return "♫";
        return s.trim().substring(0, 1).toUpperCase();
    }

    /** Average colour of a bitmap, pushed toward a pleasant saturated accent. */
    public static int accent(Bitmap bitmap) {
        if (bitmap == null) return 0xFF5B8CFF;
        try {
            Bitmap small = Bitmap.createScaledBitmap(bitmap, 8, 8, true);
            long r = 0, g = 0, b = 0;
            int[] px = new int[64];
            small.getPixels(px, 0, 8, 0, 0, 8, 8);
            for (int v : px) {
                r += Color.red(v); g += Color.green(v); b += Color.blue(v);
            }
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / 64), (int) (g / 64), (int) (b / 64), hsv);
            hsv[1] = Math.max(0.42f, hsv[1]);
            hsv[2] = Math.max(0.45f, Math.min(0.82f, hsv[2]));
            return Color.HSVToColor(0xFF, hsv);
        } catch (Exception e) {
            return 0xFF5B8CFF;
        }
    }

    /** Returns an already-rounded cover without touching the artwork cache. */
    public static Drawable peekRounded(String key, float radius) {
        if (key == null || key.isEmpty()) return null;
        return ROUNDED.get(key + "|" + radius);
    }

    /** Reuses rounded covers across recycled list rows; same artwork/radius is identical. */
    public static Drawable roundedCached(Bitmap bitmap, float radius, String key) {
        String cacheKey = key + "|" + radius;
        Drawable hit = ROUNDED.get(cacheKey);
        if (hit != null) return hit;
        Drawable made = rounded(bitmap, radius);
        ROUNDED.put(cacheKey, made);
        return made;
    }

    public static Drawable rounded(Bitmap bitmap, float radius) {
        if (bitmap == null) return new BitmapDrawable(placeholder(null));
        Bitmap out = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.RectF rect = new android.graphics.RectF(0, 0, out.getWidth(), out.getHeight());
        c.drawRoundRect(rect, radius, radius, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(bitmap, 0, 0, p);
        BitmapDrawable d = new BitmapDrawable(out);
        d.setAntiAlias(true);
        return d;
    }

    public static void clear(String path) {
        if (path != null) CACHE.remove(path);
        ROUNDED.evictAll();
    }
}
