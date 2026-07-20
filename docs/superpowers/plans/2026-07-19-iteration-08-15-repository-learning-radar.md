# Iteration 8.15 Repository Learning Radar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aggregate the current repository's source-free Passport history into three category averages and a recent overall-score trend, then render it locally in the IntelliJ Cockpit.

**Architecture:** A core use case resolves the repository identity through the staged identity boundary, filters bounded validated receipts, and computes deterministic Java-owned insights. A strict JSON CLI adapter feeds a bounded plugin service; Swing renders labeled bars and recent scores without model text or user identity.

**Tech Stack:** Java 21, Maven, Picocli, Jackson, JUnit 5, IntelliJ SDK, Swing, hand-written fakes.

## Global Constraints

- Work only on Iteration 8.15 after 8.14 is green.
- Do not implement commit trailers.
- Limit input to the newest 20 complete attempts for the current repository.
- Include staged, commit, and range attempts; filter strictly by repository identity hash.
- Use stored Java-owned category and overall scores; never recalculate from model text.
- Include skipped category scores as authoritative zeros.
- Persist no new analytics database or files.
- Output contains no project/root name, timestamps, source, paths, questions, answers, feedback, evidence, model, or user identity.
- No automated test invokes Codex.

---

### Task 1: Model repository-local learning insights

**Files:**
- Create: `src/main/java/dev/codedefense/domain/CategoryLearningInsight.java`
- Create: `src/main/java/dev/codedefense/domain/RepositoryLearningInsights.java`
- Create: `src/test/java/dev/codedefense/domain/RepositoryLearningInsightsTest.java`

**Interfaces:**
- Produces immutable domain values consumed by application/JSON layers.

- [x] **Step 1: Write RED invariant tests**

Use exact records:

```java
public record CategoryLearningInsight(String id, int averageScore) { }

public record RepositoryLearningInsights(
        int schemaVersion,
        int attemptCount,
        int defendedChangeCount,
        List<CategoryLearningInsight> categories,
        String strongestCategory,
        String practiceCategory,
        List<Integer> recentOverallScores) { }
```

Require schema version 1; counts 0–20; `defendedChangeCount <= attemptCount`; category order exactly `decision`, `counterfactual`, `test-prediction`; scores 0–100; recent list 0–10; empty history uses empty strongest/practice strings; nonempty history uses valid category IDs. Lists are defensive copies. `toString()` exposes counts only.

- [x] **Step 2: Run RED**

```powershell
mvn -Dtest=RepositoryLearningInsightsTest test
```

- [x] **Step 3: Implement records and run GREEN**

Implement explicit compact constructors and rerun the focused test.

### Task 2: Build deterministic insights from receipt history

**Files:**
- Create: `src/main/java/dev/codedefense/application/BuildRepositoryLearningInsightsUseCase.java`
- Create: `src/test/java/dev/codedefense/application/BuildRepositoryLearningInsightsUseCaseTest.java`
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Modify: `src/test/java/dev/codedefense/passport/FileSystemChangePassportStoreTest.java`

**Interfaces:**
- Consumes: `StagedChangeSource.captureIdentity(Path)` and `ChangePassportStore.listByRepository(repositoryHash, limit)`.
- Produces: `RepositoryLearningInsights build(Path repository, int limit)`.

- [x] **Step 1: Write RED aggregation tests**

Cover:

- empty matching history;
- repository hash filtering;
- more than 50 newer receipts from other repositories do not hide matching history;
- staged/commit/range inclusion;
- newest-first store ordering converted to chronological recent scores;
- requested limit 1–20 and hard limit 20;
- integer average using `Math.round(sum / (double) count)`;
- skipped zeros included;
- distinct changes counted by full diff fingerprint;
- strongest highest average and practice lowest average;
- stable category order breaks ties;
- recent trend contains at most the newest 10 in chronological order;
- corrupt store and invalid repository map to typed local failure without raw path leakage.

- [x] **Step 2: Run RED**

```powershell
mvn -Dtest=BuildRepositoryLearningInsightsUseCaseTest test
```

- [x] **Step 3: Implement one-pass bounded aggregation**

Resolve repository hash through `captureIdentity` even when `hasStagedChanges()` is false. Query by repository before limiting. Traverse the selected receipts once, maintaining three integer sums, a `HashSet<String>` of full fingerprints, and a bounded chronological score list. Never inspect Markdown.

- [x] **Step 4: Run GREEN**

Run the same focused command.

### Task 3: Add strict `passport insights` JSON output

**Files:**
- Create: `src/main/java/dev/codedefense/passport/RepositoryLearningInsightsJsonCodec.java`
- Create: `src/main/java/dev/codedefense/cli/PassportInsightsCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Create: `src/test/java/dev/codedefense/passport/RepositoryLearningInsightsJsonCodecTest.java`
- Create: `src/test/java/dev/codedefense/cli/PassportInsightsCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

**Interfaces:**
- Produces: `codedefense passport insights [PATH] --format json --limit 20`.

- [x] **Step 1: Write golden JSON and CLI tests**

Assert exact deterministic shape:

```json
{"schemaVersion":1,"attemptCount":3,"defendedChangeCount":2,"categories":[{"id":"decision","averageScore":92},{"id":"counterfactual","averageScore":54},{"id":"test-prediction","averageScore":31}],"strongestCategory":"decision","practiceCategory":"test-prediction","recentOverallScores":[33,61,84]}
```

Assert `--limit` accepts 1–20, `--format` accepts only json, help touches no Git/store, output is newline terminated and below 256 KiB, and forbidden marker strings never appear.

- [x] **Step 2: Run RED**

```powershell
mvn -Dtest=RepositoryLearningInsightsJsonCodecTest,PassportInsightsCommandTest,CliFoundationTest test
```

- [x] **Step 3: Implement codec/command and production wiring**

Wire `GitCliStagedChangeSource`, `JdkProcessExecutor`, the existing file store, and the use case. Use Picocli's writer and safe documented exit codes. Do not construct runtime/Codex objects.

- [x] **Step 4: Run GREEN and package help**

```powershell
mvn -Dtest=RepositoryLearningInsightsJsonCodecTest,PassportInsightsCommandTest,CliFoundationTest test
mvn package
java -jar target/codedefense.jar passport insights --help
```

### Task 4: Add the bounded plugin insights service

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/insights/CategoryInsightView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/insights/RepositoryInsightsView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/insights/RepositoryInsightsService.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/insights/RepositoryInsightsServiceTest.java`

**Interfaces:**
- Produces: `RepositoryInsightsView refresh(Path projectRoot)`.

- [x] **Step 1: Write strict decoder/process tests**

Assert exact command tokens:

```text
<java> -jar <cli> passport insights <root> --format json --limit 20
```

Reject duplicate/unknown/missing fields, trailing JSON, malformed UTF-8, score/count/order violations, oversized stdout, timeout, nonzero exit, and secret environment inheritance. Exception messages contain no child output or root path.

- [x] **Step 2: Implement bounded service**

Use the same process safety as the gate service: explicit minimal Git-capable environment allowlist (`PATH`, `SystemRoot`/`WINDIR`, and `PATHEXT` when present), exact working directory, 15-second timeout, concurrent drain, 256-KiB stdout, discarded bounded stderr, and process-tree cleanup. Do not inherit arbitrary secret variables.

- [x] **Step 3: Run focused plugin tests**

```powershell
cd jetbrains-plugin
.\gradlew.bat test --tests "dev.codedefense.jetbrains.insights.*" --console=plain
```

### Task 5: Render the Learning Radar in the Cockpit

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/insights/LearningRadarPanel.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Create/modify focused UI/controller tests

**Interfaces:**
- Consumes: `RepositoryInsightsView`.
- Produces: accessible labeled bars and recent-score text.

- [x] **Step 1: Write RED rendering/refresh tests**

Assert:

- heading `Repository-local defense history`;
- all three human-readable category labels and scores;
- `Recent overall: 33 -> 61 -> 84`;
- `Practice next: Test prediction`;
- empty state `No completed defenses for this repository yet.`;
- unavailable state without stale scores;
- refresh on Tool Window open, manual Refresh, and `passportSaved` only;
- ordinary VFS/Git status updates do not invoke insights;
- no answer/question/feedback/path markers are rendered.

- [x] **Step 2: Implement panel and refresh coordination**

Use standard Swing `JProgressBar` with text visible for the three categories and a label for recent scores. Do not create custom chart dependencies. Load insights on a background executor, discard stale generations, and marshal UI updates onto EDT.

- [x] **Step 3: Run plugin tests/build**

```powershell
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

### Task 6: Verify and commit Iteration 8.15

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`
- Modify: this plan's checked evidence

- [x] **Step 1: Run full offline verification commands**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar passport insights --help

cd jetbrains-plugin
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

- [ ] **Step 2: Run Plugin Verifier and installed-plugin acceptance**

Run Plugin Verifier for exact 261 and 262, then perform the separate installed-plugin acceptance. Inspect fixture JSON and Tool Window text for source, answer, feedback, evidence, absolute path, user, and model markers; all must be absent.

- [x] **Step 3: Run disposable receipt acceptance**

Create three source-free receipt fixtures with overall scores 33, 61, and 84. Confirm exact category averages, chronological trend, current-repository filtering, exit code 0, and no Codex process.

- [ ] **Step 4: Commit**

```powershell
git add README.md docs/implementation-checklist.md docs/superpowers/plans/2026-07-19-iteration-08-15-repository-learning-radar.md src jetbrains-plugin
git commit -m "feat: add repository learning radar"
```

Do not begin Iteration 8.16 until this commit is green.

## Offline verification evidence (2026-07-20)

- `mvn clean verify`: 564 tests, 0 failures, 0 errors, 4 skips; build successful.
- `mvn package`: build successful.
- `java -jar target/codedefense.jar passport insights --help`: exit code 0.
- `jetbrains-plugin\\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain`: 140 tests,
  0 failures, 0 errors, 4 skips; plugin package built successfully.
- Disposable local receipt acceptance returned bounded source-free insights JSON with exit code 0 and
  no Codex invocation.
- Exact Plugin Verifier and installed-plugin acceptance remain pending; Iteration 8.15 therefore
  remains unchecked in the implementation checklist.
