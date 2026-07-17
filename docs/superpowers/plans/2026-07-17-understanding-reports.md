# CodeDefense Iteration 7: Understanding Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate one privacy-safe Markdown understanding report after a completed three-question interview, persist it under the user's CodeDefense home directory, and expose it through `codedefense report`.

**Architecture:** Keep the existing generic `AiProvider` and make a report-narrative adapter parallel to the analysis and answer-evaluation adapters. Java owns metadata, scoring, readiness, Markdown structure, and persistence; the final model call can supply only validated narrative sections. A `ReportStore` port isolates secure filesystem operations, while `StartCommand` coordinates report creation only after a completed interview.

**Tech stack:** Java 21, Maven, Picocli, Jackson, JLine, JUnit 5; no new dependencies.

## Global Constraints

- Stay on `feat/iteration-07-understanding-reports`; implement only Iteration 7.
- Default tests, help, version, dry-run, and `report` must make no Codex call; never run `start . --yes` or either live-smoke script.
- Preserve the generic `AiProvider.execute(StructuredCodexRequest)` boundary and the one shared production provider graph.
- The report-narrative request uses operation `report-narrative`, configured model, `ReasoningEffort.LOW`, and `ReportConfig.narrativeTimeout()`.
- Never send or render snapshot content, expected key points, evidence metadata or reasons, schemas, prompts, or raw model JSON. Narrative prompts also omit user answers.
- Java remains authoritative for all scores, readiness, metadata, filenames, storage location, and Markdown structure.
- Persist reports only under `<user.home>/.codedefense`; tests use injected `@TempDir` homes only.
- Use strict UTF-8, bounded reads/writes, fixed safe persistence messages, no stack traces, and atomic-move fallback.
- Keep `docs/implementation-checklist.md` Iteration 7 unchecked. Do not commit or push automatically.

---

## File Map

### Create: domain contracts

- `src/main/java/dev/codedefense/domain/NarrativeSource.java`
- `src/main/java/dev/codedefense/domain/AnalyzedFile.java`
- `src/main/java/dev/codedefense/domain/ReportMetadata.java`
- `src/main/java/dev/codedefense/domain/ReportNarrative.java`
- `src/main/java/dev/codedefense/domain/ReportGenerationRequest.java`
- `src/main/java/dev/codedefense/domain/FinalReport.java`
- `src/main/java/dev/codedefense/domain/SavedReport.java`

### Create: report application and adapter layer

- `src/main/java/dev/codedefense/report/ReportConfig.java`
- `src/main/java/dev/codedefense/report/ReportNarrativeGenerator.java`
- `src/main/java/dev/codedefense/report/AiReportNarrativeGenerator.java`
- `src/main/java/dev/codedefense/report/ReportNarrativePromptFactory.java`
- `src/main/java/dev/codedefense/report/ReportNarrativeSchemaLoader.java`
- `src/main/java/dev/codedefense/report/ReportNarrativeValidator.java`
- `src/main/java/dev/codedefense/report/DeterministicReportNarrativeFactory.java`
- `src/main/java/dev/codedefense/report/MarkdownTextEscaper.java`
- `src/main/java/dev/codedefense/report/MarkdownReportRenderer.java`
- `src/main/java/dev/codedefense/report/CodeDefensePaths.java`
- `src/main/java/dev/codedefense/report/ReportPersistenceException.java`
- `src/main/java/dev/codedefense/report/ReportStore.java`
- `src/main/java/dev/codedefense/report/FileSystemReportStore.java`
- `src/main/java/dev/codedefense/report/ReportService.java`
- `src/main/java/dev/codedefense/report/UnderstandingReportService.java`
- `src/main/java/dev/codedefense/application/ShowLatestReportUseCase.java`

### Create: resources

- `src/main/resources/prompts/generate-report.md`
- `src/main/resources/schemas/report-narrative.schema.json`

### Create: focused tests

