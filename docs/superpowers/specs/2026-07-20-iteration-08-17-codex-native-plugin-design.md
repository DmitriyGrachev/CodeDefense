# Iteration 8.17 Codex-Native Plugin and Passport Hook Design

## Product outcome

Iteration 8.17 makes CodeDefense visible inside the Codex workflow without
moving Git capture, scoring, Passport persistence, or AI orchestration out of
the existing Java core. An installable Codex plugin contributes one reusable
skill and one advisory `Stop` hook. After a Codex turn, the hook reports whether
the exact staged change is `UNDEFENDED`, `CURRENT`, or `EXPIRED`.

This iteration lands immediately before Iteration 9. It is deliberately smaller
than a stateful MCP integration: interactive defense remains in the existing CLI
and IntelliJ surfaces. The plugin adds awareness and a safe entry point, not a
second interview engine.

## User promise

After Codex edits a repository, the user should see a short source-free status:

```text
CodeDefense: UNDEFENDED — 2 staged files, +18/-4.
Run a CodeDefense staged defense before committing.
```

After a successful defense:

```text
CodeDefense: CURRENT — Passport 06ee23eada14, attempt 1.
```

If the staged index changes:

```text
CodeDefense: EXPIRED — the staged change no longer matches its Passport.
Run a new defense for the current staged change.
```

No message is emitted when there is no staged change or the working directory is
not a Git repository. The integration is advisory and never claims that code is
safe, approved, certified, or ready to merge.

## Supported surfaces and platforms

The plugin targets Codex hosts that support installable plugins and lifecycle
hooks:

- ChatGPT desktop Codex;
- Codex CLI;
- the Codex IDE extension when it uses the same local Codex host.

The packaged integration targets:

- Windows through a PowerShell launcher;
- macOS and Linux through a POSIX shell launcher;
- Java 21 supplied by `JAVA_HOME` or the local command path.

Release documentation may claim support for a platform only after executing the
packaged launcher there. Contract inspection alone is not sufficient evidence
that a launcher works.

The Java core remains the portable implementation. Platform scripts are thin,
fixed launchers and contain no Git, Passport, JSON, or policy logic.

## Architecture

```text
Codex turn finishes
        |
        v
plugin Stop hook
        |
        v
fixed platform launcher
        |
        v
codedefense codex-hook status
        |
        v
EvaluateStagedPassportGateUseCase
        |
        +-- Git staged identity
        +-- local Passport receipts
        |
        v
bounded Codex hook response JSON
        |
        v
advisory system message in Codex
```

The hook reuses `EvaluateStagedPassportGateUseCase` and
`StagedPassportGateResult`. It does not parse the output of the public
`passport gate` command in a shell script. A dedicated hidden Picocli adapter
renders the Codex hook response directly, keeping all state mapping and JSON
escaping in Java.

## Repository layout

Source-controlled plugin files live separately from the JetBrains plugin:

```text
.agents/plugins/marketplace.json
plugins/codedefense/
  .codex-plugin/plugin.json
  skills/codedefense/SKILL.md
  hooks/hooks.json
  scripts/codedefense-hook.ps1
  scripts/codedefense-hook.sh
  cli/.gitkeep
scripts/package-codex-plugin.ps1
scripts/package-codex-plugin.sh
```

The shaded `target/codedefense.jar` is copied into
`plugins/codedefense/cli/codedefense.jar` only for local installation or release
packaging. The copied binary is ignored by Git. Release packaging creates a
self-contained plugin archive after `mvn package`; source commits do not embed a
generated JAR.

The repository marketplace contains one local CodeDefense entry with explicit
installation and authentication policy. A clean local installation is tested
through the Codex plugin browser after restarting the host.

## Core hook adapter

The hidden command is:

```text
java -jar codedefense.jar codex-hook status
```

It has no project path argument. Codex runs hook commands with the session
working directory, and the adapter evaluates `.` after normal repository
validation. The command reads no transcript and ignores model/session content
on standard input.

The command owns stdout. Successful stdout is either empty or one strict UTF-8
JSON object followed by LF. Diagnostics use stderr and contain no source,
relative paths, answers, questions, model content, or raw exception messages.

State mapping is fixed:

