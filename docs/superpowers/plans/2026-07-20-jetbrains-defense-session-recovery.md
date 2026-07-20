# JetBrains Defense Session Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate latest-Passport status from the defense transcript and provide an explicit, privacy-safe full-defense retry after an invalid model response.

**Architecture:** Keep all retry classification in `CodeDefenseToolWindowController` and all presentation state in `SwingCodeDefenseToolWindowView`. Reuse the existing Start action and bridge launch path; no bridge/core changes and no automatic model call are introduced.

**Tech Stack:** Java 21, Gradle 9 IntelliJ plugin build, Swing, IntelliJ Platform 2026.2 APIs, JUnit 5, hand-written fakes.

## Global Constraints

- Work only in the JetBrains adapter files named below.
- Do not change Git capture, Passport identity, analysis prompts, scoring, persistence, Codex execution, or the bridge protocol.
- `Retry defense` always starts a fresh normal workflow and never retries automatically.
- Preserve the safe error text `Codex returned an invalid response.`
- Never retain or log answers, raw JSON, prompts, source content, or evidence reasons.
- Automated tests must never invoke Codex.
- Preserve the user's unrelated untracked files.

---

### Task 1: Replace appended Passport history with one dedicated status area

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Test: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowViewTest.java`

**Interfaces:**
- Consumes: `CodeDefenseToolWindowView.showPassportStatus(String value)`.
- Produces: one named read-only component `codeDefense.latestPassportStatus` whose text is replaced on refresh.

- [ ] **Step 1: Add failing replacement and separation tests**

Add focused tests that call only the public view methods:

```java
@Test
void latestPassportRefreshReplacesStatusWithoutAppendingToSessionOutput() {
    SwingCodeDefenseToolWindowView view = new SwingCodeDefenseToolWindowView();

    view.showPassportStatus("CURRENT | COMMIT | first | balanced | attempt 1");
    view.showPassportStatus("CURRENT | COMMIT | second | balanced | attempt 2");

    JTextArea status = named(view.component(), JTextArea.class,
            "codeDefense.latestPassportStatus");
    JTextArea session = named(view.component(), JTextArea.class,
            "codeDefense.sessionOutput");
    assertEquals("CURRENT | COMMIT | second | balanced | attempt 2", status.getText());
    assertEquals("", session.getText());
}

@Test
void stagedGateAndLatestPassportRemainSeparate() {
    SwingCodeDefenseToolWindowView view = new SwingCodeDefenseToolWindowView();
    view.showGateStatus(new StagedGateView(1, StagedGateView.State.UNDEFENDED,
            StagedGateView.Reason.NO_STAGED_HISTORY, "a".repeat(64),
            0, 1, 4, 0, List.of()));
    view.showPassportStatus("CURRENT | COMMIT | 6482a10a90f1 | balanced | attempt 2");

    assertEquals("UNDEFENDED", named(view.component(), JLabel.class,
            "codeDefense.gateBadge").getText());
    assertTrue(named(view.component(), JTextArea.class,
            "codeDefense.latestPassportStatus").getText().contains("CURRENT | COMMIT"));
}
```

- [ ] **Step 2: Run the focused view test and verify RED**

Run:

```powershell
cd jetbrains-plugin
.\gradlew.bat test --tests "dev.codedefense.jetbrains.ui.SwingCodeDefenseToolWindowViewTest" --console=plain
```

Expected: test failure because `codeDefense.latestPassportStatus` does not
exist and Passport status is appended to `codeDefense.sessionOutput`.

- [ ] **Step 3: Add the dedicated read-only status component**

Add a field and initialize it with no HTML rendering:

```java
private final JTextArea latestPassportStatus = new JTextArea("No Passport");

latestPassportStatus.setName("codeDefense.latestPassportStatus");
latestPassportStatus.setEditable(false);
latestPassportStatus.setLineWrap(true);
latestPassportStatus.setWrapStyleWord(true);
latestPassportStatus.setOpaque(false);
latestPassportStatus.getAccessibleContext().setAccessibleName("Latest saved Passport");

JPanel latestPassport = new JPanel(new BorderLayout(4, 4));
latestPassport.setName("codeDefense.latestPassport");
JLabel latestPassportLabel = new JLabel("Latest saved Passport:");
latestPassportLabel.setLabelFor(latestPassportStatus);
latestPassport.add(latestPassportLabel, BorderLayout.WEST);
latestPassport.add(latestPassportStatus, BorderLayout.CENTER);
north.add(latestPassport);
```

Replace the append behavior:

```java
@Override
public void showPassportStatus(String value) {
    latestPassportStatus.setText(Objects.requireNonNull(value, "value"));
    latestPassportStatus.setCaretPosition(0);
}
```

- [ ] **Step 4: Run the focused view test and verify GREEN**

Run the Step 2 command. Expected: all
`SwingCodeDefenseToolWindowViewTest` tests pass.

- [ ] **Step 5: Commit the independently testable status correction**

```powershell
git add jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java `
        jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowViewTest.java
git commit -m "fix: separate latest Passport from defense output"
```

