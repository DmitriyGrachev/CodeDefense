# CodeDefense

CodeDefense is a Java 21 command-line application that helps developers prove they understand an AI-assisted local codebase. It creates a privacy-aware, bounded repository snapshot and uses the locally authenticated Codex CLI for the technical-defense workflow.

## Requirements

- Java 21
- Maven
- Codex CLI installed locally and authenticated with `codex login`

CodeDefense does not require or use an OpenAI API key. It analyzes local directories only: it does not ingest GitHub URLs, execute analyzed source code, use a web interface, or use a database.

Confirm the Codex prerequisite before a normal run:

```powershell
codex --version
codex login
```

## Build and run

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar start . --dry-run
java -jar target/codedefense.jar start . --yes
```

`--dry-run` scans and previews the bounded snapshot without sending source content, invoking Codex, initializing the interactive terminal, or consuming credits. `--yes` bypasses confirmation and starts the structured analysis and interview through the locally authenticated Codex CLI. These requests consume Codex credits.

The interactive defense asks exactly three repository-specific primary questions and may ask at most one focused follow-up for each. Entering exactly `skip` (case-insensitively) skips that turn locally without an evaluation request. Blank or overlong answers are rejected locally. Ctrl+C or end-of-input cancels safely with no report generated.

Codex evaluates answer quality, but CodeDefense computes question scores, the rounded overall score, skipped-primary count, and readiness classification locally. A complete run makes at most seven model requests: one project analysis and up to six answer evaluations. Question prompts and evidence locations are displayed; internal expected key points, evidence reasons, raw model JSON, and model internals are not. Iteration 6 keeps the completed session only in memory—report generation and persistence remain deferred to Iteration 7.

## Privacy model

CodeDefense selects at most 30 files and limits the snapshot to 120 KiB. It previews the selected relative paths before source content can be sent, excludes known secret and generated files, avoids symbolic links, and redacts common secret assignments as defense in depth. Repository content is treated as untrusted data: instructions found in source files, READMEs, comments, configuration, or generated text are not followed. Review the preview before confirming: redaction is not a guarantee that a repository contains no sensitive material.

## Codex launcher support

- **Windows native installation:** `codex.exe` available through `PATH`.
- **Windows npm installation:** `codex.ps1` available through `PATH`, launched through Windows PowerShell with `-File`.
- **Linux/macOS:** native `codex` available through `PATH`.

On Windows, CodeDefense never directly launches `codex.cmd` or the extensionless npm Unix shim, and it does not use `cmd.exe /c` or PowerShell `-Command`.

## Credits and opt-in live smoke

Credits are consumed only by real structured `codex exec` requests. The default Maven suite never calls Codex. The Iteration 4 live smoke test is opt-in, submits one small schema-constrained request, and consumes a small amount of Codex credit:

```powershell
.\scripts\live-smoke-test.ps1
```

```bash
./scripts/live-smoke-test.sh
```

The scripts show the resolved launcher, verify installation and authentication, then run only `CodexLiveSmokeTest` with `-Dcodedefense.live.codex=true`.

## Current status

Iterations 0-3 provide the executable CLI, deterministic local discovery, and privacy-aware bounded snapshots. Iteration 4 provides Codex preflight, safe structured process execution, and the opt-in live smoke test. Iteration 5 adds structured project analysis and a safe terminal overview. Iteration 6 adds the adaptive three-question interview, bounded answer evaluation, and local scoring; persisted reports remain out of scope until Iteration 7.

See [the implementation plan](docs/codedefense-mvp-implementation-plan.md) and [the iteration checklist](docs/implementation-checklist.md).
