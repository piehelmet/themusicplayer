# The music player

An independent, offline Android music player written from scratch. It does not reuse
Musicolet's code, branding, assets, or licensing.

## APK

`TheMusicPlayer.apk` — signed with a local debug key, ready to install.

```
adb install -r TheMusicPlayer.apk
```

Package: `com.themusicplayer.app` · minSdk 21 · targetSdk 34

## Included in v2

### High-end interface (v3 refresh)
- Rebuilt around custom views instead of stock Android buttons: icon-only circular transport controls, navigation chips and a floating glass mini player
- Immersive artwork backdrop: the current cover is blurred behind the library and the full player (Android 12+)
- Animated ambient gradient that drifts and recolours from the cover art
- Bottom-sheet player with rounded top corners, drag handle and slide-up motion
- Large rounded cover art, accent-tinted sliders and active-row highlighting; **no visualizer bars**
- Staggered list entrance animations, springy chip/button feedback and smooth accent crossfades
- Cover art from embedded tags with generated colour-themed placeholders

### Playback engine
- Queue with shuffle, repeat off/all/one, play-next, add-to-queue, remove and reorder (drag or menu)
- **Crossfade** between tracks with adjustable 2–15 s fade length
- **ReplayGain** volume normalization (track/album tags, with fallback)
- Per-song playback speed 0.5x–2x, device equalizer with presets and manual bands
- Sleep timer, app volume with ReplayGain-aware scaling
- Background foreground-service playback, notification controls, MediaSession + hardware keys

### Library & organization
- Songs, Albums (grid or list), Artists (grid or list), Genres, Folders, Playlists, Queue and Lyrics views
- Search across title, artist, album and filename; sorting by title / artist / album / duration / folder
- Playlists: create, rename, delete, add songs, save current queue, export/import via SAF file picker
- Hide folders from the library
- Synchronized `.lrc` / `.txt` lyrics with a full lyric pane
- Tag editor for title / artist / album (where the OS permits MediaStore updates)
- Home-screen widget with title, artist and transport controls
- Open a local audio file by tapping it in another app

No `INTERNET` permission, no ads, no analytics.

## Build

Requires JDK 17 and Android SDK (build-tools 34.0.0 + platform android-35). Paths are
configured at the top of `build.ps1`; adjust them for your machine.

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

The script compiles resources with `aapt2`, Java sources with `javac`, converts to DEX
with `d8`, aligns with `zipalign`, and signs with `apksigner`. Output is `TheMusicPlayer.apk`.

## Source layout

```
AndroidManifest.xml
res/layout/widget.xml
res/drawable/widget_bg.xml
res/xml/widget_info.xml
res/values/styles.xml
src/com/themusicplayer/app/
    MainActivity.java        UI, browsing, menus, dialogs, animations
    MusicService.java        playback, crossfade, ReplayGain, MediaSession, notification, equalizer
    Library.java             MediaStore queries, sorting, filtering, grouping, genres
    Art.java                 embedded cover art, cache, colour extraction, rounded covers
    AmbientBackground.java   animated translucent gradient backdrop
    Glass.java               frosted-glass drawables
    MusicWidget.java         home-screen widget
    ReplayGain.java          lightweight ReplayGain tag reader
    Song.java                song model
    Playlists.java           persistent playlists in SharedPreferences
    Lyrics.java              .lrc parser
build.ps1                    repeatable build
```

## Roadmap

Chromecast / Cast SDK integration (needs internet permission and Google Play services),
true gapless decoding for formats that support it, a release signing key instead of the
debug key, and a Play Store listing.
