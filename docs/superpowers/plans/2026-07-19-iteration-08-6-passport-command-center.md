# Iteration 8.6 Passport Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the latest source-free Change Passport into a safe local command center with show, list, verify, and deterministic JSON export workflows.

**Architecture:** Introduce a versioned `PassportReceipt` containing only change metadata and structured scores. Save a bounded JSON sidecar next to Markdown, read history through the receipt rather than parsing arbitrary prose, and keep existing v1 identity verification compatible. Picocli delegates to application use cases and initializes neither Codex nor JLine.

**Tech Stack:** Java 21, Maven, Picocli, Jackson already present, JUnit 5. No new dependency.

## Global Constraints

- Do not call real Codex.
- Preserve `passport --verify PATH`.
- Do not change Git state or existing Passport artifacts during show/list/verify.
- JSON and Markdown must contain no model-controlled prose, source, diff, or answers.
- Bound one receipt to 256 KiB, list at most 50 receipts, and reject symlinks.

---

### Task 1: Model a versioned source-free receipt

**Files:**
- Create: `src/main/java/dev/codedefense/domain/PassportReceipt.java`
- Create: `src/main/java/dev/codedefense/domain/PassportCategoryReceipt.java`
- Create: `src/main/java/dev/codedefense/domain/PassportFileReceipt.java`
- Create: `src/main/java/dev/codedefense/domain/ChangeKind.java`
- Create: `src/test/java/dev/codedefense/domain/PassportReceiptTest.java`

**Interfaces:**

```java
public enum ChangeKind { STAGED }

public record PassportCategoryReceipt(
        String id,
        Verdict primaryVerdict,
        int primaryScore,
        Optional<Verdict> followUpVerdict,
        Optional<Integer> followUpScore,
        int finalScore) { }

public record PassportReceipt(
        int schemaVersion,
        String receiptId,
        String repositoryIdentityHash,
        ChangeKind changeKind,
        String baseCommit,
        String sourceIdentity,
        String diffFingerprint,
        Instant createdAt,
        PassportStatus statusAtCreation,
        List<PassportFileReceipt> files,
        List<PassportCategoryReceipt> categories,
        int overallScore,
        Readiness readiness,
        int skippedPrimaryCount,
        String model) { }
```

- [ ] Write failing tests for schema version `1`, lowercase 64-character hashes, three ordered category IDs, bounded safe relative file paths, immutable collections, score ranges, follow-up pair consistency, and a content-free `toString()`.
- [ ] Run `mvn -Dtest=PassportReceiptTest test`; expect compilation failure.
- [ ] Implement records plus `PassportReceipt.from(ChangePassport, String receiptId)`; include only structured facts.
- [ ] Re-run the focused test; expect all tests green.

### Task 2: Render and parse deterministic receipt JSON

**Files:**
- Create: `src/main/java/dev/codedefense/passport/PassportReceiptJsonCodec.java`
- Create: `src/main/resources/schemas/change-passport-receipt.schema.json`
- Create: `src/test/java/dev/codedefense/passport/PassportReceiptJsonCodecTest.java`

**Interfaces:**

```java
public final class PassportReceiptJsonCodec {
    public byte[] encode(PassportReceipt receipt);
    public PassportReceipt decode(byte[] utf8Json);
}
```

- [ ] Write failing tests for deterministic bytes, trailing newline, strict UTF-8, trailing-token rejection, unknown/missing fields, fractional integers, duplicate paths, 256-KiB input cap, defensive byte copies, and absence of secret markers.
- [ ] Run `mvn -Dtest=PassportReceiptJsonCodecTest test`; expect failure.
- [ ] Implement with a locally configured strict `ObjectMapper`; do not mutate shared mapper configuration.
- [ ] Validate decoded domain invariants after Jackson parsing and map every invalid input to one safe persistence error.
- [ ] Re-run the test; expect green.

### Task 3: Store Markdown and receipt sidecars atomically

