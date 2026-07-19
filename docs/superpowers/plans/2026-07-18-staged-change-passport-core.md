# Staged Change Passport Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add prove --staged to assess understanding of the exact Git index diff and persist a local, verifiable Markdown Change Passport.

**Architecture:** A Git adapter derives a read-only SHA-256 index identity from HEAD plus canonical raw staged entries, then captures bounded, parsed unified hunks without reading the working tree. Literal pathspecs, exact-rename options, a 30-file subprocess cap, and a final identity recapture keep the initial snapshot safe and internally consistent. A dedicated staged-context builder applies the existing limits, redaction, original line ranges, preview, confirmation, structured Codex analysis, and interview engine. A separate passport store persists Markdown plus a bounded metadata envelope needed to determine CURRENT or EXPIRED.

**Tech Stack:** Java 21, Maven, Picocli, JUnit 5, Jackson, the existing bounded ProcessExecutor, and the local Git CLI. No new dependencies.

## Global Constraints

- Implement Iteration 8.5 only; do not begin optional Iteration 8.6 or Iteration 9.
- The command is java -jar target/codedefense.jar prove --staged [PATH]; PATH defaults to dot.
- Use the Git index and HEAD tree as the source of truth. Never read staged source content from the working tree.
- Invoke Git only as explicit command tokens through the existing process boundary. Never use a shell, Git aliases, external diff drivers, or text-conversion filters.
- Do not execute analyzed code, tests, Git hooks, external diff programs, or Codex in automated tests.
- Preserve the 30-file, 120-KiB total, per-file byte, UTF-8, redaction, prompt-boundary, preview, confirmation, local-score, three-primary-question, and one-follow-up limits.
- Require exactly one analysis question with each stable ID: decision, counterfactual, and test-prediction.
- First-release passports always display Codex session link: NOT_REQUESTED.
- Do not implement app-server, thread discovery, HTML, JSON, browser opening, Skill, GitHub, PR, CI, signing, cloud storage, dashboards, or team scoring.
- prove --dry-run must construct neither Codex runtime nor JLine nor a passport and must print the existing no-send/no-Codex lines.
- No source snapshot, raw diff, Git command output, transcript, raw model JSON, answer, prompt, schema, or environment secret may appear in exception messages, toString, terminal diagnostics, or persisted passport metadata.
- A Change Passport is educational only. It is not merge approval, a security result, a compliance result, a certification, an employee score, or proof that Codex authored a change.

---

## File Structure

### New production files

- src/main/java/dev/codedefense/domain/StagedFileStatus.java — ADDED, MODIFIED, DELETED, RENAMED.
- src/main/java/dev/codedefense/domain/StagedChangeFile.java — safe relative changed-path facts and line counts, without blob content.
- src/main/java/dev/codedefense/domain/StagedChange.java — normalized repository/index identity, changed-file facts, and fingerprints.
- src/main/java/dev/codedefense/domain/PassportStatus.java — CURRENT and EXPIRED.
- src/main/java/dev/codedefense/domain/ChangePassport.java — completed local assessment tied to a staged identity.
- src/main/java/dev/codedefense/domain/PassportVerification.java — explicit verification result.
- src/main/java/dev/codedefense/change/StagedChangeSource.java — port for capturing staged Git content.
- src/main/java/dev/codedefense/change/CapturedStagedChange.java — immutable bounded hunk transfer object from the Git adapter to the context builder; its toString hides contents.
- src/main/java/dev/codedefense/change/StagedHunk.java — parsed old/new ranges plus bounded unified hunk content; its toString hides contents.
- src/main/java/dev/codedefense/domain/SourceLineRange.java — exact retained evidence provenance.
- src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java — bounded tokenized Git implementation.
- src/main/java/dev/codedefense/change/GitChangeException.java — fixed safe Git/index errors.
- src/main/java/dev/codedefense/change/StagedChangeContextBuilder.java — bounded/redacted ProjectSnapshot from staged/HEAD hunks.
- src/main/java/dev/codedefense/change/StagedChangePreviewRenderer.java — staged identity and snapshot preview.
- src/main/java/dev/codedefense/analysis/StagedChangeAnalyzer.java — staged-analysis port.
- src/main/java/dev/codedefense/analysis/AiStagedChangeAnalyzer.java — structured Codex implementation.
- src/main/java/dev/codedefense/analysis/StagedChangePromptFactory.java — strict UTF-8 prompt resource and collision-safe boundary.
- src/main/java/dev/codedefense/analysis/StagedChangeSchemaLoader.java — schema resource loader.
- src/main/java/dev/codedefense/analysis/StagedChangeAnalysisValidator.java — existing evidence validation plus exact category IDs.
- src/main/java/dev/codedefense/application/StagedChangeDefenseRunner.java — command-independent prove workflow.
- src/main/java/dev/codedefense/application/DefaultStagedChangeDefenseRunner.java — preview, confirmation, lazy runtime, interview, recapture, and persistence orchestration.
- src/main/java/dev/codedefense/application/ChangePassportService.java — creation-time identity comparison and save.
- src/main/java/dev/codedefense/application/VerifyLatestChangePassportUseCase.java — explicit current/expired verification.
- src/main/java/dev/codedefense/passport/ChangePassportStore.java — persistence port.
- src/main/java/dev/codedefense/passport/StoredPassportIdentity.java — validated metadata used only by verification.
- src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java — bounded no-follow-link Markdown/pointer store.
- src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java — deterministic source-free Markdown and bounded metadata parser.
- src/main/java/dev/codedefense/passport/ChangePassportPaths.java — ~/.codedefense/change-passports paths.
- src/main/java/dev/codedefense/passport/ChangePassportPersistenceException.java — fixed persistence messages.
- src/main/java/dev/codedefense/cli/ProveCommand.java — prove --staged Picocli adapter.
- src/main/java/dev/codedefense/cli/PassportCommand.java — passport --verify Picocli adapter.
- src/main/resources/prompts/analyze-staged-change.md — untrusted staged-payload instruction template.
- src/main/resources/schemas/staged-change-analysis.schema.json — strict staged question schema.

