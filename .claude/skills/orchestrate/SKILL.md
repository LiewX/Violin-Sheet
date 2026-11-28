---
name: orchestrate
description: The main session's rules for this repo. How issue work runs (plan, implementer, review, commit), working style, progress tracking and session cleanup with the token review. Load at session start in the main session, before any issue work, grilling or cleanup. Not for subagents.
---

# Orchestrate

The main session is the **orchestrator**: it plans, delegates, reviews and commits. **Implementer** and **reviewer** subagents (`.claude/agents/`) do the code and the reviews. This applies to all issue work, whether or not the user is watching. Agreed with the user on 2026-11-26; token rules added after the grilling of 2026-11-26 (workflow idea 27).

## Working style

- **Ask before assuming** when a requirement is unclear: present the tradeoff, don't pick silently.
- **Justify architectural decisions** in one or two sentences when you introduce them. Record hard-to-reverse ones as an ADR.
- **Grill before big changes.** When a request changes scope, adds or removes a feature, or forces a significant design decision, run a grilling session (`/grill-with-docs` or `mattpocock-skills:grilling`) before implementing. Update `docs/SPECS.md`, `GLOSSARY.md` and `docs/adr/` from its outcome.
- **Workflow rules are loops.** A new or changed workflow rule (in `CLAUDE.md`, this skill, `.claude/agents/` or `docs/agents/`) is grilled before it is written, and written as a loop: who does what, in which order, and what happens when a step fails.
- **Review the whole spec after every grilling.** Once its outcome is written into the docs, read `docs/SPECS.md` end to end (with `GLOSSARY.md`, `CLAUDE.md` and the ADRs) and check for contradictions, stale wording and gaps the new decisions created. Fix wording-only issues directly; bring anything that needs a decision back to the user.
- **Don't implement Post-V1.0 or Nice-to-have features**, even if asked in passing. Note them in `PROGRESS.md` instead.
- **Working through issues:** once an issue is reviewed, built and checked on the tablet over adb, commit straight to `main` and push without asking. End the message with `Closes #N`. Then go straight on to the next issue. Stop only for something that needs the user: a design change, a spec gap, or a decision the docs don't settle.
- **Mockups:** only after the user says yes to an Artifact (never unasked). The orchestrator writes the agreed decisions into `handoffs/<N>-mockup-brief.md` and spawns a `general-purpose` Opus subagent, which loads `artifact-design`, builds the board and replies with only the link. The user's feedback rounds go to that same agent through SendMessage. The orchestrator doesn't open the board; the user reviews it. Once the user picks, the same agent writes `handoffs/<N>-ui-decisions.md`: what each pick draws (positions, order, dp), and what the picks leave open. The orchestrator asks the user the open items, writes the answers into the spec, and runs the whole-spec review. A board of the final design is made only if the user asks for one (workflow idea 85).
- **Manual checks:** for each finished issue, add the things only a person can judge (S Pen feel, palm rejection, visual quality), from the implementer's report and the review, to `docs/manual-checks.md`. The user works through that list when they check in.
- **Device check early:** when an issue's acceptance needs the tablet, run `adb devices` while planning, before spawning the implementer, and ask the user to plug it in if needed.
- **Update `PROGRESS.md` after every session.** It is a one-page status table (`| Feature | Sub-feature | Status | Next Step |`, Status one of Not Started / In Progress / Done / Post-V1.0 / Nice-to-have). Detailed work items live in GitHub Issues; link to them rather than repeating them.

## Models

