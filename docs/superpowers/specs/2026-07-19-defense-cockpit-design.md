# CodeDefense Defense Cockpit Design

**Status:** approved in conversation on 2026-07-19
**Scope:** Iterations 8.13–8.16
**Product surface:** Java 21 core CLI and IntelliJ IDEA plugin
**Dependency:** Iterations 8.11 and the existing source-free Passport contracts

## Product thesis

CodeDefense should not behave like a quiz window that the developer remembers to open. It should become a passive, local proof-of-understanding layer around the exact staged Git change. The IDE continuously shows whether the current staged change has a matching Passport, provides one-click access to an evidence-grounded defense, and warns immediately before commit when the proof is absent or stale.

The Defense Cockpit adds five connected product moments:

1. live staged Passport status;
2. an advisory pre-commit gate;
3. evidence navigation from each question into the editor;
4. a repository-local learning radar;
5. an optional source-free Passport fingerprint commit trailer.

The feature remains educational. It does not certify code, prove authorship, rank developers, approve a merge, or prevent an explicit user override.

## Approved product decisions

- The gate covers the exact staged Git index only.
- Status refresh is event-driven with debounce, plus a mandatory fresh check immediately before commit.
- The gate is advisory: `Commit anyway` is always available for the current commit attempt.
- Evidence navigation opens a validated relative path at a line range in the editor. It does not copy source into the plugin protocol.
- Learning history is limited to the current repository and remains local.
- Codex is never invoked automatically by status, insights, navigation, or commit checks.
- Iteration 9 remains the final reliability, release, and hackathon-submission iteration.

## Architecture boundary

The core CLI remains the only component that captures Git identity, reads Passport receipts, and calculates learning insights. The IntelliJ plugin launches bounded CLI commands and renders strict source-free JSON. It must not independently run Git commands, parse Passport Markdown, inspect source content, or duplicate Passport identity rules.

```text
IntelliJ Git/VFS event
        -> debounced plugin coordinator
        -> bundled CLI source-free command
        -> strict bounded JSON
        -> status badge / radar

IntelliJ pre-commit callback
        -> fresh bundled CLI gate command
        -> CURRENT: continue
        -> otherwise: Defend / Commit anyway / Cancel
```

The core commands introduced by this design do not initialize `AiProvider`, JLine, Codex environment checks, or report generation.

## Iteration 8.13 — Live Staged Passport Gate

### Core gate contract

Add a local-only command equivalent to:

```text
codedefense passport gate --staged [PATH] --format json
```

It captures the current staged identity using the existing bounded Git-change adapter and compares it with bounded, validated local Passport receipts for the same repository root.

The result has one of five states:

- `NO_STAGED_CHANGE`: the Git index contains no staged change;
- `UNDEFENDED`: a staged change exists but no staged Passport history exists for this repository;
- `CURRENT`: a complete staged Passport matches the repository identity, base commit, index identity, and diff fingerprint exactly;
- `EXPIRED`: staged Passport history exists, but none matches the current exact staged identity;
- `UNAVAILABLE`: Git or local Passport state cannot be checked safely.

A commit/range Passport does not satisfy the first staged-only gate version. The state must never be inferred from a short fingerprint alone.

The strict JSON response is versioned, deterministic, bounded to 256 KiB, and contains only:

- protocol version;
- gate state and safe reason code;
- current full diff fingerprint when available; the UI derives the 12-character display form locally;
- matching attempt number when current;
- staged file/addition/deletion counts;
- at most 30 normalized relative staged paths only for `EXPIRED`, where they are needed to explain the stale proof; other states return no paths.

It contains no source, hunks, answers, questions, feedback, model output, absolute paths, environment values, or raw errors.

### Event-driven plugin status

Add a project-scoped staged-status coordinator. It refreshes when:

- the project opens;
- the CodeDefense Tool Window becomes visible;
- IntelliJ reports a Git repository/index change;
- a relevant VFS batch completes;
- a Passport is saved;
- the user presses Refresh.