### Modified production files

- src/main/java/dev/codedefense/application/CodeDefenseRuntime.java — expose StagedChangeAnalyzer.
- src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java — construct it from the same lazy provider, mapper, and configuration.
- src/main/java/dev/codedefense/ai/ProcessResult.java — retain a defensive copy of bounded stdout bytes for strict Git output decoding while keeping toString content-free.
- src/main/java/dev/codedefense/ai/JdkProcessExecutor.java — populate the already bounded stdout-byte capture without introducing an unbounded read.
- src/main/java/dev/codedefense/CodeDefenseApplication.java — register prove and passport.
- src/main/java/dev/codedefense/cli/ExitCodes.java — add GIT_EXECUTION_FAILED with value 10.
- README.md — commands, exact-index privacy semantics, verification, expiration, and non-claims.
- docs/implementation-checklist.md — unchecked 8.5 and optional 8.6 entries; Iteration 9 remains release work.

### New or modified tests

- src/test/java/dev/codedefense/domain/StagedChangeTest.java
- src/test/java/dev/codedefense/domain/ChangePassportTest.java
- src/test/java/dev/codedefense/change/GitCliStagedChangeSourceTest.java
- src/test/java/dev/codedefense/change/StagedChangeContextBuilderTest.java
- src/test/java/dev/codedefense/analysis/AiStagedChangeAnalyzerTest.java
- src/test/java/dev/codedefense/analysis/StagedChangePromptFactoryTest.java
- src/test/java/dev/codedefense/analysis/StagedChangeAnalysisValidatorTest.java
- src/test/java/dev/codedefense/application/StagedChangeDefenseRunnerTest.java
- src/test/java/dev/codedefense/application/ChangePassportServiceTest.java
- src/test/java/dev/codedefense/application/VerifyLatestChangePassportUseCaseTest.java
- src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java
- src/test/java/dev/codedefense/passport/MarkdownChangePassportRendererTest.java
- src/test/java/dev/codedefense/cli/ProveCommandTest.java
- src/test/java/dev/codedefense/cli/PassportCommandTest.java
- src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java
- src/test/java/dev/codedefense/cli/CliFoundationTest.java
- src/test/java/dev/codedefense/ai/JdkProcessExecutorTest.java

## Task 1: Model staged changes and passport identity

**Files:**

- Create: src/main/java/dev/codedefense/domain/StagedFileStatus.java
- Create: src/main/java/dev/codedefense/domain/StagedChangeFile.java
- Create: src/main/java/dev/codedefense/domain/StagedChange.java
- Create: src/main/java/dev/codedefense/domain/PassportStatus.java
- Create: src/main/java/dev/codedefense/domain/ChangePassport.java
- Create: src/main/java/dev/codedefense/domain/PassportVerification.java
- Test: src/test/java/dev/codedefense/domain/StagedChangeTest.java
- Test: src/test/java/dev/codedefense/domain/ChangePassportTest.java

**Interfaces:**

~~~java
public enum StagedFileStatus { ADDED, MODIFIED, DELETED, RENAMED }
public enum PassportStatus { CURRENT, EXPIRED }

