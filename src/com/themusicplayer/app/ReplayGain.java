package com.themusicplayer.app;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight ReplayGain tag reader (ID3v2 TXXX and Vorbis comments). */
public final class ReplayGain {
    private static final Map<String, Gain> CACHE = new HashMap<>();
    private static final Pattern DB = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*dB");
    private static final Pattern LINEAR = Pattern.compile("(\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");

    private static final class Gain {
        final float db;
        final float peakDb;      // peak expressed in dBFS (negative for normal material)
        final boolean hasPeak;

        Gain(float db, float peakDb, boolean hasPeak) {
            this.db = db;
            this.peakDb = peakDb;
            this.hasPeak = hasPeak;
        }
    }

    private ReplayGain() {}

    /** Attenuation factor for MediaPlayer.setVolume (never greater than 1). */
    public static float volume(String path, boolean albumMode) {
        Gain g = gain(path, albumMode);
        if (g == null || g.db >= 0f) return 1f;
        return (float) Math.pow(10, g.db / 20.0);
    }

    /**
     * Safe positive gain in millibels for LoudnessEnhancer. The peak tag limits the boost so
     * the restored gain does not clip; without a peak tag no boost is applied (attenuation only).
     */
    public static int boostMilliBel(String path, boolean albumMode) {
        Gain g = gain(path, albumMode);
        if (g == null || g.db <= 0f) return 0;
        double headroom = g.hasPeak ? -g.peakDb - 0.5 : 0.0;
        double boost = Math.min(g.db, headroom);
        if (boost <= 0) return 0;
        return (int) Math.round(Math.min(boost, 12.0) * 100);
    }

    private static Gain gain(String path, boolean albumMode) {
        if (path == null) return null;
        String key = path + (albumMode ? "|album" : "|track");
        Gain cached = CACHE.get(key);
        if (cached != null) return cached;
        float db = readDb(path, albumMode);
        if (Float.isNaN(db)) return null;
        float[] peak = readPeak(path, albumMode); // [dBFS, present?] or null
        Gain g = peak == null
                ? new Gain(db, 0f, false)
                : new Gain(db, peak[0], peak[1] > 0f);
        CACHE.put(key, g);
        return g;
    }

    private static float readDb(String path, boolean albumMode) {
        String text = header(path);
        if (text == null) return Float.NaN;
        String primary = albumMode ? "REPLAYGAIN_ALBUM_GAIN" : "REPLAYGAIN_TRACK_GAIN";
        String fallback = albumMode ? "REPLAYGAIN_TRACK_GAIN" : "REPLAYGAIN_ALBUM_GAIN";
        Float a = parseGain(text, primary);
        if (a != null) return a;
        Float b = parseGain(text, fallback);
        return b == null ? Float.NaN : b;
    }

    /** Returns {peakDb, 1} when found, or null. Peak tags normally store linear amplitude. */
    private static float[] readPeak(String path, boolean albumMode) {
        String text = header(path);
        if (text == null) return null;
        String primary = albumMode ? "REPLAYGAIN_ALBUM_PEAK" : "REPLAYGAIN_TRACK_PEAK";
        String fallback = albumMode ? "REPLAYGAIN_TRACK_PEAK" : "REPLAYGAIN_ALBUM_PEAK";
        Float a = parsePeak(text, primary);
        if (a != null) return new float[]{a, 1f};
        Float b = parsePeak(text, fallback);
        return b == null ? null : new float[]{b, 1f};
    }

    private static String header(String path) {
        File f = new File(path);
        if (!f.isFile()) return null;
        byte[] buf = new byte[(int) Math.min(f.length(), 256 * 1024)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int n = raf.read(buf);
            if (n <= 0) return null;
            return new String(buf, 0, n, StandardCharsets.ISO_8859_1);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float parseGain(String text, String key) {
        String tail = tail(text, key);
        if (tail == null) return null;
        Matcher m = DB.matcher(tail);
        if (!m.find()) return null;
        try { return Float.parseFloat(m.group(1)); } catch (NumberFormatException ignored) { return null; }
    }

    private static Float parsePeak(String text, String key) {
        String tail = tail(text, key);
        if (tail == null) return null;
        Matcher db = DB.matcher(tail);
        if (db.find()) {
            try { return Float.parseFloat(db.group(1)); } catch (NumberFormatException ignored) {}
        }
        Matcher linear = LINEAR.matcher(tail);
        if (linear.find()) {
            try {
                float value = Float.parseFloat(linear.group(1));
                if (value > 0f && value <= 1f) return (float) (20.0 * Math.log10(value));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String tail(String text, String key) {
        int from = 0;
        while (true) {
            int at = text.indexOf(key, from);
            if (at < 0) return null;
            String tail = text.substring(at + key.length(),
                    Math.min(text.length(), at + key.length() + 48));
            // Reject a match where the key is only a prefix of a longer name (e.g. ALBUM_GAIN
            // inside REPLAYGAIN_ALBUM_GAIN_X); the tail must contain a number or ':'/space.
            if (DB.matcher(tail).find() || LINEAR.matcher(tail).find()) return tail;
            from = at + key.length();
        }
    }

    public static void clear(String path) {
        if (path == null) return;
        CACHE.remove(path + "|track");
        CACHE.remove(path + "|album");
    }
}
