# Looply - Product Roadmap & Backlog (`TODO.md`)

This document tracks planned architectural milestones, feature implementations, and quality checklists for Looply.

---

## 🚀 Phase 1: Architecture & Design System Alignment (Immediate Priority)

### 1. 🏗️ Clean Architecture & Hilt Dependency Injection
- [ ] Add Hilt Gradle plugin and dependencies (`hilt-android`, `hilt-compiler`).
- [ ] Create `@HiltAndroidApp` Application class (`LooplyApplication.kt`).
- [ ] Restructure packages into `core/`, `data/`, `domain/`, `feature/`, `navigation/`.
- [ ] Define domain repository interface `VideoRepository` and use cases (`DownloadReelUseCase`, `DeleteVideoUseCase`, `GetVideosByCreatorUseCase`).
- [ ] Provide Hilt modules for Database, Repository, and Player dependencies.

### 2. 🎨 Neon-Pink Dark Theme & Glassmorphic Design System
- [ ] Update `LooplyColors` with Hot Pink (`#FF3366`), Deep Rose (`#E02856`), Purple Glow (`#7C4DFF`), and Dark Charcoal surfaces (`#121214`, `#1C1C22`).
- [ ] Configure `sourceSets` in `app/build.gradle.kts` to link `src/main/res-icons`.
- [ ] Implement `LinkerlyIcons.kt` pointing to vector drawables (replacing all emojis across the entire app).
- [ ] Port and adapt Linkerly components: `LinkerlyCard`, `LinkerlyButton`, `LinkerlyTextField`, `LinkerlyDialog`, `LinkerlyChip`, `LinkerlyFab`, `LinkerlyNavigationBar`.
- [ ] Create `@Preview` composables wrapped in `LooplyTheme` for each component.

### 3. 📳 Haptics Feedback Integration
- [ ] Port `HapticsManager.kt` providing pure vibrational feedback (`CONFIRM`, `FAVORITE_POP`, `HEAVY_DELETE`, `SEEK_TICK`, `TICK`).
- [ ] Verify zero audio sound effects to ensure complete playback tranquility.

---

## 🎬 Phase 2: Video Player & Full-Screen Feed

### 4. 🔁 Media3 ExoPlayer & Preloading Engine
- [ ] Implement `ReelPlayerManager` with Media3 ExoPlayer.
- [ ] Integrate `SimpleCache` with LRU eviction for smooth video streaming and offline caching.
- [ ] Implement adjacent video preloading (current, next, previous) for zero-buffer swipe transitions.
- [ ] Set `Player.REPEAT_MODE_ONE` as default infinite looping mode.

### 5. 📱 Swipeable Reel Feed (`VerticalPager`)
- [ ] Build vertical full-screen swipe feed using Compose Foundation `VerticalPager`.
- [ ] Auto-play currently visible reel; pause off-screen reels immediately to preserve battery.
- [ ] Build auto-hiding glassmorphic controls overlay (fades out after 2.5s).
- [ ] Add double-tap to favorite with spring scale animation and `FAVORITE_POP` haptic feedback.
- [ ] Add single-tap to pause/resume with animated indicator.
- [ ] Add floating loop toggle button with rotation animation.

---

## 📥 Phase 3: Ingestion & Background Download Pipeline

### 6. ⚡ Transparent Share Receiver
- [ ] Implement transparent `ShareReceiverActivity` for `ACTION_SEND` intents.
- [ ] Extract shared Instagram reel URL and validate format.
- [ ] Schedule download worker and display quick floating HUD/Toast.
- [ ] Finish activity in <200ms without displacing user workflow.

### 7. 🌐 Jsoup Instagram Scraper & WorkManager
- [ ] Enhance `InstagramScraper` with resilient extraction of video URL, thumbnail, author handle, and caption.
- [ ] Implement `DownloadReelWorker` using AndroidX WorkManager.
- [ ] Display ongoing foreground notification with download progress bar.
- [ ] Handle error states (private accounts, rate limits) with actionable messages.

---

## 🗃️ Phase 4: Creator Organization & Watch-and-Delete Flow

### 8. 👤 Auto-Grouping by Creator Handle
- [ ] Auto-categorize saved videos by Instagram handle (`@creator`).
- [ ] Build `CreatorFilterRow` in `SavedVideosScreen` with horizontal `LinkerlyChip`s.
- [ ] Filter video grid on creator selection with smooth layout transitions.

### 9. 🧹 Watch-and-Delete Storage Management
- [ ] Prominent 1-tap delete icon in video player overlay.
- [ ] Multi-select bulk delete in `SavedVideosScreen`.
- [ ] Storage usage progress bar displaying total MB/GB occupied by downloaded videos.
- [ ] "Clear Watched Loops" 1-tap cleanup utility.
- [ ] "Export to Gallery" action to write videos to public `MediaStore`.

---

## 🛡️ Quality & Verification Checklist
- [ ] Room database migrations must be strictly non-destructive (never use `fallbackToDestructiveMigration()`).
- [ ] Zero emoji characters in production UI (all replaced by `LinkerlyIcons`).
- [ ] All composables under 150 lines with `@Preview` annotations.
- [ ] Strict top-level imports; zero inline fully-qualified package prefixes.