public record StagedChange(
        Path repositoryRoot,
        String repositoryIdentityHash,
        String baseCommit,
        String indexIdentity,
        String diffFingerprint,
        List<StagedChangeFile> files,
        int addedLines,
        int deletedLines) { }

public record ChangePassport(
        StagedChange change,
        ProjectAnalysis analysis,
        InterviewSession session,
        Instant createdAt,
        String model,
        PassportStatus statusAtCreation) { }
~~~

- [ ] **Step 1: Write failing domain tests.**

~~~java
assertThrows(IllegalArgumentException.class,
        () -> new StagedChange(root, "abc", "head", "tree", "fingerprint", List.of(), 0, 0));
assertThrows(IllegalArgumentException.class,
        () -> new StagedChangeFile(Path.of("../escape.java"), StagedFileStatus.MODIFIED, 1, 1));
assertFalse(passport.toString().contains("private-answer"));
~~~

Cover normalized absolute roots; lower-case SHA-256 hashes; 40-to-64-character hexadecimal Git IDs; sorted unique safe paths; non-negative line totals; immutable collections; exactly three analysis questions; and no answer, prompt, feedback, expected-key-point, or evidence-reason leak through toString.

- [ ] **Step 2: Run the focused tests.**

Run: mvn -Dtest=StagedChangeTest,ChangePassportTest test

Expected before implementation: compilation failure for the staged/passport types.

- [ ] **Step 3: Implement the records.**

StagedChangeFile contains only a relative path, status, and added/deleted counts. StagedChange retains a normalized root only during the run; persistence uses its SHA-256 repository identity instead of the absolute path. ChangePassport validates that the session belongs to the supplied analysis.

- [ ] **Step 4: Re-run the focused tests.**

Run: mvn -Dtest=StagedChangeTest,ChangePassportTest test

Expected: PASS without Git or Codex.

- [ ] **Step 5: Commit the isolated increment.**

~~~powershell
git add src/main/java/dev/codedefense/domain/StagedFileStatus.java src/main/java/dev/codedefense/domain/StagedChangeFile.java src/main/java/dev/codedefense/domain/StagedChange.java src/main/java/dev/codedefense/domain/PassportStatus.java src/main/java/dev/codedefense/domain/ChangePassport.java src/main/java/dev/codedefense/domain/PassportVerification.java src/test/java/dev/codedefense/domain/StagedChangeTest.java src/test/java/dev/codedefense/domain/ChangePassportTest.java
git commit -m "feat: model staged change passports"
~~~

## Task 2: Capture a read-only index identity and bounded staged hunks

**Files:**

- Create: src/main/java/dev/codedefense/change/StagedChangeSource.java
- Create: src/main/java/dev/codedefense/change/CapturedStagedChange.java
- Create: src/main/java/dev/codedefense/change/StagedHunk.java
- Create: src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java
- Create: src/main/java/dev/codedefense/change/GitChangeException.java
- Test: src/test/java/dev/codedefense/change/GitCliStagedChangeSourceTest.java

**Interfaces:**

~~~java
public interface StagedChangeSource {
    CapturedStagedChange capture(Path requestedPath);
}
~~~

CapturedStagedChange and StagedHunk are public immutable transfer records because the application runner and context builder reside in a different package. They transfer bounded hunk text only to the context builder, copy all collections, and override toString to expose counts/paths/ranges but never contents.

- [ ] **Step 1: Write failing command/parser tests with a hand-written ProcessExecutor fake.**

~~~java
assertEquals(List.of("git", "-C", root.toString(), "rev-parse", "--show-toplevel"),
        executor.commands().getFirst());
assertTrue(captured.change().indexIdentity().matches("[0-9a-f]{64}"));
assertFalse(executor.commands().stream().anyMatch(command -> command.contains("write-tree")));
assertTrue(captured.hunks().stream().anyMatch(hunk -> hunk.unifiedContent().contains("changedLine")));
assertFalse(executor.standardInputs().stream().anyMatch(value -> value.contains("source")));
~~~

The fake supplies bounded results for rev-parse --show-toplevel, rev-parse --verify HEAD, canonical raw and numstat diffs with --find-renames=100%, and per-file `--literal-pathspecs diff --cached --unified=3` calls. Assert tokenized commands, empty stdin, resolved-root working directory, no shell command, no write-tree/cat-file call, and no external diff/text-conversion configuration.