| Gate state | Hook behavior |
|---|---|
| `NO_STAGED_CHANGE` | Exit 0 with no output. |
| `UNDEFENDED` | Emit an advisory `systemMessage` with file and line counts. |
| `CURRENT` | Emit an informational `systemMessage` with 12-character fingerprint and attempt number. |
| `EXPIRED` | Emit an advisory `systemMessage` with counts and re-defense guidance. |
| `UNAVAILABLE` caused by an invalid repository | Exit 0 with no output. |
| Other `UNAVAILABLE` reasons | Emit one safe availability warning without repository data. |

Every emitted object sets `continue: true`. The hook never asks Codex to repeat
the turn and never blocks completion. Output is capped at 4 KiB even though the
underlying gate output has a larger bound.

## Codex skill

The skill teaches Codex when and how to use CodeDefense without granting it the
role of interview participant. It supports these intents:

- show staged Passport status;
- preview the bounded staged defense with `--dry-run`;
- show the latest source-free Passport score card;
- show repository Learning Radar insights;
- explain `UNDEFENDED`, `CURRENT`, `EXPIRED`, and `UNAVAILABLE`;
- give the user the exact CLI or IntelliJ action for starting a defense.

The skill must not:

- start a source-sending run without explicit user confirmation;
- add `--yes` automatically;
- answer defense questions for the user;
- read a Codex transcript or infer a thread ID;
- expose expected key points, evidence reasons, answers, raw JSON, source, or
  diffs;
- claim that a Passport authorizes merge or deployment;
- modify Git state, commit messages, Passports, or settings unless the user
  separately requests an existing supported CodeDefense action.

Interactive defense remains intentionally external. The skill directs the user
to the IntelliJ Tool Window or to an interactive terminal command so that the
same Codex host that edited the code does not silently answer the defense on the
user's behalf.

## Hook lifecycle and recursion boundary

Only one `Stop` hook is bundled. There is no `PostToolUse` hook because running
Git and Passport inspection after every edit would be noisy and wasteful. There
is no `PreToolUse` commit blocker because Codex hooks are a guardrail rather
than a complete enforcement boundary and shell-command matching cannot cover
all commit paths reliably.

The hook command invokes no `codex exec`, app-server, MCP server, JLine, or
interactive workflow. Therefore the hook cannot recursively trigger a model
request. It performs one bounded staged identity inspection and one bounded
Passport history read.

The plugin remains subject to Codex hook trust. Installation does not bypass
the host's review flow, and documentation requires the user to inspect and
trust the exact bundled hook definition.

## Cross-platform launchers

Both launchers follow the same deterministic resolution policy:

1. Prefer `${JAVA_HOME}/bin/java` or `%JAVA_HOME%\bin\java.exe`.
2. Otherwise use `java`/`java.exe` from the command path.
3. Require the bundled `cli/codedefense.jar` beneath the plugin root.
4. Invoke Java with a fixed token sequence ending in `codex-hook status`.
5. Preserve the Java process exit code.

The PowerShell launcher uses `-NoLogo`, `-NoProfile`, and `-NonInteractive`.
It never uses `Invoke-Expression`, `cmd.exe /c`, or a dynamically assembled
command string. The POSIX launcher passes a fixed sequence of separately quoted
arguments and never calls `eval`. Neither launcher logs arguments, environment
values, stdin, or paths.

## Error handling

- Missing Java or a missing bundled JAR produces one fixed safe stderr
  diagnostic and a nonzero launcher exit without a stack trace. It cannot
  produce hook JSON because the Java adapter is unavailable.
- Git capture and Passport persistence failures use existing typed gate reasons.
- Invalid hook output is treated as a plugin defect; tests require strict JSON
  and bounded UTF-8.
- Hook timeout is 15 seconds. Codex may continue normally after a timeout; no
  automatic retry is configured.
- An interrupted Java process preserves interruption and emits no partial JSON.
- Help and version for the hidden command initialize neither Git nor Codex.

## Privacy and security invariants

- The hook sends no repository source or diff to Codex.
- The hook invokes no model and consumes no Codex credit.
- Hook messages contain at most state, counts, short fingerprint, attempt
  number, and fixed application-owned guidance.
- Relative paths from `EXPIRED` gate results are deliberately omitted from
  model-visible hook messages.
- No hook input, transcript path, session ID, model name, user identity, or
  environment value is persisted.
- Plugin `toString()` and exception messages contain no repository path or
  hook input.
- Existing Passport identity rules remain authoritative; the plugin does not
  reproduce or weaken them.

