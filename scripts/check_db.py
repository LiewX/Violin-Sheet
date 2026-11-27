"""Readable views of the app's database, for tablet checks. Called by `check_db` and `ids_same` in device.sh.

    check_db.py <db> counts           strokes per Sheet
    check_db.py <db> strokes <sheet>  one summary line per stroke (points decoded)
    check_db.py <db> ids <sheet>      stroke ids, one per line, sorted
"""
import sqlite3
import struct
import sys

POINT = struct.Struct("<fffi")  # x, y, pressure, elapsed ms; after a version byte (InkPointCodec.kt)


def points(blob):
    return [POINT.unpack_from(blob, 1 + i * POINT.size)[:2] for i in range((len(blob) - 1) // POINT.size)]


def summary(row):
    sid, page, tool, size, blob = row
    p = points(blob)
    xs, ys = [x for x, _ in p], [y for _, y in p]
    f = lambda x, y: f"({x:.2f},{y:.2f})"
    return (f"id={sid} page={page} tool={tool} size={size:.3f} n={len(p)} first={f(*p[0])} last={f(*p[-1])} "
            f"centroid={f(sum(xs) / len(p), sum(ys) / len(p))} box=[{min(xs):.2f},{min(ys):.2f},{max(xs):.2f},{max(ys):.2f}]"
            if p else f"id={sid} page={page} tool={tool} size={size:.3f} n=0")


def main(path, mode, sheet=None):
    if mode in ("strokes", "ids") and sheet is None:
        sys.exit(__doc__)
    con = sqlite3.connect(path)
    if mode == "counts":
        for sid, n in con.execute("SELECT s.id, COUNT(k.id) FROM Sheet s LEFT JOIN InkStroke k ON k.sheetId = s.id GROUP BY s.id"):
            print(f"Sheet {sid}: {n} strokes")
    elif mode == "strokes":
        for row in con.execute("SELECT id, pageIndex, tool, size, points FROM InkStroke WHERE sheetId = ? ORDER BY id", (int(sheet),)):
            print(summary(row))
    elif mode == "ids":
        for (sid,) in con.execute("SELECT id FROM InkStroke WHERE sheetId = ? ORDER BY id", (int(sheet),)):
            print(sid)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    assert points(b"\x01" + POINT.pack(1.5, 2.5, 0.5, 7)) == [(1.5, 2.5)]  # self-check of the point layout
    main(*sys.argv[1:])
