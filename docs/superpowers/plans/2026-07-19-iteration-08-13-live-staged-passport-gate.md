# Iteration 8.13 Live Staged Passport Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continuously classify the exact staged Git index as `NO_STAGED_CHANGE`, `UNDEFENDED`, `CURRENT`, `EXPIRED`, or `UNAVAILABLE`, display it in IntelliJ, and perform a fresh advisory check before commit.

**Architecture:** The Maven core inspects Git and Passport receipts and emits strict source-free JSON. The IntelliJ plugin invokes the bundled JAR, coalesces repository events, renders the state, and uses a public `CheckinHandlerFactory` for an advisory final check. No status path initializes Codex or reads source bytes into the plugin.

**Tech Stack:** Java 21, Maven, Picocli, Jackson, JUnit 5, IntelliJ Platform Gradle Plugin 2.18.1, IntelliJ public VCS/Git APIs, hand-written fakes.

## Global Constraints

- Work only on Iteration 8.13; do not implement evidence navigation, learning insights, or commit trailers.
- The gate covers only the exact staged Git index.
- Every automated test is offline and must never call real Codex.
- Do not add third-party dependencies; declaring the bundled `Git4Idea` plugin is allowed.
- `CURRENT` requires repository hash, `STAGED` kind, base commit, source/index identity, and full diff fingerprint equality.
- Status refresh is event-driven with 750 ms debounce and a mandatory fresh pre-commit check.
- `Commit anyway` remains available and is never persisted.
- The plugin must not run Git itself or parse Passport Markdown.
- Preserve Java-owned scoring, three question categories, source limits, preview, confirmation, and Iteration 8.12 behavior.

---

### Task 1: Add a metadata-only staged inspection boundary

**Files:**
- Modify: `src/main/java/dev/codedefense/change/StagedChangeSource.java`
- Modify: `src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java`
- Modify: `src/test/java/dev/codedefense/change/GitCliStagedChangeSourceTest.java`

**Interfaces:**
- Produces: `StagedChange StagedChangeSource.inspect(Path requestedPath)`.
- Preserves: `capture(Path)` and `captureIdentity(Path)` behavior.

- [ ] **Step 1: Write failing inspection tests**

Add focused tests proving `inspect` returns normalized repository identity, all staged file metadata, line counts, and no hunks/blob content; also prove an empty index raises `GitChangeException.Kind.NO_STAGED_CHANGE` and mutation during inspection raises `CHANGED_DURING_CAPTURE`.

```java
StagedChange inspected = source.inspect(repository);
assertEquals(2, inspected.files().size());
assertEquals(3, inspected.addedLines());
assertEquals(1, inspected.deletedLines());
assertEquals(expectedFingerprint, inspected.diffFingerprint());
```

- [ ] **Step 2: Run the focused test and observe RED**

Run:

```powershell
mvn -Dtest=GitCliStagedChangeSourceTest test
```

Expected: compilation fails because `inspect(Path)` does not exist.

- [ ] **Step 3: Add the boundary and refactor one capture path**

Use this exact public contract:

```java
public interface StagedChangeSource {
    CapturedStagedChange capture(Path requestedPath);
    StagedChangeIdentity captureIdentity(Path requestedPath);
    StagedChange inspect(Path requestedPath);
}
```

Inside `GitCliStagedChangeSource`, extract a private `CapturedMetadata` containing the existing `CapturedIndex` and constructed `StagedChange`. `inspect` performs raw/numstat parsing and the second identity check, but never calls `readHunks`. `capture` reuses the same metadata and then reads bounded hunks.

- [ ] **Step 4: Run the focused test and observe GREEN**

Run the same Maven command. Expected: all `GitCliStagedChangeSourceTest` tests pass with zero real Codex calls.

### Task 2: Model and evaluate the staged gate

**Files:**
- Create: `src/main/java/dev/codedefense/domain/StagedPassportGateState.java`
- Create: `src/main/java/dev/codedefense/domain/StagedPassportGateReason.java`
- Create: `src/main/java/dev/codedefense/domain/StagedPassportGateResult.java`
- Create: `src/main/java/dev/codedefense/application/EvaluateStagedPassportGateUseCase.java`
- Create: `src/test/java/dev/codedefense/domain/StagedPassportGateResultTest.java`
- Create: `src/test/java/dev/codedefense/application/EvaluateStagedPassportGateUseCaseTest.java`
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Modify: `src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java`

