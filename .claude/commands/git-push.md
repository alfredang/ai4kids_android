---
description: Securely commit & push code to GitHub — scans for secrets/keys first (blocking), commits with this repo's conventions, pushes (branching off main when needed), and optionally opens a PR.
argument-hint: "[pr]"
allowed-tools: Bash, Grep, Read, Glob
---

# /git-push

Securely ship code changes for the **AI4Kids Android** app to GitHub. Scan → commit → push → optional PR. This app is offline-first with a few opt-in online features; there is **no server-side deploy tied to a push** (unlike the web sibling), so a push here just updates the repo — it does not release to devices.

**Argument:** pass `pr` (in `$ARGUMENTS`) to also open a pull request after pushing.

## Phase 1 — Secret scan (MANDATORY, blocking)

Never push secrets. Scan what's about to go up **before** any git write.

> Use the **Grep tool (ripgrep)** for pattern scanning — do NOT shell out to `grep`. This is a Windows box (PowerShell + Git Bash); the Grep tool is the sanctioned, reliable path.

1. List what's staged: `git diff --cached --name-only`. If nothing is staged yet, scan the files you intend to stage.
2. **Block sensitive files outright** — if any of these are staged, STOP:
   - `local.properties` (holds `GEMINI_API_KEY`; must stay git-ignored — see [app/build.gradle.kts](../../app/build.gradle.kts) which reads it).
   - Signing material: `*.jks`, `*.keystore` (except `debug.keystore`), `keystore.properties`, `*.p12`, `*.pfx`.
   - `google-services.json` / `google-play-*.json` (Play publishing / Firebase service accounts).
   - Generic: `.env`, `.env.*`, `*.pem`, `*.key`, `id_rsa`/`id_ed25519`, `credentials.json`, `secrets.json`, `.netrc`.
3. **Scan staged file contents** with the Grep tool (case-insensitive where sensible). Run each as a separate search so one noisy hit doesn't hide others:
   - **This app's own secrets (hard stops):**
     - `AIza[0-9A-Za-z\-_]{35}` — a **Google/Gemini API key** literal. The Gemini key belongs in `local.properties` only, injected via `BuildConfig.GEMINI_API_KEY` — never hardcoded in Kotlin or committed.
     - `GEMINI_API_KEY\s*=\s*\S` outside `local.properties` — the key assigned anywhere it shouldn't be (e.g. `gradle.properties`, a committed `.properties`, or inline in source).
     - `storePassword\s*=\s*\S`, `keyPassword\s*=\s*\S`, `keyAlias\s*=\s*\S` — release-signing credentials; must live in a git-ignored `keystore.properties`, never in `build.gradle.kts` or a committed file.
   - **Generic credential assignments:** `(api[_-]?key|api[_-]?secret|secret|password|passwd|token|credential)\s*[:=]\s*['"][^'"]{8,}['"]` — a literal secret assigned in code.
   - **Private keys:** `-----BEGIN (RSA|DSA|EC|OPENSSH|PGP)? ?PRIVATE KEY-----` and `-----BEGIN PRIVATE KEY-----`.
   - **Vendor keys:** AWS `AKIA[0-9A-Z]{16}`; Stripe `sk_live_`, `rk_live_`; Slack `xox[baprs]-`; GitHub `gh[pousr]_[A-Za-z0-9_]{36,}`.
   - **JWTs / bearer tokens:** `eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+`, `bearer\s+[A-Za-z0-9._\-]{20,}` (the online Brain Arcade / co-op features carry a NextAuth session cookie — it must never be committed).
4. **`.gitignore` sanity:** confirm `local.properties` and `*.keystore` are ignored (they are). **`*.jks` is currently NOT in [.gitignore](../../.gitignore)** — if any `.jks` is present or staged, that's a finding: add the ignore rule before pushing.

**If anything is found:** STOP. Report each hit as `file:line`, and remediate — move the value into `local.properties` / `keystore.properties` (git-ignored) and read it via `BuildConfig` / a `Properties` load in Gradle, then `git restore --staged <file>` to unstage. Never commit a real secret "temporarily."

**If clean:** report "🔒 Secret scan clean — no secrets staged" and continue.

> Note: an empty/placeholder value (e.g. `GEMINI_API_KEY=` in a `local.properties.sample`) is fine — don't flag obviously-fake or blank values.

## Phase 2 — Commit & push

1. `git status` and `git diff --cached --stat` to confirm exactly what's going up.
2. **Stage explicitly** — list the files and `git add <paths>`. Never `git add -A` / `git add .` (it sweeps in `local.properties`, build output, or a stray keystore).
3. **Branch policy:** the default branch is `main` and day-to-day work lands on `dev`. If you're on `main` and the user hasn't explicitly said to commit to `main`, create a feature branch first (`git switch -c <type>/<short-topic>`). Committing to `dev` is fine.
4. **Never commit build output** — `/app/build`, `/build`, `*.apk`, `*.aab`, and `/app/src/main/jniLibs/` are git-ignored generated artifacts; if any show up staged, unstage them.
5. **Commit message:** conventional commits — `feat(scope): …`, `fix(scope): …`, `docs:`, `refactor:`, `chore:`, `style:`, `ci:` — derived from the actual diff (scope = the feature, e.g. `phonics`, `story`, `escape`, `cards`, `theme`). End the message with the trailer:
   ```
   Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
   ```
   (No interactive flags — no `git commit` editor, no `git rebase -i`, no `git add -i`; they hang in this environment.)
6. **Push:** `git push origin <branch>` (use `-u` on a new branch). If rejected as non-fast-forward: `git pull --rebase origin <branch>` then push again. Never `--force` unless the user explicitly asks.

## Phase 3 — Pull request (only if `pr` in `$ARGUMENTS` or the user asks)

Use `gh`. Target `main` unless told otherwise (feature work usually flows `dev` → `main`):
```bash
gh pr create --base main --title "<conventional title>" --body "## Summary
- <what changed and why>

## Test plan
- [ ] ./gradlew assembleDebug passes
- [ ] smoke-tested the affected activity on a device/emulator (offline path works)

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```
Report the PR URL.

## Phase 4 — Report

Summarize: branch, commit hash + subject, push result, and PR URL (if any). Note that this only updates GitHub — it does **not** ship a build to any device or the Play Store.

## What this command deliberately does NOT do

No app store upload, no signed-release build, no version bump. Keep it to: scan → commit → push → optional PR. Signing and release publishing are separate, manual steps.
