package com.violinsheet.core.ink

// A stroke's points packed as bytes for storage: a version byte, then per point x, y, pressure (float32) and
// elapsed milliseconds (int32), all little-endian. Compact (16 bytes a point) and readable on any platform.

private const val VERSION: Byte = 1
private const val BYTES_PER_POINT = 16

fun encodeInkPoints(points: List<InkPoint>): ByteArray {
    val bytes = ByteArray(1 + points.size * BYTES_PER_POINT)
    bytes[0] = VERSION
    var at = 1
    fun put(value: Int) {
        for (shift in 0 until 32 step 8) bytes[at++] = (value ushr shift).toByte()
    }
    for (point in points) {
        put(point.x.toRawBits())
        put(point.y.toRawBits())
        put(point.pressure.toRawBits())
        put(point.elapsedMillis.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }
    return bytes
}

fun decodeInkPoints(bytes: ByteArray): List<InkPoint> {
    require(bytes.isNotEmpty() && bytes[0] == VERSION) { "Unknown Ink point format ${bytes.firstOrNull()}" }
    require((bytes.size - 1) % BYTES_PER_POINT == 0) { "Truncated Ink points: ${bytes.size} bytes" }
    var at = 1
    fun int(): Int {
        var value = 0
        for (shift in 0 until 32 step 8) value = value or ((bytes[at++].toInt() and 0xFF) shl shift)
        return value
    }
    return List((bytes.size - 1) / BYTES_PER_POINT) {
        InkPoint(Float.fromBits(int()), Float.fromBits(int()), Float.fromBits(int()), int().toLong())
    }
}
