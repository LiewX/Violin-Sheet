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
| 10 | Two long Bash scripts that wrote several files through heredocs failed to parse ("unexpected EOF while looking for matching `'`"), so nothing ran and the work had to be redone. | Write each new source file with the Write tool. Keep Bash for running commands and short single-file edits. | done: rule in `CLAUDE.md` → Build & run (Edits and new files) |
| 11 | uiautomator dumps don't include snackbar text, so two message checks came back empty although the messages showed. | Check snackbar messages with `screencap` and look at the image (noted in `CLAUDE.md` → Build & run). | done |
| 12 | Driving a text field over adb took three tries: keys sent before the field had focus went nowhere, and ctrl+A didn't select. | Tap the field first, then send `KEYCODE_MOVE_END` plus enough `KEYCODE_DEL`s. Add a `type_into <field> <text>` helper to `scripts/device.sh` if this comes up again. | done: `type_into` in `scripts/device.sh` (Text boxes will need it) |
| 13 | Each uiautomator dump over adb takes about 2 seconds, so a timed check (Undo within 10 s) ran out of time, and a tap at a remembered position hit the wrong row after the list changed (Back then closed the app). | For timed checks, read every position before the action and tap by coordinates without dumping in between. After the list changes, read positions again. | done: in `CLAUDE.md` → Build & run (Device checks) |

## 2026-11-26: #4

| # | Problem | Fix | Status |
|---|---|---|---|
| 14 | Pinch can't be driven from `adb shell input`, and `sendevent` is blocked by SELinux even for the `input` group. Two attempts were wasted before switching to UiAutomator. | Gesture checks that need several fingers go straight into a UiAutomator test run with `device_test` (`CLAUDE.md` → Build & run). | done |
| 15 | `am instrument` restarts the app's process, so a throwaway test that expected an already-open Sheet found the home screen. | On-device tests open the screen they need themselves, and save any screenshot they need with `UiDevice.takeScreenshot` (as `SheetGesturesDeviceTest` does). | done |

## 2026-11-26: #5 and #6

| # | Problem | Fix | Status |
|---|---|---|---|
| 16 | Ink strokes drew at the wrong place at first: `CanvasStrokeRenderer.draw` takes the transform only as a hint, and the canvas has to be moved by the caller. Found only on the device. | Read the KDoc of a new library's main calls before using them, not just the signatures from `javap`. A screenshot check right after the first draw caught it quickly; keep doing that. | done: in the implementer plan template's Rules |
| 17 | A full-screen Android view (the Ink layer) hid the Compose nodes beneath it from accessibility, and after that Compose stopped refreshing those nodes when zooming. About 20 minutes were lost probing it through throwaway device tests. | Check behaviour in device tests (did it turn, did it draw), not accessibility bounds. Label Pages from above any interop view. Time-box probing a platform quirk to about 10 minutes, then work around it and log it here. | done: labels were frozen because they were far larger than the window; clipping them to the screen fixed it, and the direct zoom check is back |

## 2026-11-26: fixes after the manual check

| # | Problem | Fix | Status |
|---|---|---|---|
| 18 | Palm rejection broke in real use, though every adb check passed: adb can't rest a palm and hold a pen at the same time, and the first design (a View touch listener for the pen) only failed in that case. | For input features, list in the plan the multi-pointer cases adb can't simulate (palm then pen, pen then finger), and design for them explicitly. Put them first in `docs/manual-checks.md`. | done: "Cases adb can't simulate" section in the plan template |
| 19 | The Bash heredoc failure (idea 10) happened twice more this session, after it was logged. | Idea 10's fix applies to Python edit scripts too: write the script with the Write tool, then run `python -I <file>`. | done: same rule as idea 10 |

## 2026-11-26: model guidance and orchestration

