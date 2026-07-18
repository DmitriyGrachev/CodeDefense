# Embedded Sample Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an embedded, safe-to-extract 15-file sample project that can run the same CodeDefense defense workflow as a user-selected directory.

**Architecture:** Extract the existing `start` orchestration into `ProjectDefenseRunner`, so `StartCommand` and `SampleCommand` only translate Picocli input into an explicit application call. `SampleProjectExtractor` is a bounded ZIP-to-temporary-directory boundary with injectable package-private seams; it returns an `AutoCloseable` workspace which `RunSampleUseCase` owns for the complete delegated run. The production runtime is constructed only after a scan, snapshot preview, non-dry-run decision, and confirmation.

**Tech Stack:** Java 21, Maven, Picocli, JUnit 5, JDK `ZipInputStream` and `Files`; no dependencies added.

## Global Constraints

- Remain on `feat/iteration-08-embedded-sample`; Iteration 7 is accepted on `main` and its offline baseline is green.
- Implement Iteration 8 only; do not begin Iteration 9.
- Do not call real Codex in any test or verification, run `sample` without `--dry-run`, run `sample --yes`, or run either live-smoke script.
- The default Maven suite must initialize neither real Codex nor JLine for `sample --dry-run`.
- The embedded resource is exactly `src/main/resources/sample/sample-project.zip`, contains exactly the documented 15 regular text files, and is the sole sample source sent through scanning/snapshot building.
- Never compile, execute, or load code from the archive; use only text files and `ZipInputStream` with UTF-8 entry names.
- Reject archive traversal, absolute paths, duplicate logical paths, symlinks/non-regular entries, overlong entry paths, oversized archives, too many entries, oversized entries, and excessive aggregate expanded bytes without writing outside the temporary root.
- The extracted temporary root name is `codedefense-sample-news-service`; the temporary directory prefix is `codedefense-sample-`; cleanup is recursive, no-follow-link, and deterministic.
- `sample --dry-run` prints `Mode: Embedded sample` and `Preparing built-in sample project...`, runs normal scan/snapshot preview, prints the normal dry-run no-send/no-Codex lines, creates no report, and removes the workspace.
- `sample --yes` bypasses confirmation; `sample` otherwise uses the normal confirmation wording and behavior.
- Preserve current `start` exit-code mappings and wording verbatim; map `SampleProjectException` to exit code `7` and only its fixed safe messages.
- `ProjectDefenseRunner` owns workflow behaviour; commands use configured Picocli `PrintWriter` instances and must have no production construction side effects.
- Keep Iteration 8 unchecked in `docs/implementation-checklist.md`; update README to document the command and mark it implemented while retaining Iteration 9 as future work.

---

## File Structure

### New production files

- `src/main/java/dev/codedefense/application/ProjectDefenseRunner.java` — command-independent workflow contract.
- `src/main/java/dev/codedefense/application/DefaultProjectDefenseRunner.java` — scan, snapshot, preview, confirmation, lazy runtime, analysis/interview/report orchestration and existing exit mappings.
- `src/main/java/dev/codedefense/application/CodeDefenseRuntimeProvider.java` — lazy runtime factory boundary accepting the command output writer.
- `src/main/java/dev/codedefense/sample/SampleProjectConfig.java` — immutable archive and extraction bounds with defaults.
- `src/main/java/dev/codedefense/sample/SampleProjectException.java` — three fixed safe extraction/cleanup messages.
- `src/main/java/dev/codedefense/sample/SampleProjectExtractor.java` — bounded UTF-8 ZIP extraction and `ExtractedSampleProject` cleanup handle.
- `src/main/java/dev/codedefense/application/SampleProjectRunner.java` — small command-facing sample workflow contract for a hand-written CLI fake.
- `src/main/java/dev/codedefense/application/RunSampleUseCase.java` — prints sample status, manages extraction lifetime, invokes the runner exactly once.
- `src/main/resources/sample/sample-project.zip` — deterministic 15-file sample project archive.

### Modified production files

