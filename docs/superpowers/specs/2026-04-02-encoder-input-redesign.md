# Encoder Input Redesign: Double-Click + Header Voice Button

**Date:** 2026-04-02
**Status:** Approved

## Problem

The rotary encoder has exactly 3 physical inputs: rotate CW, rotate CCW, single click. The current code overloads the click with hold-timing detection (350ms promote, 350-800ms context menu, 800ms+ voice input), but **hold detection does not work on the physical encoder hardware**. This makes voice input, context menu, and promote actions unreachable.

Additionally, the codebase docs (CLAUDE.md, memory files) contain references to long-press/hold as valid inputs, causing AI assistants to repeatedly suggest hold-based solutions.

## Design

### 1. Voice Input: Header Button (Always Visible)

Add a focusable **microphone/voice button** to the `ClockHeader`, positioned at the **far left**.

**Layout:** `[Voice Button]` — `[Clock / Date]` — `[Settings Button]`

**Focus order:**
- Rotating up past the first task → focus lands on Voice button
- Rotating down from Voice button → focus returns to first task
- Click on focused Voice button → activates voice capture (existing `startVoiceWithPermission()` flow)

**Visual style:**
- Subtle mic icon, same visual weight as the existing settings gear
- No pulsing, no animation in idle state — consistent with calm UI philosophy
- When focused: same gentle glow treatment as task cards

### 2. Context Menu: Double-Click

Replace all hold-timing logic with **double-click detection** on focused tasks.

- **Single click** = toggle task completion (unchanged, most common action stays fast)
- **Double-click** = open context menu on the focused task

**Double-click timing:**
- Configurable window via a constant (default: 350ms, matching current `PROMOTE_DELAY_MS`)
- Extract to a named constant `DOUBLE_CLICK_WINDOW_MS` for easy tuning after hardware testing
- First click starts a timer. Second click within the window → context menu. Timer expires → single-click action fires.

**Context menu actions** (unchanged):
- Schedule (all tasks)
- Restore (completed tasks only)
- Delete (all tasks)

### 3. Remove All Hold/Long-Press Detection

Strip out the entire hold-timing state machine from `TaskWallScreen.kt`:
- Remove `HOLD_TO_TALK_DELAY_MS`, `PROMOTE_DELAY_MS` constants (replace with `DOUBLE_CLICK_WINDOW_MS`)
- Remove `holdStartTime`, `holdProgress`, `holdToTalkActive`, `promoteTriggered` state
- Remove the coroutine-based hold progress animation
- Remove medium-hold context menu trigger
- Simplify the ENTER KeyDown/KeyUp handler to just double-click detection

### 4. Docs Audit

Audit and patch all project documentation to:
- Remove references to long-press, hold-to-talk, medium-hold as valid inputs
- Clarify that the encoder supports only: rotate CW, rotate CCW, single click
- Document the new double-click and header voice button patterns
- Update memory files that mention hold detection

**Files to audit:**
- `CLAUDE.md` (root and worktree)
- `.claude/projects/*/memory/MEMORY.md` and individual memory files
- Any in-code comments referencing hold gestures

## Out of Scope

- The V, S, R, Escape keyboard handlers — these don't exist in the current code (already resolved)
- Changing the context menu's visual design or action set
- Touch input changes (touch still works independently of encoder)

## Success Criteria

- Voice input activatable via encoder: rotate to header → click voice button
- Context menu activatable via encoder: double-click on any focused task
- Task completion remains single-click (no regression on most common action)
- Zero references to hold/long-press as valid encoder inputs in docs
- Build compiles successfully