Cover add/modify/delete/exact-rename records; literal pathspec magic names; no repository; no HEAD; no staged difference; index changes during capture; malformed NUL records; unsafe/control-character paths; nonzero Git exit; timeout; truncated output; unsupported/binary/symlink/submodule entries; a 30 hunk-process cap; and a staged hunk deliberately different from a working-tree sentinel.

- [ ] **Step 2: Run the focused test.**

Run: mvn -Dtest=GitCliStagedChangeSourceTest test

Expected before implementation: compilation failure for the Git source boundary.

- [ ] **Step 3: Implement deterministic Git capture.**

Resolve root first, then capture HEAD and canonical NUL-delimited raw staged entries. Reject an unborn repository, empty staged diff, malformed Git data, nonzero exit, timeout, or bounded-output truncation with fixed messages that contain no Git output.

Use this fingerprint:

~~~java
sha256("codedefense-staged-change-v2\0" + baseCommit + "\0" + canonicalRawEntries)
~~~

Derive `indexIdentity` with a separate domain prefix from the same base commit and canonical raw entries. This is read-only and does not materialize a tree object. Retain bounded changed-file metadata for prompt context.

Parse only NUL-delimited raw/numstat records and strict unified hunk headers. Filter through ProjectFileFilter, deterministically prioritize with FilePrioritizer, and capture at most 30 eligible paths. Run every per-file patch with `--literal-pathspecs`, `--no-ext-diff`, `--no-textconv`, and `--find-renames=100%`. For exact renames preserve both paths and pass both literal pathspecs; pure rename-only changes contain no source hunk and are not defensible by themselves. Never resolve a changed path against the working tree. Recapture identity after numstat/hunks and abort safely if HEAD or index changed.

- [ ] **Step 4: Preserve bounded byte correctness.**

Use ProcessResult's defensive stdoutBytes accessor backed by a copied bounded byte array. Keep ProcessResult.toString content-free and continue draining after capture limits. Decode bounded Git hunk output with strict UTF-8, trimming only an incomplete trailing sequence when truncated and using deterministic fallback for genuinely malformed bytes. Keep process diagnostics text-only and secret-safe.

- [ ] **Step 5: Re-run the focused test and commit.**

Run: mvn -Dtest=GitCliStagedChangeSourceTest test

Expected: PASS without requiring Git or Codex on the test host.

~~~powershell
git add src/main/java/dev/codedefense/change src/test/java/dev/codedefense/change/GitCliStagedChangeSourceTest.java
# Run this second command only when Step 4 added the bounded-byte accessor.
git add src/main/java/dev/codedefense/ai/ProcessResult.java src/main/java/dev/codedefense/ai/JdkProcessExecutor.java src/test/java/dev/codedefense/ai/JdkProcessExecutorTest.java
git commit -m "feat: capture bounded staged Git changes"
~~~

Stage the generic process files only if the byte accessor proved necessary.

## Task 3: Build the redacted staged context and preview

**Files:**

- Create: src/main/java/dev/codedefense/change/StagedChangeContextBuilder.java
- Create: src/main/java/dev/codedefense/change/StagedChangePreviewRenderer.java
- Test: src/test/java/dev/codedefense/change/StagedChangeContextBuilderTest.java

**Interfaces:**

~~~java
public final class StagedChangeContextBuilder {
    public ProjectSnapshot build(CapturedStagedChange captured);
}

public final class StagedChangePreviewRenderer {
    public void render(StagedChange change, ProjectSnapshot snapshot, PrintWriter out);
}
~~~

- [ ] **Step 1: Write failing context tests.**

~~~java
ProjectSnapshot snapshot = builder.build(capturedWithDifferentIndexAndWorktree());
assertTrue(snapshot.promptContent().contains("STAGED_HUNK: src/App.java"));
assertFalse(snapshot.promptContent().contains("unstaged-working-tree-secret"));
assertTrue(snapshot.promptContent().contains("[REDACTED]"));
assertTrue(snapshot.promptBytes() <= config.maximumSnapshotBytes());
~~~

Cover deterministic order; 30 selected files; total/per-file byte limits including final separators; line numbers; redaction count; valid truncated Unicode; additions/modifications/deletions; base context for an already changed path; and failure when no current staged text is eligible.

- [ ] **Step 2: Run the focused test.**

Run: mvn -Dtest=StagedChangeContextBuilderTest test

Expected before implementation: compilation failure for the context builder.

- [ ] **Step 3: Implement the builder using existing primitives.**

Reuse ProjectFileFilter, FilePrioritizer, SecretRedactor, LineNumberFormatter, SnapshotBudget, and CodeDefenseConfig. Build a ProjectSnapshot named from the repository root and typed Staged Git change.

