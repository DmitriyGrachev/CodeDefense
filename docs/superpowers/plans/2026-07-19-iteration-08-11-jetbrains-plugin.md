# Iteration 8.11 JetBrains Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make CodeDefense usable inside IntelliJ IDEA through a native Tool Window that previews and runs the existing change-defense workflow, shows Passport status/history, and never duplicates core Git, privacy, analysis, scoring, or persistence behavior.

**Architecture:** Add a versioned bounded NDJSON bridge to the Maven CLI, then build a thin Java JetBrains plugin in `jetbrains-plugin/`. The plugin launches the bundled `codedefense.jar` as a child JVM with an exact token list and the IDE project's base directory. It renders bridge events and sends confirmation/answer/cancel messages. All source capture, Codex access, scoring, and Passport writes occur inside the existing CLI process. The plugin never reads source files, runs Git, calls Codex, or parses Markdown.

**Build exception:** The core remains Java 21 + Maven. JetBrains officially recommends the IntelliJ Platform Gradle Plugin 2.x for plugin development, so the explicitly approved adapter uses an isolated Gradle build. Pin IntelliJ Platform Gradle Plugin `2.18.1`, Gradle wrapper `9.0.0`, Java toolchain `21`, and IntelliJ IDEA `2026.1.4` (`sinceBuild = 261`, `untilBuild = 261.*`). Verify both IntelliJ IDEA Community and Ultimate on Windows. Do not convert the root Maven project or move core classes into Gradle.

**Tech Stack:** Core: Java 21, Maven, Jackson, Picocli, JUnit 5. Plugin: Java 21, Gradle 9 wrapper, IntelliJ Platform Gradle Plugin 2.18.1, IntelliJ Platform test framework. No web UI, database, telemetry, account, GitHub token, or new core dependency.

## User Experience

The `CodeDefense` Tool Window contains:

- a status card: `No Passport`, `CURRENT`, or `EXPIRED`;
- exact change kind, short fingerprint, focus, attempt number, three category scores, overall score, and readiness;
- selector controls for staged, commit, and range modes;
- focus selector;
- `Preview defense`, `Start defense`, `Cancel`, `Refresh`, and `Open Passport` actions;
- one question/answer panel at a time, with explicit `Skip`;
- a completion card with local scores and saved Passport path.

The UI never displays source/diff content, expected key points, schemas, raw JSON, internal prompts, or evidence reasons.

## Global Constraints

- No plugin business logic may calculate identity, select files, score answers, or interpret model output.
- Product support is Windows-first and limited to verified IntelliJ IDEA Community and Ultimate 2026.1 builds. Do not claim support for other JetBrains products or operating systems in Iteration 8.11.
- No CLI command is built as a shell string. Do not use `cmd.exe /c`, `powershell -Command`, or terminal text injection.
- Bridge stdout is NDJSON only; human diagnostics use stderr. Maximum line size is 256 KiB and maximum captured session output is 4 MiB.
- User answers and model-controlled text are never written to IDE logs, notifications, settings, Passport files, or exception messages.
- All process I/O runs off the Event Dispatch Thread (EDT); UI updates return to the EDT.
- Project close, IDE shutdown, or user cancel terminates the child and descendants using bounded graceful/forcible cleanup.
- Automated Maven and Gradle tests never call real Codex.
- The plugin supports one active defense per IntelliJ project and no global employee/user history.

---

### Task 1: Define the local bridge protocol in the core

**Files:**
- Create: `src/main/java/dev/codedefense/bridge/BridgeProtocol.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeRequest.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeEvent.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeJsonCodec.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeProtocolException.java`
- Create: `src/test/java/dev/codedefense/bridge/BridgeJsonCodecTest.java`

Use sealed event/request hierarchies with an explicit `protocolVersion: 1` and `type` discriminator.

```java
public sealed interface BridgeRequest permits
        ConfirmRequest, AnswerRequest, SkipRequest, CancelRequest { }

public sealed interface BridgeEvent permits
        HelloEvent, PreviewEvent, ConfirmationRequiredEvent,
        QuestionEvent, EvaluationEvent, QuestionScoreEvent,
        SummaryEvent, PassportSavedEvent, CompletedEvent, ErrorEvent { }
```

- [ ] Write failing codec tests for every type, deterministic one-line JSON, LF termination, strict UTF-8, unknown type/field rejection, duplicate keys, fractional integers, trailing tokens, 256-KiB line cap, and safe errors.
- [ ] Events expose trusted metadata and sanitized terminal fields only. They must not expose snapshot content, evidence reasons, expected key points, schemas, prompts, or raw model JSON.
- [ ] `HelloEvent` includes a bounded immutable capability list. Iteration 8.11 emits no provenance capability; Iteration 8.12 may advertise `codexProvenanceV1` without changing the base protocol version.
- [ ] `QuestionEvent` and `EvaluationEvent` are ephemeral model-controlled data. Override `toString()` to expose only type/length/count metadata.
- [ ] Requests carrying answers must also have content-free `toString()`.
- [ ] Run `mvn -Dtest=BridgeJsonCodecTest test`; expect green after minimal implementation.