---

### Task 2: Preserve evidence and expose an explicit full-defense retry

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Test: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowControllerTest.java`
- Test: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowViewTest.java`

**Interfaces:**
- Produces: `CodeDefenseToolWindowView.setRetryAvailable(boolean available)`.
- Consumes: bridge error code `INVALID_MODEL_RESPONSE` and the existing
  `start(Selector, String, String)` workflow.

- [ ] **Step 1: Add failing controller tests for recoverable and ordinary errors**

Extend the hand-written `FakeView` with a `retryAvailable` field and add:

```java
@Test
void invalidModelResponsePreservesEvidenceAndOffersFreshDefenseRetry() {
    FakeView view = new FakeView();
    SequencedLauncher launcher = new SequencedLauncher();
    var controller = lifecycleController(view, launcher, Runnable::run);

    controller.start(Selector.STAGED, null, "balanced");
    launcher.event(0, question(false, "src/Current.java", 4, 5));
    launcher.event(0, "{\"protocolVersion\":2,\"type\":\"error\"," +
            "\"code\":\"INVALID_MODEL_RESPONSE\"," +
            "\"message\":\"Codex returned an invalid response.\",\"exitCode\":9}\n");
    launcher.sessions.getFirst().completion.complete(9);

    assertEquals(List.of(new EvidenceLocationView("src/Current.java", 4, 5)), view.evidence);
    assertTrue(view.retryAvailable);
    assertFalse(view.active);

    controller.start(Selector.STAGED, null, "balanced");

    assertEquals(2, launcher.sessions.size());
    assertFalse(view.retryAvailable);
    assertTrue(view.evidence.isEmpty());
    assertTrue(launcher.sessions.get(1).requests.isEmpty());
}

@Test
void ordinaryTerminalErrorStillClearsEvidenceAndDoesNotOfferRetry() {
    FakeView view = new FakeView();
    SequencedLauncher launcher = new SequencedLauncher();
    var controller = lifecycleController(view, launcher, Runnable::run);

    controller.start(Selector.STAGED, null, "balanced");
    launcher.event(0, question(false, "src/Current.java", 4, 5));
    launcher.event(0, "{\"protocolVersion\":2,\"type\":\"error\"," +
            "\"code\":\"CODEX_EXECUTION_FAILED\",\"message\":\"Safe failure\"," +
            "\"exitCode\":8}\n");

    assertTrue(view.evidence.isEmpty());
    assertFalse(view.retryAvailable);
}
```

Add to `FakeView`:

```java
private boolean retryAvailable;
@Override public void setRetryAvailable(boolean available) {
    retryAvailable = available;
}
```

- [ ] **Step 2: Add failing Swing label tests**

```java
@Test
void retryAvailabilityChangesOnlyTheExistingStartActionLabel() {
    SwingCodeDefenseToolWindowView view = new SwingCodeDefenseToolWindowView();
    JButton start = named(view.component(), JButton.class, "codeDefense.startDefense");

    view.setRetryAvailable(true);
    assertEquals("Retry defense", start.getText());
    assertEquals("Retry defense", start.getAccessibleContext().getAccessibleName());

    view.setRetryAvailable(false);
    assertEquals("Start defense", start.getText());
    assertEquals("Start defense", start.getAccessibleContext().getAccessibleName());
}
```

Ensure the constructor names the existing button:

```java
start.setName("codeDefense.startDefense");
```

- [ ] **Step 3: Run both focused test classes and verify RED**

```powershell
cd jetbrains-plugin
.\gradlew.bat test `
  --tests "dev.codedefense.jetbrains.ui.CodeDefenseToolWindowControllerTest" `
  --tests "dev.codedefense.jetbrains.ui.SwingCodeDefenseToolWindowViewTest" `
  --console=plain