The deterministic payload contains identity facts, changed statuses, bounded metadata, STAGED_HUNK blocks, and HEAD_HUNK blocks for deletions. Preserve exact old/new retained ranges in SelectedFile metadata so evidence validation accepts only visible lines, including deleted HEAD evidence. Pure exact renames appear as old-path/new-path metadata but do not send unchanged whole-file source. Add redaction counts only for included blocks and assert the final UTF-8 prompt size is within the configured limit.

- [ ] **Step 4: Implement preview rendering.**

Print repository name, Mode: Staged change, shortened base commit, index identity, fingerprint, changed/added/deleted counts, Unstaged working-tree content ignored: yes, selected-file and byte limits, truncation/redaction counts, and selected paths. Do not print raw diff, hunks, Git output, or absolute paths.

- [ ] **Step 5: Re-run focused tests and commit.**

Run: mvn -Dtest=StagedChangeContextBuilderTest test

Expected: PASS without Codex.

~~~powershell
git add src/main/java/dev/codedefense/change/StagedChangeContextBuilder.java src/main/java/dev/codedefense/change/StagedChangePreviewRenderer.java src/test/java/dev/codedefense/change/StagedChangeContextBuilderTest.java
git commit -m "feat: build bounded staged change context"
~~~

## Task 4: Generate and validate three typed staged questions

**Files:**

- Create: src/main/java/dev/codedefense/analysis/StagedChangeAnalyzer.java
- Create: src/main/java/dev/codedefense/analysis/AiStagedChangeAnalyzer.java
- Create: src/main/java/dev/codedefense/analysis/StagedChangePromptFactory.java
- Create: src/main/java/dev/codedefense/analysis/StagedChangeSchemaLoader.java
- Create: src/main/java/dev/codedefense/analysis/StagedChangeAnalysisValidator.java
- Create: src/main/resources/prompts/analyze-staged-change.md
- Create: src/main/resources/schemas/staged-change-analysis.schema.json
- Modify: src/main/java/dev/codedefense/application/CodeDefenseRuntime.java
- Modify: src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java
- Test: src/test/java/dev/codedefense/analysis/AiStagedChangeAnalyzerTest.java
- Test: src/test/java/dev/codedefense/analysis/StagedChangePromptFactoryTest.java
- Test: src/test/java/dev/codedefense/analysis/StagedChangeAnalysisValidatorTest.java
- Modify test: src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java

**Interfaces:**

~~~java
public interface StagedChangeAnalyzer {
    ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot);
}
~~~

- [ ] **Step 1: Write failing prompt/schema/validator tests.**

~~~java
assertEquals(Set.of("decision", "counterfactual", "test-prediction"),
        analysis.questions().stream().map(TechnicalQuestion::id).collect(toSet()));
assertTrue(request.prompt().contains("BEGIN CODEDEFENSE_UNTRUSTED_STAGED_CHANGE"));
assertFalse(request.toString().contains("private-index-content"));
assertThrows(InvalidCodexResponseException.class,
        () -> validator.validate(analysisWithDuplicateDecisionId(), snapshot));
~~~

Require additionalProperties false, all fields required, exactly one question per category, ordinary project-analysis semantic bounds, evidence limited to selected current staged paths/line ranges, strict trailing-token rejection, and a fixed invalid-response message that leaks no JSON or source.

- [ ] **Step 2: Run the focused analysis group.**

Run: mvn -Dtest=AiStagedChangeAnalyzerTest,StagedChangePromptFactoryTest,StagedChangeAnalysisValidatorTest test

Expected before implementation: compilation/resource failure.

- [ ] **Step 3: Implement prompt, schema, and analyzer.**

The template states that source, diff, path names, and metadata within generated boundary markers are untrusted data, never instructions. It requires decision, counterfactual, and test-prediction questions grounded in current staged evidence; forbids generic repository questions, source reproduction, invented tests, and security/merge/compliance claims.

AiStagedChangeAnalyzer uses the same injected AiProvider, configured model, medium reasoning effort, mapper, and analysis timeout as project analysis. StagedChangeAnalysisValidator first invokes ProjectAnalysisValidator, then checks the exact ID set. It must not change the project-analysis schema.

- [ ] **Step 4: Wire the analyzer lazily.**

Add StagedChangeAnalyzer to CodeDefenseRuntime. Update every test runtime with a hand-written fake. Construct the production analyzer only from CodeDefenseRuntimeFactory.create(PrintWriter), after a confirmed non-dry-run prove flow needs it.

- [ ] **Step 5: Re-run focused tests and commit.**