### Task 2: Adapt the existing workflow to bridge input/output ports

**Files:**
- Create: `src/main/java/dev/codedefense/bridge/BridgeInterviewInput.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeInterviewOutput.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeConfirmationPrompt.java`
- Create: `src/main/java/dev/codedefense/bridge/BridgeSession.java`
- Modify: `src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java`
- Create: `src/test/java/dev/codedefense/bridge/BridgeSessionTest.java`

- [ ] Start with hand-written input/output fakes proving request/event order for: dry-run, accepted three-question run, one follow-up, primary skip, follow-up skip, decline, EOF, cancellation, invalid client message, model failure, and successful Passport save.
- [ ] Reuse `InterviewEngine`, `InterviewScorer`, change runners, preview model, and Passport service. Do not fork their algorithms.
- [ ] A bridge answer is one bounded line, maximum 8 KiB UTF-8. Empty answer is allowed only where current terminal behavior allows it. `SkipRequest` maps to the existing exact skip behavior.
- [ ] Dry-run emits preview plus `CompletedEvent(codexInvoked=false)` and reads no confirmation.
- [ ] EOF is a safe decline before disclosure and a safe cancellation during an active interview; never leak a stack trace.
- [ ] Preserve `CodexInterruptedException`/exit semantics and destroy no unrelated process.

### Task 3: Add a hidden bridge CLI with strict stdout ownership

**Files:**
- Create: `src/main/java/dev/codedefense/cli/BridgeCommand.java`
- Create: `src/main/java/dev/codedefense/cli/BridgeProveCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Create: `src/test/java/dev/codedefense/cli/BridgeProveCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

```powershell
java -jar codedefense.jar bridge prove --protocol 1 --staged --focus balanced C:\project
```

- [ ] Require one selector (`--staged`, `--commit`, or `--range`), one closed focus value, and protocol version 1.
- [ ] Help/version initializes neither Git, JLine, nor Codex. The bridge command is documented for adapters but omitted from the root usage synopsis if Picocli permits this without unstable behavior.
- [ ] Stdout contains bridge event lines only. Map every validation/runtime failure to an `ErrorEvent` with stable code plus the documented exit code; stderr may contain one safe diagnostic but no JSON payload.
- [ ] Add an end-to-end test using a fake runtime and byte streams; assert exact event order and that answers never appear in captured logs or exceptions.

### Task 4: Scaffold the isolated JetBrains plugin build

**Files:**
- Create: `jetbrains-plugin/settings.gradle.kts`
- Create: `jetbrains-plugin/build.gradle.kts`
- Create: `jetbrains-plugin/gradle.properties`
- Create: `jetbrains-plugin/gradlew`
- Create: `jetbrains-plugin/gradlew.bat`
- Create: `jetbrains-plugin/gradle/wrapper/gradle-wrapper.properties`
- Create: `jetbrains-plugin/gradle/wrapper/gradle-wrapper.jar`
- Create: `jetbrains-plugin/src/main/resources/META-INF/plugin.xml`
- Create: `jetbrains-plugin/src/main/resources/messages/CodeDefenseBundle.properties`
- Create: `jetbrains-plugin/src/main/resources/icons/codedefense.svg`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/PluginMetadataTest.java`

- [ ] Configure plugin ID `dev.codedefense.jetbrains`, name `CodeDefense`, vendor, version, IDEA dependency, `sinceBuild=261`, and `untilBuild=261.*`. Declare only the IntelliJ Platform APIs actually used; do not add Java PSI or VCS implementation dependencies merely to manufacture product compatibility.
- [ ] Register a project-level `com.intellij.toolWindow` with lazy `ToolWindowFactory` initialization and a settings configurable.
- [ ] Add a `syncCodeDefenseCli` task that requires `../target/codedefense.jar`, copies it to `CodeDefense/cli/codedefense.jar` in the plugin sandbox/distribution, and fails with an actionable message when the Maven artifact is absent.
- [ ] Do not place the shaded CLI JAR on the plugin classpath. Runtime lookup uses the installed plugin path plus `cli/codedefense.jar`.
- [ ] Add build ordering documentation: `mvn clean package`, then `jetbrains-plugin/gradlew test verifyPlugin buildPlugin`.

### Task 5: Implement bounded child-process transport in the plugin

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/CodeDefenseLauncher.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/BridgeProcess.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/BridgeLineCodec.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/process/JavaExecutableResolver.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/process/CodeDefenseLauncherTest.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/process/BridgeLineCodecTest.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/fixture/FakeBridgeMain.java`

```java
public interface CodeDefenseLauncher {
    BridgeProcess launch(Path projectRoot, BridgeLaunchSpec spec);
}
```

