# Ledger Rebrand Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app from "ToDoWallApp" to "Ledger" and replace the launcher icon with the architectural shelf mark.

**Architecture:** String replacements across resource files and Kotlin source, plus replacing the launcher icon foreground PNG with a vector drawable SVG-based mark. The Compose theme function gets renamed from `ToDoWallAppTheme` to `LedgerTheme`.

**Tech Stack:** Android XML resources, Kotlin/Compose, Android Vector Drawable

**Spec:** `docs/superpowers/specs/2026-03-12-ledger-rebrand-design.md`

---

## Chunk 1: Icon & Resources

### Task 1: Replace launcher icon foreground with vector drawable

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml` (replaces existing PNG)
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (no change needed — already references `@drawable/ic_launcher_foreground`)
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` (same — already references foreground)

**Context:** The current foreground is a PNG at `app/src/main/res/drawable/ic_launcher_foreground.png`. We replace it with an XML vector drawable at the same path (but `.xml` extension). The old PNG must be deleted.

- [ ] **Step 1: Delete the old PNG foreground**

```bash
rm app/src/main/res/drawable/ic_launcher_foreground.png
```

- [ ] **Step 2: Create the vector drawable foreground**

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="120"
    android:viewportHeight="120">

    <!-- Wall (vertical line, left side) -->
    <path
        android:pathData="M30,24 L30,96"
        android:strokeWidth="5"
        android:strokeColor="#666666"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />

    <!-- Ledge (horizontal teal bar) -->
    <path
        android:pathData="M30,68 L88,68"
        android:strokeWidth="5"
        android:strokeColor="#80CBC4"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />

    <!-- Task line 1 (most visible) -->
    <path
        android:pathData="M40,56 L78,56"
        android:strokeWidth="3.5"
        android:strokeColor="#EEEEEE"
        android:strokeLineCap="round"
        android:strokeAlpha="0.9"
        android:fillColor="#00000000" />

    <!-- Task line 2 (medium) -->
    <path
        android:pathData="M40,46 L68,46"
        android:strokeWidth="3.5"
        android:strokeColor="#EEEEEE"
        android:strokeLineCap="round"
        android:strokeAlpha="0.55"
        android:fillColor="#00000000" />

    <!-- Task line 3 (faint) -->
    <path
        android:pathData="M40,36 L72,36"
        android:strokeWidth="3.5"
        android:strokeColor="#EEEEEE"
        android:strokeLineCap="round"
        android:strokeAlpha="0.25"
        android:fillColor="#00000000" />
</vector>
```

**Note:** The 108dp size is the standard adaptive icon foreground size. The viewportWidth/Height of 120 matches our SVG design. The icon will be centered within the 72dp visible area after the adaptive icon system applies its masking and 18dp padding on each side.

- [ ] **Step 3: Add monochrome icon for Android 13+ themed icons**

Create `app/src/main/res/drawable/ic_launcher_monochrome.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="120"
    android:viewportHeight="120">

    <!-- Wall -->
    <path
        android:pathData="M30,24 L30,96"
        android:strokeWidth="5"
        android:strokeColor="#000000"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />

    <!-- Ledge -->
    <path
        android:pathData="M30,68 L88,68"
        android:strokeWidth="5"
        android:strokeColor="#000000"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />

    <!-- Task line 1 -->
    <path
        android:pathData="M40,56 L78,56"
        android:strokeWidth="3.5"
        android:strokeColor="#000000"
        android:strokeLineCap="round"
        android:strokeAlpha="0.9"
        android:fillColor="#00000000" />

    <!-- Task line 2 -->
    <path
        android:pathData="M40,46 L68,46"
        android:strokeWidth="3.5"
        android:strokeColor="#000000"
        android:strokeLineCap="round"
        android:strokeAlpha="0.55"
        android:fillColor="#00000000" />

    <!-- Task line 3 -->
    <path
        android:pathData="M40,36 L72,36"
        android:strokeWidth="3.5"
        android:strokeColor="#000000"
        android:strokeLineCap="round"
        android:strokeAlpha="0.25"
        android:fillColor="#00000000" />
</vector>
```

- [ ] **Step 4: Update adaptive icon XML to include monochrome**

Modify `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

Modify `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` identically.

**Note:** The `<monochrome>` element requires `minSdk` ≥ 26 in the mipmap-anydpi-**v26** folder, which is already the case. On devices below Android 13, the monochrome layer is simply ignored.

- [ ] **Step 5: Delete legacy mipmap WebP files**

The pre-generated WebP files in `mipmap-hdpi` through `mipmap-xxxhdpi` are fallbacks for pre-API 26 devices. Since minSdk is 23, we should regenerate them. For now, delete the old ones — Android Studio can regenerate them, or we keep the `anydpi-v26` vector-only approach (which covers API 26+; API 23-25 will fall back to a default icon temporarily).

