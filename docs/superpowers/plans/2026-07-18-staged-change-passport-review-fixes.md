# Staged Change Passport Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make staged Change Passport evidence point to the actual staged hunks, support deletion-only changes, accurately render adaptive follow-ups, and make verification free of Git-object writes.

**Architecture:** The Git adapter will calculate a read-only index identity from raw index entries and capture bounded, parsed unified hunks for eligible staged files. `StagedChangeContextBuilder` will render only those hunks and retain their original line ranges as snapshot provenance. The passport domain will require its session results to match the analysis questions in order, and the renderer will publish safe primary and optional follow-up evaluation summaries without answers.

**Tech Stack:** Java 21, Maven, JUnit 5, existing bounded `ProcessExecutor`, existing Git CLI adapter; no new dependencies and no real Codex calls.

## Global Constraints

- Remain on `feat/iteration-08-5-staged-change-passport` and do not begin Iteration 9.
- Do not add dependencies or call real Codex from tests.
- Do not invoke a shell, external diff driver, or text-conversion driver for Git capture.
- Every Git command uses `ProcessSpec` with tokenized arguments and bounded stdout/stderr.
- Prompt content receives only eligible, redacted, bounded staged or HEAD deletion hunks; it never receives a working-tree path resolution or a whole-file prefix as evidence.
- The snapshot remains limited to 30 selected files and 120 KiB through `CodeDefenseConfig` and `SnapshotBudget`.
- `passport --verify` must not call `git write-tree`, change the working tree/index, create passport artifacts, or update the latest pointer.
- Automated verification is offline and must not call Codex.

---

## Affected Files

- Create: `src/main/java/dev/codedefense/change/StagedHunk.java` — immutable, source-hidden unified-hunk transfer object.
- Create: `src/main/java/dev/codedefense/domain/SourceLineRange.java` — normalized original-source range used by snapshot evidence validation.
- Modify: `src/main/java/dev/codedefense/change/CapturedStagedChange.java` — replace prefix blobs with hunk evidence.
- Modify: `src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java` — remove `write-tree` and blob-prefix capture; calculate raw-entry identity and parse bounded per-file hunks.
- Delete: `src/main/java/dev/codedefense/change/IndexBlob.java` — whole-file prefix transport is no longer evidence.
- Modify: `src/main/java/dev/codedefense/change/StagedChangeContextBuilder.java` — render redacted hunk blocks, including HEAD-only deletion evidence.
- Modify: `src/main/java/dev/codedefense/domain/ProjectSnapshot.java` — store allowed original evidence ranges while preserving `includedLines()` for report metadata.
- Modify: `src/main/java/dev/codedefense/analysis/ProjectAnalysisValidator.java` — accept evidence only when its full range belongs to selected provenance.
- Modify: `src/main/java/dev/codedefense/domain/StagedChange.java` and `src/main/java/dev/codedefense/domain/StagedChangeIdentity.java` — rename the non-Git-object state field to `indexIdentity` and validate it as SHA-256.
- Modify: `src/main/java/dev/codedefense/passport/StoredPassportIdentity.java`, `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`, `src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java`, `src/main/java/dev/codedefense/change/StagedChangePreviewRenderer.java`, `src/main/java/dev/codedefense/application/ChangePassportService.java`, and `src/main/java/dev/codedefense/application/VerifyLatestChangePassportUseCase.java` — persist/compare the read-only `indexIdentity` metadata.
- Modify: `src/main/java/dev/codedefense/domain/ChangePassport.java` — require ordered analysis/session question ID agreement.
- Modify focused tests under `src/test/java/dev/codedefense/change`, `src/test/java/dev/codedefense/analysis`, `src/test/java/dev/codedefense/domain`, `src/test/java/dev/codedefense/passport`, and `src/test/java/dev/codedefense/application`.
- Modify: `README.md` — retain the accurate read-only verification claim and describe the index identity rather than a Git tree object.

## Task 1: Read-only staged identity and bounded hunk transfer

**Files:**
- Create: `src/main/java/dev/codedefense/change/StagedHunk.java`
- Modify: `src/main/java/dev/codedefense/change/CapturedStagedChange.java`
- Modify: `src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java`
- Modify: `src/main/java/dev/codedefense/domain/StagedChange.java`
- Modify: `src/main/java/dev/codedefense/domain/StagedChangeIdentity.java`
- Test: `src/test/java/dev/codedefense/change/GitCliStagedChangeSourceTest.java`
- Test: `src/test/java/dev/codedefense/domain/StagedChangeIdentityTest.java`

