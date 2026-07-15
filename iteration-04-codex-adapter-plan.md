# CodeDefense — Iteration 4: Codex Adapter Implementation Plan

> **Goal:** add a reusable, testable Java adapter for locally authenticated `codex exec` runs with bounded process I/O, schema-constrained final JSON, deterministic safety flags, clean temporary workspaces, and user-friendly preflight errors.
>
> **Branch:** `feat/iteration-04-codex-adapter`
>
> **Iteration boundary:** this iteration builds and proves the Codex runtime adapter. It does **not** analyze a project, create technical questions, start the interview, or generate reports.

## 1. Current baseline

Iteration 3 is merged into `main`.

The current `start` flow already:

1. scans a local repository;
2. builds a privacy-aware bounded snapshot;
3. previews selected files and limits;
4. asks for explicit confirmation;
5. stops before invoking Codex.

Iteration 4 replaces the final placeholder with a **Codex readiness check**, and separately proves the actual structured `codex exec` adapter through unit tests and an opt-in live smoke test.

`start` must still **not consume model credits** in Iteration 4. The first real project-analysis call belongs to Iteration 5.

## 2. Global constraints

- Java 21 and Maven only.
- No Spring or dependency-injection framework.
- No OpenAI API key.
- Use the user's locally authenticated Codex CLI session.
- No new third-party dependencies.
- No analyzed source code is written to the Codex workspace.
- No prompt or source snapshot is passed as a command-line argument.
- No test in the default Maven suite calls real Codex.
- The opt-in live smoke test is disabled unless a system property explicitly enables it.
- No `--yolo`, `danger-full-access`, or `workspace-write`.
- Use `--sandbox read-only`.
- Use `--ask-for-approval never`.
- Use an empty temporary workspace.
- Use `--ephemeral`.
- Use `--output-schema` and `--output-last-message`.
- Use `-` as the prompt argument and write the prompt through stdin.
- Delete temporary files in `finally`/`AutoCloseable`.
- Bound stdout, stderr, and final-response memory usage.
- Expected failures must not print Java stack traces.

## 3. Official Codex command contract used by CodeDefense

The adapter should construct the semantic equivalent of:

```text
codex exec
  --ephemeral
  --ignore-user-config
  --sandbox read-only
  --ask-for-approval never
  --skip-git-repo-check
  --color never
  --model gpt-5.6-terra
  --config model_reasoning_effort="medium"
  --cd <empty-temporary-workspace>
  --output-schema <temporary-schema.json>
  --output-last-message <temporary-final-message.json>
  -
```

The prompt is written to process stdin and stdin is then closed.

Do **not** use:

```text
--dangerously-bypass-approvals-and-sandbox
--yolo
--full-auto
--sandbox workspace-write
--sandbox danger-full-access
```

`--ignore-user-config` is intentional: CodeDefense must not unexpectedly inherit user MCP servers, hooks, model choices, or runtime behavior. Authentication still uses the user's Codex credential cache.

Do not pass `--ignore-rules` in the MVP. Existing execpolicy rules are an additional safety boundary and may remain active.

## 4. Architecture

```text
StartCommand
    └── CodexPreflight
            └── CodexEnvironmentChecker
                    └── ProcessExecutor

Iteration 5 domain services
    └── AiProvider
            └── CodexCliAiProvider
                    ├── CodexEnvironmentChecker
                    └── CodexProcessRunner
                            ├── CodexCommandFactory
                            ├── ProcessExecutor
                            ├── CodexTemporaryWorkspace
                            └── Jackson JSON validation
```

### 4.1 Two boundaries

**Generic process boundary**

```java
public interface ProcessExecutor {
    ProcessResult execute(ProcessSpec spec);
}
```

This layer knows nothing about Codex.

**Structured AI boundary**

```java
public interface AiProvider {
    StructuredCodexResult execute(StructuredCodexRequest request);
}
```

This gives future providers a stable contract. A future OpenAI API or local-model adapter can implement the same interface without changing project-analysis services.

### 4.2 Do not create Iteration 5 types yet

Do not add:

```text
ProjectAnalysis
TechnicalQuestion
CodeEvidence
AnswerEvaluation
InterviewSession
```

Do not add production analysis prompts or project-analysis schemas.