```

Expected: compilation/test failures because `setRetryAvailable` and recoverable
finish state do not exist.

- [ ] **Step 4: Add the presentation boundary**

Add to `CodeDefenseToolWindowView`:

```java
default void setRetryAvailable(boolean available) { }
```

Implement it in the Swing view:

```java
@Override
public void setRetryAvailable(boolean available) {
    String label = available ? "Retry defense" : "Start defense";
    start.setText(label);
    start.getAccessibleContext().setAccessibleName(label);
}
```

- [ ] **Step 5: Track recoverable completion by session generation**

Add a lock-guarded field:

```java
private boolean preserveEvidenceAfterFinish;
```

At the start of `begin`, reset it and clear retry presentation before launching:

```java
synchronized (lock) {
    if (starting || activeSession != null) {
        throw new IllegalStateException("A CodeDefense session is already active.");
    }
    generation = ++sessionGeneration;
    starting = true;
    preserveEvidenceAfterFinish = false;
}
uiCurrent(generation, () -> {
    view.setRetryAvailable(false);
    view.clearEvidence();
    view.setSessionActive(true);
});
```

Before dispatching an error event to the EDT, classify only the documented
recoverable code:

```java
boolean invalidModelResponse = event.type().equals("error")
        && "INVALID_MODEL_RESPONSE".equals(event.text("code"));
synchronized (lock) {
    if (generation != sessionGeneration || (!starting && activeSession == null)) return;
    if (invalidModelResponse) preserveEvidenceAfterFinish = true;
}
```

Render error state explicitly:

```java
case "error" -> {
    String code = event.text("code");
    view.showError(safe(event.text("message")));
    if ("INVALID_MODEL_RESPONSE".equals(code)) {
        view.setRetryAvailable(true);
    } else if (!"INVALID_ANSWER".equals(code)) {
        view.setRetryAvailable(false);
        view.clearEvidence();
    }
}
```

Capture and reset the flag in `finish`; do not clear evidence for the one
recoverable terminal condition:

```java
boolean preserveEvidence;
synchronized (lock) {
    if (generation != sessionGeneration) return;
    preserveEvidence = preserveEvidenceAfterFinish;
    preserveEvidenceAfterFinish = false;
    activeSession = null;
    starting = false;
    confirmationPending = false;
    clearPendingProvenanceLocked();
}
uiCurrent(generation, () -> {
    if (failure != null) view.showError("CodeDefense bridge execution failed.");
    if (!preserveEvidence) view.clearEvidence();
    view.setRetryAvailable(preserveEvidence);
    view.setSessionActive(false);
});
```

Reset retry/evidence preservation in cancellation, launch failure, successful
`completed`, and unsupported-event paths. Do not alter `INVALID_ANSWER`, which
is a non-terminal input correction.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: both test classes pass with zero Codex calls.

- [ ] **Step 7: Commit the independently testable recovery correction**

```powershell
git add jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java `
        jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java `
        jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java `
        jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowControllerTest.java `
        jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowViewTest.java
git commit -m "fix: preserve failed defense context for explicit retry"
```

---

### Task 3: Complete offline regression and packaging verification

**Files:**
- Verify only; no production file should change unless a focused regression test proves it necessary.

**Interfaces:**
- Consumes: the completed plugin correction.
- Produces: reproducible offline evidence that the Maven core and installable plugin remain green.

- [ ] **Step 1: Run focused plugin tests**

```powershell
cd jetbrains-plugin
.\gradlew.bat test `
  --tests "dev.codedefense.jetbrains.ui.CodeDefenseToolWindowControllerTest" `
  --tests "dev.codedefense.jetbrains.ui.SwingCodeDefenseToolWindowViewTest" `
  --console=plain
```

Expected: zero failures, zero errors, and no real Codex process.

- [ ] **Step 2: Run the complete Maven suite and package the CLI**

```powershell
cd ..
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar start . --dry-run
```

Expected: Maven `BUILD SUCCESS`; help and dry-run exit `0`; dry-run contains
`Codex was not invoked.`

- [ ] **Step 3: Build and test the installable plugin**

```powershell
cd jetbrains-plugin
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

Expected: Gradle `BUILD SUCCESSFUL`; plugin tests use fakes; a ZIP exists at
`jetbrains-plugin/build/distributions/codedefense-jetbrains-0.1.0.zip`.

- [ ] **Step 4: Inspect the final diff and privacy boundary**

```powershell
cd ..
git status --short
git diff --check
git diff --stat HEAD~2..HEAD
rg -n "raw JSON|standardInput|expectedKeyPoints|evidence reasons" `
  jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui
```

Expected: no whitespace errors; only the planned plugin UI/tests and planning
documents changed; no answer/source/model payload was added to logs or state.

## Stop Rule

Stop after the recovery correction is offline-verified. Do not perform a live
Codex run, modify the bridge/core workflow, begin another iteration, push, or
mark an implementation checklist item complete without separate approval.