**Interfaces:**

```java
public record StagedHunk(
        StagedChangeFile file,
        int oldStartLine,
        int oldLineCount,
        int newStartLine,
        int newLineCount,
        String unifiedContent,
        boolean truncated) { }

public record CapturedStagedChange(
        StagedChange change,
        List<StagedHunk> hunks) { }
```

`StagedHunk` must copy no mutable state, reject invalid non-empty ranges/content, and override `toString()` so it exposes path/ranges/counts but never `unifiedContent`. For deletion hunks `newLineCount` is zero and the context renderer uses `EVIDENCE_STATE: DELETED_FROM_INDEX`.

- [ ] **Step 1: Write the failing Git-adapter tests**

Add a fake `ProcessExecutor` expectation proving that capture:

```java
assertFalse(executor.commands().stream().anyMatch(command -> command.contains("write-tree")));
assertTrue(executor.commands().stream().anyMatch(command -> command.equals(List.of(
    "git", "-C", root.toString(), "diff", "--cached", "--no-ext-diff", "--no-textconv",
    "--unified=3", "--no-color", "--", "src/LargeService.java"))));
assertEquals("changedLine();", captured.hunks().getFirst().unifiedContent());
```

The fixture must use a staged `MODIFIED` Java file whose returned hunk has `@@ -1497,7 +1497,8 @@`, and a staged `DELETED` Java file whose hunk has no new lines. Assert the adapter returns a SHA-256 `indexIdentity`, never a 40/64-character Git tree object.

- [ ] **Step 2: Run the focused test and observe RED**

Run:

```powershell
mvn -Dtest=GitCliStagedChangeSourceTest,StagedChangeIdentityTest test
```

Expected: compilation/test failure because hunk evidence and `indexIdentity` do not yet exist; the former blob-prefix/write-tree behavior must not satisfy the new assertions.

- [ ] **Step 3: Implement the minimal read-only capture**

Replace `git write-tree` with a canonical identity serialization built from the already parsed raw records:

```java
String canonical = rawEntries.stream()
        .sorted(Comparator.comparing(entry -> portable(entry.path())))
        .map(entry -> entry.oldMode() + "\\0" + entry.newMode() + "\\0"
                + entry.oldObjectId() + "\\0" + entry.newObjectId() + "\\0"
                + entry.status() + "\\0" + portable(entry.path()))
        .collect(Collectors.joining("\\0"));
String indexIdentity = sha256("codedefense-index-v1\\0" + baseCommit + "\\0" + canonical);
String diffFingerprint = sha256("codedefense-staged-change-v2\\0" + baseCommit + "\\0" + canonical);
```

For each safe eligible file, execute exactly tokenized `git diff --cached --no-ext-diff --no-textconv --unified=3 --no-color -- <known relative path>`. Capture a bounded byte prefix, decode only complete UTF-8 lines, parse only `@@ -old[,count] +new[,count] @@` ranges plus space/`+`/`-` lines, and associate the parsed hunks with the already validated `StagedChangeFile`; never trust patch headers as paths. Preserve a `truncated` marker when the hunk command output exceeds its capture bound. Reject malformed output with the existing safe `GitChangeException` kind, without including diff text in an exception.

- [ ] **Step 4: Run the focused test and observe GREEN**

Run:

```powershell
mvn -Dtest=GitCliStagedChangeSourceTest,StagedChangeIdentityTest test
```

Expected: all tests pass; fake command logs contain no `write-tree`, and no test launches Codex.

- [ ] **Step 5: Commit boundary (only after all plan tasks verify)**

Do not commit this task independently if later tasks need the same model rename. Include it in the final intentional review-fix commit.

## Task 2: Hunk-only snapshot evidence, original ranges, and deletion-only snapshots

**Files:**
- Create: `src/main/java/dev/codedefense/domain/SourceLineRange.java`
- Delete: `src/main/java/dev/codedefense/change/IndexBlob.java`
- Modify: `src/main/java/dev/codedefense/change/StagedChangeContextBuilder.java`
- Modify: `src/main/java/dev/codedefense/domain/ProjectSnapshot.java`
- Modify: `src/main/java/dev/codedefense/analysis/ProjectAnalysisValidator.java`
- Test: `src/test/java/dev/codedefense/change/StagedChangeContextBuilderTest.java`
- Test: `src/test/java/dev/codedefense/analysis/StagedChangeAnalysisValidatorTest.java`
- Test: all fixture callers that construct `CapturedStagedChange`

