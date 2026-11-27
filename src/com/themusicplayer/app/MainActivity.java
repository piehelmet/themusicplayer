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
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Translucent, animated music player UI. */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 7;
    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;
    private static final String[] SORT_NAMES = {"Title", "Artist", "Album", "Duration", "Folder"};
    private static final String[] TABS = {"Songs", "Albums", "Artists", "Genres", "Folders",
            "Playlists", "Queue", "Lyrics", "History"};

    private MusicService service;
    private final List<Song> allSongs = new ArrayList<>();
    private final List<Song> viewSongs = new ArrayList<>();
    private SongListAdapter listAdapter;
    private AlbumGridAdapter gridAdapter;

    private AmbientBackground ambient;
    private VisualizerView visualizer;
    private LinearLayout content, miniPlayer;
    private FrameLayout browseArea, playerOverlay;
    private ListView list;
    private GridView grid;
    private TextView lyricsPane, miniTitle, miniSub;
    private ScrollView lyricsScroll;
    private EditText searchBox;
    private TextView playlistPicker, genrePicker, folderPicker, gridToggle, playButton, miniPlay;
    private TextView sortButton, shuffleButton, repeatButton, crossfadeButton, visualizerButton;
    private TextView fullTitle, fullArtist, fullElapsed, fullTotal, fullLyrics;
    private SeekBar fullSeek;
    private ImageView miniArt, fullArt, blurredArt, backdropArt;
    private View miniProgress;
    private final List<TextView> tabChips = new ArrayList<>();
    private String artPathKey = "";
    private ValueAnimator accentAnimator;
    private boolean visualizerOn = false;
    private int sortIndex = 0;
    private int currentAccent = Theme.baseAccent();

    private Playlists playlists;
    private History history;
    private SharedPreferences settings;
    private String tab = "Songs";
    private boolean gridMode = false;
    private boolean userSeeking = false;
    private long lastLrcIndex = -2;
    private long highlightedId = -2;
    private int highlightedAccent = 0;
    private boolean animateItems = true;
    private int dragFrom = -1;
    private int dragStartY = 0;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable applySearch = this::rebuild;
    private final Map<String, Integer> albumTrackCounts = new HashMap<>();
    private final Map<String, Integer> artistTrackCounts = new HashMap<>();
    private final Map<String, Integer> artistAlbumCounts = new HashMap<>();
    private final Set<Integer> animatedRows = new HashSet<>();
    private Drawable placeholderArt;
    private int placeholderTheme = -1;

    private final ExecutorService artLoader = Executors.newFixedThreadPool(2);
    private final Map<String, List<ImageView>> pendingArtViews = new HashMap<>();
    private List<String> cachedGenres = new ArrayList<>();
    private final List<History.Entry> historyEntries = new ArrayList<>();
    private final Map<String, History.Entry> historyByPath = new HashMap<>();
    private long historyVersion = -1;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MusicService.LocalBinder) binder).getService();
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int accent = Theme.accent(intent.getIntExtra(
                    MusicService.EXTRA_ACCENT, currentAccent));
            if (accent != currentAccent) animateAccent(accent);
            refresh();
            if ("Lyrics".equals(tab)) refreshLyrics();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlists = new Playlists(this);
        history = new History(this);
        settings = getSharedPreferences("tmp_settings", MODE_PRIVATE);
        Theme.set(settings.getInt("theme", Theme.OBSIDIAN));
        currentAccent = Theme.baseAccent();
        visualizerOn = settings.getBoolean("visualizer_on", false);
        buildUi();
        restoreUiState();
        requestPermissionsIfNeeded();
        // Keep playback alive even when this Activity is recreated or unbound.
        startService(new Intent(this, MusicService.class));
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

    private TextView label(String text, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        return tv;
    }

    private TextView section(String text) {
        TextView tv = label(text, 10, Theme.section());
        tv.setLetterSpacing(0.2f);
        return tv;
    }

    /** Modern rounded chip; no default Android button chrome. */
    private TextView chip(String text, boolean selected, View.OnClickListener click) {
        TextView tv = label(text, 12, Theme.chipText(selected));
        tv.setTag(selected);
        tv.setBackground(Glass.chip(selected, currentAccent));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(16), dp(9), dp(16), dp(9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        tv.setLayoutParams(p);
        tv.setOnClickListener(v -> { animatePop(v); click.onClick(v); });
        return tv;
    }

    private void setChipSelected(TextView chip, boolean selected) {
        if (chip == null) return;
        if (Boolean.TRUE.equals(chip.getTag()) == selected) return;
        chip.setTag(selected);
        chip.setBackground(Glass.chip(selected, currentAccent));
        chip.setTextColor(Theme.chipText(selected));
    }

    /** Circular icon button built from a TextView, so it never looks like a stock Button. */
    private TextView iconButton(String glyph, float size, boolean filled, View.OnClickListener click) {
        TextView tv = label(glyph, size, filled ? Theme.onAccent() : Theme.textPrimary());
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Glass.circle(filled, currentAccent));
        int d = dp(Math.round(size * 2.5f));
        tv.setMinimumWidth(d);
        tv.setMinimumHeight(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(d, d);
        p.setMargins(dp(5), dp(4), dp(5), dp(4));
        tv.setLayoutParams(p);
        tv.setOnClickListener(v -> { animatePop(v); click.onClick(v); });
        return tv;
    }

    private SeekBar styledSeek() {
        SeekBar bar = new SeekBar(this);
        bar.setProgressTintList(ColorStateList.valueOf(currentAccent));
        bar.setThumbTintList(ColorStateList.valueOf(currentAccent));
        return bar;
    }

    private void styleSeek(SeekBar bar) {
        bar.setProgressTintList(ColorStateList.valueOf(currentAccent));
        bar.setThumbTintList(ColorStateList.valueOf(currentAccent));
    }

    private void updateTabs() {
        for (TextView chip : tabChips)
            setChipSelected(chip, chip.getText().toString().equals(tab));
    }

    private String[] sortNames() {
        if ("Albums".equals(tab))
            return new String[]{"Album", "Artist", "Duration", "Folder"};
        if ("Artists".equals(tab))
            return new String[]{"Artist", "Album", "Duration", "Folder"};
        if ("Playlists".equals(tab))
            return new String[]{"Order", "Title", "Artist", "Album", "Duration", "Folder"};
        return SORT_NAMES;
    }

    private Library.Sort[] sortModes() {
        if ("Albums".equals(tab))
            return new Library.Sort[]{Library.Sort.ALBUM, Library.Sort.ARTIST,
                    Library.Sort.DURATION, Library.Sort.FOLDER};
        if ("Artists".equals(tab))
            return new Library.Sort[]{Library.Sort.ARTIST, Library.Sort.ALBUM,
                    Library.Sort.DURATION, Library.Sort.FOLDER};
        if ("Playlists".equals(tab))
            return new Library.Sort[]{null, Library.Sort.TITLE, Library.Sort.ARTIST,
                    Library.Sort.ALBUM, Library.Sort.DURATION, Library.Sort.FOLDER};
        return new Library.Sort[]{Library.Sort.TITLE, Library.Sort.ARTIST,
                Library.Sort.ALBUM, Library.Sort.DURATION, Library.Sort.FOLDER};
    }

    private int sortPosition() {
        int max = sortNames().length - 1;
        if (sortIndex < 0) sortIndex = 0;
        if (sortIndex > max) sortIndex = max;
        return sortIndex;
    }

    private void updateSortLabel() {
        if (sortButton == null) return;
        sortButton.setText("Sort: " + sortNames()[sortPosition()]);
    }

    private void applyVisualizerState() {
        if (visualizerButton != null) {
            visualizerButton.setText(visualizerOn ? "Visualizer ✓" : "Visualizer");
            setChipSelected(visualizerButton, visualizerOn);
        }
        if (visualizer != null)
            visualizer.setVisibility(visualizerOn ? View.VISIBLE : View.GONE);
    }

    private void toggleVisualizer() {
        visualizerOn = !visualizerOn;
        settings.edit().putBoolean("visualizer_on", visualizerOn).apply();
        applyVisualizerState();
        if (service != null) refresh();
        toast(visualizerOn ? "Visualizer on" : "Visualizer off");
    }

    private void switchTheme() {
        boolean playerWasOpen = playerOverlay != null
                && playerOverlay.getVisibility() == View.VISIBLE;
        String query = searchBox == null ? "" : searchBox.getText().toString();
        int next = Theme.next();
        settings.edit().putInt("theme", next).apply();
        Theme.set(next);
        currentAccent = service == null || service.current() == null
                ? Theme.baseAccent() : Theme.accent(service.accentColor());
        if (accentAnimator != null) accentAnimator.cancel();
        if (visualizer != null) visualizer.setPlaying(false);
        if (playerOverlay != null) playerOverlay.animate().cancel();
        artPathKey = "";
        buildUi();
        setQuery(query);
        if (playerWasOpen) openPlayer();
        toast(Theme.name() + " theme");
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.root());

        // Immersive, blurred artwork behind the whole library.
        backdropArt = new ImageView(this);
        backdropArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Build.VERSION.SDK_INT >= 31)
            backdropArt.setRenderEffect(RenderEffect.createBlurEffect(64f, 64f, Shader.TileMode.CLAMP));
        backdropArt.setAlpha(Theme.backdropAlpha());
        root.addView(backdropArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ambient = new AmbientBackground(this);
        root.addView(ambient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = row(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(14));

        // Header
        LinearLayout header = row(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = row(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titles.addView(section("YOUR LIBRARY"));
        TextView brand = label("The music player", 22, Theme.textPrimary());
        brand.setTypeface(Theme.display());
        brand.setLetterSpacing(Theme.brandSpacing());
        titles.addView(brand);
        header.addView(titles);
        gridToggle = iconButton(gridMode ? "☰" : "▦", 17, false, v -> {
            gridMode = !gridMode;
            gridToggle.setText(gridMode ? "☰" : "▦");
            rebuild();
        });
        header.addView(gridToggle);
        header.addView(iconButton("⋯", 19, false, v -> showMainMenu(v)));
        content.addView(header);

        // A fine rule is the signature of the light themes and the Glass material.
        if (Theme.isLight() || Theme.mode() == Theme.GLASS) {
            View rule = new View(this);
            rule.setBackgroundColor(Theme.rule());
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            ruleParams.setMargins(0, dp(8), 0, 0);
            rule.setLayoutParams(ruleParams);
            content.addView(rule);
        }

        // Search field
        LinearLayout searchBar = row(LinearLayout.HORIZONTAL);
        searchBar.setBackground(Glass.card(20));
        searchBar.setPadding(dp(14), dp(2), dp(14), dp(2));
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.addView(label("⌕", 18, Theme.icon()));
        searchBox = new EditText(this);
        searchBox.setHint("Search songs, artists, albums, folders");
        searchBox.setTextColor(Theme.textPrimary());
        searchBox.setHintTextColor(Theme.hint());
        searchBox.setBackground(null);
        searchBox.setSingleLine(true);
        searchBox.setPadding(dp(10), dp(10), dp(4), dp(10));
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { scheduleSearch(); }
            public void afterTextChanged(Editable s) {}
        });
        searchBar.addView(searchBox);
        content.addView(searchBar);

        // Filter chips
        LinearLayout filterRow = row(LinearLayout.HORIZONTAL);
        sortButton = chip("Sort: " + sortNames()[sortPosition()], false, v -> {
            sortIndex = (sortIndex + 1) % sortNames().length;
            updateSortLabel();
            rebuild();
        });
        filterRow.addView(sortButton);
        playlistPicker = chip("Playlist: —", false,
                v -> cycleName(playlists.names(), playlistPicker, "Playlist: "));
        playlistPicker.setVisibility(View.GONE);
        filterRow.addView(playlistPicker);
        genrePicker = chip("Genre: —", false,
                v -> cycleName(cachedGenres, genrePicker, "Genre: "));
        genrePicker.setVisibility(View.GONE);
        filterRow.addView(genrePicker);
        folderPicker = chip("Folder: —", false,
                v -> cycleName(Library.folders(allSongs), folderPicker, "Folder: "));
        LinearLayout.LayoutParams folderParams =
                (LinearLayout.LayoutParams) folderPicker.getLayoutParams();
        folderParams.width = 0;
        folderParams.weight = 1f;
        folderPicker.setLayoutParams(folderParams);
        folderPicker.setVisibility(View.GONE);
        filterRow.addView(folderPicker);
        content.addView(filterRow);

        // Navigation chips
        LinearLayout tabRow = row(LinearLayout.HORIZONTAL);
        for (final String t : TABS) {
            TextView c = chip(t, t.equals(tab), v -> {
                tab = t;
                setQuery("");
                sortIndex = 0;
                updateSortLabel();
                updateTabs();
                rebuild();
            });
            tabChips.add(c);
            tabRow.addView(c);
        }
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(tabRow);
        content.addView(tabScroll);

        // Browse area
        browseArea = new FrameLayout(this);
        browseArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(6));
        listAdapter = new SongListAdapter();
        list.setAdapter(listAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> onItemClick(pos));
        list.setOnItemLongClickListener((p, v, pos, id) -> { showItemMenu(v, pos); return true; });
        list.setOnTouchListener((v, event) -> handleQueueDrag(event));
        browseArea.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setBackgroundColor(Color.TRANSPARENT);
        gridAdapter = new AlbumGridAdapter();
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onItemClick(pos));
        browseArea.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lyricsScroll = new ScrollView(this);
        lyricsScroll.setVisibility(View.GONE);
        lyricsPane = label("", 14, Theme.lyrics());
        lyricsPane.setLineSpacing(dp(6), 1f);
        lyricsPane.setPadding(dp(8), dp(8), dp(8), dp(8));
        lyricsScroll.addView(lyricsPane);
        browseArea.addView(lyricsScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(browseArea);

        // Floating mini player
        miniPlayer = row(LinearLayout.HORIZONTAL);
        miniPlayer.setBackground(Glass.card(22));
        miniPlayer.setElevation(dp(10));
        miniPlayer.setPadding(dp(8), dp(8), dp(8), dp(8));
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        miniParams.setMargins(0, dp(8), 0, 0);
        miniPlayer.setLayoutParams(miniParams);
        miniArt = new ImageView(this);
        miniArt.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniPlayer.addView(miniArt);
        LinearLayout meta = row(LinearLayout.VERTICAL);
        meta.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        meta.setPadding(dp(12), 0, dp(6), 0);
        miniTitle = label("Nothing selected", 15, Theme.textPrimary());
        miniTitle.setMaxLines(1);
        miniTitle.setTypeface(Theme.display());
        miniSub = label("Tap to open player", 12, Theme.sub());
        miniSub.setMaxLines(1);
        meta.addView(miniTitle);
        meta.addView(miniSub);
        miniProgress = new View(this);
        miniProgress.setBackgroundColor(currentAccent);
        miniProgress.setPivotX(0f);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        progressParams.setMargins(0, dp(6), 0, 0);
        miniProgress.setLayoutParams(progressParams);
        meta.addView(miniProgress);
        miniPlayer.addView(meta);
        miniPlay = iconButton("▶", 15, true, v -> {
            if (service != null) service.toggle();
        });
        miniPlayer.addView(miniPlay);
        miniPlayer.setOnClickListener(v -> openPlayer());
        content.addView(miniPlayer);

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildPlayerOverlay(root);
        setContentView(root);
        updateTabs();
        applyVisualizerState();
        rebuild();
    }

    private void restoreUiState() {
        String savedTab = settings.getString("ui_tab", "Songs");
        tab = Arrays.asList(TABS).contains(savedTab) ? savedTab : "Songs";
        sortIndex = settings.getInt("ui_sort_index", 0);
        gridMode = settings.getBoolean("ui_grid", false);
        restorePicker(playlistPicker, "ui_playlist", "Playlist: —");
        restorePicker(genrePicker, "ui_genre", "Genre: —");
        restorePicker(folderPicker, "ui_folder", "Folder: —");
        String query = settings.getString("ui_query", "");
        if (query != null && !query.isEmpty()) setQuery(query);
        updateTabs();
        rebuild();
    }

    private void restorePicker(TextView picker, String key, String fallback) {
        String value = settings.getString(key, "");
        picker.setText(value == null || value.isEmpty() ? fallback : value);
    }

    private void saveUiState() {
        if (settings == null) return;
        settings.edit()
                .putString("ui_tab", tab)
                .putInt("ui_sort_index", sortIndex)
                .putBoolean("ui_grid", gridMode)
                .putString("ui_query", searchBox == null ? "" : searchBox.getText().toString())
                .putString("ui_playlist", playlistPicker == null ? "" : playlistPicker.getText().toString())
                .putString("ui_genre", genrePicker == null ? "" : genrePicker.getText().toString())
                .putString("ui_folder", folderPicker == null ? "" : folderPicker.getText().toString())
                .apply();
    }

    private void buildPlayerOverlay(FrameLayout root) {
        playerOverlay = new FrameLayout(this);
        playerOverlay.setBackgroundColor(Theme.overlay());
        playerOverlay.setVisibility(View.GONE);
        playerOverlay.setAlpha(0f);

        blurredArt = new ImageView(this);
        blurredArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Build.VERSION.SDK_INT >= 31)
            blurredArt.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP));
        blurredArt.setAlpha(Theme.blurAlpha());
        playerOverlay.addView(blurredArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Bottom sheet with rounded top, handle and scrollable content.
        LinearLayout sheet = row(LinearLayout.VERTICAL);
        sheet.setBackground(Glass.sheet(dp(28)));
        sheet.setElevation(dp(24));
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sheetParams.topMargin = dp(44);
        sheet.setLayoutParams(sheetParams);

        LinearLayout handleRow = row(LinearLayout.HORIZONTAL);
        handleRow.setGravity(Gravity.CENTER_HORIZONTAL);
        handleRow.setPadding(0, dp(10), 0, dp(6));
        View handle = new View(this);
        handle.setBackground(Glass.handle());
        handle.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(4)));
        handleRow.addView(handle);
        sheet.addView(handleRow);

        LinearLayout head = row(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(22), dp(2), dp(16), dp(6));
        head.addView(section("NOW PLAYING"));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(spacer);
        head.addView(iconButton("✕", 15, false, v -> closePlayer()));
        sheet.addView(head);

        LinearLayout artWrap = row(LinearLayout.HORIZONTAL);
        artWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        artWrap.setPadding(dp(24), dp(4), dp(24), dp(4));
        fullArt = new ImageView(this);
        fullArt.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(244)));
        fullArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fullArt.setBackground(Glass.card(dp(24)));
        artWrap.addView(fullArt);
        sheet.addView(artWrap);

        visualizer = new VisualizerView(this);
        LinearLayout.LayoutParams vizParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        vizParams.setMargins(0, dp(2), 0, dp(2));
        visualizer.setLayoutParams(vizParams);
        visualizer.setAccent(currentAccent);
        sheet.addView(visualizer);

        fullTitle = label("Nothing selected", 22, Theme.textPrimary());
        fullTitle.setGravity(Gravity.CENTER);
        fullTitle.setTypeface(Theme.display());
        fullTitle.setPadding(dp(24), dp(4), dp(24), 0);
        sheet.addView(fullTitle);
        fullArtist = label("", 14, Theme.sub());
        fullArtist.setGravity(Gravity.CENTER);
        fullArtist.setPadding(dp(24), dp(2), dp(24), dp(8));
        sheet.addView(fullArtist);

        fullSeek = styledSeek();
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.setMargins(dp(22), dp(4), dp(22), 0);
        fullSeek.setLayoutParams(seekParams);
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
        sheet.addView(fullSeek);
        LinearLayout times = row(LinearLayout.HORIZONTAL);
        times.setPadding(dp(22), 0, dp(22), 0);
        fullElapsed = label("0:00", 11, Theme.muted());
        fullTotal = label("0:00", 11, Theme.muted());
        fullElapsed.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fullTotal.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fullTotal.setGravity(Gravity.RIGHT);
        times.addView(fullElapsed);
        times.addView(fullTotal);
        sheet.addView(times);

        LinearLayout transport = row(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(0, dp(10), 0, dp(2));
        shuffleButton = iconButton("⤨", 15, false, v -> {
            if (service != null) { service.setShuffle(!service.shuffle()); refresh(); }
        });
        transport.addView(shuffleButton);
        transport.addView(iconButton("❮❮", 19, false, v -> {
            if (service != null) service.prev();
        }));
        playButton = iconButton("▶", 26, true, v -> {
            if (service != null) service.toggle();
        });
        transport.addView(playButton);
        transport.addView(iconButton("❯❯", 19, false, v -> {
            if (service != null) service.next(true);
        }));
        repeatButton = iconButton("↻", 15, false, v -> {
            if (service != null) { service.cycleRepeat(); refresh(); }
        });
        transport.addView(repeatButton);
        sheet.addView(transport);

        LinearLayout switches = row(LinearLayout.HORIZONTAL);
        switches.setGravity(Gravity.CENTER_HORIZONTAL);
        switches.setPadding(dp(14), dp(8), dp(14), dp(2));
        crossfadeButton = chip("Crossfade", false, v -> {
            if (service != null) { service.setCrossfade(!service.crossfade()); refresh(); }
        });
        switches.addView(crossfadeButton);
        visualizerButton = chip(visualizerOn ? "Visualizer ✓" : "Visualizer", visualizerOn,
                v -> toggleVisualizer());
        switches.addView(visualizerButton);
        sheet.addView(switches);

        LinearLayout tools = row(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);
        tools.setPadding(dp(14), dp(2), dp(14), dp(4));
        tools.addView(chip("Sound", false, v -> showSound()));
        tools.addView(chip("Speed", false, v -> askSpeed()));
        tools.addView(chip("Sleep", false, v -> askSleepTimer()));
        sheet.addView(tools);

        fullLyrics = label("Lyrics appear here when a matching .lrc file is found.", 12, Theme.lyrics());
        fullLyrics.setMaxLines(3);
        fullLyrics.setPadding(dp(22), dp(8), dp(22), dp(22));
        sheet.addView(fullLyrics);

        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        scroller.addView(sheet);
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
                .withEndAction(() -> {
                    playerOverlay.setVisibility(View.GONE);
                    if (visualizer != null) visualizer.setPlaying(false);
                }).start();
    }

    private void animateAccent(int accent) {
        if (accentAnimator != null) accentAnimator.cancel();
        int from = currentAccent;
        currentAccent = accent;
        accentAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), from, accent);
        accentAnimator.setDuration(900);
        accentAnimator.addUpdateListener(a -> applyAccent((int) a.getAnimatedValue()));
        accentAnimator.start();
    }

    private void applyAccent(int c) {
        ambient.setAccent(c);
        miniProgress.setBackgroundColor(c);
        gridToggle.setBackground(Glass.circle(false, c));
        miniPlay.setBackground(Glass.circle(true, c));
        playButton.setBackground(Glass.circle(true, c));

        boolean shuffleOn = service != null && service.shuffle();
        shuffleButton.setBackground(Glass.circle(shuffleOn, c));
        shuffleButton.setTextColor(shuffleOn ? Theme.onAccent() : Theme.textPrimary());
        boolean repeatOn = service != null && service.repeat() != 0;
        repeatButton.setBackground(Glass.circle(repeatOn, c));
        repeatButton.setTextColor(repeatOn ? Theme.onAccent() : Theme.textPrimary());

        boolean crossOn = service != null && service.crossfade();
        crossfadeButton.setBackground(Glass.chip(crossOn, c));
        crossfadeButton.setTextColor(Theme.chipText(crossOn));
        visualizerButton.setBackground(Glass.chip(visualizerOn, c));
        visualizerButton.setTextColor(Theme.chipText(visualizerOn));
        if (visualizer != null) visualizer.setAccent(c);

        sortButton.setBackground(Glass.chip(false, c));
        playlistPicker.setBackground(Glass.chip(false, c));
        genrePicker.setBackground(Glass.chip(false, c));
        styleSeek(fullSeek);
        updateTabs();
    }

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

    private void scheduleSearch() {
        searchHandler.removeCallbacks(applySearch);
        searchHandler.postDelayed(applySearch, 120);
    }

    private void setQuery(String value) {
        if (searchBox == null) return;
        searchHandler.removeCallbacks(applySearch);
        searchBox.setText(value);
        searchHandler.removeCallbacks(applySearch);
    }

    private void buildGroupCounts() {
        albumTrackCounts.clear();
        artistTrackCounts.clear();
        artistAlbumCounts.clear();
        Map<String, Set<String>> albumsByArtist = new HashMap<>();
        for (Song s : allSongs) {
            String album = Library.albumKey(s);
            String artist = Library.artistKey(s);
            Integer tracks = albumTrackCounts.get(album);
            albumTrackCounts.put(album, tracks == null ? 1 : tracks + 1);
            Integer artistTracks = artistTrackCounts.get(artist);
            artistTrackCounts.put(artist, artistTracks == null ? 1 : artistTracks + 1);
            Set<String> albums = albumsByArtist.get(artist);
            if (albums == null) { albums = new HashSet<>(); albumsByArtist.put(artist, albums); }
            albums.add(album);
        }
        for (Map.Entry<String, Set<String>> e : albumsByArtist.entrySet())
            artistAlbumCounts.put(e.getKey(), e.getValue().size());
    }

    private void loadLibrary() {
        allSongs.clear();
        allSongs.addAll(Library.exclude(Library.load(this), excludedFolders()));
        cachedGenres = Library.genres(this);
        buildGroupCounts();
        if (allSongs.isEmpty())
            toast("No music found. Add audio files or check excluded folders.");
        animateItems = true;
        rebuild();
    }

    private String currentPlaylist() { return pickedName(playlists.names(), playlistPicker, "Playlist: "); }
    private String currentGenre() { return pickedName(cachedGenres, genrePicker, "Genre: "); }
    private String currentFolder() { return pickedName(Library.folders(allSongs), folderPicker, "Folder: "); }

    private String pickedName(List<String> names, TextView picker, String prefix) {
        if (names.isEmpty()) return null;
        String label = picker == null ? "" : picker.getText().toString();
        String selected = label.startsWith(prefix) ? label.substring(prefix.length()) : names.get(0);
        return names.contains(selected) ? selected : names.get(0);
    }

    private void cycleName(List<String> names, TextView picker, String prefix) {
        if (names.isEmpty()) {
            if ("Playlist: ".equals(prefix)) toast("No playlists yet");
            else if ("Genre: ".equals(prefix)) toast("No genres found");
            else toast("No folders found");
            return;
        }
        String current = pickedName(names, picker, prefix);
        String next = names.get((names.indexOf(current) + 1) % names.size());
        picker.setText(prefix + next);
        animatePop(picker);
        rebuild();
    }

    private void rebuild() {
        viewSongs.clear();
        animatedRows.clear();
        updateSortLabel();
        String q = searchBox == null ? "" : searchBox.getText().toString();

        if (playlistPicker != null)
            playlistPicker.setVisibility("Playlists".equals(tab) ? View.VISIBLE : View.GONE);
        if (genrePicker != null)
            genrePicker.setVisibility("Genres".equals(tab) ? View.VISIBLE : View.GONE);
        if (folderPicker != null)
            folderPicker.setVisibility("Folders".equals(tab) ? View.VISIBLE : View.GONE);
        if ("Playlists".equals(tab)) {
            String name = currentPlaylist();
            if (name != null) playlistPicker.setText("Playlist: " + name);
        }
        if ("Genres".equals(tab)) {
            String name = currentGenre();
            if (name != null) genrePicker.setText("Genre: " + name);
        }
        if ("Folders".equals(tab)) {
            String name = currentFolder();
            if (name != null) folderPicker.setText("Folder: " + name);
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
            List<Song> inGenre = genre == null ? new ArrayList<>()
                    : Library.inGenre(this, allSongs, genre);
            viewSongs.addAll(Library.filter(inGenre, q));
        } else if ("Folders".equals(tab)) {
            String folder = currentFolder();
            for (Song s : Library.filter(allSongs, q))
                if (folder == null || folder.equals(s.folder)) viewSongs.add(s);
        } else if ("History".equals(tab)) {
            historyEntries.clear();
            historyByPath.clear();
            historyEntries.addAll(history.all());
            for (History.Entry entry : historyEntries) {
                historyByPath.put(entry.song.path, entry);
                viewSongs.add(entry.song);
            }
            historyVersion = history.version();
        } else {
            List<Song> base = Library.filter(allSongs, q);
            if ("Albums".equals(tab)) base = Library.groupByAlbum(base);
            else if ("Artists".equals(tab)) base = Library.groupByArtist(base);
            viewSongs.addAll(base);
        }

        if (!"Queue".equals(tab) && !"Lyrics".equals(tab) && !"History".equals(tab)) {
            Library.Sort sort = sortModes()[sortPosition()];
            if (sort != null) Library.sort(viewSongs, sort, true);
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
        saveUiState();
    }

    private void onItemClick(int pos) {
        if (pos < 0 || pos >= viewSongs.size()) return;
        if (service == null) { toast("Player starting…"); return; }
        Song picked = viewSongs.get(pos);

        if ("Queue".equals(tab)) { service.playSongAt(pos); return; }
        if ("History".equals(tab)) {
            List<Song> songs = new ArrayList<>();
            for (History.Entry entry : historyEntries) songs.add(entry.song);
            int at = -1;
            for (int i = 0; i < songs.size(); i++)
                if (songs.get(i).path != null && songs.get(i).path.equals(picked.path)) { at = i; break; }
            service.playQueue(songs, Math.max(0, at));
            return;
        }
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

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private String folderLeaf(String folder) {
        String f = folder == null ? "" : folder;
        int slash = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < f.length() ? f.substring(slash + 1) : f;
    }

    private String rowTitle(Song s) {
        if (s == null) return "";
        if ("Albums".equals(tab)) return nonEmpty(s.album, "<unknown album>");
        if ("Artists".equals(tab)) return nonEmpty(s.artist, "<unknown artist>");
        if ("Folders".equals(tab)) return nonEmpty(folderLeaf(s.folder), "<root>");
        return nonEmpty(s.title, s.fileName);
    }

    private String rowSubtitle(Song s) {
        if (s == null) return "";
        String artist = nonEmpty(s.artist, "<unknown>");
        if ("Albums".equals(tab)) {
            String key = Library.albumKey(s);
            Integer tracks = albumTrackCounts.get(key);
            if (tracks == null) tracks = Library.albumTracks(allSongs, s);
            return artist + "  ·  " + count(tracks, "track");
        }
        if ("Artists".equals(tab)) {
            String key = Library.artistKey(s);
            Integer albums = artistAlbumCounts.get(key);
            Integer tracks = artistTrackCounts.get(key);
            if (albums == null) albums = Library.artistAlbums(allSongs, s);
            if (tracks == null) tracks = Library.artistTracks(allSongs, s);
            return count(albums, "album") + "  ·  " + count(tracks, "track");
        }
        if ("History".equals(tab)) {
            History.Entry entry = historyByPath.get(s.path);
            int plays = entry == null ? 1 : entry.count;
            long last = entry == null ? 0 : entry.lastPlayedMs;
            String when = last == 0 ? ""
                    : DateUtils.getRelativeTimeSpanString(last).toString();
            return count(plays, "play") + (when.isEmpty() ? "" : "  ·  " + when);
        }
        return artist + "  ·  " + Library.duration(s.durationMs);
    }

    private boolean rowActive(Song s) {
        Song current = service == null ? null : service.current();
        if (s == null || current == null) return false;
        if (s.id == current.id) return true;
        if ("Albums".equals(tab)) return Library.sameAlbum(s, current);
        if ("Artists".equals(tab)) return Library.sameArtist(s, current);
        if ("Folders".equals(tab)) return s.folder != null && s.folder.equals(current.folder);
        return false;
    }

    private static class RowViews {
        ImageView art;
        TextView title;
        TextView sub;
        TextView chevron;
        View rule;
        boolean active;
        int accent;
    }

    private class SongListAdapter extends BaseAdapter {
        @Override public int getCount() { return viewSongs.size(); }
        @Override public Song getItem(int pos) { return viewSongs.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout v = (LinearLayout) convertView;
            RowViews h;
            if (v == null) {
                v = row(LinearLayout.HORIZONTAL);
                v.setPadding(dp(8), dp(7), dp(10), dp(7));
                h = new RowViews();
                h.art = new ImageView(MainActivity.this);
                h.art.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(54)));
                h.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.addView(h.art);
                LinearLayout texts = row(LinearLayout.VERTICAL);
                texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                texts.setPadding(dp(12), dp(2), dp(4), dp(2));
                h.title = label("", 15, Theme.textPrimary());
                h.title.setMaxLines(1);
                h.title.setTypeface(Theme.display());
                h.sub = label("", 12, Theme.sub());
                h.sub.setMaxLines(1);
                texts.addView(h.title);
                texts.addView(h.sub);
                h.rule = new View(MainActivity.this);
                h.rule.setBackgroundColor(Theme.rule());
                LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                ruleParams.topMargin = dp(5);
                h.rule.setLayoutParams(ruleParams);
                texts.addView(h.rule);
                v.addView(texts);
                h.chevron = label("›", 18, Theme.muted());
                h.chevron.setGravity(Gravity.CENTER);
                v.addView(h.chevron);
                v.setTag(h);
            } else {
                h = (RowViews) v.getTag();
            }
            Song s = getItem(pos);
            boolean active = rowActive(s);
            if (h.active != active || h.accent != currentAccent) {
                h.active = active;
                h.accent = currentAccent;
                v.setBackground(active ? Glass.activeCard(currentAccent, 16) : Glass.card(16));
            }
            if (h.rule != null)
                h.rule.setVisibility(Theme.mode() == Theme.PORCELAIN
                    || Theme.mode() == Theme.GLASS ? View.VISIBLE : View.GONE);
            setArtwork(h.art, s, dp(12));
            h.title.setText(rowTitle(s));
            h.title.setTextColor(active ? Theme.activeText() : Theme.textPrimary());
            h.chevron.setTextColor(active ? Theme.activeText() : Theme.muted());
            h.sub.setText(rowSubtitle(s));
            if (animateItems && pos < 12 && animatedRows.add(pos)) {
                v.setAlpha(0f);
                v.setTranslationX(dp(24));
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
            LinearLayout v = (LinearLayout) convertView;
            RowViews h;
            if (v == null) {
                v = row(LinearLayout.VERTICAL);
                v.setPadding(dp(7), dp(7), dp(7), dp(10));
                h = new RowViews();
                h.art = new ImageView(MainActivity.this);
                h.art.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
                h.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.addView(h.art);
                h.title = label("", 13, Theme.textPrimary());
                h.title.setGravity(Gravity.CENTER);
                h.title.setMaxLines(1);
                h.title.setPadding(dp(4), dp(8), dp(4), 0);
                h.title.setTypeface(Theme.display());
                v.addView(h.title);
                v.setTag(h);
            } else {
                h = (RowViews) v.getTag();
            }
            Song s = getItem(pos);
            boolean active = rowActive(s);
            if (h.active != active || h.accent != currentAccent) {
                h.active = active;
                h.accent = currentAccent;
                v.setBackground(active ? Glass.activeCard(currentAccent, 20) : Glass.card(20));
            }
            setArtwork(h.art, s, dp(12));
            h.title.setText(rowTitle(s));
            h.title.setTextColor(active ? Theme.activeText() : Theme.textPrimary());
            return v;
        }
    }

    private Drawable placeholderCard(int radius) {
        if (placeholderArt == null || placeholderTheme != Theme.mode()) {
            placeholderArt = Glass.card(radius);
            placeholderTheme = Theme.mode();
        }
        return placeholderArt;
    }

    /** Binds artwork without doing MediaMetadataRetriever work on the main thread. */
    private void setArtwork(ImageView view, Song song, float radius) {
        final String key = song == null || song.path == null ? "" : song.path;
        view.setTag(key);
        Drawable cached = Art.peekRounded(key, radius);
        if (cached != null) {
            view.setImageDrawable(cached);
            return;
        }
        view.setImageDrawable(placeholderCard(dp(12)));
        if (key.isEmpty()) return;

        List<ImageView> waiting = pendingArtViews.get(key);
        if (waiting != null) {
            waiting.add(view);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(view);
        pendingArtViews.put(key, waiting);
        artLoader.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = Art.get(MainActivity.this, song); } catch (Exception ignored) {}
            final Drawable result = bitmap == null ? null : Art.roundedCached(bitmap, radius, key);
            runOnUiThread(() -> {
                List<ImageView> views = pendingArtViews.remove(key);
                if (views == null) return;
                for (ImageView iv : views) {
                    if (key.equals(iv.getTag()))
                        iv.setImageDrawable(result == null ? placeholderCard(dp(12)) : result);
                }
            });
        });
    }

    /** History rows may not exist in the current library; prefer the library Song for IDs. */
    private Song resolveSong(Song song) {
        if (song == null) return null;
        for (Song candidate : allSongs)
            if (candidate.path != null && candidate.path.equals(song.path)) return candidate;
        return song;
    }

    // ---------------------------------------------------------- Menus

    private void showItemMenu(View anchor, int pos) {
        if (pos < 0 || pos >= viewSongs.size()) return;
        final Song song = resolveSong(viewSongs.get(pos));
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
        if ("History".equals(tab))
            m.add("Remove from history").setOnMenuItemClickListener(item -> {
                history.remove(song.path);
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
        m.add("Sound");
        m.add("Volume");
        m.add("Crossfade: " + (service != null && service.crossfade() ? "on" : "off"));
        m.add("Visualizer: " + (visualizerOn ? "on" : "off"));
        m.add("Theme: " + Theme.name());
        m.add("Fade length");
        m.add("ReplayGain: " + (service != null && service.replayGain() ? "on" : "off"));
        m.add("ReplayGain mode: "
                + (service != null && service.replayGainAlbumMode() ? "album" : "track"));
        m.add("Excluded folders");
        m.add("Backup to file");
        m.add("Restore from file");
        m.add("Rescan library");
        m.add("Clear history");
        m.add("About");
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.startsWith("Crossfade")) {
                if (service != null) { service.setCrossfade(!service.crossfade()); toast(t); }
            } else if (t.startsWith("Visualizer")) {
                toggleVisualizer();
            } else if (t.startsWith("Theme")) {
                switchTheme();
            } else if (t.startsWith("ReplayGain mode")) {
                if (service != null) {
                    boolean album = !service.replayGainAlbumMode();
                    service.setReplayGainAlbumMode(album);
                    toast(album ? "ReplayGain: album mode" : "ReplayGain: track mode");
                }
            } else if (t.startsWith("ReplayGain")) {
                if (service != null) { service.setReplayGain(!service.replayGain()); toast(t); }
            } else switch (t) {
                case "New playlist": askNewPlaylist(null); break;
                case "Save queue as playlist": saveQueueAsPlaylist(); break;
                case "Manage playlists": managePlaylists(); break;
                case "Sleep timer": askSleepTimer(); break;
                case "Playback speed": askSpeed(); break;
                case "Sound": showSound(); break;
                case "Volume": askVolume(); break;
                case "Fade length": askFade(); break;
                case "Excluded folders": chooseExcluded(); break;
                case "Backup to file": backupToFile(); break;
                case "Restore from file": restoreFromFile(); break;
                case "Rescan library": loadLibrary(); break;
                case "Clear history":
                    history.clear();
                    historyVersion = history.version();
                    rebuild();
                    toast("Play history cleared");
                    break;
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
        input.setTextColor(Theme.textPrimary());
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
        input.setTextColor(Theme.textPrimary());
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

    private TextView soundSection(String text) {
        TextView t = section(text);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private SeekBar soundSlider(int max, int progress) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        ColorStateList accent = ColorStateList.valueOf(currentAccent);
        sb.setProgressTintList(accent);
        sb.setThumbTintList(accent);
        sb.setProgressBackgroundTintList(
                ColorStateList.valueOf(Theme.chipStroke(false, currentAccent)));
        return sb;
    }

    private void showSound() {
        if (service == null) { toast("Sound settings unavailable"); return; }
        final android.media.audiofx.Equalizer eq = service.equalizer();
        boolean bassOk = service.bassBoostAvailable();
        boolean loudOk = service.loudnessAvailable();
        if (eq == null && !bassOk && !loudOk) { toast("Audio effects unavailable on this device"); return; }

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalFadingEdgeEnabled(true);
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(scroll);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout outer = row(LinearLayout.VERTICAL);
        outer.setBackground(Glass.card(26));
        outer.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout box = row(LinearLayout.VERTICAL);
        box.addView(section("SOUND"));

        if (eq != null) {
            LinearLayout enableRow = row(LinearLayout.HORIZONTAL);
            enableRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView enableLabel = label("Enable equalizer", 14, Theme.textPrimary());
            enableLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            CheckBox enable = new CheckBox(this);
            enable.setButtonTintList(ColorStateList.valueOf(currentAccent));
            enable.setChecked(eq.getEnabled());
            enable.setOnCheckedChangeListener((b, on) -> service.setEqualizerEnabled(on));
            enableRow.addView(enableLabel);
            enableRow.addView(enable);
            box.addView(enableRow);

            box.addView(soundSection("PRESETS"));
            final List<TextView> presetChips = new ArrayList<>();
            HorizontalScrollView presetsScroll = new HorizontalScrollView(this);
            presetsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout presets = row(LinearLayout.HORIZONTAL);
            short activePreset = eq.getCurrentPreset();
            for (short i = 0; i < eq.getNumberOfPresets(); i++) {
                final short preset = i;
                TextView c = chip(eq.getPresetName(i), i == activePreset, v -> {
                    service.setPreset(preset);
                    for (TextView other : presetChips) setChipSelected(other, other == v);
                });
                presetChips.add(c);
                presets.addView(c);
            }
            presetsScroll.addView(presets);
            box.addView(presetsScroll);

            box.addView(soundSection("BANDS"));
            short bands = eq.getNumberOfBands();
            final short min = eq.getBandLevelRange()[0], max = eq.getBandLevelRange()[1];
            for (short i = 0; i < bands; i++) {
                final short band = i;
                LinearLayout meta = row(LinearLayout.HORIZONTAL);
                TextView freq = label(eq.getBandFreqRange(i)[0] / 1000 + "–"
                        + eq.getBandFreqRange(i)[1] / 1000 + " Hz", 12, Theme.sub());
                freq.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView value = label(String.format(java.util.Locale.US, "%.1f dB",
                        eq.getBandLevel(i) / 100f), 11, Theme.section());
                meta.addView(freq);
                meta.addView(value);
                box.addView(meta);
                SeekBar sb = soundSlider(max - min, eq.getBandLevel(i) - min);
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                        if (!fromUser) return;
                        service.setBand(band, (short) (p + min));
                        value.setText(String.format(java.util.Locale.US, "%.1f dB", (p + min) / 100f));
                    }
                    public void onStartTrackingTouch(SeekBar s) {}
                    public void onStopTrackingTouch(SeekBar s) {}
                });
                box.addView(sb);
            }
        }

        if (bassOk) {
            box.addView(soundSection("BASS BOOST"));
            LinearLayout meta = row(LinearLayout.HORIZONTAL);
            TextView name = label("Low-end presence", 12, Theme.textPrimary());
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = label(String.valueOf(service.bassStrength()), 11, Theme.section());
            meta.addView(name);
            meta.addView(value);
            box.addView(meta);
            SeekBar sb = soundSlider(1000, service.bassStrength());
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (!fromUser) return;
                    service.setBassStrength(p);
                    value.setText(String.valueOf(p));
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(sb);
        }

        if (loudOk) {
            box.addView(soundSection("LOUDNESS"));
            LinearLayout meta = row(LinearLayout.HORIZONTAL);
            TextView name = label("Perceived loudness", 12, Theme.textPrimary());
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = label(String.format(java.util.Locale.US, "%.2f dB",
                    service.loudnessBoost() / 100f), 11, Theme.section());
            meta.addView(name);
            meta.addView(value);
            box.addView(meta);
            SeekBar sb = soundSlider(1200, service.loudnessBoost());
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (!fromUser) return;
                    service.setLoudnessBoost(p);
                    value.setText(String.format(java.util.Locale.US, "%.2f dB", p / 100f));
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(sb);
        }

        TextView note = label("ReplayGain boost is applied automatically when a positive, "
                + "peak-safe tag is present.", 11, Theme.sub());
        note.setBackground(Glass.card(14));
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(12), 0, 0);
        note.setLayoutParams(noteParams);
        box.addView(note);

        TextView done = chip("Done", true, v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneParams.setMargins(0, dp(14), 0, 0);
        done.setLayoutParams(doneParams);

        outer.addView(box);
        outer.addView(done);
        scroll.addView(outer);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.55f);
        }
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
                        + "High-end translucent UI with Obsidian, Ivory, Aurora, Porcelain and Glass themes, "
                        + "optional spectrum visualizer, animated ambient backgrounds, "
                        + "album art, crossfade, ReplayGain, EQ, bass, loudness, playlists, "
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
        saveUiState();
        if (service != null) service.saveStateNow();
        unregisterReceiver(updateReceiver);
        super.onStop();
    }

    @Override protected void onDestroy() {
        saveUiState();
        if (service != null) service.saveStateNow();
        artLoader.shutdownNow();
        try { unbindService(connection); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // Rotation is handled without recreating the Activity, so the open player,
    // scroll positions and Service binding all survive a tilt.
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ambient != null) ambient.invalidate();
        refresh();
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
        if ("History".equals(tab) && history.version() != historyVersion) {
            historyVersion = history.version();
            rebuild();
        }
        Song s = service.current();
        boolean playing = service.isPlaying();

        String title = s == null ? "Nothing selected" : s.title == null ? s.fileName : s.title;
        String artist = s == null ? "Tap a song to play" :
                s.artist == null || s.artist.trim().isEmpty() ? "<unknown artist>" : s.artist;
        miniTitle.setText(title);
        miniSub.setText(artist);
        fullTitle.setText(title);
        fullArtist.setText(artist);
        playButton.setText(playing ? "❚❚" : "▶");
        miniPlay.setText(playing ? "❚❚" : "▶");

        boolean shuffleOn = service.shuffle();
        shuffleButton.setBackground(Glass.circle(shuffleOn, currentAccent));
        shuffleButton.setTextColor(shuffleOn ? Theme.onAccent() : Theme.textPrimary());
        boolean repeatOn = service.repeat() != 0;
        repeatButton.setBackground(Glass.circle(repeatOn, currentAccent));
        repeatButton.setTextColor(repeatOn ? Theme.onAccent() : Theme.textPrimary());
        boolean crossOn = service.crossfade();
        crossfadeButton.setText(crossOn ? "Crossfade ●" : "Crossfade");
        crossfadeButton.setBackground(Glass.chip(crossOn, currentAccent));
        crossfadeButton.setTextColor(Theme.chipText(crossOn));

        applyVisualizerState();
        if (visualizer != null) visualizer.setPlaying(visualizerOn && playing
                && playerOverlay.getVisibility() == View.VISIBLE);

        String key = s == null || s.path == null ? "" : s.path;
        if (!key.equals(artPathKey)) {
            artPathKey = key;
            Bitmap art = Art.get(this, s);
            if (art != null) {
                miniArt.setImageDrawable(Art.roundedCached(art, dp(14), key));
                fullArt.setImageDrawable(Art.roundedCached(art, dp(24), key));
                blurredArt.setImageBitmap(art);
                backdropArt.setImageBitmap(art);
            } else {
                miniArt.setImageDrawable(Glass.card(dp(14)));
                fullArt.setImageDrawable(Glass.card(dp(24)));
                blurredArt.setImageDrawable(null);
                backdropArt.setImageDrawable(null);
            }
        }

        int dur = service.duration(), pos = service.position();
        fullSeek.setMax(Math.max(1, dur));
        if (!userSeeking) fullSeek.setProgress(pos);
        fullElapsed.setText(Library.duration(pos));
        fullTotal.setText(Library.duration(dur));
        miniProgress.setScaleX(dur > 0 ? pos / (float) dur : 0f);

        int desiredAccent = s == null ? Theme.baseAccent() : Theme.accent(service.accentColor());
        long activeId = s == null ? -1L : s.id;
        if (activeId != highlightedId || desiredAccent != highlightedAccent) {
            highlightedId = activeId;
            highlightedAccent = desiredAccent;
            // Visible rows are only rebound on notifyDataSetChanged; otherwise the
            // active track would not tint until its view was recycled by scrolling.
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        }
        if (desiredAccent != currentAccent) animateAccent(desiredAccent);
        if ("Queue".equals(tab) && service.queue().size() != viewSongs.size()) rebuild();
    }
}
