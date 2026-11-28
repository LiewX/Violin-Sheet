# Helpers for checking the app on the tablet over adb from Git Bash.
# Usage: source scripts/device.sh, then e.g. `install_app; launch; tap_text "Import PDF"; has_text "Scales"`.

export MSYS_NO_PATHCONV=1 # Git Bash would otherwise rewrite device paths like /sdcard
ADB="${ANDROID_HOME:-$HOME/scoop/apps/android-clt/current}/platform-tools/adb.exe"
APP=com.violinsheet

adb() { "$ADB" "$@"; }

install_app() { ./gradlew :androidApp:installDebug -q --console=plain 2>&1 | grep -vE "^\[=|^$|SDK XML|strip the following"; }

launch() {
    adb shell settings put global stay_on_while_plugged_in 3 # stay awake on AC/USB power while testing
    adb shell am force-stop $APP
    adb logcat -c
    adb shell am start -n $APP/$APP.android.MainActivity >/dev/null
    sleep 2
}

# Current screen as uiautomator XML, one node per line.
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb exec-out cat /sdcard/ui.xml | sed 's/<node/\n<node/g'; }

# "x1 y1 x2 y2" of the first node whose text or content-desc matches the regex $1 (empty if none or no full bounds).
# The regex sits inside text="..." so it is already anchored: no ^ or $.
bounds_of() {
    ui | grep -E "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' |
        grep -oE '[0-9]+' | paste -sd' ' | awk 'NF==4'
}

# Centre of the first node matching regex $1 (empty unless all four bounds were found, so tap_text fails cleanly).
center_of() { bounds_of "$1" | awk '{ print int(($1+$3)/2), int(($2+$4)/2) }'; }

# Taps the node matching regex $1 with a finger; fails if there is none.
tap_text() {
    local xy
    xy=$(center_of "$1")
    [ -n "$xy" ] || { echo "not on screen: $1" >&2; return 1; }
    adb shell input touchscreen tap $xy
    sleep "${2:-1}"
}

# Replaces the text of the field matching regex $1 with $2: taps it first (keys sent before focus are lost),
# then clears it with MOVE_END plus DELs, since ctrl+A doesn't select over adb.
type_into() {
    tap_text "$1" || return 1
    adb shell input keyevent KEYCODE_MOVE_END
    adb shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq 80))
    adb shell input text "${2// /%s}" # input text takes %s for a space
}

# Pattern is anchored like center_of's.
has_text() { ui | grep -qE "(text|content-desc)=\"$1\""; }

# Copies the app's database (debug build) into a temp folder and prints the folder.
pull_db() {
    local dir
    dir=$(cygpath -m "$(mktemp -d)") # Windows Python needs a Windows path
    for f in violinsheet.db violinsheet.db-wal violinsheet.db-shm; do adb exec-out run-as $APP cat databases/$f >"$dir/$f" 2>/dev/null; done
    echo "$dir"
}

# Runs SQL against the app's database and prints the rows.
db() {
    local dir; dir=$(pull_db)
    python -I -c 'import sqlite3, sys; [print(r) for r in sqlite3.connect(sys.argv[1]).execute(sys.argv[2])]' "$dir/violinsheet.db" "$1" | tr -d '\r'
    rm -rf "$dir"
}

# Prints the first column of each row of SQL $1, plain (no Python repr), LF only. Fails if the database can't be read.
db_col() {
    local dir rc; dir=$(pull_db)
    python -I -c 'import sqlite3, sys; [sys.stdout.write(str(r[0]) + "\n") for r in sqlite3.connect(sys.argv[1]).execute(sys.argv[2])]' "$dir/violinsheet.db" "$1" | tr -d '\r'; rc=${PIPESTATUS[0]}
    rm -rf "$dir"; return $rc
}

# Prints one Setting's stored value (empty if it holds the default).
setting() { db_col "select value from SettingValue where key='$1'"; }

# Saves all SettingValue rows as key=value lines to file $1. Fails (leaving $1 untouched) if the dump is empty or malformed.
save_settings() {
    local tmp="$1.tmp"
    db_col "select key || '=' || value from SettingValue order by key" >"$tmp" || { rm -f "$tmp"; echo "save_settings: reading the database failed" >&2; return 1; }
    settings_file_ok "$tmp" || { rm -f "$tmp"; return 1; }
    mv "$tmp" "$1"
}

