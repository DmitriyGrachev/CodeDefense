# CodeDefense Product Roadmap Design: Iterations 8.6–8.12

## Product thesis

CodeDefense is the human-ownership layer between an AI-assisted Git change and code review. It does not review code, certify authorship, approve a merge, rank employees, or replace automated tests. It binds a bounded human technical defense to an exact local Git change and produces a source-free Change Passport.

The product loop is:

```text
select exact Git change
-> preview bounded disclosure
-> run three-category human defense
-> compute scores locally
-> save source-free Passport
-> show CURRENT or EXPIRED when the change is checked again
```

## Sequencing decision

Iteration 9 remains the final reliability, packaging, documentation, and submission iteration. All approved product expansion is numbered 8.6 through 8.12 and must land sequentially. Every iteration must leave the full Maven suite green and must be independently releasable.

| Iteration | Product outcome | Dependency |
|---|---|---|
| 8.6 | Passport Command Center: show, list, verify, deterministic JSON export | 8.5 privacy boundary |
| 8.7 | Defend staged, commit, or merge-base range changes | 8.6 receipt format |
| 8.8 | Re-defense attempts and deterministic learning guidance | 8.7 generalized identity |
| 8.9 | Portable handoff package with local integrity and change matching | 8.8 attempt lineage |
| 8.10 | Evidence-grounded defense focus modes | 8.9 portable receipt |
| 8.11 | JetBrains plugin over a stable local bridge protocol | 8.10 machine-readable commands |
| 8.12 | Experimental, consented Codex thread-to-diff provenance | 8.11 stable adapter boundaries |
| 9 | Final productization and release | all accepted 8.x work |

## Cross-iteration invariants

- Java 21 and Maven remain authoritative for the core CLI.
- The JetBrains adapter is an explicitly approved packaging exception: it uses the official IntelliJ Platform Gradle Plugin in an isolated `jetbrains-plugin/` build and contains no core business logic.
- No analyzed source code is executed.
- Git commands use explicit tokens, never a shell string, aliases, external diff drivers, or text-conversion filters.
- Source selection remains bounded to 30 files and 120 KiB.
- Preview and confirmation remain mandatory unless `--yes` is explicit.
- Automated tests never call real Codex.
- Exactly three primary categories remain: decision, counterfactual, and test prediction.
- At most one follow-up is asked per category.
- Java computes category and overall scores.
- Persisted Passport data contains no source, raw diff, user answers, prompts, model feedback, concepts, schemas, or raw model output.
- No status is named PASS, APPROVED, CERTIFIED, SAFE, or COMPLIANT.
- History is keyed to change identity and attempt, never to an employee profile.
- New JSON formats use strict schemas, bounded UTF-8, deterministic property ordering, and explicit versioning.
- Every external file boundary rejects symlinks, traversal, absolute-path injection, and oversized content.

## Shared domain direction

Iteration 8.6 introduces a source-free `PassportReceipt` as the stable machine-readable representation. Markdown becomes one renderer of the receipt; JSON becomes another. Later iterations extend the receipt by schema version without parsing arbitrary Markdown prose.

Iteration 8.7 generalizes staged-only names into Git-change names. The identity records a `ChangeKind`, resolved immutable Git object IDs, a source identity, and a diff fingerprint. User-supplied refs are resolved before capture and are never passed to subsequent Git commands.

Iterations 8.8 and 8.9 add attempt lineage and a portable envelope. Neither adds signatures or authenticity claims. A checksum detects accidental corruption only; it does not prove who created the package.

Iteration 8.10 changes question policy without changing the three-category contract. Iteration 8.11 adds a versioned local bridge and a JetBrains tool window that launches the bundled CLI JAR without duplicating Git, privacy, analysis, scoring, or persistence logic. Iteration 8.12 is an experimental adapter with explicit consent, strict capability checks, and a kill switch.

## User-facing wow path

```powershell
codedefense prove --range origin/main...HEAD
codedefense passport show .
codedefense passport export . --format json --output passport.json

# after the selected Git change moves
codedefense passport verify .
# Change Passport: EXPIRED
```

The terminal emphasizes exact identity, the three category scores, follow-up presence, and the transition from CURRENT to EXPIRED. It never prints hidden evaluation material.

## Error and compatibility policy

- Existing `prove --staged` and `passport --verify PATH` syntax remains supported.
- Legacy v1 Markdown-only Passports remain verifiable but are not silently upgraded.
- New readers reject unknown required schema versions with safe messages.
- Missing history, unavailable Git objects, malformed packages, unsupported app-server capabilities, and provenance mismatches are non-secret typed outcomes.
- Cancellation and interruption preserve existing exit semantics.

## Acceptance strategy

Each iteration requires focused TDD tests, `mvn clean verify`, `mvn package`, root/subcommand help, and an offline acceptance fixture. Real Codex runs require separate explicit authorization and are never repeated automatically after failure.

## Deliberate non-goals before Iteration 9

- no automatic merge blocking;
- no GitHub token or GitHub URL ingestion;
- no cloud database or team dashboard;
- no employee ranking;
- no security-scanner claims;
- no cryptographic signing;
- no automatic transcript collection;
- no claim that a Codex thread authored a commit.

## Design self-review

- The dependency order is acyclic: receipt -> generalized change -> attempts -> handoff -> focus -> JetBrains adapter -> experimental provenance.
- Every persisted representation remains source-free.
- JetBrains and provenance work depend on stable core contracts instead of bypassing them.
- Iteration 9 remains last and is not specified or implemented here.
