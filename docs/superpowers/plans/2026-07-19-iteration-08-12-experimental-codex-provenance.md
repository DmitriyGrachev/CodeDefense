# Iteration 8.12 Experimental Consented Codex Provenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With explicit per-run consent, compare one user-selected local Codex thread's file-change metadata with the exact Git change being defended and record only a source-free match summary.

**Architecture:** Launch the installed `codex app-server` over stdio through the existing executable resolver and a new bounded long-lived JSONL client. Perform the required initialize/initialized handshake, read only the explicitly named thread, extract bounded file-change items in memory, and compare their normalized path/patch evidence with the already captured Git change. Persist only a typed provenance summary. Never persist or render thread messages, reasoning, commands, tool output, patches, prompts, or answers. This feature is experimental, off by default, and must not affect interview scores or Passport validity.

**Honesty boundary:** A match means only that the selected local Codex thread contains file-change evidence consistent with all or part of the selected Git change. It does not prove authorship, exclusive causation, review quality, safety, or that no later human edit occurred.

**Tech Stack:** Java 21, Maven, existing Jackson/process/Codex executable adapters, JUnit 5; optional presentation in the Iteration 8.11 JetBrains plugin. No new dependency, direct rollout-file scraping, database, cloud call, or automatic thread discovery.

## Global Constraints

- Feature requires all three: `--experimental-codex-provenance`, `--codex-thread <ID>`, and `--consent-codex-history`.
- The thread ID is supplied explicitly. Do not call `thread/list` in product flow and do not guess the latest thread.
- Require `CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE=true` as a kill-switch enablement in addition to CLI consent.
- Use app-server stdio only. Do not use websocket/socket transports or read `~/.codex/sessions`, SQLite, rollout JSONL, or desktop files.
- Cap each app-server line at 1 MiB, total received data at 8 MiB, relevant items at 1,000, relevant paths at 100, and the operation at 15 seconds.
- Thread-controlled content must never enter `toString()`, logs, exceptions, terminal diagnostics, Passport prose, JSON receipts, or JetBrains notifications.
- `CodexInterruptedException`/cancellation propagates. Unsupported capability, no match, or safe protocol failure reports `UNAVAILABLE`/`NO_MATCH` and does not change scores.
- Default tests use a fake app-server process and never invoke real Codex.
- Direct CLI use may carry the explicitly supplied thread ID in `--codex-thread`. The JetBrains adapter must never put the thread ID in child-process arguments; it sends one ephemeral capability-gated bridge request after startup.

---

### Task 1: Model narrow provenance outcomes

**Files:**
- Create: `src/main/java/dev/codedefense/domain/CodexProvenanceStatus.java`
- Create: `src/main/java/dev/codedefense/domain/CodexProvenanceSummary.java`
- Create: `src/test/java/dev/codedefense/domain/CodexProvenanceSummaryTest.java`

```java
public enum CodexProvenanceStatus {
    EXACT_CHANGE_MATCH,
    PARTIAL_PATH_MATCH,
    NO_MATCH,
    UNAVAILABLE
}

public record CodexProvenanceSummary(
        int schemaVersion,
        CodexProvenanceStatus status,
        String threadIdentityHash,
        String codexVersion,
        int selectedFileCount,
        int matchedFileCount,
        List<String> matchedRelativePaths,
        Instant capturedAt) { }
```

- [ ] Write tests for schema version 1, lowercase SHA-256 thread identity, semantic version bounds, safe relative unique sorted paths, count consistency, immutable lists, and a content-free `toString()`.
- [ ] `EXACT_CHANGE_MATCH` requires every selected Git-change path to match and equal selected/matched counts. `PARTIAL_PATH_MATCH` requires `0 < matched < selected`. `NO_MATCH` has zero paths. `UNAVAILABLE` stores no thread hash, version, or paths.
- [ ] Do not store raw thread IDs, cwd, transcript timestamps, prompts, messages, commands, patches, usernames, or absolute paths.

### Task 2: Add bounded app-server process transport

**Files:**
- Create: `src/main/java/dev/codedefense/ai/CodexAppServerClient.java`
- Create: `src/main/java/dev/codedefense/ai/JdkCodexAppServerClient.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerRequest.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerResponse.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerProtocolException.java`
- Create: `src/test/java/dev/codedefense/ai/AppServerFixtureMain.java`
- Create: `src/test/java/dev/codedefense/ai/JdkCodexAppServerClientTest.java`

```java
public interface CodexAppServerClient extends AutoCloseable {
    void initialize(AppServerClientInfo clientInfo, boolean experimentalApi);
    AppServerThread readThread(String threadId, boolean includeTurns);
    List<AppServerThreadItem> listThreadItems(String threadId, int limit);
    @Override void close();
}
```

