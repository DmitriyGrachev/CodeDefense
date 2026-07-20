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
java -jar target/codedefense.jar prove --commit HEAD .
java -jar target/codedefense.jar prove --range main...HEAD --focus failure-modes .
java -jar target/codedefense.jar passport --verify .
java -jar target/codedefense.jar passport show .
java -jar target/codedefense.jar passport list . --limit 10
java -jar target/codedefense.jar passport export . --format json --output passport.json
java -jar target/codedefense.jar passport timeline .
java -jar target/codedefense.jar passport handoff create . --output change.cdhandoff.json
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

## Git Change Passports

`prove` is a separate, opt-in defense mode for an exact staged index, resolved commit, or merge-base range:

```powershell
git add src/Example.java
java -jar target/codedefense.jar prove --staged .
java -jar target/codedefense.jar prove --commit HEAD .
java -jar target/codedefense.jar prove --range main...HEAD --focus testing .
java -jar target/codedefense.jar passport show .
java -jar target/codedefense.jar passport verify .
java -jar target/codedefense.jar passport gate --staged --format json .
java -jar target/codedefense.jar passport list . --limit 10
java -jar target/codedefense.jar passport export . --format json --output passport.json
java -jar target/codedefense.jar passport timeline .
java -jar target/codedefense.jar passport insights . --format json --limit 20
java -jar target/codedefense.jar passport show . --format json
```

User-supplied commit and range refs are resolved once to immutable commit IDs before any diff capture. CodeDefense then uses bounded unified hunks, literal pathspecs, disabled external diff/textconv, and no shell command strings. It ignores unstaged working-tree content, previews the bounded redacted context, and requires explicit confirmation. Every `prove ... --dry-run` sends no source content and invokes no Codex.

`--focus` is a closed educational emphasis with four values: `balanced`, `architecture`, `failure-modes`, and `testing`. It changes the trusted analysis instruction, not Git capture, source budgets, the three required question categories, scoring, or follow-up limits. Focus is not a security, coverage, or approval claim.

The staged defense asks exactly three categories of question: **Decision**, **Counterfactual**, and **Test prediction**. The existing local scoring and readiness calculation apply. A completed run stores paired Markdown and versioned JSON receipt files under `<user.home>/.codedefense/change-passports/`; `<user.home>/.codedefense/latest-change-passport.txt` points to the latest Markdown artifact. The receipt is the strict machine-readable source of truth, while Markdown remains the human-readable view. Both retain only change metadata and structured verdict/score facts; model-generated questions, feedback, concepts, and user answers remain terminal-only.

`passport verify .` and the compatible legacy spelling `passport --verify .` are read-only: they do not modify the working tree, Git index, Passport artifact, or latest pointer. `passport show` displays the latest source-free score card; `passport show --format json` exposes a stable local adapter boundary; `passport list` shows recent receipts; and `passport export --format json` copies the exact validated receipt without overwriting an existing file. `passport timeline` groups up to 20 complete three-category attempts by exact diff fingerprint. `prove --retry ATTEMPT_ID` verifies that identity before starting a fresh full defense. Previous artifacts are never rewritten.

`passport insights . --format json --limit 20` aggregates only validated receipts for the current repository identity. It reports the three Java-owned category averages, the number of attempts and distinct defended changes, the strongest and practice-next categories, and at most ten recent overall scores in chronological order. It reads no Passport Markdown, creates no analytics database, sends no source, and never invokes Codex. Its bounded deterministic JSON omits project names, roots, timestamps, paths, questions, answers, feedback, evidence, model data, and user identity.

Portable handoffs use `passport handoff create`, `inspect`, and `match`. A `.cdhandoff.json` contains at most 20 source-free attempt summaries and a SHA-256 corruption checksum. Inspect is Git-free; match is Codex-free and compares the package with a separately captured local change. Imported handoffs never enter the trusted local Passport store. Integrity means only that bytes match the checksum: it is not a signature, identity, authorship, certification, or trust claim.

Staged source context is built from bounded unified hunks for at most 30 deterministically prioritized supported files. Repository paths are passed to Git as literal pathspecs, and HEAD/index identity is checked again after initial capture; if it changed, CodeDefense aborts and asks you to retry. Exact renames retain both old and new paths. A pure rename with no changed source lines is intentionally not enough to start a defense, and unchanged whole-file content is not sent as an artificial addition.