`PromptLoader` and `PromptRenderer` are deferred to Iteration 5 because Iteration 4 has no production prompt template. Creating unused templating code now would violate YAGNI.

## 5. Target source tree

```text
src/main/java/dev/codedefense/
├── ai/
│   ├── AiProvider.java
│   ├── CodexCliAiProvider.java
│   ├── CodexCommandFactory.java
│   ├── CodexEnvironment.java
│   ├── CodexEnvironmentChecker.java
│   ├── CodexExecutable.java
│   ├── CodexPreflight.java
│   ├── CodexProcessEnvironment.java
│   ├── CodexProcessRunner.java
│   ├── CodexRuntimeConfig.java
│   ├── CodexTemporaryWorkspace.java
│   ├── JdkProcessExecutor.java
│   ├── ProcessExecutor.java
│   ├── ProcessResult.java
│   ├── ProcessSpec.java
│   ├── ReasoningEffort.java
│   ├── StructuredCodexRequest.java
│   ├── StructuredCodexResult.java
│   └── exception/
│       ├── CodexException.java
│       ├── CodexExecutionException.java
│       ├── CodexInterruptedException.java
│       ├── CodexNotAuthenticatedException.java
│       ├── CodexNotInstalledException.java
│       ├── CodexTimeoutException.java
│       └── InvalidCodexResponseException.java
├── cli/
│   └── StartCommand.java
└── CodeDefenseApplication.java

src/test/java/dev/codedefense/
├── ai/
│   ├── CodexCliAiProviderTest.java
│   ├── CodexCommandFactoryTest.java
│   ├── CodexEnvironmentCheckerTest.java
│   ├── CodexLiveSmokeTest.java
│   ├── CodexProcessEnvironmentTest.java
│   ├── CodexProcessRunnerTest.java
│   ├── JdkProcessExecutorTest.java
│   └── ProcessFixtureMain.java
└── cli/
    └── StartCommandCodexPreflightTest.java

src/test/resources/
└── schemas/
    └── codex-live-smoke.schema.json

scripts/
├── live-smoke-test.ps1
└── live-smoke-test.sh
```

Class names may be adjusted slightly, but responsibilities must stay separated.

## 6. Core contracts

### 6.1 `ReasoningEffort`

```java
public enum ReasoningEffort {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String cliValue;

    ReasoningEffort(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }
}
```

Do not include `XHIGH` in the MVP because model support is conditional.

### 6.2 `CodexRuntimeConfig`

```java
public record CodexRuntimeConfig(
        String defaultModel,
        Duration environmentCheckTimeout,
        Duration defaultExecutionTimeout,
        Duration terminationGracePeriod,
        int maximumCapturedStdoutBytes,
        int maximumCapturedStderrBytes,
        int maximumFinalResponseBytes
) {
    public static CodexRuntimeConfig defaults() {
        return new CodexRuntimeConfig(
                "gpt-5.6-terra",
                Duration.ofSeconds(15),
                Duration.ofSeconds(180),
                Duration.ofSeconds(2),
                16 * 1024,
                32 * 1024,
                1024 * 1024
        );
    }
}
```

Validation:

- model is nonblank;
- durations are positive;
- all byte limits are positive;
- final-response limit is at least 64 KiB.

Do not add Codex runtime fields to the existing snapshot `CodeDefenseConfig`. They are separate concerns.

### 6.3 `StructuredCodexRequest`

```java
public record StructuredCodexRequest(
        String operationName,
        String prompt,
        String schemaJson,
        String model,
        ReasoningEffort reasoningEffort,
        Duration timeout
) {
}
```

Validation:

- operation name is nonblank and contains no source text;
- prompt is nonblank;
- schema is nonblank;
- model is nonblank;
- reasoning effort is non-null;
- timeout is positive;
- prompt and schema are held in memory only;
- `toString()` must not expose prompt or schema content.

Provide a convenience factory using defaults, but keep all final values explicit in the record.

### 6.4 `StructuredCodexResult`

```java
public record StructuredCodexResult(
        String finalJson,
        Duration duration,
        String model
) {
}
```

Validation:

- final JSON is nonblank;
- duration is nonnegative;
- model is nonblank.

