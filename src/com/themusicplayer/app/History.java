package com.themusicplayer.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent play history: one row per track with a play count and last-played time. */
public final class History {

    public static final class Entry {
        public final Song song;
        public int count;
        public long lastPlayedMs;

        Entry(Song song, int count, long lastPlayedMs) {
            this.song = song;
            this.count = count;
            this.lastPlayedMs = lastPlayedMs;
        }
    }

    private static final String PREFS = "tmp_history";
    private static final String KEY = "entries";

    private final SharedPreferences prefs;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long version;

    public History(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        read();
    }

    private void read() {
        entries.clear();
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String path = o.optString("path", "");
                if (path.isEmpty()) continue;
                Song song = new Song(
                        o.optLong("id", -1L),
                        o.optString("title", ""),
                        o.optString("artist", "<unknown>"),
                        o.optString("album", ""),
                        path,
                        o.optLong("duration", 0L));
                entries.put(path, new Entry(song,
                        Math.max(1, o.optInt("count", 1)),
                        o.optLong("last", 0L)));
            }
        } catch (Exception ignored) {
        }
    }

    private void write() {
        JSONArray arr = new JSONArray();
        for (Entry e : entries.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", e.song.id);
                o.put("title", text(e.song.title));
                o.put("artist", text(e.song.artist));
                o.put("album", text(e.song.album));
                o.put("path", text(e.song.path));
                o.put("duration", e.song.durationMs);
                o.put("count", e.count);
                o.put("last", e.lastPlayedMs);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        version++;
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    /** Called when a track actually starts (not when it is merely queued). */
    public void record(Song song) {
        if (song == null || song.path == null || song.path.isEmpty()) return;
        Entry existing = entries.get(song.path);
        long now = System.currentTimeMillis();
        if (existing == null) {
            entries.put(song.path, new Entry(song, 1, now));
        } else {
            existing.count++;
            existing.lastPlayedMs = now;
        }
        write();
    }

    /** Called when a paused track resumes; updates time without inflating the play count. */
    public void touch(Song song) {
        if (song == null || song.path == null || song.path.isEmpty()) return;
        Entry existing = entries.get(song.path);
        if (existing == null) {
            record(song);
            return;
        }
        existing.lastPlayedMs = System.currentTimeMillis();
        write();
    }

    public void remove(String path) {
        if (path != null && entries.remove(path) != null) write();
    }

    public void clear() {
        if (entries.isEmpty()) return;
        entries.clear();
        write();
    }

    /** Newest first. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>(entries.values());
        Collections.sort(out, (a, b) -> Long.compare(b.lastPlayedMs, a.lastPlayedMs));
        return out;
    }

    public Entry byPath(String path) {
        return path == null ? null : entries.get(path);
    }

    /** Changes whenever the stored history is rewritten; lets the UI refresh lazily. */
    public long version() {
        return version;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