- `src/test/java/dev/codedefense/domain/AnalyzedFileTest.java`
- `src/test/java/dev/codedefense/domain/ReportMetadataTest.java`
- `src/test/java/dev/codedefense/domain/ReportNarrativeTest.java`
- `src/test/java/dev/codedefense/domain/ReportGenerationRequestTest.java`
- `src/test/java/dev/codedefense/domain/FinalReportTest.java`
- `src/test/java/dev/codedefense/domain/SavedReportTest.java`
- `src/test/java/dev/codedefense/report/MarkdownTextEscaperTest.java`
- `src/test/java/dev/codedefense/report/MarkdownReportRendererTest.java`
- `src/test/java/dev/codedefense/report/CodeDefensePathsTest.java`
- `src/test/java/dev/codedefense/report/FileSystemReportStoreTest.java`
- `src/test/java/dev/codedefense/report/ReportNarrativePromptFactoryTest.java`
- `src/test/java/dev/codedefense/report/ReportNarrativeSchemaLoaderTest.java`
- `src/test/java/dev/codedefense/report/ReportNarrativeSchemaTest.java`
- `src/test/java/dev/codedefense/report/ReportNarrativeValidatorTest.java`
- `src/test/java/dev/codedefense/report/AiReportNarrativeGeneratorTest.java`
- `src/test/java/dev/codedefense/report/DeterministicReportNarrativeFactoryTest.java`
- `src/test/java/dev/codedefense/report/UnderstandingReportServiceTest.java`
- `src/test/java/dev/codedefense/application/ShowLatestReportUseCaseTest.java`
- `src/test/java/dev/codedefense/cli/StartCommandReportTest.java`
- `src/test/java/dev/codedefense/cli/ReportCommandTest.java`

### Modify

- `src/main/java/dev/codedefense/application/CodeDefenseRuntime.java`
- `src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java`
- `src/main/java/dev/codedefense/cli/StartCommand.java`
- `src/main/java/dev/codedefense/cli/ReportCommand.java`
- `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- `src/main/java/dev/codedefense/terminal/ConsoleInterviewOutput.java`
- `README.md`
- `src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java`
- `src/test/java/dev/codedefense/cli/CliFoundationTest.java`
- `src/test/java/dev/codedefense/terminal/ConsoleInterviewOutputTest.java`

No files are deleted or renamed. `docs/implementation-checklist.md` remains unchanged in this iteration.

---

## Domain and Boundary Contracts

```java
enum NarrativeSource { AI, DETERMINISTIC_FALLBACK }

record AnalyzedFile(String path, int includedLines, boolean truncated, int renderedBytes) {}
record ReportMetadata(Instant analyzedAt, String model, String projectName, String projectType,
                      List<AnalyzedFile> selectedFiles, int snapshotBytes, int redactionCount) {
    static ReportMetadata from(ProjectSnapshot snapshot, String model, Instant analyzedAt);
}
record ReportNarrative(String headline, String summary, List<String> strengths,
                       List<String> knowledgeGaps, List<String> recommendedActions) {}
record ReportGenerationRequest(ProjectAnalysis analysis, InterviewSession session) {}
record FinalReport(ReportMetadata metadata, ProjectAnalysis analysis, InterviewSession session,
                   ReportNarrative narrative, NarrativeSource narrativeSource) {}
