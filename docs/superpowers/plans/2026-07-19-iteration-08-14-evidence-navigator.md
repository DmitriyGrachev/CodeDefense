# Iteration 8.14 Evidence Navigator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Carry validated source-free evidence locations through bridge protocol 2 and let IntelliJ open safe existing files at the cited line.

**Architecture:** The core converts existing validated `CodeEvidence` into bounded bridge DTOs. Protocol 1 remains byte-compatible and omits evidence; protocol 2 adds typed locations. The plugin validates path containment and symlinks before delegating navigation to public IntelliJ editor APIs.

**Tech Stack:** Java 21, Maven, Jackson, JUnit 5, IntelliJ Platform SDK, Swing, hand-written fakes.

## Global Constraints

- Work only on Iteration 8.14 after Iteration 8.13 is green.
- Do not implement Learning Radar or commit trailers.
- Protocol 1 behavior must remain compatible.
- Protocol 2 primary questions contain 1–10 deterministic evidence locations; follow-ups contain no new evidence.
- Bridge messages contain paths and line ranges only, never reasons or source snippets.
- Reject absolute paths, parent traversal, control characters, symlinks, unreadable/missing files, and paths outside the real project root.
- Deleted-file evidence remains visible but is not opened.
- No automated test invokes Codex.

---

### Task 1: Version the bridge and model evidence locations

**Files:**
- Modify: `src/main/java/dev/codedefense/bridge/BridgeProtocol.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeEvidenceLocation.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeEvent.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeSession.java`
- Modify: `src/test/java/dev/codedefense/bridge/BridgeSessionTest.java`

**Interfaces:**
- Produces: protocol constants `VERSION_1`, `VERSION_2`, `CURRENT_VERSION` and `BridgeSession.protocolVersion()`.
- Produces: `QuestionEvent(..., String prompt, List<BridgeEvidenceLocation> evidence)`.

- [x] **Step 1: Write RED contract tests**

Use this record:

```java
public record BridgeEvidenceLocation(String relativePath, int startLine, int endLine) {
    public BridgeEvidenceLocation {
        relativePath = BridgeProtocol.requireRelativePath(relativePath, "relativePath", 4096);
        BridgeProtocol.requireRange(startLine, 1, Integer.MAX_VALUE, "startLine");
        BridgeProtocol.requireRange(endLine, startLine, Integer.MAX_VALUE, "endLine");
    }
}
```

Tests cover portable relative paths, no `..`, no drive/absolute paths, no NUL/control characters, line bounds, safe `toString`, immutable unique sorted evidence, v1/v2 supported versions, and rejection of every other protocol number.

- [x] **Step 2: Run RED tests**

```powershell
mvn -Dtest=BridgeSessionTest,BridgeJsonCodecTest test
```

- [x] **Step 3: Implement version-aware session and DTOs**

Keep the existing two-argument `BridgeSession` constructor defaulting to version 1 for compatibility. Add:

```java
public BridgeSession(InputStream input, OutputStream output, int protocolVersion)
public int protocolVersion()
```

`BridgeProtocol.requireSupportedVersion` accepts exactly 1 or 2. `QuestionEvent` defensively copies and validates at most 10 entries. `BridgeSession`/codec apply the version-aware rule: protocol 1 omits evidence, protocol 2 primary questions require 1–10 entries, and protocol 2 follow-ups require an empty list.

- [x] **Step 4: Run GREEN tests**

Run the same focused Maven command.

### Task 2: Encode/decode protocol 2 without changing protocol 1

**Files:**
- Modify: `src/main/java/dev/codedefense/bridge/BridgeJsonCodec.java`
- Modify: `src/main/java/dev/codedefense/cli/BridgeProveCommand.java`
- Modify: `src/test/java/dev/codedefense/bridge/BridgeJsonCodecTest.java`
- Modify: `src/test/java/dev/codedefense/cli/BridgeProveCommandTest.java`

**Interfaces:**
- Consumes: version-aware `QuestionEvent`.
- Produces: exact protocol-1 shape and protocol-2 `evidence` array.

- [x] **Step 1: Add golden JSON tests**

Protocol 1 remains:

```json
{"protocolVersion":1,"type":"question","number":1,"total":3,"followUp":false,"prompt":"Explain it"}
```

Protocol 2 becomes:

```json
{"protocolVersion":2,"type":"question","number":1,"total":3,"followUp":false,"prompt":"Explain it","evidence":[{"relativePath":"src/A.java","startLine":4,"endLine":9}]}
```

Assert strict field sets for each version, 256-KiB line limit, duplicate and unknown field rejection, and protocol selection through `bridge prove --protocol 1|2`.

- [x] **Step 2: Implement conditional codec fields**

Never serialize an `evidence` property for version 1. Require it for protocol-2 primary questions and encode an empty array for protocol-2 follow-ups. Create `BridgeSession` with the requested supported protocol in `BridgeProveCommand`.

- [x] **Step 3: Run focused tests**

```powershell
mvn -Dtest=BridgeJsonCodecTest,BridgeProveCommandTest test
```

### Task 3: Emit validated evidence from the interview output

**Files:**
- Modify: `src/main/java/dev/codedefense/bridge/BridgeInterviewOutput.java`
- Create: `src/test/java/dev/codedefense/bridge/BridgeInterviewOutputTest.java`

**Interfaces:**
- Consumes: `TechnicalQuestion.evidence()`.
- Produces: protocol-aware primary/follow-up question events.

- [x] **Step 1: Write failing output tests**

For protocol 2, assert primary question evidence is mapped, normalized to `/`, sorted by path/start/end, distinct, and reason-free. Assert a follow-up event has an empty evidence list. For protocol 1, assert the encoder produces the old shape.

