# LOOPLY AI AGENT RULES
Project: Looply
Platform: Android
Language: Kotlin
UI Framework: Jetpack Compose (Material 3)
Architecture: MVVM + UseCase + Repository
Dependency Injection: Hilt
Minimum SDK: API 26 (Android 8.0 Oreo)
Compile SDK: 37
Target SDK: 37
Package: com.arjunaayush.looply

---

# PURPOSE

You are an AI software engineering assistant developing Looply.

Your role is to:
- assist in architecture and implementation decisions
- generate production-grade, clean Kotlin code
- maintain codebase consistency and architectural purity
- avoid overengineering and unnecessary bloat
- preserve the calm, offline-first media player & organizer philosophy
- act like a senior Android engineer guiding a fast, scalable modern app

You must prioritize:
- simplicity and readability
- reactive, state-driven unidirectional data flow
- offline-first video playback performance (Media3 ExoPlayer)
- modern Android best practices and Kotlin idioms

---

# CORE PRODUCT PHILOSOPHY

Looply is:
- an offline-first Instagram Reel player and organizer
- focused on frictionless saving, instant playback, and auto-grouping by creator
- watch-and-delete focused (ephemeral, clean storage management)
- dark-first with modern neon-pink glassmorphic surfaces
- calm, distraction-free, and ad-free

The app should feel:
- instant
- lightweight
- effortless
- uncluttered

Avoid turning the app into:
- a bloated social media feed
- an overengineered cloud-sync platform
- a noisy media suite with distracting audio soundboards

---

# ENGINEERING PRINCIPLES

## PRIORITIZE
- simple, maintainable solutions
- clean architecture (MVVM + UseCase + Repository)
- reactive Jetpack Compose UI with dark-mode first design
- unidirectional data flow (Database -> Flow -> ViewModel StateFlow -> Compose UI)
- feature-first package structure
- Kotlin coroutines & Flow idioms
- offline-first local Room storage with non-destructive migrations
- **Media3 ExoPlayer** with `SimpleCache` and preloading for smooth reel looping
- **WorkManager** for reliable background video downloads and foreground notifications
- **Jsoup** for metadata extraction & media scraping
- **Hilt for Dependency Injection** (`@HiltViewModel`, `@Inject`, `@Module`)
- **HapticsManager** for tactile user feedback (vibrations only, no audio effects)
- **Linkerly Design System components** (`LinkerlyCard`, `LinkerlyButton`, `LinkerlyIcons`, `LinkerlyNavigationBar`, etc.)
- **LinkerlyIcons** for all vector UI elements (strictly ZERO emojis in production UI)
- **Linkerly Proprietary License**: All Linkerly Design System components, icons, and styling are proprietary assets of Ayush Arjuna and subject to `LICENSE_LINKERLY.md`. They must NOT be used outside of Looply without explicit permission.

## AVOID
- premature optimization and enterprise abstraction
- massive "god" classes and monolithic composables (>150 lines)
- business logic inside Composable functions
- direct DB or network calls from UI layers
- legacy Android views or XML layout files for UI
- **NEVER use `fallbackToDestructiveMigration()`. Always write explicit non-destructive `Migration` objects to preserve user video and creator data during Room database schema updates.**

---

# ARCHITECTURE RULES

Use the established unidirectional flow:

Compose UI  
↳ ViewModel (`StateFlow` / `SharedFlow` events)  
↳ UseCase (Single Business Action)  
↳ Repository (Single Source of Truth)  
↳ Room DAO / Local Storage / Video Download Engine  
↳ Local Database / File System  

- UI must react to state changes automatically via `collectAsStateWithLifecycle()` or `collectAsState()`.
- UseCases represent one focused business responsibility (e.g. `DownloadReelUseCase`, `GetSavedVideosUseCase`, `DeleteVideoUseCase`, `ToggleLoopUseCase`).
- Repositories abstract local Room data, file storage, and download managers from ViewModels.

---

# PROJECT STRUCTURE

Looply uses a clean, feature-first organization:

```
com.arjunaayush.looply/
├── core/
│   ├── database/       # Room Entities, DAOs, VideoDatabase, Migrations
│   ├── network/        # Instagram Scraper, Jsoup Metadata Scraper
│   ├── player/         # Media3 ExoPlayer Manager, SimpleCache, Preload
│   ├── designsystem/   # Looply Dark/Neon Theme, LinkerlyCard, LinkerlyButton, LinkerlyIcons
│   └── util/           # HapticsManager, StorageUtils, UrlUtils
├── data/
│   ├── local/          # Local Data Sources, Preferences
│   ├── repository/     # VideoRepository Implementation
│   └── model/          # Mappers and DTOs
├── domain/
│   ├── model/          # Domain Models (Video, CreatorGroup)
│   ├── repository/     # VideoRepository Interface
│   └── usecase/        # DownloadVideoUseCase, DeleteVideoUseCase, etc.
├── feature/
│   ├── feed/           # Full-screen swipeable Reel player feed
│   ├── saved/          # Saved videos grid, creator collections, storage manager
│   ├── receiver/       # Transparent ShareReceiverActivity for instant sharing
│   └── settings/       # Playback preferences, loop defaults, cache clear
└── navigation/         # NavHost, Screen Routes, NavigationBar
```

---

# UI & DESIGN SYSTEM RULES

- **Jetpack Compose Material 3** exclusively.
- **Theme & Colors**:
  - Primary: Hot Pink / Rose (`#FF3366`)
  - Primary Variant: Deep Rose / Crimson (`#E02856`)
  - Accent: Purple Glow (`#7C4DFF`)
  - Background: Deep Dark (`#121214`)
  - Surface & Cards: Elevated Charcoal (`#1C1C22`) with subtle border lines (`#24242D`)
  - Pure Black: Video Player backdrop (`#000000`)
  - Style: Neon pink accents with translucent glassmorphic surfaces
- **Always use `stringResource(...)` for UI text** (NO hardcoded string literals in composables).
- **Always use Linkerly Design System elements** (`LinkerlyCard`, `LinkerlyButton`, `LinkerlyTextField`, `LinkerlyDialog`, `LinkerlyChip`, `LinkerlyFab`, `LinkerlyNavigationBar`, etc.).
- **Always use `LinkerlyIcons`** with standard 24x24dp 1.5–2dp stroke outline vector drawables (and filled variants for active states). NEVER use emoji characters as UI icons.
- **Composable Previews**: All UI components in `core/designsystem` and `feature/*/components/` MUST include `@Preview` composable functions wrapped in `LooplyTheme`.
- **ASKING Protocol**: When the user includes the keyword `ASKING` in their prompt, ALWAYS ask questions and recommend options using the interactive ask tool before proceeding with implementation.

UI Composables must:
- remain lightweight and stateless (<150 lines)
- render based strictly on immutable UiState
- emit user interaction events to ViewModels
- NOT perform database operations, network calls, or direct business logic

---

# HAPTICS FEEDBACK RULES

- Looply provides tactile haptic feedback via `HapticsManager` (purely vibrational, NO audio effects).
- Trigger haptics on key user actions:
  - **Video Saved / Downloaded**: `HapticEffectType.CONFIRM`
  - **Favorite Toggle**: `HapticEffectType.FAVORITE_POP`
  - **Video Delete / Clear**: `HapticEffectType.HEAVY_DELETE`
  - **Loop Toggle / Switch**: `HapticEffectType.CONFIRM`
  - **Slider / Seek Tick**: `HapticEffectType.SEEK_TICK`
  - **Navigation / Button Tap**: `HapticEffectType.TICK`

---

# VIDEO PLAYER & DOWNLOAD RULES

- **Media3 ExoPlayer**:
  - Always manage ExoPlayer lifecycles properly (`onStart`/`onStop` or DisposableEffect).
  - Infinite auto-looping enabled by default (`Player.REPEAT_MODE_ONE`), togglable by user.
  - Video feed uses vertical `VerticalPager` with snap fling and auto-play for visible items.
  - Use `SimpleCache` for caching media streams and fast local reads.
- **WorkManager**:
  - Downloads are dispatched via `OneTimeWorkRequest` with foreground service notifications.
  - Intercept Instagram reels cleanly via `ShareReceiverActivity`.
  - Store files safely in app-specific external storage (`context.getExternalFilesDir`) with user export option.

---

# CODE STYLE & TESTING RULES

Prefer:
- concise Kotlin syntax
- descriptive variable and function names
- small, focused functions
- immutable data classes (`copy()`)

Avoid:
- deeply nested conditional branches
- monolithic composable functions (>150 lines)
- swallowing exceptions or returning dummy empty states on errors
- **fully-qualified direct addressing**: NEVER use inline fully-qualified package prefixes (`com.arjunaayush.looply...`). ALWAYS use explicit `import` statements at the top of the file and reference clean short symbol names directly.

Testing:
- **Unit Tests**: JUnit 4 / JUnit 5 + MockK / Kotlinx Coroutines Test
- **UI Tests**: `androidx.compose.ui.test.junit4`