```bash
rm app/src/main/res/mipmap-hdpi/ic_launcher.webp
rm app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
rm app/src/main/res/mipmap-mdpi/ic_launcher.webp
rm app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
rm app/src/main/res/mipmap-xhdpi/ic_launcher.webp
rm app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
rm app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
rm app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
rm app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp
rm app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
```

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/res/drawable/ app/src/main/res/mipmap-anydpi-v26/ app/src/main/res/mipmap-*/
git commit -m "feat: replace launcher icon with Ledger architectural shelf mark"
```

---

### Task 2: Update app name and tagline strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:2` — change `app_name`
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/SignInScreen.kt:91` — change tagline
- Modify: `settings.gradle.kts:22` — change `rootProject.name`

- [ ] **Step 1: Update strings.xml**

In `app/src/main/res/values/strings.xml`, change line 2:
```xml
<!-- OLD -->
<string name="app_name">ToDoWallApp</string>
<!-- NEW -->
<string name="app_name">Ledger</string>
```

- [ ] **Step 2: Update sign-in screen tagline**

In `app/src/main/java/com/example/todowallapp/ui/screens/SignInScreen.kt`, change line 91:
```kotlin
// OLD
text = "Your tasks, beautifully displayed",
// NEW
text = "Your tasks, held on the wall",
```

- [ ] **Step 3: Update settings.gradle.kts project name**

In `settings.gradle.kts`, change line 22:
```kotlin
// OLD
rootProject.name = "ToDoWallApp"
// NEW
rootProject.name = "Ledger"
```

- [ ] **Step 4: Update API application names**

In `app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt`, line 61:
```kotlin
// OLD
.setApplicationName("ToDoWallApp")
// NEW
.setApplicationName("Ledger")
```

In `app/src/main/java/com/example/todowallapp/data/repository/GoogleCalendarRepository.kt`, line 69:
```kotlin
// OLD
.setApplicationName("ToDoWallApp")
// NEW
.setApplicationName("Ledger")
```

In `app/src/main/java/com/example/todowallapp/data/repository/GoogleCalendarRepository.kt`, line 214:
```kotlin
// OLD
append("Scheduled from ToDoWallApp")
// NEW
append("Scheduled from Ledger")
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml settings.gradle.kts
git add app/src/main/java/com/example/todowallapp/ui/screens/SignInScreen.kt
git add app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt
git add app/src/main/java/com/example/todowallapp/data/repository/GoogleCalendarRepository.kt
git commit -m "feat: rename app to Ledger, update tagline and API names"
```

---

## Chunk 2: Theme Rename

### Task 3: Rename `ToDoWallAppTheme` to `LedgerTheme`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/Theme.kt:96` — rename function definition
- Modify (16 files total): All files importing/calling `ToDoWallAppTheme` — update to `LedgerTheme`

**Full list of files referencing `ToDoWallAppTheme`:**

| File | Usage |
|------|-------|
| `ui/theme/Theme.kt:96` | Function definition |
| `MainActivity.kt:65,267` | Import + call |
| `ui/screens/SignInScreen.kt:4,179,191,203` | Import + preview calls |
| `ui/screens/TaskWallScreen.kt:141` | Import |
| `ui/components/ClockHeader.kt:42,180` | Import + preview |
| `ui/components/PageIndicator.kt:29,95` | Import + preview |
| `ui/components/SettingsPanel.kt:49,586,607` | Import + previews |
| `ui/components/TaskItem.kt:61,549` | Import + preview |
| `ui/components/UndoToast.kt:30,98,113` | Import + previews |
| `ui/components/ViewSwitcherPill.kt:34,146,161` | Import + previews |
| `ui/components/WaveformVisualizer.kt:37,167` | Import + preview |
| `androidTest/.../PhoneModeUiTest.kt:18,44,59,97` | Import + test calls |
| `androidTest/.../TaskWallScreenshotTest.kt:18,107` | Import + test call |

- [ ] **Step 1: Rename the function definition in Theme.kt**

In `app/src/main/java/com/example/todowallapp/ui/theme/Theme.kt`, line 96:
```kotlin
// OLD
fun ToDoWallAppTheme(
// NEW
fun LedgerTheme(
```

- [ ] **Step 2: Find-and-replace all imports across the codebase**

In every file listed above, replace:
```kotlin
// OLD
import com.example.todowallapp.ui.theme.ToDoWallAppTheme
// NEW
import com.example.todowallapp.ui.theme.LedgerTheme
```

- [ ] **Step 3: Find-and-replace all call sites across the codebase**

In every file listed above, replace all instances of:
```kotlin
// OLD
ToDoWallAppTheme {
// NEW
LedgerTheme {
```

And the one call with parameters in `MainActivity.kt:267`:
```kotlin
// OLD
ToDoWallAppTheme(darkTheme = isDarkTheme) {
// NEW
LedgerTheme(darkTheme = isDarkTheme) {
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run unit tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/theme/Theme.kt
git add app/src/main/java/com/example/todowallapp/MainActivity.kt
git add app/src/main/java/com/example/todowallapp/ui/screens/
git add app/src/main/java/com/example/todowallapp/ui/components/
git add app/src/androidTest/
git commit -m "refactor: rename ToDoWallAppTheme to LedgerTheme"
```

---

## Post-Implementation Notes

**Not in scope (deferred):**
- Package rename (`com.example.todowallapp` → `com.example.ledger`) — high risk, many files, separate task
- Regenerating legacy mipmap WebPs for API 23-25 — use Android Studio's Image Asset tool when convenient
- Updating Google Cloud Console project name or OAuth consent screen branding
