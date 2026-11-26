# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cross-platform violin sheet music app. V1.0 ships on **Samsung Galaxy Tab S9+ (Android 13+)** with the S Pen; iPad with Apple Pencil follows after V1.0. The pen is the main input.

- **What to build:** [`docs/SPECS.md`](docs/SPECS.md). V1.0 is Feature 1 (annotation editing); Features 2–4 (sheet fixing, automatic page flipping, metronome) are Post-V1.0; Nice-to-have items come after a stable deployment.
- **Vocabulary:** [`GLOSSARY.md`](GLOSSARY.md). Use its terms (Sheet, Page, Printed marking, Ink, Symbol, Cover, Text box, Link, Ink mode, Edit mode, Text mode) in code, issues and docs.
- **Decisions:** [`docs/adr/`](docs/adr/).
- **Status:** [`PROGRESS.md`](PROGRESS.md).

## Working Style

- **Ask before assuming** when a requirement is unclear: present the tradeoff, don't pick silently.
- **Justify architectural decisions** in one or two sentences when you introduce them. Record hard-to-reverse ones as an ADR.
- **Grill before big changes.** When a request changes scope, adds or removes a feature, or forces a significant design decision, run a grilling session (`/grill-with-docs`) before implementing. Update `docs/SPECS.md`, `GLOSSARY.md` and `docs/adr/` from its outcome.
- **Review the whole spec after every grilling.** Once its outcome is written into the docs, read `docs/SPECS.md` end to end (with `GLOSSARY.md`, `CLAUDE.md` and the ADRs) and check for contradictions, stale wording and gaps the new decisions created. Fix wording-only issues directly; bring anything that needs a decision back to the user.
- **Don't implement Post-V1.0 or Nice-to-have features**, even if asked in passing. Note them in `PROGRESS.md` instead.
- **Review before committing an issue.** Run `/code-review` (the `mattpocock-skills:code-review` skill) on the issue's changes: it checks them against the coding standards and the issue's acceptance criteria. Fix or explicitly defer each finding, rerun the build, then commit.
- **Working through issues:** once an issue is reviewed, built and checked on the tablet over adb, commit straight to `main` and push without asking. End the message with `Closes #N`, so the push closes the issue. Then go straight on to the next issue. Stop only for something that needs the user: a design change, a spec gap, or a decision the docs don't settle.
- **Manual checks:** for each finished issue, add the things only a person can judge (S Pen feel, palm rejection, visual quality) to [`docs/manual-checks.md`](docs/manual-checks.md). The user works through that list when they check in.
- **Device check early:** when an issue's acceptance needs the tablet, run `adb devices` at the start and ask the user to plug it in while the code is being written.
- **Update `PROGRESS.md` after every session.**
- **At cleanup** (the user asks before ending a session), update [`docs/workflow-ideas.md`](docs/workflow-ideas.md): add what slowed the session or caused mistakes, each with a concrete fix, and mark earlier ideas **done** or **dropped**.

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

- **Read build output** with `--console=plain 2>&1 | grep -vE "^> Task|^\[=|^$"`. It drops the task lines but keeps every warning and error. Don't pipe Gradle into `head`, which cuts off the error.
- **adb from Git Bash:** prefix commands with `MSYS_NO_PATHCONV=1`, or Git Bash rewrites device paths (`/sdcard/...` becomes `C:/Program Files/Git/sdcard/...`). Pass local files as Windows paths (`C:/...`).
- **Device checks:** `source scripts/device.sh` gives `install_app`, `launch`, `tap_text <label regex>`, `has_text`, `db <sql>` and `crashes`. Snackbar text doesn't show in uiautomator dumps, so check messages with a screenshot. Gestures `adb shell input` can't do (pinch, multi-finger) go in a UiAutomator test under `androidApp/src/androidTest`, run with `device_test <Class>`: it keeps the app's data, unlike `connectedAndroidTest`, which uninstalls the app. `sendevent` is blocked by SELinux. The raw commands (debug build):
  - Launch: `adb shell am start -n com.violinsheet/com.violinsheet.android.MainActivity`
  - Screenshot: `adb exec-out screencap -p > shot.png`
  - Crashes: `adb logcat -d -b crash`
  - Database: copy `violinsheet.db`, `-wal` and `-shm` with `adb exec-out run-as com.violinsheet cat databases/<file>` and open them with Python's `sqlite3`.
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
feature/settings/                     The Settings page, generated from each feature's declared Settings
feature/metronome/                    Stub (Post-V1.0)
scripts/                              Dev helper scripts, created when the first one is needed
graphify-out/                         Generated knowledge graph, git-ignored
```

- Inside a module: `src/{commonMain,androidMain,iosMain,commonTest}/kotlin/com/violinsheet/<module>/`, where the package matches the Gradle path (`:core:data` → `com.violinsheet.core.data`).
- One main class per file, named after it. A platform file that is the counterpart of a common file of the same name takes a `.android.kt`/`.ios.kt` suffix (`AppDatabase.android.kt` builds the `AppDatabase` from `AppDatabase.kt`).
- Keep packages flat until a module has around ten files, then split by feature area (`viewer/home`, `viewer/page`), not by layer.
- A new shared module needs an entry in `settings.gradle.kts`, a row in this tree and the `violinsheet.kmp-library` plugin.

## UI/UX

- **Toolbar:** a slim palette that stays at the bottom edge, within thumb and pen reach. No modal dialogs for switching tools or picking colours.
- **Input split:** the pen draws and edits; fingers only navigate (turn pages, zoom, open Links). Rely on the platform's built-in palm rejection, and verify that finger touches during pen input do not create strokes.
- **Symbol placement:** select Symbol → tap to place → ghost preview → confirm, so placing a printed-look Symbol feels deliberate.
- **Page transitions:** pre-render at least the next Page so page turns are instant and silent. Automatic page flipping (Feature 3) needs zero blank-flash latency.
- **Surround:** neutral dark grey (not pure black) around the Sheet to reduce eye strain. Never invert the Sheet itself.

## Progress Tracking

`PROGRESS.md` is a one-page status overview. Detailed work items live in GitHub Issues; link to them rather than repeating them.

| Feature | Sub-feature | Status | Next Step |
|---|---|---|---|

Status is one of: Not Started / In Progress / Done / Post-V1.0 / Nice-to-have.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `/graphify query "<question>"` before your first grep, search or source-file read for a codebase question. Read raw files only for lines the graph or an earlier read already located. Use `/graphify path "<A>" "<B>"` for relationships and `/graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Search order: graphify always comes first. After it, use Grep for exact matches (symbol tracing, refactoring, regex) and `mgrep "<natural-language query>"` for intent search (code exploration, feature discovery, onboarding). This overrides the mgrep plugin's "replaces all built-in search tools" rule.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `/graphify update` to keep the graph current (AST-only, no API cost). If the skill is unavailable, run `graphify update .` in a terminal outside Claude Code.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