- [ ] Launch exact tokens `<CodexExecutable.commandPrefix()> app-server --stdio`. Reuse resolver semantics for native launchers and Windows `codex.ps1`; never shell-wrap.
- [ ] Start stdout/stderr drainers immediately on virtual threads. Write newline-delimited request objects and correlate bounded numeric IDs with responses while ignoring permitted notifications.
- [ ] Send `initialize` with client name/title/version and `capabilities.experimentalApi=true`, then send `initialized` before any thread request, matching the official protocol.
- [ ] Reject pre-handshake data, duplicate response IDs, malformed JSON, unknown required fields, oversized lines/totals, truncation, EOF, timeout, and nonzero exit with one safe error that contains no server payload.
- [ ] On close/timeout/interruption, perform bounded graceful then forcible process-tree termination and preserve the caller's interrupt flag.
- [ ] Fixture modes cover fragmented lines, interleaved notifications, large stderr, unsupported `thread/items/list` (`-32601`), timeout, invalid UTF-8, and secrets in payloads/exceptions.

### Task 3: Decode only bounded thread/file-change projections

**Files:**
- Create: `src/main/java/dev/codedefense/ai/AppServerThread.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerThreadItem.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerFileChange.java`
- Create: `src/main/java/dev/codedefense/ai/AppServerProjectionCodec.java`
- Create: `src/test/java/dev/codedefense/ai/AppServerProjectionCodecTest.java`

- [ ] Create local DTOs for only: thread ID, cwd, source kind, turn/item IDs, file-change item type, relative path, change kind, and bounded patch text needed transiently for matching.
- [ ] Do not model or retain user messages, assistant messages, reasoning, shell command output, tokens, or tool parameters. Configure Jackson to skip those subtrees without copying them into strings.
- [ ] If `thread/items/list` is supported, page relevant items to the 1,000-item bound. If it returns method-not-supported, fall back once to `thread/read(includeTurns=true)` and stream the same narrow projection.
- [ ] Require thread cwd real-path equality with the selected repository root after platform-aware normalization. A mismatch produces `NO_MATCH` before patch comparison.
- [ ] Override every DTO `toString()` to report only safe types/counts/lengths.
- [ ] Tests seed transcript/command/source secrets and assert none appear in exceptions, logs, or DTO string forms.

### Task 4: Match normalized Codex evidence to captured Git hunks

**Files:**
- Create: `src/main/java/dev/codedefense/provenance/CodexChangeMatcher.java`
- Create: `src/main/java/dev/codedefense/provenance/NormalizedChangeEvidence.java`
- Create: `src/main/java/dev/codedefense/provenance/ProvenanceMatch.java`
- Create: `src/test/java/dev/codedefense/provenance/CodexChangeMatcherTest.java`

```java
public interface CodexChangeMatcher {
    ProvenanceMatch match(CapturedGitChange gitChange,
            List<AppServerFileChange> codexChanges);
}
```

- [ ] Normalize paths using the same Git path policy, reject absolute/traversal/NUL/control paths, normalize CRLF/lone CR to LF, and parse patch hunks with the existing strict unified-hunk parser rather than regexing arbitrary transcript text.
- [ ] Canonical per-path evidence is SHA-256 over status plus ordered old/new ranges and normalized changed lines after the same secret redaction used for disclosure. Hashes are in memory only.
- [ ] Collapse multiple Codex edits to one path deterministically by ordered item ID and require the final normalized changed-line multiset to equal the selected Git evidence for an exact path match. If intermediate edits cannot be reconciled safely, treat the path as unmatched.
- [ ] `EXACT_CHANGE_MATCH` requires all selected eligible Git paths and no conflicting Codex path state. Partial overlap is informational only.
- [ ] Cover add/modify/delete/rename, repeated edits, line-ending differences, redacted secrets, large change bounds, unrelated thread paths, malicious patch headers, and same-path/different-content false matches.

### Task 5: Implement consented capture orchestration

**Files:**
- Create: `src/main/java/dev/codedefense/provenance/CodexProvenanceService.java`
- Create: `src/main/java/dev/codedefense/provenance/DefaultCodexProvenanceService.java`
- Create: `src/main/java/dev/codedefense/provenance/CodexProvenanceConfig.java`
- Create: `src/test/java/dev/codedefense/provenance/DefaultCodexProvenanceServiceTest.java`

```java
public interface CodexProvenanceService {
    CodexProvenanceSummary capture(Path repository,
            CapturedGitChange change, String threadId);
}
```

- [ ] With fakes, assert order: verify kill switch -> validate explicit thread ID -> resolve/check Codex -> start app-server -> initialize -> read only named thread -> match -> hash thread ID -> close/clean process.
- [ ] Use SHA-256 over `codedefense-thread-v1\0` plus UTF-8 thread ID for the stored opaque identity.
- [ ] An unavailable CLI, unsupported method, invalid projection, no history, or protocol mismatch returns `UNAVAILABLE` with one safe reason rendered transiently; it never falls back to filesystem scraping.
- [ ] Interruption/cancellation propagates and leaves no Passport update. Unexpected runtime bugs are not converted to a false provenance result.
- [ ] The service makes no model request and starts no Codex turn.

