# Iteration 8.18 — Evidence Coverage Map Design

## Goal

Make the grounding of a staged-change defense visible without claiming that evidence coverage proves correctness, safety, review quality, or merge readiness.

CodeDefense will deterministically measure which bounded Git hunks are referenced by the three primary questions, update an IntelliJ coverage card as those questions appear, persist a safe aggregate in the Passport receipt, and keep the detailed path-and-range map local only.

## Scope

Iteration 8.18 covers staged, commit, and range defenses already supported by `prove`. It does not change question count, prompts, answer evaluation, scores, readiness, Passport identity, Git capture, Codex invocation count, or the definition of `CURRENT`.

The iteration adds:

- deterministic changed-line coverage calculation;
- progressive coverage events over a versioned bridge protocol;
- a grouped IntelliJ Evidence Coverage card;
- a bounded local detailed coverage sidecar;
- an aggregate coverage summary in new Passport receipts;
- a local-only `passport coverage` command.

It does not add CI behavior, GitHub access, cloud storage, source upload, a web dashboard, or model-generated coverage judgments.

## Coverage semantics

The unit of coverage is one bounded `StagedHunk` from the exact captured Git change.

A hunk is `REFERENCED` when at least one primary-question `CodeEvidence` range intersects a real changed new-side line in that hunk. Unified-diff context lines do not count. Multiple questions may reference the same hunk, but the hunk contributes once to the aggregate numerator.

For a pure deletion inside a surviving file, the nearest valid new-side anchor line represents the hunk. A fully deleted file remains measurable but non-navigable; its old-side location can be identified locally without pretending that the deleted file can be opened.

A hunk is `UNMEASURABLE` when bounded capture does not retain enough structural information to establish its changed-line ranges safely. In particular, a truncated hunk is not assumed to be uncovered. Unmeasurable hunks remain visible in totals but are excluded from the percentage denominator.

The aggregate is:

```text
coverage = referencedHunks / measurableHunks
```

When `measurableHunks == 0`, the percentage is unavailable rather than zero.

## Domain model

The core adds small immutable types:

- `EvidenceCoverageState`: `REFERENCED`, `UNREFERENCED`, `UNMEASURABLE`;
- `EvidenceCoverageHunk`: relative path, deterministic hunk ordinal, safe line range, navigation availability, state, and zero to three category IDs;
- `EvidenceCoverageMap`: exact change fingerprint plus a bounded ordered hunk list;
- `EvidenceCoverageSummary`: total, measurable, and referenced hunk counts with derived uncovered count and percentage;
- `EvidenceCoverageCalculator`: maps captured hunk structure and primary-question evidence to the map.

No coverage type contains unified diff text, source content, evidence reasons, question prompts, answers, feedback, expected key points, model output, absolute paths, or environment data. Their `toString()` methods expose counts and states only.

Changed new-side ranges are captured structurally while Git hunks are parsed. The calculator must not reparse prompt text or infer coverage from rendered Markdown.

## Progressive data flow

The complete map is calculated after staged-change analysis and before the interview starts. It is then revealed cumulatively:

1. Before any primary question, the IntelliJ card is empty or displays `Waiting for defense questions`.
2. When primary question 1 is rendered, CodeDefense emits the coverage contributed by category 1.
3. Questions 2 and 3 extend the cumulative referenced set.
4. Follow-up questions never add new coverage because they reuse the primary question evidence contract.
5. On completion, the final aggregate is attached to the Passport and the detailed local sidecar is stored.

This is deterministic presentation timing, not a new model request.

The active bridge session may display these local details while it owns the exact captured change and fingerprint. After the session ends or the IDE restarts, detailed rows are loaded only from the sidecar and only while that fingerprint is still `CURRENT`.

## Persistence boundary

### Portable receipt summary

New receipts use schema version 5. Schema 5 always includes:

```json
{
  "evidenceCoverage": {
    "totalHunks": 12,
    "measurableHunks": 12,
    "referencedHunks": 9
  },
  "codexProvenance": null
}
```

`codexProvenance` may contain the existing bounded provenance object or `null`. This replaces the old coupling between schema number and provenance presence while retaining strict exact-field validation. Schemas 1–4 continue to decode unchanged and expose coverage as unavailable.

