package com.violinsheet.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetNamingTest {
    @Test fun dropsPdfExtension() = assertEquals("Bach Partita 2", sheetNameFor("Bach Partita 2.pdf", emptyList()))

    @Test fun dropsUppercaseExtension() = assertEquals("Etude", sheetNameFor("Etude.PDF", emptyList()))

    @Test fun keepsNameWithoutExtension() = assertEquals("Etude", sheetNameFor("Etude", emptyList()))

    @Test fun onlyDropsTrailingExtension() = assertEquals("a.pdf.b", sheetNameFor("a.pdf.b.pdf", emptyList()))

    @Test fun secondImportGetsTwo() = assertEquals("Etude (2)", sheetNameFor("Etude.pdf", listOf("Etude")))

    @Test fun thirdImportGetsThree() =
        assertEquals("Etude (3)", sheetNameFor("Etude.pdf", listOf("Etude", "Etude (2)")))

    @Test fun fillsFirstFreeNumber() =
        assertEquals("Etude (2)", sheetNameFor("Etude.pdf", listOf("Etude", "Etude (3)")))

    @Test fun baseNameFreeAgainAfterRename() = assertEquals("Etude", sheetNameFor("Etude.pdf", listOf("Etude (2)")))

    @Test fun clashIgnoresCase() = assertEquals("etude (2)", sheetNameFor("etude.pdf", listOf("Etude")))

    @Test fun blankNameFallsBack() = assertEquals("Sheet", sheetNameFor(".pdf", emptyList()))
}
