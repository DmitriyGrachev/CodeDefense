# Iteration 8.16 Passport-aware Commit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user with a freshly `CURRENT` staged Passport explicitly attach its full source-free diff fingerprint as a Git commit trailer, then finish the responsive Defense Cockpit presentation.

**Architecture:** Trailer parsing and mutation live in a small deterministic plugin class. The existing pre-commit handler uses only the fresh full fingerprint returned by the core gate, exposes an unchecked per-commit option, validates existing trailers, and updates the IntelliJ commit message only after explicit consent.

**Tech Stack:** Java 21, IntelliJ public VCS APIs, Swing, JUnit 5, existing core/plugin build systems, no new dependencies.

## Global Constraints

- Work only on Iteration 8.16 after 8.15 is green.
- Add only `CodeDefense-Passport: sha256:<64 lowercase hex>`.
- Never add scores, readiness, paths, repository/user/thread identity, URLs, or timestamps.
- The checkbox is unchecked by default and is never persisted.
- Add a trailer only after a fresh staged gate result is `CURRENT`.
- Preserve the user's commit message and existing unrelated trailers.
- Do not silently replace a different existing CodeDefense trailer.
- No automated test invokes Codex.
- Do not begin Iteration 9.

---

### Task 1: Implement deterministic Passport trailer handling

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/commit/PassportCommitTrailer.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/commit/PassportTrailerResult.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/commit/PassportCommitTrailerTest.java`

**Interfaces:**
- Produces: `PassportTrailerResult apply(String commitMessage, String diffFingerprint)`.

- [x] **Step 1: Write RED table-driven tests**

Cover:

- plain subject;
- subject/body preserving blank lines;
- existing unrelated Git trailers;
- same CodeDefense trailer is idempotent;
- different CodeDefense trailer returns `CONFLICT` without mutation;
- duplicate CodeDefense trailers return `CONFLICT`;
- malformed fingerprint rejected;
- mixed-case/malformed trailer rejected as conflict rather than overwritten;
- existing CRLF, LF, lone-CR, and mixed line endings remain byte-for-byte unchanged in the original message; appended separators use the first existing line-ending style, or LF when the message has none;
- no score/readiness/path marker appears.

Use:

```java
public record PassportTrailerResult(Status status, String commitMessage) {
    public enum Status { ADDED, ALREADY_PRESENT, CONFLICT }
}
```

- [x] **Step 2: Run RED**

```powershell
cd jetbrains-plugin
.\gradlew.bat test --tests "dev.codedefense.jetbrains.commit.PassportCommitTrailerTest" --console=plain
```

- [x] **Step 3: Implement strict line-oriented mutation**

Validate `[0-9a-f]{64}`. Match only complete lines beginning exactly `CodeDefense-Passport:`. Preserve every original character and ensure one blank line before the appended trailer using the message's first existing line-ending style (LF when none exists). Return a new string; do not mutate UI here.

- [x] **Step 4: Run GREEN**

Run the same focused Gradle command.

### Task 2: Add explicit per-commit consent UI

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/commit/PassportTrailerCommitOption.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseCheckinHandlerFactory.java`
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseCheckinHandler.java`
- Modify: handler tests

**Interfaces:**
- Consumes: fresh `StagedGateView.diffFingerprint()`.
- Produces: an unchecked `RefreshableOnComponent`/public checkin configuration component.

- [x] **Step 1: Write RED consent tests**

Assert:

- checkbox starts unchecked for every handler instance;
- selection is not stored in `CodeDefenseSettings`;
- opening the commit UI starts a bounded preflight refresh; the option is enabled only after that refresh reports `CURRENT`;
- unchecked leaves message byte-for-byte unchanged;
- checked current status appends the exact full fingerprint;
- same trailer is accepted without duplication;
- conflicting trailer cancels and shows a safe action message;
- non-current/unsupported/override paths never append;
- a new commit attempt is unchecked even after the previous one selected it.
- mutation between the first fresh result and the final trailer recheck cancels the commit and appends nothing.

- [x] **Step 2: Implement explicit option and handler integration**

Use `CheckinProjectPanel.getCommitMessage()` and `setCommitMessage(String)` only after the fresh gate check. If the option is selected, perform a second immediate gate check after calculating the candidate message and before setting it; append only when both fresh results are `CURRENT` with the same full identity/fingerprint. Otherwise cancel with a safe stale-index message. This narrows the unavoidable external-process race to the final IntelliJ commit handoff and never claims cryptographic atomicity. Keep the full fingerprint out of notifications and ordinary Tool Window status; display only the existing 12-character form there.

- [x] **Step 3: Run handler tests**

```powershell
.\gradlew.bat test --tests "dev.codedefense.jetbrains.commit.*" --tests "dev.codedefense.jetbrains.gate.*" --console=plain
```

### Task 3: Finish the responsive Cockpit layout

**Files:**
- Modify: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Modify: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowViewTest.java`
- Modify: `jetbrains-plugin/src/main/resources/messages/CodeDefenseBundle.properties` if user-facing strings are centralized

