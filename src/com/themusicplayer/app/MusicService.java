package com.themusicplayer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Playback engine: queue, crossfade, ReplayGain, equalizer, sleep timer, widget and media session. */
public class MusicService extends Service {

    public static final String ACTION_PLAY = "com.themusicplayer.app.PLAY";
    public static final String ACTION_TOGGLE = "com.themusicplayer.app.TOGGLE";
    public static final String ACTION_NEXT = "com.themusicplayer.app.NEXT";
    public static final String ACTION_PREV = "com.themusicplayer.app.PREV";
    public static final String ACTION_STOP = "com.themusicplayer.app.STOP";
    public static final String ACTION_SEEK = "com.themusicplayer.app.SEEK";
    public static final String EXTRA_POSITION = "position";
    public static final String UPDATE = "com.themusicplayer.app.UPDATE";
    public static final String EXTRA_ACCENT = "accent";

    private static final String CHANNEL = "themusicplayer_channel";
    private static final int NOTIFICATION_ID = 42;

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private MediaPlayer incoming;
    private MediaSession session;
    private AudioFx fx;
    private AudioFx incomingFx;
    private SharedPreferences prefs;

    private final List<Song> queue = new ArrayList<>();
    private int index = -1;
    private int incomingIndex = -1;
    private boolean shuffle = false;
    private int repeat = 0;              // 0 off, 1 all, 2 one
    private float speed = 1f;
    private float volume = 1f;
    private boolean crossfadeOn = true;
    private boolean replayGainOn = true;
    private int fadeMs = 5000;
    private long sleepUntil = 0;
    private long crossStart = 0;
    private boolean prepared = false;
    private boolean incomingPrepared = false;
    private boolean crossfading = false;
    private int accent = 0xFF5B8CFF;
    private Lyrics lyrics = new Lyrics();

    private short[] eqBands;
    private short eqPreset = -1;
    private boolean eqEnabled = false;
    private int bassStrength = 0;      // 0..1000, 0 = off
    private int loudnessMb = 0;        // 0..1200 millibels, 0 = off
    private boolean replayGainAlbum = false;

