# GEMINI.md - ToDoWallApp Instructional Context

## Project Overview
**ToDoWallApp** is a dedicated Android kiosk-style application designed for wall-mounted devices (e.g., tablets) to display Google Tasks. Its core mission is to **reduce mental load** by externalizing task management to a reliable, calm, and always-on physical display.

### Core Philosophy: Externalizing Mental Load
- **Reduce Thought Clutter:** Offload complex project ideas and nested subtasks to a physical wall so the brain can focus on the present moment.
- **Frictionless Capture:** Voice input is the primary method for adding tasks to ensure offloading thoughts is effortless.
- **Calm UI:** Design for "calm productivity" — professional, muted palettes, generous whitespace, and purposeful transitions. Not a high-contrast or anxiety-inducing dashboard.
- **High Trust:** Reliable sync and always-on presence (OLED-safe Ambient Mode) to build user confidence in the system.

## Technical Architecture
- **Language:** Kotlin (V8 runtime equivalent for Android).
- **Framework:** Jetpack Compose (Material 3).
- **Architecture:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow.
- **Data Layer:** 
    - `GoogleTasksRepository`: Wrapper for Google Tasks API.
    - `DataStore Preferences`: Local persistence for settings and list selection.
- **Auth:** Google Sign-In with OAuth2 (`TasksScopes.TASKS`).
- **Kiosk Mode:** Implemented in `MainActivity` with immersive fullscreen, system UI hiding, and `FLAG_KEEP_SCREEN_ON`.
- **Hardware Integration:**
    - **Rotary Encoder:** Primary physical interaction (Rotate=Navigate, Click=Toggle/Confirm). Connected via Arduino as Bluetooth HID.
    - **Voice Input:** Android `SpeechRecognizer` (Phase 1) and potential AI-backed parsing (Phase 2).

## Key Features & UX Specs
- **Task Completion:** Tasks drift to the bottom into a "Completed" section with a ~300ms ease-in-out animation.
- **Hierarchy:** Indented subtasks with a subtle vertical connecting line. Full hierarchy is always visible.
- **Multiple Lists:** Accordion folders. Lists bloom open on focus and collapse when focus moves away. Sorted by urgency.
- **Ambient Mode:** 
    - **Tier 1 (Quiet):** Only top 2-3 urgent tasks visible after 30s of inactivity.
    - **Tier 2 (Sleep):** Fully dark/black screen based on schedule or ambient light sensing.
- **Urgency Visuals:** Muted amber/terracotta for overdue (warm temperature shift), not alarming red.
- **Voice Confirmation:** Draft task card appears for confirmation before committing to Google Tasks.

## Building and Running
- **Build Debug APK:** `./gradlew assembleDebug`
- **Install on Device:** `./gradlew installDebug`
- **Run Unit Tests:** `./gradlew test`
- **Run UI Tests:** `./gradlew connectedAndroidTest`
- **Lint/Quality:** `./gradlew lint`

## Development Conventions & Mandates
### Core Mandates
- **System Integrity:** Never commit credentials, tokens, or debug keystores. Use `local.properties` for local SDK paths only.
- **Contextual Precedence:** These instructions are foundational. Adhere strictly to the "Calm Productivity" design language.
- **Code Style:** 4-space indents, PascalCase for `@Composable` functions. Keep UI logic in `ui/`, state in `ViewModel`, and network in `Repository`.
- **Validation:** Always verify changes with `./gradlew compileDebugKotlin` and tests. Add new tests for urgency logic and repository behavior.

### Tool Usage & Best Practices (serena MCP Server)
- **Regex Safety:** When using `replace_content` with `mode: "regex"`, avoid overly specific or brittle patterns. Use non-greedy wildcards (`.*?`) between clear, unique start and end markers to prevent catastrophic backtracking and timeouts.
- **Size Limits:** Avoid very large replacement strings in a single `replace_content` call. Break complex modifications into multiple smaller steps if the tool hangs.
- **Fallback Mechanism:** If `serena__replace_content` hangs or fails on complex multi-line blocks, fall back to the `default_api:replace` tool, which is often more robust for large textual replacements.
- **Symbolic First:** Prioritize symbolic tools (`find_symbol`, `replace_symbol_body`) over regex for well-defined code entities.

### UI/UX Standards & Frontend Design Mandates
- **Senior Designer Persona:** Always approach UI tasks as an expert in Frontend Design, UI/UX, and Modern Web Frameworks.
- **Mobile-First:** Ensure all designs are responsive and optimized for smaller screens.
- **Accessibility (a11y):** Prioritize semantic HTML/Components, ARIA labels, and accessible contrast ratios.
- **Component-Driven:** Build reusable, isolated components to maintain system integrity.
- **Calm Productivity:** Adhere to the professional, muted palette and purposeful transitions defined for ToDoWallApp.
- **Premium Tactility:** Use `HapticFeedbackConstants` for task completion ("thunk" feel).
- **Visible Focus:** The focused item must have a soft, warm edge glow (not a border) visible from 6+ feet.
- **Zero Flash:** Avoid particle effects, green checkmarks, or gamified dopamine hooks.
- **Design Workflow:** For every UI change, automatically: **Analyze** requirements -> **Suggest** specific components -> **Implement** accessible code -> **Review** for responsiveness.

## Project Structure
- `app/src/main/java/com/example/todowallapp/`
    - `auth/`: Google Auth flow.
    - `data/model/`: `Task`, `TaskList`, `DisplayState`, `TaskUrgency`.
    - `data/repository/`: API and DataStore logic.
    - `viewmodel/`: `TaskWallViewModel` (StateFlow source of truth).
    - `ui/screens/`: `SignInScreen`, `TaskWallScreen`.
    - `ui/components/`: `TaskItem`, `ClockHeader`.
    - `ui/theme/`: Muted palette, typography, and theme tokens.