### Task 6: Integrate without affecting defense or scoring

**Files:**
- Modify: `src/main/java/dev/codedefense/application/DefaultGitChangeDefenseRunner.java`
- Modify: `src/main/java/dev/codedefense/domain/PassportReceipt.java`
- Modify: `src/main/java/dev/codedefense/passport/PassportReceiptJsonCodec.java`
- Modify: `src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java`
- Modify: focused runner/receipt/renderer tests

- [ ] Capture provenance after exact Git capture but before disclosure confirmation so preview can show outcome; do not send source to the model during provenance capture.
- [ ] Preview must state `Experimental Codex provenance: Exact change match/Partial path match/No match/Unavailable` plus the authorship disclaimer.
- [ ] Continue the normal defense for every non-cancellation status. Provenance cannot change questions, evaluations, category scores, overall score, readiness, CURRENT/EXPIRED calculation, or fallback behavior.
- [ ] Persist only `CodexProvenanceSummary` in receipt schema v4 and strict source-free Markdown/JSON. Legacy receipts decode without provenance.
- [ ] Red-team renderer/codec tests with source, answer, transcript, and thread-ID markers; only the opaque hash/count/path summary may remain.

### Task 7: Add explicit CLI consent and kill switch

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/ProveCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/ProveRetryCommand.java`
- Modify: relevant CLI tests

```powershell
$env:CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE = "true"
codedefense prove --staged . `
  --experimental-codex-provenance `
  --codex-thread <THREAD_ID> `
  --consent-codex-history
```

- [ ] Require the three options together; any missing component fails before Git/Codex/JLine. The thread ID is never echoed in help, errors, or preview.
- [ ] `--dry-run` may capture provenance only when full consent and kill switch are present; it prints `No source content was sent.` and `No model request was made.` If app-server was started, it must also print `Codex app-server was invoked only to read the selected local thread.`
- [ ] Help must clearly say CodeDefense reads selected local thread file-change metadata and never stores the transcript.
- [ ] No environment variable may supply a thread ID or silently grant consent.

### Task 8: Expose the experiment in the JetBrains plugin

**Files:**
- Modify: `src/main/java/dev/codedefense/bridge/BridgeRequest.java`
- Create: `src/main/java/dev/codedefense/bridge/ProvenanceConsentRequest.java`
- Modify: `src/main/java/dev/codedefense/bridge/BridgeJsonCodec.java`
- Modify: JetBrains Tool Window controller/view/settings files
- Modify: plugin tests

- [ ] Hide the experimental section unless the kill switch is present in the bridge child environment.
- [ ] Require the user to paste a thread ID and check a per-run consent box. Do not persist either value in IDE settings, recent history, logs, or notifications.
- [ ] Extend `HelloEvent.capabilities` with `codexProvenanceV1` only when the core kill switch is enabled. The plugin sends `ProvenanceConsentRequest(threadId, consent=true)` only after observing that capability; older cores remain usable without the experimental section.
- [ ] `ProvenanceConsentRequest.toString()` exposes only type and thread-ID length. Its codec enforces the existing 256-KiB bridge line limit and rejects blank/oversized IDs, unknown fields, duplicate keys, and non-boolean consent.
- [ ] Clear the field and checkbox immediately after the request is written. Render only the status/disclaimer returned by the core bridge.
- [ ] Plugin still never launches app-server itself. It does not place the thread ID in `BridgeLaunchSpec`, process arguments, settings, notifications, or logs.

### Task 9: Offline acceptance and compatibility gate

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] Document experimental status, consent, data read transiently, data persisted, kill switch, unsupported-version behavior, and non-authorship disclaimer.
- [ ] Run focused app-server transport/projection/matcher/service/runner/CLI/plugin tests using fixtures only.
- [ ] Run `mvn clean verify`, `mvn package`, CLI help/dry-run with the fake adapter, and the JetBrains Gradle verification suite.
- [ ] Search all generated Passport/handoff/plugin/log fixtures for transcript, prompt, command, raw thread ID, patch, source, and answer markers; all must be absent.
- [ ] Record a compatibility matrix for the exact Codex CLI version used by fixtures and one prior supported version. Unknown versions default to `UNAVAILABLE`, never best-effort parsing.
- [ ] One real local thread read requires separate explicit authorization after all offline checks pass. It must use a disposable non-sensitive thread and may not be repeated automatically after failure.
- [ ] If exact matching is unreliable across the two tested Codex versions, ship 8.12 as disabled research code or remove it; do not weaken the match claim.

## Suggested commits

```text
feat: add bounded Codex app-server projections
feat: add consented experimental change provenance
test: enforce provenance privacy and compatibility
docs: document experimental Codex provenance limits
```

## Stop rule

Do not auto-discover threads, scrape rollout/session files, start or resume turns, collect complete transcripts, claim authorship, change scoring, upload provenance, add employee tracking, or enable the feature by default.

## Official OpenAI reference

- Codex app-server protocol and lifecycle: https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md