**Interfaces:**

```java
public record SourceLineRange(int startLine, int endLine) {
    public boolean contains(int start, int end) {
        return start >= startLine && end <= endLine;
    }
}

public record SelectedFile(
        Path relativePath,
        int includedLines,
        List<SourceLineRange> evidenceRanges,
        boolean truncated,
        int renderedBytes) {
    public SelectedFile(Path path, int lines, boolean truncated, int bytes) {
        this(path, lines, List.of(new SourceLineRange(1, lines)), truncated, bytes);
    }
}
```

- [ ] **Step 1: Write the failing hunk-context regressions**

Replace the prefix-only tests with these observable contracts:

```java
assertTrue(snapshot.promptContent().contains("STAGED_HUNK: src/LargeService.java"));
assertTrue(snapshot.promptContent().contains("NEW_LINES: 1497-1504"));
assertTrue(snapshot.promptContent().contains("changedLine();"));
assertTrue(snapshot.promptContent().contains("nearbyContext();"));
assertFalse(snapshot.promptContent().contains("UNRELATED_PREFIX_ONLY_DATA"));

assertTrue(deleteOnly.promptContent().contains("EVIDENCE_STATE: DELETED_FROM_INDEX"));
assertTrue(deleteOnly.promptContent().contains("HEAD_HUNK: src/RemovedService.java"));
assertEquals(1, deleteOnly.selectedFiles().size());
```

Also assert an evidence reference `1497-1504` is accepted for the hunk-selected file while `1-2` is rejected if it lies outside every retained hunk range. Keep existing secret-redaction and byte-limit assertions, but seed the secret inside a hunk rather than a file prefix.

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
mvn -Dtest=StagedChangeContextBuilderTest,StagedChangeAnalysisValidatorTest test
```

Expected: tests fail because the builder still renders `INDEX_FILE`/`HEAD_FILE`, treats `DELETED` as ineligible, and permits only line numbers from one.

- [ ] **Step 3: Implement the bounded hunk renderer**

Group trusted `StagedHunk` objects by file, filter with the existing `ProjectFileFilter`, prioritize deterministically, and render only redacted hunk blocks. The exact header shape is:

```text
STAGED_HUNK: src/LargeService.java
EVIDENCE_STATE: STAGED_INDEX
STATUS: MODIFIED
OLD_LINES: 1497-1503
NEW_LINES: 1497-1504
TRUNCATED: false
OLD 1497 | previousCall();
NEW 1500 | changedLine();
```

For deleted entries, render `HEAD_HUNK` and `EVIDENCE_STATE: DELETED_FROM_INDEX`; select original old ranges as evidence ranges. Fit a whole hunk block through `SnapshotBudget`, reserve the final separator newline before appending, redact before fitting, and add only markers present in a selected fitted block to `redactionCount`.

Change `ProjectAnalysisValidator` from `Map<String, Integer>` to `Map<String, SelectedFile>` and reject evidence unless `selectedFile.evidenceRanges().stream().anyMatch(range -> range.contains(start, end))`. Existing filesystem snapshots use the four-argument `SelectedFile` constructor and therefore retain their `1..includedLines` behavior.

- [ ] **Step 4: Run the focused tests and observe GREEN**

Run:

```powershell
mvn -Dtest=StagedChangeContextBuilderTest,StagedChangeAnalysisValidatorTest test
```

Expected: all tests pass. The changed line beyond the former 24 KiB prefix is present, unrelated prefix data is absent, and a deletion-only project produces one selected HEAD evidence block.

- [ ] **Step 5: Check formatting and byte invariants**

Run:

```powershell
mvn -Dtest=ProjectSnapshotBuilderTest,ProjectAnalysisValidatorTest,StagedChangeContextBuilderTest test
```

Expected: existing normal-snapshot range behavior remains green and no test invokes Codex.

## Task 3: Passport invariants, adaptive follow-up rendering, persistence identity, and documentation

**Files:**
- Modify: `src/main/java/dev/codedefense/domain/ChangePassport.java`
- Modify: `src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java`
- Modify: `src/main/java/dev/codedefense/passport/StoredPassportIdentity.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/application/ChangePassportService.java`
- Modify: `src/main/java/dev/codedefense/application/VerifyLatestChangePassportUseCase.java`
- Modify: `src/main/java/dev/codedefense/change/StagedChangePreviewRenderer.java`
- Modify: `README.md`
- Test: `src/test/java/dev/codedefense/domain/ChangePassportTest.java`
- Test: `src/test/java/dev/codedefense/passport/MarkdownChangePassportRendererTest.java`
- Test: `src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java`
- Test: `src/test/java/dev/codedefense/application/ChangePassportServiceTest.java`
- Test: `src/test/java/dev/codedefense/application/VerifyLatestChangePassportUseCaseTest.java`

- [ ] **Step 1: Write failing domain and renderer tests**

Add a domain test that uses a valid three-question analysis but swaps two `InterviewSession.results()` entries:

```java
assertThrows(IllegalArgumentException.class, () -> new ChangePassport(
        change, analysis, reorderedSession, Instant.parse("2026-07-18T00:00:00Z"), "model", PassportStatus.CURRENT));
