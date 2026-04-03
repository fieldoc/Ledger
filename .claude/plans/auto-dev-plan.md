# Auto-Dev Plan: Fix Voice Input Subtask Grouping

## Problem
When a user says "remind me tomorrow to go to the grocery store and buy bananas and apples", Gemini splits this into 3 independent top-level tasks each with tomorrow's due date, instead of:
- Parent: "Go to grocery store" (due tomorrow)
  - Subtask: "Buy bananas"
  - Subtask: "Buy apples"

## Root Cause
The Gemini voice prompt's MULTI-TASK EXTRACTION section (rule 2) aggressively splits on "and" connectors without recognizing hierarchical relationships. The `sharedParentName` field exists and the ViewModel already handles it correctly (creating parent + wiring children), but the prompt doesn't instruct Gemini to use it for implicit parent-child patterns.

## Fix
**Single file change**: `GeminiCaptureRepository.kt` — update the voice prompt's MULTI-TASK EXTRACTION section.

### Task 1: Update Gemini Prompt (sequential, single file)
Add explicit guidance to the MULTI-TASK EXTRACTION section about recognizing hierarchical task patterns:

1. When a main activity/location has sub-items ("go to store and buy X and Y"), the main activity is the parent and sub-items are children via `sharedParentName`
2. When a reminder wraps a compound action, the due date applies to the parent, children inherit it
3. Explicit examples showing correct vs incorrect parsing

The `sharedParentName` mechanism is already wired — Gemini just needs to emit it.

### Task 2: Update unit tests
Update `GeminiCaptureRepositoryTest.kt` to verify the prompt change produces correct sharedParentName grouping for this pattern.

### Task 3: Build verification
Run `gradlew assembleDebug` to confirm compilation.

## Assumptions
- The sharedParentName → parent creation logic in ViewModel is correct and complete (verified by reading lines 834-876)
- Children should inherit the parent's due date (set by Gemini in the response)
- This is a prompt-only fix — no structural code changes needed

## Status
- [ ] Task 1: Update Gemini prompt
- [ ] Task 2: Update tests
- [ ] Task 3: Build verification
