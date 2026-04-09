# UX Evolution Loop — Design Spec

**Date:** 2026-04-02
**Status:** Not Implemented (meta-design for future use)
**Scope:** Autonomous iterative UX/UI improvement system for ToDoWallApp

---

## 1. Overview

An autonomous loop that iteratively improves the ToDoWallApp's UX/UI through 10 cycles of creation and review, using separate agents for implementation and critique. The system is anchored by an immutable design constitution that prevents aesthetic drift ("deep-frying") across iterations.

**Two phases:**
- **Phase 1 — Web Prototype (10 iterations):** Rapid visual exploration using HTML/CSS/JS mockups via the `frontend-design` skill. No risk to the real codebase.
- **Phase 2 — Compose Port (3-5 iterations):** Faithful translation of the winning web design into real Android Jetpack Compose code. Human-triggered after Phase 1 review.

---

## 2. Design Constitution

The constitution is an immutable anchor document. Both the improver and reviewer agents receive it in their prompts. It is never modified across iterations.

### 2.1 Six Non-Negotiable Principles

1. **Calm over clever** — The wall reduces anxiety. No element should demand attention. Urgency whispers (warm amber), it never screams (red alerts). If a change makes the wall feel "busier," it fails.

2. **Hierarchy through typography and weight, not color** — Size, weight, and spacing do the heavy lifting. Color is a supporting actor, never the lead. Saturated primaries are banned.

3. **The wall holds complexity so the mind doesn't** — All subtasks visible (never collapsed). Accordion folders peek at contents. Information is always accessible without interaction.

4. **Motion is inevitable, not eventful** — Animations feel like gravity, not fireworks. Ease-in-out, 200-350ms. Task completion settles into place. Nothing bounces, springs, or celebrates.

5. **Ambient-first** — The default state is quiet. The display should look good from 10 feet away with zero interaction. Readability at distance trumps information density up close.

6. **Premium tactility without playfulness** — Surfaces feel real (matte, layered, subtle shadows). Nothing is glossy, gamified, or cartoonish. Think high-end stationery, not a mobile app.

### 2.2 Hard Constraints (Instant Rejection)

- No saturated red, green, or blue as primary/accent colors
- No bounce/spring physics animations
- No gamification elements (badges, streaks, confetti, particle effects)
- No fonts from the banned list (Inter, Roboto, Arial, system-ui, sans-serif)
- Clock/date must remain small and cornered (< 48dp equivalent)
- Subtask hierarchy must always be visible (never collapsed or hidden behind interaction)
- No always-listening indicators or pulsing mic icons
- No glossy/reflective surface treatments

---

## 3. Loop Architecture

### 3.1 Iteration Flow

```
for i in 0..9:
  1. Orchestrator assigns focus area for iteration i+1
  2. Spawn IMPROVER agent
     - Input: v{i}.html + reviewer feedback from iteration i + constitution + focus area
     - Constraint: changes MUST stay within the assigned focus area
     - Output: new HTML saved as v{i+1}.html
  3. Spawn REVIEWER agent
     - Input: v{i+1}.html + v0.html (frozen baseline) + constitution
     - Scores 6 axes (1-5 each, mapped to constitution principles)
     - Measures drift from baseline (1-5 scale)
     - Output: structured review JSON
  4. Decision gate:
     - If any axis score < 2: REVERT to v{i}, retry with different approach
     - If drift score > 4: REVERT to v{i}, retry with more restraint
     - If 2 consecutive reverts on same iteration: STOP loop (escalate to human)
     - Otherwise: save snapshot, log scores, continue to next iteration
```

### 3.2 Focus Rotation Schedule

Each iteration targets one specific axis. The improver must preserve everything outside its focus area. Changes outside the focus area trigger automatic reviewer rejection.

| Iteration | Focus Area | What Can Change |
|-----------|-----------|-----------------|
| 1 | Typography & font pairing | Font families, sizes, weights, line heights, letter spacing |
| 2 | Color palette & surface treatment | Background colors, card surfaces, shadows, gradients, opacity |
| 3 | Spacing, density & layout rhythm | Margins, padding, gaps, content density, whitespace distribution |
| 4 | Task card design & hierarchy | Card shape, subtask indentation, connecting lines, completion states |
| 5 | Motion & transition curves | Animation durations, easing, transition properties, state changes |
| 6 | Ambient/quiet mode appearance | Dimmed state, reduced information density, sleep mode visuals |
| 7 | Voice overlay & waveform | Listening state, waveform style, dim overlay, draft card appearance |
| 8 | Calendar integration visuals | Month/week/day views, event chips, date navigation |
| 9 | Micro-interactions & focus states | Hover/focus glow, selection indicators, encoder feedback visuals |
| 10 | Holistic polish pass | Refinement only. No new ideas. Harmonize the 9 previous changes. |

