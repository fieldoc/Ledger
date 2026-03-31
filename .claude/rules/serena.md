# Serena MCP Server — Usage Rules & Playbook

This project has Serena configured with tools prefixed `mcp__plugin_serena_serena__`.

## When to Use Serena (INSTEAD of Grep/Glob)

Use Serena's semantic tools whenever the task involves **understanding code structure**, not just finding text:

- **Finding where a function/class/variable is defined** → `mcp__plugin_serena_serena__find_symbol` (NOT grep)
- **Finding all callers/references to a symbol** → `mcp__plugin_serena_serena__find_referencing_symbols` (NOT grep)
- **Getting a structured overview of a file's contents** → `mcp__plugin_serena_serena__get_symbols_overview` (NOT reading the whole file)
- **Searching for a symbol by partial name** → `mcp__plugin_serena_serena__find_symbol` with substring
- **Searching for a text pattern in code** → `mcp__plugin_serena_serena__search_for_pattern` (indexed, project-aware)
- **Understanding what references a Composable/ViewModel/Repository** → `find_referencing_symbols` scoped to the symbol

## When Grep/Glob Is Still Better

Stick with Grep/Glob for:

- Searching for **literal strings** in non-code files: YAML, JSON, .env, markdown, gradle configs
- Finding **log messages, error strings, or magic constants**
- Simple **filename pattern matching** (use Glob)
- Searching in files **outside the project source tree**

## Decision Rule

Ask: "Am I looking for a **code symbol** (function, class, variable, Composable, type) or a **text pattern**?"
- Code symbol → Serena
- Text pattern in non-code files → Grep
- Text pattern in code files → Serena's `search_for_pattern` first (it's project-aware and respects .gitignore)

## Tool Quick Reference

| Task | Serena Tool | Instead Of |
|------|------------|------------|
| Find definition of `TaskWallViewModel` | `find_symbol` name="TaskWallViewModel" | `grep "class TaskWallViewModel"` |
| Find everything that calls `refreshTaskLists()` | `find_referencing_symbols` | `grep "refreshTaskLists"` |
| See what's defined in a .kt file | `get_symbols_overview` | Reading 1500-line file |
| Search for a pattern in all code | `search_for_pattern` | `grep -r "pattern"` |
| Read a specific file | `read_file` | Built-in Read (either works) |

## Common Pitfalls & Fixes

### 1. Kotlin LSP Slow to Index
**Symptom:** `find_symbol` returns nothing for a symbol that definitely exists.
**Cause:** The Kotlin language server takes time to index, especially after cold start.
**Fix:** Wait a few seconds and retry. If it fails twice, fall back to Grep and note the failure.

### 2. First-Run Token Exhaustion
**Symptom:** Conversation gets slow/confused after Serena onboarding.
**Cause:** Onboarding indexes the whole project and eats context budget.
**Fix:** After first onboarding completes, start a fresh Claude Code session. The index persists.

### 3. Stale Results After Edits
**Symptom:** Serena returns outdated symbol locations after file edits.
**Cause:** LSP index lags behind filesystem changes.
**Fix:** Use `restart_language_server` if results seem stale. Usually catches up in seconds.

### 4. Multiple KotlinLsp Processes
**Symptom:** System slows down, Serena becomes unresponsive.
**Cause:** KotlinLsp processes accumulate across sessions.
**Fix:** Already handled by the PreToolUse hook that kills duplicate processes.

## Retry Protocol

When a Serena tool call fails:
1. **First failure:** Retry the exact same call (transient LSP issue)
2. **Second failure:** Try `search_for_pattern` as a broader fallback
3. **Third failure:** Fall back to Grep and **explicitly note** that Serena was unavailable
4. **Never silently switch to Grep** — always log when falling back so the failure can be debugged
