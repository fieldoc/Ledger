# Kotlin Code Navigation — Kotlin LSP

The `kotlin-lsp@claude-plugins-official` plugin is active. It runs as a transparent Language Server Protocol integration that provides code intelligence (definitions, types, diagnostics) for `.kt` and `.kts` files automatically — **no explicit tool calls required**.

## Code Search Strategy

| Task | Tool | How |
|------|------|-----|
| Find class/function definition | Grep | `pattern: "class Foo"` or `"fun foo"` with `glob: "**/*.kt"` |
| Find all callers/references | Grep | `pattern: "functionName"` with `glob: "**/*.kt"`, `output_mode: "files_with_matches"` first |
| Explore a file's contents | Read | Read directly; LSP enhances understanding automatically |
| Find a file by name | Glob | `pattern: "**/ClassName.kt"` |
| Search configs/YAML/JSON/Gradle | Grep | No type filter |

## Decision Rule

Ask: "Am I looking for a **code symbol** or a **text pattern**?"
- **Code symbol** (class, function, Composable, ViewModel) → Grep with `glob: "**/*.kt"` scoped to `app/src`
- **Text in configs/YAML/JSON/Gradle/Markdown** → Grep without type filter
- **File discovery** → Glob

## Performance Tips

- Use `output_mode: "files_with_matches"` first to locate the file, then `Read` with `offset`/`limit` to target the relevant section — avoids reading 2000-line files in full.
- Scope Grep to a subdirectory (`path: "app/src/main/.../ui"`) when you know the area.
- For multi-file analysis, use Gemini CLI with `@file` syntax — it handles 5+ files in one context window efficiently.

## What NOT to Do

- Do **not** call any `mcp__plugin_serena_*` tools — Serena has been removed from this project.
- Do **not** read an entire large file just to find a single symbol — Grep first, then targeted Read.