Do not include prompt, schema, auth information, or temporary paths.

### 6.5 `AiProvider`

```java
public interface AiProvider {
    StructuredCodexResult execute(StructuredCodexRequest request);
}
```

### 6.6 `CodexPreflight`

```java
public interface CodexPreflight {
    CodexEnvironment checkReady();
}
```

`StartCommand` depends on this small interface rather than on `ProcessBuilder` or the full AI provider.

## 7. Generic process execution

### 7.1 `ProcessSpec`

```java
public record ProcessSpec(
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        String standardInput,
        Duration timeout,
        Duration terminationGracePeriod,
        int maximumStdoutBytes,
        int maximumStderrBytes
) {
}
```

Validation:

- command is nonempty;
- no command token is null;
- working directory exists and is a directory;
- environment is copied immutably;
- stdin is non-null;
- durations and limits are positive.

### 7.2 `ProcessResult`

```java
public record ProcessResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        Duration duration
) {
}
```

### 7.3 `JdkProcessExecutor`

Responsibilities:

1. create `ProcessBuilder` from a list of command tokens;
2. set the exact working directory;
3. clear inherited environment;
4. apply the sanitized environment from `ProcessSpec`;
5. launch the process;
6. immediately start concurrent stdout/stderr drainers;
7. write UTF-8 stdin;
8. close stdin;
9. wait until timeout;
10. on timeout call `destroy()`;
11. wait for the grace period;
12. call `destroyForcibly()` if still alive;
13. always finish stream-drainer tasks;
14. preserve thread interruption;
15. never log stdin.

Use Java 21 virtual threads or a small executor. Do not call `readAllBytes()` without a bound.

The collectors must continue draining after the capture limit is reached. They may discard additional bytes but must not stop reading, because stopping can deadlock the child process.

### 7.4 Cross-platform process fixture

`ProcessFixtureMain` supports modes:

```text
echo       Read stdin, write it to stdout, exit 0.
stderr     Write requested bytes to stderr, exit 0.
fail       Write diagnostic text to stderr, exit requested code.
sleep      Sleep for requested milliseconds.
both       Write to stdout and stderr.
```

`JdkProcessExecutorTest` launches the current test JVM:

```java
Path javaExecutable = Path.of(
        System.getProperty("java.home"),
        "bin",
        isWindows ? "java.exe" : "java"
);
```

This proves the real process behavior without calling Codex.

## 8. Process environment

### 8.1 Why sanitize

Even in a read-only sandbox, a child process and commands it launches can see inherited environment variables. CodeDefense must not pass unrelated repository or CI secrets to the Codex process.

### 8.2 `CodexProcessEnvironment`

Start from the current environment and remove keys whose uppercase name contains:

```text
TOKEN
SECRET
PASSWORD
PASSWD
API_KEY
PRIVATE_KEY
CREDENTIAL
CONNECTION_STRING
DATABASE_URL
```

Preserve the environment required to run Codex and authenticate through its local cache:

```text
PATH
PATHEXT
SystemRoot
WINDIR
ComSpec
HOME
USERPROFILE
HOMEDRIVE
HOMEPATH
APPDATA
LOCALAPPDATA
TEMP
TMP
TMPDIR
LANG
LANGUAGE
LC_ALL
TERM
COLORTERM
CODEX_HOME
CODEX_CA_CERTIFICATE
SSL_CERT_FILE
SSL_CERT_DIR
HTTP_PROXY
HTTPS_PROXY
ALL_PROXY
NO_PROXY
```

Also preserve environment keys not matching the sensitive-name rule. This is a denylist defense-in-depth strategy rather than a strict allowlist, reducing platform breakage.

Explicitly remove:

```text
OPENAI_API_KEY
CODEX_API_KEY
CODEX_ACCESS_TOKEN
GITHUB_TOKEN
GH_TOKEN
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN
```

Return an immutable map.

## 9. Codex executable and preflight

### 9.1 Candidate commands

Do not parse shell startup files and do not concatenate shell command strings.

Try direct executable candidates through `ProcessExecutor`:

On Windows:

```text
codex
codex.exe
codex.cmd
```

On Linux/macOS:

```text
codex
```

A candidate is accepted only if:

```text
<candidate> --version
```

launches and exits with `0`.

