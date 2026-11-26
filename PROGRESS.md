# Progress

Status overview. Scope is in [`docs/SPECS.md`](docs/SPECS.md); detailed work items are in GitHub Issues.

| Feature | Sub-feature | Status | Next Step |
|---|---|---|---|
| Setup | Docs: spec, glossary, ADR 0001–0002, agent skills; spec re-reviewed 2026-11-26 | Done | V1.0 is broken into GitHub issues #1–#21 (`ready-for-agent`, with blocking links) |
| Setup | Project skeleton (Kotlin Multiplatform modules, Compose Multiplatform, Room storage — ADR 0003) | Done | #1 verified on the Galaxy Tab S9+ 2026-11-26. Android Ink API comes with the Ink issues |
| 1. Annotation editing | Sheet import and list (rename, delete with undo bar, backup without PDFs + hash reattach) | In Progress | Import, naming, hash and list done (#1); rename, delete, backup reattach remain |
| 1. Annotation editing | PDF Page rendering, zoom/pan, page turns, 1-up/2-up layout | Not Started | — |
| 1. Annotation editing | Ink mode: pen, highlighter, eraser, side-button erase | Not Started | — |
| 1. Annotation editing | Ink lasso: move, resize, rotate, copy/paste within a Sheet, delete | Not Started | — |
| 1. Annotation editing | Colour picker and size sliders | Not Started | — |
| 1. Annotation editing | Symbols: fingerings | Not Started | — |
| 1. Annotation editing | Symbols: articulations | Not Started | — |
| 1. Annotation editing | Symbols: bowings | Not Started | — |
| 1. Annotation editing | Symbols: dynamic letters | Not Started | — |
| 1. Annotation editing | Symbols: ties/slurs | Not Started | — |
| 1. Annotation editing | Symbols: hairpins | Not Started | — |
| 1. Annotation editing | Symbol scale (staff-line detection + overrides) | Not Started | — |
| 1. Annotation editing | Covers | Not Started | — |
| 1. Annotation editing | Text mode: Text boxes (italic/regular) and Links | Not Started | — |
| 1. Annotation editing | Layers (Ink, Edit, Text) and visibility toggles | Not Started | — |
| 1. Annotation editing | Settings page (feature-declared app-wide defaults) | Not Started | — |
| 1. Annotation editing | Undo/redo | Not Started | — |
| 1. Annotation editing | Autosave | Not Started | — |
| 1. Annotation editing | Sharps/flats/naturals with spacing adjustment | Post-V1.0 | Needs music data |
| 1. Annotation editing | Paste Ink across Sheets | Post-V1.0 | — |
| 1. Annotation editing | Export/import Settings | Post-V1.0 | — |
| 1. Annotation editing | Handwriting to Symbols | Post-V1.0 | — |
| 1. Annotation editing | Gesture Covers (scribble/cross, side button in Edit mode) | Post-V1.0 | Decide which gesture |
| 1. Annotation editing | Free-form Cover shapes | Post-V1.0 | — |
| 1. Annotation editing | Edit mode eraser removes Printed markings (auto-Cover) | Post-V1.0 | — |
| 1. Annotation editing | Automatic bar counting | Post-V1.0 | — |
| 1. Annotation editing | Music data (MusicXML and/or OMR, hidden layer) | Post-V1.0 | Decide with Feature 3 |
| 1. Annotation editing | Flattened PDF export | Post-V1.0 | — |
| 1. Annotation editing | Folders and set lists | Post-V1.0 | — |
| Platform | iPad port (Apple Pencil) | Post-V1.0 | Decide hold-to-erase trigger; recheck Compose `UIKitView` interop |
| 2. Sheet fixing | Perspective correction, mesh warp, discolour fill | Post-V1.0 | — |
| 2. Sheet fixing | Crop and enlarge per Page | Post-V1.0 | — |
| 3. Automatic page flipping | Microphone listening | Post-V1.0 | Grill: music data vs recording alignment |
| 3. Automatic page flipping | Bluetooth page-turn device | Post-V1.0 | — |
| 4. Metronome | Built-in metronome | Post-V1.0 | — |
| Nice-to-have | Page navigation (partial flip, coda jumps, reflow) | Nice-to-have | — |
| Nice-to-have | Page editing (page deletion, merge across Sheets) | Nice-to-have | — |
| Nice-to-have | Annotation extras (custom/note-name Symbols, beautification, Edit-mode lasso) | Nice-to-have | — |
| Nice-to-have | Playback and audio (MIDI bars, synced metronome, imported audio, tuner) | Nice-to-have | — |
| Nice-to-have | Recording (front camera) | Nice-to-have | — |
| Nice-to-have | Practice streak | Nice-to-have | — |
| Nice-to-have | Appearance (custom background) | Nice-to-have | — |
| Nice-to-have | Ensemble (conductor score) | Nice-to-have | — |
