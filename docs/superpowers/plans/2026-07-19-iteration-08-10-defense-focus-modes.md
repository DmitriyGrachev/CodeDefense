# Iteration 8.10 Evidence-Grounded Defense Focus Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the developer choose what kind of understanding to defend - balanced, architecture, failure modes, or testing - while preserving the fixed three-category interview and exact Git-change privacy boundary.

**Architecture:** Add a closed `DefenseFocus` domain enum and deterministic per-focus guidance. The selected focus changes only analysis/question emphasis; it does not change Git capture, evidence budgets, number of primary questions, follow-up limit, local scoring, or persistence privacy. The prompt receives a trusted focus directive outside all untrusted diff blocks. Validators still require the same ordered category IDs: decision, counterfactual, and test prediction.

**Tech Stack:** Java 21, Maven, existing Jackson/Picocli/AI ports, JUnit 5. No new dependency and no additional model call.

## Product Modes

| CLI value | Display name | Question emphasis |
|---|---|---|
| `balanced` | Balanced | intent, trade-offs, failure behavior, and validation |
| `architecture` | Architecture | component boundaries, dependencies, state transitions, and compatibility |
| `failure-modes` | Failure modes | degradation, retries, partial failure, rollback, and operational assumptions |
| `testing` | Testing | observable behavior, edge cases, regression boundaries, and falsifiable tests |

Focus is educational emphasis, not a security scan, test execution, or coverage claim.

## Global Constraints

- Exactly three primary questions and at most one follow-up per question.
- Existing category IDs and Java scoring formulas remain unchanged.
- Focus adds no source bytes and does not raise the 30-file or 120-KiB limits.
- Focus is persisted as one trusted enum value; no custom free-form focus prompt exists.
- Automated tests use fake `AiProvider` implementations and never call real Codex.
- Legacy receipts without focus decode as `BALANCED`.

---

### Task 1: Model the closed focus policy

**Files:**
- Create: `src/main/java/dev/codedefense/domain/DefenseFocus.java`
- Create: `src/main/java/dev/codedefense/analysis/DefenseFocusPolicy.java`
- Create: `src/test/java/dev/codedefense/analysis/DefenseFocusPolicyTest.java`

```java
public enum DefenseFocus {
    BALANCED("balanced", "Balanced"),
    ARCHITECTURE("architecture", "Architecture"),
    FAILURE_MODES("failure-modes", "Failure modes"),
    TESTING("testing", "Testing");
}

public record DefenseFocusPolicy(
        DefenseFocus focus,
        String analysisInstruction,
        List<String> requiredAngles) { }
```

- [ ] Write failing tests for exact CLI names/display labels, case-insensitive `Locale.ROOT` parsing, unknown value rejection, immutable required angles, bounded trusted instructions, and content-free `toString()`.
- [ ] Define fixed application-owned instructions. Never accept focus wording from environment variables, receipt files, or command-line free text.
- [ ] Require every policy to mention all three category responsibilities while weighting its own emphasis.
- [ ] Run `mvn -Dtest=DefenseFocusPolicyTest test`; expect green after minimal implementation.

### Task 2: Carry focus through requests and prompt boundaries

**Files:**
- Modify: `src/main/java/dev/codedefense/analysis/StagedChangeAnalyzer.java` or its 8.7 generalized successor `GitChangeAnalyzer.java`
- Modify: `src/main/java/dev/codedefense/analysis/AiStagedChangeAnalyzer.java` or `AiGitChangeAnalyzer.java`
- Modify: `src/main/java/dev/codedefense/analysis/StagedChangePromptFactory.java` or `GitChangePromptFactory.java`
- Modify: corresponding analyzer/prompt-factory tests

```java
ProjectAnalysis analyze(ProjectSnapshot snapshot, DefenseFocus focus);
```

- [ ] First add tests proving each focus directive appears exactly once in the trusted instruction section and never inside the untrusted snapshot delimiters.
- [ ] Seed malicious project metadata/diff containing focus labels and delimiter collisions; prove the generated prompt keeps collision-safe boundaries.
- [ ] Prove prompt/snapshot byte budgets are unchanged and `StructuredCodexRequest.toString()` remains content-free.
- [ ] Do not alter analysis schema shape or introduce focus-specific output fields.
- [ ] Run focused analyzer and prompt-factory tests.

### Task 3: Validate question relevance without brittle keyword checks

**Files:**
- Modify: `src/main/java/dev/codedefense/analysis/StagedChangeAnalysisValidator.java` or generalized successor
- Create: `src/main/java/dev/codedefense/analysis/FocusQuestionContract.java`
- Modify: validator tests

