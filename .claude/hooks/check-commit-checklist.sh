#!/bin/bash
# check-commit-checklist.sh — PreToolUse hook for Bash(git commit*)
#
# Intercepts git commit commands and checks whether staged files contain
# real code. If they do, blocks the commit and asks Claude to run the
# pre-commit checklist first. Docs/config-only commits pass straight through.

INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# Only act on git commit commands
if ! echo "$COMMAND" | grep -qE '^\s*git commit'; then
  exit 0
fi

# Get staged file list
STAGED=$(git diff --cached --name-only 2>/dev/null)

if [ -z "$STAGED" ]; then
  # Nothing staged — let git handle the error
  exit 0
fi

# Check for code file extensions
CODE_FILES=$(echo "$STAGED" | grep -E '\.(kt|java|xml|gradle|kts)$' || true)

if [ -z "$CODE_FILES" ]; then
  # Docs/config only — allow commit without checklist
  exit 0
fi

# Code files are staged — block and prompt for checklist
cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Code files are staged for this commit:\n$(echo "$CODE_FILES" | sed 's/^/  - /')\n\nThe pre-commit checklist (CLAUDE.md) must be run before committing code changes. Please run the checklist now, then commit once all checks pass."
  }
}
EOF
exit 0