    private PowerManager.WakeLock wakeLock;
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tickCount = 0;
    private History history;
    private int pendingSeekMs;
    private long lastStateSaveMs;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            maybeCrossfade();
            advanceCrossfade();
            if (sleepUntil > 0 && System.currentTimeMillis() >= sleepUntil) {
                sleepUntil = 0;
                pause();
            }
            broadcast();
            // State is broadcast every 500 ms instead of every tick; explicit user
            // actions still broadcast immediately. Widget work only happens if one exists.
            tickCount++;
            if (tickCount % 2 == 0) broadcast();
            if (tickCount % 4 == 0) notifyWidget();
            handler.postDelayed(this, 250);
        }
    };

    public class LocalBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    /** Effects bound to one MediaPlayer session. Crossfade keeps one set per player. */
    private static final class AudioFx {
        final Equalizer eq;
        final BassBoost bass;
        final LoudnessEnhancer loud;

        AudioFx(int session) {
            Equalizer e = null;
            try { e = new Equalizer(0, session); } catch (Exception ignored) {}
            eq = e;
            BassBoost b = null;
            try { b = new BassBoost(0, session); } catch (Exception ignored) {}
            bass = b;
            LoudnessEnhancer l = null;
            try { l = new LoudnessEnhancer(session); } catch (Exception ignored) {}
            loud = l;
        }

        void apply(short[] bands, short preset, boolean eqOn, int bassValue, int boostMb) {
            if (eq != null) {
                try {
                    if (bands != null && bands.length == eq.getNumberOfBands()) {
                        for (short i = 0; i < bands.length; i++) eq.setBandLevel(i, bands[i]);
                    } else if (preset >= 0 && preset < eq.getNumberOfPresets()) {
                        eq.usePreset(preset);
                    }
                    eq.setEnabled(eqOn);
                } catch (Exception ignored) {}
            }
            if (bass != null) {
                try {
                    int strength = Math.max(0, Math.min(1000, bassValue));
                    bass.setStrength((short) strength);
                    bass.setEnabled(strength > 0);
                } catch (Exception ignored) {}
            }
            if (loud != null) {
                try {
                    int gain = Math.max(0, Math.min(2000, boostMb));
                    loud.setTargetGain(gain);
                    loud.setEnabled(gain > 0);
                } catch (Exception ignored) {}
            }
        }

        void release() {
            if (eq != null) { try { eq.release(); } catch (Exception ignored) {} }
            if (bass != null) { try { bass.release(); } catch (Exception ignored) {} }
            if (loud != null) { try { loud.release(); } catch (Exception ignored) {} }
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("tmp_settings", MODE_PRIVATE);
        history = new History(this);
        crossfadeOn = prefs.getBoolean("crossfade", true);
        replayGainOn = prefs.getBoolean("replaygain", true);
        volume = prefs.getFloat("volume", 1f);
        fadeMs = prefs.getInt("fade_ms", 5000);
        bassStrength = prefs.getInt("bass_strength", 0);
        loudnessMb = prefs.getInt("loudness_mb", 0);
        replayGainAlbum = prefs.getBoolean("rg_album", false);
        eqEnabled = prefs.getBoolean("eq_enabled", false);
        eqPreset = (short) prefs.getInt("eq_preset", -1);
        String rawBands = prefs.getString("eq_bands", "");
        if (rawBands != null && !rawBands.isEmpty()) {
            try {
                String[] parts = rawBands.split(",");
                short[] values = new short[parts.length];
                for (int i = 0; i < parts.length; i++) values[i] = Short.parseShort(parts[i].trim());
                eqBands = values;
            } catch (Exception ignored) {}
        }

        player = newPlayer();
        session = new MediaSession(this, "TheMusicPlayer");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(true); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long pos) { seek((int) pos); }
            @Override public void onStop() { pause(); }
        });
        session.setSessionActivity(PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "themusicplayer:playback");
        createChannel();
        restoreState();
        handler.post(tick);
    }

    private MediaPlayer newPlayer() {
        MediaPlayer mp = new MediaPlayer();
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnCompletionListener(p -> {
            if (p == player && !crossfading) next(true);
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) next(true);
            return true;
        });
        return mp;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Playback",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Music playback controls");
            nm.createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) toggle();
            else if (ACTION_NEXT.equals(action)) next(true);
            else if (ACTION_PREV.equals(action)) prev();
            else if (ACTION_STOP.equals(action)) pause();
            else if (ACTION_SEEK.equals(action)) seek(intent.getIntExtra(EXTRA_POSITION, 0));
        }
        return START_STICKY;
    }

    // ---------------------------------------------------------- Queue

    public void playQueue(List<Song> songs, int startIndex) {
        cancelCrossfade();
        queue.clear();
        queue.addAll(songs);
        index = queue.isEmpty() ? -1 : Math.max(0, Math.min(startIndex, queue.size() - 1));
        playCurrent();
    }

    public void addToQueue(List<Song> songs) {
        queue.addAll(songs);
        if (index < 0 && !queue.isEmpty()) { index = 0; playCurrent(); }
        else broadcast();
    }

    public void insertNext(Song song) {
        int at = (index < 0 ? 0 : index + 1);
        queue.add(Math.min(at, queue.size()), song);
        broadcast();
    }

    public void removeAt(int pos) {
        if (pos < 0 || pos >= queue.size()) return;
        boolean wasCurrent = pos == index;
        queue.remove(pos);
        if (queue.isEmpty()) { index = -1; pause(); return; }
        if (pos < index) index--;
        else if (pos == index) index = Math.min(index, queue.size() - 1);
        if (wasCurrent) playCurrent(); else broadcast();
    }

    public void reorder(int from, int to) {
        if (from < 0 || to < 0 || from >= queue.size() || to >= queue.size() || from == to) return;
        Song moved = queue.remove(from);
        queue.add(to, moved);
        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        broadcast();
    }

    public void playSongAt(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        playCurrent();
    }

    // ------------------------------------------------------- Playback

    private void playCurrent() {
        pendingSeekMs = 0;
        playCurrent(true);
    }

    /** Prepares the active track; autoPlay=false is used when restoring a saved session. */
    private void playCurrent(boolean autoPlay) {
        cancelCrossfade();
        if (index < 0 || index >= queue.size()) return;
        Song song = queue.get(index);
        prepared = false;
        if (fx != null) { fx.release(); fx = null; }
        try {
            player.reset();
            player.setDataSource(song.path);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setOnPreparedListener(mp -> {
                prepared = true;
                fx = createFx(mp);
                applyFx(fx, song);
                applySpeed(mp);
                if (pendingSeekMs > 0) {
                    try {
                        int duration = mp.getDuration();
                        mp.seekTo(duration > 0 ? Math.min(pendingSeekMs, duration) : pendingSeekMs);
                    } catch (Exception ignored) {
                    }
                }
                pendingSeekMs = 0;
                applyVolume();
                if (autoPlay) {
                    mp.start();
                    session.setActive(true);
                    if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
                    history.record(song);
                    updateState(PlaybackState.STATE_PLAYING);
                    startForeground(NOTIFICATION_ID, notification());
                } else {
                    updateState(PlaybackState.STATE_PAUSED);
                }
                accent = Art.accent(Art.get(this, song));
                updateMetadata();
                notifyWidget();
                broadcast();
            });
            player.prepareAsync();
        } catch (Exception e) {
            next(true);
        }
    }

    private void maybeCrossfade() {
        if (!crossfadeOn || crossfading || !isPlaying() || !prepared
                || repeat == 2 || queue.size() < 2) return;
        int dur = duration();
        int pos = position();
        if (dur <= 0 || pos < dur - fadeMs) return;
        int nextIndex = computeNextIndex(false, true);
        if (nextIndex < 0 || nextIndex == index) return;
        prepareIncoming(nextIndex);
    }

    private void prepareIncoming(int nextIndex) {
        try {
            if (incoming != null) { incoming.release(); incoming = null; }
            MediaPlayer mp = newPlayer();
            mp.setOnCompletionListener(p -> {});
            mp.setOnErrorListener((p, w, e) -> true);
            incomingPrepared = false;
            incomingIndex = nextIndex;
            incoming = mp;
            mp.setDataSource(queue.get(nextIndex).path);
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            Song incomingSong = queue.get(nextIndex);
            mp.setOnPreparedListener(p -> {
                incomingPrepared = true;
                crossfading = true;
                crossStart = System.currentTimeMillis();
                incomingFx = createFx(p);
                applyFx(incomingFx, incomingSong);
                applySpeed(p);
                p.setVolume(0f, 0f);
                p.start();
                broadcast();
            });
            mp.prepareAsync();
        } catch (Exception e) {
            cancelCrossfade();
        }
    }

    private void advanceCrossfade() {
        if (!crossfading || incoming == null || !incomingPrepared) return;
        float f = Math.max(0f, Math.min(1f,
                (System.currentTimeMillis() - crossStart) / (float) fadeMs));
        float g1 = gain(current());
        float g2 = gain(incomingIndex >= 0 && incomingIndex < queue.size()
                ? queue.get(incomingIndex) : null);
        // Equal-power curve: avoids the ~3 dB dip of a linear amplitude crossfade.
        double angle = f * Math.PI / 2.0;
        float v1 = clamp((float) (volume * g1 * Math.cos(angle)));
        float v2 = clamp((float) (volume * g2 * Math.sin(angle)));
        try { player.setVolume(v1, v1); incoming.setVolume(v2, v2); } catch (Exception ignored) {}
        if (f >= 1f) finishCrossfade();
    }

    private void finishCrossfade() {
        MediaPlayer old = player;
        MediaPlayer next = incoming;
        if (next == null) { crossfading = false; return; }
        player = next;
        incoming = null;
        index = incomingIndex;
        Song now = current();
        if (now != null) history.record(now);
        prepared = true;
        incomingPrepared = false;
        crossfading = false;
        try { old.stop(); } catch (Exception ignored) {}
        try { old.release(); } catch (Exception ignored) {}
        if (fx != null) { fx.release(); fx = null; }
        fx = incomingFx;
        incomingFx = null;
        applyFx(fx, now);
        applySpeed(player);
        applyVolume();
        accent = Art.accent(Art.get(this, current()));
        updateState(PlaybackState.STATE_PLAYING);
        updateMetadata();
        startForeground(NOTIFICATION_ID, notification());
        notifyWidget();
        broadcast();
    }

    private void cancelCrossfade() {
        if (incoming != null) {
            try { incoming.stop(); } catch (Exception ignored) {}
            try { incoming.release(); } catch (Exception ignored) {}
        }
        if (incomingFx != null) { incomingFx.release(); incomingFx = null; }
        incoming = null;
        incomingPrepared = false;
        crossfading = false;
        incomingIndex = -1;
        applyVolume();
    }

    private int computeNextIndex(boolean byUser, boolean forCrossfade) {
        if (queue.isEmpty()) return -1;
        if (shuffle && queue.size() > 1) {
            int n;
            do { n = random.nextInt(queue.size()); } while (n == index);
            return n;
        }
        int n = index + 1;
        if (n >= queue.size()) {
            if (repeat == 0 && byUser && !forCrossfade) return index;
            return 0;
        }
        return n;
    }

    public void next(boolean byUser) {
        if (queue.isEmpty()) return;
        cancelCrossfade();
        if (repeat == 2 && !byUser) { seek(0); if (!isPlaying()) play(); return; }
        int n = computeNextIndex(byUser, false);
        if (n == index && repeat == 0 && byUser) { seek(0); return; }
        index = n;
        playCurrent();
    }

    public void prev() {
        if (queue.isEmpty()) return;
        if (position() > 3000) { seek(0); return; }
        cancelCrossfade();
        index = (index - 1 + queue.size()) % queue.size();
        playCurrent();
    }

    public void toggle() { if (isPlaying()) pause(); else play(); }

    public void play() {
        if (index < 0 && !queue.isEmpty()) index = 0;
        if (index < 0) return;
        if (!isPlaying()) {
            if (prepared) {
                try {
                    applyVolume();
                    player.start();
                    history.touch(current());
                } catch (IllegalStateException e) { playCurrent(); return; }
            } else { playCurrent(); return; }
        }
        session.setActive(true);
        if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
        updateState(PlaybackState.STATE_PLAYING);
        startForeground(NOTIFICATION_ID, notification());
        notifyWidget();
        broadcast();
    }

    public void pause() {
        cancelCrossfade();
        try { if (player != null && player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        updateState(PlaybackState.STATE_PAUSED);
        if (wakeLock.isHeld()) wakeLock.release();
        stopForeground(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification());
        notifyWidget();
        saveState();
        broadcast();
    }

    public void seek(int ms) {
        cancelCrossfade();
        if (!prepared) return;
        try { player.seekTo(Math.max(0, Math.min(ms, player.getDuration()))); } catch (Exception ignored) {}
        broadcast();
    }

    // --------------------------------------------------------- Extras

    public void setShuffle(boolean value) { shuffle = value; broadcast(); }
    public void cycleRepeat() { repeat = (repeat + 1) % 3; broadcast(); }
    public void setSpeed(float value) { speed = value; applySpeed(player); broadcast(); }

    private void applySpeed(MediaPlayer mp) {
        if (mp == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            android.media.PlaybackParams params = new android.media.PlaybackParams();
            params.setSpeed(speed);
            mp.setPlaybackParams(params);
        } catch (Exception ignored) {}
    }

    public void setSleepTimer(long minutes) {
        sleepUntil = minutes <= 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        broadcast();
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        prefs.edit().putFloat("volume", volume).apply();
        applyVolume();
        broadcast();
    }

    public void setCrossfade(boolean on) {
        crossfadeOn = on;
        prefs.edit().putBoolean("crossfade", on).apply();
        if (!on) cancelCrossfade();
        broadcast();
    }

    public void setReplayGain(boolean on) {
        replayGainOn = on;
        prefs.edit().putBoolean("replaygain", on).apply();
        applyVolume();
        broadcast();
    }

    public void setReplayGainAlbumMode(boolean album) {
        replayGainAlbum = album;
        prefs.edit().putBoolean("rg_album", album).apply();
        applyVolume();
        broadcast();
    }

    public void setFadeSeconds(int seconds) {
        fadeMs = Math.max(1, Math.min(20, seconds)) * 1000;
        prefs.edit().putInt("fade_ms", fadeMs).apply();
        broadcast();
    }

    private float gain(Song song) {
        if (song == null) return 1f;
        return replayGainOn ? ReplayGain.volume(song.path, replayGainAlbum) : 1f;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void applyVolume() {
        if (player == null) return;
        float v = clamp(volume * gain(current()));
        try { player.setVolume(v, v); } catch (Exception ignored) {}
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
    }

    private Song incomingSong() {
        return incomingIndex >= 0 && incomingIndex < queue.size() ? queue.get(incomingIndex) : null;
    }

    private AudioFx createFx(MediaPlayer mp) {
        try {
            int session = mp.getAudioSessionId();
            if (session <= 0) return null;
            return new AudioFx(session);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Applies saved EQ/effects plus the peak-safe ReplayGain boost for one track. */
    private void applyFx(AudioFx target, Song song) {
        if (target == null) return;
        int boost = replayGainOn && song != null
                ? ReplayGain.boostMilliBel(song.path, replayGainAlbum) : 0;
        target.apply(eqBands, eqPreset, eqEnabled, bassStrength, loudnessMb + boost);
    }

    private void persistEq() {
        SharedPreferences.Editor e = prefs.edit()
                .putBoolean("eq_enabled", eqEnabled)
                .putInt("eq_preset", eqPreset);
        if (eqBands == null) e.remove("eq_bands");
        else {
            StringBuilder sb = new StringBuilder();
            for (short value : eqBands) {
                if (sb.length() > 0) sb.append(',');
                sb.append(value);
            }
            e.putString("eq_bands", sb.toString());
        }
        e.apply();
    }

    public Equalizer equalizer() { return fx == null ? null : fx.eq; }
    public boolean bassBoostAvailable() { return fx != null && fx.bass != null; }
    public boolean loudnessAvailable() { return fx != null && fx.loud != null; }
    public int bassStrength() { return bassStrength; }
    public int loudnessBoost() { return loudnessMb; }

    public void setEqualizerEnabled(boolean on) {
        eqEnabled = on;
        persistEq();
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
        broadcast();
    }

    public void setBand(short band, short level) {
        if (eqBands == null && fx != null && fx.eq != null) {
            try { eqBands = new short[fx.eq.getNumberOfBands()]; } catch (Exception ignored) {}
        }
        if (eqBands == null || band < 0 || band >= eqBands.length) return;
        eqBands[band] = level;
        eqPreset = -1;
        persistEq();
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
        broadcast();
    }

    public void setPreset(short preset) {
        eqPreset = preset;
        eqBands = null;
        persistEq();
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
        broadcast();
    }

    public void setBassStrength(int strength) {
        bassStrength = Math.max(0, Math.min(1000, strength));
        prefs.edit().putInt("bass_strength", bassStrength).apply();
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
        broadcast();
    }

    public void setLoudnessBoost(int boostMb) {
        loudnessMb = Math.max(0, Math.min(1200, boostMb));
        prefs.edit().putInt("loudness_mb", loudnessMb).apply();
        applyFx(fx, current());
        applyFx(incomingFx, incomingSong());
        broadcast();
    }

    // --------------------------------------------------------- State

    public Song current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    public int currentIndex() { return index; }
    public List<Song> queue() { return queue; }
    public boolean isPlaying() { try { return player != null && player.isPlaying(); } catch (Exception e) { return false; } }
    public int position() { if (!prepared) return 0; try { return player.getCurrentPosition(); } catch (Exception e) { return 0; } }
    public int duration() { if (!prepared) return 0; try { return player.getDuration(); } catch (Exception e) { return 0; } }
    public boolean shuffle() { return shuffle; }
    public int repeat() { return repeat; }
    public float speed() { return speed; }
    public float volume() { return volume; }
    public boolean crossfade() { return crossfadeOn; }
    public boolean replayGain() { return replayGainOn; }
    public boolean replayGainAlbumMode() { return replayGainAlbum; }
    public int fadeSeconds() { return fadeMs / 1000; }
    public boolean crossfading() { return crossfading; }
    public int accentColor() { return accent; }
    public long sleepRemaining() { return sleepUntil == 0 ? 0 : Math.max(0, sleepUntil - System.currentTimeMillis()); }
    public Lyrics lyrics() { lyrics = Lyrics.forSong(current()); return lyrics; }

    // ---------------------------------------------------- Session persistence

    /** Called by the Activity when it stops, in case the process is killed without onDestroy. */
    public void saveStateNow() {
        saveState();
    }

    private void saveState() {
        if (prefs == null) return;
        lastStateSaveMs = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray();
            for (Song s : queue) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", text(s.title));
                o.put("artist", text(s.artist));
                o.put("album", text(s.album));
                o.put("path", text(s.path));
                o.put("duration", s.durationMs);
                arr.put(o);
            }
            prefs.edit()
                    .putString("session_queue", arr.toString())
                    .putInt("session_index", index)
                    .putInt("session_position", position())
                    .putBoolean("shuffle", shuffle)
                    .putInt("repeat", repeat)
                    .putFloat("speed", speed)
                    .putLong("sleep_remaining", sleepRemaining())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void restoreState() {
        shuffle = prefs.getBoolean("shuffle", false);
        repeat = prefs.getInt("repeat", 0) % 3;
        if (repeat < 0) repeat = 0;
        speed = prefs.getFloat("speed", 1f);
        long remaining = prefs.getLong("sleep_remaining", 0L);
        if (remaining > 0) sleepUntil = System.currentTimeMillis() + remaining;

        String raw = prefs.getString("session_queue", "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(raw);
            List<Song> restored = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String path = o.optString("path", "");
                if (path.isEmpty()) continue;
                restored.add(new Song(
                        o.optLong("id", -1L),
                        o.optString("title", ""),
                        o.optString("artist", "<unknown>"),
                        o.optString("album", ""),
                        path,
                        o.optLong("duration", 0L)));
            }
            if (restored.isEmpty()) return;
            queue.addAll(restored);
            index = Math.max(0, Math.min(prefs.getInt("session_index", 0), queue.size() - 1));
            pendingSeekMs = Math.max(0, prefs.getInt("session_position", 0));
            // Prepare without starting: playback resumes only when the user presses play.
            playCurrent(false);
        } catch (Exception ignored) {
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private void broadcast() {
        Intent i = new Intent(UPDATE).setPackage(getPackageName());
        i.putExtra("playing", isPlaying());
        i.putExtra("position", position());
        i.putExtra("duration", duration());
        i.putExtra("index", index);
        i.putExtra("shuffle", shuffle);
        i.putExtra("repeat", repeat);
        i.putExtra(EXTRA_ACCENT, accent);
        i.putExtra("crossfading", crossfading);
        sendBroadcast(i);
        if (System.currentTimeMillis() - lastStateSaveMs > 2000) saveState();
    }

    private void notifyWidget() {
        if (!MusicWidget.hasWidgets(this)) return;
        Song s = current();
        try {
            MusicWidget.refresh(this, s == null ? "The music player" : s.title,
                    s == null ? "Nothing selected" : s.artist, isPlaying());
        } catch (Exception ignored) {}
    }

    private void updateState(int state) {
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setState(state, position(), speed)
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                        PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackState.ACTION_SEEK_TO);
        session.setPlaybackState(b.build());
    }

    private void updateMetadata() {
        Song s = current();
        if (s == null) return;
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title == null ? s.fileName : s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist == null ? "<unknown>" : s.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album == null ? "" : s.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs);
        if (s.path != null) {
            android.graphics.Bitmap art = Art.get(this, s);
            if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        session.setMetadata(b.build());
    }

    private PendingIntent servicePi(String action) {
        return PendingIntent.getService(this, action.hashCode(),
                new Intent(this, MusicService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification notification() {
        Song s = current();
        String title = s == null ? "The music player" : s.title == null ? s.fileName : s.title;
        String sub = s == null ? "Ready to play" : s.artist == null ? "<unknown>" : s.artist;
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle(title)
                .setContentText(sub)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying());

        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,
                "Previous", servicePi(ACTION_PREV)).build());
        b.addAction(new Notification.Action.Builder(
                isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying() ? "Pause" : "Play", servicePi(ACTION_TOGGLE)).build());
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,
                "Next", servicePi(ACTION_NEXT)).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStyle(new Notification.MediaStyle()
                    .setMediaSession(session.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return b.build();
    }

    @Override public void onDestroy() {
        saveState();
        handler.removeCallbacks(tick);
        cancelCrossfade();
        try { player.stop(); player.release(); } catch (Exception ignored) {}
        if (fx != null) { fx.release(); fx = null; }
        if (incomingFx != null) { incomingFx.release(); incomingFx = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        session.setActive(false);
        session.release();
        super.onDestroy();
    }
}