```java
public record FocusQuestionContract(
        DefenseFocus focus,
        List<String> orderedCategoryIds) { }
```

- [ ] Keep structural validation authoritative: exactly three ordered IDs, valid evidence locations, safe lengths, unique questions, and expected key-point bounds.
- [ ] Do not reject model output because it lacks a particular English keyword. Focus relevance is created by the prompt and assessed in offline fixtures/human acceptance.
- [ ] Prove a valid architecture-focused response with no literal word `architecture` passes, while wrong IDs/evidence still fail.
- [ ] Preserve one safe invalid-analysis exception with no raw JSON.

### Task 4: Add focus to workflow, preview, and source-free receipts

**Files:**
- Modify: `src/main/java/dev/codedefense/application/GitChangeDefenseRunner.java`
- Modify: `src/main/java/dev/codedefense/application/DefaultGitChangeDefenseRunner.java`
- Modify: `src/main/java/dev/codedefense/change/GitChangePreviewRenderer.java`
- Modify: `src/main/java/dev/codedefense/domain/PassportReceipt.java`
- Modify: `src/main/java/dev/codedefense/passport/PassportReceiptJsonCodec.java`
- Modify: focused runner/receipt/codec tests

- [ ] With hand-written fakes, prove selected focus flows preview -> analyzer and has no effect on Git capture or `InterviewConfig`.
- [ ] Dry-run prints `Focus: Failure modes`, `No source content was sent.`, and `Codex was not invoked.`
- [ ] Persist only the enum CLI value in receipt schema v3. Decode v1/v2 as balanced without rewriting old artifacts.
- [ ] Include focus in handoff envelopes by carrying the validated receipt; do not add model prose.
- [ ] Re-defense defaults to the parent attempt's focus unless the user supplies a new explicit focus, which starts a new attempt under the same diff fingerprint.

### Task 5: Add `--focus` to prove commands

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/ProveCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/ProveRetryCommand.java`
- Modify: `src/test/java/dev/codedefense/cli/ProveCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/ProveRetryCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

```powershell
codedefense prove --staged --focus balanced .
codedefense prove --range origin/main...HEAD --focus failure-modes .
codedefense prove --retry <ATTEMPT_ID> --focus testing .
```

- [ ] Default to balanced, expose exactly four values in help, reject unknown values before Git/Codex/JLine construction, and preserve all selector exclusivity.
- [ ] Test `--dry-run` for each focus and `--yes` behavior without real Codex.
- [ ] Ensure root/subcommand help and version require no runtime preflight.

### Task 6: Add deterministic machine-readable status for adapters

**Files:**
- Create: `src/main/java/dev/codedefense/application/PassportStatusView.java`
- Create: `src/main/java/dev/codedefense/passport/PassportStatusJsonCodec.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportShowCommand.java`
- Create: `src/test/java/dev/codedefense/passport/PassportStatusJsonCodecTest.java`
- Modify: command-center CLI tests

```powershell
codedefense passport show . --format json
```

```java
public record PassportStatusView(
        int protocolVersion,
        boolean present,
        PassportStatus status,
        String changeKind,
        String shortFingerprint,
        String focus,
        int attemptNumber,
        int overallScore,
        String readiness,
        List<PassportCategoryReceipt> categories) { }
```

- [ ] Define protocol version `1`, deterministic one-line JSON to stdout, and structured safe errors to stderr with a nonzero exit code.
- [ ] Test absent/current/expired states, stable property order, trailing LF, bounded values, no paths beyond safe relative evidence paths, and forbidden marker absence.
- [ ] Keep human text as the default format. This machine contract is the only status boundary the later JetBrains plugin may consume.

### Task 7: Offline acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] Document focus as emphasis rather than assurance and list the four modes.
- [ ] Run focused policy/prompt/validator/runner/receipt/CLI tests.
- [ ] Run `mvn clean verify`, `mvn package`, root/prove/passport help, and four staged dry-runs.
- [ ] With a fake provider, compare four generated prompts for the same captured change: snapshot bytes and category contract must match; only the trusted focus directive may differ.
- [ ] Human-review one fake analysis per focus for relevance before marking the iteration complete.
- [ ] No real Codex acceptance is required; any proposed real comparison needs separate authorization and a fixed maximum of four calls.

## Suggested commits

```text
feat: add evidence-grounded defense focus modes
feat: expose machine-readable passport status
docs: document defense focus workflows
```

## Stop rule

Do not add dynamic custom prompts, extra interview questions, automatic security claims, JetBrains plugin code, or Codex session provenance in Iteration 8.10.
