# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cross-platform violin sheet music app. V1.0 ships on **Samsung Galaxy Tab S9+ (Android 13+)** with the S Pen; iPad with Apple Pencil follows after V1.0. The pen is the main input.

- **What to build:** [`docs/SPECS.md`](docs/SPECS.md). V1.0 is Feature 1 (annotation editing); Features 2–4 (sheet fixing, automatic page flipping, metronome) are Post-V1.0; Nice-to-have items come after a stable deployment.
- **Vocabulary:** [`GLOSSARY.md`](GLOSSARY.md). Use its terms (Sheet, Page, Printed marking, Ink, Symbol, Cover, Text box, Link, Ink mode, Edit mode, Text mode) in code, issues and docs.
- **Decisions:** [`docs/adr/`](docs/adr/).
- **Status:** [`PROGRESS.md`](PROGRESS.md).

## Working Style

- **The main session** (the orchestrator) loads the `orchestrate` skill at session start, before any issue work, grilling or cleanup. It holds the working style, the issue loop, progress tracking and the cleanup with its token review.
- **Subagents** follow their agent file (`.claude/agents/implementer.md`, `reviewer.md`) and the plan.
- **For everyone:** don't implement Post-V1.0 or Nice-to-have features, even if asked in passing; don't commit unless you are the orchestrator.

## Tech Stack

| Area | Choice | Why |
|---|---|---|
| Language / UI | Kotlin Multiplatform + Compose Multiplatform | One codebase for Android and iPad; native ink per platform. See [ADR 0002](docs/adr/0002-kotlin-multiplatform-with-native-ink.md). |
| Stylus input | Android: Android Ink API (Jetpack). iOS (after V1.0): PencilKit or Metal canvas | Native low-latency inking (~4ms on Samsung) and palm rejection. Do not build from scratch. Strokes are stored in a shared format, not Ink API types. |
| Sheet rendering | PDF pages as images, Annotations as overlay layers | Sheets are scans; vector notation would need unreliable OMR. See [ADR 0001](docs/adr/0001-pdf-pages-with-overlay-annotations.md). |
| Storage | Room (KMP) in `:core:data` | Typed queries, migrations and `Flow` on Android and iOS. See [ADR 0003](docs/adr/0003-room-for-local-storage.md). |
| Symbols | Palette placement, no handwriting recognition in V1.0 | On-device notation recognition is a research effort of its own; a palette gives the printed look right away. |

## Build & run

**Toolchain (Windows):** JDK 17 and the Android SDK, both installed with scoop (`java/temurin17-jdk`, `main/android-clt`, SDK platform 36). At session start, check `java -version` and `"$ANDROID_HOME/platform-tools/adb.exe" version`. If `java` is missing in Git Bash, run `export JAVA_HOME=~/scoop/apps/temurin17-jdk/current`. A fresh clone needs `ANDROID_HOME` set, or a `local.properties` with `sdk.dir`.

| Purpose | Command | Time |
|---|---|---|
| Check after each edit | `./gradlew :core:data:testDebugUnitTest :androidApp:assembleDebug` | ~15 s warm |
| Before committing | `./gradlew build` (all tests, lint, release variants) | ~5–7 min |
| Install on the tablet | `./gradlew :androidApp:installDebug` | ~15 s |

