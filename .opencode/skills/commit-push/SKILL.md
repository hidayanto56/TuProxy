---
name: commit-push
description: Review, commit, and push current changes following this repo's git workflow
---

Follow this workflow exactly:

1. Run `git status --short --untracked-files=all` and `git diff` (plus `git diff --stat` if the diff is large) to see everything changed, deleted, or untracked.
2. Review the diff for anything that must NOT be committed:
   - Secrets/credentials in any file (especially `application.properties`, `.env`, config files).
   - Personal/local dev files (`.env`, `claude*.sh`, `opencode.sh`, `.claude/`, `plans/`, `build/build.sh` — most are gitignored, but double-check nothing slipped through).
   - Binary/build artifacts (`target/`, `*.jar`, `*.class`, `logs/`) that look accidental rather than intentional.
   If anything looks off, stop and ask before proceeding.
3. Stage only the legitimate changes explicitly by path (`git add <file1> <file2> ...`, or `git add -u` for tracked modifications/deletions plus explicit `git add` for new files). Never `git add -A` / `git add .`.
4. Check existing style with `git log --oneline -5`, then draft one commit per topic: concise message, imperative mood, 1-2 sentences max, focused on *why*. Never mention the agent / add `Co-Authored-By` lines.
5. Commit (HEREDOC to avoid quoting issues):
   `git commit -m "$(cat <<'EOF'
   <message>
   EOF
   )"`
   Never `--amend` a published commit or use `--no-verify` unless explicitly asked.
6. Run `git status` to confirm the commit succeeded and nothing unexpected remains staged/unstaged.
7. Push to the current branch's upstream — check with `git rev-parse --abbrev-ref --symbolic-full-name @{u}` first, don't assume `origin master`. Never force-push unless explicitly asked.
8. Report back concisely: what was committed, what (if anything) was left out and why.

If extra scope/instructions are provided with the invocation (e.g. "only the service files", "skip push"), treat them as overriding scope for steps 3 and 7.