record SavedReport(Path path, NarrativeSource narrativeSource) {}
```

- Paths become portable `/`-separated relative paths and reject absolute, drive-qualified, empty, `.` and `..` segments.
- Metadata preserves `ProjectSnapshot.selectedFiles()` order but never retains `promptContent`; all lists are copied and immutable.
- Narrative bounds are headline 5–160, summary 40–1200, optional strengths/gaps 0–6, required actions 1–6, list items 3–240 after stripping/collapsing whitespace.
- Request/final report enforce same project name/type and the three question IDs/order. Privacy-safe `toString()` methods omit answers, prompts, feedback, key points, evidence metadata or reasons, narrative, and snapshot content.
- `ReportConfig` is exactly `record ReportConfig(Duration narrativeTimeout, int maximumReportBytes, int maximumLatestPointerBytes, int maximumProjectSlugLength)`. Its defaults are 120 seconds, 1 MiB, 4096 bytes, and 60; it rejects non-positive timeout, report limits below 64 KiB, pointer limits below 256 bytes, and slug lengths outside 16–120.
- `CodeDefensePaths` is exactly `record CodeDefensePaths(Path rootDirectory, Path reportsDirectory, Path latestPointer)`. It normalizes an injected user home, preserves all derived paths beneath it, and maps blank/missing `user.home` to `ReportPersistenceException` with a fixed safe message.

## Narrative Request, Prompt, Schema, and Fallback

- `ReportNarrativePromptFactory` reads at most 64 KiB of strict UTF-8 template, normalizes CRLF/lone-CR to LF, serializes one untrusted payload, then chooses `CODEDEFENSE_UNTRUSTED_REPORT`, adding `_X` until absent from the complete payload. Trusted instructions precede `BEGIN <marker>`; variable data occurs only inside matching markers.
- The payload contains project metadata/validated overview, question IDs/prompts, evaluations and concepts, local final scores/readiness/skipped count. It excludes all answers, expected key points, evidence metadata or reasons, snapshot/source, templates, schemas, and raw JSON.
- `report-narrative.schema.json` is a strict object with all five fields required, no `uniqueItems`, references, combinators, nullable fields, or conditionals. Loader strictly validates one bounded UTF-8 object (256 KiB) and caches only successful loads.
- `AiReportNarrativeGenerator` creates one low-reasoning request, strictly rejects unknown/trailing JSON, and maps local resource faults before provider invocation to `CodexExecutionException(-1, "Report narrative resources are unavailable.")`. Invalid output always becomes `InvalidCodexResponseException("Codex returned an invalid report narrative.")` without private data.
- Validator canonicalizes items, rejects duplicates/intersections, bounds violations, `overall score`, `final score`, `<number>/100`, and percentage claims. Java's local values are never model-controlled.
- `DeterministicReportNarrativeFactory` makes zero AI calls. It derives bounded first-seen strengths/gaps/actions from validated concept lists and skipped questions; it uses a local fallback action when no missing concepts exist.

## Markdown, Paths, and Persistence

- `MarkdownTextEscaper.inline` normalizes line endings, flattens separators/tabs, HTML-escapes `&<>`, escapes Markdown controls, and preserves Unicode. `fencedText` selects a backtick run longer than any run in content and uses a `text` fence.
- `MarkdownReportRenderer` is pure, LF-only, and deterministic. It owns all headings/metadata/authoritative score/readiness/question sections/privacy note. It includes user answers only inside safe fences and never emits source snapshot, expected key points, evidence reasons, raw JSON, templates, schemas, or temporary paths.
- `CodeDefensePaths.under(Path userHome)` returns absolute normalized `<home>/.codedefense`, `reports`, and `latest-report.txt` without I/O. Defaults use `user.home` and convert missing/blank property to a fixed persistence exception.
- `FileSystemReportStore.save` renders/strictly UTF-8-encodes in memory, applies configured bounds, writes report and pointer temps in their final directories, prefers `ATOMIC_MOVE`, falls back on `AtomicMoveNotSupportedException`, never overwrites, updates `latest-report.txt` with one normalized absolute report path plus LF, and cleans temps/orphans on failure.
- `readLatest` uses `NOFOLLOW_LINKS`, bounded strict reads, exactly one pointer line, an absolute normalized `.md` path below `reportsDirectory`, and a non-symlink regular report. Corruption/escape/missing/oversize maps to fixed safe read failure text.

## Runtime and CLI Integration

- `UnderstandingReportService` creates `ReportGenerationRequest`, local metadata using injected `Clock`, calls the generator exactly once, rethrows `CodexInterruptedException`, converts each known non-cancellation Codex outcome error to deterministic fallback, saves through `ReportStore`, and returns `SavedReport`. It does not catch persistence or unrelated runtime failures.
- `CodeDefenseRuntime` gains `ReportService`; factory creates one shared `AiProvider`, one `ObjectMapper`, one `Clock.systemUTC()`, one `ReportConfig`, and supplies the same provider to analysis/evaluation/narrative adapters. Construction remains no-I/O/no-process/no-JLine.
- `StartCommand` calls report generation only after a successful analysis and completed interview. It prints the generating line, fallback warning when appropriate, then the sanitized saved path. It maps `ReportPersistenceException` to exit 9 and preserves cancellation mapping. All pre-interview and failure paths avoid `ReportService`.
- `ReportCommand` is `Callable<Integer>` backed by `ShowLatestReportUseCase`; default construction builds only local report dependencies. It prints Markdown with exactly one final newline, prints the fixed no-report message for empty, and maps safe read failure to 9. `report --help`, root help, and version perform no report I/O.

---

### Task 1: Domain contracts and configuration

**Files:** create all seven domain records/enums above, `report/ReportConfig.java`, six domain tests, and `report/CodeDefensePathsTest.java`; create `report/CodeDefensePaths.java`.

- [ ] Write failing tests for immutable copies, safe `toString()`, path normalization/rejection, preserved selected-file order, no retained snapshot prompt, narrative bounds/canonicalization, cross-record identity checks, absolute normalized saved paths, report limits, and no-I/O path construction.
- [ ] Run: `mvn -Dtest=AnalyzedFileTest,ReportMetadataTest,ReportNarrativeTest,ReportGenerationRequestTest,FinalReportTest,SavedReportTest,CodeDefensePathsTest test` — expect compilation failures before contracts exist.
- [ ] Implement the contracts and configuration with constructor validation and fixed safe persistence messages.
- [ ] Re-run the focused command — expect all tests green.

### Task 2: Narrative resource boundary and structured generator

**Files:** create prompt/schema resources, narrative generator/prompt/schema-loader/validator classes, and the six narrative generator tests.

- [ ] Write failing malicious-data prompt tests covering collision markers, injection text, CRLF template, required inclusion, and exclusion of answers/key points/evidence reasons/snapshot.
- [ ] Write failing schema/validator/generator tests for strict root shape, unsupported keywords, duplicates/intersections, score claims, strict parsing, one provider call, LOW reasoning, configured model/120-second timeout, fixed safe errors, and no provider call after resource failure.
- [ ] Run: `mvn -Dtest=ReportNarrativePromptFactoryTest,ReportNarrativeSchemaLoaderTest,ReportNarrativeSchemaTest,ReportNarrativeValidatorTest,AiReportNarrativeGeneratorTest test` — expect failures before implementation.
- [ ] Implement strict bounded resource loading, collision-safe untrusted payload construction, structured request creation, strict Jackson parsing, semantic validation, and fixed exception mapping.
- [ ] Re-run the focused command — expect green with hand-written provider fakes only.

### Task 3: Deterministic fallback and Markdown rendering

**Files:** create `DeterministicReportNarrativeFactory.java`, `MarkdownTextEscaper.java`, `MarkdownReportRenderer.java`; create their three test classes.

- [ ] Write failing tests for fallback order/de-duplication/six-item cap/skipped action/empty local action/no AI, and for inline/fenced Markdown injection containment including Unicode and backtick runs.
- [ ] Write renderer tests with private expected-key/evidence/snapshot/raw-JSON markers, asserting headings, metadata, local scores/readiness, three question sections, skipped/follow-up handling, LF-only deterministic output, fenced answers, fallback notice, and privacy note.
- [ ] Run: `mvn -Dtest=DeterministicReportNarrativeFactoryTest,MarkdownTextEscaperTest,MarkdownReportRendererTest test` — expect failures before implementation.
- [ ] Implement the deterministic factory, escaping, and pure renderer.
- [ ] Re-run the focused command — expect all tests green and no private marker in Markdown.

### Task 4: Secure filesystem report store

**Files:** create `ReportPersistenceException.java`, `ReportStore.java`, `FileSystemReportStore.java`, `FileSystemReportStoreTest.java`.

- [ ] Write `@TempDir` failing tests for UTF-8 write/read, UTC timestamp and collision suffix, safe slug, configured size limits, pointer update/second latest, temp/orphan cleanup, no-I/O constructor, safe messages, and no IOException leak.
- [ ] Add malformed/extra/relative/traversal/missing/directory/oversize/malformed-UTF8 pointer/report cases plus supported-platform symlink rejection. Add a package-private move seam to exercise atomic-move fallback.
- [ ] Run: `mvn -Dtest=FileSystemReportStoreTest,CodeDefensePathsTest test` — expect failures before the store exists.
- [ ] Implement bounded strict-UTF8 reads, `NOFOLLOW_LINKS` validation, unique safe output selection, temp files, atomic/fallback moves, pointer checks, cleanup, and fixed exception messages.
- [ ] Re-run the focused command — expect all filesystem tests green without using real home.

### Task 5: Report service and latest-report use case

**Files:** create `ReportService.java`, `UnderstandingReportService.java`, `application/ShowLatestReportUseCase.java`; create corresponding two tests.

- [ ] Write failing fake-based tests for AI success, one-call timeout/execution/invalid/resource fallback, interruption propagation, unexpected-runtime propagation, persistence propagation, metadata fixed-clock values, no snapshot prompt retention, and use-case delegation.
- [ ] Run: `mvn -Dtest=UnderstandingReportServiceTest,ShowLatestReportUseCaseTest test` — expect failures before implementation.
- [ ] Implement exact one-call fallback policy and `ShowLatestReportUseCase` direct delegation.
- [ ] Re-run the focused command — expect green; assertions prove no retry and no extra model call.

### Task 6: Runtime composition and CLI commands

**Files:** modify `CodeDefenseRuntime.java`, `CodeDefenseRuntimeFactory.java`, `StartCommand.java`, `ReportCommand.java`, `CodeDefenseApplication.java`, `ConsoleInterviewOutput.java`; create/modify CLI/runtime/output tests listed in the file map.

- [ ] Write failing runtime test proving the analyzer, evaluator, and narrative generator share exactly one provider, while construction makes no provider call/input/filesystem I/O.
- [ ] Write failing `StartCommandReportTest` cases for success/fallback messages, same snapshot-analysis-session instances, exit 9 safe persistence error, and every no-report path. Write `ReportCommandTest` cases for content/newline/no-report/read error/help/no Codex-JLine initialization. Update root CLI injection/help tests and remove the old Iteration-7 future sentence from terminal output assertions.
- [ ] Run: `mvn -Dtest=CodeDefenseRuntimeFactoryTest,StartCommandReportTest,ReportCommandTest,CliFoundationTest,ConsoleInterviewOutputTest test` — expect failures before integration.
- [ ] Extend runtime factory with shared report graph; inject service through test constructors; integrate post-interview reporting and exit mapping; replace placeholder report command; add injectable `createCommandLine(StartCommand, ReportCommand)`.
- [ ] Re-run the focused command — expect green, no real provider, no JLine for help/report construction, and report invocation only after completed interview.

### Task 7: Documentation and complete offline acceptance

**Files:** modify `README.md`; leave `docs/implementation-checklist.md` with Iteration 7 unchecked.

- [ ] Update README with report lifecycle/path/pointer, report privacy boundaries, local score/readiness authority, eight-call maximum, fallback, exit 9, report's no-Codex/no-JLine behavior, UTF-8 PowerShell example, current Iteration 7 status, and Iteration 8 future status.
- [ ] Run all focused report, filesystem, CLI, runtime, and markdown commands above.
- [ ] Run: `mvn clean verify` — require zero failures/errors, two expected skips, and no live Codex call.
- [ ] Run: `mvn package` — require `BUILD SUCCESS` and shaded JAR.
- [ ] Run: `java -jar target/codedefense.jar --help`; `java -jar target/codedefense.jar --version`; `java -jar target/codedefense.jar start . --dry-run` — each exit 0; dry-run prints `No source content was sent.` and `Codex was not invoked.` and creates no report directory.
- [ ] Run the packaged `report` under a fresh temporary `-Duser.home=<temp>`; require exit 0, `No completed CodeDefense report is available yet.`, no created report files, no Codex, and no JLine.
- [ ] Run `git diff --check`, `git status --short`, and confirm the checklist remains `- [ ] Iteration 7 — Markdown reports and report command.`

## Plan Self-Review

- [ ] Coverage: tasks map every requested domain, AI, fallback, Markdown, persistence, runtime, CLI, documentation, and offline acceptance requirement.
- [ ] Type consistency: `ReportService.generateAndSave(ProjectSnapshot, ProjectAnalysis, InterviewSession)` is the only report-generation integration signature; `ReportStore.save(FinalReport)` and `readLatest()` remain adapter-only.
- [ ] Privacy: both model prompt and renderer have separate explicit exclusion tests; no test uses a real Codex provider or real home directory.
- [ ] Scope: no Iteration 8 sample implementation, external integrations, dependencies, live request, automatic commit, or automatic push.
