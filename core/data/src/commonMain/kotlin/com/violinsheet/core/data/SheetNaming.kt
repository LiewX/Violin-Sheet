package com.violinsheet.core.data

/**
 * Name for a newly imported Sheet: the file name without ".pdf", numbered "<name> (2)", "(3)" …
 * when that name is already taken.
 */
fun sheetNameFor(fileName: String, takenNames: Collection<String>): String {
    val base = (if (fileName.endsWith(".pdf", ignoreCase = true)) fileName.dropLast(4) else fileName)
        .trim()
        .ifEmpty { "Sheet" }
    if (base !in takenNames) return base
    return generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it !in takenNames }
}
