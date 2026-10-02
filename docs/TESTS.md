# Looply Master Test Suite & Verification Plan

> **Project**: Looply (`com.arjunaayush.looply`)  
> **Platform**: Android | **Test Frameworks**: JUnit 4/5 + MockK + Kotlinx Coroutines Test

---

## 📊 Test Suite Summary

| Category | Status | Verification Target |
| :--- | :--- | :--- |
| **Domain Logic & UseCases** | 🟡 Planned | Core video CRUD, favorite toggling, creator filtering |
| **Core Utilities & Logic** | 🟡 Planned | Instagram URL sanitization, storage calculation |
| **ViewModel State Flow** | 🟡 Planned | Feed paging state, playback controls, creator grouping |

---

## 📋 Test Checklist

### 1. Domain Logic & UseCases (`domain/usecase/`)
- [ ] **`DownloadReelUseCaseTest`**: Verifies URL validation, WorkManager dispatch, and metadata persistence.
- [ ] **`DeleteVideoUseCaseTest`**: Verifies local file removal on disk and Room database row deletion.
- [ ] **`GetVideosByCreatorUseCaseTest`**: Verifies accurate creator filtering and sorting by recency.
- [ ] **`ToggleFavoriteUseCaseTest`**: Verifies favorite state toggling and Flow emission.
- [ ] **`ClearWatchedVideosUseCaseTest`**: Verifies bulk deletion of watched videos to reclaim disk space.

### 2. Core Utility & Data Logic (`core/util/` & `core/network/`)
- [ ] **`UrlUtilsTest`**: Tests extraction of reel IDs from `instagram.com/reel/...`, `instagram.com/p/...`, and tracking parameter stripping (`?igsh=...`).
- [ ] **`StorageUtilsTest`**: Verifies accurate free disk space calculations and video path formatting.
- [ ] **`InstagramScraperTest`**: Mocked HTML response tests ensuring robust extraction of OpenGraph tags.

### 3. ViewModel StateFlow Tests (`feature/*/`)
- [ ] **`ReelsViewModelTest`**: Tests vertical paging state, auto-play active index, and loop toggle state.
- [ ] **`SavedVideosViewModelTest`**: Tests creator group filtering, multi-select deletion, and disk usage state.

---

## 🏃 Running Automated Tests

```bash
./gradlew test
```
