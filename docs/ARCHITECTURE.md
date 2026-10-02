# Looply Architecture Specification
**Project:** Looply  
**Platform:** Android (Min SDK: 26, Target/Compile SDK: 37)  
**Language:** Kotlin 2.x  
**UI Framework:** Jetpack Compose (Material 3)  
**Dependency Injection:** Hilt  
**Database:** Room (Offline-First Local Storage)  
**Video Player:** Media3 ExoPlayer (SimpleCache + Preloading)  
**Background Tasks:** WorkManager  

---

## 1. Executive Summary & Design Philosophy

Looply is an offline-first Instagram Reel player and media organizer built for instant playback, distraction-free looping, and clean disk management.

### Core Architectural Values
1. **Local-First Single Source of Truth**: All video metadata and creator groups reside locally in Room SQLite. Downloaded media files (`.mp4`) and thumbnails (`.jpg`) live in app-specific external storage.
2. **Unidirectional Reactive Data Flow (UDF)**: Data streams reactively from Room DAOs through Kotlin Coroutine `Flow`s, transformed by domain `UseCase`s into immutable `UiState` in ViewModels, and rendered statelessly by Jetpack Compose.
3. **Smooth Video Preloading & Playback**: Media3 ExoPlayer with `SimpleCache` preloads adjacent reels in the vertical pager feed for zero-buffer instant swipes.
4. **Resilient Non-Destructive Migrations**: Room database migrations are strictly non-destructive (`fallbackToDestructiveMigration()` is forbidden) to protect user saved video metadata.
5. **Calm, Distraction-Free UI**: Pure dark AMOLED backdrops, hot pink accents (`#FF3366`), glassmorphic overlays, and tactile haptic confirmation without audio soundboard noise.

---

## 2. Layered Architecture & Unidirectional Data Flow

Looply follows Clean Architecture organized in a feature-first package structure:

```
┌───────────────────────────────────────────────────────────────┐
│                       Compose UI Layer                        │
│   ReelsScreen, SavedVideosScreen, VideoPlayer, Components     │
└───────────────────────────────┬───────────────────────────────┘
                                │ Emits user events / gestures
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                        ViewModel Layer                        │
│          @HiltViewModel, StateFlow<UiState>, Events           │
└───────────────────────────────┬───────────────────────────────┘
                                │ Executes single-intent operations
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                         Domain Layer                          │
│     UseCases (DownloadReelUseCase, DeleteVideoUseCase, etc.)  │
└───────────────────────────────┬───────────────────────────────┘
                                │ Accesses repository interfaces
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                          Data Layer                           │
│     VideoRepositoryImpl, Local Entities, Media Mappers        │
└───────────────┬───────────────────────────────┬───────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────────┐ ┌─────────────────────────────┐
│      Local Storage Layer      │ │      External / Network     │
│   Room Database (VideoDatabase)│ │ Jsoup Instagram Scraper,   │
│   Video Files & Thumbnails    │ │ WorkManager Download Worker,│
│   DataStore Preferences       │ │ Media3 ExoPlayer Stream     │
└───────────────────────────────┘ └─────────────────────────────┘
```

---

## 3. Project Package Layout