- `src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java` — implement the lazy provider contract and construct JLine only from `create(PrintWriter)`.
- `src/main/java/dev/codedefense/cli/StartCommand.java` — retain Picocli option parsing but delegate to `ProjectDefenseRunner`.
- `src/main/java/dev/codedefense/cli/SampleCommand.java` — Picocli callable with `--dry-run` and `-y`/`--yes`, configured output/error writers, and safe extraction error mapping.
- `src/main/java/dev/codedefense/CodeDefenseApplication.java` — construct one shared production runner and support explicit start/sample/report injection for tests.
- `README.md` — document embedded sample usage, privacy/credit semantics, and current implementation status.

### New tests

- `src/test/java/dev/codedefense/sample/SampleProjectConfigTest.java` — defaults, validation, and immutable configuration behaviour.
- `src/test/java/dev/codedefense/sample/SampleProjectExtractorTest.java` — safe extraction, cleanup, all security bounds, fixed messages, and injected seams.
- `src/test/java/dev/codedefense/sample/SampleArchiveContractTest.java` — package resource, exact logical 15-file set, text-only content, required sample semantics, and no README spoilers.
- `src/test/java/dev/codedefense/application/ProjectDefenseRunnerTest.java` — runner preview/confirmation/lazy runtime/error mapping behaviour with hand-written fakes.
- `src/test/java/dev/codedefense/application/RunSampleUseCaseTest.java` — status text, one delegation, close-on-success/failure, and exception propagation.
- `src/test/java/dev/codedefense/cli/SampleCommandTest.java` — help no-side-effects, dry-run/yes flags, writers, and exit mapping.
- `src/test/java/dev/codedefense/sample/SampleWorkflowEndToEndTest.java` — production ZIP plus real scanner/snapshot/renderer and fake runtime services; dry-run never initializes runtime/Codex/JLine/reports and cleans up.

### Modified tests

- `src/test/java/dev/codedefense/cli/CliFoundationTest.java` — inject the sample command and preserve root registration/help expectations without extraction.
- `src/test/java/dev/codedefense/cli/StartCommandSnapshotTest.java` — exercise the thin command through an injected runner rather than its former internal workflow.
- `src/test/java/dev/codedefense/cli/StartCommandProjectAnalysisTest.java` — preserve analysis error and output contracts through runner fakes.
- `src/test/java/dev/codedefense/cli/StartCommandInterviewTest.java` — preserve interview cancellation/normal paths through runner fakes.
- `src/test/java/dev/codedefense/cli/StartCommandReportTest.java` — preserve report/fallback/persistence mappings through runner fakes.
- `src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java` — prove output-dependent runtime creation remains lazy and shares the expected provider graph.

## Task 1: Establish the shared defense-runner boundary

**Files:**

- Create: `src/main/java/dev/codedefense/application/ProjectDefenseRunner.java`
- Create: `src/main/java/dev/codedefense/application/CodeDefenseRuntimeProvider.java`
- Create: `src/main/java/dev/codedefense/application/DefaultProjectDefenseRunner.java`
- Modify: `src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java`
- Modify: `src/main/java/dev/codedefense/cli/StartCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Test: `src/test/java/dev/codedefense/application/ProjectDefenseRunnerTest.java`
- Modify tests: `src/test/java/dev/codedefense/cli/StartCommandSnapshotTest.java`, `src/test/java/dev/codedefense/cli/StartCommandProjectAnalysisTest.java`, `src/test/java/dev/codedefense/cli/StartCommandInterviewTest.java`, `src/test/java/dev/codedefense/cli/StartCommandReportTest.java`, `src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java`

**Interfaces:**

- Produces `int ProjectDefenseRunner.run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err)`.
- Produces `CodeDefenseRuntime CodeDefenseRuntimeProvider.create(PrintWriter output)`.
- `DefaultProjectDefenseRunner` consumes `ProjectScanner`, `ProjectSnapshotBuilder`, `ConfirmationPrompt`, `ProjectAnalysisRenderer`, and `CodeDefenseRuntimeProvider`.
- `StartCommand.call()` delegates exactly one call using its parsed `path`, `dryRun`, `yes`, and Picocli output/error writers.

- [ ] **Step 1: Write failing runner tests for preview, dry-run, confirmation, normal runtime construction, all existing exception mappings, and writer-only output.**

```java
assertEquals(ExitCodes.SUCCESS, runner.run(root, true, false, out, err));
assertEquals(0, runtimeProvider.calls());
assertTrue(outText.contains("No source content was sent."));
assertTrue(outText.contains("Codex was not invoked."));
```

- [ ] **Step 2: Run the focused runner test to verify it fails because the contract does not exist.**

Run: `mvn -Dtest=ProjectDefenseRunnerTest test`

Expected: compilation failure referencing `ProjectDefenseRunner`.

- [ ] **Step 3: Add the two small contracts and implement `DefaultProjectDefenseRunner`.**

```java
public interface ProjectDefenseRunner {
    int run(Path projectPath, boolean dryRun, boolean skipConfirmation,
            PrintWriter out, PrintWriter err);
}

