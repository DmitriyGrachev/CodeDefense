# Iteration 8.17 Codex-Native Plugin and Passport Hook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Package CodeDefense as a repo-local Codex plugin with one instruction-only skill and one advisory `Stop` hook that reports the source-free staged Change Passport state without invoking Codex or starting an interview.

**Architecture:** A hidden `codex-hook status` Picocli adapter calls the existing staged Passport gate through a small application port and renders a separate, 4-KiB Codex hook response. The plugin contributes only declarative metadata, one skill, one trusted lifecycle hook, and thin PowerShell/POSIX launchers; Git capture, Passport identity, persistence, scoring, and all AI behavior remain in the Java core.

**Tech Stack:** Java 21, Maven, Picocli, JUnit 5, Jackson already present in the project, Codex plugin manifests, PowerShell 5.1+, POSIX `sh`.

## Global Constraints

- Remain on `main` unless the user explicitly chooses an implementation branch at execution time.
- Preserve the pre-existing staged `src/main/java/dev/codedefense/PluginAcceptanceFixture.java` and all pre-existing untracked research, plan, and specification files; never include them in an Iteration 8.17 commit.
- Use Java 21 and Maven; add no third-party dependency.
- Reuse `EvaluateStagedPassportGateUseCase`; do not duplicate Git inspection or Passport matching.
- The hook must never construct `CodeDefenseRuntimeFactory`, `AiProvider`, JLine, a prompt, or any Codex process adapter.
- The hook must not read its stdin or expose source, diffs, paths, questions, answers, feedback, evidence, raw gate JSON, model data, environment values, or exception messages.
- Successful stdout is either empty or one strict UTF-8 JSON object followed by LF; every object contains `"continue":true` and is at most 4 KiB.
- `NO_STAGED_CHANGE` and `UNAVAILABLE/INVALID_REPOSITORY` are silent; the hook is advisory and never blocks or retries a Codex turn.
- Bundle exactly one instruction-only skill and exactly one `Stop` hook with a 15-second timeout.
- Support Windows with a fixed PowerShell launcher and macOS/Linux with a fixed POSIX launcher; never use `Invoke-Expression`, `cmd.exe /c`, `powershell.exe -Command`, `eval`, or a shell-built Java command string.
- Never add `--yes` in the skill or hook. Interactive defense remains in the CLI or IntelliJ Tool Window.
- Automated tests and default verification must never call real Codex.
- Do not begin Iteration 9, Evidence Coverage Map, Delta Defense, MCP, Apps SDK, GitHub/CI integration, or any Passport/scoring schema change.
- Keep Iteration 8.17 unchecked until installed-plugin acceptance proves `UNDEFENDED -> CURRENT -> EXPIRED` and the packaged launchers have run on their claimed platform families.

---

## File map

### Java core

- Create `src/main/java/dev/codedefense/application/StagedPassportGateEvaluator.java`: application port shared by public gate and hook adapters.
- Modify `src/main/java/dev/codedefense/application/EvaluateStagedPassportGateUseCase.java`: implement that port without changing behavior.
- Create `src/main/java/dev/codedefense/codexhook/CodexHookStatusRenderer.java`: fixed state-to-message mapping and bounded JSON encoding.
- Create `src/main/java/dev/codedefense/cli/StagedPassportGateRuntimeFactory.java`: one package-private production composition root for staged gate evaluation.
- Modify `src/main/java/dev/codedefense/cli/PassportGateCommand.java`: depend on the shared evaluator port/factory.
- Create `src/main/java/dev/codedefense/cli/CodexHookCommand.java`: hidden parent command.
- Create `src/main/java/dev/codedefense/cli/CodexHookStatusCommand.java`: lazy, source-free hook adapter for the session working directory.
- Modify `src/main/java/dev/codedefense/CodeDefenseApplication.java`: register hidden `codex-hook` without showing it in root help.

### Java tests

- Create `src/test/java/dev/codedefense/codexhook/CodexHookStatusRendererTest.java`: exact output, silence, privacy, bound, and LF tests.
- Create `src/test/java/dev/codedefense/cli/CodexHookStatusCommandTest.java`: cwd, writer, safe failure, and help/version isolation tests with a fake evaluator.
- Modify `src/test/java/dev/codedefense/cli/PassportGateCommandTest.java`: compile against the shared evaluator port and retain the existing public protocol tests.
- Modify `src/test/java/dev/codedefense/cli/CliFoundationTest.java`: assert hidden command registration and absence from public help.
- Create `src/test/java/dev/codedefense/plugin/CodexPluginContractTest.java`: exact manifest, marketplace, hook, and skill safety contracts.
- Create `src/test/java/dev/codedefense/plugin/CodexPluginLauncherTest.java`: execute the platform launcher with a fixture JAR, paths containing spaces, missing Java, and missing JAR.

### Plugin and packaging

