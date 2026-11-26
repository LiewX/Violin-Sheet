---
name: device-check
description: How to check the app on the Galaxy Tab S9+ over adb from Git Bash. Helpers in scripts/device.sh (install, launch, tap, db, pixels, shot, device_test), pen strokes, screenshots without reading them, and the traps found so far. Load when a plan has tablet steps.
---

# Device checks

The tablet holds the user's own data (Sheets, Ink): the rules in `CLAUDE.md` → Build & run apply (never `connectedAndroidTest`; no `pm clear` or uninstall; never change an installed database version).

## Helpers

`source scripts/device.sh` gives:

| Helper | Does |
|---|---|
| `install_app`, `launch` | Install the debug build; force-stop and start the app |
| `tap_text <label regex> [sleep]`, `type_into <field regex> <text>`, `has_text <regex>`, `ui` | Find and tap nodes through a uiautomator dump |
| `db <sql>` | Run SQL against a copy of the app's database and print rows |
| `shot <path>` | Save a screenshot as `<path>.png` plus a 1000 px wide `<path>-small.png` |
| `pixels <png> x,y …` | Print the RGB at each point (full-size screenshot coordinates) |
| `device_test <Class>` | Run an androidTest class from `androidApp/src/androidTest`, keeping the app's data |
| `crashes` | The crash log |

- **Pen strokes:** `adb shell input stylus swipe x1 y1 x2 y2 [ms]` draws a real pen stroke (tool type STYLUS). Fingers: `adb shell input touchscreen tap|swipe`.
- **Gestures `adb shell input` can't do** (pinch, multi-finger, the S Pen side button) go in a UiAutomator test under `androidApp/src/androidTest`, run with `device_test`. `sendevent` is blocked by SELinux.
- **Each dump takes about 2 s:** for timed checks (an Undo window), read every position first and tap by coordinates without dumping in between, and read positions again after the screen changes.

## Checking without reading images

Implementers check with text: `db` rows, the `ui` dump, and `pixels` for colours (e.g. "glyph under the highlighter is (0,0,0), paper is yellow"). Take screenshots with `shot` and save them as evidence in `handoffs/<N>-evidence/`, but **don't open them**: each one costs thousands of tokens. The orchestrator looks at the `-small.png` copies when a check is visual (layout, look, misplaced Ink).

## Traps found so far

- Snackbar text doesn't show in uiautomator dumps: check the message with `pixels`, or leave it to the orchestrator's screenshot look.
- Compose reports a `selectable` choice as **checked** (`checked="true"`), not selected, to UiAutomator.
- `device_test` ends by killing the app, which clears the undo history. Run undo/redo checks, and clean up test Ink by undo, **before** any `device_test` run, and before closing the Sheet.
- Clean up test Ink on the test Sheet before closing it, and record the user's Sheets' stroke ids before and after (`db "SELECT id FROM InkStroke WHERE sheetId = 1 ORDER BY id"`). A `delete_strokes <sheetId>` helper (a small permanent androidTest) doesn't exist yet; build it in the first issue that needs it.

## Raw commands (debug build)

- Launch: `adb shell am start -n com.violinsheet/com.violinsheet.android.MainActivity`
- Screenshot: `adb exec-out screencap -p > shot.png`
- Crashes: `adb logcat -d -b crash`
- Database: copy `violinsheet.db`, `-wal` and `-shm` with `adb exec-out run-as com.violinsheet cat databases/<file>` and open them with Python's `sqlite3`.
- From Git Bash, prefix adb with `MSYS_NO_PATHCONV=1` (device.sh does), or `/sdcard/...` becomes `C:/Program Files/Git/sdcard/...`. Pass local files as Windows paths (`C:/...`).
