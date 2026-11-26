package com.violinsheet.core.data

/**
 * Name for a newly imported Sheet: the file name without ".pdf", numbered "<name> (2)", "(3)" …
 * when that name is already taken. Names compare ignoring case, as the list sorts them, so
 * "etude" next to "Etude" doesn't give two Sheets that look the same.
 */
fun sheetNameFor(fileName: String, takenNames: Collection<String>): String {
    val base = (if (fileName.endsWith(".pdf", ignoreCase = true)) fileName.dropLast(4) else fileName)
        .trim()
        .ifEmpty { "Sheet" }
    val taken = takenNames.mapTo(HashSet()) { it.lowercase() }
    if (base.lowercase() !in taken) return base
    return generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it.lowercase() !in taken }
}