- Create `.agents/plugins/marketplace.json`: one repo-local CodeDefense marketplace entry.
- Create `plugins/codedefense/.codex-plugin/plugin.json`: minimal valid plugin manifest with skills and install-surface metadata; rely on default `hooks/hooks.json` discovery.
- Create `plugins/codedefense/skills/codedefense/SKILL.md`: concise safe workflow instructions.
- Create `plugins/codedefense/hooks/hooks.json`: one advisory `Stop` command handler.
- Create `plugins/codedefense/scripts/codedefense-hook.ps1`: Windows launcher.
- Create `plugins/codedefense/scripts/codedefense-hook.sh`: POSIX launcher.
- Create `plugins/codedefense/cli/.gitkeep`: retain the generated-JAR destination.
- Create `scripts/package-codex-plugin.ps1`: assemble the local install and release ZIP on Windows.
- Create `scripts/package-codex-plugin.sh`: assemble the same layout on POSIX.
- Modify `.gitignore`: ignore the copied plugin JAR and generated plugin archive/staging directory.

### Documentation

- Modify `README.md`: plugin purpose, installation, trust, commands, privacy, platform evidence, disable/uninstall, and interactive-defense boundary.
- Modify `docs/implementation-checklist.md`: add unchecked Iteration 8.17 between 8.16 and 9.

---

### Task 1: Add the shared evaluator port and bounded hook renderer

**Files:**
- Create: `src/main/java/dev/codedefense/application/StagedPassportGateEvaluator.java`
- Modify: `src/main/java/dev/codedefense/application/EvaluateStagedPassportGateUseCase.java`
- Create: `src/main/java/dev/codedefense/codexhook/CodexHookStatusRenderer.java`
- Create: `src/test/java/dev/codedefense/codexhook/CodexHookStatusRendererTest.java`

**Interfaces:**
- Consumes: `StagedPassportGateResult` and its existing state/reason invariants.
- Produces: `StagedPassportGateEvaluator.evaluate(Path)` and `CodexHookStatusRenderer.render(StagedPassportGateResult): Optional<byte[]>`.

- [ ] **Step 1: Write renderer tests for every exact state mapping**

Create tests using `FINGERPRINT = "a".repeat(64)` and these exact assertions:

```java
assertArrayEquals(new byte[0], render(noStaged()));
assertArrayEquals(new byte[0], render(unavailable(StagedPassportGateReason.INVALID_REPOSITORY)));

assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: UNDEFENDED — 2 staged files, +18/-4.\\nRun a CodeDefense staged defense before committing.\"}\n",
        text(undefended(2, 18, 4)));
assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: CURRENT — Passport aaaaaaaaaaaa, attempt 3.\"}\n",
        text(current(3)));
assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: EXPIRED — 2 staged files, +18/-4; the staged change no longer matches its Passport.\\nRun a new defense for the current staged change.\"}\n",
        text(expired(List.of("private/marker.java"))));
assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: UNAVAILABLE — staged Passport status could not be determined safely.\"}\n",
        text(unavailable(StagedPassportGateReason.GIT_CAPTURE_FAILED)));
assertEquals(text(unavailable(StagedPassportGateReason.GIT_CAPTURE_FAILED)),
        text(unavailable(StagedPassportGateReason.PASSPORT_STORE_FAILED)));
```

Also assert that every nonempty output is UTF-8, ends in exactly one LF, is at most `CodexHookStatusRenderer.MAXIMUM_OUTPUT_BYTES`, parses as one JSON object, has boolean `continue == true`, and does not contain `private/marker.java`, `relativePaths`, `diffFingerprint`, `source`, `question`, `answer`, `feedback`, or `evidence`.

- [ ] **Step 2: Run the renderer test and prove RED**

Run:

```powershell
mvn -Dtest=CodexHookStatusRendererTest test
```

Expected: compilation fails because `CodexHookStatusRenderer` does not exist.

- [ ] **Step 3: Add the evaluator port and implement it in the existing use case**

Create:

```java
package dev.codedefense.application;

import dev.codedefense.domain.StagedPassportGateResult;
import java.nio.file.Path;

@FunctionalInterface
public interface StagedPassportGateEvaluator {
    StagedPassportGateResult evaluate(Path repository);
}
```

Change only the declaration of the existing use case:

```java
public final class EvaluateStagedPassportGateUseCase implements StagedPassportGateEvaluator {
```

Add `@Override` to its existing `evaluate(Path repository)` method. Do not change any gate state, reason, path, history, or identity behavior.

- [ ] **Step 4: Implement the renderer with explicit state mapping and JSON escaping**

Create `CodexHookStatusRenderer` with this public surface:

