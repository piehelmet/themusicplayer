# The music player

An independent, offline Android music player written from scratch. It does not reuse
Musicolet's code, branding, assets, or licensing.

## APK

`TheMusicPlayer.apk` — signed with a local debug key, ready to install.

```
adb install -r TheMusicPlayer.apk
```

Package: `com.themusicplayer.app` · minSdk 21 · targetSdk 34

## Releases

Tagged builds with attached APKs are on the [GitHub Releases page](../../releases):

- **v5.5** (latest) — new iOS-inspired **Glass** theme, top-lit frosted panels and a redesigned Sound panel with preset chips and accent-tinted sliders
- **v5.4** — peak-aware ReplayGain boost, equal-power crossfade, bass and loudness controls
- **v5.3** — play history with play counts, portable build script and one-click ADB installer
- **v5.2** — faster search and scrolling, plus the Porcelain light theme
- **v5.1** — corrected grouped sorting/filtering across Albums, Artists, Genres and Folders
- **v5.0** — Aurora theme, persistent rotation, reliable active-row highlight
- **v4.0** — optional visualizer, Ivory theme, original echo-play icon
- **v3.2** — visualizer bars removed, other motion restored
- **v3.1** — short-lived fully static now-playing experiment
- **v3.0** — complete UI redesign
- **v2.0** — first published feature build

The raw `TheMusicPlayer.apk` on `main` always tracks the latest build. Historical APKs
were rebuilt from their tagged commits and are debug-signed with the same key. The Android
`versionName` remains `1.0`; release tags track UI milestones.

## Included in v2

### High-end interface (v5 refresh)
- Five complete UI moods, switchable from the ⋯ menu: **Obsidian** (midnight glass), **Ivory** (warm editorial cream, champagne accents, serif display type and a fine gilt rule), **Aurora** (deep teal night, mint light, wide-spaced sans display), **Porcelain** (cool Apple-inspired white/gray glass, blue accent, fine separators and crisp sans type) and **Glass** (iOS-inspired dark frosted material with top-lit gradient panels, luminous hairlines, cool blue/indigo gel and iOS blue accents)
- Rebuilt around custom views instead of stock Android buttons: icon-only circular transport controls, navigation chips and a floating glass mini player
- Immersive artwork backdrop: the current cover is blurred behind the library and the full player (Android 12+)
- Animated ambient gradient that drifts and recolours from the cover art
- Bottom-sheet player with rounded top corners, drag handle and slide-up motion
- Large rounded cover art, accent-tinted sliders and active-row highlighting
- **Optional spectrum visualizer**: off by default, toggle from the now-playing screen or the ⋯ menu; remembers your choice
- Active track is clearly tinted in every theme, including Ivory; device tilts no longer reset the open player or playback
- Staggered list entrance animations, springy chip/button feedback and smooth accent crossfades
- Search is debounced, and group counts, artwork, row backgrounds and text keys are cached so filtering and scrolling stay responsive
- Original echo-into-play launcher icon (adaptive on Android 8+, hand-drawn vectors/PNG for older devices)
- Cover art from embedded tags with generated colour-themed placeholders

### Playback engine
- Queue with shuffle, repeat off/all/one, play-next, add-to-queue, remove and reorder (drag or menu)
- **Crossfade** between tracks with adjustable 2–15 s fade length
- **ReplayGain** volume normalization (track/album tags, with fallback), now peak-aware:
  positive gain is applied as a safe boost without clipping, with a track/album mode toggle
- Equal-power crossfade (no mid-fade loudness dip), effects applied to both players during fades
- Per-song playback speed 0.5x–2x, device equalizer with presets and manual bands,
  plus optional bass boost and loudness sliders in the new **Sound** dialog
- Sleep timer, app volume with ReplayGain-aware scaling
- Background foreground-service playback, notification controls, MediaSession + hardware keys