Exports and portable handoffs include only the aggregate summary. They never include the detailed map.

### Local detailed sidecar

The detailed map is stored next to the paired Passport artifacts as a separate bounded file associated with receipt ID, repository identity, and full diff fingerprint. It contains only relative paths, line ranges, hunk ordinals, navigation flags, coverage states, and category IDs.

The sidecar:

- is capped at 256 KiB and 256 hunks;
- uses strict UTF-8 and strict deterministic JSON;
- follows existing containment, symlink, atomic-write, and bounded-read rules;
- is never exported, handed off, added to a commit trailer, sent to Codex, or consumed by CI;
- is ignored when its receipt identity or current Git fingerprint does not match.

Missing, corrupt, unsafe, or stale sidecars degrade to the aggregate summary. They do not invalidate the Passport.

## Bridge protocol

Bridge protocol 3 adds a bounded `coverage` event and capability `evidenceCoverageV1`. Protocols 1 and 2 retain their exact existing event shapes.

A coverage event contains:

- cumulative summary counts;
- at most 30 files and 256 hunks;
- relative paths and safe line ranges;
- state, navigation flag, and category IDs;
- the fixed educational disclaimer.

It contains no source, diff text, question prompt, answer, feedback, score, readiness, evidence reason, expected key point, or absolute path.

The bundled IntelliJ plugin and CLI use protocol 3. An override CLI that rejects protocol 3 may fall back before any bridge event, confirmation, or possible model invocation, preserving the existing no-double-send rule.

## IntelliJ design

The chosen layout is a separate Evidence Coverage card between the defense session and repository-local defense history.

The card displays:

```text
Evidence Coverage                         9 / 12 · 75%

Config.java                               0 / 2
○ hunk 2 · lines 41–48                    Not referenced

RetryPolicy.java                          1 / 1
✓ hunk 1 · lines 12–18                    Decision

Evidence use only — not correctness or safety coverage.
```

Files with uncovered hunks appear first. Files and hunks otherwise use deterministic path and line ordering. State is communicated with text and symbols, not color alone.

Clicks reuse the existing `EvidenceNavigator`. Deleted, unavailable, unsafe, expired, or unmeasurable locations are disabled with a safe explanation. When the exact fingerprint expires, detailed rows are cleared immediately; the persisted summary may remain visible with an explicit stale/unavailable label.

## CLI design

The local-only command is:

```text
codedefense passport coverage [PATH] [--format text|json]
```

It never initializes JLine or an AI provider. For an exact `CURRENT` change and an intact sidecar, it renders the detailed map. For expired, missing, or unsafe detail, it renders only the aggregate summary and a safe reason. JSON output is strict, versioned, bounded, and source-free.

## Error handling

- Coverage calculation failure must not abort an otherwise valid defense; it produces unavailable coverage.
- Receipt persistence remains authoritative. A sidecar write failure is reported safely but does not discard an already saved Passport.
- A receipt summary invariant violation rejects the receipt.
- Unsafe or corrupt sidecars never leak parser, filesystem, or absolute-path diagnostics.
- Coverage never changes scoring, readiness, question flow, `CURRENT`, or commit behavior.

## Minimum focused tests

The implementation intentionally keeps new tests small and high-value:

1. changed-line overlap is referenced while context-only evidence is not;
2. pure deletion uses the defined anchor behavior;
3. cumulative question updates produce the final summary and percentage;
4. one parameterized receipt test preserves schemas 1–4 and validates schema 5;
5. one sidecar test covers bounds, privacy fields, and unsafe symlink rejection;
6. one bridge codec test covers the bounded protocol-3 event;
7. one IntelliJ test covers uncovered-first grouping and safe navigation.

No test invokes real Codex.

## Acceptance

Iteration 8.18 is accepted when a staged defense in IntelliJ visibly grows the coverage card across three primary questions, the final counts match deterministic hunk/evidence fixtures, a restart can show the safe summary, a changed fingerprint removes detailed rows, and all outputs retain the disclaimer that evidence use is not correctness or safety coverage.