Events are coalesced with a 750 ms debounce. Only one status process may be active. Every request receives a monotonically increasing generation; a late result from an older generation is discarded. Window activation performs an additional refresh so external `git add` operations are observed without polling.

The badge renders both color and text:

- gray `NO STAGED CHANGE`;
- orange `UNDEFENDED`;
- green `CURRENT`;
- red `EXPIRED`;
- yellow `UNAVAILABLE`.

Color is never the only signal. Background failures update the badge and a concise diagnostic area but do not open modal dialogs.

### Advisory commit check

Immediately before an IntelliJ Git commit, run a fresh gate check under a bounded progress task. Cached badge state is never sufficient for the commit decision.

For `CURRENT`, continue without interruption. For every other state, show:

```text
This staged change does not have a current CodeDefense Passport.

[Defend change] [Commit anyway] [Cancel]
```

- `Defend change` cancels the commit, opens CodeDefense, selects `STAGED`, and focuses `Preview defense`.
- `Commit anyway` applies only to that one callback and is not persisted or logged.
- `Cancel` cancels the commit.

If IntelliJ is committing an unstaged changelist rather than the staged index, show `UNSUPPORTED COMMIT MODE`. The user may commit anyway or cancel; CodeDefense must not claim that the selected changelist was checked.

The gate does not install Git hooks and cannot block commits made outside IntelliJ.

## Iteration 8.14 — Evidence Navigator

### Bridge protocol

Introduce bridge protocol version 2 while keeping protocol 1 behavior unchanged. Protocol 2 question events add a bounded list of typed evidence locations:

```json
{
  "relativePath": "src/main/java/dev/codedefense/Example.java",
  "startLine": 27,
  "endLine": 40
}
```

The core emits locations only after existing analysis validation has established that the path belongs to the captured change and the line range is valid. Each question contains 1–10 evidence locations. Paths and ranges are size-bounded, unique, and deterministically ordered. Evidence reasons and source snippets are not sent to the plugin.

The bundled plugin requests protocol 2. Protocol 1 remains available for older adapters and emits questions without evidence navigation.

### Safe editor navigation

The plugin renders each location as a clickable evidence item beneath the current question. Before opening it, the plugin:

1. normalizes the project root and relative path;
2. rejects absolute paths and parent traversal;
3. requires the resolved path to remain beneath the real project root;
4. rejects intermediate and final symbolic links;
5. requires a regular readable file;
6. converts the validated one-based line to the IntelliJ editor location.

Missing, renamed, deleted, unreadable, or unsafe paths produce a concise message and are never opened. Deleted-file evidence remains visible as metadata, but editor navigation is disabled in this iteration. Git diff navigation may be added later as a separate design.

The plugin does not read file bytes to implement navigation and never puts source in logs, settings, notifications, or bridge messages.

## Iteration 8.15 — Repository Learning Radar

### Core insights contract

Add a local-only command equivalent to:

```text
codedefense passport insights [PATH] --format json --limit 20
```

It reads at most the most recent 20 complete attempts whose validated repository-root identity matches the current repository. Repository filtering occurs before the limit, so newer receipts from unrelated repositories cannot hide local history. It includes staged, commit, and range defenses because the radar describes repository learning rather than gate eligibility.

The core calculates:

- complete attempt count;
- distinct defended change count;
- integer average for `decision`;
- integer average for `counterfactual`;
- integer average for `test-prediction`;
- strongest category, using stable category order for ties;
- practice category, using stable category order for ties;
- ordered overall scores for at most the most recent 10 attempts.

Skipped scores remain authoritative zeros and are included. No score is recalculated from model text.

The strict source-free JSON contains no project name, absolute root, user identity, answers, questions, feedback, concepts, evidence, source, timestamps, or model data. An empty repository history returns a valid empty insight result.

### Radar presentation

The Tool Window renders three labeled horizontal bars and a compact recent-score trend:

```text
Decision          92
Counterfactual    54
Test prediction   31

Recent overall    33 -> 61 -> 84
Practice next     Test prediction
```