Passports and proof output exclude staged source, diffs, blobs, answers, raw model JSON, expected key points, and evidence reasons. JSON receipts are educational records, not approval to merge or deploy. The optional experiment below adds narrowly bounded local app-server matching; the default proof mode still performs no session matching. Neither mode adds browser integration, GitHub/PR/CI/signing/cloud/dashboard integration.

### Live staged Passport gate

`passport gate --staged --format json .` is a read-only, staged-index-only status check. It returns exactly one of five states: `NO_STAGED_CHANGE` when the index has no entries; `UNDEFENDED` when the exact repository has no staged Passport history; `CURRENT` only when a staged receipt matches the full repository, change kind, base commit, index identity, and diff fingerprint; `EXPIRED` when staged history exists but that full identity changed; or `UNAVAILABLE` when Git capture, repository validation, or Passport storage cannot be read safely. A matching fingerprint alone is not enough for `CURRENT`.

The IntelliJ Tool Window refreshes this badge from project-open, Tool Window visibility, Git repository, `.git/index`, Passport-save, manual-refresh, and application-activation signals. Signals are debounced for 750 ms. Cached status is display-only: every supported staged-index commit callback requests a mandatory fresh check with a bounded timeout before deciding.

The commit integration is advisory. A non-`CURRENT` or unavailable fresh result offers `Defend change`, `Commit anyway`, or `Cancel`; `Commit anyway` applies only to that callback and is not persisted. CodeDefense installs no Git hook and does not hard-block commits. IntelliJ changelist or other non-index commit modes receive a separate unsupported-mode warning with `Commit anyway` or `Cancel`; they are not claimed as verified by the staged gate.

Gate checks launch only the local metadata adapter. Its deterministic JSON is capped at 256 KiB and contains state/reason, fingerprint, attempt and line/file counts, plus at most 30 relative paths for `EXPIRED`; it contains no source, diffs, questions, answers, feedback, or model output. Background and pre-commit gate checks never invoke Codex, start a defense, or consume Codex credits.

## Experimental consented Codex provenance

Iteration 8.12 can compare the exact defended Git hunks with file-change items from one local Codex thread selected by the user. It is disabled by default and requires a process-level kill switch plus three explicit per-run options:

```powershell
$env:CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE = "true"
java -jar target/codedefense.jar prove --staged . `
  --experimental-codex-provenance `
  --codex-thread <THREAD_ID> `
  --consent-codex-history `
  --dry-run
```

CodeDefense launches the installed Codex app-server over local stdio, performs the documented initialize/initialized handshake, and reads only the named thread. It never calls `thread/list`, guesses a recent thread, starts or resumes a turn, sends a model request, or reads Codex session/rollout files directly. The operation is bounded to 15 seconds, 1 MiB per JSONL line, 8 MiB total input, 1,000 relevant items, and 100 relevant paths.

Thread messages, reasoning, commands, tool output, patches, prompts, answers, the raw thread ID, and the thread working directory are transient and are never written to the Passport or receipt. Schema-v4 receipts retain only a domain-validated status, an opaque salted SHA-256 thread identity, the compatible Codex version, selected/matched counts, matched relative paths, and capture time. Older receipts remain schema compatible without provenance.

`Exact change match` means only that normalized, secret-redacted file-change evidence in the selected thread is consistent with every eligible defended Git hunk. `Partial path match`, `No match`, and `Unavailable` are informational. No provenance status changes questions, model evaluation, Java-owned scores, readiness, Passport validity, or CURRENT/EXPIRED identity. A match does not prove authorship, exclusive causation, review quality, safety, or that no later human edit occurred.

The IntelliJ plugin exposes the experiment only when it inherits the kill switch. A thread ID and consent are held for one run, sent in a bounded bridge request only after the core advertises `codexProvenanceV1`, and cleared immediately. The ID never appears in child-process arguments, plugin settings, notifications, or logs.

Offline fixture compatibility is recorded in [the app-server compatibility matrix](docs/codex-app-server-compatibility.md). A real local thread read is a separate opt-in acceptance gate and is never run by Maven or Gradle tests.

## Privacy model

CodeDefense selects at most 30 files and limits the snapshot to 120 KiB. It previews the selected relative paths before source content can be sent, excludes known secret and generated files, avoids symbolic links, and redacts common secret assignments as defense in depth. Repository content is treated as untrusted data: instructions found in source files, READMEs, comments, configuration, or generated text are not followed. Review the preview before confirming: redaction is not a guarantee that a repository contains no sensitive material.

