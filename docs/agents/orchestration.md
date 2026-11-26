# Orchestration

How issue work runs: an Opus **orchestrator** (the main session) plans, delegates, reviews and commits; **implementer** subagents write the code. It applies to all issue work, whether or not the user is watching. Agreed with the user on 2026-11-26.

## Models

| Role | Model |
|---|---|
| Orchestrator (main session) | Opus |
| Plan agent | Opus |
| Review subagents (standards and spec), on every issue | Opus |
| Implementer for a `model:opus` issue | Opus |
| Implementer for a `model:sonnet` issue | Sonnet |
| Explore agent (finding files and symbols) | Haiku. Never coding |

- Each issue carries one `model:` label, set at triage; what goes where is in [triage-labels.md](triage-labels.md). An issue without one: the orchestrator labels it from that table before planning, choosing `model:opus` when unsure.
- Explore results are facts (where things are). Design decisions stay with Opus.
- Pass the model explicitly with the Agent tool's `model` parameter. Implementers and reviewers are `general-purpose` agents (they need every tool, including Bash for builds, adb and `git diff`), without worktree isolation: the tablet and Gradle work on the one tree.
- **At session start**, before any issue work, the orchestrator checks it is running on Opus. If not, it asks the user to run `/model opus` and waits.
- Unattended runs rely on **auto mode**, which the user turns on; there is no permission allowlist.
- Any agent may query the knowledge graph (`graphify-out/`) to find code. Only the orchestrator updates it, after each commit that changes code (issue or small fix), with `/graphify update` (or `graphify update .` in a terminal if the skill is unavailable).

## The loop, per issue

Issues run one at a time: there is one tablet, and the issues block each other.

0. **Clean tree.** `git status` must show no changes, so the commit holds only this issue. If it doesn't, the orchestrator stops and asks the user.
1. **Plan.** The orchestrator reads the issue, the spec sections and the code it touches, using the Plan agent for design-heavy issues. It grills the user first if the issue forces a big design decision (`CLAUDE.md` → Working Style). If the issue needs the tablet, it runs `adb devices` and asks the user to plug it in if needed. It writes `handoffs/<N>-plan.md` from the template below. Design questions the docs don't settle stop the run here: ask the user.
   - **Splitting:** an issue too big for one implementer's context (for example lasso, or staff-line detection) may be split into ordered parts: plans `<N>-plan-1.md`, `<N>-plan-2.md` …, reports `<N>-report-1.md`, `<N>-report-2.md` …, one shared evidence folder. Each part gets its own implementer, which reads the earlier parts' reports first. There is still one review and one commit for the whole issue; the last part's implementer fixes the review findings.
2. **Implement.** It spawns one implementer with the issue's label model, pointing it at the plan file. The implementer:
   - writes the code and tests,
   - runs the builds and the tablet checks (`CLAUDE.md` → Build & run),
   - saves its key tablet evidence (screenshots, `db` output) in `handoffs/<N>-evidence/`,
   - runs `./gradlew build`, writes its report from the template (listing the evidence), and stops.

   What it must not do is in the plan template's Rules. While an implementer runs, the orchestrator only reads: changes go through the implementer, so the two never edit the same files.
3. **Review.** The orchestrator first looks at the evidence itself: a report saying "checked on the tablet" is a claim, and on 2026-11-26 screenshots caught misplaced Ink and a broken layout that the other checks had passed. Then it runs the two reviews in parallel, both on Opus. They follow the `mattpocock-skills:code-review` process (standards with the Fowler smell baseline, spec against the issue), spawned directly so their model can be set. It sends the findings to the **same** implementer (after a split, the last part's) with SendMessage, so it keeps its context. The implementer fixes them, reruns `./gradlew build` and the affected tablet checks, and updates its report.
   - A finding the implementer disagrees with gets its reason in the report. The orchestrator then decides: fix it, defer it with a note in the commit, or ask the user if it is a design question.
   - The report's **open questions**: the orchestrator answers them from the docs (and sends the answer back), or stops the run and asks the user. A spec gap the user settles goes into the docs, followed by the whole-spec review (`CLAUDE.md` → Working Style), before the implementer resumes.
4. **Commit.** The orchestrator:
   - reruns `./gradlew build`,
   - checks the report (all parts' reports, after a split) against the plan,
   - updates `PROGRESS.md`, and copies the reports' manual checks and friction into `docs/manual-checks.md` and `docs/workflow-ideas.md`,
   - commits to `main` with `Closes #N` and pushes,
   - updates the knowledge graph (see Models),
   - deletes the issue's files in `handoffs/` (plans, reports, evidence).
5. **Next issue**, unless something needs the user.

**When an implementer is stuck** (the build won't go green, a device check keeps failing, a review round comes back still broken): the orchestrator sends it back with specific guidance at most twice. After that it stops the run and tells the user what is blocked. It doesn't take the issue over itself.

**When an implementer dies midway** (a usage limit, a crash): the orchestrator compares `git status` (new files) and `git diff` with the plan, then resumes the same implementer with SendMessage, or spawns a new one told to continue from the current tree with the plan and any report. This is not one of the two retries.

**Small fixes outside an issue** (a docs edit, a one-file bug fix, fixes from the user's manual check): if under about 30 lines in one or two files, the orchestrator does them itself, still with both Opus reviews before committing, and updates the knowledge graph after a code change. Anything bigger becomes a plan and an implementer.

## Handoff files

They live in `handoffs/` (git-ignored), so a cold subagent gets the whole context, and they survive the orchestrator's context being summarised. After a split, plans and reports are numbered by part (see step 1).

### `handoffs/<N>-plan.md` (orchestrator → implementer)

```markdown
# #<N> <title>: plan

## Goal
<What the user gets, in a sentence or two.>

## Acceptance criteria
<Copied from the issue.>

## Design decisions
<What is settled, with the reason for each. Anything the implementer must not change.>

## Files
<Files to create or change, and existing code to reuse (with paths).>

## Rules
- Read CLAUDE.md first and follow these sections: Architecture, Directory layout, UI/UX, and Build & run (its commands, the Git Bash and adb notes, and the tablet-data rule). Its Working Style section is for the orchestrator, not you.
- Build: the quick check from Build & run after each edit; `./gradlew build` before each report.
- Tablet: `source scripts/device.sh`, and use `device_test`. Never `pm clear`, never uninstall, and never run `connectedAndroidTest` (it uninstalls the app and wipes the user's data).
- Save key screenshots and db output in handoffs/<N>-evidence/ and list them in the report.
- Don't commit, don't edit PROGRESS.md, docs/manual-checks.md or docs/workflow-ideas.md, don't update the knowledge graph (querying it is fine), don't ask the user: put questions in the report.

## Verify
<Unit tests to add, and the tablet checks to run.>

## Report
Write handoffs/<N>-report.md (after a split: <N>-report-<part>.md) from the template in docs/agents/orchestration.md, then stop.
```

### `handoffs/<N>-report.md` (implementer → orchestrator)

```markdown
# #<N> <title>: report

## Changed
<Files, and why each changed.>

## Deviations from the plan
<And why. "None" if none.>

## Verification
<Build result, unit tests (counts), tablet checks with what was seen. Evidence files in handoffs/<N>-evidence/, one line each on what it shows.>

## Review findings
<Per finding: fixed, or disputed with the reason. Empty before the first review.>

## Open questions
<Design questions or spec gaps for the orchestrator. "None" if none.>

## Manual checks for the user
<What only a person can judge, as checklist items.>

## What slowed me down or went wrong
<Tool, doc or rule friction, each with a concrete fix.>
```
