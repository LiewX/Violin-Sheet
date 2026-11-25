# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Violin sheet music app for **Samsung Galaxy Tab S9+ (Android 13+)**. The S Pen is the main input.

- **V1.0** = Feature 1 (annotation editing).
- **Features 2–4** = post-V1.0. Feature 3 is automatic page flipping.

## Working Style

- **Ask before assuming** when a requirement is unclear (e.g. PDF vs. vector rendering): present the tradeoff, don't pick silently.
- **Justify architectural decisions** in one or two sentences when you introduce them.
- **Don't implement post-V1.0 features**, even if asked in passing. Note them in `PROGRESS.md` instead.
- **Update `PROGRESS.md` after every session** (see [Progress Tracking](#progress-tracking)).

## Tech Stack

| Area | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | — |
| Stylus input | Android Ink API (Jetpack) | ~4ms latency on Samsung devices; handles stroke geometry and palm rejection natively. Do not build from scratch. |
| Sheet rendering | **Undecided.** Compare PDF overlay with MusicXML/MEI vector rendering and justify the choice before writing code. | The default lean is PDF overlay: imported sheets are scans or PDFs. "Printed-look" annotations may later need a notation-aware layer. |
| Converted annotations | Symbol palette, no handwriting recognition in V1.0 | On-device ML for notation is a large research effort of its own; a palette gives the printed look right away. Leave the ML pipeline as a post-V1.0 TODO behind a defined interface. |

## Architecture

- **Modules:** `:app`, `:core:pdf`, `:core:ink`, `:core:data`, `:feature:viewer`, `:feature:annotation`. Stub `:feature:metronome` for later.
- **State:** MVVM with Kotlin `StateFlow`.
- **Undo/redo:** command-pattern undo stack from day one, covering both raw and converted annotations. Annotation editing without solid undo is unusable, and adding it later is costly.
- **Layers:** raw ink strokes and converted symbols live on separate layers, each with its own visibility toggle. This lets printed-look symbols be edited and saved separately from freehand ink.

## UI/UX

- **Toolbar:** a slim palette that stays at the bottom edge, within thumb and S Pen reach. No modal dialogs for switching tools.
- **Palm rejection:** rely on Android's built-in stylus palm rejection. Verify that finger touches during pen input do not create strokes.
- **Converted annotations:** two steps with a ghost preview (select symbol → tap to place → ghost preview → confirm), so placing a printed-look symbol feels deliberate.
- **Page transitions:** pre-render at least the next page so page turns are instant and silent. Automatic page flipping (Feature 3) needs zero blank-flash latency.
- **Surround:** neutral dark grey (not pure black) around the sheet to reduce eye strain. Never invert the sheet itself.

## Feature 1 Scope (V1.0)

**Raw annotation tools** (stay as raw ink): pen, highlighter, eraser (area and stroke modes), adjustable size and color.

**Converted annotation types**, implement in this order:

1. Fingerings (number placement)
2. Articulations (tenuto, fermata)
3. Bowing marks (up/down)
4. Ties/slurs (curved lines between anchor points)

**Deferred to V1.1:** sharps/flats with spacing adjustment. Accidentals need note/barline reflow, which is structurally harder and should not block V1.0.

## Progress Tracking

Maintain `PROGRESS.md` with this table:

| Feature | Sub-feature | Status | Next Step |
|---|---|---|---|

Status is one of: Not Started / In Progress / Done / Deferred.

**V1.0 scope:** project setup, PDF rendering, raw ink layer, converted annotation palette (items 1–3 above), undo/redo, persistence. Mark everything else "Post-V1.0".

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
