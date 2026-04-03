---
name: auto-dev
user_invocable: true
description: Autonomous feature development for the ToDoWallApp. Use this skill when the user asks Claude to implement one or more features, work autonomously on a list of tasks, build something end-to-end, or says things like "just build it", "go implement this", "work on these features", "do this automatically", or "don't ask me questions, just do it". Handles full plan → implement → review → build cycles without interrupting the user.
---

# Autonomous Feature Development

Work fully autonomously on the features provided. Do NOT ask the user questions — make reasonable decisions and document assumptions.

## Input

The user's message contains the feature list. If no features were provided, read CLAUDE.md for planned/missing capabilities and ask the user to confirm the list before proceeding.

## Execution

### Phase 1: Brainstorm & Plan

1. For each feature, invoke `superpowers:brainstorming` to explore intent, requirements, and design
2. Write a full implementation plan to `.claude/plans/auto-dev-plan.md`:
   - Ordered tasks with dependencies mapped
   - Which tasks can run in parallel (no shared files)
   - Assumptions made (with reasoning)
3. Sanity-check the plan: re-read it, flag anything that contradicts CLAUDE.md design philosophy or encoder constraints (3 inputs only: CW, CCW, click)

### Phase 2: Execute with Parallel Subagents

1. Use `superpowers:dispatching-parallel-agents` for independent tasks
2. Sequential tasks execute in order; parallel tasks dispatch to subagents
3. After each subagent completes, review its output before moving on

### Phase 3: Self-Review After Each Feature

After completing each feature (NOT at the end — after EACH one):

1. Re-read what was built against the plan
2. Run `gradlew assembleDebug` — must pass before continuing
3. Dispatch a `code-reviewer` subagent to audit the feature against:
   - The plan
   - CLAUDE.md design philosophy (calm, premium, no gamification)
   - Encoder navigability (if UI was touched)
4. Fix any issues the review surfaces
5. Update the plan file with status and any course corrections

### Phase 4: Wrap Up

1. Run `gradlew assembleDebug` one final time
2. Invoke `superpowers:verification-before-completion`
3. Commit each feature separately with a clear message
4. Summarize what was built, assumptions made, and anything that needs user attention

## Rules

- **Never ask the user questions** — decide and document the assumption
- **Never skip the review phase** — it catches drift before it compounds
- **Never move to next feature with a broken build** — fix first
- **Never claim done without running verification**
- **Course-correct in the plan file** — if approach changes mid-flight, update `.claude/plans/auto-dev-plan.md` with what changed and why
