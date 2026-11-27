# Helpers for checking the app on the tablet over adb from Git Bash.
# Usage: source scripts/device.sh, then e.g. `install_app; launch; tap_text "Import PDF"; has_text "Scales"`.

export MSYS_NO_PATHCONV=1 # Git Bash would otherwise rewrite device paths like /sdcard
ADB="${ANDROID_HOME:-$HOME/scoop/apps/android-clt/current}/platform-tools/adb.exe"
APP=com.violinsheet

adb() { "$ADB" "$@"; }

install_app() { ./gradlew :androidApp:installDebug -q --console=plain 2>&1 | grep -vE "^\[=|^$|SDK XML|strip the following"; }

launch() {
    adb shell am force-stop $APP
    adb logcat -c
    adb shell am start -n $APP/$APP.android.MainActivity >/dev/null
    sleep 2
}

# Current screen as uiautomator XML, one node per line.
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb exec-out cat /sdcard/ui.xml | sed 's/<node/\n<node/g'; }

# Centre of the first node whose text or content-desc matches the regex $1.
center_of() {
    ui | grep -E "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' |
        grep -oE '[0-9]+' | paste -sd' ' | awk '{ print int(($1+$3)/2), int(($2+$4)/2) }'
}

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
    python -I -c 'import sqlite3, sys; [print(r) for r in sqlite3.connect(sys.argv[1]).execute(sys.argv[2])]' "$dir/violinsheet.db" "$1"
    rm -rf "$dir"
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

# Runs an on-device test class from androidApp/src/androidTest, e.g. `device_test SheetGesturesDeviceTest`.
# Installs the app and test APKs and runs them with `am instrument`, so the app's data stays
# (connectedAndroidTest would uninstall the app afterwards).
device_test() {
    ./gradlew :androidApp:installDebug :androidApp:installDebugAndroidTest -q --console=plain 2>&1 | grep -vE "^\[=|^$|SDK XML|strip the following"
    [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "build failed; not running $1" >&2; return 1; }
    adb shell am instrument -w -e class "$APP.android.$1" "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tail -15
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