| # | Problem | Fix | Status |
|---|---|---|---|
| 20 | The session used about 70% of a 1M-token context. Most of it (about 600k) was tool output: whole-file reads, full screenshots, long Gradle logs. One session doing everything (plans, code, device checks, reviews) carried all of it. | The orchestration workflow (now the `orchestrate` skill) moves code and device work into implementer subagents. The orchestrator also reads with offset/limit or grep, not whole files, and opens a screenshot only when an implementer's evidence needs a look. Check context use after the first orchestrated issue. | open: reading rule now in the `orchestrate` skill; follow-up is idea 27. #7 data: first implementer about 131k tokens, each review about 80k, the resumed implementer about 102k; recheck the orchestrator's own use at the next cleanup |
| 21 | Twice, Gradle failed on Windows with "The process cannot access the file because it is being used by another process" (a daemon held a jar). Each time cost a rebuild. | When that error appears, run `./gradlew --stop` and rebuild. Add it to `CLAUDE.md` → Build & run if it happens again. | done: added to `CLAUDE.md` → Build & run now, since it already happened twice |
| 22 | Writing the model guidance took three review rounds to close its gaps (ownership of steps, split issues, evidence, recovery). The first version said which model, not how the work flows. | When a workflow rule is added, write it as a loop (who does what, in which order, what happens when it fails) and run the grilling before writing, not after. | done: `CLAUDE.md` → Working Style (Workflow rules are loops) |

## 2026-11-26: #7 undo/redo

| # | Problem | Fix | Status |
|---|---|---|---|
| 23 | `adb shell input stylus swipe` draws real STYLUS strokes without a UiAutomator test, but no doc said so; the plan didn't mention it. | Add it to `CLAUDE.md` → Build & run, device checks. | done |
| 24 | `advanceUntilIdle()` skips `backgroundScope` coroutines, so the first ViewModel test run failed for a reason unrelated to the code. | Tests whose writer runs in `backgroundScope` use `advanceTimeBy` + `runCurrent` (the `settle()` helper in `AnnotationViewModelTest`). Add it to `CLAUDE.md` if a second module needs it. | open |
| 25 | The plan cleaned up tablet Ink by undoing after reopening, but closing a Sheet clears the history; cleanup then needed a throwaway instrumentation test. Cleaning up before closing instead meant reload order couldn't be checked on screen. | Plans clean up test Ink before the Sheet closes. Add a `delete_strokes <sheetId>` helper to `scripts/device.sh` (a small permanent androidTest), so checks can reopen and still clean up. | open: noted in the `device-check` skill; built by the first issue that needs it |
| 26 | The usage limit stopped the session mid review round; resuming needed a hand-written resume note. | When a run stops early, the orchestrator writes `handoffs/<N>-resume.md` (what's done, what's left, the commit steps). It worked: the new implementer continued without redoing work. | done: keep doing it |

## 2026-11-26: #8 highlighter and eraser

| # | Problem | Fix | Status |
|---|---|---|---|
| 27 | Subagents use many tokens. Measured: every subagent starts at ~43–45k before any work (system prompt and tool definitions ~31k, a 61-skill list ~7k, `CLAUDE.md` ~3.8k). The #8 implementer ended at ~220k (tool output ~43k from 113 shell calls and 5 screenshots; ~130k its own output), each review at ~87–98k. | Being grilled with the user: custom agent types with fewer tools, smaller screenshots, unrelated skills off, single-role `CLAUDE.md` content into skills, one combined review, and a periodic token review at cleanup. | done (2026-11-26, after grilling): `implementer`/`reviewer` agent types with fewer tools; one reviewer (two for storage/input `model:opus` issues); implementers check with text and never open screenshots (`shot` saves a 1000 px copy for the orchestrator); code via Write/Edit; split issues spanning two subsystems; unrelated skills off in `.claude/settings.json` (philosophy skills kept until #9's numbers); `orchestrate` and `device-check` skills took single-role `CLAUDE.md` content (15.1k → 10.4k chars); `scripts/agent-tokens.py` runs at each cleanup. Measure on #9 against #8; the user turns off the Claude Docs connector with `/mcp` |
| 28 | The Ink API ships no sources in the Gradle cache; the implementer downloaded the `-sources.jar` to read KDoc. | Note the sources URL pattern (`https://dl.google.com/android/maven2/androidx/ink/ink-<module>-android/<ver>/ink-<module>-android-<ver>-sources.jar`) in the build notes. | done: in `.claude/agents/implementer.md` → New library calls |
| 29 | Compose reports a `selectable` choice as *checked* to UiAutomator, not *selected*; a device test assertion failed once on it. | Add to the device-check notes. | done: in the `device-check` skill |
| 30 | `device_test` ends by killing the app, which clears the undo history, so cleanup had to erase after reopening. | Plans order tablet steps so undo/redo checks and cleanup by undo come before any `device_test` run. | done: in the `device-check` skill |