# Succeeds if file $1 is non-empty, every line is key=value, and no value holds ' ~ or a backslash.
settings_file_ok() {
    [ -s "$1" ] || { echo "settings file $1 is empty" >&2; return 1; }
    grep -qvE '^[A-Za-z0-9._]+=' "$1" && { echo "settings file $1 has a malformed line" >&2; return 1; }
    grep -qE "['~\\]" "$1" && { echo "settings file $1: a value holds ' ~ or a backslash" >&2; return 1; }
    return 0
}

# Puts the rows saved in $1 back through RestoreSettingsDeviceTest (deletes the current keys, stores the saved ones),
# relaunches, and diffs a fresh dump against the file. Refuses an empty or malformed file before touching the tablet.
restore_settings() {
    settings_file_ok "$1" || return 1
    local keys put out
    keys=$(db_col "select key from SettingValue" | paste -sd,)
    put=$(paste -sd'~' "$1")
    ./gradlew :androidApp:installDebugAndroidTest -q --console=plain 2>&1 | grep -vE "^\[=|^$|SDK XML|strip the following"
    out=$(adb shell "am instrument -w -e class $APP.android.RestoreSettingsDeviceTest -e keys '$keys' -e put '$put' $APP.test/androidx.test.runner.AndroidJUnitRunner")
    echo "$out" | tail -3
    echo "$out" | grep -q '^OK (' || { echo "restore_settings: instrumentation failed" >&2; return 1; }
    launch; sleep 2
    save_settings "$1.now" || return 1
    diff --strip-trailing-cr "$1" "$1.now" && echo "settings restored: no diff"; local rc=$?; rm -f "$1.now"; return $rc
}

# Strokes per Sheet; with a Sheet id, also one decoded line per stroke (id, size, points, first/last, centroid, box).
check_db() {
    local dir; dir=$(pull_db)
    python -I scripts/check_db.py "$dir/violinsheet.db" counts
    [ -n "$1" ] && python -I scripts/check_db.py "$dir/violinsheet.db" strokes "$1"
    rm -rf "$dir"
}

# Guards the user's own Ink: `ids_same <file> [sheet, default 1]`. The first call saves the Sheet's stroke ids to the
# file; later calls compare and print "same N ids" or the ids added (+) and gone (-), returning 1 on any change.
ids_same() {
    local dir now rc; dir=$(pull_db)
    now=$(python -I scripts/check_db.py "$dir/violinsheet.db" ids "${2:-1}"); rc=$?; rm -rf "$dir"
    [ $rc -eq 0 ] || { echo "ids_same: reading the database failed" >&2; return 2; }
    [ -f "$1" ] || { echo "$now" >"$1"; echo "saved $(grep -c . "$1") ids"; return 0; }
    if [ "$now" = "$(cat "$1")" ]; then echo "same $(grep -c . "$1") ids"; else diff <(cat "$1") <(echo "$now") | grep -E '^[<>] .' | sed 's/^</-/;s/^>/+/'; return 1; fi
}