### 3.3 Decision Gate Rules

The gate prevents runaway drift:

- **Score threshold:** Any single axis dropping below 2/5 = automatic revert. The change made things actively worse on a constitutional principle.
- **Drift threshold:** Drift score > 4/5 on a single iteration = too much change at once. Revert and retry with more restraint.
- **Consecutive revert limit:** 2 reverts on the same iteration number = the loop is stuck. Stop and surface the problem to the human rather than spinning.
- **Retry behavior:** On revert, the improver receives the rejection reason and must try a different approach, not the same one harder.

---

## 4. Agent Specifications

### 4.1 Improver Agent

- **Agent type:** `general-purpose` (requires file write access)
- **Skill invoked:** `frontend-design:frontend-design` for each iteration
- **Prompt structure:**
  1. The full design constitution (principles + hard constraints)
  2. The focus area for this iteration (from the rotation schedule)
  3. The previous iteration's HTML (as the starting point)
  4. The reviewer's feedback from the previous iteration (what worked, what to improve, what to avoid)
  5. Explicit instruction: "You may ONLY change elements related to [focus area]. Everything else must remain identical."
- **Output:** A single self-contained HTML file saved as `docs/ux-evolution/v{N}.html`
- **Target viewport:** 1280x800 (landscape tablet, approximating wall-mounted display)

### 4.2 Reviewer Agent

- **Agent type:** `general-purpose` (with a design-review prompt; does not edit files, only reads and scores)
- **Prompt structure:**
  1. The full design constitution
  2. The current iteration's HTML (`v{i+1}.html`)
  3. The baseline HTML (`v0.html`)
  4. The assigned focus area for this iteration
  5. Instruction: "Score the iteration, measure drift, and produce a structured verdict."

**Scoring rubric (6 axes, mapped 1:1 to constitution principles):**

| Axis | Principle | 1 (Fail) | 3 (Acceptable) | 5 (Excellent) |
|------|-----------|----------|-----------------|---------------|
| Calm | #1 Calm over clever | Feels busy or anxious | Neutral, inoffensive | Genuinely calming |
| Hierarchy | #2 Typography-led hierarchy | Flat, unclear structure | Readable | Eye flows effortlessly |
| Complexity-holding | #3 Wall holds complexity | Info hidden or collapsed | Visible but cluttered | All visible, feels spacious |
| Motion | #4 Inevitable motion | Bouncy, flashy, or absent | Functional transitions | Smooth, inevitable, satisfying |
| Ambient presence | #5 Ambient-first | Looks broken from 10ft | Readable at distance | Beautiful at distance |
| Premium feel | #6 Premium tactility | Cheap or gamified | Professional | High-end physical object |

**Drift score (separate from the 6 axes):**

| Score | Meaning |
|-------|---------|
| 1 | Nearly identical to baseline |
| 2 | Noticeable but conservative changes |
| 3 | Clear evolution, same family |
| 4 | Significant departure, still recognizable |
| 5 | Unrecognizable from baseline |

**Output format (JSON):**

```json
{
  "iteration": 3,
  "focus_area": "Spacing, density & layout rhythm",
  "scores": {
    "calm": 4,
    "hierarchy": 4,
    "complexity_holding": 3,
    "motion": 4,
    "ambient_presence": 5,
    "premium_feel": 4
  },
  "drift_from_baseline": 3,
  "focus_compliance": true,
  "verdict": "ACCEPT",
  "feedback": {
    "worked_well": "Generous whitespace between accordion sections creates breathing room...",
    "needs_improvement": "Task card internal padding could be more generous...",
    "avoid_next": "Don't reduce the peek text size further — it's at the readability limit..."
  }
}
```

**Verdict values:**
- `ACCEPT` — Iteration passes. Proceed to next.
- `REVERT` — Iteration fails. Roll back to previous version, retry with feedback.
- `STOP` — Unrecoverable problem. Halt the loop and escalate to human.