```
com.arjunaayush.looply/
├── core/
│   ├── database/
│   │   ├── dao/             # VideoDao.kt
│   │   ├── entity/          # VideoEntity.kt
│   │   ├── VideoDatabase.kt # Room database definition
│   │   └── Migration.kt     # Verified non-destructive schema migrations
│   ├── network/
│   │   ├── InstagramScraper.kt # Jsoup metadata & video URL extractor
│   │   └── ScrapeResult.kt
│   ├── player/
│   │   ├── ReelPlayerManager.kt # Media3 ExoPlayer controller & preloader
│   │   └── VideoCache.kt        # SimpleCache singleton provider
│   ├── designsystem/
│   │   ├── theme/           # LooplyColors, LooplyTheme, Typography
│   │   ├── LinkerlyCard.kt  # Squircle elevated card
│   │   ├── LinkerlyButton.kt# Pill-shaped neon action buttons
│   │   ├── LinkerlyIcons.kt # 24x24dp vector stroke icons
│   │   ├── LinkerlyDialog.kt# Glassmorphic modal confirmation dialogs
│   │   ├── LinkerlyChip.kt  # Creator tag chips
│   │   └── LinkerlyNavigationBar.kt # Floating docked bottom navigation
│   └── util/
│       ├── HapticsManager.kt# Pure vibration tactile feedback
│       ├── StorageUtils.kt  # Video file paths & disk cleanup utilities
│       └── UrlUtils.kt      # Instagram reel URL parser & sanitization
├── data/
│   ├── local/
│   │   └── PreferencesDataStore.kt # User settings (loop default, mute memory)
│   ├── repository/
│   │   └── VideoRepositoryImpl.kt  # Single source of truth implementation
│   └── model/
│       └── VideoMappers.kt         # Entity <-> Domain mappers
├── domain/
│   ├── model/
│   │   ├── Video.kt                # Immutable domain video model
│   │   └── CreatorGroup.kt         # Aggregated videos per Instagram creator
│   ├── repository/
│   │   └── VideoRepository.kt      # Domain repository interface
│   └── usecase/
│       ├── DownloadReelUseCase.kt
│       ├── GetSavedVideosUseCase.kt
│       ├── GetVideosByCreatorUseCase.kt
│       ├── ToggleFavoriteUseCase.kt
│       ├── DeleteVideoUseCase.kt
│       └── ClearWatchedVideosUseCase.kt
├── feature/
│   ├── feed/                       # Full-screen vertical swipeable reel player
│   │   ├── ReelsScreen.kt
│   │   ├── ReelsViewModel.kt
│   │   └── components/
│   │       ├── ReelPlayerItem.kt
│   │       └── PlayerControlsOverlay.kt
│   ├── saved/                      # Saved videos grid & creator tabs
│   │   ├── SavedVideosScreen.kt
│   │   ├── SavedVideosViewModel.kt
│   │   └── components/
│   │       ├── CreatorFilterRow.kt
│   │       ├── VideoGridCard.kt
│   │       └── StorageUsageBar.kt
│   ├── receiver/                   # Transparent share sheet handler
│   │   └── ShareReceiverActivity.kt
│   └── settings/                   # Playback defaults, cache manager
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
├── navigation/                     # AppNavigation.kt, Screen routes
└── MainActivity.kt
```

---

## 4. Video Player & Media3 Lifecycle

- **Preload Strategy**: ExoPlayer initializes a circular pool of 3 player items (current, next, previous) using `SimpleCache` with a Least-Recently-Used (LRU) eviction rule.
- **Infinite Looping**: Videos default to `Player.REPEAT_MODE_ONE`. When the loop toggle is switched off, the player transitions to auto-advancing to the next reel on `STATE_ENDED`.
- **Lifecycle Management**: ExoPlayer is paused on `onPause` and released or cached properly in `DisposableEffect` to eliminate background battery drain and audio leaks.

---

## 5. Download Pipeline via WorkManager

1. **Ingestion**: URL received via `ShareReceiverActivity` or manual in-app paste.
2. **Extraction**: `InstagramScraper` fetches HTML via Jsoup, parses OpenGraph and script JSON tags to retrieve title, creator handle, thumbnail URL, and `.mp4` stream.
3. **Download**: Dispatched to `DownloadReelWorker` (extending `CoroutineWorker`).
4. **Foreground Service**: Shows an ongoing system notification with percentage progress.
5. **Persistence**: File written to `context.getExternalFilesDir("videos")`, entry inserted into `VideoDatabase`, and `HapticEffectType.CONFIRM` triggered.

---

## 6. Haptics Architecture

Looply replaces all audio sound effects with vibrational haptics through `HapticsManager`:
- Android 12+ (API 31+): Uses `Vibrator.areAllPrimitivesSupported()` and `VibrationEffect.startComposition()` for crisp tactile ticks and pops.
- Android 8–11 (API 26–30): Uses standardized `VibrationEffect.createOneShot()` and `VibrationEffect.createWaveform()`.