**Interfaces:**
- Consumes: `StagedChangeSource.inspect(Path)` and a bounded repository-scoped Passport query.
- Produces: `StagedPassportGateResult EvaluateStagedPassportGateUseCase.evaluate(Path)`.

- [ ] **Step 1: Write domain and use-case tests**

Use exact enums:

```java
public enum StagedPassportGateState {
    NO_STAGED_CHANGE, UNDEFENDED, CURRENT, EXPIRED, UNAVAILABLE
}

public enum StagedPassportGateReason {
    NONE, NO_INDEX_ENTRIES, NO_STAGED_HISTORY, IDENTITY_MATCH,
    IDENTITY_CHANGED, INVALID_REPOSITORY, GIT_CAPTURE_FAILED, PASSPORT_STORE_FAILED
}
```

Use this result shape:

```java
public record StagedPassportGateResult(
        int protocolVersion,
        StagedPassportGateState state,
        StagedPassportGateReason reason,
        String diffFingerprint,
        int attemptNumber,
        int stagedFileCount,
        int addedLines,
        int deletedLines,
        List<String> relativePaths) { }
```

Tests must cover all five states, immutable sorted unique relative paths, full 64-character fingerprint only when available, zero attempt unless current, bounds of 0–30 paths, and a safe `toString()` that exposes counts but no paths/fingerprint. `relativePaths` must be empty for every state except `EXPIRED`.

For `CURRENT`, build a staged receipt with exact repository/base/source/fingerprint identity. Prove that a commit receipt with the same diff fingerprint does not satisfy the gate. Prove a different repository hash is ignored.

- [ ] **Step 2: Run RED tests**

```powershell
mvn -Dtest=StagedPassportGateResultTest,EvaluateStagedPassportGateUseCaseTest test
```

Expected: compilation fails because the gate types do not exist.

- [ ] **Step 3: Implement deterministic evaluation**

`evaluate(Path)` must:

1. call `source.inspect(repository)` once;
2. map `NO_STAGED_CHANGE` to `NO_STAGED_CHANGE/NO_INDEX_ENTRIES`;
3. query the newest at most 50 staged receipts for the matching repository hash without applying a global cross-repository cutoff first;
4. return `UNDEFENDED/NO_STAGED_HISTORY` when that list is empty;
5. find the newest receipt matching base, source/index identity, and fingerprint;
6. return `CURRENT/IDENTITY_MATCH` with its attempt number when found;
7. otherwise return `EXPIRED/IDENTITY_CHANGED`;
8. map invalid repository/Git/store failures to source-free `UNAVAILABLE` reasons.

Do not catch unexpected programming errors.

Add `ChangePassportStore.listByRepository(String repositoryIdentityHash, int limit)` with a production filesystem implementation that filters by validated repository identity while selecting the bounded newest results. A default implementation may preserve adapter compatibility, but correctness tests must prove that more than 50 newer receipts from other repositories cannot hide a matching receipt.

- [ ] **Step 4: Run GREEN tests**

Run the same focused Maven command. Expected: all focused tests pass.

### Task 3: Add the strict CLI gate adapter

**Files:**
- Create: `src/main/java/dev/codedefense/passport/StagedPassportGateJsonCodec.java`
- Create: `src/main/java/dev/codedefense/cli/PassportGateCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Create: `src/test/java/dev/codedefense/passport/StagedPassportGateJsonCodecTest.java`
- Create: `src/test/java/dev/codedefense/cli/PassportGateCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

**Interfaces:**
- Consumes: `EvaluateStagedPassportGateUseCase.evaluate(Path)`.
- Produces: `codedefense passport gate --staged [PATH] --format json`.

- [ ] **Step 1: Write failing codec and command tests**

Assert deterministic newline-terminated JSON with exact fields:

```json
{"protocolVersion":1,"state":"CURRENT","reason":"IDENTITY_MATCH","diffFingerprint":"<64 hex>","attemptNumber":2,"stagedFileCount":3,"addedLines":12,"deletedLines":4,"relativePaths":["README.md","src/A.java","src/B.java"]}
```

Assert `--staged` is required, only `--format json` is accepted in this adapter, help performs no Git access, output is at most 256 KiB, and safe operational states return exit code 0. Invalid CLI usage returns `ExitCodes.INVALID_USAGE` without a stack trace.

- [ ] **Step 2: Run RED tests**

```powershell
mvn -Dtest=StagedPassportGateJsonCodecTest,PassportGateCommandTest,CliFoundationTest test
```

- [ ] **Step 3: Implement codec and Picocli command**

Register `PassportGateCommand` in `PassportCommand.subcommands`. The production constructor wires `GitCliStagedChangeSource`, `JdkProcessExecutor`, `FileSystemChangePassportStore`, and the use case. Write bytes as UTF-8 through Picocli's configured writer. Do not initialize runtime/Codex objects.

- [ ] **Step 4: Run GREEN tests and package smoke**

```powershell
mvn -Dtest=StagedPassportGateJsonCodecTest,PassportGateCommandTest,CliFoundationTest test
mvn package
java -jar target/codedefense.jar passport gate --help
```

Expected: tests and package succeed; help exits 0 without Codex.

### Task 4: Decode and coordinate live gate status in the plugin

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/StagedGateView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/StagedGateService.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/StagedGateCoordinator.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/gate/StagedGateServiceTest.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/gate/StagedGateCoordinatorTest.java`

**Interfaces:**
- Produces: `StagedGateView refresh(Path projectRoot)` and observable coordinator updates.
- Consumes later: Tool Window and pre-commit handler.

- [ ] **Step 1: Write failing strict-decoder tests**

`StagedGateView` mirrors the core fields with immutable paths and derives:

```java
public String shortFingerprint() {
    return diffFingerprint.isEmpty() ? "" : diffFingerprint.substring(0, 12);
}
```

Tests reject duplicate/unknown/missing fields, trailing tokens, invalid UTF-8, invalid enum, invalid hash, oversized output, traversal/absolute paths, nonzero child exit, timeout, and inherited secret environment values. Assert command tokens are exactly:

```text
<java> -jar <cli> passport gate --staged <root> --format json
```

- [ ] **Step 2: Write coordinator RED tests**

With a fake scheduler/loader/observer, prove 750 ms debounce, one in-flight load, monotonic generations, stale result suppression, refresh after completion, observer removal, and disposal cleanup.

- [ ] **Step 3: Implement service and coordinator**

Reuse the bounded process rules already enforced by `PassportStatusService`: token list, exact project working directory, 15-second timeout, concurrent full drain, 256-KiB stdout cap, bounded stderr discarded from diagnostics, and graceful/forcible termination. Never include child output in exceptions. Child environment handling must use an explicit minimal allowlist sufficient for the nested CLI to resolve Git cross-platform (`PATH`, `SystemRoot`/`WINDIR`, and `PATHEXT` when present); arbitrary secret variables must not be inherited or logged.

- [ ] **Step 4: Run plugin focused tests**

```powershell
cd jetbrains-plugin
.\gradlew.bat test --tests "dev.codedefense.jetbrains.gate.*" --console=plain
```

Expected: focused gate tests pass.

### Task 5: Render the live badge and subscribe to Git events

**Files:**
- Modify: `jetbrains-plugin/build.gradle.kts`
- Modify: `jetbrains-plugin/src/main/resources/META-INF/plugin.xml`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseProjectGateService.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Modify: focused plugin UI/factory/metadata tests

**Interfaces:**
- Consumes: `StagedGateCoordinator` updates.
- Produces: project-scoped fresh/cached gate access for Task 6.

- [ ] **Step 1: Add failing UI and event tests**

Assert text and accessible names for all five states. Assert project-open, Tool Window visibility, `GitRepository.GIT_REPO_CHANGE`, `.git/index` VFS change, Passport saved, manual refresh, and application/window activation schedule refresh. Assert source-editor changes outside `.git` do not immediately launch a gate process.

- [ ] **Step 2: Declare the bundled Git plugin**

Add:

```kotlin
intellijPlatform {
    bundledPlugin("Git4Idea")
}
```

and:

```xml
<depends>Git4Idea</depends>
```

Only public `git4idea.repo.GitRepository.GIT_REPO_CHANGE` is permitted.

- [ ] **Step 3: Implement project service and badge**

The project service owns the coordinator and supplies `cached()` plus `fresh(Duration timeout)`. The Tool Window registers/unregisters an observer through the project disposable. Render state text plus staged counts; do not rely on color alone. `Defend staged change` sets selector `STAGED` and focuses Preview without starting a session.

- [ ] **Step 4: Run plugin tests**

```powershell
.\gradlew.bat test --tests "dev.codedefense.jetbrains.ui.*" --tests "dev.codedefense.jetbrains.gate.*" --tests "dev.codedefense.jetbrains.PluginMetadataTest" --console=plain
```

### Task 6: Add the advisory pre-commit handler

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CommitModeDetector.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CommitGatePrompt.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseCheckinHandlerFactory.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseCheckinHandler.java`
- Modify: `jetbrains-plugin/src/main/resources/META-INF/plugin.xml`
- Create: focused handler tests

