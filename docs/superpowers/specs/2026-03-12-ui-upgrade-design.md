# UI Upgrade Design — Soft Graphite Refresh

**Date**: 2026-03-12
**Status**: Approved
**Scope**: Full visual refresh across all screens — theme, wall display, ambient modes, voice, calendar, phone, settings

---

## 1. Core Design System

### 1.1 Color Palette — Soft Graphite

Replace the current Scandinavian blue-slate palette with true neutral grays. No blue or warm undertone in the gray surfaces — accents carry all the color.

**Dark theme surfaces:**

| Token | Hex | Role |
|-------|-----|------|
| `surfaceBlack` | `#0A0A0A` | Sleep mode, deepest background |
| `surfaceBackground` | `#121212` | Primary background |
| `surfaceCard` | `#1C1C1C` | Focused/filled card background |
| `surfaceElevated` | `#262626` | Elevated overlays, settings panel |
| `surfaceHigh` | `#303030` | Highest elevation (modals, menus) |

**Text hierarchy:**

| Token | Hex | Role |
|-------|-----|------|
| `textPrimary` | `#EEEEEE` | Task titles, primary content |
| `textSecondary` | `#BDBDBD` | Folder headers, descriptions |
| `textMuted` | `#757575` | Metadata, timestamps |
| `textDisabled` | `#555555` | Completed task text, disabled |
| `textFaint` | `#333333` | Peek text, collapsed hints |

**Border:**

| Token | Hex | Role |
|-------|-----|------|
| `borderDefault` | `#1E1E1E` | Unfocused card border |
| `borderSubtle` | `#1A1A1A` | Dividers, completed card border |
| `borderFocused` | `rgba(128,203,196,0.2)` | Focused card border |

### 1.2 Primary Accent — Mint Teal

| Token | Hex | Role |
|-------|-----|------|
| `accentPrimary` | `#80CBC4` | Focus glow, active checkbox, primary actions |
| `accentDeep` | `#4DB6AC` | Pressed/active states |
| `accentSubtle` | `#B2DFDB` | Subtle highlights, progress bar fill |
| `accentSurface` | `rgba(128,203,196,0.08)` | Tinted backgrounds (expand indicator, pills) |
| `accentGlow` | `rgba(128,203,196,0.18)` | Outer glow on focused cards |

### 1.3 Secondary Accent — Soft Rose

| Token | Hex | Role |
|-------|-----|------|
| `accentWarm` | `#CF8E8E` | Completed checkbox fill, completed divider |
| `accentWarmSubtle` | `rgba(207,142,142,0.08)` | Completed section divider line |

### 1.4 Urgency Colors (unchanged values, warm gradient)

| Token | Hex | Role |
|-------|-----|------|
| `urgencyOverdue` | `#C97C52` | Overdue badge text |
| `urgencyOverdueSubtle` | `rgba(201,124,82,0.15)` | Overdue badge background |
| `urgencyDueToday` | `#D4A06A` | Due today badge text |
| `urgencyDueTodaySubtle` | `rgba(212,160,106,0.12)` | Due today badge background |
| `urgencyDueSoon` | `#B8A88A` | Due soon badge text |
| `urgencyDueSoonSubtle` | `rgba(184,168,138,0.1)` | Due soon badge background |

The warmth gradient (rose → amber → terracotta) creates a coherent system where increasing warmth = increasing urgency.

### 1.5 Connectivity

| Token | Hex | Role |
|-------|-----|------|
| `connectivityOnline` | `#80CBC4` | Online indicator (matches teal) |
| `connectivityOffline` | `#CF8E8E` | Offline indicator (matches rose) |

### 1.6 Light Theme

Adapt all surface values for light mode:
- Backgrounds shift to warm linen tones (`#FAFAF8`, `#F5F4F0`, `#EDECE8`)
- Text shifts to dark warm grays (`#1A1A1A`, `#4A4A4A`, `#888888`)
- Accents stay the same but at slightly increased saturation
- `isDark` boolean in `WallColors` controls which set is active

---

## 2. Typography — Plus Jakarta Sans

Replace Inter with Plus Jakarta Sans throughout.

**Font source**: Google Fonts (downloadable via GMS, same mechanism as current Inter setup)

**Weight mapping:**
- Light (300): Display sizes, clock, ambient mode
- Regular (400): Body text, task titles
- Medium (500): Folder headers, badges, emphasized text
- SemiBold (600): Section labels, settings group headers
- Bold (700): Reserved for exceptional emphasis only

**Scale**: Keep existing sp values from `Type.kt`. The tighter metrics of Plus Jakarta Sans will naturally gain ~5-8% horizontal space per line at equal sizes.

**Implementation**: Update `Type.kt` to reference `PlusJakartaSans` font family. Update `GoogleFont` provider reference.

---

## 3. Surface Treatment — Hybrid Border/Elevation