```java
public final class CodexHookStatusRenderer {
    public static final int MAXIMUM_OUTPUT_BYTES = 4 * 1024;

    public Optional<byte[]> render(StagedPassportGateResult result) {
        Objects.requireNonNull(result, "result");
        Optional<String> message = switch (result.state()) {
            case NO_STAGED_CHANGE -> Optional.empty();
            case UNDEFENDED -> Optional.of((
                    "CodeDefense: UNDEFENDED — %d staged files, +%d/-%d.\n"
                    + "Run a CodeDefense staged defense before committing.")
                    .formatted(result.stagedFileCount(), result.addedLines(), result.deletedLines()));
            case CURRENT -> Optional.of("CodeDefense: CURRENT — Passport %s, attempt %d."
                    .formatted(result.diffFingerprint().substring(0, 12), result.attemptNumber()));
            case EXPIRED -> Optional.of((
                    "CodeDefense: EXPIRED — %d staged files, +%d/-%d; the staged change no longer matches its Passport.\n"
                    + "Run a new defense for the current staged change.")
                    .formatted(result.stagedFileCount(), result.addedLines(), result.deletedLines()));
            case UNAVAILABLE -> result.reason() == StagedPassportGateReason.INVALID_REPOSITORY
                    ? Optional.empty()
                    : Optional.of("CodeDefense: UNAVAILABLE — staged Passport status could not be determined safely.");
        };
        return message.map(CodexHookStatusRenderer::encode);
    }
}
```

`encode` must build exactly `{"continue":true,"systemMessage":<quoted>}` plus LF, escape `"`, `\`, `\b`, `\f`, `\n`, `\r`, `\t`, and control characters, encode once with `StandardCharsets.UTF_8`, and reject a byte array larger than 4096 with `IllegalArgumentException("Codex hook output exceeds the maximum size")`. It must never serialize `StagedPassportGateResult` itself.

- [ ] **Step 5: Run focused tests and prove GREEN**

Run:

```powershell
mvn "-Dtest=CodexHookStatusRendererTest,EvaluateStagedPassportGateUseCaseTest" test
```

Expected: all selected tests pass; no test invokes Codex.

- [ ] **Step 6: Commit only Task 1 files**

```powershell
git add -- `
  src/main/java/dev/codedefense/application/StagedPassportGateEvaluator.java `
  src/main/java/dev/codedefense/application/EvaluateStagedPassportGateUseCase.java `
  src/main/java/dev/codedefense/codexhook/CodexHookStatusRenderer.java `
  src/test/java/dev/codedefense/codexhook/CodexHookStatusRendererTest.java
git commit --only -m "feat: add bounded Codex hook status rendering" -- `
  src/main/java/dev/codedefense/application/StagedPassportGateEvaluator.java `
  src/main/java/dev/codedefense/application/EvaluateStagedPassportGateUseCase.java `
  src/main/java/dev/codedefense/codexhook/CodexHookStatusRenderer.java `
  src/test/java/dev/codedefense/codexhook/CodexHookStatusRendererTest.java
```

---

### Task 2: Add the hidden lazy `codex-hook status` CLI adapter

**Files:**
- Create: `src/main/java/dev/codedefense/cli/StagedPassportGateRuntimeFactory.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportGateCommand.java`
- Create: `src/main/java/dev/codedefense/cli/CodexHookCommand.java`
- Create: `src/main/java/dev/codedefense/cli/CodexHookStatusCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Create: `src/test/java/dev/codedefense/cli/CodexHookStatusCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/PassportGateCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

**Interfaces:**
- Consumes: `Supplier<? extends StagedPassportGateEvaluator>` and `CodexHookStatusRenderer`.
- Produces: hidden command path `codedefense codex-hook status` with no path or model options.

- [ ] **Step 1: Write command-level tests with a hand-written fake evaluator**

Cover all of the following in `CodexHookStatusCommandTest`:

```java
@Test
void evaluatesOnlyTheCurrentWorkingDirectoryAndUsesConfiguredOutput() {
    AtomicReference<Path> evaluated = new AtomicReference<>();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CommandLine cli = commandLine(() -> repository -> {
        evaluated.set(repository);
        return current();
    }, output, new ByteArrayOutputStream());

    assertEquals(ExitCodes.SUCCESS, cli.execute());
    assertEquals(Path.of(".").toAbsolutePath().normalize(),
            evaluated.get().toAbsolutePath().normalize());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Passport aaaaaaaaaaaa"));
}
```

Add tests proving: silent states write zero stdout bytes; unexpected evaluator failure returns `ExitCodes.GIT_EXECUTION_FAILED`, writes only `CodeDefense hook status is unavailable.\n` to the configured error writer, and does not leak the exception message or stack trace; `--help` and `--version` return success without calling the supplier or evaluator; an unexpected positional path returns `ExitCodes.INVALID_USAGE` without evaluating; stdin is not referenced by the command.

- [ ] **Step 2: Extend CLI foundation tests and prove RED**

Add assertions:

```java
assertTrue(commandLine.getSubcommands().containsKey("codex-hook"));
assertFalse(rootHelp.contains("codex-hook"));
assertEquals(ExitCodes.SUCCESS, commandLine.execute("codex-hook", "status", "--help"));
```

Run:

```powershell
mvn "-Dtest=CodexHookStatusCommandTest,CliFoundationTest" test
```

Expected: compilation/test failure because the hidden command is not registered.

- [ ] **Step 3: Centralize production staged-gate composition**

Create a package-private factory:

```java
final class StagedPassportGateRuntimeFactory {
    private StagedPassportGateRuntimeFactory() {
    }

