# Progress

Status overview. Scope is in [`docs/SPECS.md`](docs/SPECS.md); detailed work items are in GitHub Issues.

| Feature | Sub-feature | Status | Next Step |
|---|---|---|---|
| Setup | Docs: spec, glossary, ADR 0001–0003, agent skills, directory layout (`CLAUDE.md`), [workflow ideas](docs/workflow-ideas.md), [orchestration](.claude/skills/orchestrate/SKILL.md) with `model:` labels on #7–#21, `implementer`/`reviewer` agent types and the token review (2026-11-26); spec re-reviewed 2026-11-26 | Done | V1.0 is broken into GitHub issues #1–#21 (`ready-for-agent`, with blocking links) |
| Setup | Project skeleton (Kotlin Multiplatform modules, Compose Multiplatform, Room storage — ADR 0003) | Done | #1 verified on the Galaxy Tab S9+ 2026-11-26. Android Ink API 1.0 added in #6 |
| 1. Annotation editing | Sheet import and list (rename, delete with undo bar, backup without PDFs + hash reattach) | Done | #1, #3. A real Google backup/restore is only simulated (PDF files deleted); see `docs/manual-checks.md` |
| 1. Annotation editing | PDF Page rendering, zoom/pan, page turns, 1-up/2-up layout | Done | #2, #4 |
| 1. Annotation editing | Ink mode: pen, highlighter, eraser, side-button erase | Done | #6, #8 |
| 1. Annotation editing | Ink lasso: move, resize, rotate, copy/paste within a Sheet, delete | In Progress | #10 done: lasso (fully inside; optional partly-enclosed switch), tap to select, move/resize/rotate/delete with undo. Copy/paste is #11 (after #22) |
| 1. Annotation editing | Colour picker and size sliders | Done | #9: HSV picker and size sliders in a panel above the toolbar; the same picker on the Settings page. Token use to compare with #8 at the next cleanup |
| 1. Annotation editing | Saved colour palettes and editable size presets | In Progress | #22 part 1 (palettes, + Add colour, editing in the Sheet) built, uncommitted; part 2 (Settings → Ink) and review next: `handoffs/22-resume.md` |
| 1. Annotation editing | Fixes from use (2026-11-27): toolbar keeps one width, palm rejection for navigation, strokes split at Page edges | Done | #23 toolbar, #24 palm rules, #25 strokes split at Page edges. Next: #22, then #11 |
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
| 1. Annotation editing | Settings page (feature-declared app-wide defaults) | In Progress | Page, registry and Reading Setting done (#5); Ink, Edit, Text and Links Settings arrive with their features |
| 1. Annotation editing | Undo/redo | In Progress | Undo stack, Ink draw/highlight/erase undo done (#7, #8); lasso, Symbols, Covers and Text boxes add their commands with their features |
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
| 1. Annotation editing | Per-Sheet and per-Page settings | Post-V1.0 | — |
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
