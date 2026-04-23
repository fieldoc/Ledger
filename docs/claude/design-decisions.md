# Design Decisions (Binding Specs)

Load when implementing or modifying any of the nine pillars below. These are **finalized** — implementation must follow them exactly. Treat any divergence as a bug.

### 1. Task Completion Behavior
Completed tasks **drift to the bottom** of the list. The completed card animates downward into a "Completed" section; pending tasks close the gap smoothly. Ease-in-out, ~300ms reorder.

### 2. Encoder Focus State: Gentle Glow
The focused card has a **soft, warm edge glow** — light coming from behind. Visible and unambiguous from 6+ feet. NOT a border highlight — a luminous halo. Implement via soft outer shadow/glow in the accent color, low opacity, moderate blur radius.

### 3. Task Hierarchy: Indented Children with Connecting Line
Subtasks are **indented under their parent with a subtle vertical connecting line on the left**. The full hierarchy is **always visible** — never collapsed or hidden. If subtasks hide behind a tap, they stay in the user's head.

### 4. Multiple Lists: Accordion Folders
All lists in one scrollable view as collapsible folder sections.
- **Collapsed**: title + first task as faded, truncated text (ellipsis trailing).
- **Expanded**: when encoder focus lands on the folder, it blooms open at full opacity. Focus leaves → settles back.
- **Ordering**: by urgency of children (nearest due date floats up). No due dates → most-recently-updated, then Google Tasks API order.
- **Transition**: ~300ms ease-in-out. Tasks fade in/out, not pop.

### 5. Voice Input: Full-Screen Dim + Waveform Visualizer
- Entire task list **dims** (similar to ambient).
- **Waveform visualizer** centered, responding to mic in real time.
- Style: thin lines, muted accent color, smooth motion. NOT a flashy equalizer.
- **No live transcription text** during listening — the wall just listens quietly.
- On end: waveform fades, screen returns, then the draft card appears (#6).

### 6. Voice Confirmation: Preview Draft Card
- A **draft task card** appears at the top of the current list, visually distinct from committed tasks (subtle "draft" indicator).
- Transcribed text shown.
- **Click** = commit (syncs to Google Tasks, settles into place).
- **Twist** = navigate to edit/cancel options.
- Waits for explicit user action — no auto-commit, no timeout.

### 7. Clock Header: Minimal Corner Utility
Small, tucked into a corner. Tasks are the star. `bodyMedium` or `labelLarge` text with date beneath; total height under ~48dp.

### 8. Urgency: Warm Temperature Shift
Overdue uses a **muted warm amber/terracotta** — not red, not alarming. Due-today is a slightly warmer neutral. Due-soon and normal stay cool/neutral. Warm accent example: `Color(0xFFB8866B)`. Use the `WallColors.accentWarm` token — there is **no** `urgencyWarm`. Full urgency tokens: `urgencyOverdue`, `urgencyDueToday`, `urgencyDueSoon`, `urgencyOverdueSubtle`.

### 9. Ambient Mode: Two-Tier System
**Tier 1 — Quiet Mode** (after 30s idle):
- Show only the next 2–3 upcoming/urgent tasks as faint text.
- Everything else fades. Low brightness but readable on approach.

**Tier 2 — Sleep Mode** (schedule or low ambient light):
- Screen fully dark (true black, OLED-friendly).
- Triggered by configurable sleep schedule (e.g. 11pm–7am) **or** ambient light sensing via the device camera.
- Encoder instantly wakes from either tier.
- Essential for bedroom installations.
