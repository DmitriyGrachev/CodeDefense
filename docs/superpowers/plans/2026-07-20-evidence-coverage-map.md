# Evidence Coverage Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, source-free map showing which captured Git hunks are referenced by the three primary defense questions.

**Architecture:** Extend captured hunks with structural changed-line ranges, calculate coverage once after analysis, and expose cumulative snapshots through bridge protocol 3. Store only aggregate counts in portable receipt schema 5; store detailed path/range data in a bounded local sidecar and render it in a separate IntelliJ card.

**Tech Stack:** Java 21, Maven, Jackson, Picocli, IntelliJ Platform Swing, JUnit 5.

## Global Constraints

- No new dependencies and no real Codex calls in tests.
- Coverage never changes scores, readiness, question count, Passport `CURRENT`, or model-call count.
- Detailed coverage contains no source, diff text, prompts, answers, feedback, expected key points, or absolute paths.
- Detailed coverage is capped at 256 hunks and 256 KiB; receipt coverage is aggregate only.
- Tests are limited to the smallest focused set proving changed-line overlap, persistence, bridge transport, and UI grouping.

---

### Task 1: Structural changed-line coverage domain

**Files:**
- Modify: `src/main/java/dev/codedefense/change/StagedHunk.java`
- Modify: `src/main/java/dev/codedefense/change/GitCliStagedChangeSource.java`
- Create: `src/main/java/dev/codedefense/domain/EvidenceCoverageState.java`
- Create: `src/main/java/dev/codedefense/domain/EvidenceCoverageHunk.java`
- Create: `src/main/java/dev/codedefense/domain/EvidenceCoverageSummary.java`
- Create: `src/main/java/dev/codedefense/domain/EvidenceCoverageMap.java`
- Create: `src/main/java/dev/codedefense/change/EvidenceCoverageCalculator.java`
- Test: `src/test/java/dev/codedefense/change/EvidenceCoverageCalculatorTest.java`

**Interfaces:**
- Produces: `EvidenceCoverageCalculator.calculate(CapturedGitChange, ProjectAnalysis)`.
- Produces: `EvidenceCoverageMap.cumulativeThrough(String questionId)` for progressive display.
- Preserves the existing seven-argument `StagedHunk` constructor for current tests and adapters.

- [ ] **Step 1: Write the focused calculator test**

Create one test fixture with an added line, a context line, and a deletion-only hunk. Assert that evidence intersecting the added line is `REFERENCED`, context-only evidence leaves the hunk `UNREFERENCED`, and deletion-only evidence uses the safe anchor.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=EvidenceCoverageCalculatorTest test`

Expected: compilation fails because the coverage types do not exist.

- [ ] **Step 3: Add the minimal immutable model and calculator**

Use these signatures:

```java
public enum EvidenceCoverageState { REFERENCED, UNREFERENCED, UNMEASURABLE }

public record EvidenceCoverageSummary(int totalHunks, int measurableHunks, int referencedHunks) {
    public OptionalInt percentage();
}

public record EvidenceCoverageHunk(String relativePath, int ordinal,
        int startLine, int endLine, boolean navigable,
        EvidenceCoverageState state, List<String> categoryIds) { }

public record EvidenceCoverageMap(String diffFingerprint,
        List<EvidenceCoverageHunk> hunks) {
    public EvidenceCoverageSummary summary();
    public EvidenceCoverageMap cumulativeThrough(String questionId);
}

