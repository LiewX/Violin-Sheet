# Sheets are PDF pages; all Annotations are overlays

Sheets are imported only as PDFs and drawn as page images. Every Annotation (Ink, Symbol, Cover) lives in layers on top and is saved separately, so the imported PDF is never modified. We chose this over MusicXML/MEI vector rendering because the player's Sheets are scans, and turning a scan into notation needs optical music recognition, which is unreliable.

## Consequences

- Symbols are positioned on the page, not attached to notes. The app does not know where notes or barlines are, which is why sharps/flats that need spacing changes are deferred.
- "Removing" a Printed marking means placing a Cover over it. The original pixels stay in the PDF.
- Feature 2 (straightening pages) changes page geometry, so it will have to move existing Annotations along with the page.