**Files:**
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Create: `src/main/java/dev/codedefense/passport/StoredChangePassport.java`
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportPaths.java`
- Modify: `src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java`

**Interfaces:**

```java
public interface ChangePassportStore {
    StoredChangePassport save(ChangePassport passport);
    Optional<StoredChangePassport> readLatest();
    List<StoredChangePassport> list(int limit);
    Optional<StoredPassportIdentity> readLatestIdentity();
}
```

- [ ] Add failing tests for paired `.md`/`.json` names, pointer publication only after both files exist, cleanup after partial failure, deterministic newest-first list order, maximum 50, unrelated-file ignore, corrupt sidecar failure, symlink rejection, and legacy v1 identity-only fallback.
- [ ] Run `mvn -Dtest=FileSystemChangePassportStoreTest test`; confirm RED.
- [ ] Implement save as temp Markdown + temp JSON -> publish both -> atomically replace latest pointer. Delete both published artifacts if pointer publication fails.
- [ ] Use bounded no-follow reads and real-path containment for list/latest.
- [ ] Re-run focused storage tests; expect green.

### Task 4: Add command-center application use cases

**Files:**
- Create: `src/main/java/dev/codedefense/application/ShowLatestChangePassportUseCase.java`
- Create: `src/main/java/dev/codedefense/application/ListChangePassportsUseCase.java`
- Create: `src/main/java/dev/codedefense/application/ExportChangePassportUseCase.java`
- Create: `src/main/java/dev/codedefense/passport/PassportTerminalRenderer.java`
- Create: `src/test/java/dev/codedefense/application/PassportCommandCenterUseCaseTest.java`

**Interfaces:**

```java
public final class ShowLatestChangePassportUseCase {
    public int show(Path repository, PrintWriter out, PrintWriter err);
}

public final class ListChangePassportsUseCase {
    public int list(Path repository, int limit, PrintWriter out, PrintWriter err);
}

public final class ExportChangePassportUseCase {
    public int export(Path repository, Path output, PrintWriter out, PrintWriter err);
}
```

- [ ] Write fakes-first tests for empty storage, CURRENT/EXPIRED rendering, three category rows, follow-up count, deterministic history, explicit export path, no overwrite by default, symlink output rejection, and zero Codex/JLine construction.
- [ ] Implement concise plain-text rendering; sanitize every domain-controlled label.
- [ ] Export the exact validated receipt bytes and never the Markdown artifact.
- [ ] Re-run use-case tests.

### Task 5: Expand `passport` CLI without breaking compatibility

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportShowCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportListCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportVerifyCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportExportCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Modify: `src/test/java/dev/codedefense/cli/PassportCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

- [ ] Write failing command tests for `passport show PATH`, `list PATH --limit 10`, `verify PATH`, `export PATH --format json --output FILE`, and legacy `passport --verify PATH`.
- [ ] Make subcommands use Picocli writers and hand-written injected use cases.
- [ ] Require `--format json` in 8.6; reject other formats without creating files.
- [ ] Run `mvn -Dtest=PassportCommandTest,CliFoundationTest test` with the property quoted on PowerShell.

### Task 6: Add the post-defense product card and verify offline

**Files:**
- Modify: `src/main/java/dev/codedefense/application/DefaultStagedChangeDefenseRunner.java`
- Modify: `src/test/java/dev/codedefense/application/StagedChangeDefenseRunnerTest.java`
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] Add a failing runner test requiring status, overall score, short fingerprint, and exact next commands after save; assert no source/answer/model prose.
- [ ] Render the card only after successful persistence.
- [ ] Document command-center syntax and explicitly state that JSON is an educational receipt, not merge approval.
- [ ] Run focused passport/runner/CLI tests.
- [ ] Run `mvn clean verify`, `mvn package`, root help, `passport --help`, and an offline filesystem acceptance fixture.
- [ ] Do not mark 8.6 complete until one human review confirms show/list/export readability.

## Suggested commits

```text
feat: add source-free passport receipts
feat: add passport command center
docs: document passport command workflows
```

## Stop rule

Do not begin commit/range capture, retries, handoff import, focus modes, IDE code, or app-server work in Iteration 8.6.