public final class EvidenceCoverageCalculator {
    public EvidenceCoverageMap calculate(CapturedGitChange change, ProjectAnalysis analysis);
}
```

Track changed new-side ranges while `ParsedHunk.append` sees `+` lines. A truncated hunk becomes `UNMEASURABLE`. For a hunk with no new lines, use `max(1, newStartLine)` as a non-navigable deletion anchor.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `mvn -Dtest=EvidenceCoverageCalculatorTest test`

Expected: all calculator tests pass.

- [ ] **Step 5: Commit the domain slice**

```powershell
git add src/main/java/dev/codedefense/change src/main/java/dev/codedefense/domain/EvidenceCoverage*.java src/test/java/dev/codedefense/change/EvidenceCoverageCalculatorTest.java
git commit -m "feat: calculate deterministic evidence coverage"
```

### Task 2: Portable summary and local detailed sidecar

**Files:**
- Modify: `src/main/java/dev/codedefense/domain/ChangePassport.java`
- Modify: `src/main/java/dev/codedefense/domain/PassportReceipt.java`
- Modify: `src/main/java/dev/codedefense/passport/PassportReceiptJsonCodec.java`
- Modify: `src/main/java/dev/codedefense/passport/ChangePassportStore.java`
- Modify: `src/main/java/dev/codedefense/passport/FileSystemChangePassportStore.java`
- Create: `src/main/java/dev/codedefense/passport/EvidenceCoverageSidecarCodec.java`
- Create: `src/main/java/dev/codedefense/passport/StoredEvidenceCoverage.java`
- Test: `src/test/java/dev/codedefense/passport/EvidenceCoveragePersistenceTest.java`

**Interfaces:**
- `ChangePassport` gains `Optional<EvidenceCoverageMap> evidenceCoverage` with compatibility constructors.
- Receipt schema 5 gains `Optional<EvidenceCoverageSummary> evidenceCoverage`; schema 5 requires it and permits nullable provenance.
- `ChangePassportStore.readLatestCoverage()` defaults to `Optional.empty()`.

- [ ] **Step 1: Write one persistence regression test**

Assert schema 5 round-trips aggregate counts, the `.coverage.json` sidecar contains only safe path/range/category data, schemas 1-4 still decode, and a symlinked sidecar is rejected or ignored without exposing its target.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=EvidenceCoveragePersistenceTest test`

- [ ] **Step 3: Implement schema 5 and bounded sidecar storage**

Use this receipt shape:

```json
"evidenceCoverage":{"totalHunks":12,"measurableHunks":12,"referencedHunks":9},
"codexProvenance":null
```

Write `<passport-base>.coverage.json` atomically after the authoritative Markdown/receipt pair. A sidecar write failure must not roll back an already valid Passport. On read, require matching `receiptId` and full `diffFingerprint`, strict UTF-8, exact fields, `NOFOLLOW_LINKS`, 256 KiB, and 256 hunks.

- [ ] **Step 4: Run the focused persistence test and existing receipt tests**

Run: `mvn -Dtest=EvidenceCoveragePersistenceTest,PassportReceiptJsonCodecTest,FileSystemChangePassportStoreTest test`

- [ ] **Step 5: Commit the persistence slice**

```powershell
git add src/main/java/dev/codedefense/domain src/main/java/dev/codedefense/passport src/test/java/dev/codedefense/passport
git commit -m "feat: persist source-free evidence coverage"
```

### Task 3: Coverage-aware runner and bridge protocol 3

**Files:**
- Modify: `src/main/java/dev/codedefense/application/DefaultGitChangeDefenseRunner.java`
- Modify: `src/main/java/dev/codedefense/application/GitChangePassportService.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeProtocol.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeEvent.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeJsonCodec.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeInterviewOutput.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeEvidenceCoveragePublisher.java`
- Test: `src/test/java/dev/codedefense/bridge/BridgeEvidenceCoverageTest.java`

**Interfaces:**
- Bridge protocol 3 adds capability `evidenceCoverageV1` and event type `coverage`.
- Protocols 1 and 2 retain their exact current event shapes.

- [ ] **Step 1: Write one protocol/flow test**

Assert that protocol 3 encodes/decodes a bounded coverage event and that three primary questions reveal cumulative categories while follow-ups do not change coverage.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=BridgeEvidenceCoverageTest test`

- [ ] **Step 3: Implement protocol 3 and runner wiring**

Add:

```java
record CoverageEvent(int protocolVersion, int totalHunks, int measurableHunks,
        int referencedHunks, List<BridgeCoverageHunk> hunks,
        String disclaimer) implements BridgeEvent { }
