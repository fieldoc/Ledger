# Voice Pipeline V2: Conversational Intent Engine

**Date**: 2026-03-15
**Status**: Approved

## Summary

Upgrade the Gemini-powered voice parsing pipeline from single-task extraction to a full conversational intent engine. Users can speak naturally and verbosely; the pipeline strips speech to actionable intent, extracts multiple tasks, classifies intent type, detects duplicates, and resolves fuzzy temporal expressions.

## Design Decisions (from brainstorming)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Multi-task confirmation | Batch confirm (A) | Minimal interaction, calm wall UX |
| Intent execution | Parse + confirm (B) | Shows what will happen, waits for click |
| Confidence behavior | Informational only (C) | No auto-commit; drives clarification logic |
| Duplicate detection UI | Soft warning (B) | Non-blocking "similar to: X" hint |
| Temporal reasoning | Date + time preference (B) | `preferredTime` field for future Day Organizer |

## Data Model

### New types (replace `ParsedVoiceTask`)

```kotlin
enum class VoiceIntent { ADD, COMPLETE, RESCHEDULE, DELETE, QUERY, AMEND }

enum class PreferredTime { MORNING, AFTERNOON, EVENING }

data class ParsedVoiceTaskItem(
    val title: String,
    val dueDate: LocalDate?,
    val preferredTime: PreferredTime?,
    val targetListId: String?,
    val parentTaskId: String?,
    val confidence: Float,       // 0.0-1.0
    val duplicateOf: String?     // existing task ID if near-duplicate detected
)

data class ParsedVoiceResponse(
    val intent: VoiceIntent,
    val tasks: List<ParsedVoiceTaskItem>,
    val clarification: String?,
    val rawTranscript: String
)
```

### VoiceInputState.Preview changes

```kotlin
// Before:
data class Preview(
    val transcribedText: String,
    val dueDate: LocalDate? = null,
    val targetListId: String? = null,
    val clarification: String? = null
)

// After:
data class Preview(
    val response: ParsedVoiceResponse
)
```

## Prompt Engineering

### New `buildVoicePrompt` structure

The prompt will be restructured with these sections:

1. **Role & context**: date, time, day of week, existing lists, existing tasks
2. **Intent classification**: Explicit rubric for ADD/COMPLETE/RESCHEDULE/DELETE/QUERY/AMEND with examples
3. **Multi-task extraction**: Instructions to split compound utterances into discrete tasks
4. **Conversational noise stripping**: "Extract actionable intent. Reduce to shortest imperative phrase." with before/after examples
5. **Temporal reasoning rubric**: Explicit mappings for fuzzy expressions ("this weekend" → Saturday, "end of week" → Friday, "in a couple days" → today+2, "first thing Monday" → Monday + MORNING)
6. **Duplicate detection**: "Check each extracted task against existing tasks. If semantically equivalent, set duplicateOf to the matching task ID."
7. **Confidence scoring guide**: High (>0.85) = clear unambiguous single intent. Medium (0.5-0.85) = reasonable inference with some ambiguity. Low (<0.5) = uncertain, should include clarification.
8. **List inference**: "Infer target list from task content even when not explicitly mentioned. 'Buy drill bit' → Hardware/Home Improvement list."
9. **Response schema**: New JSON format matching `ParsedVoiceResponse`

### New JSON response schema

```json
{
  "intent": "add|complete|reschedule|delete|query|amend",
  "tasks": [
    {
      "title": "string",
      "dueDate": "YYYY-MM-DD|null",
      "preferredTime": "morning|afternoon|evening|null",
      "targetListId": "string|null",
      "parentTaskId": "string|null",
      "confidence": 0.0,
      "duplicateOf": "existing-task-id|null"
    }
  ],
  "clarification": "string|null"
}
```

## Pipeline Changes

### GeminiCaptureRepository

- New method `parseVoiceInputV2()` with updated prompt and response parsing
- Keep old `parseVoiceInput()` temporarily for backwards compat (can remove later)
- New `parseVoiceResponseJson()` replaces `parseVoiceTaskJson()`
- Pass current time (hour) in addition to date for temporal reasoning

### VoiceParsingCoordinator

- Store `ParsedVoiceResponse` instead of individual fields (dueDate, targetListId, parentTaskId)
- `handleRawTranscription` calls `parseVoiceInputV2`
- On success: store full response, call `showPreview(response)`

### VoiceCaptureManager / VoiceInputState

- `VoiceInputState.Preview` holds `ParsedVoiceResponse` instead of individual fields
- `showPreview()` signature changes to accept `ParsedVoiceResponse`
- Fallback (no API key): construct a minimal `ParsedVoiceResponse` with intent=ADD, single task from raw text, confidence=0.0

### ViewModel layer (both TaskWallViewModel + PhoneCaptureViewModel)

- `confirmVoiceTask()` → `confirmVoiceTasks()`: iterates `response.tasks`, creates/completes/deletes based on `response.intent`
- For COMPLETE intent: find matching task by ID, call completeTask
- For DELETE intent: find matching task by ID, call deleteTask (after confirm)
- For RESCHEDULE intent: update task due date
- For QUERY intent: no-op (just show the tasks, don't commit)
- For AMEND intent: modify the most recently created voice task
- ADD intent: create tasks (current behavior, but batched)

### UI layer (TaskWallScreen preview card)

- Preview card shows a list of tasks (not just one)
- Each task item shows: title, due date, target list name, preferredTime if set
- Duplicate warning: if `duplicateOf != null`, show subtle "similar to: <existing title>" line
- Intent display: for non-ADD intents, show action label ("Complete:", "Delete:", "Reschedule:")
- Encoder click = batch confirm all tasks
- Encoder twist = cancel

## File Change Map

| File | Change |
|------|--------|
| `capture/repository/GeminiCaptureRepository.kt` | New `parseVoiceInputV2`, new prompt, new JSON parser |
| `voice/VoiceCaptureManager.kt` | Update `VoiceInputState.Preview`, update `showPreview` signature |
| `capture/VoiceParsingCoordinator.kt` | Store `ParsedVoiceResponse`, call V2 method |
| `viewmodel/TaskWallViewModel.kt` | Update `confirmVoiceTasks`, handle intents |
| `viewmodel/PhoneCaptureViewModel.kt` | Mirror wall ViewModel changes |
| `ui/screens/TaskWallScreen.kt` | Multi-task preview card, intent labels, duplicate warnings |

## Testing

- Unit tests for `parseVoiceResponseJson` with various JSON shapes
- Unit tests for temporal resolution examples
- Unit tests for multi-task extraction edge cases
- Integration test: full pipeline from raw text → ParsedVoiceResponse
