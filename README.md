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
java -jar target/codedefense.jar sample --dry-run
java -jar target/codedefense.jar report
git add src/Example.java
java -jar target/codedefense.jar prove --staged .
java -jar target/codedefense.jar passport --verify .
```

`--dry-run` scans and previews the bounded snapshot without sending source content, invoking Codex, initializing the interactive terminal, or consuming credits. `--yes` bypasses confirmation and starts the structured analysis and interview through the locally authenticated Codex CLI. These requests consume Codex credits.

## Embedded sample project

Use the built-in sample to explore the complete workflow without selecting a repository path:

```powershell
java -jar target/codedefense.jar sample --dry-run
java -jar target/codedefense.jar sample
java -jar target/codedefense.jar sample --yes
```

`sample --dry-run` extracts the built-in project into a temporary directory, uses the same scanner and bounded snapshot preview as `start`, then removes that directory. It does not send source content, initialize JLine, invoke Codex, consume credits, or create a report. `sample` retains the normal preview and confirmation; `sample --yes` bypasses only that confirmation and can consume Codex credits. Both normal modes require the locally installed and authenticated Codex CLI described above.

The sample uses the same analysis, interview, and report pipeline as a local project. It is handled only as text: CodeDefense does not compile or execute any sample source. After any terminal path, its extracted workspace is removed. A completed normal run stores its report in the regular local report location, not beside the temporary sample.

The interactive defense asks exactly three repository-specific primary questions and may ask at most one focused follow-up for each. Entering exactly `skip` (case-insensitively) skips that turn locally without an evaluation request. Blank or overlong answers are rejected locally. Ctrl+C or end-of-input cancels safely with no report generated.

Codex evaluates answer quality, but CodeDefense computes question scores, the rounded overall score, skipped-primary count, and readiness classification locally. Those local values are authoritative: the report narrative cannot replace or alter them. A complete run makes at most eight model requests: one project analysis, up to six answer evaluations, and one report-narrative request. Question prompts and evidence locations are displayed; internal expected key points, evidence reasons, raw model JSON, and model internals are not.

## Understanding reports

After a successful analysis and completed interview, CodeDefense creates an Understanding Report in Markdown. It writes reports beneath the current user's home directory at:

```text
<user.home>/.codedefense/reports/
```

The file `<user.home>/.codedefense/latest-report.txt` is a local pointer to the most recently saved report. Run the following to print that report:

```powershell
java -jar target/codedefense.jar report
```

`report` is local-only: it does not invoke Codex and does not initialize JLine. If no completed report is available, it prints a safe explanatory message and exits successfully. Corrupt, unreadable, or unsafe report persistence data is reported safely with exit code `9`.

Report generation sends only the bounded report payload needed for the narrative: validated project metadata and overview, question prompts, evaluation concepts, and local scores/readiness. It does not send repository source or snapshots, user answers, expected key points, evidence metadata or reasons, prompt templates, schemas, or raw model JSON. The saved Markdown report includes answers only in escaped text fences; it never includes the source snapshot, expected key points, evidence reasons, raw model JSON, templates, schemas, or temporary paths.

If the report-narrative request fails for any non-cancellation Codex error, CodeDefense still creates and saves a deterministic local fallback report. Cancellation is preserved and no report is generated. A persistence failure also exits with code `9`.

For PowerShell scripts that read a report file, use explicit UTF-8 decoding:

```powershell
Get-Content -LiteralPath "$HOME\.codedefense\latest-report.txt" -Encoding utf8
```

## Staged Change Passports

`prove --staged` is a separate, opt-in defense mode for the exact Git staged index. Stage the change you want to defend, then run:

```powershell
git add src/Example.java
java -jar target/codedefense.jar prove --staged .
java -jar target/codedefense.jar passport --verify .
```

CodeDefense reads staged Git objects and ignores unstaged working-tree content. It previews the bounded, redacted staged context before sending anything and requires explicit confirmation. `prove --staged --dry-run` sends no source content and invokes no Codex.

The staged defense asks exactly three categories of question: **Decision**, **Counterfactual**, and **Test prediction**. The existing local scoring and readiness calculation apply. A completed run stores a Markdown-only Change Passport under `<user.home>/.codedefense/change-passports/`; `<user.home>/.codedefense/latest-change-passport.txt` points to the latest artifact. Its fixed Codex session-link state is `NOT_REQUESTED`.

`passport --verify .` is read-only: it does not modify the working tree, Git index, passport artifact, or latest pointer. It derives a deterministic index identity from the base commit and raw staged-index entries without materializing a Git tree object. It reports `CURRENT` when the saved passport matches that identity and `EXPIRED` when it does not. If the staged index changes during the interview, the pre-save recapture stores an `EXPIRED` artifact.

Passports and proof output exclude staged source, diffs, blobs, answers, raw model JSON, expected key points, and evidence reasons. They are educational only and are never approval to merge or deploy. This mode does not add an application server or session matching, HTML or JSON reports, a Skill, browser integration, GitHub/PR/CI/signing/cloud/dashboard integration.

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

Iterations 0-3 provide the executable CLI, deterministic local discovery, and privacy-aware bounded snapshots. Iteration 4 provides Codex preflight, safe structured process execution, and the opt-in live smoke test. Iteration 5 adds structured project analysis and a safe terminal overview. Iteration 6 adds the adaptive three-question interview and local scoring. Iteration 7 adds Markdown Understanding Reports, local report persistence, deterministic fallback, and the `report` command. Iteration 8 adds the embedded sample project and shared `start`/`sample` workflow. Iteration 8.5 adds the staged Change Passport core; it remains unchecked pending a real acceptance run. Iteration 9 remains future work.

See [the implementation plan](docs/codedefense-mvp-implementation-plan.md) and [the iteration checklist](docs/implementation-checklist.md).
