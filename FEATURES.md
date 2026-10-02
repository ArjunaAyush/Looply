# Looply v1.0.0 — Comprehensive Features & Architecture Catalog

> **Version**: 1.0.0 (versionCode 1)  
> **Target SDK**: Android 16 (API Level 37) | **Min SDK**: Android 8.0 Oreo (API Level 26)  
> **Package**: `com.arjunaayush.looply`  
> **Philosophy**: Offline-First, Calm, Watch-and-Delete Instagram Reel Player & Creator Organizer  
> **Primary Accent**: Neon Rose / Pink (`#FF3366`) & Pure Dark Backdrop (`#121214`)

---

## Table of Contents
1. [Executive Summary & Vision](#1-executive-summary--vision)
2. [Pillar 1: Video Ingestion & Background Download Engine](#2-pillar-1-video-ingestion--background-download-engine)
3. [Pillar 2: Media3 Full-Screen Reel Player & Infinite Looping](#3-pillar-2-media3-full-screen-reel-player--infinite-looping)
4. [Pillar 3: Creator-Centric Organization & Watch-and-Delete Flow](#4-pillar-3-creator-centric-organization--watch-and-delete-flow)
5. [Pillar 4: Design System, Neon-Pink Theme & Glassmorphism](#5-pillar-4-design-system-neon-pink-theme--glassmorphism)
6. [Pillar 5: LinkerlyIcons Vector Library & Visual Hierarchy](#6-pillar-5-linkerlyicons-vector-library--visual-hierarchy)
7. [Pillar 6: Tactile Haptics System (Zero Audio Distraction)](#7-pillar-6-tactile-haptics-system-zero-audio-distraction)
8. [Pillar 7: Storage Architecture & Media Export](#8-pillar-7-storage-architecture--media-export)
9. [Pillar 8: Architecture & Modern Android Tech Stack](#9-pillar-8-architecture--modern-android-tech-stack)
10. [Deliberate Anti-Features & Product Non-Goals](#10-deliberate-anti-features--product-non-goals)

---

## 1. Executive Summary & Vision

Looply is a distraction-free, offline-first Instagram Reel player and media organizer built with modern Jetpack Compose. Looply is designed for users who want to save, loop, and enjoy reels without doomscrolling algorithms, social pressures, intrusive advertisements, or spotty network buffering.

### Core Tenets
- **Local-First & Offline Playback**: Once downloaded, reels play instantaneously with zero buffering, frame drops, or tracking.
- **Infinite Looping by Default**: True to its name, Looply loops every video seamlessly, with clear visible toggles for auto-advancing.
- **Watch-and-Delete Philosophy**: Designed for clean disk management—watch saved clips in ultra-high quality, organize by creator handle, and purge with one tap when finished.
- **Calm, Neon-Pink Aesthetic**: Dark-only glassmorphic surfaces accented by hot pink glow (`#FF3366`) and deep rose (`#E02856`).
- **Zero Distracting Sounds**: Looply respects audio playback; all tactile user feedback is delivered via sophisticated device haptics without sound effect pollution.

---

## 2. Pillar 1: Video Ingestion & Background Download Engine

### 1-Tap Interception via System Share Sheet
- **Transparent `ShareReceiverActivity`**: When an Instagram reel is shared to Looply, a lightweight transparent activity handles the incoming URL, verifies format validity, schedules the download, displays a floating confirmation toast/HUD, and finishes in under 200ms without displacing the user's workflow.
- **Quick-Paste Input**: An in-app paste button on the home screen allows users to quickly paste copied Instagram URLs directly with immediate metadata resolution.

### Extraction & Download Pipeline
- **Scraper Engine**: Employs Jsoup to parse reel metadata—including author username, avatar/thumbnail URL, caption text, duration, and direct video media stream sources.
- **Reliable Background Execution**: Managed through Android Jetpack **WorkManager** via `OneTimeWorkRequest` linked with foreground notification updates, progress tracking, and automatic retry upon network interruption.
- **Graceful Error Handling**: Detects private accounts, deleted reels, or rate-limited responses and conveys clear actionable messages to the user.

---

## 3. Pillar 2: Media3 Full-Screen Reel Player & Infinite Looping

### TikTok / Reels Style Swipeable Pager
- **`VerticalPager` Interaction**: Vertical gesture-driven feed with snap-to-page physics and auto-play initiation for the currently focused reel.
- **Media3 ExoPlayer & Preloading**: Utilizes ExoPlayer with `SimpleCache` to preload adjacent reels (previous and next), guaranteeing instantaneous transitions without blank loading spinners.
- **Seamless Looping Engine**: Videos default to `Player.REPEAT_MODE_ONE`. A quick floating badge allows users to toggle single-play or auto-advance modes.

### Minimalist Auto-Hiding Controls Overlay
- **Clean Touch Interactions**:
  - *Single Tap*: Pause / Resume playback.
  - *Double Tap*: Heart / Favorite reel with pop haptic feedback.
  - *Long Press*: Half-speed scrub / inspection.
  - *Auto-Hiding HUD*: Overlay controls (Loop toggle, Mute/Unmute, Share, Delete, Creator info) fade out after 2.5 seconds of inactivity.
- **Global Mute Memory**: Audio state (muted/unmuted) is remembered across reel transitions, with a quick 1-tap unmute icon.

---

## 4. Pillar 3: Creator-Centric Organization & Watch-and-Delete Flow

### Auto-Categorization by Creator Handle
- In Looply, users do not need to manually create and manage complex folder hierarchies. 
- All downloaded reels are automatically grouped by the Instagram creator's username (e.g., `@amurot`, `@nature`, `@techcreators`).
- Tapping a creator chip instantly filters the feed to all saved loops from that specific creator.

### Ephemeral Storage & Watch-and-Delete Workflow
- Looply prevents device storage bloat by treating downloaded reels as an active watchlist rather than a permanent hoard.
- Quick 1-tap delete icon directly in the player overlay or long-press multi-select delete in the saved videos grid.
- Prominent storage usage indicator in settings displaying total MB/GB occupied with a single-tap "Clear Watched Loops" button.

---

## 5. Pillar 4: Design System, Neon-Pink Theme & Glassmorphism

### Color Palette & Visual Style
- **Primary Brand**: `#FF3366` (Looply Rose / Hot Pink)
- **Primary Variant**: `#E02856` (Deep Crimson Rose)
- **Accent Glow**: `#7C4DFF` (Electric Purple)
- **Dark-Only Surfaces**:
  - `Background`: `#121214` (Deep obsidian)
  - `Surface`: `#1C1C22` (Elevated card background)
  - `SurfaceElevated`: `#25252E` (Frosted action buttons & badges)
  - `PlayerBackdrop`: `#000000` (Pure AMOLED black)
  - `Divider`: `#24242D` (Subtle 1dp border lines)
- **Glassmorphic Overlays**: Frosted translucent backgrounds with blur filters (`Modifier.blur`) for bottom navigation, floating pills, and player overlays.

### Reusable Linkerly Design System Components
Looply adapts Linkerly's robust component library for code parity and visual polish:
- **`LinkerlyCard`**: Elevated squircle cards with 16dp–20dp corner radii, subtle borders, and touch spring animations.
- **`LinkerlyButton` & `LinkerlyTextButton`**: Pill-shaped action buttons with glowing pink accents and debounced clicks.
- **`LinkerlyTextField`**: Polished text inputs with active neon stroke borders and quick-clear actions.
- **`LinkerlyDialog`**: Glassmorphic modal sheets for confirmations, video deletion, and export actions.
- **`LinkerlyChip`**: Creator filter pills and playback mode badges.
- **`LinkerlyFab`**: Floating action button with pink gradient and spring physics.
- **`LinkerlyNavigationBar` & `LinkerlyNavigationBarItem`**: Floating docked navigation pill with smooth indicator slides.

---

## 6. Pillar 5: LinkerlyIcons Vector Library & Visual Hierarchy

- **Strict Zero-Emoji Standard**: All emojis across the app have been replaced with vector stroke icons from the `LinkerlyIcons` library.
- **90+ Custom 24x24dp Vectors**: Contained in `src/main/res-icons/drawable/`, covering navigation (`ic_home_*`, `ic_play_circle_*`), actions (`ic_favorite_*`, `ic_delete_*`, `ic_share_*`), and playback controls (`ic_undo_*`, `ic_redo_*`, `ic_tune_*`).
- **Consistent Stroke Weight**: Standardized 1.5dp–2.0dp outline stroke with crisp filled variants for selected states.

---

## 7. Pillar 6: Tactile Haptics System (Zero Audio Distraction)

- Looply avoids audio sound effects so as not to interrupt or clash with reel background audio.
- Tactile feedback is powered by `HapticsManager`:
  - **Video Downloaded / Complete**: `HapticEffectType.CONFIRM`
  - **Favorite / Double Tap**: `HapticEffectType.FAVORITE_POP`
  - **Delete Reel**: `HapticEffectType.HEAVY_DELETE`
  - **Loop Toggle Changed**: `HapticEffectType.CONFIRM`
  - **Player Scrub / Progress Tick**: `HapticEffectType.SEEK_TICK`
  - **Tab / Pill Selection**: `HapticEffectType.TICK`

---

## 8. Pillar 7: Storage Architecture & Media Export

- **App-Specific External Storage**: Downloaded video files (`.mp4`) and extracted thumbnail snapshots (`.jpg`) are saved to `context.getExternalFilesDir("videos")`. This ensures instant access without requiring runtime storage permissions on Android 10+.
- **User Export Option**: A prominent "Export to Gallery" button allows users to write any saved loop to the public `MediaStore.Video` directory when they want to keep it permanently.
- **Room Database Metadata**:
  - `VideoEntity`: `id`, `reelUrl`, `videoPath`, `thumbnailPath`, `author`, `caption`, `durationMs`, `aspectRatio`, `dateAdded`, `isFavorite`, `isWatched`.
  - Non-destructive Room migrations ensure zero data loss during schema evolution.

---

## 9. Pillar 8: Architecture & Modern Android Tech Stack

```
Compose UI  
↳ ViewModel (StateFlow / SharedFlow events)  
↳ UseCase (DownloadVideoUseCase, GetVideosByCreatorUseCase, DeleteVideoUseCase)  
↳ VideoRepository (Single Source of Truth)  
↳ Room DAO / WorkManager / StorageManager  
↳ Local SQLite / File System  
```

- **Clean Architecture & Unidirectional Data Flow**
- **Dependency Injection**: Hilt (`@HiltViewModel`, `@Inject`, `@Module`)
- **Jetpack Compose**: 100% declarative UI with Material 3.
- **Media3 ExoPlayer**: High-performance video playback with cache preloading.
- **Kotlin Coroutines & Flow**: Reactive data streams from Room to UI.

---

## 10. Deliberate Anti-Features & Product Non-Goals

To maintain Looply's speed, lightness, and calm focus, the following features are intentionally excluded:
- ❌ **No Search Screen**: Reels are consumed immediately or organized simply by creator handle; no complex search indexing overhead.
- ❌ **No In-App Social Network**: No user accounts, comments, followers, or algorithms.
- ❌ **No Soundboard Audio Effects**: Audio feedback is disabled in favor of silent, precise haptics.
- ❌ **No In-App Purchases or Subscriptions**: Pure, distraction-free utility.
- ❌ **No Complex Nested Folders**: Simple creator-based auto-grouping replaces cumbersome folder management.