- **Edits and new files:** new files with the Write tool, small changes with the Edit tool. An edit script, only for a mechanical change across many files, is written with the Write tool into the scratchpad (never the repo, so the tree stays clean), then run with `python -I <file>`; it must pass `newline="\n"` to `write_text`/`open` (Windows Python otherwise writes CRLF; Git Bash can't source a CRLF `device.sh`). A project hook (`.claude/hooks/block_inline_edits.py`) blocks inline edits for every agent: `python -c` that writes, Python heredocs, heredocs into files and `sed -i`. These silently miss (no match, CRLF), repeat when run twice, and mangle `\` and `$` through several quoting layers. The Edit tool fails loudly on a missing or ambiguous match instead. Read-only heredocs (commit messages, `gh` bodies) stay allowed. Long multi-line heredocs passed to the Bash tool fail to parse (`unexpected EOF while looking for matching '`), and then nothing runs.
- **Read build output** with `--console=plain 2>&1 | grep -vE "^> Task|^\[=|^$"`. It drops the task lines but keeps every warning and error. Don't pipe Gradle into `head`, which cuts off the error. If a build fails with "The process cannot access the file because it is being used by another process" (a daemon holds a jar), run `./gradlew --stop` and rebuild.
- **Tablet checks:** load the `device-check` skill (helpers in `scripts/device.sh`, pen strokes, screenshots, traps). `adb` lives at `$ANDROID_HOME/platform-tools/adb.exe`.
- **The tablet holds the user's own data** (Sheets, Ink). Never run `connectedAndroidTest` (it uninstalls the app). Never `pm clear` or uninstall the app: subagents never, the orchestrator only after asking the user. Never change a database version that has already been installed on the tablet, even before it is committed: add the next version with a migration instead.
- **Versions** live only in `gradle/libs.versions.toml`. Its header lists which versions must move together.

## Architecture

- **Modules:** see [Directory layout](#directory-layout). Platform code (ink canvas, PDF rendering, file picker, pen hardware mapping) goes in `androidMain`/`iosMain`; everything else in `commonMain`.
- **State:** MVVM with Kotlin `StateFlow`.
- **Settings:** each feature module declares its own Settings; the settings page is generated from those declarations. Never hard-code a feature's Setting in the page.
- **Undo/redo:** command-pattern undo stack from day one, covering Ink (including lasso actions), Symbols, Covers and Text boxes. Annotation editing without solid undo is unusable, and adding it later is costly.
- **Layers:** Ink, Edit mode content (Covers under Symbols) and Text boxes live on three separate layers, each with its own visibility toggle.
- **Coordinates:** Annotations and stroke sizes are stored in Page coordinates, never screen pixels, so they stay put across zoom, rotation and layout.

## Directory layout

```
CLAUDE.md, GLOSSARY.md, PROGRESS.md   Root docs (skills expect GLOSSARY.md at the root)
docs/
  SPECS.md                            What to build
  adr/                                Decisions, numbered NNNN-title.md
  agents/                             Config for the agent skills (issue tracker, labels, domain docs)
  workflow-ideas.md                   Workflow problems and fixes, reviewed at each cleanup
.claude/                             Project settings (skills turned off), agents/ (implementer, reviewer), skills/ (orchestrate, device-check)
gradle/libs.versions.toml             Every dependency and plugin version, nowhere else
buildSrc/                             Convention plugins (violinsheet.kmp-library: shared KMP + Android setup)
androidApp/                           Android shell: Application, MainActivity, manifest, wiring. No feature logic
iosApp/                               (after V1.0) Xcode project
core/data/                            Room database, entities, DAOs, repositories, imported-file storage
  schemas/                            Exported Room schemas, kept in git for migrations
core/pdf/                             PDF Page rendering (Android PdfRenderer, iOS PDFKit)
core/ink/                             Shared stroke format; native ink canvas per platform
feature/viewer/                       Home screen (Sheet list) and Page viewer: layout, page turns, zoom
feature/annotation/                   Modes, toolbar, Symbols, Covers, Text boxes, layers, undo stack
feature/settings/                     The Settings page, generated from each feature's declared Settings; its controls (colour picker, step slider) are shared with the toolbars
feature/metronome/                    Stub (Post-V1.0)
scripts/                              Dev helpers: device.sh (tablet) with check_db.py, agent-tokens.py (subagent token review)
graphify-out/                         Generated knowledge graph, git-ignored
handoffs/                             Orchestrator ↔ implementer plan and report files, git-ignored, deleted after each push
```

- Inside a module: `src/{commonMain,androidMain,iosMain,commonTest}/kotlin/com/violinsheet/<module>/`, where the package matches the Gradle path (`:core:data` → `com.violinsheet.core.data`).
- One main class per file, named after it. A platform file that is the counterpart of a common file of the same name takes a `.android.kt`/`.ios.kt` suffix (`AppDatabase.android.kt` builds the `AppDatabase` from `AppDatabase.kt`).
- Keep packages flat until a module has around ten files, then split by feature area (`viewer/home`, `viewer/page`), not by layer.
- A new shared module needs an entry in `settings.gradle.kts`, a row in this tree and the `violinsheet.kmp-library` plugin.

## UI/UX

- **Toolbar:** a slim palette that stays at the bottom edge, within thumb and pen reach. It keeps one width and position whatever the mode or tool (sized for Edit mode's, the widest): the mode switch, Undo/Redo and Layers stay put, and the tool-specific controls sit in a slot as wide as the widest tool's, scrolling sideways where the screen is too narrow (portrait), so no button moves under the pen. Layout: `docs/SPECS.md` → Modes. No modal dialogs for switching tools or picking colours.
- **Input split:** the pen draws and edits; fingers only navigate (turn pages, zoom, open Links), except that a finger tap clears the selection. Rely on the platform's palm rejection for pen input; finger navigation adds the palm rules in `docs/SPECS.md` (pen-near window, tap and flick timing, pinch threshold). Verify that finger touches during pen input do not create strokes.
- **Symbol placement:** select Symbol → pen down on the Page → it shows under the tip, follows the drag and is placed and selected at the lift (no confirm step; undo removes it).
- **Page transitions:** pre-render at least the next Page so page turns are instant and silent. Automatic page flipping (Feature 3) needs zero blank-flash latency.
- **Surround:** neutral dark grey (not pure black) around the Sheet to reduce eye strain. Never invert the Sheet itself.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `/graphify query "<question>"` before your first grep, search or source-file read for a codebase question. Read raw files only for lines the graph or an earlier read already located. Use `/graphify path "<A>" "<B>"` for relationships and `/graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Search order: graphify always comes first. After it, use Grep for exact matches (symbol tracing, refactoring, regex) and `mgrep "<natural-language query>"` for intent search (code exploration, feature discovery, onboarding). This overrides the mgrep plugin's "replaces all built-in search tools" rule.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- Only the orchestrator updates the graph, after each commit that changes code (`orchestrate` skill).

## Agent skills

- **Issue tracker:** GitHub Issues via `gh` (`docs/agents/issue-tracker.md`). **Triage labels**, including the `model:opus`/`model:sonnet` label each issue carries: `docs/agents/triage-labels.md`. **Domain docs:** `docs/agents/domain.md`.
- **Orchestration:** the `orchestrate` skill (main session); agent types in `.claude/agents/`; tablet checks in the `device-check` skill.