Store the accepted command as immutable command-prefix tokens in `CodexExecutable`.

This empirical resolution handles the user's actual installation rather than assuming whether Codex came from npm or the standalone installer.

### 9.2 `CodexEnvironmentChecker`

Flow:

1. try candidates with `--version`;
2. if all fail to launch, throw `CodexNotInstalledException`;
3. retain the first nonblank version-output line, capped to 256 characters;
4. run:

```text
<resolved codex> login status
```

5. exit `0` means authenticated;
6. nonzero means `CodexNotAuthenticatedException`;
7. a timeout maps to `CodexTimeoutException`;
8. other launch/execution failures map to `CodexExecutionException`.

Result:

```java
public record CodexEnvironment(
        CodexExecutable executable,
        String version
) {
}
```

Do not return or print the raw authentication-mode output; it may contain account/workspace details.

### 9.3 User-facing messages

Not installed:

```text
Codex CLI was not found.

Install Codex, then run:
  codex login
```

Not authenticated:

```text
Codex CLI is installed but not authenticated.

Run:
  codex login
```

Ready:

```text
Codex CLI is installed and authenticated.
Version: <bounded version string>
Project analysis will be connected in Iteration 5.
```

## 10. Codex command factory

`CodexCommandFactory` consumes:

```text
CodexExecutable
StructuredCodexRequest
temporary workspace path
schema path
output path
```

It returns an immutable `List<String>`.

Expected order:

```text
<codex executable prefix>
exec
--ephemeral
--ignore-user-config
--sandbox
read-only
--ask-for-approval
never
--skip-git-repo-check
--color
never
--model
<request model>
--config
model_reasoning_effort="<request effort>"
--cd
<temporary workspace>
--output-schema
<temporary schema path>
--output-last-message
<temporary output path>
-
```

Requirements:

- prompt is absent from the command list;
- schema content is absent from the command list;
- only paths to temporary files are included;
- paths with spaces remain one command token;
- model remains one command token;
- no dangerous flag is present;
- command construction never calls a shell;
- final token is `-`.

Unit tests inspect the exact token list.

## 11. Temporary workspace

`CodexTemporaryWorkspace` implements `AutoCloseable`.

Creation:

```text
<system-temp>/codedefense-codex-<random>/
├── schema.json
└── final-message.json  (created by Codex)
```

The analyzed repository is never copied or linked here.

Responsibilities:

- create directory;
- write schema in UTF-8;
- expose workspace/schema/output paths;
- recursively delete without following symbolic links;
- make `close()` idempotent;
- cleanup after success, nonzero exit, invalid output, timeout, or interruption.

For tests, allow injecting a parent temporary directory so cleanup can be asserted deterministically.

## 12. Structured Codex process runner

### 12.1 Input validation

Before launching Codex:

1. parse `schemaJson` with Jackson;
2. require the schema root to be a JSON object;
3. reject schema larger than 256 KiB;
4. reject prompt larger than 512 KiB;
5. never include prompt/schema in exception messages.

### 12.2 Execution flow

```text
create temporary workspace
→ write schema.json
→ build command tokens
→ build sanitized process environment
→ execute process with prompt on stdin
→ inspect exit code
→ read bounded final-message.json
→ parse final message as JSON
→ return StructuredCodexResult
→ cleanup temporary workspace
```

### 12.3 Failure mapping

Missing output file:

```text
InvalidCodexResponseException
```

Empty output file:

```text
InvalidCodexResponseException
```

Output larger than configured maximum:

```text
InvalidCodexResponseException
```

Malformed JSON:

```text
InvalidCodexResponseException
```

Nonzero Codex exit:

```java
new CodexExecutionException(
        exitCode,
        boundedStderr
)
```

Timeout:

```text
CodexTimeoutException
```

Interrupted caller:

```text
CodexInterruptedException
```

Preserve the thread interrupt flag.

### 12.4 Diagnostic bounds

- stdout capture: 16 KiB;
- stderr capture: 32 KiB;
- final message: 1 MiB;
- stderr exception message must include a truncation marker when capped;
- no source code, prompt, schema, or final JSON is written to normal logs.

## 13. `CodexCliAiProvider`

Responsibilities:

