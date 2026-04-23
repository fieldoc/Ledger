# Design Philosophy & UX Principles

Load when touching anything under `ui/`, theming, animation, typography/spacing, encoder UX, or task-completion feel. The core promise is "calm productivity" — a wall device that holds complexity without shouting.

## Visual Identity

**The UI communicates:**
- **Calm productivity** — a quiet confidence. Feels like a well-organized desk, not a busy dashboard.
- **Controlled depth** — subtle elevation, soft shadows, layering that implies physicality without shouting.
- **Slight tactility without playfulness** — surfaces feel real and considered. Matte, not glossy bouncing buttons.
- **Enterprise-ready but human** — professional enough for an office wall, warm enough for a kitchen.

**The UI is NOT:**
- Experimental, trendy, or highly animated
- Visually loud or high-contrast for its own sake
- Gamified (no badges, streaks, rewards, dopamine hooks)

## Motion & Transitions

- Fluid and elegant, never flashy. Every animation must guide attention or confirm an action.
- Subtle easing curves (ease-in-out, decelerate) over springy/bouncy physics.
- Duration sweet spot: **200–350ms**. Nothing should feel sluggish or snappy.
- State changes (completion, list switch, sync) should feel like inevitable progressions, not events.

## Typography & Spacing

- Generous whitespace. Let content breathe.
- Typographic hierarchy does the heavy lifting — not color or iconography.
- Favor weight and size contrast over color contrast.

## Color & Surface

- Muted, sophisticated palette. No saturated primaries competing for attention.
- Urgency indicators are understated — quiet warmth for overdue, not an alarm.
- Dark and light themes both feel intentional and complete, not one derived from the other.

## Interaction Design

- Display-first device — interactive moments must feel deliberate and premium.
- Generous touch targets (wall mounted = less precision).
- Feedback is immediate but restrained — a gentle state shift, not a celebration.

## Rotary Encoder Input (Physical Hardware)

ESP32 BLE HID emulating a keyboard. **Only 3 inputs exist:** rotate CW, rotate CCW, click. There are no letter keys, no Escape, no Shift. Never propose keyboard shortcuts that require any other key — they are unreachable.

**Mapping:**
- Rotate → scroll/navigate (CW = down/right, CCW = up/left)
- Click → select / confirm / toggle completion
- Double-click on focused task → context menu (configurable: `DOUBLE_CLICK_WINDOW_MS`, default 350ms)
- **Hold/long-press is NOT supported in hardware** — all hold-timing code was removed 2026-04-02.

**Design implications:**
- The UI must always show a clear, visible focus state. The user must always know which item the encoder points at.
- Focus moves smoothly with the physical rotation. One detent = one item. No drift.
- All core actions must be reachable via sequential focus + click. Touch is secondary.
- Focus order (wall mode): ViewSwitcher → Voice button → Settings button → folder headers → tasks.

## Task Completion Feel

Marking a task done is a **mini reward** — small, satisfying, premium. Think Apple haptic.

- **Haptic**: short, crisp pulse — a *thunk*, not a buzz. `HapticFeedbackConstants` or a tuned `VibrationEffect`.
- **Visual**: confident state change — task gently contracts, text softens, surface settles into "done". Like something clicking into place, not disappearing.
- **Avoid**: green checkmarks, confetti, particle effects, celebratory sounds, anything resembling a mobile-game reward.
- **Timing**: haptic, visual, and encoder click land **simultaneously**. Any delay breaks the illusion.
