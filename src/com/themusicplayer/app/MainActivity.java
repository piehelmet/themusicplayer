package com.themusicplayer.app;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Translucent, animated music player UI. */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 7;
    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;

    private MusicService service;
    private final List<Song> allSongs = new ArrayList<>();
    private final List<Song> viewSongs = new ArrayList<>();
    private SongListAdapter listAdapter;
    private AlbumGridAdapter gridAdapter;

    private AmbientBackground ambient;
    private VisualizerView visualizer;
    private LinearLayout content, miniPlayer, controls;
    private FrameLayout browseArea, playerOverlay;
    private ListView list;
    private GridView grid;
    private TextView lyricsPane, miniTitle, miniSub;
    private ScrollView lyricsScroll;
    private EditText searchBox;
    private Button playlistPicker, genrePicker, gridToggle, playButton, miniPlay;
    private Spinner sortSpinner;
    private TextView elapsed, total, fullTitle, fullArtist, fullElapsed, fullTotal, fullLyrics;
    private SeekBar seekBar, fullSeek;
    private Button shuffleButton, repeatButton, crossfadeButton;
    private ImageView miniArt, fullArt, blurredArt;
    private View miniProgress;
    private int currentAccent = 0xFF5B8CFF;

    private Playlists playlists;
    private SharedPreferences settings;
    private String tab = "Songs";
    private boolean gridMode = false;
    private boolean userSeeking = false;
    private long lastLrcIndex = -2;
    private boolean animateItems = true;
    private int dragFrom = -1;
    private int dragStartY = 0;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MusicService.LocalBinder) binder).getService();
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int accent = intent.getIntExtra(MusicService.EXTRA_ACCENT, currentAccent);
            if (accent != currentAccent) animateAccent(accent);
            refresh();
            if ("Lyrics".equals(tab)) refreshLyrics();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlists = new Playlists(this);
        settings = getSharedPreferences("tmp_settings", MODE_PRIVATE);
        buildUi();
        requestPermissionsIfNeeded();
        bindService(new Intent(this, MusicService.class), connection, BIND_AUTO_CREATE);
        handleIntent(getIntent());
    }

    // ------------------------------------------------------------ UI kit

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout row(int orientation) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(orientation);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private Button glassButton(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setBackground(Glass.pill(currentAccent));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(4, 2, 4, 2);
        b.setLayoutParams(p);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(click);
        return b;
    }

    private TextView label(String text, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        return tv;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF08090E);
        ambient = new AmbientBackground(this);
        root.addView(ambient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = row(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(8));

        // Header
        LinearLayout header = row(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("THE MUSIC PLAYER", 13, 0xFFEEF2FF);
        brand.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brand.setLayoutParams(bp);
        header.addView(brand);
        gridToggle = glassButton("▦ Grid", v -> {
            gridMode = !gridMode;
            gridToggle.setText(gridMode ? "☷ List" : "▦ Grid");
            animatePop(gridToggle);
            rebuild();
        });
        header.addView(gridToggle);
        header.addView(glassButton("⋯", v -> showMainMenu(v)));
        content.addView(header);

        // Tabs
        String[] tabs = {"Songs", "Albums", "Artists", "Genres", "Folders", "Playlists", "Queue", "Lyrics"};
        LinearLayout tabRow = row(LinearLayout.HORIZONTAL);
        for (final String t : tabs) {
            Button b = glassButton(t, v -> {
                tab = t;
                if (searchBox != null) searchBox.setText("");
                animatePop(v);
                rebuild();
            });
            tabRow.addView(b);
        }
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(tabRow);
        content.addView(tabScroll);

        // Filter row
        LinearLayout filterRow = row(LinearLayout.HORIZONTAL);
        searchBox = new EditText(this);
        searchBox.setHint("Search library");
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(0x99FFFFFF);
        searchBox.setBackground(Glass.card(14));
        searchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { rebuild(); }
            public void afterTextChanged(Editable s) {}
        });
        filterRow.addView(searchBox);

        playlistPicker = glassButton("Playlist: —", v -> cycleName(playlists.names(), true));
        playlistPicker.setVisibility(View.GONE);
        filterRow.addView(playlistPicker);
        genrePicker = glassButton("Genre: —", v -> cycleName(Library.genres(this), false));
        genrePicker.setVisibility(View.GONE);
        filterRow.addView(genrePicker);

        sortSpinner = new Spinner(this);
        String[] sorts = {"Title", "Artist", "Album", "Duration", "Folder"};
        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sorts);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sa);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { rebuild(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        filterRow.addView(sortSpinner);
        content.addView(filterRow);

        // Browse area
        browseArea = new FrameLayout(this);
        browseArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(Color.TRANSPARENT);
        listAdapter = new SongListAdapter();
        list.setAdapter(listAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> onItemClick(pos));
        list.setOnItemLongClickListener((p, v, pos, id) -> { showItemMenu(v, pos); return true; });
        list.setOnTouchListener((v, event) -> handleQueueDrag(event));
        browseArea.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setBackgroundColor(Color.TRANSPARENT);
        gridAdapter = new AlbumGridAdapter();
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onItemClick(pos));
        browseArea.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lyricsScroll = new ScrollView(this);
        lyricsScroll.setVisibility(View.GONE);
        lyricsPane = label("", 14, 0xFFE8ECFF);
        lyricsPane.setPadding(dp(8), dp(8), dp(8), dp(8));
        lyricsScroll.addView(lyricsPane);
        browseArea.addView(lyricsScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(browseArea);

        // Mini player
        miniPlayer = row(LinearLayout.HORIZONTAL);
        miniPlayer.setBackground(Glass.card(18));
        miniPlayer.setPadding(dp(10), dp(8), dp(10), dp(8));
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniArt = new ImageView(this);
        miniArt.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniPlayer.addView(miniArt);
        LinearLayout meta = row(LinearLayout.VERTICAL);
        meta.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        miniTitle = label("Nothing selected", 14, 0xFFFFFFFF);
        miniTitle.setMaxLines(1);
        miniSub = label("Tap to open player", 11, 0xBBFFFFFF);
        miniSub.setMaxLines(1);
        meta.addView(miniTitle);
        meta.addView(miniSub);
        miniProgress = new View(this);
        miniProgress.setBackgroundColor(0xFF7AA2FF);
        miniProgress.setPivotX(0f);
        miniProgress.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        meta.addView(miniProgress);
        miniPlayer.addView(meta);
        miniPlay = glassButton("▶", v -> {
            if (service != null) service.toggle();
            animatePop(v);
        });
        miniPlayer.addView(miniPlay);
        miniPlayer.setOnClickListener(v -> openPlayer());
        content.addView(miniPlayer);

        // Slim progress row
        LinearLayout progressRow = row(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = label("0:00", 10, 0xAAFFFFFF);
        total = label("0:00", 10, 0xAAFFFFFF);
        seekBar = new SeekBar(this);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) elapsed.setText(Library.duration(p));
            }
            public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (service != null) service.seek(s.getProgress());
            }
        });
        progressRow.addView(elapsed);
        progressRow.addView(seekBar);
        progressRow.addView(total);
        content.addView(progressRow);

        // Main controls
        controls = row(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(glassButton("|<<", v -> { if (service != null) service.prev(); }));
        playButton = glassButton("Play", v -> { if (service != null) service.toggle(); });
        controls.addView(playButton);
        controls.addView(glassButton(">>|", v -> { if (service != null) service.next(true); }));
        shuffleButton = glassButton("Shuffle", v -> {
            if (service != null) { service.setShuffle(!service.shuffle()); refresh(); }
        });
        controls.addView(shuffleButton);
        repeatButton = glassButton("Repeat", v -> {
            if (service != null) { service.cycleRepeat(); refresh(); }
        });
        controls.addView(repeatButton);
        content.addView(controls);

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildPlayerOverlay(root);
        setContentView(root);
        rebuild();
    }

    private void buildPlayerOverlay(FrameLayout root) {
        playerOverlay = new FrameLayout(this);
        playerOverlay.setBackgroundColor(0xE9080910);
        playerOverlay.setVisibility(View.GONE);
        playerOverlay.setAlpha(0f);

        blurredArt = new ImageView(this);
        blurredArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Build.VERSION.SDK_INT >= 31)
            blurredArt.setRenderEffect(RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP));
        blurredArt.setAlpha(0.32f);
        playerOverlay.addView(blurredArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = row(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(24), dp(20), dp(20));
        panel.addView(glassButton("✕ Close", v -> closePlayer()));

        fullArt = new ImageView(this);
        fullArt.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        fullArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fullArt.setBackground(Glass.card(24));
        LinearLayout artWrap = row(LinearLayout.HORIZONTAL);
        artWrap.setGravity(Gravity.CENTER);
        artWrap.addView(fullArt);
        panel.addView(artWrap);

        visualizer = new VisualizerView(this);
        visualizer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        panel.addView(visualizer);

        fullTitle = label("Nothing selected", 23, 0xFFFFFFFF);
        fullTitle.setGravity(Gravity.CENTER);
        panel.addView(fullTitle);
        fullArtist = label("", 15, 0xC0FFFFFF);
        fullArtist.setGravity(Gravity.CENTER);
        panel.addView(fullArtist);

        fullSeek = new SeekBar(this);
        fullSeek.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fullSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) fullElapsed.setText(Library.duration(p));
            }
            public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (service != null) service.seek(s.getProgress());
            }
        });
        panel.addView(fullSeek);
        LinearLayout times = row(LinearLayout.HORIZONTAL);
        fullElapsed = label("0:00", 11, 0xAAFFFFFF);
        fullTotal = label("0:00", 11, 0xAAFFFFFF);
        fullElapsed.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fullTotal.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fullTotal.setGravity(Gravity.RIGHT);
        times.addView(fullElapsed);
        times.addView(fullTotal);
        panel.addView(times);

        LinearLayout bigControls = row(LinearLayout.HORIZONTAL);
        bigControls.setGravity(Gravity.CENTER);
        bigControls.addView(glassButton("|<<", v -> { if (service != null) service.prev(); }));
        bigControls.addView(glassButton("▶/❚❚", v -> { if (service != null) service.toggle(); }));
        bigControls.addView(glassButton(">>|", v -> { if (service != null) service.next(true); }));
        panel.addView(bigControls);

        crossfadeButton = glassButton("Crossfade", v -> {
            if (service != null) { service.setCrossfade(!service.crossfade()); refresh(); }
        });
        LinearLayout extras = row(LinearLayout.HORIZONTAL);
        extras.setGravity(Gravity.CENTER);
        extras.addView(crossfadeButton);
        extras.addView(glassButton("EQ", v -> showEqualizer()));
        extras.addView(glassButton("Speed", v -> askSpeed()));
        extras.addView(glassButton("Sleep", v -> askSleepTimer()));
        panel.addView(extras);

        fullLyrics = label("Lyrics appear here when a matching .lrc file is found.", 13, 0xD0FFFFFF);
        fullLyrics.setMaxLines(3);
        panel.addView(fullLyrics);

        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        scroller.addView(panel);
        playerOverlay.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(playerOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void animatePop(View v) {
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(110)
                .setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start())
                .start();
    }

    private void openPlayer() {
        if (playerOverlay.getVisibility() == View.VISIBLE) return;
        playerOverlay.setVisibility(View.VISIBLE);
        playerOverlay.setTranslationY(dp(180));
        playerOverlay.animate().alpha(1f).translationY(0f).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
        refresh();
    }

    private void closePlayer() {
        playerOverlay.animate().alpha(0f).translationY(dp(180)).setDuration(280)
                .withEndAction(() -> playerOverlay.setVisibility(View.GONE)).start();
    }

    private void animateAccent(int accent) {
        currentAccent = accent;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(),
                ambientAccent(), accent);
        anim.setDuration(900);
        anim.addUpdateListener(a -> {
            int c = (int) a.getAnimatedValue();
            ambient.setAccent(c);
            gridToggle.setBackground(Glass.pill(c));
            playButton.setBackground(Glass.pill(c));
            miniPlay.setBackground(Glass.pill(c));
            crossfadeButton.setBackground(Glass.pill(c));
        });
        anim.start();
    }

    private int ambientAccent() { return currentAccent; }

    // ------------------------------------------------------------ Library

    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        else loadLibrary();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_PERMISSIONS) loadLibrary();
    }

    private Set<String> excludedFolders() {
        Set<String> out = new LinkedHashSet<>();
        String raw = settings.getString("excluded_folders", "");
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void loadLibrary() {
        allSongs.clear();
        allSongs.addAll(Library.exclude(Library.load(this), excludedFolders()));
        if (allSongs.isEmpty())
            toast("No music found. Add audio files or check excluded folders.");
        animateItems = true;
        rebuild();
    }

    private String currentPlaylist() { return pickedName(playlists.names(), playlistPicker, "Playlist: "); }
    private String currentGenre() { return pickedName(Library.genres(this), genrePicker, "Genre: "); }

    private String pickedName(List<String> names, Button picker, String prefix) {
        if (names.isEmpty()) return null;
        String label = picker == null ? "" : picker.getText().toString();
        String selected = label.startsWith(prefix) ? label.substring(prefix.length()) : names.get(0);
        return names.contains(selected) ? selected : names.get(0);
    }

    private void cycleName(List<String> names, boolean playlist) {
        if (names.isEmpty()) { toast(playlist ? "No playlists yet" : "No genres found"); return; }
        Button picker = playlist ? playlistPicker : genrePicker;
        String prefix = playlist ? "Playlist: " : "Genre: ";
        String current = pickedName(names, picker, prefix);
        String next = names.get((names.indexOf(current) + 1) % names.size());
        picker.setText(prefix + next);
        animatePop(picker);
        rebuild();
    }

    private void rebuild() {
        viewSongs.clear();
        String q = searchBox == null ? "" : searchBox.getText().toString();

        playlistPicker.setVisibility("Playlists".equals(tab) ? View.VISIBLE : View.GONE);
        genrePicker.setVisibility("Genres".equals(tab) ? View.VISIBLE : View.GONE);
        if ("Playlists".equals(tab)) {
            String name = currentPlaylist();
            if (name != null) playlistPicker.setText("Playlist: " + name);
        }
        if ("Genres".equals(tab)) {
            String name = currentGenre();
            if (name != null) genrePicker.setText("Genre: " + name);
        }

        if ("Queue".equals(tab)) {
            if (service != null) viewSongs.addAll(service.queue());
        } else if ("Playlists".equals(tab)) {
            String name = currentPlaylist();
            if (name != null) {
                Set<Long> ids = new HashSet<>(playlists.ids(name));
                for (Song s : Library.filter(allSongs, q)) if (ids.contains(s.id)) viewSongs.add(s);
            }
        } else if ("Genres".equals(tab)) {
            String genre = currentGenre();
            if (genre != null) viewSongs.addAll(Library.inGenre(this, allSongs, genre));
            viewSongs.addAll(Library.filter(viewSongs, q));
        } else if ("Folders".equals(tab)) {
            viewSongs.addAll(Library.filter(allSongs, q));
        } else {
            List<Song> base = Library.filter(allSongs, q);
            if ("Albums".equals(tab)) base = Library.groupByAlbum(base);
            else if ("Artists".equals(tab)) base = Library.groupByArtist(base);
            viewSongs.addAll(base);
        }

        if (sortSpinner != null && !"Queue".equals(tab) && !"Lyrics".equals(tab)) {
            Library.Sort[] modes = {Library.Sort.TITLE, Library.Sort.ARTIST, Library.Sort.ALBUM,
                    Library.Sort.DURATION, Library.Sort.FOLDER};
            Library.sort(viewSongs, modes[sortMode()], true);
        }

        boolean gridOn = gridMode && ("Albums".equals(tab) || "Artists".equals(tab));
        boolean lyricsOn = "Lyrics".equals(tab);
        list.setVisibility(gridOn || lyricsOn ? View.GONE : View.VISIBLE);
        grid.setVisibility(gridOn ? View.VISIBLE : View.GONE);
        lyricsScroll.setVisibility(lyricsOn ? View.VISIBLE : View.GONE);
        listAdapter.notifyDataSetChanged();
        gridAdapter.notifyDataSetChanged();
        if (lyricsOn) refreshLyrics();
        refresh();
        animateItems = false;
    }

    private int sortMode() {
        return sortSpinner == null ? 0 : sortSpinner.getSelectedItemPosition();
    }

    private void onItemClick(int pos) {
        if (pos < 0 || pos >= viewSongs.size()) return;
        if (service == null) { toast("Player starting…"); return; }
        Song picked = viewSongs.get(pos);

        if ("Queue".equals(tab)) { service.playSongAt(pos); return; }
        if ("Playlists".equals(tab)) {
            String name = currentPlaylist();
            if (name == null) return;
            List<Song> ordered = new ArrayList<>();
            for (Long id : playlists.ids(name))
                for (Song s : allSongs) if (s.id == id) { ordered.add(s); break; }
            int at = -1;
            for (int i = 0; i < ordered.size(); i++)
                if (ordered.get(i).id == picked.id) { at = i; break; }
            service.playQueue(ordered, Math.max(0, at));
            return;
        }
        if ("Albums".equals(tab)) { service.playQueue(Library.ofAlbum(allSongs, picked), 0); return; }
        if ("Artists".equals(tab)) { service.playQueue(Library.ofArtist(allSongs, picked), 0); return; }
        service.playQueue(new ArrayList<>(viewSongs), pos);
    }

    private boolean handleQueueDrag(android.view.MotionEvent event) {
        if (!"Queue".equals(tab) || service == null) return false;
        int x = (int) event.getX(), y = (int) event.getY();
        int pos = list.pointToPosition(x, y);
        switch (event.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                dragFrom = pos;
                dragStartY = (int) event.getY();
                return false;
            case android.view.MotionEvent.ACTION_MOVE:
                if (dragFrom >= 0 && pos >= 0 && pos != dragFrom
                        && Math.abs((int) event.getY() - dragStartY) > dp(12)) {
                    service.reorder(dragFrom, pos);
                    dragFrom = pos;
                    dragStartY = (int) event.getY();
                    return true;
                }
                return false;
            default:
                dragFrom = -1;
                return false;
        }
    }

    // --------------------------------------------------------- Adapters

    private class SongListAdapter extends BaseAdapter {
        @Override public int getCount() { return viewSongs.size(); }
        @Override public Song getItem(int pos) { return viewSongs.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout v = convertView == null ? row(LinearLayout.HORIZONTAL)
                    : (LinearLayout) convertView;
            if (convertView == null) {
                v.setBackground(Glass.card(14));
                v.setPadding(dp(8), dp(6), dp(8), dp(6));
                ImageView art = new ImageView(MainActivity.this);
                art.setId(1);
                art.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.addView(art);
                LinearLayout texts = row(LinearLayout.VERTICAL);
                texts.setId(2);
                texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView title = label("", 14, 0xFFFFFFFF);
                title.setId(3);
                title.setMaxLines(1);
                TextView sub = label("", 11, 0xAFFFFFFF);
                sub.setId(4);
                sub.setMaxLines(1);
                texts.addView(title);
                texts.addView(sub);
                v.addView(texts);
            }
            Song s = getItem(pos);
            ImageView art = (ImageView) v.findViewById(1);
            art.setImageDrawable(drawableFor(s));
            ((TextView) v.findViewById(3)).setText(s == null ? "" : s.title);
            ((TextView) v.findViewById(4)).setText(s == null ? ""
                    : (s.artist == null ? "<unknown>" : s.artist) + "   •   " + Library.duration(s.durationMs));
            if (animateItems && pos < 12) {
                v.setAlpha(0f);
                v.setTranslationX(dp(28));
                v.animate().alpha(1f).translationX(0f)
                        .setDuration(320).setStartDelay(pos * 26L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            return v;
        }
    }

    private class AlbumGridAdapter extends BaseAdapter {
        @Override public int getCount() { return viewSongs.size(); }
        @Override public Song getItem(int pos) { return viewSongs.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout v = convertView == null ? row(LinearLayout.VERTICAL)
                    : (LinearLayout) convertView;
            if (convertView == null) {
                v.setBackground(Glass.card(18));
                v.setPadding(dp(6), dp(6), dp(6), dp(8));
                ImageView art = new ImageView(MainActivity.this);
                art.setId(1);
                art.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.addView(art);
                TextView title = label("", 12, 0xFFFFFFFF);
                title.setId(2);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(1);
                v.addView(title);
            }
            Song s = getItem(pos);
            ((ImageView) v.findViewById(1)).setImageDrawable(drawableFor(s));
            String name = s == null ? "" : "Albums".equals(tab) ? s.album : s.artist;
            ((TextView) v.findViewById(2)).setText(name == null || name.trim().isEmpty()
                    ? "<unknown>" : name);
            return v;
        }
    }

    private Drawable drawableFor(Song s) {
        Bitmap b = Art.get(this, s);
        return b == null ? Glass.card(12) : Art.rounded(b, dp(12));
    }

    // ---------------------------------------------------------- Menus

    private void showItemMenu(View anchor, int pos) {
        if (pos < 0 || pos >= viewSongs.size()) return;
        final Song song = viewSongs.get(pos);
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();
        m.add("Play now");
        m.add("Play next");
        m.add("Add to queue");
        SubMenu pl = m.addSubMenu("Add to playlist");
        for (final String name : playlists.names()) {
            pl.add(name).setOnMenuItemClickListener(item -> {
                List<Long> ids = playlists.ids(name);
                if (!ids.contains(song.id)) { ids.add(song.id); playlists.save(name, ids); toast("Added"); }
                return true;
            });
        }
        pl.add("New playlist…").setOnMenuItemClickListener(item -> { askNewPlaylist(song); return true; });
        if ("Queue".equals(tab)) {
            m.add("Move up").setOnMenuItemClickListener(item -> { moveQueue(pos, -1); return true; });
            m.add("Move down").setOnMenuItemClickListener(item -> { moveQueue(pos, 1); return true; });
            m.add("Remove from queue").setOnMenuItemClickListener(item -> {
                service.removeAt(pos); rebuild(); return true;
            });
        }
        if ("Playlists".equals(tab))
            m.add("Remove from playlist").setOnMenuItemClickListener(item -> {
                String name = currentPlaylist();
                if (name == null) return true;
                List<Long> ids = playlists.ids(name);
                ids.remove(song.id);
                playlists.save(name, ids);
                rebuild();
                return true;
            });
        m.add("Edit tags…").setOnMenuItemClickListener(item -> { editTags(song); return true; });
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (service == null) return true;
            if ("Play now".equals(title)) onItemClick(pos);
            else if ("Play next".equals(title)) { service.insertNext(song); toast("Playing next"); }
            else if ("Add to queue".equals(title)) {
                List<Song> one = new ArrayList<>(); one.add(song);
                service.addToQueue(one);
                toast("Added to queue");
            }
            return true;
        });
        menu.show();
    }

    private void moveQueue(int pos, int delta) {
        if (service == null) return;
        int to = pos + delta;
        if (to < 0 || to >= service.queue().size()) return;
        service.reorder(pos, to);
        rebuild();
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();
        m.add("New playlist");
        m.add("Save queue as playlist");
        m.add("Manage playlists");
        m.add("Sleep timer");
        m.add("Playback speed");
        m.add("Equalizer");
        m.add("Volume");
        m.add("Crossfade: " + (service != null && service.crossfade() ? "on" : "off"));
        m.add("Fade length");
        m.add("ReplayGain: " + (service != null && service.replayGain() ? "on" : "off"));
        m.add("Excluded folders");
        m.add("Backup to file");
        m.add("Restore from file");
        m.add("Rescan library");
        m.add("About");
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.startsWith("Crossfade")) {
                if (service != null) { service.setCrossfade(!service.crossfade()); toast(t); }
            } else if (t.startsWith("ReplayGain")) {
                if (service != null) { service.setReplayGain(!service.replayGain()); toast(t); }
            } else switch (t) {
                case "New playlist": askNewPlaylist(null); break;
                case "Save queue as playlist": saveQueueAsPlaylist(); break;
                case "Manage playlists": managePlaylists(); break;
                case "Sleep timer": askSleepTimer(); break;
                case "Playback speed": askSpeed(); break;
                case "Equalizer": showEqualizer(); break;
                case "Volume": askVolume(); break;
                case "Fade length": askFade(); break;
                case "Excluded folders": chooseExcluded(); break;
                case "Backup to file": backupToFile(); break;
                case "Restore from file": restoreFromFile(); break;
                case "Rescan library": loadLibrary(); break;
                case "About": about(); break;
            }
            return true;
        });
        menu.show();
    }

    // --------------------------------------------------------- Dialogs

    private void askNewPlaylist(final Song first) {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this).setTitle("New playlist").setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { toast("Enter a name"); return; }
                    List<Long> ids = new ArrayList<>();
                    if (first != null) ids.add(first.id);
                    playlists.save(name, ids);
                    tab = "Playlists";
                    playlistPicker.setText("Playlist: " + name);
                    rebuild();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void saveQueueAsPlaylist() {
        if (service == null || service.queue().isEmpty()) { toast("Queue is empty"); return; }
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this).setTitle("Save queue").setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    List<Long> ids = new ArrayList<>();
                    for (Song s : service.queue()) ids.add(s.id);
                    playlists.save(name, ids);
                    toast("Saved " + ids.size() + " songs");
                }).setNegativeButton("Cancel", null).show();
    }

    private void managePlaylists() {
        final List<String> names = playlists.names();
        if (names.isEmpty()) { toast("No playlists yet"); return; }
        new AlertDialog.Builder(this).setTitle("Playlists")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    final String name = names.get(which);
                    new AlertDialog.Builder(this).setTitle(name)
                            .setItems(new String[]{"Play", "Rename", "Delete", "Copy text"}, (d2, w2) -> {
                                if (w2 == 0) {
                                    List<Song> songs = new ArrayList<>();
                                    for (Long id : playlists.ids(name))
                                        for (Song s : allSongs) if (s.id == id) { songs.add(s); break; }
                                    if (service != null) service.playQueue(songs, 0);
                                    tab = "Queue";
                                    rebuild();
                                } else if (w2 == 1) {
                                    final EditText input = new EditText(this);
                                    input.setText(name);
                                    new AlertDialog.Builder(this).setTitle("Rename").setView(input)
                                            .setPositiveButton("OK", (d3, w3) -> {
                                                String nn = input.getText().toString().trim();
                                                if (!nn.isEmpty()) { playlists.rename(name, nn); rebuild(); }
                                            }).setNegativeButton("Cancel", null).show();
                                } else if (w2 == 2) {
                                    new AlertDialog.Builder(this).setMessage("Delete \"" + name + "\"?")
                                            .setPositiveButton("Delete", (d3, w3) -> { playlists.delete(name); rebuild(); })
                                            .setNegativeButton("Cancel", null).show();
                                } else {
                                    android.content.ClipboardManager cb = (android.content.ClipboardManager)
                                            getSystemService(CLIPBOARD_SERVICE);
                                    cb.setPrimaryClip(android.content.ClipData.newPlainText(
                                            "playlists", playlists.exportAll()));
                                    toast("Copied");
                                }
                            }).show();
                }).show();
    }

    private void askSleepTimer() {
        final String[] mins = {"Off", "5 min", "10 min", "15 min", "30 min", "45 min", "60 min"};
        new AlertDialog.Builder(this).setTitle("Sleep timer")
                .setItems(mins, (d, w) -> {
                    if (service != null)
                        service.setSleepTimer(w == 0 ? 0 : Integer.parseInt(mins[w].split(" ")[0]));
                    toast(w == 0 ? "Sleep timer off" : "Sleep timer: " + mins[w]);
                }).show();
    }

    private void askSpeed() {
        final String[] speeds = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
        new AlertDialog.Builder(this).setTitle("Playback speed")
                .setItems(speeds, (d, w) -> {
                    if (service != null) service.setSpeed(Float.parseFloat(speeds[w].replace("x", "")));
                    refresh();
                }).show();
    }

    private void askVolume() {
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(service == null ? 100 : Math.round(service.volume() * 100));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && service != null) service.setVolume(p / 100f);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        new AlertDialog.Builder(this).setTitle("Playback volume").setView(sb)
                .setPositiveButton("Done", null).show();
    }

    private void askFade() {
        final String[] values = {"2 s", "4 s", "5 s", "8 s", "10 s", "15 s"};
        new AlertDialog.Builder(this).setTitle("Crossfade length")
                .setItems(values, (d, w) -> {
                    if (service != null) service.setFadeSeconds(Integer.parseInt(values[w].split(" ")[0]));
                }).show();
    }

    private void chooseExcluded() {
        final List<String> folders = Library.folders(allSongs);
        if (folders.isEmpty()) { toast("No folders found"); return; }
        final Set<String> excluded = excludedFolders();
        boolean[] checked = new boolean[folders.size()];
        for (int i = 0; i < folders.size(); i++) checked[i] = excluded.contains(folders.get(i));
        new AlertDialog.Builder(this).setTitle("Folders to hide")
                .setMultiChoiceItems(folders.toArray(new String[0]), checked,
                        (DialogInterface d, int w, boolean on) -> {
                            if (on) excluded.add(folders.get(w)); else excluded.remove(folders.get(w));
                        })
                .setPositiveButton("Apply", (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (String f : excluded) sb.append(f).append('\n');
                    settings.edit().putString("excluded_folders", sb.toString()).apply();
                    loadLibrary();
                    toast("Library updated");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEqualizer() {
        if (service == null || service.equalizer() == null) { toast("Equalizer unavailable"); return; }
        final android.media.audiofx.Equalizer eq = service.equalizer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = row(LinearLayout.VERTICAL);
        CheckBox enable = new CheckBox(this);
        enable.setText("Enable equalizer");
        enable.setTextColor(0xFFFFFFFF);
        enable.setChecked(eq.getEnabled());
        enable.setOnCheckedChangeListener((b, on) -> service.setEqualizerEnabled(on));
        box.addView(enable);

        List<String> names = new ArrayList<>();
        for (short i = 0; i < eq.getNumberOfPresets(); i++) names.add(eq.getPresetName(i));
        Spinner presets = new Spinner(this);
        ArrayAdapter<String> pa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presets.setAdapter(pa);
        presets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                service.setPreset((short) pos);
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        box.addView(presets);

        short bands = eq.getNumberOfBands();
        final short min = eq.getBandLevelRange()[0], max = eq.getBandLevelRange()[1];
        for (short i = 0; i < bands; i++) {
            TextView t = label(eq.getBandFreqRange(i)[0] / 1000 + "–"
                    + eq.getBandFreqRange(i)[1] / 1000 + " Hz", 12, 0xFFE0E8FF);
            box.addView(t);
            SeekBar sb = new SeekBar(this);
            sb.setMax(max - min);
            sb.setProgress(eq.getBandLevel(i) - min);
            final short band = i;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (fromUser) service.setBand(band, (short) (p + min));
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(sb);
        }
        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Equalizer").setView(scroll)
                .setPositiveButton("Done", null).show();
    }

    private void editTags(final Song song) {
        final EditText title = new EditText(this), artist = new EditText(this), album = new EditText(this);
        title.setText(song.title == null ? "" : song.title);
        artist.setText(song.artist == null ? "" : song.artist);
        album.setText(song.album == null ? "" : song.album);
        title.setHint("Title"); artist.setHint("Artist"); album.setHint("Album");
        LinearLayout box = row(LinearLayout.VERTICAL);
        box.addView(title); box.addView(artist); box.addView(album);
        new AlertDialog.Builder(this).setTitle("Edit tags").setMessage(song.fileName).setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.Audio.Media.TITLE, title.getText().toString());
                    values.put(MediaStore.Audio.Media.ARTIST, artist.getText().toString());
                    values.put(MediaStore.Audio.Media.ALBUM, album.getText().toString());
                    try {
                        int n = getContentResolver().update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                values, MediaStore.Audio.Media._ID + "=?",
                                new String[]{String.valueOf(song.id)});
                        toast(n > 0 ? "Saved" : "Blocked by storage policy");
                        Art.clear(song.path);
                        ReplayGain.clear(song.path);
                        loadLibrary();
                    } catch (Exception e) { toast("Could not update tags"); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void backupToFile() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "themusicplayer-playlists.txt");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void restoreFromFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("text/plain");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) os.write(playlists.exportAll().getBytes(StandardCharsets.UTF_8));
                    toast("Backed up");
                }
            } else if (req == REQ_IMPORT) {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) return;
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    playlists.importAll(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    loadLibrary();
                    toast("Restored");
                }
            }
        } catch (Exception e) { toast("Could not read/write file"); }
    }

    private void about() {
        new AlertDialog.Builder(this).setTitle("The music player")
                .setMessage("An original, offline music player.\n\n"
                        + "High-end translucent UI, animated ambient backgrounds, "
                        + "album art, crossfade, ReplayGain, equalizer, playlists, "
                        + "queue, lyrics, sleep timer, widgets and media controls.\n\n"
                        + "No internet permission. No ads. No tracking.")
                .setPositiveButton("OK", null).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    // ------------------------------------------------------ Lifecycle

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MusicService.UPDATE);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(updateReceiver, filter);
    }

    @Override protected void onStop() {
        unregisterReceiver(updateReceiver);
        super.onStop();
    }

    @Override protected void onDestroy() {
        try { unbindService(connection); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        String path = null;
        if ("file".equals(uri.getScheme())) path = uri.getPath();
        else {
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{MediaStore.Audio.Media.DATA}, null, null, null)) {
                if (c != null && c.moveToFirst()) path = c.getString(0);
            } catch (Exception ignored) {}
        }
        if (path == null) { toast("Cannot open this file"); return; }
        Song song = new Song(-1, null, "<unknown>", null, path, 0);
        List<Song> one = new ArrayList<>();
        one.add(song);
        if (service != null) service.playQueue(one, 0);
        else toast("Player starting…");
    }

    private void refreshLyrics() {
        if (service == null) return;
        Lyrics l = service.lyrics();
        String text = l.isEmpty() ? "No .lrc / .txt lyrics file found next to this song."
                : l.textAt(service.position());
        if (!text.equals(fullLyrics.getText().toString())) fullLyrics.setText(text);
        long idx = l.indexAt(service.position());
        if (idx == lastLrcIndex) return;
        lastLrcIndex = idx;
        if (l.isEmpty()) { lyricsPane.setText(text); return; }
        StringBuilder sb = new StringBuilder();
        for (Lyrics.Line line : l.lines()) {
            String rowText = Library.duration(line.timeMs) + "  " + line.text;
            sb.append(line.timeMs == l.lines().get((int) Math.max(0, idx)).timeMs
                    ? "▶ " : "  ").append(rowText).append('\n');
        }
        lyricsPane.setText(sb.toString());
    }

    private void refresh() {
        if (service == null) return;
        Song s = service.current();
        boolean playing = service.isPlaying();

        String title = s == null ? "Nothing selected" : s.title == null ? s.fileName : s.title;
        String artist = s == null ? "Tap a song to play" :
                s.artist == null || s.artist.trim().isEmpty() ? "<unknown artist>" : s.artist;
        miniTitle.setText(title);
        miniSub.setText(artist);
        fullTitle.setText(title);
        fullArtist.setText(artist);
        playButton.setText(playing ? "Pause" : "Play");
        miniPlay.setText(playing ? "❚❚" : "▶");
        shuffleButton.setText(service.shuffle() ? "Shuffle ●" : "Shuffle");
        String[] repeatText = {"Repeat", "Repeat all", "Repeat one"};
        repeatButton.setText(repeatText[service.repeat()]);
        crossfadeButton.setText(service.crossfade() ? "Crossfade ●" : "Crossfade");
        visualizer.setPlaying(playing);
        visualizer.setAccent(currentAccent);

        Bitmap art = Art.get(this, s);
        if (art != null) {
            miniArt.setImageBitmap(art);
            fullArt.setImageBitmap(art);
            blurredArt.setImageBitmap(art);
        } else {
            miniArt.setImageDrawable(Glass.card(12));
            fullArt.setImageDrawable(Glass.card(12));
        }

        int dur = service.duration(), pos = service.position();
        seekBar.setMax(Math.max(1, dur));
        fullSeek.setMax(Math.max(1, dur));
        if (!userSeeking) { seekBar.setProgress(pos); fullSeek.setProgress(pos); }
        elapsed.setText(Library.duration(pos));
        total.setText(Library.duration(dur));
        fullElapsed.setText(Library.duration(pos));
        fullTotal.setText(Library.duration(dur));
        miniProgress.setScaleX(dur > 0 ? pos / (float) dur : 0f);

        if (service.accentColor() != currentAccent) animateAccent(service.accentColor());
        if ("Queue".equals(tab) && service.queue().size() != viewSongs.size()) rebuild();
    }
}
