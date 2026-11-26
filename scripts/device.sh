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

# Runs SQL against the app's database (debug build) and prints the rows.
db() {
    local dir
    dir=$(cygpath -m "$(mktemp -d)") # Windows Python needs a Windows path
    for f in violinsheet.db violinsheet.db-wal violinsheet.db-shm; do adb exec-out run-as $APP cat databases/$f >"$dir/$f" 2>/dev/null; done
    python -I -c 'import sqlite3, sys; [print(r) for r in sqlite3.connect(sys.argv[1]).execute(sys.argv[2])]' "$dir/violinsheet.db" "$1"
    rm -rf "$dir"
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