```

Create a valid `QuestionResult` with a real `Optional<InterviewTurn>` follow-up. Assert rendered Markdown contains, in order:

```text
### Primary evaluation
- Verdict: PARTIAL
- Score: 42/100
### Follow-up evaluation
- Question: ...
- Verdict: CORRECT
- Score: 80/100
- Feedback: ...
- Understood concepts: ...
- Knowledge gaps: ...
- Local final score: 61/100
```

Assert the Markdown does not contain either primary or follow-up answer strings, expected key points, raw JSON, internal prompts/schemas, or evidence reasons.

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
mvn -Dtest=ChangePassportTest,MarkdownChangePassportRendererTest,FileSystemChangePassportStoreTest,ChangePassportServiceTest,VerifyLatestChangePassportUseCaseTest test
```

Expected: reordered results are accepted, the follow-up section is absent, and identity tests still refer to a Git tree field.

- [ ] **Step 3: Implement minimum safe behavior**

In `ChangePassport`, require every position to match exactly:

```java
for (int index = 0; index < analysis.questions().size(); index++) {
    if (!analysis.questions().get(index).id().equals(session.results().get(index).question().id())) {
        throw new IllegalArgumentException("session results must match analysis questions in order");
    }
}
```

Use explicit renderer helpers `appendEvaluation(markdown, heading, turn)` for `PRIMARY` and optional `FOLLOW_UP`. Never call `turn.answer()` in the renderer. Render `Local final score` only after the optional follow-up section.

Replace persisted `tree=` metadata with `index=` and parse only the new field. If the latest artifact has legacy `tree=` metadata, return no matching current identity and make `passport --verify` report `EXPIRED`; never claim a legacy tree-object identity is equivalent to the new read-only index identity. Update README language to say verification is read-only and compares a deterministic index identity, not a materialized Git tree.

- [ ] **Step 4: Run the focused tests and observe GREEN**

Run:

```powershell
mvn -Dtest=ChangePassportTest,MarkdownChangePassportRendererTest,FileSystemChangePassportStoreTest,ChangePassportServiceTest,VerifyLatestChangePassportUseCaseTest test
```

Expected: all tests pass with safe source-free rendering and `index=` metadata; no fake executor receives `write-tree`.

- [ ] **Step 5: Run complete offline verification**

Run:

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar start . --dry-run
git diff --no-ext-diff --no-textconv --check
```

Expected: Maven verification/package succeed, help and dry run exit zero without Codex, and Git whitespace check exits zero. Do not run `start --yes` or any live smoke script.

- [ ] **Step 6: Commit only after green verification**

Stage only review-fix files plus this plan/README, explicitly excluding unrelated untracked research, then create one intentional commit. Do not commit automatically if repository permissions prevent it; report the exact user-side Git command instead.

## Self-Review

- Spec coverage: Task 1 resolves actual changed-code visibility and removes `write-tree`; Task 2 covers large-file hunk evidence, bounded redacted prompt construction, and deletion-only changes; Task 3 covers follow-up passport reporting, session/question ordering, persistence metadata, and accurate read-only documentation.
- Placeholder scan: no TODO/TBD steps; each behavior has an exact file, API, command, and expected result.
- Type consistency: `StagedHunk` is the only source-content transfer object; `SourceLineRange` supplies `SelectedFile.evidenceRanges`; `indexIdentity` is consistently the SHA-256 field compared in capture, persistence, verification, preview, and renderer metadata.