The radar refreshes after Passport creation, on manual Refresh, and when the Tool Window opens. It does not refresh for every editor keystroke. It is explicitly labeled `Repository-local defense history`, not developer performance.

## Iteration 8.16 — Passport-aware Commit

When and only when the fresh gate state is `CURRENT`, expose an unchecked per-commit option:

```text
Attach CodeDefense Passport fingerprint
```

The commit UI performs an early bounded gate refresh to enable the option. The final pre-commit callback remains authoritative. With explicit selection, append one Git trailer containing the full lowercase SHA-256 diff fingerprint:

```text
CodeDefense-Passport: sha256:<64 lowercase hex characters>
```

The trailer contains no score, readiness, path, repository name, user identity, thread identity, or URL. It is a reference to a local educational artifact, not a signature or approval.

Trailer rules:

- preserve the user's commit message;
- do not add a duplicate when the same trailer already exists;
- never silently replace a different existing CodeDefense trailer;
- if an existing trailer differs from the fresh current fingerprint, warn and require the user to remove or explicitly replace it;
- never add a trailer for `UNDEFENDED`, `EXPIRED`, `UNAVAILABLE`, `NO_STAGED_CHANGE`, or unsupported commit mode;
- never persist the checkbox selection as a default.

When trailer attachment is selected, perform a second immediate staged gate check before mutating the commit message. Both fresh checks must report the same complete current identity. A mismatch cancels the commit and appends nothing. External mutation after the final callback remains an unavoidable Git/IDE handoff race and CodeDefense does not claim atomic signing.

The final iteration also polishes the Cockpit layout: live badge at the top, staged summary and actions below it, question/evidence area in the center, and the learning radar below the session output. Narrow Tool Windows retain access to all actions.

## User-facing flow

```text
git add
  -> live badge becomes UNDEFENDED or EXPIRED
  -> user selects Defend staged change
  -> bounded preview and explicit disclosure confirmation
  -> three evidence-linked questions
  -> source-free Passport becomes CURRENT
  -> learning radar refreshes
  -> pre-commit gate performs a fresh check
  -> optional fingerprint trailer
  -> commit proceeds
```

## Error and cancellation behavior

- A background status failure produces `UNAVAILABLE`; it never reuses stale `CURRENT` as a fresh result.
- A pre-commit timeout or malformed response is treated as `UNAVAILABLE` and offers explicit override or cancellation.
- Starting a defense from the gate uses the existing one-session and one-shot-confirmation protections.
- Cancelled status processes are drained and terminated with the existing bounded process policy.
- Plugin disposal cancels pending debounce work and child processes.
- Evidence navigation failures never terminate an interview.
- Insight corruption or unknown schema yields an unavailable radar without affecting the gate or defense.
- Cancellation and interruption preserve existing exit semantics.

## Privacy and security invariants

- No background action invokes Codex, consumes credits, or sends source.
- No analyzed source code or tests are executed.
- Preview and confirmation remain mandatory before disclosure.
- The plugin does not run Git directly; all Git identity logic remains in the bundled CLI.
- Child commands are token lists, never shell strings.
- Background CLI children receive only an explicit minimal cross-platform environment allowlist required to resolve Git; arbitrary environment secrets are not inherited.
- Every child process has a positive timeout, bounded capture, concurrent draining, and process-tree cleanup.
- JSON readers use strict duplicate detection, fail on trailing tokens, validate exact fields, and enforce byte limits.
- Repository paths are normalized, contained, and symlink-safe at every filesystem boundary.
- Passport and insights remain source-free.
- Commit overrides, evidence clicks, and radar data are not logged as user analytics.
- Existing Iteration 8.12 provenance remains off by default and is not modified by this package.

## Compatibility

- Java 21 and the Maven core remain authoritative.
- No new core or plugin dependency is required unless a public IntelliJ Git integration API requires the bundled `Git4Idea` plugin dependency declaration.
- Only public IntelliJ APIs may be used.
- The plugin continues to target IntelliJ IDEA builds `261.*` and `262.*` on Windows.
- Plugin Verifier must report compatibility for one exact 261 build and the installed exact 262 build.
- Override CLI JARs using bridge protocol 1 remain usable without evidence navigation.