```

Calculate the complete map immediately after analysis. Give it to a bridge publisher before `InterviewEngine.conduct`; `BridgeInterviewOutput.renderPrimaryQuestion` publishes the cumulative map for that question ID. Save the same complete map with the Passport.

- [ ] **Step 4: Run bridge and runner tests**

Run: `mvn -Dtest=BridgeEvidenceCoverageTest,BridgeJsonCodecTest,DefaultGitChangeDefenseRunnerTest test`

- [ ] **Step 5: Commit the bridge slice**

```powershell
git add src/main/java/dev/codedefense/application src/main/java/dev/codedefense/bridge src/test/java/dev/codedefense/bridge
git commit -m "feat: stream evidence coverage to IDE adapters"
```

### Task 4: Local coverage command

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportCoverageCommand.java`
- Create: `src/main/java/dev/codedefense/application/ShowEvidenceCoverageUseCase.java`
- Create: `src/main/java/dev/codedefense/passport/EvidenceCoverageTerminalRenderer.java`
- Test: `src/test/java/dev/codedefense/cli/PassportCoverageCommandTest.java`

**Interfaces:**
- Adds `passport coverage [PATH] [--format text|json]`.
- The command constructs no `AiProvider` and never initializes JLine.

- [ ] **Step 1: Write one command test**

Assert CURRENT detail renders paths and counts; stale or missing detail renders only the aggregate/unavailable reason; JSON contains no source-controlled text.

- [ ] **Step 2: Implement the command and renderer**

The use case loads the latest receipt and sidecar, captures only current Git identity, compares full fingerprint, and renders details only on equality.

- [ ] **Step 3: Run the focused command test**

Run: `mvn -Dtest=PassportCoverageCommandTest test`

- [ ] **Step 4: Commit the CLI slice**

```powershell
git add src/main/java/dev/codedefense/cli src/main/java/dev/codedefense/application/ShowEvidenceCoverageUseCase.java src/main/java/dev/codedefense/passport/EvidenceCoverageTerminalRenderer.java src/test/java/dev/codedefense/cli/PassportCoverageCommandTest.java
git commit -m "feat: show local evidence coverage"
```

### Task 5: IntelliJ Evidence Coverage card

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/BridgeLineCodec.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/CodeDefenseLauncher.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/evidence/EvidenceCoverageView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/evidence/EvidenceCoveragePanel.java`
- Test: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/evidence/EvidenceCoveragePanelTest.java`

**Interfaces:**
- `CodeDefenseToolWindowView.showEvidenceCoverage(EvidenceCoverageView, opener)`.
- Separate card is placed between defense session and repository-local history.

- [ ] **Step 1: Write one Swing grouping test**

Assert uncovered files precede referenced files, the heading renders `9 / 12 · 75%`, safe hunks are clickable through the existing navigator, and the exact disclaimer is present.

- [ ] **Step 2: Implement protocol-3 decoding and the card**

Request protocol 3, decode exact bounded fields, render text/symbols rather than color alone, and clear detailed rows when a session/fingerprint expires.

- [ ] **Step 3: Run minimal plugin tests**

Run: `./mvnw` is not used. From `jetbrains-plugin`, run `./gradlew test --tests '*EvidenceCoveragePanelTest' --tests '*BridgeLineCodecTest'` (PowerShell: `.\gradlew.bat test --tests "*EvidenceCoveragePanelTest" --tests "*BridgeLineCodecTest"`).

- [ ] **Step 4: Commit the IntelliJ slice**

```powershell
git add jetbrains-plugin/src
git commit -m "feat: add IDE evidence coverage card"
```

### Task 6: Documentation and verification

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] **Step 1: Document the coverage meaning and privacy boundary**

State explicitly: `Evidence use only — not correctness or safety coverage.` Describe aggregate portable data versus local detailed data.

- [ ] **Step 2: Run offline verification**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar passport coverage --help
java -jar target/codedefense.jar prove --staged --dry-run .
Set-Location jetbrains-plugin
.\gradlew.bat test
.\gradlew.bat buildPlugin
```

Expected: all tests/builds pass; dry-run says Codex was not invoked; no real Codex request occurs.

- [ ] **Step 3: Commit documentation**

```powershell
git add README.md docs/implementation-checklist.md
git commit -m "docs: explain evidence coverage map"
```

