# Looply Polish, Micro-Interactions & Animations Blueprint

> **Project**: Looply (`com.arjunaayush.looply`)  
> **Platform**: Android (Min SDK: 26, Target SDK: 37) | **Language**: Kotlin 2.x  
> **UI Framework**: Jetpack Compose (Material 3) | **Tactile Engine**: `HapticsManager`  
> **Visual Identity**: Neon Pink & Rose Glow (`#FF3366`, `#E02856`) with Obsidian Glassmorphism (`#121214`, `#1C1C22`)

---

## 1. Executive Summary & Aesthetic Vision

Looply is crafted to feel **hypnotic, seamless, tactile, and visually effortless**. Video playback should feel immediate, touch gestures should trigger physical-like feedback, and the interface should recede into darkness to let the content shine.

---

## 2. Haptics Feedback Matrix (`HapticsManager`)

Looply avoids audio sound effects so as not to interrupt or clash with reel audio. All tactile confirmation is handled via device vibration primitives:

| User Action | Trigger Component | `HapticEffectType` | Visual Motion / Response |
| :--- | :--- | :--- | :--- |
| **Download Complete** | WorkManager Worker / Notification | `CONFIRM` | Floating glowing checkmark pill with subtle pulse |
| **Double-Tap Heart / Like** | Full-Screen Video Tap | `FAVORITE_POP` | 3D Scale pop ($1.0 \rightarrow 1.4 \rightarrow 1.0$) with neon pink heart burst |
| **Delete Reel** | Trash Button in Player / Saved Grid | `HEAVY_DELETE` | Card collapse + scale-fade exit |
| **Loop Toggle** | Loop Button in Player Overlay | `CONFIRM` | Rotating loop arrow spin ($0^\circ \rightarrow 360^\circ$) |
| **Video Scrubbing** | Seek Bar Drag | `SEEK_TICK` | Subtle micro-vibration on 1-second scrub steps |
| **Tab / Creator Chip Tap** | Bottom Navigation / Creator Filter | `TICK` | Pill background slide and text glow |

---

## 3. Video Player Micro-Interactions

### A. Auto-Hiding Controls Overlay
- **Behavior**: Tapping anywhere on the video toggles controls visibility. If left untouched, controls smoothly fade out after 2.5 seconds.
- **Motion Spec**: `fadeIn(animationSpec = tween(180))` and `fadeOut(animationSpec = tween(250))`.

### B. Double-Tap to Favorite Animation
- **Motion Spec**: Double-tapping the video centers an animated heart vector:
  - Scale: `0f` to `1.35f` with `Spring.DampingRatioMediumBouncy`.
  - Alpha: `1f` for 300ms, then fades to `0f` over 200ms.
  - Haptic: `HapticEffectType.FAVORITE_POP`.

### C. Play / Pause Spring Pulse
- When the reel is paused, a large centered translucent pause icon appears momentarily and shrinks from `1.2f` to `1.0f` before holding.
- Resuming immediately fades the play icon with spring scale (`1.0f` to `0.8f`).

---

## 4. Neon-Pink Glassmorphism & Surface Design

### A. Dark Glass Surfaces
- Bottom navigation bar and player overlay badges use semi-transparent surfaces (`Color(0xCC1C1C22)`) paired with `Modifier.blur(16.dp)`.
- Borders feature subtle 1dp strokes with `Color(0x33FF3366)` to give a refined neon edge glow.

### B. LinkerlyCard Adaptation
- Saved video grid cards use 16dp squircle rounded corners with elevated charcoal background (`#1C1C22`).
- Ripple color is tinted with `LooplyColors.Primary` (`#FF3366`) at 12% opacity.

---

## 5. Strict Zero-Emoji Standard (LinkerlyIcons)

Emojis degrade UI consistency across different Android versions and device manufacturers. Looply strictly uses 24x24dp vector drawables from `LinkerlyIcons`:
- Play / Pause: `LinkerlyIcons.Play` / `LinkerlyIcons.Pause`
- Heart / Favorite: `LinkerlyIcons.Favorite` / `LinkerlyIcons.FavoriteFilled`
- Loop / Repeat: `LinkerlyIcons.Loop`
- Share: `LinkerlyIcons.Share`
- Delete: `LinkerlyIcons.Delete`
- Creator: `LinkerlyIcons.Person` / `LinkerlyIcons.PersonFilled`