## Codex launcher support

- **Windows native installation:** `codex.exe` available through `PATH`.
- **Windows npm installation:** `codex.ps1` available through `PATH`, launched through Windows PowerShell with `-File`.
- **Linux/macOS:** native `codex` available through `PATH`.

On Windows, CodeDefense never directly launches `codex.cmd` or the extensionless npm Unix shim, and it does not use `cmd.exe /c` or PowerShell `-Command`.

## IntelliJ IDEA plugin

Iteration 8.11 adds a Windows-first CodeDefense Tool Window for IntelliJ IDEA 2026.1 Community and Ultimate modes. The plugin is a passive adapter: it launches the bundled shaded CLI JAR as a child Java process and exchanges bounded, versioned NDJSON over stdin/stdout. Git capture, privacy filtering, Codex access, question generation, scoring, and Passport persistence remain in the CLI. The plugin does not read project source, run Git itself, parse Passport Markdown, or place answers and model text in IDE logs or settings.

Iteration 8.14 extends that bridge with protocol 2 evidence navigation. Primary questions carry only one to ten validated portable relative paths and line ranges; follow-ups carry no new evidence. The bridge never includes source snippets, evidence reasons, expected key points, or absolute paths. Protocol 1 remains compatible, and an override CLI that cleanly rejects protocol 2 before any valid bridge event or possible Codex invocation is retried once with protocol 1. There is no fallback after a bridge event, confirmation, question, or any other point where source could have been sent.

Evidence links are resolved against the real project root before IntelliJ sees the file. Absolute paths, parent traversal, control characters, missing or unreadable files, directories, final symlinks, intermediate symlinks, paths outside the real root, and stale line ranges are not opened. IntelliJ resolves the `VirtualFile` only after those checks and opens a valid location at its cited line without reading or displaying source through the bridge.

The Tool Window also renders a repository-local Learning Radar from the source-free `passport insights` adapter: accessible bars for Decision, Counterfactual, and Test prediction, a recent overall-score trend, and a practice-next label. It loads in the background only when the Tool Window opens, after manual Refresh, or after a Passport is saved; ordinary Git/VFS gate signals do not start an insights read. Stale generations are discarded, unavailable results clear old scores, and the panel receives no source, paths, questions, answers, feedback, evidence, model data, or user identity.

Build the core artifact before the isolated plugin build:

```powershell
mvn clean package
cd jetbrains-plugin
.\gradlew.bat test verifyPlugin buildPlugin
```

The plugin ZIP is written beneath `jetbrains-plugin/build/distributions/` and contains the plugin JAR plus `cli/codedefense.jar`. Install it through **Settings → Plugins → Install Plugin from Disk**, then open **View → Tool Windows → CodeDefense**. The Tool Window supports staged, commit, and range selectors; the four closed defense focus values; dry preview; explicit source confirmation; one-question-at-a-time answer/skip controls; cancellation; source-free Passport status; safe path-and-line evidence links; and opening only the exact regular non-symlink Passport path returned by a successful core event.

Plugin settings contain only the bundled/override CLI choice, an optional validated JAR path, the default selector, and the default focus. They never contain Codex credentials, prompts, source, questions, or answers. The plugin targets IntelliJ IDEA builds `261.*` and `262.*` on Windows and declares the bundled `Git4Idea` dependency. Its staged refresh and advisory commit integration use public IntelliJ Platform and Git4Idea APIs; it does not claim support for other JetBrains products or operating systems.

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

Iterations 0-8.10 provide the executable local defense workflow, bounded Codex adapter, adaptive interview, reports, embedded sample, Git Change Passports, command center, commit/range capture, change-scoped attempt timelines, portable source-free handoffs, defense focus modes, and machine-readable local status. Iteration 8.11 adds the IntelliJ IDEA adapter. Iteration 8.12 is implemented behind an off-by-default kill switch and remains unchecked until a separately authorized real-thread acceptance read. Iteration 8.13 adds the source-free live staged Passport badge and advisory IntelliJ commit check. Iterations 8.14 and 8.15 are implemented and offline-verified, but remain pending exact Plugin Verifier and installed-plugin acceptance. Iteration 8.16 and final Iteration 9 remain future work.

See [the implementation plan](docs/codedefense-mvp-implementation-plan.md) and [the iteration checklist](docs/implementation-checklist.md).