### Library & organization
- Songs, Albums (grid or list), Artists (grid or list), Genres, Folders, Playlists, Queue, Lyrics and Play History views
- Albums and Artists show group names and counts, sorted by the field shown in the Sort chip
- Genre and folder pickers filter their tabs; a genre no longer duplicates its song list
- Playlists can keep their saved order; search covers title, artist, album, folder and filename
- Sorting uses natural per-tab modes (Album for Albums, Artist for Artists, saved Order for Playlists)
- Playlists: create, rename, delete, add songs, save current queue, export/import via SAF file picker
- Hide folders from the library
- Synchronized `.lrc` / `.txt` lyrics with a full lyric pane
- Tag editor for title / artist / album (where the OS permits MediaStore updates)
- Home-screen widget with title, artist and transport controls
- Open a local audio file by tapping it in another app

No `INTERNET` permission, no ads, no analytics.

## Session restore and history

The app now saves the active queue (as paths, so it survives MediaStore ID changes), current
track, playback position, shuffle/repeat, speed, volume, crossfade, ReplayGain, fade length,
sleep timer, theme, tab, sort mode, grid mode, filters and search text. On relaunch the queue
and position are restored without auto-playing; press play to resume. State is written to
`SharedPreferences` on every meaningful change, on `onStop`, and at least every two seconds
while playing, so it survives a process kill as well as a clean exit.

Play history is stored in its own `SharedPreferences` file, one entry per track with a play
count and last-played time. It is available as the **History** tab, newest first. Tapping a
row plays from that point in the history; long-press removes a single entry, and the ⋯ menu
offers **Clear history**.

## Efficiency notes

- State broadcasts are sent every 500 ms instead of every 250 ms; user actions still update immediately.
- Widget updates are skipped entirely when no widget is placed.
- Genre listings are cached after each library scan instead of re-querying MediaStore during filtering.
- Row artwork is decoded on a two-thread executor and delivered to recycled views only if the
  view still represents the same path, avoiding main-thread `MediaMetadataRetriever` stalls while scrolling.

## Build

Requires a JDK (17+), Android SDK build-tools and an Android platform (android-35 preferred).
`build.ps1` auto-detects `ANDROID_HOME`/`ANDROID_SDK_ROOT` (or `%LOCALAPPDATA%\Android\Sdk`),
the newest installed build-tools, and `JAVA_HOME` (or `javac` on PATH). It signs with your
own `~/.android/debug.keystore`, creating one if needed.

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

To install on a connected phone/emulator (USB debugging enabled), double-click `install.cmd`
or run `adb install -r TheMusicPlayer.apk`. No device is required to produce the APK.

The script compiles resources with `aapt2`, Java sources with `javac`, converts to DEX
with `d8`, aligns with `zipalign`, and signs with `apksigner`. Output is `TheMusicPlayer.apk`.

## Source layout

```
AndroidManifest.xml
res/layout/widget.xml
res/drawable/widget_bg.xml
res/drawable/ic_launcher_foreground.xml
res/mipmap/ic_launcher.png, ic_launcher_round.png
res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
res/xml/widget_info.xml
res/values/styles.xml, colors.xml
src/com/themusicplayer/app/
    MainActivity.java        UI, browsing, menus, dialogs, animations
    MusicService.java        playback, crossfade, ReplayGain, MediaSession, notification, equalizer
    Library.java             MediaStore queries, sorting, filtering, grouping, genres
    Art.java                 embedded cover art, cache, colour extraction, rounded covers
    AmbientBackground.java   animated translucent gradient backdrop
    Glass.java               frosted-glass drawables
    Theme.java               Obsidian / Ivory / Aurora / Porcelain / Glass palettes and typography
    VisualizerView.java      optional animated spectrum bars
    MusicWidget.java         home-screen widget
    ReplayGain.java          lightweight ReplayGain tag reader
    Song.java                song model
    Playlists.java           persistent playlists in SharedPreferences
    History.java             persistent play history with counts and last-played times
    Lyrics.java              .lrc parser
brand/make_icon.ps1          regenerates the launcher icon PNGs
build.ps1                    repeatable build
```

## Roadmap

Chromecast / Cast SDK integration (needs internet permission and Google Play services),
true gapless decoding for formats that support it, a release signing key instead of the
debug key, and a Play Store listing.