| Role | How it runs |
|---|---|
| Orchestrator (main session) | Opus. At session start, check it is on Opus; if not, ask the user to run `/model opus` and wait |
| Plan agent | `general-purpose` on Opus (the built-in Plan agent can't write files), told to design only: no code, and to grep the code before stating how something works today (Loop step 1). Writes `handoffs/<N>-design.md` and replies with at most ~150 words plus the path |
| Implementer | `subagent_type: implementer`, `model` from the issue's `model:` label (`model:opus` → opus, `model:sonnet` → sonnet), fix rounds included (never downgraded to save tokens). One exception: a refactor-only part (see Splitting) runs on sonnet |
| Reviewer | `subagent_type: reviewer`, `model: opus`, except the sonnet tier for small low-risk fixes outside issues (see Small fixes) |
| Explore agent (finding files and symbols) | Haiku. Never coding; its results are facts (where things are), design decisions stay with Opus. The prompt asks for a reply of at most ~200 words of paths and line numbers |
| Mockup agent | `general-purpose`, Opus (see Working style → Mockups) |

- Each issue carries one `model:` label, set at triage ([`docs/agents/triage-labels.md`](../../../docs/agents/triage-labels.md)). An issue without one: label it from that table before planning, choosing `model:opus` when unsure.
- Always pass `model` explicitly. No worktree isolation: the tablet and Gradle work on the one tree.
- **The orchestrator's context is the scarce one:** read with offset/limit or grep, not whole files; filter Gradle output; open a screenshot only for a visual check, and then the `-small.png` copy. Every subagent reply is capped (implementers, reviewers, Plan and mockup agents ~150 words, Explore ~200); the detail stays in files the orchestrator reads by section.
- **Session length:** at each commit step, `python -I scripts/agent-tokens.py` prints the main session's context. At ≥ ~400k, write `handoffs/<N>-resume.md` for the next issue and stop after that commit, rather than at the usage limit. If the script fails or prints no main-session figure, say so to the user and go on.
- **Usage limit** (workflow idea 86): the orchestrator can't see the user's usage.
  - When the user reports usage at 80% or more: write `handoffs/<N>-resume.md` at once. Finish only an issue already at its commit step, and start no new issue or part.
  - Whatever the usage, write the resume file before spawning the implementer of an issue's last part, so a sudden limit never loses the commit steps.
- Unattended runs rely on **auto mode**, which the user turns on; there is no permission allowlist.
- Any agent may query the knowledge graph (`graphify-out/`). Only the orchestrator updates it, after each commit that changes code, with `/graphify update` (or `graphify update .` in a terminal if the skill is unavailable).
- Issues live in GitHub Issues via `gh` ([`docs/agents/issue-tracker.md`](../../../docs/agents/issue-tracker.md)); triage labels in [`docs/agents/triage-labels.md`](../../../docs/agents/triage-labels.md); domain docs in [`docs/agents/domain.md`](../../../docs/agents/domain.md).

## The loop, per issue

Issues run one at a time: there is one tablet, and the issues block each other.

0. **Clean tree, in step with GitHub.** `git status` must show no changes, so the commit holds only this issue. If it doesn't, stop and ask the user. Then `git fetch` and `git status -sb`: if `main` is behind origin, `git pull --ff-only` before planning; if that fails, stop and ask the user (workflow idea 83).
1. **Plan.** Read the issue, the spec sections and the code it touches, using the Plan agent for design-heavy issues. Grill the user first if the issue forces a big design decision. If the issue needs the tablet, run `adb devices`. Write `handoffs/<N>-plan.md` from the template below. Design questions the docs don't settle stop the run here: ask the user.
   - **Current behaviour:** before a plan says how something works today ("as today"), grep the code for it; if it isn't there, the plan lists it as new work (workflow idea 60).
   - **File size:** if the issue would push a source file past ~300 lines (test files exempt), the issue's first part splits it by feature area (e.g. `InkLayer.android.kt` → drawing, selection gestures, erase). No refactor-only issues for this.
   - **Tablet steps:** each one is written as a scripted check (`device-check` → One script per tablet step) with its expected result, so the implementer can rerun it after each fix.
   - **Skills:** name in the plan the skills relevant to the issue (`device-check` when it has tablet steps; e.g. `mattpocock-skills:tdd` for pure logic).
   - **Splitting:** an issue that spans two subsystems (for example storage plus pen input), or is otherwise too big for one implementer's context (lasso, staff-line detection), is split into ordered parts: plans `<N>-plan-1.md`, `<N>-plan-2.md` …, reports `<N>-report-1.md` …, one shared evidence folder. Each part gets its own implementer, which reads the earlier parts' reports first. There is still one review and one commit for the whole issue; the last part's implementer fixes the review findings.
   - **Split by size too:** each part aims at under ~150k of implementer context. As a rule of thumb, a part touches at most ~8 source files and at most two of {storage, new UI, new gestures, refactor}. A part with new UI or new gestures adds 25% to its estimate, and is split when that passes 150k; the cleanup retunes the 25% (workflow idea 81). The plan's `## Size` line records the estimate, and the token review compares it with the actual final context to tune the rule. A part that runs over isn't stopped; its fix round gets a fresh implementer (step 3).
   - **Refactor-only part:** a file split for the ~300-line cap is its own part 1 on sonnet, whatever the issue's label. Its acceptance: a pure move, build and tests green, no behaviour change. It joins the issue's one review and commit, and the standards reviewer checks it was a pure move.
2. **Implement.** Spawn one `implementer` with the issue's label model (sonnet for a refactor-only part), pointing it at the plan file. Its rules (including what it must not do) are in `.claude/agents/implementer.md`. It writes code and tests, runs the builds and tablet checks, saves evidence in `handoffs/<N>-evidence/`, runs `./gradlew build`, writes its report and stops. While it runs, the orchestrator only reads.
3. **Review.** First look at the evidence yourself: a report saying "checked on the tablet" is a claim (on 2026-11-26 screenshots caught misplaced Ink and a broken layout that the other checks had passed). Read the db and dump files; open a `-small.png` only when the check is visual. Then spawn **one** `reviewer` (Opus) with the issue number and the plan path; it covers standards and spec in two sections and writes them to `handoffs/<N>-review.md`. Exception: a `model:opus` issue touching storage or pen input gets two reviewers in parallel, one per axis (say which axis in the prompt), writing `<N>-review-standards.md` and `<N>-review-spec.md`. Read the blockers in full and the should-fix titles (and later any finding the implementer disputes). The fix-round message is the review file path(s), plus a line per finding you defer or overrule. By default the implementer fixes every blocker and should-fix, handles nits at its discretion (with a reason for each skipped), reruns `./gradlew build` and the affected tablet check scripts, and updates its report.
   - **Which implementer:** read its final context from `python -I scripts/agent-tokens.py` (its "final context" column). Under ~150k: the **same** one, with SendMessage. Over: a **fresh** one with the same model, told to read the plan, the report, the findings and `git diff --stat`, then only the files the findings touch.
   - **No new scope in a fix round:** the message holds only findings and answered open questions. A change the user asks for mid-issue becomes a new part (`<N>-plan-K.md`, fresh implementer) that goes into the same review and commit; the review then covers it too. Only a change outside the issue's scope becomes a new issue.
   - A finding the implementer disagrees with gets its reason in the report. Decide: fix it, defer it with a note in the commit, or ask the user if it is a design question.
   - The report's **open questions**: answer them from the docs (and send the answer back), or stop the run and ask the user. A spec gap the user settles goes into the docs, followed by the whole-spec review, before the implementer resumes.
4. **Commit.**
   - rerun `./gradlew build`,
   - check the report(s) against the plan,
   - update `PROGRESS.md`, and append the reports' manual checks and friction (already in the target format) to `docs/manual-checks.md` and `docs/workflow-ideas.md` with a shell extract (e.g. `sed -n '/^## Manual checks/,/^## /{/^## /!p}'`, which leaves out the headings), numbering the friction rows; check only the resulting `git diff` of those two files,
   - commit to `main` with `Closes #N`, then `git fetch` and, if origin moved, `git rebase origin/main` before the push. The orchestrator resolves a rebase conflict only in `docs/workflow-ideas.md`, `PROGRESS.md` or `docs/manual-checks.md` (appended rows, renumbering); a conflict in any other file: `git rebase --abort`, then stop and ask the user. A rebase that brought in code reruns the quick check (`CLAUDE.md` → Build & run) before the push. Then push,
   - update the knowledge graph,
   - delete the issue's files in `handoffs/`,
   - check the session length (Models → Session length).
5. **Next issue**, unless something needs the user.

**When an implementer is stuck** (the build won't go green, a device check keeps failing, a review round comes back still broken): send it back with specific guidance at most twice. After that, stop the run and tell the user what is blocked. Don't take the issue over.

**After adding or changing an agent type mid-session:** the new definition only loads in the next session (spawning it fails with "Agent type '…' not found", or runs the old one). For the rest of the session, spawn `general-purpose` with the model from the table above (the issue's `model:` label for an implementer, the reviewer's tier for a reviewer: opus, or sonnet for a small low-risk fix), told to read and follow that `.claude/agents/<type>.md`.

**When an implementer dies midway** (a usage limit, a crash): compare `git status` and `git diff` with the plan, then resume the same implementer with SendMessage, or spawn a new one told to continue from the current tree with the plan and any report. This is not one of the two retries. If the session itself must stop mid-issue, write `handoffs/<N>-resume.md` (what's done, what's left, the commit steps) first.

**Small fixes outside an issue** (a docs edit, a one-file bug fix, fixes from the user's manual check): if under about 30 lines in one or two files, do them yourself; update the knowledge graph after a code change. Anything bigger becomes a plan and an implementer. Issues keep the reviews in step 3. Every change outside an issue (small or planned) is reviewed by risk, not size (workflow idea 64):
   - **Wording only:** docs, spec phrasing, comments, typos, including in workflow files, as long as no rule or behaviour changes. No reviewer; the orchestrator reads the diff.
   - **Small and low-risk:** UI text, a test, a pure refactor. One `reviewer` on sonnet, both axes.
   - **The user's data, input, or a rule:** storage, migrations, scripts that write to the tablet, pen or finger input, or a changed workflow rule (`.claude/`, `docs/agents/`, `CLAUDE.md`). One `reviewer` on Opus, however small.

   The loop:
   1. **Pick the tier** before starting. A planned fix writes it in its plan as a `## Review` line (tier and a one-line reason); a fix the orchestrator makes itself names it in the reviewer's prompt, or in the commit message when there is no reviewer.
   2. **Check the diff:** before reviewing, read `git diff --stat`. If it changes behaviour or a rule in a higher-risk area than planned (`core/data`, migrations, `scripts/device.sh` or another tablet-writing script, input handling, a workflow rule), use that higher tier.
   3. **Review.** When the orchestrator made the fix itself, it answers the implementer's four self-check questions (`.claude/agents/implementer.md` → Self-check) in the reviewer's prompt.
   4. **If the reviewer reports that the diff belongs to a higher tier,** rerun the review at that tier before fixing anything.
   5. **Findings** are fixed by whoever made the change: the orchestrator for its own fixes, otherwise the implementer, sent the review file as in the issue loop's step 3. Blockers and should-fix are fixed; a fix round that changes more than wording gets one more review at the same tier. After two rounds still broken, stop and ask the user.

## Cleanup

When the user asks before ending a session:

1. **Token review.** Run `python -I scripts/agent-tokens.py` (no arguments: it reads this project's most recent session). It prints the main session's context and, per subagent, the starting context, tool output by tool, screenshots read and final context, without any content. Compare each part's final context with its plan's `## Size` estimate. Measure any workflow idea whose status says "measure at next cleanup" against its baseline (ideas 50/51: parts ≤150k, hand-backs under ~20k vs ~60k), then mark it done or reopen it. List each review's tier and its findings by severity. If a change reviewed at a lower tier (or not at all) later needed a fix that an Opus review would likely have caught, reopen idea 64. Then check each `CLAUDE.md` section and each doc agents load against which roles use it. Write the findings into `docs/workflow-ideas.md`, each with a concrete fix. A move into a skill, or any other rule change, is proposed to the user and grilled before it is written. If the script fails or finds no transcripts, say so in the cleanup message and skip this step rather than guess.
2. **Workflow ideas.** Add what slowed the orchestration or caused mistakes, each with a concrete fix, and mark earlier ideas **done** or **dropped**. (Implementers' friction is copied in at each issue's commit.)
3. Update `PROGRESS.md` and the memory.

## Handoff files

They live in `handoffs/` (git-ignored), so a cold subagent gets the whole context, and they survive the orchestrator's context being summarised. The report template is in `.claude/agents/implementer.md`.

### `handoffs/<N>-plan.md`

```markdown
# #<N> <title>: plan

## Goal
<What the user gets, in a sentence or two.>

## Acceptance criteria
<Copied from the issue.>

## Design decisions
<What is settled, with the reason for each. Anything the implementer must not change.>

## Size
<~N source files; areas (storage / new UI / new gestures / refactor); expected under ~150k.>

## Files
<Files to create or change, and existing code to reuse (with paths).>

## Skills
<Skills to load for this issue, e.g. device-check. "None" if none.>

## Verify
<Unit tests to add. Each tablet step: what the `check-<step>.sh` script does (starting from the Settings and screen state it sets up itself) and its expected `check_db`/`ui`/`pixels` result. The user's stroke counts are recorded at the start, never given as numbers.>

## Cases adb can't simulate
<Input issues only: the multi-pointer cases (palm then pen, pen then finger, two hands) and how the design handles each. "None" if not an input issue.>
```