1. lazily call `CodexEnvironmentChecker.checkReady()`;
2. cache the successful `CodexEnvironment` for the lifetime of the provider;
3. delegate to `CodexProcessRunner`;
4. never cache a failed readiness check;
5. synchronize only the first successful initialization.

Tests:

- first execution performs preflight once;
- second execution reuses cached environment;
- failed preflight is retried on the next call;
- request/result is passed through unchanged.

## 14. Start command integration

Modify `StartCommand` to inject `CodexPreflight`.

Updated normal flow:

```text
scan
→ snapshot
→ preview
→ confirmation
→ Codex preflight
→ ready message
→ exit 0
```

`--dry-run`:

```text
scan
→ snapshot
→ preview
→ no confirmation
→ no Codex preflight
→ exit 0
```

Declined confirmation:

```text
no Codex preflight
→ exit 0
```

`--yes`:

```text
bypass confirmation
→ run preflight
```

Error mapping:

```text
CodexNotInstalledException       → exit 5
CodexNotAuthenticatedException   → exit 6
CodexTimeoutException            → exit 7
CodexExecutionException          → exit 7
CodexInterruptedException        → exit 130
```

Do not invoke `codex exec` from `StartCommand` in Iteration 4.

Update the placeholder message from:

```text
Codex execution arrives in Iteration 4.
```

to:

```text
Project analysis will be connected in Iteration 5.
```

## 15. Exception hierarchy

```java
public abstract class CodexException extends RuntimeException {
    protected CodexException(String message) {
        super(message);
    }

    protected CodexException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Typed subclasses:

```text
CodexNotInstalledException
CodexNotAuthenticatedException
CodexExecutionException
CodexTimeoutException
CodexInterruptedException
InvalidCodexResponseException
```

Rules:

- messages are safe for direct CLI display;
- prompt and schema are never included;
- temporary absolute paths are omitted or replaced with generic labels;
- `CodexExecutionException` exposes exit code as a field;
- stderr is bounded and normalized to LF.

## 16. Test matrix

### 16.1 Runtime configuration

- defaults are exactly as specified;
- blank model rejected;
- zero/negative durations rejected;
- zero byte limits rejected.

### 16.2 Generic process executor

- stdin arrives unchanged;
- stdout captured;
- stderr captured;
- nonzero exit preserved;
- timeout terminates child;
- large stderr is drained without deadlock;
- captured stderr is bounded and marked truncated;
- interruption preserves interrupt status;
- command tokens with spaces remain intact.

### 16.3 Environment sanitizer

- `OPENAI_API_KEY` removed;
- `GITHUB_TOKEN` removed;
- arbitrary `*_PASSWORD` removed;
- `PATH` retained;
- `CODEX_HOME` retained;
- original map is not modified;
- result is immutable.

### 16.4 Environment checker

- first candidate succeeds;
- first candidate missing, second succeeds;
- all candidates missing;
- version command times out;
- login status exits 0;
- login status exits nonzero;
- raw auth output is not exposed;
- version output is bounded.

### 16.5 Command factory

- exact argument order;
- `--ephemeral`;
- `--ignore-user-config`;
- `--sandbox read-only`;
- `--ask-for-approval never`;
- `--skip-git-repo-check`;
- `--color never`;
- model and reasoning effort;
- output schema and final-message paths;
- final `-`;
- prompt absent;
- no `--yolo`;
- no full access;
- paths with spaces are one token.

### 16.6 Temporary workspace

- schema written exactly;
- output path starts absent;
- close removes directory;
- double close is safe;
- nested files deleted;
- cleanup does not follow symlinks.

### 16.7 Process runner

- prompt sent through stdin exactly;
- valid final JSON returned;
- schema path exists during execution;
- missing output rejected;
- empty output rejected;
- malformed JSON rejected;
- oversized output rejected;
- nonzero exit maps correctly;
- timeout maps correctly;
- stderr bounded;
- workspace deleted on every path;
- prompt absent from exception text.

### 16.8 Provider

- readiness cached after success;
- no cache after failure;
- runner receives resolved executable.

### 16.9 Start command

- dry-run never calls preflight;
- decline never calls preflight;
- `--yes` calls preflight;
- ready prints version;
- missing install → exit 5;
- missing auth → exit 6;
- preflight timeout/failure → exit 7;
- interruption → exit 130;
- help/version never call preflight.

## 17. Opt-in live smoke test

### 17.1 Schema

`src/test/resources/schemas/codex-live-smoke.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "status": {
      "type": "string",
      "const": "ok"
    },
    "message": {
      "type": "string",
      "minLength": 1,
      "maxLength": 120
    }
  },
  "required": ["status", "message"]
}
```

### 17.2 Smoke request

```text
Return the JSON object required by the supplied schema.
Set status to "ok".
Use a short message confirming that structured Codex execution works.
Do not run commands or use tools.
```

Use:

```text
model: gpt-5.6-terra
reasoning: low
timeout: 120 seconds
```

The smoke test parses the result and asserts:

```text
status == "ok"
message is nonblank
```

### 17.3 Disabled by default

```java
@EnabledIfSystemProperty(
        named = "codedefense.live.codex",
        matches = "true"
)
```

Default `mvn clean verify` must not perform a live model call.

### 17.4 PowerShell script

```powershell
$ErrorActionPreference = "Stop"

