# CodeDefense JetBrains Plugin and Consented Codex Provenance Design

## Purpose

Iterations 8.11 and 8.12 turn CodeDefense from a terminal-only workflow into
an IntelliJ IDEA product while preserving the CLI as the sole owner of Git
capture, disclosure controls, Codex access, scoring, and Passport persistence.
Iteration 8.12 then adds an optional experiment that compares one explicitly
selected local Codex thread with the exact Git change being defended.

The product claim remains narrow: CodeDefense records an educational human
defense of an exact change. It does not certify authorship, approve a merge,
prove safety, or track employee performance.

## Approved scope

### Iteration 8.11

- Target IntelliJ IDEA Community and Ultimate 2026.1 on Windows.
- Build against IntelliJ Platform branch 261 with Java 21.
- Use IntelliJ Platform Gradle Plugin 2.18.1 and Gradle 9.0.0 in an isolated
  `jetbrains-plugin/` build.
- Provide the complete preview, confirmation, interview, completion, and
  Passport status workflow inside a native Tool Window.
- Bundle the shaded CodeDefense CLI JAR in the plugin distribution and launch
  it as a child JVM.
- Keep the root application Java 21 + Maven. Do not move core logic into the
  Gradle project or place the shaded CLI JAR on the plugin classpath.
- Claim compatibility only for the verified 2026.1 IntelliJ IDEA Community
  and Ultimate builds. Use `sinceBuild = 261` and `untilBuild = 261.*` after
  Plugin Verifier passes both targets.

### Iteration 8.12

- Keep provenance experimental, disabled by default, and protected by the
  `CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE=true` kill switch.
- Require an explicit thread ID and explicit per-run consent.
- Never discover the latest thread or call `thread/list` in product flow.
- Read the selected thread only through `codex app-server` over stdio.
- Require exact normalized hunk equivalence for `EXACT_CHANGE_MATCH`.
- Treat path-only or incomplete overlap as `PARTIAL_PATH_MATCH`.
- Do not change questions, evaluations, local scores, readiness, or Passport
  CURRENT/EXPIRED identity because of provenance.

## Rejected alternatives

### Loading core classes into the plugin

The plugin must not depend directly on CodeDefense application/domain classes.
Doing so would couple Maven and Gradle packaging, expose the plugin to
Jackson/JLine/Picocli classloader conflicts, and create a second runtime wiring
path. A process boundary is more stable and independently testable.

### Terminal-only IDE integration

A Tool Window that merely opens a terminal would be cheaper but would not
provide the intended product experience. It would also make status refresh,
safe cancellation, and structured UI state dependent on parsing terminal text.

### Reading Codex rollout/session files

Iteration 8.12 must not read `~/.codex/sessions`, SQLite, rollout JSONL, desktop
files, or other implementation storage. Those formats are broader than the
approved data boundary and are not a supported integration contract.

### Matching provenance by path alone

The same path can be edited by unrelated tasks. Path-only matching would create
misleading provenance. Exact status therefore requires matching normalized
changed-line evidence for every selected eligible Git path.

## Iteration 8.11 architecture

### Stable local bridge

The core adds a hidden adapter command:

```text
java -jar codedefense.jar bridge prove --protocol 1 <selector> --focus <focus> <project>
```

The child process uses stdout exclusively for newline-delimited JSON. Safe
human diagnostics use stderr. Every request and event contains
`protocolVersion: 1` and a closed `type` discriminator. Unknown versions,
types, duplicate keys, unknown fields, trailing tokens, fractional integer
values, malformed UTF-8, and oversized lines are rejected with content-free
errors.

Bridge requests are:

- `ConfirmRequest`;
- `AnswerRequest`;
- `SkipRequest`;
- `CancelRequest`.

Bridge events are:

- `HelloEvent`;
- `PreviewEvent`;
- `ConfirmationRequiredEvent`;
- `QuestionEvent`;
- `EvaluationEvent`;
- `QuestionScoreEvent`;
- `SummaryEvent`;
- `PassportSavedEvent`;
- `CompletedEvent`;
- `ErrorEvent`.

One encoded line is limited to 256 KiB, aggregate session output to 4 MiB,
and one answer to 8 KiB of UTF-8. Answer-bearing requests and model-controlled
events use content-free `toString()` implementations.

### Reusing the existing workflow

Bridge adapters implement the existing confirmation and interview input/output
ports. `InterviewEngine`, local scoring, exact Git capture, bounded context,
Codex analysis/evaluation, and Passport persistence remain unchanged. The
bridge is another presentation adapter, not another business workflow.

The required event sequence is:

```text
Hello
Preview
ConfirmationRequired
Confirm
(Question -> Answer|Skip -> Evaluation -> QuestionScore) x 3
Summary
PassportSaved
Completed
```

