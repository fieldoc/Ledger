# Ledger — Name & Logo Rebrand Design

**Date**: 2026-03-12
**Status**: Implemented
**Implemented:** 2026-04-09

## Summary

Rename the app from "ToDoWallApp" to **Ledger** and replace the launcher icon with an architectural shelf mark.

## Name: Ledger

**Why it works:**
- **Architectural**: A ledger is a horizontal stone slab projecting from a wall — literally what the app is
- **Record-keeping**: A ledger is where important things are written down
- **Load-bearing**: In construction, a ledger board carries structural weight — the app holds mental load so you don't have to
- **Short, memorable, ownable** as a brand

**Display name**: "Ledger" everywhere — launcher, sign-in screen, about text.
**Tagline update**: "Your tasks, held on the wall" (replacing "Your tasks, beautifully displayed")

## Logo: Architectural Shelf (Bold variant)

An abstract mark showing a vertical wall line with a horizontal teal ledge projecting outward. Three task lines (fading opacity) rest on the surface.

### Construction

On a rounded-rectangle background (`#1C1C1C`, rx=28):

| Element | Shape | Position | Size | Color | Notes |
|---------|-------|----------|------|-------|-------|
| Wall | Vertical rect, rounded ends | Left third | 5w × 72h | `#666666` | Structural anchor |
| Ledge | Horizontal rect, rounded ends | Bottom third, projecting right from wall | 58w × 5h | `#80CBC4` (teal accent) | The defining feature |
| Task line 1 | Horizontal rect | Above ledge, closest | 38w × 3.5h | `#EEEEEE` @ 90% opacity | Most prominent task |
| Task line 2 | Horizontal rect | Middle | 28w × 3.5h | `#EEEEEE` @ 55% opacity | Secondary task |
| Task line 3 | Horizontal rect | Highest | 32w × 3.5h | `#EEEEEE` @ 25% opacity | Tertiary/fading task |

All task lines are offset ~8px right of the wall (indented, resting on the ledge surface).

### SVG Reference (120×120 viewBox)

```xml
<svg width="120" height="120" viewBox="0 0 120 120">
  <rect width="120" height="120" rx="28" fill="#1C1C1C"/>
  <rect x="30" y="24" width="5" height="72" rx="2.5" fill="#666666"/>
  <rect x="30" y="68" width="58" height="5" rx="2.5" fill="#80CBC4"/>
  <rect x="40" y="56" width="38" height="3.5" rx="1.75" fill="#EEEEEE" opacity="0.9"/>
  <rect x="40" y="46" width="28" height="3.5" rx="1.75" fill="#EEEEEE" opacity="0.55"/>
  <rect x="40" y="36" width="32" height="3.5" rx="1.75" fill="#EEEEEE" opacity="0.25"/>
</svg>
```

### Adaptive Icon (Android)

- **Background**: Solid `#0A1220` (existing dark blue-gray, kept for consistency with system theming)
- **Foreground**: The shelf mark SVG converted to vector drawable, centered within the 108dp safe zone
- **Monochrome**: For themed icons (Android 13+), provide a single-color version using just the outlines

### Size Considerations

The bold stroke weights (5dp wall, 5dp ledge, 3.5dp task lines) were chosen specifically for legibility at launcher icon size (48dp rendered). At this weight, all elements remain distinct even on small screens.

## Changes Required

### Strings & Branding
- `strings.xml`: `app_name` → "Ledger"
- `SignInScreen.kt`: tagline → "Your tasks, held on the wall"
- Any other display of "ToDoWallApp" in UI text

### Icon Assets
- Replace `ic_launcher_foreground.png` with vector drawable version of the shelf mark
- Keep `ic_launcher_background.xml` (dark blue-gray) or update to match `#1C1C1C`
- Generate all density variants (mdpi through xxxhdpi) for the adaptive icon
- Add monochrome variant for Android 13+ themed icons

### Package Name
- **No change** to `com.example.todowallapp` — package rename is a separate, higher-risk task and not part of this rebrand

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Name | "Ledger" (one word) | Clean, short, dual meaning (architecture + record-keeping) |
| Logo style | Architectural shelf, bold weight | Best legibility at icon size; communicates wall + tasks instantly |
| Background color | Keep `#0A1220` | Consistent with existing system icon theming |
| Tagline | "Your tasks, held on the wall" | Emphasizes the wall-holding-for-you concept |
| Package name | Unchanged | Low priority, high risk — defer |