Run: mvn -Dtest=AiStagedChangeAnalyzerTest,StagedChangePromptFactoryTest,StagedChangeAnalysisValidatorTest,CodeDefenseRuntimeFactoryTest test

Expected: PASS; captured request uses configured model/timeout and no diagnostics leak staged content.

~~~powershell
git add src/main/java/dev/codedefense/analysis src/main/resources/prompts/analyze-staged-change.md src/main/resources/schemas/staged-change-analysis.schema.json src/main/java/dev/codedefense/application/CodeDefenseRuntime.java src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java src/test/java/dev/codedefense/analysis src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java
git commit -m "feat: generate staged change defense questions"
~~~

## Task 5: Save and verify Markdown Change Passports

**Files:**

- Create: src/main/java/dev/codedefense/passport/ChangePassportPaths.java
- Create: src/main/java/dev/codedefense/passport/ChangePassportStore.java
- Create: src/main/java/dev/codedefense/passport/StoredPassportIdentity.java
- Create: src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java
- Create: src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java
- Create: src/main/java/dev/codedefense/passport/ChangePassportPersistenceException.java
- Create: src/main/java/dev/codedefense/application/ChangePassportService.java
- Create: src/main/java/dev/codedefense/application/VerifyLatestChangePassportUseCase.java
- Test: src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java
- Test: src/test/java/dev/codedefense/passport/MarkdownChangePassportRendererTest.java
- Test: src/test/java/dev/codedefense/application/ChangePassportServiceTest.java
- Test: src/test/java/dev/codedefense/application/VerifyLatestChangePassportUseCaseTest.java

**Interfaces:**

~~~java
public interface ChangePassportStore {
    Path save(ChangePassport passport);
    Optional<StoredPassportIdentity> readLatestIdentity();
}

public final class ChangePassportService {
    public Path createAndSave(StagedChange beforeInterview,
            ProjectAnalysis analysis, InterviewSession session);
}

public final class VerifyLatestChangePassportUseCase {
    public Optional<PassportVerification> verify(Path repositoryPath);
}
~~~

- [ ] **Step 1: Write failing renderer/store/service/verifier tests.**

~~~java
assertTrue(markdown.contains("# CodeDefense Change Passport"));
assertTrue(markdown.contains("Codex session link: NOT_REQUESTED"));
assertFalse(markdown.contains("private-staged-source"));
assertFalse(markdown.contains("private-answer"));
assertEquals(PassportStatus.EXPIRED, verifier.verify(root).orElseThrow().status());
~~~

Cover identity, Java-owned category/overall scores/readiness, changed paths/statuses, privacy/non-authority wording, and correct CURRENT/pre-save EXPIRED behavior. Assert exclusion of raw diff, blobs, prompt content, expected key points, evidence reasons, raw model JSON, full answers, absolute root, and authorship claims.

Test strict UTF-8, bounded pointers/files, atomic-move fallback, collision naming, malformed metadata, traversal/symlink rejection, and cleanup. Persist a fixed bounded ASCII metadata comment with only version, root hash, base commit, index identity, diff fingerprint, sorted changed-path hashes, and timestamp; parse only this grammar. Legacy `tree=` metadata remains readable but always verifies as EXPIRED.

- [ ] **Step 2: Run the focused passport test group.**

Run: mvn -Dtest=MarkdownChangePassportRendererTest,FileSystemChangePassportStoreTest,ChangePassportServiceTest,VerifyLatestChangePassportUseCaseTest test

Expected before implementation: compilation failure for passport types.

- [ ] **Step 3: Implement creation-time and verification semantics.**

ChangePassportService recaptures the index immediately before save. It stores CURRENT only when repository identity, base, index identity, fingerprint, and changed-path hashes equal the pre-interview capture; otherwise stores EXPIRED. Initial Git capture separately recaptures identity after reading numstat/hunks and aborts if the snapshot was internally raced.

VerifyLatestChangePassportUseCase recaptures and compares the latest stored identity. Any mismatch, including a different repository, returns EXPIRED. It never mutates the original immutable Markdown artifact; passport --verify reports compatibility at verification time.

- [ ] **Step 4: Implement source-free Markdown.**

Render exactly the headings Change identity, Status, Local assessment, Changed files, Decision defense, Counterfactual defense, Test prediction, and Privacy. Use QuestionResult.finalScore and InterviewSession for scores/readiness. Persist only deterministic change metadata, evidence paths/ranges, structured verdicts/scores, and Java-owned category/overall scores/readiness. Keep model-generated questions, follow-up prompts, feedback, concepts, and all user answers outside the persisted artifact so model prose cannot reproduce staged source.

