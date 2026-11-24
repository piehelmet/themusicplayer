package com.themusicplayer.app;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads and organizes the device audio library through MediaStore. */
public final class Library {

    private Library() {}

    public static List<Song> load(Context context) {
        List<Song> out = new ArrayList<>();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                MediaStore.Audio.Media.DURATION + " > 1000";
        try (Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (c == null) return out;
            int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            while (c.moveToNext()) {
                out.add(new Song(
                        c.getLong(iId),
                        c.getString(iTitle),
                        c.getString(iArtist),
                        c.getString(iAlbum),
                        c.getString(iPath),
                        c.getLong(iDur)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public enum Sort { TITLE, ARTIST, ALBUM, DURATION, FOLDER }

    public static void sort(List<Song> songs, Sort sort, boolean ascending) {
        Comparator<Song> cmp = comparator(sort);
        Collections.sort(songs, ascending ? cmp : cmp.reversed());
    }

    /** Stable sort with a secondary key, used by grouped tabs so rows read predictably. */
    public static void sort(List<Song> songs, Sort primary, Sort secondary) {
        Collections.sort(songs, comparator(primary).thenComparing(comparator(secondary)));
    }

    private static Comparator<Song> comparator(Sort sort) {
        switch (sort) {
            case ARTIST: return (a, b) -> compare(a.artist, b.artist);
            case ALBUM: return (a, b) -> compare(a.album, b.album);
            case DURATION: return (a, b) -> Long.compare(a.durationMs, b.durationMs);
            case FOLDER: return (a, b) -> compare(a.folder, b.folder);
            default: return (a, b) -> compare(a.title, b.title);
        }
    }

    private static int compare(String a, String b) {
        return empty(a).trim().compareToIgnoreCase(empty(b).trim());
    }

    /** One representative song per album. Input is already title-ordered by MediaStore. */
    public static List<Song> groupByAlbum(List<Song> all) {
        Map<String, Song> seen = new LinkedHashMap<>();
        for (Song s : all) seen.putIfAbsent(albumKey(s), s);
        return new ArrayList<>(seen.values());
    }

    /** One representative song per artist. Input is already title-ordered by MediaStore. */
    public static List<Song> groupByArtist(List<Song> all) {
        Map<String, Song> seen = new LinkedHashMap<>();
        for (Song s : all) seen.putIfAbsent(artistKey(s), s);
        return new ArrayList<>(seen.values());
    }

    public static String albumKey(Song s) { return s == null ? "" : s.albumKey; }

    public static String artistKey(Song s) { return s == null ? "" : s.artistKey; }

    private static String empty(String s) { return s == null ? "" : s; }

    public static boolean sameAlbum(Song a, Song b) {
        return a != null && b != null && albumKey(a).equals(albumKey(b));
    }

    public static boolean sameArtist(Song a, Song b) {
        return a != null && b != null && artistKey(a).equals(artistKey(b));
    }

    public static List<Song> ofAlbum(List<Song> all, Song picked) {
        List<Song> out = new ArrayList<>();
        for (Song s : all) if (sameAlbum(s, picked)) out.add(s);
        sort(out, Sort.TITLE, Sort.DURATION);
        return out;
    }

    public static List<Song> ofArtist(List<Song> all, Song picked) {
        List<Song> out = new ArrayList<>();
        for (Song s : all) if (sameArtist(s, picked)) out.add(s);
        sort(out, Sort.ALBUM, Sort.TITLE);
        return out;
    }

    public static int albumTracks(List<Song> all, Song picked) {
        return ofAlbum(all, picked).size();
    }

    public static int artistTracks(List<Song> all, Song picked) {
        return ofArtist(all, picked).size();
    }

    public static int artistAlbums(List<Song> all, Song picked) {
        Set<String> albums = new HashSet<>();
        for (Song s : all) if (sameArtist(s, picked)) albums.add(s.albumKey);
        return albums.size();
    }

    public static List<String> folders(List<Song> all) {
        Set<String> set = new LinkedHashSet<>();
        for (Song s : all) set.add(s.folder);
        List<String> out = new ArrayList<>(set);
        Collections.sort(out, String::compareToIgnoreCase);
        return out;
    }

    public static List<Song> inFolder(List<Song> all, String folder) {
        List<Song> out = new ArrayList<>();
        for (Song s : all) if (folder.equals(s.folder)) out.add(s);
        return out;
    }

    public static List<Song> filter(List<Song> all, String query) {
        if (query == null || query.trim().isEmpty()) return all;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Song> out = new ArrayList<>();
        for (Song s : all) if (s.searchKey.contains(q)) out.add(s);
        return out;
    }

    public static List<Song> exclude(List<Song> all, Set<String> excludedFolders) {
        if (excludedFolders == null || excludedFolders.isEmpty()) return all;
        List<Song> out = new ArrayList<>();
        for (Song s : all)
            if (!excludedFolders.contains(s.folder)) out.add(s);
        return out;
    }

    public static List<String> genres(Context context) {
        List<String> out = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Genres.NAME}, null, null,
                MediaStore.Audio.Genres.NAME + " ASC")) {
            if (c == null) return out;
            int i = c.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);
            while (c.moveToNext()) {
                String name = c.getString(i);
                if (name != null && !name.trim().isEmpty()) out.add(name);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<Song> inGenre(Context context, List<Song> all, String genre) {
        List<Song> out = new ArrayList<>();
        try (Cursor g = context.getContentResolver().query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Genres._ID},
                MediaStore.Audio.Genres.NAME + "=?", new String[]{genre}, null)) {
            if (g == null || !g.moveToFirst()) return out;
            long genreId = g.getLong(0);
            android.net.Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId);
            try (Cursor m = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Audio.Media._ID}, null, null, null)) {
                if (m == null) return out;
                int i = m.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                Set<Long> ids = new LinkedHashSet<>();
                while (m.moveToNext()) ids.add(m.getLong(i));
                for (Song s : all) if (ids.contains(s.id)) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String duration(long ms) {
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }
}