    static StagedPassportGateEvaluator create() {
        return new EvaluateStagedPassportGateUseCase(
                new GitCliStagedChangeSource(new JdkProcessExecutor()),
                new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                        new MarkdownChangePassportRenderer(), Clock.systemUTC()));
    }
}
```

Change `PassportGateCommand` to store `StagedPassportGateEvaluator`, accept that interface in its package-private constructor, and call `StagedPassportGateRuntimeFactory.create()` from its no-argument constructor. Remove its private duplicate `production()` method. Keep the public `passport gate` JSON contract byte-for-byte unchanged.

- [ ] **Step 4: Implement the hidden parent and lazy status command**

Create the parent:

```java
@Command(name = "codex-hook", hidden = true, mixinStandardHelpOptions = true,
        version = "CodeDefense 0.1.0", subcommands = CodexHookStatusCommand.class)
public final class CodexHookCommand implements Runnable {
    @Spec private CommandSpec commandSpec;

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }
}
```

Create the status adapter with a no-argument constructor that stores, but does not call, `StagedPassportGateRuntimeFactory::create`:

```java
@Command(name = "status", mixinStandardHelpOptions = true, version = "CodeDefense 0.1.0",
        description = "Render source-free staged Passport status for a Codex hook.")
public final class CodexHookStatusCommand implements Callable<Integer> {
    private final Supplier<? extends StagedPassportGateEvaluator> evaluatorFactory;
    private final CodexHookStatusRenderer renderer;
    @Spec private CommandSpec commandSpec;

    public CodexHookStatusCommand() {
        this(StagedPassportGateRuntimeFactory::create, new CodexHookStatusRenderer());
    }