- [ ] **Step 5: Re-run focused tests and commit.**

Run: mvn -Dtest=MarkdownChangePassportRendererTest,FileSystemChangePassportStoreTest,ChangePassportServiceTest,VerifyLatestChangePassportUseCaseTest test

Expected: PASS with no Git or Codex.

~~~powershell
git add src/main/java/dev/codedefense/passport src/main/java/dev/codedefense/application/ChangePassportService.java src/main/java/dev/codedefense/application/VerifyLatestChangePassportUseCase.java src/test/java/dev/codedefense/passport src/test/java/dev/codedefense/application/ChangePassportServiceTest.java src/test/java/dev/codedefense/application/VerifyLatestChangePassportUseCaseTest.java
git commit -m "feat: save and verify change passports"
~~~

## Task 6: Add prove/passport workflows and CLI registration

**Files:**

- Create: src/main/java/dev/codedefense/application/StagedChangeDefenseRunner.java
- Create: src/main/java/dev/codedefense/application/DefaultStagedChangeDefenseRunner.java
- Create: src/main/java/dev/codedefense/cli/ProveCommand.java
- Create: src/main/java/dev/codedefense/cli/PassportCommand.java
- Modify: src/main/java/dev/codedefense/CodeDefenseApplication.java
- Modify: src/main/java/dev/codedefense/cli/ExitCodes.java
- Test: src/test/java/dev/codedefense/application/StagedChangeDefenseRunnerTest.java
- Test: src/test/java/dev/codedefense/cli/ProveCommandTest.java
- Test: src/test/java/dev/codedefense/cli/PassportCommandTest.java
- Modify test: src/test/java/dev/codedefense/cli/CliFoundationTest.java

**Interfaces:**

~~~java
public interface StagedChangeDefenseRunner {
    int run(Path repositoryPath, boolean dryRun, boolean skipConfirmation,
            PrintWriter out, PrintWriter err);
}
~~~

- [ ] **Step 1: Write failing runner and command tests.**

~~~java
assertEquals(ExitCodes.SUCCESS, runner.run(root, true, false, out, err));
assertEquals(0, runtimeProvider.calls());
assertTrue(outText.contains("Mode: Staged change"));
assertTrue(outText.contains("Unstaged working-tree content ignored: yes"));
assertTrue(outText.contains("No source content was sent."));
assertEquals(ExitCodes.SUCCESS, cli.execute("prove", "--staged", "--dry-run", root.toString()));
assertEquals(ExitCodes.SUCCESS, cli.execute("passport", "--verify", root.toString()));
~~~

Cover help/version with no Git/Codex construction; required --staged; default path; dry-run; yes; decline; runtime laziness; call ordering; Git/no-eligible-source/Codex/persistence/cancellation mapping; and Picocli writers only.

- [ ] **Step 2: Run focused workflow/CLI tests.**

Run: mvn -Dtest=StagedChangeDefenseRunnerTest,ProveCommandTest,PassportCommandTest,CliFoundationTest test

Expected before implementation: compilation failure for the commands.

- [ ] **Step 3: Implement the runner.**

The sequence is fixed:

~~~text
capture index -> build staged snapshot -> render preview
-> dry-run return OR confirmation -> lazy runtime -> staged analysis
-> existing interview -> recapture index -> save Markdown passport
~~~

Use prompt Send bounded staged change context to Codex? [y/N]. For dry-run, decline, Git failure, or no eligible context, construct neither runtime nor JLine nor the passport store. Reuse the existing interview scorer/follow-up cap. Do not call UnderstandingReportService, so prove makes no report-narrative model call and writes no ordinary Understanding Report.

- [ ] **Step 4: Implement Picocli adapters.**

ProveCommand requires --staged, accepts default PATH, --dry-run, and -y/--yes, then delegates exactly once with configured writers. PassportCommand requires --verify PATH; it prints Change Passport: CURRENT or Change Passport: EXPIRED and a fixed expiration explanation. If no passport exists, print a safe informational message and return success. It must not open a browser or mutate a passport.

Register both commands in CodeDefenseApplication; retain start/sample/report construction semantics. Add ExitCodes.GIT_EXECUTION_FAILED = 10. GitChangeException carries one private failure kind: invalid/non-Git repository maps to INVALID_PROJECT_PATH (3), no staged or no eligible staged source maps to NO_SUPPORTED_SOURCE_FILES (4), and missing Git, malformed Git data, timeout, truncation, or nonzero Git execution maps to GIT_EXECUTION_FAILED (10). Every rendered message remains fixed and does not contain Git output.

