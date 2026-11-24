package com.themusicplayer.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal synchronized-lyrics (.lrc) reader. */
public final class Lyrics {
    public static final class Line {
        public final long timeMs;
        public final String text;
        Line(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");

    private final List<Line> lines = new ArrayList<>();

    public static Lyrics forSong(Song song) {
        Lyrics out = new Lyrics();
        if (song == null || song.path == null) return out;
        File audio = new File(song.path);
        File base = new File(audio.getParent() == null ? "." : audio.getParent(), stripExt(audio.getName()));
        File[] candidates = {
                new File(base.getAbsolutePath() + ".lrc"),
                new File(base.getAbsolutePath() + ".LRC"),
                new File(base.getAbsolutePath() + ".txt")
        };
        for (File f : candidates) {
            if (f.isFile()) { out.parse(f); break; }
        }
        return out;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void parse(File file) {
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = TAG.matcher(line);
                while (m.find()) {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    long frac = 0;
                    if (m.group(3) != null) {
                        String f = m.group(3);
                        frac = Long.parseLong(f) * (f.length() == 2 ? 10 : 100);
                    }
                    long time = min * 60000 + sec * 1000 + frac;
                    String text = line.substring(m.end()).trim();
                    if (!text.isEmpty()) lines.add(new Line(time, text));
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(lines, Comparator.comparingLong(l -> l.timeMs));
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    public List<Line> lines() { return lines; }

    public int indexAt(long positionMs) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) found = i; else break;
        }
        return found;
    }

    public String textAt(long positionMs) {
        int i = indexAt(positionMs);
        return i < 0 ? "" : lines.get(i).text;
    }
}
