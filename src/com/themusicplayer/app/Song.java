package com.themusicplayer.app;

import java.io.File;

/** A single audio item from the device library. */
public class Song {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final String path;
    public final long durationMs;
    public final String folder;
    public final String fileName;

    public Song(long id, String title, String artist, String album, String path, long durationMs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.durationMs = durationMs;
        File f = new File(path == null ? "" : path);
        this.fileName = f.getName();
        this.folder = f.getParent() == null ? "/" : f.getParent();
    }

    public String label() {
        String t = (title == null || title.trim().isEmpty()) ? fileName : title;
        String a = (artist == null || artist.trim().isEmpty()) ? "<unknown artist>" : artist;
        return t + "  -  " + a;
    }

    @Override
    public String toString() {
        return label();
    }
}
