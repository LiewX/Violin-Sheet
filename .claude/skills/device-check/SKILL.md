---
name: device-check
description: How to check the app on the Galaxy Tab S9+ over adb from Git Bash. Helpers in scripts/device.sh (install, launch, tap, db, check_db, ids_same, stroke, pixels, shot, device_test), one script per tablet step, screenshots without reading them, and the traps found so far. Load when a plan has tablet steps.
---

# Device checks

The tablet holds the user's own data (Sheets, Ink). Sheets 2 ("Test Etude", 1 Page) and 5 ("Test Spread", 4 blank staff Pages; a landscape spread shows Pages 1–2 with no gap between them) are the test Sheets: draw only there, and leave them with 0 strokes. Sheet 1 ("Scales") and Sheet 4 ("Adagio v2", the user's manual-test Sheet) are the user's: never open Sheet 4, and only view Sheet 1. The rules in `CLAUDE.md` → Build & run apply (never `connectedAndroidTest`; no `pm clear` or uninstall; never change an installed database version).

## Helpers

`source scripts/device.sh` gives:

| Helper | Does |
|---|---|
| `install_app`, `launch` | Install the debug build; force-stop and start the app |
| `tap_text <label regex> [sleep]`, `type_into <field regex> <text>`, `has_text <regex>`, `ui` | Find and tap nodes through a uiautomator dump |
| `db <sql>` | Run SQL against a copy of the app's database and print rows |
| `check_db [sheet]` | Strokes per Sheet; with a Sheet id, one decoded line per stroke (id, page, tool, size, n, first/last point, centroid, box) |
| `ids_same <file> [sheet=1]` | Guard the user's Ink: the first call saves the Sheet's stroke ids, later calls print `same N ids` or the `+`/`-` ids and return 1 |
| `stroke x1 y1 x2 y2 …` | One pen stroke through the points (screen pixels); also draws lassos |
| `shot <path>` | Save a screenshot as `<path>.png` plus a 1000 px wide `<path>-small.png` |
| `pixels <png> x,y …` | Print the RGB at each point (full-size screenshot coordinates) |
| `device_test <Class>` | Run an androidTest class from `androidApp/src/androidTest`, keeping the app's data |
| `crashes` | The crash log |
| `bounds_of <regex>`, `selected` | `x1 y1 x2 y2` of the first matching node (empty if none); content-desc of every `checked="true"` node |
| `hold_drag x1 y1 x2 y2` | Pen long press (1 s), then a drag |
| `setting <key>`, `save_settings <file>`, `restore_settings <file>` | One stored Setting value; dump all Settings; put them back via `RestoreSettingsDeviceTest`, relaunch, diff |
| `ok`, `fail`, `finish`, `expect <label> <actual> <expected>` | PASS/FAIL tally in the calling shell (not in a subshell or pipe); `finish` prints `PASS` or `FAIL: n failed` and returns non-zero on a fail; `expect` fails on an empty actual |
| `guard_start <sheet>…`, `guard_end <sheet>…` | Save, then check, each user's Sheet's stroke ids (`$IDS_DIR/ids-<sheet>.txt`) with ok/fail |

`launch` also sets `stay_on_while_plugged_in 3`, so the tablet stays awake while testing.

- **Pen strokes:** `stroke` (built on `input stylus motionevent DOWN|MOVE|UP x y`) draws any polyline with tool type STYLUS; `input stylus swipe` only draws straight lines. Fingers: `adb shell input touchscreen tap|swipe`.
- **Gestures `adb shell input` can't do** (pinch, multi-finger, the S Pen side button) go in a UiAutomator test under `androidApp/src/androidTest`, run with `device_test`. `sendevent` is blocked by SELinux.
- **Each dump takes about 2 s:** for timed checks (an Undo window), read every position first and tap by coordinates without dumping in between, and read positions again after the screen changes.

## One script per tablet step

Each tablet step in the plan's Verify section becomes one script, `handoffs/<N>-evidence/check-<step>.sh`, written once with Write and rerun after every fix. It sources `scripts/device.sh`, first sets up the state it needs (`restore_settings handoffs/<N>-evidence/settings-baseline.txt`, which relaunches the app, then open the step's Sheet), so the scripts run in any order and rerun cleanly; it never touches the user's Ink. The script then takes a fresh Ink-id baseline (`rm -f <ids file>; ids_same <ids file>`, since the user may have drawn since the last run), does the input, appends `check_db <test sheet>` under a `== <step> <time>` heading to `db.txt`, ends with `ids_same <ids file>` (only this one decides PASS/FAIL for the user's Ink), and prints one `PASS`/`FAIL <why>` line plus the summary. On FAIL, read the last section for that step in `db.txt`, not the screen. `stroke` runs one `input` process per point (~100–300 ms each), so don't check anything that depends on stroke speed or pressure with it. Record the user's Sheets' stroke counts at the start of the run: the user draws between rounds, so a plan never gives them as numbers. Only the implementer writes the Settings baseline file: `save_settings` into it once at the start of each round (fresh each round, so Settings the user changed in between are kept), and `restore_settings` from it once more at the end of the round; a script never saves it.

## Checking without reading images

Implementers check with text: `db` rows, the `ui` dump, and `pixels` for colours (e.g. "glyph under the highlighter is (0,0,0), paper is yellow"). Take screenshots with `shot` and save them as evidence in `handoffs/<N>-evidence/`, but **don't open them**: each one costs thousands of tokens. The orchestrator looks at the `-small.png` copies when a check is visual (layout, look, misplaced Ink).

## Traps found so far

- Snackbar text doesn't show in uiautomator dumps: check the message with `pixels`, or leave it to the orchestrator's screenshot look.
- Compose reports a `selectable` choice as **checked** (`checked="true"`), not selected, to UiAutomator.
- `device_test` ends by killing the app, which clears the undo history. Run undo/redo checks, and clean up test Ink by undo, **before** any `device_test` run, and before closing the Sheet.
- The eraser's Stroke/Area mode chips both report `checked="false"`: tap the mode you need instead of reading it.
- The `ui` dump reports `enabled="true"` on an Icon node always; the `IconButton` node one line above holds the real value.
- Clean up test Ink on the test Sheet before closing it, and guard the user's Sheets with `ids_same` before and after. A `delete_strokes <sheetId>` helper (a small permanent androidTest) doesn't exist yet; build it in the first issue that needs it.
- A pattern for `tap_text`/`center_of`/`has_text` is already anchored inside `text="…"`: don't add `^`/`$`.
- Samsung's S Pen Air command icon floats over every app (bottom left): check `ui` before calling a floating control the app's.
- For `selectable` choices use `clearAndSetSemantics { contentDescription }` after `selectable`, so `checked` and the description sit on one node; otherwise a one-line grep misses it.
- Material's drag slop eats the first pixels of a slider drag: extend the drag past the end. A long press needs `hold_drag`.
- UiAutomator `pinchOpen(p)`/`pinchClose(p)` move between the object's corners and points `p`·half-size in from them.

## Raw commands (debug build)

- Launch: `adb shell am start -n com.violinsheet/com.violinsheet.android.MainActivity`
- Screenshot: `adb exec-out screencap -p > shot.png`
- Crashes: `adb logcat -d -b crash`
- Database: copy `violinsheet.db`, `-wal` and `-shm` with `adb exec-out run-as com.violinsheet cat databases/<file>` and open them with Python's `sqlite3`.
- From Git Bash, prefix adb with `MSYS_NO_PATHCONV=1` (device.sh does), or `/sdcard/...` becomes `C:/Program Files/Git/sdcard/...`. Pass local files as Windows paths (`C:/...`).