### 3.1 Unfocused Cards (Resting State)
- `background`: transparent
- `border`: 1px solid `borderDefault` (#1E1E1E)
- `borderRadius`: 10dp (slight reduction from current 12dp for tighter feel)
- No shadow

### 3.2 Focused Card (Encoder Selected)
- `background`: `surfaceCard` (#1C1C1C) — fills in
- `border`: 1px solid `borderFocused`
- `shadow`: three-layer composite:
  - Glow: `0 0 14dp 3dp accentGlow`
  - Ambient glow: `0 0 28dp 6dp rgba(128,203,196,0.05)`
  - Drop shadow: `0 4dp 10dp rgba(0,0,0,0.3)`
- `translationY`: -1dp (subtle lift)
- `transition`: 300ms cubic-bezier(0.4, 0, 0.2, 1) on all properties

### 3.3 Completed Cards
- `opacity`: 0.35
- `border`: 1px solid `borderSubtle` (#1A1A1A)
- Checkbox filled with `accentWarm`
- Text: `textDisabled` with line-through (`textFaint` color for the line)

### 3.4 Subtask Cards
- Same border treatment, smaller padding (10dp vertical, 12dp horizontal)
- Font size: one step smaller than parent tasks
- Connecting line: 1px `#252525` (slightly lighter than current)

---

## 4. Wall Display Upgrades

### 4.1 Folder Headers
- Text: 11sp, SemiBold (600), `textSecondary`, uppercase, letter-spacing 0.8sp
- Task count: 10sp, Regular, `textMuted`, lowercase
- Expand indicator: 14×14dp rounded rect, `accentSurface` background, small arrow in `accentPrimary`

### 4.2 List Progress Bars (NEW)
- Position: directly below folder header, above first task
- Height: 2dp, border-radius 1dp
- Track: `borderDefault` (#1E1E1E)
- Fill: `accentSubtle` at 30% opacity
- Width: percentage of completed tasks in the list
- Animation: smooth width transition on task completion (~300ms)

### 4.3 Focus Breadcrumb (NEW)
- Position: top of wall, above clock
- Text: 9sp, `textMuted`, letter-spacing 0.3sp
- Format: `ListName › TaskTitle` (current folder and focused task name)
- The list name portion uses `accentPrimary` color
- Fades in when navigating (alpha 0→1 over 200ms), fades to 0 in quiet mode
- Only visible while actively navigating (hides after 3s idle, re-appears on encoder input)

### 4.4 Folder Urgency Glow (NEW)
- When a collapsed folder contains overdue tasks, the folder header gets a faint warm edge glow
- Implementation: subtle outer shadow in `urgencyOverdue` at ~10% opacity, 8dp blur
- Only on collapsed folders — expanded folders show the urgency badges on individual tasks
- Provides at-a-glance urgency scanning without expanding every list

### 4.5 Completed Section Divider
- Thin line: 1px solid `accentWarmSubtle`
- Label: "Completed", 9sp, `accentWarm`, uppercase, letter-spacing 0.8sp, 45% opacity
- Appears above the first completed task in each list

---

## 5. Focus & Navigation Upgrades

### 5.1 Completion Ripple (NEW)
- When marking a task done via encoder click:
  1. A subtle radial ripple emanates from the checkbox outward
  2. Color transitions from `accentPrimary` (teal) to `accentWarm` (rose) over 200ms
  3. Ripple fades at the card edge
  4. Simultaneously: haptic pulse (existing), card begins drift-to-bottom animation (existing)
- Implementation: Canvas-based ripple effect in Compose, triggered on completion
- Duration: 200ms for the ripple, 300ms for the card reorder animation (existing)

### 5.2 Staged Wake Reveal (NEW)
- When transitioning from quiet/sleep mode to active:
  1. Most urgent task fades in first (alpha 0→1 over 200ms)
  2. Each subsequent task staggers by 40ms (existing `STAGGER_ENTER` token)
  3. Folder headers appear before their children
  4. Clock and breadcrumb appear last
- Creates a "waking up" sensation that mirrors the user approaching the wall

---

## 6. Ambient Mode Upgrades

### 6.1 Quiet Mode Clock (NEW)
- When in quiet mode (30s idle), display a large faint time in the upper area
- Font: 32sp, Light (300), `textFaint` (#1A1A1A — just barely visible)
- Position: left-aligned, above the whispered task list
- The clock provides ambient utility without dominating

### 6.2 Quiet Mode Tasks (Refined)
- Show 2-3 most urgent tasks as faint text (existing behavior)
- Add: thin left border accent — teal border line `rgba(128,203,196,0.08)` on each whispered task
- Add: urgency badge text appears at 50% opacity next to overdue items (e.g., "overdue" in `urgencyOverdue` color, very faint)

---

## 7. Voice Input Upgrade

### 7.1 Radial Voice Pulse (NEW — replaces bar waveform)
- **Central dot**: 48dp diameter circle
  - Background: radial gradient from `rgba(128,203,196,0.25)` center to `rgba(128,203,196,0.08)` edge
  - Border: 1px `rgba(128,203,196,0.2)`
  - Inner dot: 16dp, `rgba(128,203,196,0.4)`, breathing animation (scale 1→1.2, opacity 0.6→1, 1.5s ease-in-out infinite)
- **Pulse rings**: 3 concentric rings expanding outward
  - Start: 0.8× scale of central dot, 60% opacity
  - End: 2.5× scale, 0% opacity
  - Duration: 2s ease-out, staggered 600ms apart
  - Ring style: 1px border `rgba(128,203,196,0.1)`
- **Label**: "Listening", 13sp, Light (300), `textMuted`, below the dot
- **Background**: full-screen dim to `surfaceBlack` at ~85% opacity (existing behavior)
- Replaces the current 28-bar waveform visualizer. The radial approach is calmer, more premium, and doesn't require real-time amplitude data to look good (the bars can feel empty when audio is quiet).

---

## 8. Calendar Upgrades

### 8.1 Calendar Urgency Dots (NEW)
- Event dots on calendar day cells use the urgency color system:
  - Normal events: `accentPrimary` (teal) at 60% opacity
  - Overdue task due dates: `urgencyOverdue` at 60% opacity
- Dot size: 4dp (slight increase from current 3dp)
- Visible from across the room — glanceable urgency scanning

### 8.2 Calendar Surface Alignment
- Day cells: borderline treatment (transparent background, 1px border on today/selected)
- Today indicator: `borderFocused` border + `accentSurface` fill + teal text
- View mode pills: borderline style with `accentSurface` fill on active pill
- Day label row: 8sp uppercase, `textDisabled`, letter-spacing 1sp

---

## 9. Phone Companion Upgrades

### 9.1 Mic-First Capture Bar (NEW)
- Mic button: 36dp diameter, `accentSurface` background, `borderFocused` border, teal dot center
- Camera button: 36dp diameter, `rgba(255,255,255,0.03)` background, `borderDefault` border, smaller and secondary
- Mic button is visually larger/more prominent than camera — voice is the primary capture method
- Capture bar separator: 1px `borderSubtle`

### 9.2 Phone Surface Alignment
- Apply same borderline card treatment as wall (transparent + thin border)
- Same font (Plus Jakarta Sans), same color tokens
- Top bar: 12sp SemiBold header, single settings icon
- Urgency badges appear on phone task items (same compact badge style)

---

## 10. Settings Panel Upgrades

### 10.1 Grouped Sections (NEW)
- Settings organized into labeled groups: **Appearance**, **Schedule**, **Sync**, **Account**
- Group labels: 9sp, SemiBold (600), uppercase, letter-spacing 1.5sp, `textMuted`
- Each setting item can have a description line: 10sp, `textDisabled`

### 10.2 Settings Surface Alignment
- Item separators: 1px `borderSubtle` (replacing current blue-slate dividers)
- Value displays: `accentPrimary` text in pill-shaped background (`accentSurface`)
- Toggle switches: track in `accentSurface`, thumb in `accentPrimary` when on
- Encoder navigation: focused setting item gets same borderline→elevated treatment as task cards

---

## Files to Modify

### Theme layer (design system):
- `ui/theme/Color.kt` — Replace all color values with Soft Graphite palette
- `ui/theme/Type.kt` — Swap Inter for Plus Jakarta Sans
- `ui/theme/Theme.kt` — Update WallColors data class if token names change

### Wall display:
- `ui/screens/TaskWallScreen.kt` — Breadcrumb, progress bars, folder urgency glow, staged wake reveal, quiet mode clock
- `ui/components/TaskItem.kt` — Hybrid surface treatment, completion ripple animation
- `ui/components/ClockHeader.kt` — Palette alignment

### Voice:
- `ui/components/WaveformVisualizer.kt` — Replace bar waveform with radial pulse (likely rename to `VoicePulseVisualizer.kt`)

### Calendar:
- `ui/components/CalendarMonthView.kt` — Urgency dots, surface alignment
- `ui/components/CalendarWeekView.kt` — Surface alignment

### Phone:
- `ui/screens/PhoneHomeScreen.kt` — Surface alignment
- `ui/components/PhoneTaskItem.kt` — Borderline treatment
- `ui/components/PhoneCaptureBar.kt` / `PhoneCaptureHub.kt` — Mic-first layout

### Settings:
- `ui/components/SettingsPanel.kt` — Grouped sections, descriptions, surface alignment

---

## Animation Tokens (unchanged)

| Token | Value | Usage |
|-------|-------|-------|
| `SHORT` | 200ms | Ripple, fade transitions |
| `MEDIUM` | 300ms | Card focus, reorder, expand/collapse |
| `LONG` | 350ms | Screen transitions, wake reveal |
| `STAGGER_ENTER` | 40ms | List item stagger on wake |
| `STAGGER_EXIT` | 25ms | List item stagger on quiet mode |

All animations use `cubic-bezier(0.4, 0, 0.2, 1)` (Material standard decelerate) unless noted.