### 4.3 Orchestrator (Not a Separate Agent)

The orchestrator is the skill itself, running in the main Claude Code session. It:

1. Creates the `docs/ux-evolution/` directory and `constitution.md`
2. Generates `v0.html` (baseline) before the loop starts
3. For each iteration: spawns improver, waits, spawns reviewer, waits, applies decision gate
4. Logs all scores and feedback to `review-log.md`
5. On loop completion: summarizes the evolution and presents the final state

---

## 5. Baseline (v0)

The baseline is a faithful HTML/CSS recreation of the current wall UI as it exists in Compose. It must capture:

- Dark Scandinavian palette (slate blues, muted surfaces)
- Accordion folder layout with collapsed peek text
- Task cards with subtask hierarchy (indented + vertical connecting line)
- Soft warm glow focus state on selected card
- Small cornered clock/date header (< 48dp equivalent)
- Warm amber urgency tones for overdue/due-today
- Plus Jakarta Sans typography (or closest web equivalent)
- Landscape tablet viewport (1280x800)
- Voice overlay state (dimmed background + waveform placeholder)
- Calendar month view with event dots

v0 is generated once by the improver agent (with explicit "recreate faithfully, do not improve" instructions) and then frozen. It is never modified.

---

## 6. File Structure

```
docs/ux-evolution/
  constitution.md          # The 6 principles + hard constraints (immutable)
  focus-schedule.md        # Iteration-to-focus mapping
  v0.html                  # Frozen baseline
  v1.html                  # Iteration 1 output
  v2.html                  # Iteration 2 output
  ...
  v10.html                 # Final iteration output
  review-log.md            # Chronological log of all reviewer scores + feedback
```

---

## 7. Phase 2 — Compose Port

Triggered manually by the user after reviewing Phase 1 output. The user picks the best web iteration (not necessarily v10) as the "target spec."

### 7.1 Differences from Phase 1

| Aspect | Phase 1 (Web) | Phase 2 (Compose) |
|--------|--------------|-------------------|
| Iterations | 10 | 3-5 |
| Skill | `frontend-design` | Direct Kotlin/Compose editing |
| Output | HTML files | Git commits |
| Improver focus | Creative exploration | Faithful translation |
| Extra reviewer axis | N/A | Fidelity (match to web target) |
| Build gate | N/A | `gradlew assembleDebug` must pass |

### 7.2 Compose Focus Areas

| Iteration | Focus |
|-----------|-------|
| 1 | Theme tokens: `Color.kt`, `Type.kt`, `Theme.kt` |
| 2 | Component styling: `TaskItem.kt`, `ClockHeader.kt`, `SharedTaskPrimitives.kt` |
| 3 | Layout & spacing: `TaskWallScreen.kt`, `LayoutDimensions.kt` |
| 4 | Motion: animation specs, transition curves across all composables |
| 5 | Polish: ambient mode, voice overlay, calendar views |

### 7.3 Fidelity Scoring (Additional Axis for Phase 2)

| Score | Meaning |
|-------|---------|
| 1 | Completely different from web target |
| 3 | Same spirit, details diverge |
| 5 | Pixel-accurate translation |

Target: fidelity >= 4 for each iteration.

### 7.4 Build Gate

After each Compose iteration, `gradlew assembleDebug` must succeed. Build failure = automatic revert, no reviewer needed.

---

## 8. Safety & Termination

- **Maximum retries per iteration:** 2 (after 2 consecutive reverts, the loop stops)
- **Maximum total iterations:** 10 for Phase 1, 5 for Phase 2 (hard caps)
- **Constitution is immutable:** No agent can modify `constitution.md`. If an agent suggests modifying the constitution, the orchestrator ignores the suggestion.
- **Baseline is frozen:** `v0.html` is never overwritten. If an agent modifies it, the orchestrator restores it.
- **Human escalation:** On STOP verdict or consecutive revert limit, the orchestrator pauses and surfaces the problem with full context.

---

## 9. Invocation

The system will be packaged as a custom skill (`ux-evolution-loop`) invocable via:

```
/ux-evolution-loop
```

The skill handles both phases. Phase 1 starts automatically. Phase 2 requires explicit user confirmation after Phase 1 review.