## Testing strategy

All automated tests are offline and must never invoke real Codex.

### Core tests

- fake Git repositories for all five gate states;
- exact base/index/fingerprint matching;
- commit/range receipt does not satisfy staged gate;
- staged index mutation between checks;
- strict bounded deterministic gate JSON;
- repository-scoped insight aggregation and 20-attempt bound;
- category averages, stable tie-breaking, skips, and empty history;
- no answer/source/model markers in gate or insight output;
- bridge v1 compatibility and bridge v2 typed evidence;
- malformed, duplicate, oversized, traversal, and unknown-field rejection.

### Plugin tests

- event coalescing and debounce generations;
- stale result suppression;
- one status process at a time;
- final check ignores cached `CURRENT`;
- every commit dialog action;
- unsupported unstaged commit mode;
- no automatic defense or Codex invocation;
- evidence containment, symlink, missing file, and line navigation;
- radar rendering and empty/unavailable states;
- trailer append, duplicate, mismatch, and explicit-consent behavior;
- narrow Tool Window layout;
- controller disposal and process cleanup.

### Acceptance

For every iteration:

- run focused Maven and Gradle tests;
- run `mvn clean verify` and `mvn package`;
- exercise new core commands against disposable Git fixtures;
- run CLI help and safe dry-run commands;
- run plugin tests and build the plugin ZIP;
- run Plugin Verifier for exact 261 and 262 IDE builds;
- inspect generated source-free JSON for forbidden markers;
- perform no real Codex call unless separately authorized.

Installed-plugin acceptance is manual in the user's real IDE. Iteration 8.13 acceptance demonstrates live status transitions and an advisory commit warning without a model call. Iteration 8.14 opens a safe evidence location from a fake/offline bridge fixture before any optional live run. Iterations 8.15 and 8.16 use local receipt and commit-message fixtures.

## Delivery and agent strategy

Development proceeds in four sequential iterations and four reviewable commits:

1. `feat: add live staged Passport gate`
2. `feat: navigate defense evidence in IntelliJ`
3. `feat: add repository learning radar`
4. `feat: attach Passport fingerprints to commits`

Within each iteration, independent core, protocol, and plugin tasks may run in parallel agents. The primary agent owns integration, cross-boundary review, verification, and commits. Agents share no live Codex authorization and must use hand-written fakes and disposable repositories.

## Explicit non-goals

- no hard commit blocking;
- no Git hooks;
- no GitHub, pull-request, CI, cloud, or dashboard integration;
- no unstaged IntelliJ changelist capture in the first gate;
- no automatic Codex invocation or model-based status check;
- no source snippets in plugin events;
- no source/test execution;
- no employee identity, ranking, leaderboards, or team analytics;
- no score/readiness commit trailers;
- no cryptographic signing or certification claims;
- no proof of unaided answers or authorship;
- no changes to Iteration 8.12 provenance behavior;
- no Iteration 9 release work.

## Design self-review

- The five features form one coherent local workflow rather than independent novelty features.
- The CLI remains the authority for Git, Passport, and score history.
- The plugin remains an adapter and never receives source content for gate, insights, or navigation.
- The staged-only limitation is explicit in both status and pre-commit behavior.
- `CURRENT` requires full identity equality and cannot be inferred from an abbreviated hash.
- A gate failure cannot silently become approval because `Commit anyway` remains explicit.
- Learning Radar language avoids employee evaluation claims.
- Commit trailers carry identity only and never score or readiness.
- Bridge evolution is versioned instead of silently breaking strict protocol-1 readers.
- No requirement contradicts the existing three-question, bounded disclosure, local scoring, or source-free Passport invariants.
- Earlier MVP stop rules are overridden only for the explicitly approved Iterations 8.13–8.16; all other expansion remains out of scope.
