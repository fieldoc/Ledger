# UI Enhancement Design: Phone Elevation + Calendar Polish

**Date**: 2026-02-23
**Status**: Approved
**Builds on**: `2026-02-22-phone-wall-ui-unification-design.md` (shared primitives established)

## Goals

1. **Elevate phone mode** from functional-but-flat to polished and distinctive
2. **Unify visual DNA** across phone and wall modes via shared visual motifs
3. **Polish calendar view** (wall mode) with structural + visual improvements

## Approach: Wall DNA to Phone (Approach A)

Port the wall's visual vocabulary into phone-specific components. Maximum visual consistency without coupling. Phone gets its own personality through fluid touch micro-animations.

---

## 1. Phone Task List — Accordion Sections

Each Google Tasks list becomes a collapsible section, matching the wall's accordion folder concept.

### Collapsed state
- List title in `titleMedium`, left-aligned
- First task visible as faded, truncated text (peek — same as wall spec)
- Task count badge ("5 tasks") in `labelSmall`, muted text color
- Subtle chevron icon, rotates on expand/collapse (200ms)
- Bottom border in `borderColor`

### Expanded state
- Tap section header to bloom open
- All tasks visible at full opacity
- 300ms ease-in-out transition
- Tasks fade in/out (not just appear/disappear)

### Sorting
- Lists with nearest due date float to top (same as wall)

### Differences from wall
- **Touch-driven**: Tap header to toggle (not encoder-focus-driven)
- **Multiple open**: Multiple sections can be open simultaneously (wall auto-collapses)

---

## 2. Phone Task Items — Visual DNA Transfer

### Card surface
- `surfaceCard` background
- 12dp corner radius (slightly smaller than wall's 16dp for phone proportions)
- Subtle elevation shadow (existing, refined)

### Urgency left bar (refine existing)
- 3dp wide vertical bar on left edge, inner radius
- Colors: `urgencyOverdue` (terracotta), `urgencyDueToday` (warm amber), `urgencyDueSoon` (subtle warmth)
- Animated color transitions on state changes

### Subtask connecting line (NEW)
- Parent tasks with subtasks show children indented below
- **Collapsible**: Tap parent to expand/collapse subtasks
- Collapse indicator: small chevron or count badge ("3 subtasks")
- Vertical connecting line: `borderColor` at 40% opacity, 1dp width
- Expand/collapse: 250ms ease-in-out, subtasks fade + slide in from above

### Due date badge
- Shared `DueDateBadge` composable, right-aligned in task row

### Completion animation
- Checkbox fills with accent color (existing shared animation)
- Card gently compresses (5% scale Y) then settles
- After 300ms, card smoothly slides to completed section (drift-to-bottom, wall spec)
- Haptic pulse via existing `Haptics` util

---

## 3. Phone Capture Bar — Refined

Redesign for premium feel while keeping the bottom bar structure.

### Changes
- **Thinner profile**: ~56dp height (reduced from current)
- **Surface integration**: `surfaceCard` background with thin top border in `borderColor` — feels like page bottom, not floating island
- **Icons only**: Remove text labels. Increase icon size 24dp → 28dp
- **Voice emphasis**: Mic icon gets subtle circular background in `accentPrimary` at 12% opacity
- **Haptic on tap**: Light haptic feedback on all actions
- **Touch targets**: 48dp minimum per action
- **Safe area**: Continues to respect `navigationBarsPadding`

---

## 4. Micro-Animation System (Phone Identity)

The phone's distinctive personality: "alive and responsive."

### List-level
- **Accordion expand/collapse**: 300ms ease-in-out. Tasks stagger in at 40ms delay. Stagger out at 25ms.
- **Task completion reorder**: Completed task slides to section bottom. Remaining tasks close gap (300ms).
- **Pull-to-refresh**: Elastic overscroll feel with rotating refresh icon.

### Item-level
- **Task tap**: Gentle press scale (98% → 100%) on touch down/up. 150ms.
- **Checkbox fill**: 250ms via shared `AnimatedTaskCompletion`.
- **Subtask expand**: Children fade + slide from y-offset (10dp up → 0). 250ms with stagger.
- **Scroll parallax**: Subtle 2dp horizontal offset on cards based on scroll velocity. Very subtle.

### Transitions
- **Section enter**: On screen load, sections stagger in from bottom (opacity 0→1, translateY 20dp→0). 300ms per section, 60ms stagger.
- **Voice bottom sheet**: Existing modal animation + pulse during listening (already implemented).

### Timing constants (reuse `WallAnimations`)
- SHORT: 200ms, MEDIUM: 300ms, LONG: 350ms
- STAGGER_ENTER: 40ms, STAGGER_EXIT: 25ms

---

## 5. Calendar View — Layout + Polish (Wall Mode)

### New: Week Overview Strip
- Compact horizontal bar at top, 7 days visible
- Each day: abbreviated name (`labelSmall`) + date number (`titleSmall`)
- **Busy indicators**: Dots below date (max 3), urgency colors for promoted tasks, accent for regular
- **Selected day**: Background pill in `accentPrimary` at 15%, text in accent color
- **Today indicator**: Subtle underline or ring, always visible
- **Encoder**: When date bar focused, twist scrolls days, click selects
- Height: ~56dp

### New: Smart Hour Compression
- Auto-focus: Scroll to current time (or first event on future dates)
- **Compress empty ranges**: Consecutive empty slots collapse into single compact row ("7:00 – 11:00", subtle "no events" label). Tap/select expands back to individual slots.
- **Always show**: 2-hour window around current time is never compressed
- **Edge hours**: Before first event and after last event compressed by default
- **Expand transition**: 300ms stagger animation (matching phone accordion feel)

### Event Chip Improvements
- Replace `[T]` prefix with urgency-colored dot (4dp) on chip left
- Remove fixed 156dp width — size to content with max constraint
- Taller chips: 6dp vertical padding (was 3dp), allow 2-line text
- Selected state: subtle left accent border (3dp)

### Slot Improvements
- Variable height: 80dp with events, 56dp empty
- Current time dot: gentle pulse animation (opacity 0.4→0.7, 2s period)
- Selected slot: soft glow matching wall task card glow DNA
- Remove dead empty `Text("")` placeholder

### Time Label
- Use `labelMedium` (slightly smaller, more utility-like)
- Right-align for cleaner left edge

---

## 6. Shared Visual DNA — Cross-Mode Consistency

| Visual Motif | Wall Mode | Phone Mode |
|---|---|---|
| Accordion sections | Focus-driven expand/collapse | Tap-driven expand/collapse |
| Subtask connecting line | Always visible, never collapsed | Collapsible, same line style when expanded |
| Warm urgency left bar | On task cards | On task cards |
| Card surface treatment | `surfaceCard` bg, 16dp corners | `surfaceCard` bg, 12dp corners |
| Soft glow (selection) | Encoder focus = luminous halo | Calendar slot selection + pressed state |
| Due date badge | Shared `DueDateBadge` | Same shared composable |
| Completion animation | Drift-to-bottom + haptic | Drift-to-bottom + haptic + press scale |
| Color palette | Full `WallColors` system | Same `WallColors` system |
| Typography | Inter, weight-based hierarchy | Same Inter, same hierarchy |
| Animation timing | `WallAnimations` constants | Same constants |

### Deliberately different
- Phone has micro-animations (press scale, stagger enter, elastic scroll) — wall doesn't need these
- Phone allows multiple accordion sections open — wall auto-collapses on focus change
- Phone subtasks are collapsible — wall shows everything always
- Phone has bottom capture bar — wall uses encoder + voice overlay
