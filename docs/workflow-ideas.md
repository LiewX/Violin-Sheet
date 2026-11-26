# Workflow ideas

What slowed sessions down or caused mistakes in the tools, docs or rules, with a concrete fix for each. Reviewed at every cleanup (see `CLAUDE.md` → Working Style): add new ideas, and mark old ones **done** or **dropped** with a short reason.

Status: **open**, **done** or **dropped**.

## 2026-11-26: #1 project skeleton

| # | Problem | Fix | Status |
|---|---|---|---|
| 1 | The machine had no JDK, Android SDK or Gradle. This only came up once building started, and cost a question plus about 10 minutes of installs. | Add a **Build & run** section to `CLAUDE.md`: required tools (scoop `temurin17-jdk`, `android-clt`, SDK platform 36), and a check to run at session start (`java -version`, `adb version`). | done |
| 2 | A full `./gradlew build` takes about 7 minutes (iOS klib, lint, release variants), and it ran several times while fixing errors. | Document two commands in **Build & run**: a fast check for each edit (`./gradlew :core:data:testDebugUnitTest :androidApp:assembleDebug`), and the full `build` once before committing. | done |
| 3 | I filtered Gradle output with `grep … \| head`, which kept task lines and cut off the actual error. The first failure needed a second run to read. | Use one standard filter for build output: `./gradlew <tasks> --console=plain 2>&1 \| grep -vE "^> Task\|^\[=\|^$"`. Put it in **Build & run**. | done |
| 4 | Git Bash rewrote device paths for adb (`/sdcard/...` became `C:/Program Files/Git/sdcard/...`), and adb couldn't read the Unix-style path to a local file. Two failed pushes. | In **Build & run**, say to prefix adb commands with `MSYS_NO_PATHCONV=1` and pass Windows-style local paths (`C:/...`). Alternatively, run adb from PowerShell. | done |
| 5 | The "runs on the tablet" check needed the user mid-session: plug in the device, then import twice by hand. | At the start of an issue that needs the device, run `adb devices` and ask the user to plug in while I code. Record the check (install, launch, `screencap`, read the database with `run-as`) as `scripts/device-check.sh` once a second issue needs it. | done (the rule; the script is still due once a second issue needs it) |
| 6 | Where to commit wasn't recorded anywhere, so it took a question at the end. | Add the user's choice (commit straight to main and push after the user verifies; close the issue from the commit message) to `CLAUDE.md` → Working Style. It's saved in memory now, but memory is per machine. | done |
| 7 | Choosing plugin and library versions needed about 6 `curl` calls to Maven metadata, plus a guess about AGP 9 compatibility. | Keep all versions in `gradle/libs.versions.toml` (done). When bumping, check one compatibility table first (Kotlin ↔ KSP ↔ Compose Multiplatform ↔ AGP), and record the chosen set in the commit message. | done |
| 8 | The graphify rule ("query the graph before any grep or read") had nothing to work on in an empty repo, and its graph is git-ignored, so it's local only. | Leave the rule as is: it now has code to index. Revisit if graph queries keep missing files that a grep would find. | dropped: no change needed |
| 9 | I committed #1 without a review. The review afterwards found a case-sensitive name clash and a missing test of the repository path, so the fixes needed a second commit. | Review before committing an issue (`CLAUDE.md` → Working Style). | done |

## 2026-11-26: #2 and #3

| # | Problem | Fix | Status |
|---|---|---|---|
| 10 | Two long Bash scripts that wrote several files through heredocs failed to parse ("unexpected EOF while looking for matching `'`"), so nothing ran and the work had to be redone. | Write each new source file with the Write tool. Keep Bash for running commands and short single-file edits. | open |
| 11 | uiautomator dumps don't include snackbar text, so two message checks came back empty although the messages showed. | Check snackbar messages with `screencap` and look at the image (noted in `CLAUDE.md` → Build & run). | done |
| 12 | Driving a text field over adb took three tries: keys sent before the field had focus went nowhere, and ctrl+A didn't select. | Tap the field first, then send `KEYCODE_MOVE_END` plus enough `KEYCODE_DEL`s. Add a `type_into <field> <text>` helper to `scripts/device.sh` if this comes up again. | open |
| 13 | Each uiautomator dump over adb takes about 2 seconds, so a timed check (Undo within 10 s) ran out of time, and a tap at a remembered position hit the wrong row after the list changed (Back then closed the app). | For timed checks, read every position before the action and tap by coordinates without dumping in between. After the list changes, read positions again. | open |