- [ ] Build tokens as `[javaExecutable, "-jar", bundledJar, "bridge", "prove", ...]` using `ProcessBuilder(List<String>)`; working directory is the normalized IntelliJ project base path.
- [ ] Resolve `java`/`java.exe` only beneath normalized `System.getProperty("java.home")`; reject missing/non-regular/symlink launchers.
- [ ] Drain stdout and stderr concurrently on virtual threads. Parse stdout incrementally with 256-KiB line and 4-MiB session limits; keep draining after safe UI truncation so the child cannot deadlock.
- [ ] Write requests as UTF-8 NDJSON and flush; close stdin on cancellation/close. Never include request bytes in diagnostics.
- [ ] On cancel or project disposal: send `CancelRequest`, wait a bounded grace period, call `destroy()`, then `destroyForcibly()` and terminate descendants if still alive.
- [ ] Fixture tests cover spaces/Unicode paths, argument token preservation, stdout/stderr concurrency, malformed/oversized lines, nonzero exits, timeout/cancel, and no real Codex.

### Task 6: Build the Tool Window as a passive presentation layer

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowController.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowView.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseViewModel.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/ui/SwingCodeDefenseToolWindowView.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowControllerTest.java`

- [ ] Test controller behavior with a hand-written view and fake launcher before creating Swing components.
- [ ] Cover initial refresh, no Passport, current/expired Passport, selector/focus mapping, dry preview, accepted run, decline, questions, skip, follow-up, evaluation, completion, safe error, cancel, double-start prevention, and project disposal.
- [ ] Use `Task.Backgroundable` or an equivalent platform background task for process I/O. Use IntelliJ EDT scheduling only for view mutation.
- [ ] Apply a plugin-local text sanitizer before rendering model/domain text. Use plain labels/text areas; never render model text as HTML.
- [ ] Disable start while a session is active. Make confirmation explicitly say that bounded source content will be sent to the locally authenticated Codex CLI.
- [ ] Do not store answer text in `CodeDefenseViewModel` after the request is written; clear the answer field immediately.

### Task 7: Add Passport status/history and safe artifact opening

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/status/PassportStatusService.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/status/PassportStatusView.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/status/PassportStatusServiceTest.java`
- Modify: Tool Window controller/view files

- [ ] Status service launches `passport show <project> --format json` and consumes only the Iteration 8.10 protocol. It must not open `.codedefense` directly.
- [ ] Refresh after project open, explicit action, successful defense, and VCS change notification with a 500-ms debounce. Never run Git on the EDT.
- [ ] `Open Passport` accepts only the path returned by a successful core event, requires a regular non-symlink file, and opens it through IntelliJ's local file system API. Do not interpret Markdown.
- [ ] Show category scores and attempt deltas without model prose. No desktop notification may include questions, feedback, or answer text.

### Task 8: Settings, compatibility, and packaging tests

**Files:**
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/settings/CodeDefenseSettings.java`
- Create: `jetbrains-plugin/src/main/java/dev/codedefense/jetbrains/settings/CodeDefenseConfigurable.java`
- Create: `jetbrains-plugin/src/test/java/dev/codedefense/jetbrains/settings/CodeDefenseSettingsTest.java`
- Modify: `jetbrains-plugin/build.gradle.kts`
- Modify: `README.md`

- [ ] Settings allow only: bundled CLI vs explicit CLI JAR override, default selector, and default focus. Do not store Codex credentials, answers, prompts, or source.
- [ ] Validate override as an existing regular non-symlink `.jar`; display the path only inside settings, never logs.
- [ ] Run plugin tests, `verifyPlugin` against IntelliJ IDEA Community and Ultimate 2026.1, and `buildPlugin`; inspect the ZIP for exactly one plugin JAR plus `cli/codedefense.jar`.
- [ ] Install the ZIP into a clean IntelliJ sandbox. Prove lazy startup, project-with-spaces support, offline Passport refresh, fake-bridge interview, cancellation cleanup, and no IDE freeze.
- [ ] A real Codex defense through the plugin requires one separately approved acceptance run after all fake/offline checks pass.

### Task 9: Full offline verification

- [ ] Run core focused bridge/CLI tests.
- [ ] Run `mvn clean verify` and `mvn package`.
- [ ] Run root, `bridge`, `prove`, and `passport` help commands.
- [ ] Run `jetbrains-plugin/gradlew test verifyPlugin buildPlugin`.
- [ ] Confirm all default tests use `FakeBridgeMain`/fake providers and no real Codex process.
- [ ] Confirm plugin logs, settings XML, Passport artifacts, and test reports contain none of the seeded answer/question/source markers.
- [ ] Update `docs/implementation-checklist.md` only after an installed-plugin human acceptance confirms preview, confirmation, three questions, skip, status refresh, and process cleanup.

## Suggested commits

```text
feat: add bounded IDE bridge protocol
feat: add CodeDefense JetBrains tool window
test: verify JetBrains adapter isolation and cleanup
docs: document IntelliJ IDEA workflow
```

## Stop rule

Do not add VS Code support, editor inspections, automatic code changes, merge gates, telemetry, cloud sync, Marketplace publishing credentials, or Codex thread/session access in Iteration 8.11.

## Official JetBrains references

- IntelliJ Platform Gradle Plugin 2.x: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- Developing plugins: https://plugins.jetbrains.com/docs/intellij/developing-plugins.html
- Tool Windows: https://plugins.jetbrains.com/docs/intellij/tool-windows.html
- Plugin configuration and verification: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