codex --version
codex login status

mvn `
  "-Dcodedefense.live.codex=true" `
  "-Dtest=CodexLiveSmokeTest" `
  test
```

### 17.5 Bash script

```bash
#!/usr/bin/env bash
set -euo pipefail

codex --version
codex login status

mvn \
  -Dcodedefense.live.codex=true \
  -Dtest=CodexLiveSmokeTest \
  test
```

One live smoke run consumes a small amount of Codex credit. Do not run it on every code edit.

## 18. Implementation tasks and commits

### Task 0 — Branch and documentation correction

Commands:

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/iteration-04-codex-adapter
```

Fix `AGENTS.md` so it references the existing plan:

```text
docs/codedefense-mvp-implementation-plan.md
```

Suggested commit:

```text
docs: prepare iteration 4 codex adapter work
```

### Task 1 — Contracts, configuration, and exceptions

Create:

```text
AiProvider
StructuredCodexRequest
StructuredCodexResult
ReasoningEffort
CodexRuntimeConfig
CodexPreflight
CodexEnvironment
CodexExecutable
exception classes
```

Write tests first.

Suggested commit:

```text
feat: define structured Codex runtime contracts
```

### Task 2 — Generic process executor

Create:

```text
ProcessSpec
ProcessResult
ProcessExecutor
JdkProcessExecutor
ProcessFixtureMain
JdkProcessExecutorTest
```

Verify stdin, output draining, timeout, and forced termination.

Suggested commit:

```text
feat: add bounded cross-platform process execution
```

### Task 3 — Environment sanitizer and Codex preflight

Create:

```text
CodexProcessEnvironment
CodexEnvironmentChecker
CodexProcessEnvironmentTest
CodexEnvironmentCheckerTest
```

No real Codex calls in unit tests.

Suggested commit:

```text
feat: add Codex installation and authentication preflight
```

### Task 4 — Deterministic Codex command factory

Create:

```text
CodexCommandFactory
CodexCommandFactoryTest
```

Assert the complete argument list.

Suggested commit:

```text
feat: build safe non-interactive Codex commands
```

### Task 5 — Temporary workspace and structured runner

Create:

```text
CodexTemporaryWorkspace
CodexProcessRunner
CodexProcessRunnerTest
```

Use Jackson for schema/final JSON validation.

Suggested commit:

```text
feat: execute schema-constrained Codex runs
```

### Task 6 — Provider and CLI preflight wiring

Create/modify:

```text
CodexCliAiProvider
CodexCliAiProviderTest
StartCommand
StartCommandCodexPreflightTest
CodeDefenseApplication, only if object wiring requires it
```

No live model call from `start`.

Suggested commit:

```text
feat: wire Codex readiness into the start flow
```

### Task 7 — Live smoke scripts and documentation

Create:

```text
CodexLiveSmokeTest
codex-live-smoke.schema.json
live-smoke-test.ps1
live-smoke-test.sh
```

Run once manually after all unit tests pass.

Suggested commit:

```text
test: add opt-in Codex structured execution smoke test
```

### Task 8 — Final verification and checklist

Run:

```bash
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar --version
java -jar target/codedefense.jar start . --dry-run
java -jar target/codedefense.jar start . --yes
```

The last command performs preflight only and must not consume model credits.

Then run the live smoke exactly once:

```powershell
.\scripts\live-smoke-test.ps1
```

Update:

```text
docs/implementation-checklist.md
```

Mark Iteration 4 complete only after the live smoke succeeds.

Suggested commit:

```text
docs: mark iteration 4 complete
```

## 19. Acceptance checklist

### Architecture

- [ ] `StartCommand` does not use `ProcessBuilder`.
- [ ] Domain snapshot records do not import AI/process classes.
- [ ] `AiProvider` is generic and structured.
- [ ] Process execution is behind `ProcessExecutor`.
- [ ] Codex command construction is behind `CodexCommandFactory`.
- [ ] Production prompts are deferred to Iteration 5.

### Security and privacy

- [ ] prompt is written only to stdin;
- [ ] prompt is absent from command arguments;
- [ ] analyzed repository is absent from the temp workspace;
- [ ] `--sandbox read-only`;
- [ ] `--ask-for-approval never`;
- [ ] `--ephemeral`;
- [ ] no `--yolo`;
- [ ] user config is ignored;
- [ ] sensitive environment variables are removed;
- [ ] stdout/stderr/final output are bounded;
- [ ] temp workspace always deleted;
- [ ] exceptions contain no prompt or schema.

### Environment behavior

- [ ] `codex --version` checked;
- [ ] `codex login status` checked;
- [ ] missing Codex gives exit 5;
- [ ] missing login gives exit 6;
- [ ] process/preflight failure gives exit 7;
- [ ] dry-run and declined confirmation perform no Codex check;
- [ ] help/version perform no Codex check.

### Structured execution

- [ ] schema written as temporary UTF-8 file;
- [ ] `--output-schema` passed;
- [ ] `--output-last-message` passed;
- [ ] final JSON exists and is parseable;
- [ ] malformed/empty/missing result rejected;
- [ ] timeout kills process;
- [ ] live smoke returns `status=ok`.

### Verification

- [ ] all default tests pass;
- [ ] no default test calls real Codex;
- [ ] shaded JAR builds;
- [ ] help/version work;
- [ ] dry-run works without Codex;
- [ ] normal confirmed start performs preflight only;
- [ ] opt-in live smoke passes once.

## 20. Pull request

Title:

```text
feat: add structured Codex CLI process adapter
```

Body:

```markdown
## Scope

