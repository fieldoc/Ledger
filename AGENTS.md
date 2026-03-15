# Repository Guidelines

## Project Structure & Module Organization
- Single Android app module in `app/`.
- Entry point is `app/src/main/java/com/example/todowallapp/MainActivity.kt`.
- Core Kotlin code is under `app/src/main/java/com/example/todowallapp/`:
- `auth/` contains Google auth flow (`GoogleAuthManager`, `AuthState`).
- `data/model/Task.kt` contains `Task`, `TaskList`, `DisplayState`, `TaskUrgency`, and `MockData`.
- `data/repository/GoogleTasksRepository.kt` contains Google Tasks API reads/writes.
- `viewmodel/TaskWallViewModel.kt` holds `TaskWallUiState`, auth/task loading, optimistic completion toggles, DataStore list preference, and auto-sync.
- `ui/screens/` contains `SignInScreen.kt` and `TaskWallScreen.kt`.
- `ui/components/` contains reusable UI (`TaskItem.kt`, `ClockHeader.kt`).
- `ui/theme/` contains palette/typography/theme tokens (`Color.kt`, `Theme.kt`, `Type.kt`).
- Android resources live in `app/src/main/res/`.
- Tests are currently in `app/src/test/java/com/example/todowallapp/ExampleUnitTest.kt` and `app/src/androidTest/java/com/example/todowallapp/ExampleInstrumentedTest.kt`.
- Dependency versions are managed in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- Use the wrapper: `./gradlew assembleDebug` to produce a debug APK; `./gradlew installDebug` to deploy to a connected device/emulator.
- Fast iteration: `./gradlew :app:compileDebugKotlin` (catch Kotlin errors) and `./gradlew :app:lint` (Android lint + Compose checks).
- Tests: `./gradlew test` (or `./gradlew :app:testDebugUnitTest`) for JVM/unit tests; `./gradlew connectedAndroidTest` for device/emulator UI tests (requires a running emulator).
- If Play Services auth fails on emulators, verify Google APIs image and a signed-in test account.
- In PowerShell on Windows, use `.\gradlew.bat <task>` if `./gradlew` is unavailable.

## Coding Style & Naming Conventions
- Kotlin style with 4-space indents; keep functions small and side-effect free where possible.
- Compose functions are `@Composable` and PascalCase, e.g., `TaskWallScreen`, `TaskItem`, `ClockHeader`.
- Keep UI logic in `ui/` composables, state/events in `TaskWallViewModel`, and network/API calls in `data/repository`. Models stay in `data/model`.
- Prefer immutable state + `StateFlow`/`collectAsState`; avoid passing mutable collections to composables. Reuse theming from `ui/theme`.
- Preserve wall-display behaviors while editing: immersive system UI handling in `MainActivity`, keyboard navigation + ambient mode in `TaskWallScreen`, and urgency/completion visuals in `TaskItem`.
- Follow existing naming patterns (`*Screen.kt`, `*Item.kt`, `*ViewModel.kt`, `*Repository.kt`) to keep previews and navigation readable.

## Testing Guidelines
- Frameworks: JUnit4 for unit tests, AndroidX Test + Espresso/Compose for instrumentation. Name tests `<Feature>Test` and mirror source packages.
- Current test files are template examples only; add focused tests for:
- task urgency/date logic in `Task.getUrgencyLevel()`;
- repository parsing/sorting + completion/uncompletion behavior;
- `TaskWallViewModel` auth transitions, list selection persistence, and optimistic updates;
- Compose loading/empty/error/syncing states in `TaskWallScreen` and sign-in error/loading states in `SignInScreen`.
- Run `./gradlew test connectedAndroidTest` before submitting; include emulator API level and device notes when reporting results.

## Commit & Pull Request Guidelines
- No repository-wide convention is enforced; prefer Conventional Commit style (`feat:`, `fix:`, `chore:`) with imperative subjects describing intent.
- Keep changes scoped and documented: include a short summary, linked issue/feature, and screenshots or screen recordings for visible UI changes (sign-in, task wall, ambient mode, task animations).
- Note any API/auth steps taken (e.g., enabling Google Tasks API, updating OAuth client). Mention test commands executed and their outcomes in the PR body.

## Security & Configuration Tips
- Google Tasks access uses OAuth via Google Play Services; do not commit credentials, tokens, or debug keystores. Ensure the OAuth client allows the `com.example.todowallapp` package/signing key you are using.
- `local.properties` should only contain SDK paths. Store any future secrets (API keys) in environment variables or an untracked config file and gate usage behind BuildConfig flags if added.
- Do not edit or commit generated outputs from `app/build/` or top-level `build/`.
