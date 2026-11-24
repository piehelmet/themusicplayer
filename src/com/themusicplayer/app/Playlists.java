package com.themusicplayer.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent playlists stored as a compact text blob in SharedPreferences. */
public final class Playlists {
    private static final String PREFS = "tmp_playlists";
    private static final String KEY = "data";
    private final SharedPreferences prefs;

    public Playlists(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').trim();
    }

    private Map<String, List<Long>> read() {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        String raw = prefs.getString(KEY, "");
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String name = line.substring(0, tab);
            List<Long> ids = new ArrayList<>();
            String[] parts = line.substring(tab + 1).split(",");
            for (String p : parts) {
                if (p.isEmpty()) continue;
                try { ids.add(Long.parseLong(p)); } catch (NumberFormatException ignored) {}
            }
            out.put(name, ids);
        }
        return out;
    }

    private void write(Map<String, List<Long>> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Long>> e : data.entrySet()) {
            sb.append(clean(e.getKey())).append('\t');
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(e.getValue().get(i));
            }
            sb.append('\n');
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    public List<String> names() {
        return new ArrayList<>(read().keySet());
    }

    public List<Long> ids(String name) {
        List<Long> ids = read().get(name);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    public void save(String name, List<Long> ids) {
        Map<String, List<Long>> data = read();
        data.put(clean(name), new ArrayList<>(ids));
        write(data);
    }

    public void delete(String name) {
        Map<String, List<Long>> data = read();
        data.remove(name);
        write(data);
    }

    public void rename(String oldName, String newName) {
        Map<String, List<Long>> data = read();
        List<Long> ids = data.remove(oldName);
        if (ids != null) data.put(clean(newName), ids);
        write(data);
    }

    public String exportAll() {
        return prefs.getString(KEY, "");
    }

    public boolean importAll(String text) {
        if (text == null) return false;
        prefs.edit().putString(KEY, text).apply();
        return true;
    }
}