- [ ] **Step 5: Re-run focused tests and commit.**

Run: mvn -Dtest=StagedChangeDefenseRunnerTest,ProveCommandTest,PassportCommandTest,CliFoundationTest test

Expected: PASS using only fakes; dry-run creates no runtime, JLine, report, or passport.

~~~powershell
git add src/main/java/dev/codedefense/application/StagedChangeDefenseRunner.java src/main/java/dev/codedefense/application/DefaultStagedChangeDefenseRunner.java src/main/java/dev/codedefense/cli/ProveCommand.java src/main/java/dev/codedefense/cli/PassportCommand.java src/main/java/dev/codedefense/CodeDefenseApplication.java src/main/java/dev/codedefense/cli/ExitCodes.java src/test/java/dev/codedefense/application/StagedChangeDefenseRunnerTest.java src/test/java/dev/codedefense/cli/ProveCommandTest.java src/test/java/dev/codedefense/cli/PassportCommandTest.java src/test/java/dev/codedefense/cli/CliFoundationTest.java
git commit -m "feat: add staged change passport workflow"
~~~

## Task 7: Document scope and perform offline verification

**Files:**

- Modify: README.md
- Modify: docs/implementation-checklist.md
- Modify: docs/codedefense-mvp-implementation-plan.md only when its future-extension section must name this approved staged-diff mode.
- Modify: affected existing tests from Tasks 1–6.

- [ ] **Step 1: Update documentation.**

Document:

~~~powershell
git add src/Example.java
java -jar target/codedefense.jar prove --staged .
java -jar target/codedefense.jar passport --verify .
~~~

Explain that CodeDefense reads the staged index, ignores unstaged working-tree content, sends only previewed bounded/redacted staged context after confirmation, and does not send anything during dry-run. Explain expiration, storage location, NOT_REQUESTED session state, and all educational/non-authority boundaries. Add unchecked Iteration 8.5 and optional 8.6 entries while retaining Iteration 9 release work.

- [ ] **Step 2: Run focused offline tests.**

~~~powershell
mvn -Dtest=StagedChangeTest,ChangePassportTest,GitCliStagedChangeSourceTest,StagedChangeContextBuilderTest test
mvn -Dtest=AiStagedChangeAnalyzerTest,StagedChangePromptFactoryTest,StagedChangeAnalysisValidatorTest,CodeDefenseRuntimeFactoryTest test
mvn -Dtest=MarkdownChangePassportRendererTest,FileSystemChangePassportStoreTest,ChangePassportServiceTest,VerifyLatestChangePassportUseCaseTest test
mvn -Dtest=StagedChangeDefenseRunnerTest,ProveCommandTest,PassportCommandTest,CliFoundationTest test
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar --version
java -jar target/codedefense.jar prove --help
java -jar target/codedefense.jar passport --help
~~~

Expected: all Maven commands are green; no default test invokes Codex; help/version commands exit 0; root help lists prove and passport; no command starts an interview.

- [ ] **Step 3: Run only the safe packaged dry-run acceptance check.**

Create a disposable Git fixture outside CodeDefense with an existing initial commit. Stage a supported text-file change, make a different unstaged edit to the same file, then run:

~~~powershell
java -jar target/codedefense.jar prove --staged --dry-run <fixture-path>
~~~

Expected: exit 0; output identifies staged mode and says unstaged content is ignored; output contains neither the unstaged sentinel nor source content; it prints No source content was sent. and Codex was not invoked.; no passport directory is created. Do not run prove --yes, a live-smoke script, or a real Codex interview.

- [ ] **Step 4: Commit only the documentation changes.**

~~~powershell
git add README.md docs/implementation-checklist.md docs/codedefense-mvp-implementation-plan.md
git commit -m "docs: describe staged change passport core"
~~~

Stage only files changed by this task. Do not stage acceptance logs, temporary Git fixtures, live-smoke output, or unrelated untracked research documents.

## Plan self-review

- [x] **Spec coverage:** This plan covers read-only raw-entry identity, literal bounded staged/HEAD hunks, exact rename metadata, atomic initial capture, bounded/redacted context, three typed questions, existing local scoring, Markdown-only persistence, current/expired verification, fixed NOT_REQUESTED session status, CLI commands, race-safe pre-save recapture, tests, and documentation.
- [x] **Scope:** It excludes app-server/session matching, HTML, JSON, Skill, browser, GitHub, PR, CI, signing, cloud storage, dashboards, and Iteration 9 work.
- [x] **Consistency:** Every consumer uses an interface introduced by an earlier task; project-mode analysis and reporting remain unchanged.