**Interfaces:**
- Consumes: project gate service fresh result.
- Produces: public IntelliJ `CheckinHandler.ReturnResult`.

- [ ] **Step 1: Write failing handler tests with hand-written fakes**

Cover:

- cached `CURRENT` followed by fresh `EXPIRED` still prompts;
- fresh `CURRENT` returns `COMMIT` without a prompt;
- `Defend change` returns `CANCEL` and opens/focuses CodeDefense in staged mode;
- `Commit anyway` returns `COMMIT` once;
- `Cancel` returns `CANCEL`;
- timeout/malformed data becomes `UNAVAILABLE` and prompts;
- unstaged/non-index commit selection is reported as unsupported;
- an index mutation after a cached result but before the fresh callback is observed by the fresh result;
- no path invokes Codex.

Use a port:

```java
interface CommitGatePrompt {
    Decision ask(StagedGateView state);
    enum Decision { DEFEND, COMMIT_ANYWAY, CANCEL }
}
```

- [ ] **Step 2: Implement only public IntelliJ APIs**

Extend `CheckinHandlerFactory`, inspect `CheckinProjectPanel` and Git staging records through public APIs, run the fresh check under bounded progress, and map decisions to `CheckinHandler.ReturnResult`. Register:

```xml
<checkinHandlerFactory implementation="dev.codedefense.jetbrains.gate.CodeDefenseCheckinHandlerFactory"/>
```

- [ ] **Step 3: Run handler and verifier tests**

```powershell
.\gradlew.bat test --tests "dev.codedefense.jetbrains.gate.*" --console=plain
.\gradlew.bat buildPlugin --console=plain
```

Expected: all tests pass and plugin ZIP is created.

### Task 7: Document, verify, and commit Iteration 8.13

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`
- Modify: `docs/superpowers/plans/2026-07-19-iteration-08-13-live-staged-passport-gate.md` checkboxes only after evidence exists

- [ ] **Step 1: Run complete offline verification**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar passport gate --help

cd jetbrains-plugin
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

Run Plugin Verifier against exact IDEA 261 and installed 262 builds. Expected: compatible, zero internal API usages.

- [ ] **Step 2: Run disposable Git acceptance**

Create a temporary Git repository and source-free receipt fixture. Prove transitions:

```text
NO_STAGED_CHANGE -> UNDEFENDED -> CURRENT -> EXPIRED
```

Assert every command exits 0, output is valid JSON, and no Codex process/workspace appears.

- [ ] **Step 3: Update docs and commit**

Mark only Iteration 8.13 complete after the offline gates and installed-plugin acceptance pass.

```powershell
git add README.md docs/implementation-checklist.md docs/superpowers/plans/2026-07-19-iteration-08-13-live-staged-passport-gate.md src jetbrains-plugin
git commit -m "feat: add live staged Passport gate"
```

Do not begin Iteration 8.14 until this commit is green.