**Interfaces:**
- Consumes: live gate status, session/evidence state, and learning insights.
- Produces: one cohesive accessible Tool Window.

- [x] **Step 1: Add RED layout/accessibility tests**

Assert component hierarchy and accessible names for:

- live gate badge at top;
- staged counts and `Defend staged change` action;
- selector/focus and session actions in responsive rows;
- current question with evidence list;
- session output and one-shot confirmation;
- learning radar below the session area;
- all actions remain reachable at 600-pixel width;
- status is communicated through text, not color alone.

- [x] **Step 2: Implement minimal standard-Swing layout**

Use nested `BoxLayout`/`BorderLayout`, existing IntelliJ theme colors where public, and standard components. Do not add a UI framework or custom painting beyond progress bars. Preserve current output text and keyboard focus order.

- [x] **Step 3: Run full plugin tests/build**

```powershell
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

### Task 4: Document and verify the complete Defense Cockpit

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`
- Modify: all four 8.13–8.16 plan checkboxes only where evidence exists

- [x] **Step 1: Run core verification and safe JAR acceptance**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar passport gate --help
java -jar target/codedefense.jar passport insights --help
```

Use disposable Git/receipt fixtures to verify gate and insights. Do not invoke Codex.

- [ ] **Step 2: Run complete plugin verification**

```powershell
cd jetbrains-plugin
.\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain
```

Run Plugin Verifier for exact IDEA 261 and installed IDEA 262. Expected: compatible, zero internal API usages, plugin ZIP contains the rebuilt CLI.

- [ ] **Step 3: Run installed-plugin offline acceptance**

Against a disposable staged fixture or injected source-free adapters, demonstrate:

```text
UNDEFENDED -> CURRENT -> EXPIRED
fresh pre-commit warning
Commit anyway
evidence click opens safe file
learning radar renders history
explicit trailer is added once
```

Confirm no Codex invocation, no source in JSON/logs, and no remaining bridge/status processes.

- [ ] **Step 4: Update checklist and commit**

Mark Iteration 8.16 complete only after the full gate. Do not mark Iteration 8.12 complete without its separate real-thread acceptance.

```powershell
git add README.md docs/implementation-checklist.md docs/superpowers/plans/2026-07-19-iteration-08-13-live-staged-passport-gate.md docs/superpowers/plans/2026-07-19-iteration-08-14-evidence-navigator.md docs/superpowers/plans/2026-07-19-iteration-08-15-repository-learning-radar.md docs/superpowers/plans/2026-07-19-iteration-08-16-passport-aware-commit.md jetbrains-plugin
git commit -m "feat: attach Passport fingerprints to commits"
```

Stop before Iteration 9.

## Offline verification evidence (2026-07-20)

- `mvn clean verify`: 564 tests, 0 failures, 0 errors, 4 expected skips; build successful.
- `mvn package`: build successful and produced the executable shaded JAR.
- Root, `passport gate`, and `passport insights` help commands exited 0.
- `jetbrains-plugin\gradlew.bat clean test buildPlugin --rerun-tasks --console=plain`: 183 tests,
  0 failures, 0 errors, 4 expected platform skips; plugin package built successfully.
- Deterministic tests cover source-free trailer contents, unchecked per-commit consent, two matching fresh
  staged-gate reads, a final commit-message reread, conflicts, and responsive Cockpit accessibility.
- Disposable gate and insights checks remained source-free and did not invoke Codex.
- Exact IDEA 261/262 Plugin Verifier and installed-plugin acceptance remain pending. Iterations 8.13-8.16
  therefore remain unchecked in the implementation checklist.
