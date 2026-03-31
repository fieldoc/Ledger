#!/bin/bash
# enforce-serena.sh — PreToolUse hook for Grep/Glob
# Redirects code-symbol searches to Serena (mcp__plugin_serena_serena__*)
# Lets text/config searches pass through to Grep/Glob normally.

INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
PATTERN=$(echo "$INPUT" | jq -r '.tool_input.pattern // empty')
GLOB_FILTER=$(echo "$INPUT" | jq -r '.tool_input.glob // empty')
FILE_TYPE=$(echo "$INPUT" | jq -r '.tool_input.type // empty')
TARGET_PATH=$(echo "$INPUT" | jq -r '.tool_input.path // empty')

# --- If searching non-code files, let Grep through ---
is_non_code_target() {
  # Check glob filter for non-code file types
  if echo "$GLOB_FILTER" | grep -qiE '\*\.(json|yaml|yml|toml|ini|cfg|env|md|txt|csv|xml|html|css|gradle|properties|pro)'; then
    return 0
  fi
  # Check type filter
  if echo "$FILE_TYPE" | grep -qiE '^(json|yaml|yml|toml|markdown|md|txt|xml|html|css)$'; then
    return 0
  fi
  # Check if path points to a non-code file
  if echo "$TARGET_PATH" | grep -qiE '\.(json|yaml|yml|toml|ini|cfg|env|md|txt|csv|xml|html|css|gradle|properties|pro)$'; then
    return 0
  fi
  return 1
}

# --- Detect if pattern looks like a code symbol search ---
is_symbol_search() {
  local p="$1"

  # Kotlin/Java definition patterns
  if echo "$p" | grep -qiE '^\s*(fun |class |object |interface |val |var |enum |data class |sealed |abstract |open |private |internal |override |suspend |companion )'; then
    return 0
  fi
  # Generic definition patterns
  if echo "$p" | grep -qiE '^\s*(def |function |const |let |struct |impl |pub fn |module |export )'; then
    return 0
  fi
  # CamelCase names (likely class/type/Composable lookups)
  if echo "$p" | grep -qE '^[A-Z][a-zA-Z0-9]+$'; then
    return 0
  fi
  # camelCase or snake_case identifiers that look like function/variable names
  # but NOT common text-search terms
  if echo "$p" | grep -qE '^[a-z_][a-zA-Z0-9_]+$'; then
    # Exclude common text-search terms
    if echo "$p" | grep -qiE '^(error|warning|todo|fixme|hack|note|bug|config|setting|env|path|url|log|print|debug|test|import|package|android|kotlin|gradle|version|build|api|http|https|true|false|null)$'; then
      return 1
    fi
    return 0
  fi

  return 1
}

# --- Main logic ---
case "$TOOL" in
  Grep)
    # Always allow non-code file searches
    if is_non_code_target; then
      exit 0
    fi

    # Redirect symbol-like searches to Serena
    if is_symbol_search "$PATTERN"; then
      CLEAN_PATTERN=$(echo "$PATTERN" | sed 's/^\s*\(fun \|class \|object \|interface \|val \|var \|def \|function \)//i' | xargs)
      cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "This looks like a code symbol search. Use Serena instead: mcp__plugin_serena_serena__find_symbol (for definitions) or mcp__plugin_serena_serena__find_referencing_symbols (for usages) or mcp__plugin_serena_serena__search_for_pattern (for text in code). See .claude/rules/serena.md for the full decision guide. Only fall back to Grep if Serena fails twice."
  }
}
EOF
      exit 0
    fi

    # Allow all other Grep calls
    exit 0
    ;;

  Glob)
    # Glob is fine for file discovery — don't intercept
    exit 0
    ;;

  *)
    exit 0
    ;;
esac
