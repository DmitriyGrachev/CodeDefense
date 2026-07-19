# Iteration 8.8 Re-defense and Attempt Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a developer repeat the complete three-category defense for an unchanged Git change and view improvement as change-scoped attempt history.

**Architecture:** Add immutable attempt lineage to the source-free receipt and a retry use case that first proves the selected Git identity is still current. A retry always generates a new analysis/interview attempt and never copies answers or asks only weak categories. Deterministic Java guidance maps category outcomes to next actions without another AI call.

**Tech Stack:** Java 21, Maven, existing Git/AI/interview/passport ports, JUnit 5. No new dependency.

## Global Constraints

- History belongs to a diff fingerprint, not a person.
- Do not store identity, email, username, answer, question, feedback, or concepts.
- Retry all three categories and preserve one-follow-up maximum.
- Do not call Codex until identity verification and confirmation succeed.

---

### Task 1: Add attempt lineage invariants

**Files:**
- Create: `src/main/java/dev/codedefense/domain/PassportAttemptId.java`
- Modify: `src/main/java/dev/codedefense/domain/PassportReceipt.java`
- Create: `src/main/java/dev/codedefense/domain/PassportAttemptSummary.java`
- Modify: `src/test/java/dev/codedefense/domain/PassportReceiptTest.java`

```java
public record PassportAttemptId(String value) { }

public record PassportAttemptSummary(
        PassportAttemptId attemptId,
        Optional<PassportAttemptId> supersedes,
        int attemptNumber,
        String diffFingerprint,
        Instant createdAt,
        int overallScore,
        Readiness readiness,
        List<PassportCategoryReceipt> categories) { }
```

- [ ] Test UUID-format IDs, attempt number `>=1`, first attempt without parent, later attempts with different parent, same fingerprint across a chain, no cycles within a loaded history, and safe `toString`.
- [ ] Introduce `AttemptIdGenerator` and inject deterministic IDs in tests.
- [ ] Bump receipt schema to v2; retain strict read support for v1 with synthesized attempt number 1 and no parent.

### Task 2: Group bounded history by fingerprint

**Files:**
- Create: `src/main/java/dev/codedefense/passport/ChangePassportHistory.java`
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Modify: `src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java`

```java
public interface ChangePassportStore {
    List<StoredChangePassport> listByFingerprint(String fingerprint, int limit);
}
```

- [ ] Test newest-first bounded loading, lineage validation, orphan parent handling as safe unreadable history, duplicate attempt ID rejection, and no cross-repository/fingerprint grouping.
- [ ] Limit one timeline to 20 attempts and global list to the existing 50.
- [ ] Never rewrite previous artifacts when a new attempt is saved.

### Task 3: Implement deterministic learning guidance

**Files:**
- Create: `src/main/java/dev/codedefense/application/DefenseGuidanceFactory.java`
- Create: `src/main/java/dev/codedefense/domain/DefenseGuidance.java`
- Create: `src/test/java/dev/codedefense/application/DefenseGuidanceFactoryTest.java`

```java
public record DefenseGuidance(String categoryId, String message) { }
```

- [ ] Define fixed messages for skipped, incorrect, partial, and strong results in each of decision/counterfactual/test-prediction.
- [ ] Tests must prove messages depend only on category ID, verdict and score, contain no model text, and are deterministic.
- [ ] Keep language educational and never say pass/fail/approved.

### Task 4: Add retry workflow

**Files:**
- Create: `src/main/java/dev/codedefense/application/RetryChangeDefenseUseCase.java`
- Modify: `src/main/java/dev/codedefense/application/DefaultGitChangeDefenseRunner.java`
- Create: `src/test/java/dev/codedefense/application/RetryChangeDefenseUseCaseTest.java`

```java
public final class RetryChangeDefenseUseCase {
    public int retry(PassportAttemptId attemptId, Path repository,
            boolean dryRun, boolean yes, PrintWriter out, PrintWriter err);
}
```

- [ ] With fakes, assert order: load receipt -> resolve stored selector/object IDs -> recapture identity -> require CURRENT -> preview -> dry-run/confirm -> lazy runtime -> fresh analysis -> full interview -> save child attempt.
- [ ] Identity mismatch returns a safe EXPIRED message and constructs no runtime/JLine.
- [ ] Cancellation or model failure creates no child attempt.
- [ ] Retry never reuses prior questions, answers, evaluations, or scores.

### Task 5: Add CLI timeline and retry commands

**Files:**
- Create: `src/main/java/dev/codedefense/cli/PassportTimelineCommand.java`
- Create: `src/main/java/dev/codedefense/cli/ProveRetryCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/ProveCommand.java`
- Modify relevant CLI tests.

```powershell
codedefense passport timeline .
codedefense prove --retry <ATTEMPT_ID> .
```

- [ ] Render attempt number, timestamp, score delta, readiness, and three category score deltas.
- [ ] Do not render global developer progress or compare different fingerprints.
- [ ] Require explicit confirmation for non-dry retry.
- [ ] Cover unknown ID, wrong repository, expired change, and legacy receipt behavior.

### Task 6: Offline acceptance

- [ ] Update README and checklist without marking Iteration 9.
- [ ] Run focused receipt/store/guidance/retry/CLI tests.
- [ ] Run `mvn clean verify`, `mvn package`, help, and dry retry fixture.
- [ ] With fake AI, create two attempts and prove old artifact hashes are unchanged, timeline delta is correct, and no private prose is persisted.
- [ ] Any real retry requires a separately approved model run.

## Suggested commits

```text
feat: add change-scoped passport attempts
feat: add complete change re-defense workflow
docs: document passport attempt timelines
```

## Stop rule

Do not add partial-category retries, user rankings, portable package import, focus modes, IDE integration, or session provenance.
