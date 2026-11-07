---
name: neoworld-github
description: Commit and push NeoWorldMod changes to GitHub, or create and update its pull requests. Use for requests to upload or publish project code, respecting project approval and compilation rules.
---

# NeoWorld GitHub

Use in the NeoWorldMod checkout selected by the user. The known checkout is `C:\Users\miw\Documents\NeoWorldMod` and expected remote is `https://github.com/9miw/NeoWorldMod.git`; verify actual configuration rather than assuming it.

## Inspect and scope

- Read applicable `AGENTS.md` instructions. Inspect `git status --short --branch`, current branch, remotes, and relevant staged and unstaged diffs. Check untracked files only as needed for the task.
- If a remote or destination differs from the requested repository, resolve the discrepancy before publishing.
- A request to inspect or diagnose does not authorize code changes. Explain proposed code changes and obtain explicit confirmation before creating, editing, deleting, generating, compiling, or installing code. Existing explicit approval covers only its agreed scope; ask again for a material expansion.
- Preserve unrelated local work. Stage explicit paths for the approved task; inspect the staged diff before committing. If task ownership of changes is unclear, ask before including them.
- Do not stage credentials, local databases, logs, or generated build output. Inspect relevant ignore rules and candidate files without displaying secret contents.

## Validate

- After each code change, run `.\gradlew.bat compileJava` in the project root and inspect its exit status and output. Do not run a full build or package unless explicitly requested.
- For pre-existing code changes awaiting upload, inspect available validation evidence. If compilation is needed and the user has not authorized it under project rules, explain the check and obtain confirmation before running it.
- Stop publication when required compilation fails; explain the failure and seek approval for fixes outside the approved scope.
- Perform relevant resource-reference and other checks. Documentation-only changes do not require compilation. State whether in-game verification was performed.

## Commit and publish

- A request to upload code authorizes the commit and push needed for that scoped upload. Create a PR only when requested or already explicitly authorized. Do not merge a PR unless requested.
- Preserve the user's destination and branch choice. For a requested new branch without a specified name, use the `codex/` prefix. Inspect branch tracking and remote divergence before pushing; never force-push, reset, amend published commits, or rewrite history without explicit authorization.
- Use a concise commit message describing the net change. If there is nothing to commit, inspect whether existing scoped commits still need pushing.
- Push to the verified remote and intended branch. On rejection, inspect the cause; do not blindly pull, rebase, switch branches, or retry with force. Explain any conflict requiring a new decision.
- For PR operations, prefer available connected GitHub tools or GitHub CLI. With CLI, check `gh --version` and `gh auth status`; if authentication is missing, ask the user to authenticate without requesting tokens in chat.
- Inspect repository PR templates and check for an existing PR for the branch. Update a matching PR rather than creating a duplicate, preserving its draft or ready state. New PRs default to draft unless the user requests otherwise.
- Explain the problem, resulting behavior, and relevant validation in the PR description. With CLI, write multiline descriptions to a temporary file and use `--body-file`.
- Attach every created PR to the current chat with `mcp__codex_app__attach_artifact` when available; also attach an existing PR when asked to update or review it.

## Report

Report the branch, short commit hash, publication result, and usable GitHub or PR link. Include compilation outcome and whether in-game verification was performed when code changed. Distinguish successful push from pending or failing CI; do not claim checks passed without evidence.
