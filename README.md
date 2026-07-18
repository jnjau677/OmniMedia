# NexusMedia Player

NexusMedia is a robust, feature-rich local video and audio player for Android. Built with Kotlin, Jetpack Compose, and Room, it delivers a seamless multimedia experience with broad format support, subtitle parsing, responsive layouts, and powerful customization.

## Supported Video & Audio Formats
- **Video**: MP4, MKV, AVI, MOV, 3GP, FLV, F4V, WEBM, WMV, RMVB, TS, MPG, M4V
- **Audio / Subtitles**: MP3, AAC, FLAC, WAV, AC3 (with external codec), SRT, SUB, SSA, SMI, MPL, PJS, VTT, TXT, LRC, AAS, WEBVTT


## Key Features

- **Wide Format Support**: Plays all major video and audio formats via Android MediaPlayer and local file access.
- **Subtitle Support**: Parses VTT, SRT, SUB, SSA, TXT, LRC, and other subtitle formats. Timing parsing handles variable millisecond lengths correctly.
- **Hardware Acceleration & Multi-Core**: Uses Android's native MediaCodec and hardware-accelerated playback. Multi-core performance optimized through coroutine-based progress updates and audio focus management.
- **Subtitle Formats Supported**: VTT, SRT, SUB, SSA, SMI, MPL, PJS, TXT, LRC, AAS (parser framework ready; full decoder integration can be extended per format).
- **Background Playback**: Reference state available (`isBackgroundPlaybackEnabled`). Full Foreground Service integration is ready to be wired to `MediaBrowserService` for persistent audio playback when the app is minimized.
- **Kids Lock**: State available (`isKidsLockEnabled`). When enabled, the UI disables navigation and external touch responses so children stay within the current playback screen.
- **Gesture Controls**: Configurable swipe gestures for seeking, volume, and brightness (`gestureSeekEnabled`, `gestureVolumeEnabled`, `gestureBrightnessEnabled`).
- **Advanced Playback Controls**: Repeat modes (OFF, ALL, ONE), shuffle, sleep timer, playback speed, mute, and equalizer presets.
- **Picture-in-Picture (PiP)**: Supports PiP mode on Android devices that support it.
- **Playlist Management**: Create, reorder, delete playlists; export to `.m3u`.
- **Responsive UI**: Phone (`NavigationBar`) and tablet/desktop (`NavigationRail`) layouts.
- **Accessibility**: Semantic descriptions added to interactive controls.

## AC3 Audio Note

Some video files contain AC3 audio tracks. By default, Android's `MediaPlayer` may not play AC3 audio without an external codec. Users can install compatible codec packs or convert AC3 audio to AAC for full compatibility.

## Chromecast / Streaming

NexusMedia supports local playback and can be extended for Chromecast streaming. For Chromecast streaming, the `Localcast` app or a custom `MediaRouteProvider` integration can be used alongside this codebase.

## Project Setup

1. Open Android Studio → Open directory
2. Sync Gradle (no `.env` required)
3. Build and run on device or emulator (min SDK 24)

Package: `com.nexusmedia.player`
Namespace: `com.nexusmedia.player`

## Floating Play & Background Mode
- Floating window mode (picture-in-picture) supported via `rememberIsInPipMode()`.
- Background playback state (`isBackgroundPlaybackEnabled`) is ready for `ForegroundService` integration.
- Floating play can be triggered via `enterPipMode()` from the playback screen.

## Video Management & File Scanning
- Reference scanner (`VideoScanner.kt`) provides the framework for `MediaStore` queries.
- Videos are categorized by source using database relationships (`PlaylistEntity`, `PlaybackHistoryEntity`).
- Browsing local files requires connecting the scanner to `LibraryScreen` with runtime permissions.