# One pen stroke through the points: `stroke 100 200 300 200 300 400` (screen pixels). Draws lassos too.
stroke() {
    [ $# -ge 4 ] && [ $(($# % 2)) -eq 0 ] || { echo "usage: stroke x1 y1 x2 y2 ..." >&2; return 2; }
    local cmd="input stylus motionevent DOWN $1 $2"
    shift 2
    while [ $# -gt 2 ]; do cmd+="; input stylus motionevent MOVE $1 $2"; shift 2; done
    adb shell "$cmd; input stylus motionevent UP $1 $2"
}

# Runs an on-device test class from androidApp/src/androidTest, e.g. `device_test SheetGesturesDeviceTest`. Further
# arguments go to `am instrument` as they are, e.g. `device_test EditSideButtonDeviceTest -e tapX 1000 -e tapY 300`.
# Installs the app and test APKs and runs them with `am instrument`, so the app's data stays
# (connectedAndroidTest would uninstall the app afterwards).
device_test() {
    ./gradlew :androidApp:installDebug :androidApp:installDebugAndroidTest -q --console=plain 2>&1 | grep -vE "^\[=|^$|SDK XML|strip the following"
    [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "build failed; not running $1" >&2; return 1; }
    adb shell am instrument -w -e class "$APP.android.$1" "${@:2}" "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tail -15
}

crashes() { adb logcat -d -b crash | tail -20; }

# Saves a screenshot as $1.png plus a 1000 px wide $1-small.png for looking at (a full one costs ~3k tokens to read).
# Uses Windows' System.Drawing through PowerShell, so no Python imaging library is needed. Paths must not contain a '.
shot() {
    adb exec-out screencap -p >"$1.png" || return 1
    powershell.exe -NoProfile -Command "Add-Type -AssemblyName System.Drawing
        \$s = [System.Drawing.Image]::FromFile('$(cygpath -w "$1.png")')
        \$h = [int](\$s.Height * 1000 / \$s.Width)
        \$d = New-Object System.Drawing.Bitmap \$s, 1000, \$h
        \$d.Save('$(cygpath -w "$1-small.png")', [System.Drawing.Imaging.ImageFormat]::Png)
        \$d.Dispose(); \$s.Dispose()"
}

# Prints the RGB at each point of a PNG, in its own pixel coordinates: `pixels shot.png 600,718 760,718`.
pixels() {
    local png
    png=$(cygpath -w "$1")
    shift
    powershell.exe -NoProfile -Command "Add-Type -AssemblyName System.Drawing
        \$b = [System.Drawing.Bitmap]::FromFile('$png')
        foreach (\$p in '$*'.Split(' ')) { \$x, \$y = \$p.Split(','); \$c = \$b.GetPixel([int]\$x, [int]\$y); \"\$p (\$(\$c.R), \$(\$c.G), \$(\$c.B))\" }
        \$b.Dispose()"
}

# Content-desc of every checked node (toolbar choices).
selected() { ui | grep 'checked="true"' | grep -oE 'content-desc="[^"]*"' | sed 's/^content-desc="//;s/"$//'; }

# Long press then drag with the pen: `hold_drag x1 y1 x2 y2`. Repeated `input` calls can't hold (~70 ms per process).
hold_drag() {
    adb shell "input stylus motionevent DOWN $1 $2; sleep 1; for i in 1 2 3 4 5; do input stylus motionevent MOVE \$(( $1 + ($3 - $1) * i / 5 )) \$(( $2 + ($4 - $2) * i / 5 )); done; input stylus motionevent UP $3 $4"
}

# Result tracking lives in this shell (never call ok/fail in a subshell or a pipe): `ok msg`, `fail msg`, `finish`.
: "${CHECK_FAILS:=0}"
ok() { echo "ok: $1"; }
fail() { echo "FAIL: $1"; CHECK_FAILS=$((CHECK_FAILS + 1)); }
finish() {
    if [ "$CHECK_FAILS" -eq 0 ]; then echo PASS; else echo "FAIL: $CHECK_FAILS failed"; fi
    local rc=$CHECK_FAILS; CHECK_FAILS=0; [ "$rc" -eq 0 ]
}

# `expect label actual expected`: ok if equal and non-empty, else fail.
expect() {
    if [ -n "$2" ] && [ "$2" = "$3" ]; then ok "$1 = $2"; else fail "$1: got '$2', expected '$3'"; fi
}

# Saves $IDS_DIR/ids-<sheet>.txt (default .) for each user's Sheet given; guard_end checks each still has the same ids.
guard_start() {
    local s f
    for s in "$@"; do
        f="${IDS_DIR:-.}/ids-$s.txt"; rm -f "$f"
        ids_same "$f" "$s" >/dev/null || fail "Sheet $s: guard_start could not read ids"
    done
}
guard_end() {
    local s f out
    for s in "$@"; do
        f="${IDS_DIR:-.}/ids-$s.txt"
        [ -f "$f" ] || { fail "Sheet $s: no saved ids (guard_start failed?)"; continue; }
        if out=$(ids_same "$f" "$s"); then ok "Sheet $s: $out"; else fail "Sheet $s: $out"; fi
    done
}