A follow-up is represented by another `QuestionEvent` inside the current
primary category and remains bounded to one. A dry-run emits preview and
`CompletedEvent(codexInvoked=false)` without requesting confirmation.

### Plugin process boundary

The plugin constructs process tokens as a list:

```text
[java.exe, -jar, bundled-codedefense.jar, bridge, prove, ...]
```

It never uses a shell string, `cmd.exe /c`, PowerShell `-Command`, or terminal
text injection. `java.exe` must resolve beneath normalized
`System.getProperty("java.home")` and must be a regular non-symlink file. The
working directory is the normalized IntelliJ project base path.

Stdout and stderr are drained immediately and concurrently away from the EDT.
Bridge events are parsed incrementally. UI changes return to the EDT. The
plugin maintains at most one active defense per IntelliJ project.

On cancel, project disposal, or IDE shutdown, the plugin sends `CancelRequest`,
closes stdin, waits a bounded grace period, invokes `destroy()`, then
`destroyForcibly()` and terminates descendants if the child remains alive.
The plugin never destroys unrelated processes.

### Tool Window experience

The `CodeDefense` Tool Window contains:

- Passport status: `No Passport`, `CURRENT`, or `EXPIRED`;
- change kind, short fingerprint, focus, attempt number, category scores,
  overall score, and readiness;
- staged, commit, and range selector controls;
- balanced, architecture, failure-modes, and testing focus controls;
- `Preview defense`, `Start defense`, `Cancel`, `Refresh`, and
  `Open Passport` actions;
- one question and answer field at a time with an explicit `Skip` action;
- completion state containing local scores and the saved Passport path.

Model/domain text is rendered as plain sanitized text, never HTML. Start is
disabled during an active session. The answer field is cleared immediately
after the request is written, and answer text is not retained in the view
model, logs, notifications, or settings.

Passport refresh calls `passport show <project> --format json`; it does not
read `.codedefense` directly. Refresh occurs on project open, explicit action,
successful defense, and debounced VCS change notifications. `Open Passport`
accepts only a path returned by a successful core event and requires a regular
non-symlink file before opening it through IntelliJ's local file system API.

### Plugin settings

Settings contain only:

- bundled CLI versus an explicit CLI JAR override;
- default selector;
- default focus.

The override must be an existing regular non-symlink `.jar`. The plugin never
stores Codex credentials, thread IDs, consent, prompts, answers, source, or
model output.

## Iteration 8.12 architecture

### Consent and invocation

Direct CLI use requires all of:

```text
--experimental-codex-provenance
--codex-thread <ID>
--consent-codex-history
CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE=true
```

Missing any component fails before Git, Codex, JLine, or app-server startup.
The thread ID is never echoed in help, preview, errors, or logs. The JetBrains
plugin does not put the thread ID in child-process arguments: it sends it in a
content-safe bridge request after startup, clears the UI field immediately,
and never persists the value or consent checkbox.

### App-server transport

The core launches the resolved Codex command prefix followed by
`app-server --stdio`. It performs exactly one `initialize` request and one
`initialized` notification before reading the selected thread. It opts into
the required experimental API capability but starts/resumes no thread and
starts no model turn.

The transport uses bounded numeric request IDs and correlates responses while
ignoring permitted notifications. Limits are:

- 1 MiB per app-server line;
- 8 MiB total received data;
- 1,000 relevant items;
- 100 relevant paths;
- 15 seconds total operation time.

`thread/items/list` is preferred for bounded item pagination. A JSON-RPC
`-32601` unsupported-method response permits one fallback to
`thread/read(includeTurns=true)`. Paginated thread formats that cannot safely
provide full file-change evidence yield `UNAVAILABLE`; the implementation does
not attempt best-effort transcript parsing.

The decoder models only thread ID, cwd, source kind, turn/item identifiers, and
file-change path/status/patch fields needed transiently for matching. Message,
reasoning, shell-command, tool-output, token, prompt, and unrelated item
subtrees are skipped without materializing them as strings.

### Strict change matching

Before comparison, the thread cwd must resolve to the same real path as the
selected repository root. A mismatch yields `NO_MATCH`.

Both Git and Codex evidence use the existing strict unified-hunk and path
policies:

- reject absolute, traversal, NUL, and control-character paths;
- normalize CRLF and lone CR to LF;
- retain add/modify/delete/rename semantics;
- apply the same secret redaction before deriving transient evidence;
- canonicalize status, ordered old/new ranges, and normalized changed lines.

Multiple Codex edits to the same path are reconciled in deterministic item
order. If a final state cannot be derived safely, that path is unmatched.
Same-path/different-content evidence never matches.

Outcomes are:

- `EXACT_CHANGE_MATCH`: every selected eligible Git path has equal final hunk
  evidence and no conflicting Codex path state;
