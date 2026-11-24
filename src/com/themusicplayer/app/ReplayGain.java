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
    private static final Map<String, Float> CACHE = new HashMap<>();
    private static final Pattern DB = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*dB");

    private ReplayGain() {}

    public static float volume(String path, boolean albumMode) {
        if (path == null) return 1f;
        Float cached = CACHE.get(path);
        if (cached != null) return cached;
        float db = read(path, albumMode);
        float gain = (float) Math.pow(10, db / 20.0);
        gain = Math.max(0.15f, Math.min(1f, gain));
        CACHE.put(path, gain);
        return gain;
    }

    private static float read(String path, boolean albumMode) {
        File f = new File(path);
        if (!f.isFile()) return 0f;
        String primary = albumMode ? "REPLAYGAIN_ALBUM_GAIN" : "REPLAYGAIN_TRACK_GAIN";
        String fallback = albumMode ? "REPLAYGAIN_TRACK_GAIN" : "REPLAYGAIN_ALBUM_GAIN";
        byte[] buf = new byte[(int) Math.min(f.length(), 256 * 1024)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int n = raf.read(buf);
            if (n <= 0) return 0f;
            String text = new String(buf, 0, n, StandardCharsets.ISO_8859_1);
            float a = parseTag(text, primary);
            if (!Float.isNaN(a)) return a;
            float b = parseTag(text, fallback);
            if (!Float.isNaN(b)) return b;
        } catch (Exception ignored) {
        }
        return 0f;
    }

    private static float parseTag(String text, String key) {
        int from = 0;
        while (true) {
            int at = text.indexOf(key, from);
            if (at < 0) break;
            String tail = text.substring(at + key.length(), Math.min(text.length(), at + key.length() + 48));
            Matcher m = DB.matcher(tail);
            if (m.find()) {
                try { return Float.parseFloat(m.group(1)); } catch (NumberFormatException ignored) {}
            }
            from = at + key.length();
        }
        return Float.NaN;
    }

    public static void clear(String path) { if (path != null) CACHE.remove(path); }
}