    CodexHookStatusCommand(Supplier<? extends StagedPassportGateEvaluator> evaluatorFactory,
            CodexHookStatusRenderer renderer) {
        this.evaluatorFactory = Objects.requireNonNull(evaluatorFactory, "evaluatorFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public Integer call() {
        try {
            Optional<byte[]> output = renderer.render(Objects.requireNonNull(
                    evaluatorFactory.get(), "evaluator").evaluate(Path.of(".")));
            if (output.isPresent()) {
                commandSpec.commandLine().getOut().print(
                        new String(output.orElseThrow(), StandardCharsets.UTF_8));
                commandSpec.commandLine().getOut().flush();
            }
            return ExitCodes.SUCCESS;
        } catch (RuntimeException exception) {
            commandSpec.commandLine().getErr().println("CodeDefense hook status is unavailable.");
            return ExitCodes.GIT_EXECUTION_FAILED;
        }
    }
}
```

Do not accept path parameters and do not read `System.in` or `System.console()`.

- [ ] **Step 5: Register the hidden command without changing public help**

In `CodeDefenseApplication.createCommandLine(...)`, add:

```java
commandLine.addSubcommand("codex-hook", new CodexHookCommand());
```

Leave all existing overloads and public command instances intact. Picocli's `hidden = true` must keep `codex-hook` out of `codedefense --help` while direct hidden help/version remains functional.

- [ ] **Step 6: Run focused CLI tests and the packaged hidden command**

```powershell
mvn "-Dtest=CodexHookStatusCommandTest,PassportGateCommandTest,CliFoundationTest" test
mvn package
java -jar target/codedefense.jar codex-hook status --help
java -jar target/codedefense.jar codex-hook status --version
```

Expected: focused tests pass; both Java commands exit `0`; neither command invokes Git or Codex. Do not run bare `codex-hook status` against the user's dirty repository during this task.

- [ ] **Step 7: Commit only Task 2 files**

```powershell
git add -- `
  src/main/java/dev/codedefense/cli/StagedPassportGateRuntimeFactory.java `
  src/main/java/dev/codedefense/cli/PassportGateCommand.java `
  src/main/java/dev/codedefense/cli/CodexHookCommand.java `
  src/main/java/dev/codedefense/cli/CodexHookStatusCommand.java `
  src/main/java/dev/codedefense/CodeDefenseApplication.java `
  src/test/java/dev/codedefense/cli/CodexHookStatusCommandTest.java `
  src/test/java/dev/codedefense/cli/PassportGateCommandTest.java `
  src/test/java/dev/codedefense/cli/CliFoundationTest.java
git commit --only -m "feat: expose advisory Codex hook command" -- `
  src/main/java/dev/codedefense/cli/StagedPassportGateRuntimeFactory.java `
  src/main/java/dev/codedefense/cli/PassportGateCommand.java `
  src/main/java/dev/codedefense/cli/CodexHookCommand.java `
  src/main/java/dev/codedefense/cli/CodexHookStatusCommand.java `
  src/main/java/dev/codedefense/CodeDefenseApplication.java `
  src/test/java/dev/codedefense/cli/CodexHookStatusCommandTest.java `
  src/test/java/dev/codedefense/cli/PassportGateCommandTest.java `
  src/test/java/dev/codedefense/cli/CliFoundationTest.java
```

---

### Task 3: Scaffold the repo marketplace, plugin, skill, and one Stop hook

**Files:**
- Create: `.agents/plugins/marketplace.json`
- Create: `plugins/codedefense/.codex-plugin/plugin.json`
- Create: `plugins/codedefense/skills/codedefense/SKILL.md`
- Create: `plugins/codedefense/hooks/hooks.json`
- Create: `plugins/codedefense/cli/.gitkeep`
- Create: `src/test/java/dev/codedefense/plugin/CodexPluginContractTest.java`

**Interfaces:**
- Consumes: bundled relative path `./cli/codedefense.jar` and hidden Java command from Task 2.
- Produces: one discoverable local plugin named `codedefense`, one skill named `codedefense`, and one trusted advisory `Stop` handler.

- [ ] **Step 1: Scaffold the repo-local plugin with the official creator**

Run from the installed `plugin-creator` skill directory, targeting this repository:

```powershell
python scripts/create_basic_plugin.py codedefense `
  --path C:\JavaFundamentals\CodeDefense\plugins `
  --marketplace-path C:\JavaFundamentals\CodeDefense\.agents\plugins\marketplace.json `
  --with-skills --with-hooks --with-scripts --with-marketplace
```

Expected: the creator produces the required manifest and repo marketplace entry. Remove any unused scaffold placeholder files with `apply_patch`; retain only the file map above plus launchers added in Task 4.

- [ ] **Step 2: Write strict plugin contract tests before finalizing the assets**

In `CodexPluginContractTest`, parse JSON with the existing Jackson dependency and assert:

```java
assertEquals("codedefense", manifest.path("name").asText());
assertEquals("0.1.0", manifest.path("version").asText());
assertEquals("./skills/", manifest.path("skills").asText());
assertFalse(manifest.has("apps"));
assertFalse(manifest.has("mcpServers"));
assertFalse(manifest.has("hooks")); // default hooks/hooks.json discovery

assertEquals("codedefense-local", marketplace.path("name").asText());
assertEquals(1, marketplace.path("plugins").size());
assertEquals("./plugins/codedefense", plugin.path("source").path("path").asText());
assertEquals("AVAILABLE", plugin.path("policy").path("installation").asText());
assertEquals("ON_INSTALL", plugin.path("policy").path("authentication").asText());
assertEquals("Productivity", plugin.path("category").asText());

assertEquals(1, hooks.path("hooks").size());
assertEquals(1, hooks.path("hooks").path("Stop").size());
assertEquals(1, hooks.path("hooks").path("Stop").get(0).path("hooks").size());
assertEquals("command", handler.path("type").asText());
assertEquals(15, handler.path("timeout").asInt());
assertTrue(handler.path("command").asText().contains("codedefense-hook.sh"));
assertTrue(handler.path("commandWindows").asText().contains("codedefense-hook.ps1"));
```

Read `SKILL.md` as strict UTF-8 and assert its frontmatter contains only `name` and `description`; it contains `codex-hook status`, `prove --staged --dry-run .`, `passport show .`, and `passport insights . --format json --limit 20`; and it does not contain `--yes`, `codex exec`, `expectedKeyPoints`, or instructions to answer defense questions.

- [ ] **Step 3: Run the contract test and prove RED against scaffold defaults**

```powershell
mvn -Dtest=CodexPluginContractTest test
```

Expected: at least one exact metadata or safe-skill assertion fails until the source-controlled assets below are finalized.

- [ ] **Step 4: Finalize exact marketplace and manifest metadata**

Use this marketplace shape:

```json
{
  "name": "codedefense-local",
  "interface": { "displayName": "CodeDefense Local" },
  "plugins": [
    {
      "name": "codedefense",
      "source": { "source": "local", "path": "./plugins/codedefense" },
      "policy": { "installation": "AVAILABLE", "authentication": "ON_INSTALL" },
      "category": "Productivity"
    }
  ]
}
```

Use this minimal manifest shape; omit the `hooks` field because the default location is used and the local validator rejects unsupported manifest fields:

```json
{
  "name": "codedefense",
  "version": "0.1.0",
  "description": "Show source-free staged Change Passport status and guide explicit CodeDefense workflows.",
  "author": { "name": "CodeDefense" },
  "skills": "./skills/",
  "interface": {
    "displayName": "CodeDefense",
    "shortDescription": "Defend the exact staged change",
    "longDescription": "Preview bounded staged context, inspect source-free Passport status, and guide an explicit technical defense.",
    "developerName": "CodeDefense",
    "category": "Productivity",
    "capabilities": ["Read"],
    "defaultPrompt": "Show the source-free CodeDefense status for my staged change."
  }
}
```

- [ ] **Step 5: Write the concise instruction-only skill**

Use only `name` and `description` in frontmatter:

```markdown
---
name: codedefense
description: Inspect source-free CodeDefense staged Passport status, preview a bounded staged defense, show the latest Passport score card, or summarize repository learning insights. Use when the user asks whether staged work is defended, wants CodeDefense status, or wants the exact safe next action. Do not use it to answer a defense for the user.
---

# CodeDefense

Resolve this plugin root from the location of this `SKILL.md`; the bundled CLI is `cli/codedefense.jar`. Require Java 21.

Use only the command needed for the user's request:

- Status: `java -jar <plugin-root>/cli/codedefense.jar codex-hook status`
- Safe preview: `java -jar <plugin-root>/cli/codedefense.jar prove --staged --dry-run .`
- Latest score card: `java -jar <plugin-root>/cli/codedefense.jar passport show .`
- Learning Radar: `java -jar <plugin-root>/cli/codedefense.jar passport insights . --format json --limit 20`

Treat an empty status response as either no staged change or a non-Git working directory. Explain `UNDEFENDED`, `CURRENT`, `EXPIRED`, and `UNAVAILABLE` as educational source-free states, never as merge or deployment approval.

To start an actual defense, instruct the user to open the CodeDefense IntelliJ Tool Window or run `java -jar <plugin-root>/cli/codedefense.jar prove --staged .` in an interactive terminal. Preserve preview and explicit confirmation.

Never start a source-sending run automatically. Never add a confirmation-bypass option. Never answer defense questions, read a transcript, infer a thread, expose raw JSON or source/diffs, or modify Git, Passport artifacts, commit messages, or settings unless the user separately requests an already-supported local CodeDefense action.
```

- [ ] **Step 6: Define exactly one advisory Stop hook**

Use:

```json
{
  "description": "Show source-free staged CodeDefense Passport status after a Codex turn.",
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "\"${PLUGIN_ROOT}/scripts/codedefense-hook.sh\"",
            "commandWindows": "powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"$env:PLUGIN_ROOT\\scripts\\codedefense-hook.ps1\"",
            "timeout": 15,
            "statusMessage": "Checking staged CodeDefense Passport"
          }
        ]
      }
    ]
  }
}
```

Do not add a matcher, async handler, second lifecycle event, continuation decision, or retry reason.

- [ ] **Step 7: Validate plugin and skill, then run GREEN tests**

```powershell
python C:\Users\dimag\.codex\skills\.system\plugin-creator\scripts\validate_plugin.py `
  C:\JavaFundamentals\CodeDefense\plugins\codedefense
python C:\Users\dimag\.codex\skills\.system\skill-creator\scripts\quick_validate.py `
  C:\JavaFundamentals\CodeDefense\plugins\codedefense\skills\codedefense
mvn -Dtest=CodexPluginContractTest test
```

Expected: both validators succeed and all contract tests pass.

- [ ] **Step 8: Commit only Task 3 files**

```powershell
git add -- `
  .agents/plugins/marketplace.json `
  plugins/codedefense/.codex-plugin/plugin.json `
  plugins/codedefense/skills/codedefense/SKILL.md `
  plugins/codedefense/hooks/hooks.json `
  plugins/codedefense/cli/.gitkeep `
  src/test/java/dev/codedefense/plugin/CodexPluginContractTest.java
git commit --only -m "feat: add CodeDefense Codex plugin and skill" -- `
  .agents/plugins/marketplace.json `
  plugins/codedefense/.codex-plugin/plugin.json `
  plugins/codedefense/skills/codedefense/SKILL.md `
  plugins/codedefense/hooks/hooks.json `
  plugins/codedefense/cli/.gitkeep `
  src/test/java/dev/codedefense/plugin/CodexPluginContractTest.java
```

---

### Task 4: Add deterministic platform launchers and release packaging

**Files:**
- Create: `plugins/codedefense/scripts/codedefense-hook.ps1`
- Create: `plugins/codedefense/scripts/codedefense-hook.sh`
- Create: `scripts/package-codex-plugin.ps1`
- Create: `scripts/package-codex-plugin.sh`
- Modify: `.gitignore`
- Create: `src/test/java/dev/codedefense/plugin/CodexPluginLauncherTest.java`

**Interfaces:**
- Consumes: `<plugin-root>/cli/codedefense.jar` and fixed arguments `codex-hook status`.
- Produces: identical Java invocation semantics and preserved exit codes on Windows and POSIX, plus `target/codedefense-codex-plugin.zip`.

- [ ] **Step 1: Write executable launcher tests before the scripts exist**

`CodexPluginLauncherTest` must build a tiny temporary executable JAR with a manifest whose main class prints `String.join("|", args)`. Copy the relevant launcher into a temporary `plugin with spaces/scripts` path and the fixture JAR into `plugin with spaces/cli/codedefense.jar`.

On Windows (`@EnabledOnOs(OS.WINDOWS)`), launch the absolute PowerShell executable with `-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <script>`, set `JAVA_HOME` to `System.getProperty("java.home")`, and assert exit `0`, stdout exactly `codex-hook|status`, and empty stderr.

On Linux/macOS (`@EnabledOnOs({OS.LINUX, OS.MAC})`), launch `/bin/sh <script>` with the same `JAVA_HOME` and assertions. Add platform-specific tests that remove the fixture JAR and receive only `CodeDefense hook launcher is unavailable.\n` on stderr with nonzero exit. Add tests with the JAR present, invalid `JAVA_HOME`, and a command environment with no Java path; receive only the same fixed diagnostic and nonzero exit.

Inspect both source scripts and assert absence of `Invoke-Expression`, `cmd.exe`, `powershell.exe -Command`, `eval`, `Write-Output`, environment dumps, and dynamic Java argument strings. Do not reject the safe PowerShell discovery cmdlet `Get-Command`.

- [ ] **Step 2: Run launcher tests and prove RED**

```powershell
mvn -Dtest=CodexPluginLauncherTest test
```

Expected: tests fail because the launchers do not exist.

- [ ] **Step 3: Implement the fixed PowerShell launcher**

Use this control flow, with all diagnostics fixed and source-free:

```powershell
$ErrorActionPreference = "Stop"
$PluginRoot = Split-Path -Parent $PSScriptRoot
$Jar = Join-Path $PluginRoot "cli\codedefense.jar"
$Unavailable = "CodeDefense hook launcher is unavailable."

if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
    [Console]::Error.WriteLine($Unavailable)
    exit 1
}

$Java = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $Candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $Candidate -PathType Leaf) { $Java = $Candidate }
}
if ($null -eq $Java) {
    $Command = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $Command) { $Java = $Command.Source }
}
if ($null -eq $Java) {
    [Console]::Error.WriteLine($Unavailable)
    exit 1
}

& $Java -jar $Jar codex-hook status
exit $LASTEXITCODE
```

Wrap setup failures in one outer `try/catch` that prints the same fixed diagnostic and exits `1`; never print `$Error`, exception text, `$env:*`, `$Jar`, or `$Java`.

- [ ] **Step 4: Implement the fixed POSIX launcher**

Use shell builtins for root resolution so a deliberately empty `PATH` still reaches the fixed missing-Java diagnostic:

```sh
#!/bin/sh
unavailable='CodeDefense hook launcher is unavailable.'
script_dir=${0%/*}
[ "$script_dir" = "$0" ] && script_dir=.
plugin_root=$(CDPATH= cd -- "$script_dir/.." 2>/dev/null && pwd) || {
  printf '%s\n' "$unavailable" >&2
  exit 1
}
jar=$plugin_root/cli/codedefense.jar
[ -f "$jar" ] || {
  printf '%s\n' "$unavailable" >&2
  exit 1
}

java_command=
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_command=$JAVA_HOME/bin/java
elif command -v java >/dev/null 2>&1; then
  java_command=java
else
  printf '%s\n' "$unavailable" >&2
  exit 1
fi

"$java_command" -jar "$jar" codex-hook status
exit $?
```

Mark it executable in Git with `git update-index --chmod=+x plugins/codedefense/scripts/codedefense-hook.sh` after staging.

- [ ] **Step 5: Add packaging scripts with identical archive layout**

Both scripts must:

1. Resolve repository root from the script location.
2. Require `target/codedefense.jar`.
3. Copy it to the ignored `plugins/codedefense/cli/codedefense.jar` for local marketplace installation.
4. Recreate `target/codex-plugin-package/codedefense` from the source plugin directory.
5. Create `target/codedefense-codex-plugin.zip` with one top-level `codedefense/` directory.
6. Fail nonzero with `Build target/codedefense.jar before packaging the Codex plugin.` when the shaded JAR is absent.

PowerShell uses `Copy-Item`, `Remove-Item`, and `Compress-Archive` with `-LiteralPath` where applicable. POSIX uses fixed quoted `cp`/`rm`/`mkdir` arguments and `jar --create --file "$archive" -C "$stage" codedefense`; it never uses `eval`.

- [ ] **Step 6: Ignore generated plugin binaries**

Append:

```gitignore
# Generated Codex plugin payload
plugins/codedefense/cli/codedefense.jar
```

The archive and staging tree already live under ignored `target/`; do not add broad ignores for source-controlled plugin files.

- [ ] **Step 7: Run launcher tests and package on Windows**

```powershell
mvn "-Dtest=CodexPluginLauncherTest,CodexPluginContractTest" test
mvn package
.\scripts\package-codex-plugin.ps1
jar tf target\codedefense-codex-plugin.zip
git check-ignore plugins/codedefense/cli/codedefense.jar
git ls-files plugins/codedefense/cli/codedefense.jar
```

Expected: tests pass with only the POSIX execution tests skipped on Windows; packaging exits `0`; archive listing contains one manifest, one skill, one hook file, two launchers, and one `cli/codedefense.jar`; `git check-ignore` prints the copied JAR; `git ls-files` prints nothing.

- [ ] **Step 8: Commit only Task 4 files**

```powershell
git add -- `
  plugins/codedefense/scripts/codedefense-hook.ps1 `
  plugins/codedefense/scripts/codedefense-hook.sh `
  scripts/package-codex-plugin.ps1 `
  scripts/package-codex-plugin.sh `
  .gitignore `
  src/test/java/dev/codedefense/plugin/CodexPluginLauncherTest.java
git update-index --chmod=+x plugins/codedefense/scripts/codedefense-hook.sh
git update-index --chmod=+x scripts/package-codex-plugin.sh
git commit --only -m "build: package cross-platform Codex plugin launchers" -- `
  plugins/codedefense/scripts/codedefense-hook.ps1 `
  plugins/codedefense/scripts/codedefense-hook.sh `
  scripts/package-codex-plugin.ps1 `
  scripts/package-codex-plugin.sh `
  .gitignore `
  src/test/java/dev/codedefense/plugin/CodexPluginLauncherTest.java
```

---

### Task 5: Document, verify, and prepare installed-plugin acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

**Interfaces:**
- Consumes: the packaged plugin archive and repo marketplace from Tasks 3-4.
- Produces: truthful installation/privacy/platform documentation and an unchecked acceptance gate.

- [ ] **Step 1: Add the Codex-native integration section to README**

Document these exact facts:

- The plugin shows source-free staged Passport awareness after a Codex turn and consumes no credit.
- Installation uses the repo marketplace `.agents/plugins/marketplace.json`, a host restart, and explicit `/hooks` review/trust.
- Java 21 is resolved from `JAVA_HOME` first and then `PATH`.
- `UNDEFENDED`, `CURRENT`, and `EXPIRED` are advisory educational states, never merge/deploy approval.
- No message is expected outside Git or with no staged change.
- The skill can run status, dry preview, latest score card, and Learning Radar, but actual defense remains interactive in CLI/IntelliJ.
- Disable/uninstall through the Codex plugin UI; hook trust is not bypassed.
- Windows is claimed only after packaged PowerShell execution; macOS/Linux only after packaged POSIX execution on those platforms.
- The hook sends no source, path, answer, question, transcript, raw JSON, evidence, or model data and invokes no Codex process.

Include the build/install commands:

```powershell
mvn package
.\scripts\package-codex-plugin.ps1
```

and POSIX equivalents:

```sh
mvn package
./scripts/package-codex-plugin.sh
```

- [ ] **Step 2: Add the unchecked checklist entry**

Insert immediately before Iteration 9:

```markdown
- [ ] Iteration 8.17 — Codex plugin, source-free skill, and advisory staged Passport Stop hook (implemented and offline-verified; installed-plugin and cross-platform launcher acceptance pending).
```

Do not mark it complete during offline verification.

- [ ] **Step 3: Run all offline verification without a model call**

```powershell
mvn clean verify
mvn package
.\scripts\package-codex-plugin.ps1
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar --version
java -jar target/codedefense.jar codex-hook status --help
java -jar target/codedefense.jar codex-hook status --version
java -jar target/codedefense.jar prove --staged --dry-run .
```

Expected: Maven commands report `BUILD SUCCESS`; all Java help/version commands exit `0`; public root help does not list `codex-hook`; dry-run says `Codex was not invoked.` and does not create a Passport. If the user's existing staged fixture makes the dry-run unsuitable, create a disposable Git fixture outside the repository and run the same JAR there; never alter or unstage the user's fixture.

- [ ] **Step 4: Inspect the final archive and repository scope**

```powershell
jar tf target\codedefense-codex-plugin.zip
git status --short
git diff --check
git diff --cached --check
git ls-files plugins/codedefense/cli/codedefense.jar target/codedefense-codex-plugin.zip
```

Expected: archive has one top-level plugin directory and the exact bounded payload; generated binaries are not tracked; no whitespace errors; the pre-existing staged/untracked files are unchanged and absent from Iteration 8.17 commits.

- [ ] **Step 5: Commit only documentation**

```powershell
git add -- README.md docs/implementation-checklist.md
git commit --only -m "docs: explain Codex-native Passport awareness" -- `
  README.md docs/implementation-checklist.md
```

- [ ] **Step 6: Perform installed-plugin acceptance only with explicit execution approval**

After installing from the repo marketplace and restarting Codex:

1. Open `/hooks`, inspect the exact bundled `Stop` command, and explicitly trust it.
2. Outside Git and with no staged change, finish a turn and confirm no status message.
3. In a disposable Git repository, stage one supported file and finish a turn; confirm `UNDEFENDED` with correct counts.
4. Run the defense through the existing interactive CLI or IntelliJ Tool Window; finish another Codex turn and confirm `CURRENT` with the 12-character fingerprint and attempt.
5. Change and restage one line; finish another turn and confirm `EXPIRED` with re-defense guidance.
6. Confirm no temporary `codedefense-codex-*` workspace, no new model request from the hook, no source/path content in hook output, and no automatic retry.
7. Execute the packaged POSIX launcher on macOS or Linux before changing documentation from “pending” to supported for that platform family.

Only after all applicable platform and transition evidence is recorded may the checklist line be changed from `[ ]` to `[x]` in a separate acceptance commit.

---

## Final review checklist

- [ ] Every design requirement maps to one task above.
- [ ] A manual placeholder scan finds no deferred decisions, unfinished code markers, or vague cross-task references.
- [ ] Both CLI adapters depend on the same `StagedPassportGateEvaluator` signature.
- [ ] The hidden command is absent from root help but direct help/version are lazy and successful.
- [ ] The renderer omits relative paths even for `EXPIRED`.
- [ ] The plugin contains one skill and one `Stop` hook, with no MCP/app configuration.
- [ ] The skill never bypasses confirmation or answers the defense.
- [ ] Platform launchers pass fixed tokens and preserve the Java exit code.
- [ ] Generated JAR/ZIP files are ignored and untracked.
- [ ] Full Maven verification is offline and invokes no real Codex.
- [ ] Iteration 8.17 remains unchecked until installed acceptance; Iteration 9 remains untouched.
