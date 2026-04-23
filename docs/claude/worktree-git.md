# Worktree, Git & PR Hygiene

Load when committing, branching, working inside a git worktree, merging back to main, rebasing, or opening a PR.

## Repo

- GitHub: https://github.com/fieldoc/Ledger (private, default branch `main`).
- User is new to git — proactively prompt for commit/push/pull at the right moments rather than assuming.

## Commit messages — Conventional Commits

Use Conventional Commits with scopes. Examples seen in this repo's history:

- `feat(voice): add Gemini intent classifier for DAY_PLAN`
- `fix(calendar): close month-view drill-down state on back press`
- `refactor(viewmodel): extract day-plan undo into PlanUndoState`
- `polish(ui): tighten overdue amber and recheck contrast on dark theme`

Keep commits focused — one concern per commit. Subject line ≤ 72 chars, imperative mood. If a body is needed, separate from the subject by a blank line and explain *why*, not *what*.

## Pre-commit checklist (mandatory — ask before committing)

1. `gradlew lint` clean (or any new warnings explained)
2. `gradlew test` green for any code path you touched
3. No `.hprof`, `nul`, `local.properties`, or other machine-specific files staged
4. `CLAUDE.md` and rules files updated if architecture/gotchas changed
5. Confirm with the user before running `git commit` or `git push`

## Pull requests

PR description should include:
- Short summary (1–3 sentences) of *why* the change exists
- Testing notes — what you ran, what you verified manually
- Linked issue or plan doc when relevant
- **Screenshots for any Compose UI change** (before/after where possible)

## Worktrees (when used)

ToDoWallApp follows the same `.claude/worktrees/<name>/` convention as HRapp. Worktrees are not in active use day-to-day, but when present:

- **`local.properties` missing in a fresh worktree:** `cp ../../../local.properties .` from inside the worktree.
- **Windows path-length** can break KSP/AAPT inside deeply-nested worktree build dirs. If you hit it, copy changed files back to the main checkout and build there. **Do not** use `subst` to shorten the path — writes through the mapped drive can silently revert edits made via the original path.

## Merging worktree → main

- **Merge worktree into a dirty main:** run THREE separate commands, never chained with `&&`:
  1. `git stash push -u -m "wip"` (in main)
  2. `git merge --ff-only <branch>`
  3. `git stash pop`
  Chaining with `&&` is dangerous: if `stash pop` exits non-zero, the merge never runs.
- **Rewritten-file stash conflicts:** the procedure above fails when popped WIP either (a) touches a file the branch rewrote, or (b) references untracked files not yet committed. Recovery: `git checkout HEAD -- <file>` → `git reset HEAD` → `git stash drop`. WIP remains as unstaged changes.
- **Stash-pop after ff-merge with duplicate untracked files:** when main mirrors the worktree (files copied over for building), `stash push -u` → `merge --ff-only <branch>` → `stash pop` will warn `<file> already exists, no checkout` for the untracked portion — the merge created identical content. Tracked portion (e.g. local `.claude/settings.local.json`) pops cleanly; `git stash drop` is safe.
- **Branch diverged behind main:** `--ff-only` fails. Fix: in the worktree run `git rebase main`, then in main run `git merge --ff-only <branch>`. Note: `git fetch . main:main` from the worktree errors with "refusing to fetch into branch checked out at ..." — no need; `git rebase main` reads the local ref directly.
- **`git rebase --continue --no-edit` is invalid** and hard-errors. Use `GIT_EDITOR=true git rebase --continue` for non-interactive continue.
- **Untracked files block `git merge`:** if the incoming branch creates a file present locally as untracked, `rm` the untracked copy first.

## Worktree teardown

- `git worktree remove` returns "permission denied" when the shell cwd is inside the worktree. `cd` out first. Use `git worktree prune` to clear stale registrations.