Implements CodeDefense Iteration 4:

- Codex CLI executable resolution
- local authentication preflight
- bounded cross-platform process execution
- safe deterministic `codex exec` command construction
- ephemeral read-only temporary workspace
- prompt delivery through stdin
- JSON Schema constrained final responses
- timeout and process termination
- bounded diagnostics
- typed Codex failure mapping
- opt-in live structured-execution smoke test

## Verification

- [ ] `mvn clean verify`
- [ ] `mvn package`
- [ ] `java -jar target/codedefense.jar --help`
- [ ] `java -jar target/codedefense.jar --version`
- [ ] `java -jar target/codedefense.jar start . --dry-run`
- [ ] `java -jar target/codedefense.jar start . --yes`
- [ ] `scripts/live-smoke-test.ps1`

## Safety properties

- [ ] prompt is sent through stdin, not command arguments
- [ ] empty workspace contains no analyzed repository
- [ ] read-only sandbox
- [ ] approvals disabled
- [ ] ephemeral Codex session
- [ ] no full-access flags
- [ ] sensitive environment variables removed
- [ ] output and diagnostics bounded
- [ ] temporary files deleted on all paths
- [ ] default tests never call real Codex

## Intentionally out of scope

- project-analysis prompt
- project-analysis JSON Schema
- architecture map
- technical questions
- interview flow
- report generation
```

## 21. Review focus for PR #3

The code review should specifically inspect:

1. shell/argument injection;
2. Windows command resolution;
3. process deadlocks;
4. timeout termination;
5. prompt leakage into args/errors;
6. environment-secret inheritance;
7. temp cleanup;
8. unbounded stdout/stderr/output;
9. accidental model calls in default tests;
10. accidental Iteration 5 scope expansion.