@FunctionalInterface
public interface CodeDefenseRuntimeProvider {
    CodeDefenseRuntime create(PrintWriter output);
}
```

Move the current `StartCommand.call()` workflow verbatim into `DefaultProjectDefenseRunner`: scan and snapshot first; write exactly the current preview; return before confirmation/runtime creation for dry-run; retain the current prompt and cancellation sentence; only then call `runtimeProvider.create(out)`; retain analysis/interview/report output and all existing catch-to-exit-code mappings.

- [ ] **Step 4: Make `CodeDefenseRuntimeFactory` implement the provider and create `new JLineUserInput()` and `new ConsoleInterviewOutput(output)` only inside `create(PrintWriter)`.**

```java
public final class CodeDefenseRuntimeFactory implements CodeDefenseRuntimeProvider {
    @Override
    public CodeDefenseRuntime create(PrintWriter output) {
        return create(new JLineUserInput(), new ConsoleInterviewOutput(output));
    }
}
```

- [ ] **Step 5: Make `StartCommand` a thin Picocli adapter and add explicit command-line injection.**

```java
@Override
public Integer call() {
    return runner.run(path, dryRun, yes,
            commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
}
```

The no-argument constructor creates the production runner; package-private constructors accept a runner. `CodeDefenseApplication` creates one shared production runner for `start` and the later sample command, and its three-command overload registers supplied commands without construction side effects.

- [ ] **Step 6: Update existing start/runtime tests and run the focused test group.**

Run: `mvn -Dtest=ProjectDefenseRunnerTest,StartCommandSnapshotTest,StartCommandProjectAnalysisTest,StartCommandInterviewTest,StartCommandReportTest,CodeDefenseRuntimeFactoryTest test`

Expected: all pass, no real Codex call.

## Task 2: Add the bounded embedded sample archive and safe extractor

**Files:**

- Create: `src/main/java/dev/codedefense/sample/SampleProjectConfig.java`
- Create: `src/main/java/dev/codedefense/sample/SampleProjectException.java`
- Create: `src/main/java/dev/codedefense/sample/SampleProjectExtractor.java`
- Create: `src/main/resources/sample/sample-project.zip`
- Test: `src/test/java/dev/codedefense/sample/SampleProjectConfigTest.java`
- Test: `src/test/java/dev/codedefense/sample/SampleProjectExtractorTest.java`
- Test: `src/test/java/dev/codedefense/sample/SampleArchiveContractTest.java`

**Interfaces:**

- Produces `SampleProjectConfig.defaults()` with `sample/sample-project.zip`, 512 KiB archive, 32 entries, 128 KiB per entry, 1 MiB aggregate expansion, and 240 path characters.
- Produces `SampleProjectExtractor.extract()` returning `ExtractedSampleProject`, where `projectRoot()` is an extracted real directory and `close()` removes its temporary parent with no-follow-link deletion.
- The extractor consumes injectable package-private archive stream, temporary-directory, and deletion seams so tests do not rely on user home or a real binary resource for hostile ZIP cases.

- [ ] **Step 1: Write failing config/extractor tests for defaults, archive absence, a successful safe extraction, close cleanup, malformed ZIP, traversal, absolute/drive paths, duplicates, count/size/path limits, non-text content, and cleanup failure.**

```java
try (var extracted = extractor.extract()) {
    assertEquals("codedefense-sample-news-service", extracted.projectRoot().getFileName().toString());
    assertTrue(Files.isRegularFile(extracted.projectRoot().resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS));
}
assertFalse(Files.exists(temporaryParent, LinkOption.NOFOLLOW_LINKS));
```

- [ ] **Step 2: Run extractor/config tests to verify they fail before implementation.**

Run: `mvn -Dtest=SampleProjectConfigTest,SampleProjectExtractorTest test`

Expected: compilation failure for sample package types.

- [ ] **Step 3: Implement immutable configuration and fixed-message exception.**

```java
public record SampleProjectConfig(String resourcePath, int maximumArchiveBytes,
        int maximumEntries, int maximumEntryBytes, int maximumExpandedBytes,
        int maximumEntryPathCharacters) { /* non-null/nonblank and positive validation */ }
```

Use no exception message derived from an archive path, ZIP entry, or filesystem error.

- [ ] **Step 4: Implement `SampleProjectExtractor` using `ZipInputStream` and per-entry bounded copy.**

Normalize every logical entry path, reject an absolute path, drive/UNC form, `.`/`..` segment, duplicate, directory entry, empty name, forbidden name/extension, invalid UTF-8/text content, and any value exceeding its configured bound before it reaches disk. Resolve under the extracted root, require `startsWith(root)`, create each parent only below the root, and write each file with `CREATE_NEW`; no overwrite and no link following. Require the final logical set to equal the required 15 paths exactly.

- [ ] **Step 5: Create deterministic archive content.**

Build `sample-project.zip` with JDK ZIP tooling, UTF-8 text, sorted entries, ordinary regular files only, and exactly the documented logical paths. Its `pom.xml` is Spring-Boot-style metadata only; none of its source is compiled or run. Ensure the sample shows scheduler/feed/retry/idempotency/outbox discussion material without README spoilers such as `intentional bug`, `deliberately broken`, `interview issue`, `expected answer`, `outbox missing`, or `duplicate vulnerability`.

- [ ] **Step 6: Add archive contract tests and run the focused sample boundary group.**

Run: `mvn -Dtest=SampleProjectConfigTest,SampleProjectExtractorTest,SampleArchiveContractTest test`

Expected: all pass; tests verify exactly 15 logical regular text files and the required `ArticleScheduler`, retry, persistence, notification, and README facts.

## Task 3: Add the sample use case, CLI adapter, and shared production wiring

**Files:**

- Create: `src/main/java/dev/codedefense/application/RunSampleUseCase.java`
- Create: `src/main/java/dev/codedefense/application/SampleProjectRunner.java`
- Modify: `src/main/java/dev/codedefense/cli/SampleCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Test: `src/test/java/dev/codedefense/application/RunSampleUseCaseTest.java`
- Test: `src/test/java/dev/codedefense/cli/SampleCommandTest.java`
- Modify test: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

**Interfaces:**

- `RunSampleUseCase` implements `SampleProjectRunner.run(boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err)` and returns the delegated runner exit code.
- `SampleCommand.call()` invokes the use case once and maps `SampleProjectException` to `ExitCodes.CODEX_EXECUTION_FAILED` (`7`) using its configured error writer.

- [ ] **Step 1: Write failing use-case and command tests.**

```java
assertEquals(ExitCodes.SUCCESS, useCase.run(true, false, out, err));
assertEquals(1, runner.calls());
assertTrue(outText.startsWith("Mode: Embedded sample\nPreparing built-in sample project..."));
assertEquals(0, confirmationOrRuntimeCalls);
```

Also assert `sample --help` calls neither extractor nor runner, `-y` maps to `skipConfirmation=true`, and extractor errors are printed only to the configured Picocli error writer.

- [ ] **Step 2: Run the focused use-case/command tests to verify they fail.**

Run: `mvn -Dtest=RunSampleUseCaseTest,SampleCommandTest,CliFoundationTest test`

Expected: failure because `RunSampleUseCase` and the completed sample command do not exist.

- [ ] **Step 3: Implement the use case with try-with-resources and exactly one delegation.**

```java
try (SampleProjectExtractor.ExtractedSampleProject extracted = extractor.extract()) {
    return runner.run(extracted.projectRoot(), dryRun, skipConfirmation, out, err);
}
```

Print the two mandated status lines before extraction. Let runner exit codes flow unchanged; let cleanup failure surface as the fixed sample error after the delegated run.

- [ ] **Step 4: Implement `SampleCommand` and application object graph wiring.**

Use `Callable<Integer>`, `mixinStandardHelpOptions = true`, `--dry-run`, and `-y`/`--yes`. Its no-arg construction uses production config/extractor and the same shared production `ProjectDefenseRunner` supplied by `CodeDefenseApplication`; injectable constructors make all CLI tests hand-written-fake-only. Add `createCommandLine(StartCommand, SampleCommand, ReportCommand)` and have all overloads preserve supplied instances.

- [ ] **Step 5: Update CLI foundation tests and run the focused group.**

Run: `mvn -Dtest=ProjectDefenseRunnerTest,RunSampleUseCaseTest,SampleCommandTest,CliFoundationTest test`

Expected: all pass; command help and root help perform no extraction, scan, JLine, report, or Codex construction.

## Task 4: Verify end-to-end dry-run behaviour and documentation

**Files:**

- Test: `src/test/java/dev/codedefense/sample/SampleWorkflowEndToEndTest.java`
- Modify: `README.md`

**Interfaces:**

- The end-to-end test consumes the packaged sample resource, `FileSystemProjectScanner`, `ProjectSnapshotBuilder`, `ProjectAnalysisRenderer`, `DefaultProjectDefenseRunner`, and fakes for the lazy runtime provider/report service.

- [ ] **Step 1: Write a failing end-to-end dry-run test using the production ZIP and an isolated temporary home.**

```java
assertEquals(ExitCodes.SUCCESS, useCase.run(true, false, out, err));
assertTrue(outText.contains("Mode: Embedded sample"));
assertTrue(outText.contains("Selected files:"));
assertTrue(outText.contains("No source content was sent."));
assertTrue(outText.contains("Codex was not invoked."));
assertEquals(0, runtimeProvider.calls());
assertFalse(Files.exists(reportRoot));
assertNoNewDirectoryWithPrefix(tempRoot, "codedefense-sample-");
```

- [ ] **Step 2: Run the end-to-end test to verify it fails if any wiring or resource is missing.**

Run: `mvn -Dtest=SampleWorkflowEndToEndTest test`

Expected: PASS only after the full wired implementation exists; before that it fails because the production resource/command is absent.

- [ ] **Step 3: Complete the end-to-end test and update README.**

Document `sample --dry-run`, `sample`, and `sample --yes`; state that dry-run extracts then removes a temporary built-in project and does not send source, initialize JLine, invoke Codex, or create a report. State that normal sample uses the same preview/confirmation/credit rules as `start`, saves a normal local report only after a completed run, and removes the extracted workspace on every terminal path. Mark Iteration 8 implemented in README but leave the checklist unchecked.

- [ ] **Step 4: Run all required offline verification and package inspection.**

Run:

```powershell
mvn -Dtest=SampleProjectConfigTest,SampleProjectExtractorTest,SampleArchiveContractTest test
mvn -Dtest=ProjectDefenseRunnerTest,RunSampleUseCaseTest,SampleCommandTest test
mvn -Dtest=SampleWorkflowEndToEndTest,CliFoundationTest test
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar --version
java -jar target/codedefense.jar sample --help
$IsolatedHome = Join-Path $env:TEMP "codedefense-sample-dry-run-home"
New-Item -ItemType Directory -Force -Path $IsolatedHome | Out-Null
java "-Duser.home=$IsolatedHome" -jar target/codedefense.jar sample --dry-run
jar tf target/codedefense.jar | Select-String "^sample/sample-project.zip$"
```

Expected: each Maven command is green; full suite has no real Codex call; help/version/sample-help exit `0`; dry-run exit `0`, prints preview and both no-send lines, leaves no report and no newly remaining `codedefense-sample-*` workspace; JAR output contains one exact sample resource path.

## Plan self-review

- [x] **Spec coverage:** Tasks 1–4 cover shared command orchestration, lazy runtime creation, ZIP bounds and containment, exact 15-file archive contract, lifecycle cleanup, CLI flags/help/error mapping, dry-run isolation, README, and all specified offline verification. No Iteration 9 feature is included.
- [x] **Placeholder scan:** no `TODO`, `TBD`, or deferred implementation step remains.
- [x] **Type consistency:** `ProjectDefenseRunner.run` and `CodeDefenseRuntimeProvider.create` signatures are the same in all consumers; `RunSampleUseCase` and `SampleCommand` carry the same two booleans end-to-end.