- [x] **Step 2: Implement evidence mapping**

Map only:

```java
new BridgeEvidenceLocation(
        evidence.path().replace('\\', '/'),
        evidence.startLine(),
        evidence.endLine())
```

Do not include `reason`, expected key points, learning goal, or analyzed source.

- [x] **Step 3: Run focused tests**

```powershell
mvn -Dtest=BridgeInterviewOutputTest,BridgeJsonCodecTest,BridgeSessionTest test
```

### Task 4: Upgrade the plugin launcher and strict codec

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/CodeDefenseLauncher.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/BridgeLineCodec.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/EvidenceLocationView.java`
- Modify: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/process/CodeDefenseLauncherTest.java`
- Modify: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/process/BridgeLineCodecTest.java`

**Interfaces:**
- Produces: launcher token `--protocol 2`.
- Produces: `BridgeMessage.evidence()` as an immutable typed list.

- [x] **Step 1: Write RED plugin codec tests**

Assert launcher requests protocol 2. Decode valid evidence and reject missing/unknown/duplicate fields, invalid ranges, unsafe paths, more than ten entries, duplicate entries, malformed UTF-8, and oversized lines. Assert decoded DTO/string forms expose only path/range metadata and never raw JSON.

Also prove an override CLI that rejects protocol 2 before emitting any valid bridge event is retried exactly once with protocol 1. Never retry after any bridge event, confirmation, question, or other point where Codex could have been invoked.

- [x] **Step 2: Implement protocol-2 decoder**

Keep decoding protocol-1 fixtures for compatibility tests. Protocol-2 question messages require the exact evidence array. All other event shapes remain unchanged except their protocol number may be 2. The launcher/controller compatibility path may fall back to protocol 1 only for a clean pre-session unsupported-version rejection, and at most once.

- [x] **Step 3: Run focused plugin tests**

```powershell
cd jetbrains-plugin
.\gradlew.bat test --tests "dev.codedefense.jetbrains.process.CodeDefenseLauncherTest" --tests "dev.codedefense.jetbrains.process.BridgeLineCodecTest" --console=plain
```

### Task 5: Add the symlink-safe navigation boundary

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/evidence/EvidenceNavigator.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/evidence/IntelliJEvidenceNavigator.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/evidence/EvidenceNavigatorTest.java`

**Interfaces:**
- Produces: `NavigationResult open(EvidenceLocationView location)`.
- Consumes: project root plus an injected editor opener in tests.

- [x] **Step 1: Write filesystem-boundary tests with `@TempDir`**

Cover valid existing file/line, `../`, absolute path, final symlink, intermediate directory symlink, missing file, directory, unreadable file where supported, a file shortened so the requested start line is beyond the current editor document, deleted file, and replacement by symlink after event receipt. Assert the opener is called only for a safe regular file with a still-valid start line.

- [x] **Step 2: Implement safe resolution**

Use real root/parent validation and `LinkOption.NOFOLLOW_LINKS` for the final file. Resolve the IntelliJ `VirtualFile` only after filesystem validation. Through an injected IntelliJ adapter, obtain the current document line count and reject a stale start line beyond EOF, then use `OpenFileDescriptor(project, virtualFile, startLine - 1, 0).navigate(true)`. Do not read raw file bytes or expose document text.

- [x] **Step 3: Run focused tests**

```powershell
.\gradlew.bat test --tests "dev.codedefense.jetbrains.evidence.*" --console=plain
```

### Task 6: Render clickable evidence beneath each question

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java`
- Modify: controller/view/factory tests

**Interfaces:**
- Consumes: decoded evidence list and `EvidenceNavigator`.
- Produces: accessible clickable items and safe navigation messages.

- [x] **Step 1: Add RED controller and Swing tests**

Assert primary questions replace the previous evidence list, follow-ups retain the primary evidence visually but do not receive new locations, clicking calls the navigator exactly once, deleted/unsafe location is disabled with a message, keyboard activation works, and no source text is displayed.

- [x] **Step 2: Implement the evidence panel**

Render labels like `src/A.java:4–9` as buttons/links below the current question. Clear them when the session completes, fails, or a new primary question arrives. Keep the action row responsive in narrow Tool Windows.

- [x] **Step 3: Run all plugin tests and build**

```powershell
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

### Task 7: Verify and commit Iteration 8.14

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`
- Modify: this plan's checkboxes after verification

**Offline verification evidence (2026-07-20):**

- Core `mvn clean verify` and packaging are green: 539 tests, 0 failures, 0 errors, 4 expected skips.
- Plugin tests are green: 113 tests, 0 failures, 0 errors, 4 expected platform skips.
- `buildPlugin` is green.
- Contract and UI tests verify path/range-only evidence, no source snippets or evidence reasons, strict protocol 2 decoding, and the single pre-session protocol 2 to protocol 1 fallback.
- Exact 261/262 Plugin Verifier and installed-plugin evidence-link acceptance have not been run; the gates below remain pending.

- [ ] **Step 1: Run core and plugin verification**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar bridge prove --help

cd jetbrains-plugin
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

Run Plugin Verifier for exact 261 and 262 IDEs. Search bridge fixtures/output for evidence reasons, expected key points, source markers, and absolute paths; all must be absent.

- [ ] **Step 2: Run offline installed-plugin acceptance**

Use a fake bridge fixture to show one question with two locations. Open an existing safe file, then prove an unsafe/missing location does not open. No model call is authorized.

- [ ] **Step 3: Commit**

```powershell
git add README.md docs/implementation-checklist.md docs/superpowers/plans/2026-07-19-iteration-08-14-evidence-navigator.md src jetbrains-plugin
git commit -m "feat: navigate defense evidence in IntelliJ"
```

Do not begin Iteration 8.15 until this commit is green.