- `PARTIAL_PATH_MATCH`: at least one but not all selected paths match;
- `NO_MATCH`: no eligible path matches or repository roots differ;
- `UNAVAILABLE`: protocol, capability, version, projection, or safe transport
  requirements are not met.

### Persisted provenance summary

The receipt schema advances to v4 and optionally stores only:

- schema version;
- provenance status;
- SHA-256 hash of the thread ID using a domain-separated prefix;
- bounded Codex version;
- selected and matched file counts;
- unique sorted matched relative paths;
- capture timestamp.

Raw thread ID, cwd, transcript timestamps, messages, prompts, reasoning,
commands, tool output, patches, answers, and usernames are excluded. The
summary and every app-server DTO use content-free `toString()` methods.

Provenance is informational. It cannot alter the interview, scores, readiness,
CURRENT/EXPIRED identity, deterministic fallback behavior, or merge/deploy
disclaimer.

## Error and cancellation model

Bridge errors use stable codes for unsupported protocol, invalid selector,
invalid client message, unavailable Codex, authentication failure, timeout,
invalid model response, Git capture failure, persistence failure, and
cancellation. Errors never include request/event payloads or raw stderr.

EOF before disclosure is a decline. EOF during an active interview is a safe
cancellation. Interruption preserves the thread interrupt flag and existing
exit semantics.

For provenance, unsupported capability and safe protocol failures yield
`UNAVAILABLE`; root mismatch and genuine evidence absence yield `NO_MATCH`.
Cancellation/interruption propagates and does not create or update a Passport.
Unexpected runtime defects are not converted into a provenance result.

## Acceptance gates

### Iteration 8.11

- Core bridge codec/session/CLI tests use hand-written fakes and no real Codex.
- Plugin launcher/transport tests cover spaces and Unicode paths, exact token
  preservation, concurrent stdout/stderr, malformed/oversized input, nonzero
  exits, cancellation, process descendants, and no deadlock.
- Controller tests cover preview, consent, three primary questions, one
  follow-up, skip, decline, completion, safe errors, double-start prevention,
  status refresh, and project disposal.
- `mvn clean verify` and `mvn package` pass.
- `gradlew test verifyPlugin buildPlugin` passes.
- Plugin Verifier passes IntelliJ IDEA Community and Ultimate 2026.1.
- The plugin ZIP contains one plugin JAR and one bundled
  `cli/codedefense.jar`, with no core classes duplicated in the plugin JAR.
- A clean sandbox demonstrates lazy startup, project paths with spaces,
  offline Passport refresh, fake bridge interview, cancellation cleanup, and
  no EDT freeze.
- One real plugin defense requires separate explicit authorization after every
  offline check passes.

### Iteration 8.12

- Fake app-server tests cover handshake, fragmented/interleaved JSONL,
  unsupported pagination, invalid UTF-8, oversized limits, timeout, large
  stderr, and safe process cleanup.
- Projection tests prove seeded transcript, command, source, patch, answer, and
  thread-ID markers never appear in DTO strings, errors, logs, Passports,
  handoffs, plugin state, or test reports.
- Matcher tests cover add, modify, delete, rename, repeated edits, line-ending
  differences, redacted secrets, malicious patch headers, large bounds,
  unrelated paths, and same-path/different-content false matches.
- Compatibility fixtures cover the current supported Codex version and one
  previous supported version. Unknown versions yield `UNAVAILABLE`.
- All Maven and plugin verification from Iteration 8.11 remains green.
- One real read of a disposable non-sensitive local thread requires separate
  explicit authorization and is never automatically repeated after failure.
- If exact matching is unreliable on either supported version, Iteration 8.12
  remains disabled research code and is not marked complete.

## Non-goals

- No VS Code integration.
- No Marketplace publishing credentials in this iteration.
- No editor inspections or automatic code changes.
- No merge gate, certification, authorship, safety, or compliance claim.
- No telemetry, cloud sync, dashboard, account, or employee history.
- No automatic thread discovery or transcript collection.
- No Codex thread start, resume, fork, turn start, or command execution.

## Official references

- JetBrains IntelliJ Platform Gradle Plugin 2.x:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- JetBrains build number ranges:
  https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
- JetBrains plugin compatibility:
  https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
- JetBrains Tool Windows:
  https://plugins.jetbrains.com/docs/intellij/tool-windows.html
- OpenAI Codex app-server protocol:
  https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md

## Design self-review

- Iterations remain separable: 8.11 ships without provenance, and 8.12 depends
  only on the stable bridge and machine-readable status boundaries.
- The plugin remains a passive adapter and contains no duplicated core logic.
- The app-server integration reads only a user-selected thread and never
  starts a model turn.
- Exact provenance is based on hunk content, not merely paths.
- Every persisted format remains source-free and versioned.
- No placeholder, automatic discovery, hidden consent, or future iteration is
  included.