## Testing strategy

### Core tests

- exact hook JSON for every staged gate state and reason;
- silence for no staged change and invalid repository;
- 12-character fingerprint rendering only for valid current state;
- no relative paths, source markers, answers, questions, or raw gate JSON;
- 4-KiB output limit and LF termination;
- safe error and exit-code mapping;
- help/version isolation;
- command-level fake gate tests proving no Codex boundary is constructed.

### Plugin contract tests

- manifest and marketplace schemas have exact required fields and relative
  paths;
- one skill and one `Stop` hook are bundled;
- hook configuration contains fixed commands and a 15-second timeout;
- Windows and POSIX scripts reject missing Java/JAR safely;
- arguments containing spaces remain separate tokens;
- packaging contains one manifest, one skill, one hook configuration, two
  launchers, and one shaded JAR;
- generated archives and copied JARs are not tracked by Git.

### Offline acceptance

1. Run all Maven tests and package the shaded JAR.
2. Assemble the Codex plugin directory/archive.
3. Install it through the repository marketplace and restart Codex.
4. Review and trust the hook with the Codex hook UI.
5. Confirm no message outside a Git repository and with no staged change.
6. Stage a supported change and observe `UNDEFENDED` after a turn.
7. Run a defense through the existing CLI or IntelliJ integration.
8. Observe `CURRENT` after the next turn.
9. Modify and restage one line, then observe `EXPIRED`.
10. Confirm the hook made no Codex request and left no temporary workspace.
11. Execute the packaged PowerShell launcher on Windows and the packaged POSIX
    launcher on at least one macOS or Linux host before claiming those platform
    families as supported.

No automated test or default acceptance command invokes real Codex. Any final
end-to-end model-backed defense still requires separate explicit authorization.

## Documentation and release

README additions cover:

- what the Codex plugin does and does not do;
- Java 21 and supported operating systems;
- local marketplace installation;
- hook review/trust;
- source-free status behavior;
- how to start the actual defense;
- uninstall/disable steps;
- privacy, advisory, and no-merge-approval boundaries.

The implementation checklist adds Iteration 8.17 after 8.16 and before 9. It
remains unchecked until the packaged plugin is installed and the
`UNDEFENDED -> CURRENT -> EXPIRED` acceptance sequence succeeds.

## Explicit non-goals

- no MCP server;
- no Apps SDK or web UI;
- no defense inside the Codex chat;
- no automatic answer generation;
- no automatic source disclosure or confirmation bypass;
- no hard commit block;
- no new Passport schema;
- no score or readiness changes;
- no new AI provider or OpenAI API key;
- no GitHub, CI, cloud, telemetry, account, or database integration;
- no Evidence Coverage Map or Delta Defense;
- no Iteration 9 release work.

## Acceptance criteria

Iteration 8.17 is complete only when:

- the plugin is discoverable through the local repository marketplace;
- Codex requires explicit trust for the bundled hook;
- Windows and POSIX packaging paths are contract-tested and executed on their
  respective platform families before support is claimed;
- `NO_STAGED_CHANGE` and invalid repositories are silent;
- staged changes show deterministic `UNDEFENDED`, `CURRENT`, or `EXPIRED`
  guidance after a Codex turn;
- the exact current Passport fingerprint is shortened to 12 lowercase hex
  characters without exposing paths;
- the hook never invokes Codex, sends source, starts JLine, or consumes credit;
- existing CLI and IntelliJ workflows remain unchanged;
- `mvn clean verify` and packaging verification pass;
- an installed-plugin offline acceptance confirms the full status transition;
- Iteration 9 has not begun.

## Official extension references

- Codex plugins: https://learn.chatgpt.com/docs/build-plugins
- Codex hooks: https://learn.chatgpt.com/docs/hooks
- Codex skills: https://learn.chatgpt.com/docs/build-skills
- Codex plugin installation: https://learn.chatgpt.com/docs/plugins

## Design self-review

- The plugin reuses the existing staged gate instead of duplicating Git or
  Passport logic.
- Hook output is source-free, bounded, deterministic, and advisory.
- Platform launchers contain no domain decisions.
- The design does not depend on MCP, app-server, a web UI, or a new dependency.
- Interactive defense remains isolated from the Codex host that edited the
  code.
- Iteration 8.17 is independently releasable and leaves Iteration 9 untouched.
