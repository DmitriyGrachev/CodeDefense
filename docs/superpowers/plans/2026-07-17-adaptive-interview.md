# CodeDefense Iteration 6: Adaptive Interview Implementation Plan

> **Execution:** Implement inline with test-driven development. Do not use subagents, commit, push, run live smoke scripts, or invoke real Codex.

**Goal:** Extend the accepted project-analysis flow with an offline-testable, exactly-three-question adaptive technical interview that evaluates answers through the existing generic `AiProvider`, computes scores locally, and returns an in-memory `InterviewSession` without persisting a report.

**Architecture:** Keep structured process execution unchanged. A shared `CodexCliAiProvider` feeds both `AiProjectAnalyzer` and `AiAnswerEvaluator`; `InterviewEngine` depends only on `AnswerEvaluator`, terminal ports, deterministic scoring services, and immutable domain records. Picocli remains the outer adapter, JLine is initialized lazily, and all variable evaluation data is enclosed in one collision-safe untrusted prompt boundary.

**Tech stack:** Java 21, Maven, Picocli, JLine 3, Jackson 2, JUnit 5. No new dependencies.

## Global constraints

- Exactly three primary questions and at most one follow-up per primary question.
- Maximum answer length: 8,000 Java characters.
- Evaluation model: `gpt-5.6-terra`; reasoning: `LOW`; timeout: 120 seconds.
- Maximum complete run: one analysis plus six answer evaluations (seven model calls total).
- `skip` is exact and case-insensitive after `strip()`; skipped turns are local and make no evaluation call.
- Numeric question scores, overall score, readiness, and skipped-primary count are computed in Java.
- Expected key points, evidence reasons, answers, prompt/schema content, raw JSON, and model internals are never rendered.
- No report is generated or persisted in Iteration 6.
- Default tests, help, version, declined confirmation, and dry-run remain offline and noninteractive.

---

## File map

### Create: domain contracts

- `src/main/java/dev/codedefense/domain/Verdict.java`
- `src/main/java/dev/codedefense/domain/EvaluationStage.java`
- `src/main/java/dev/codedefense/domain/TurnType.java`
- `src/main/java/dev/codedefense/domain/Readiness.java`
- `src/main/java/dev/codedefense/domain/AnswerEvaluation.java`
- `src/main/java/dev/codedefense/domain/AnswerEvaluationRequest.java`
- `src/main/java/dev/codedefense/domain/InterviewTurn.java`
- `src/main/java/dev/codedefense/domain/QuestionResult.java`
- `src/main/java/dev/codedefense/domain/InterviewSession.java`

### Create: interview services and ports

- `src/main/java/dev/codedefense/interview/InterviewConfig.java`
- `src/main/java/dev/codedefense/interview/AnswerEvaluator.java`
- `src/main/java/dev/codedefense/interview/AiAnswerEvaluator.java`
- `src/main/java/dev/codedefense/interview/AnswerEvaluationPromptFactory.java`
- `src/main/java/dev/codedefense/interview/AnswerEvaluationSchemaLoader.java`
- `src/main/java/dev/codedefense/interview/AnswerEvaluationValidator.java`
- `src/main/java/dev/codedefense/interview/InterviewRunner.java`
- `src/main/java/dev/codedefense/interview/InterviewCancelledException.java`
- `src/main/java/dev/codedefense/interview/InterviewScorer.java`
- `src/main/java/dev/codedefense/interview/ReadinessClassifier.java`
- `src/main/java/dev/codedefense/interview/InterviewEngine.java`

### Create: terminal adapters

- `src/main/java/dev/codedefense/terminal/UserInput.java`
- `src/main/java/dev/codedefense/terminal/JLineUserInput.java`
- `src/main/java/dev/codedefense/terminal/InterviewOutput.java`
- `src/main/java/dev/codedefense/terminal/ConsoleInterviewOutput.java`
- `src/main/java/dev/codedefense/terminal/TerminalTextSanitizer.java`

### Create: shared runtime and resources

- `src/main/java/dev/codedefense/application/CodeDefenseRuntime.java`
- `src/main/java/dev/codedefense/application/CodeDefenseRuntimeFactory.java`
- `src/main/resources/prompts/evaluate-answer.md`
- `src/main/resources/schemas/answer-evaluation.schema.json`

### Modify

- `src/main/java/dev/codedefense/analysis/AiProjectAnalyzer.java`
- `src/main/java/dev/codedefense/terminal/ProjectAnalysisRenderer.java`
- `src/main/java/dev/codedefense/cli/StartCommand.java`
- `README.md`
- Existing analyzer, renderer, snapshot, and StartCommand tests whose constructors/output change.

### Delete

- `src/main/java/dev/codedefense/analysis/ProjectAnalysisRuntimeFactory.java`

### Create: focused tests

- `src/test/java/dev/codedefense/domain/AnswerEvaluationTest.java`
- `src/test/java/dev/codedefense/domain/AnswerEvaluationRequestTest.java`
- `src/test/java/dev/codedefense/domain/InterviewSessionTest.java`
- `src/test/java/dev/codedefense/interview/AnswerEvaluationPromptFactoryTest.java`
- `src/test/java/dev/codedefense/interview/AnswerEvaluationSchemaLoaderTest.java`
- `src/test/java/dev/codedefense/interview/AnswerEvaluationSchemaTest.java`
- `src/test/java/dev/codedefense/interview/AnswerEvaluationValidatorTest.java`
- `src/test/java/dev/codedefense/interview/AiAnswerEvaluatorTest.java`
- `src/test/java/dev/codedefense/interview/InterviewScorerTest.java`
- `src/test/java/dev/codedefense/interview/ReadinessClassifierTest.java`
- `src/test/java/dev/codedefense/interview/InterviewEngineTest.java`
- `src/test/java/dev/codedefense/terminal/JLineUserInputTest.java`
- `src/test/java/dev/codedefense/terminal/ConsoleInterviewOutputTest.java`
- `src/test/java/dev/codedefense/terminal/TerminalTextSanitizerTest.java`
- `src/test/java/dev/codedefense/cli/StartCommandInterviewTest.java`
- `src/test/java/dev/codedefense/application/CodeDefenseRuntimeFactoryTest.java`

---

## Exact domain contracts

### Enums

- `Verdict`: `CORRECT`, `PARTIAL`, `INCORRECT`, `SKIPPED`; only Java creates `SKIPPED`.
- `EvaluationStage`: `PRIMARY`, `FOLLOW_UP`.
- `TurnType`: `PRIMARY`, `FOLLOW_UP`.
- `Readiness`: `STRONG_UNDERSTANDING("Strong understanding")`, `REVIEW_NEEDED("Review needed")`, `KNOWLEDGE_GAPS("Knowledge gaps")` with `displayName()`.

### `AnswerEvaluation`

```java
public record AnswerEvaluation(
        Verdict verdict,
        int score,
        String feedback,
        List<String> understoodConcepts,
        List<String> missingConcepts,
        Optional<String> followUpQuestion) {
    public static AnswerEvaluation skipped();
}
```

The constructor strips text, copies lists immutably, rejects null/blank concepts, limits each list to six, limits follow-up to 500 characters, and enforces the zero/empty/no-follow-up `SKIPPED` invariant. `toString()` exposes only verdict, score, counts, and follow-up presence.

### `AnswerEvaluationRequest`

```java
public record AnswerEvaluationRequest(
        String projectName,
        String projectType,
        String projectSummary,
        TechnicalQuestion primaryQuestion,
        EvaluationStage stage,
        String primaryAnswer,
        String currentPrompt,
        String currentAnswer,
        Optional<AnswerEvaluation> previousEvaluation) {}
```

All required text is stripped/nonblank. Answers are at most 8,000 characters and current prompt at most 500. `PRIMARY` requires no previous evaluation, the primary prompt, and identical primary/current answers. `FOLLOW_UP` requires a non-skipped previous evaluation with a follow-up and a matching current prompt. `toString()` omits answers and grading data.

### Session records

```java
public record InterviewTurn(TurnType type, String prompt, String answer, AnswerEvaluation evaluation) {}

public record QuestionResult(
        int questionNumber,
        TechnicalQuestion question,
        InterviewTurn primaryTurn,
        Optional<InterviewTurn> followUpTurn,
        int finalScore) {}

public record InterviewSession(
        String projectName,
        List<QuestionResult> results,
        int overallScore,
        Readiness readiness,
        int skippedQuestionCount) {}
```

Turns require nonblank prompt/answer and safe `toString()`. Results require question numbers 1–3, PRIMARY/FOLLOW_UP type consistency, score 0–100, and score zero for a skipped primary. Sessions copy exactly three ordered results, validate 0–100 score/readiness, and verify the supplied skipped count equals skipped primary turns.

---

## Evaluation prompt and schema

`evaluate-answer.md` contains only trusted policy: no tools/commands/files/code changes, repository-specific grading, partial-credit guidance, language/style neutrality, concise feedback, no answer-key dump, exact verdict bands, and JSON-only output.

`AnswerEvaluationPromptFactory` strictly decodes at most 64 KiB UTF-8, normalizes CRLF/lone CR to LF, renders every variable field into one payload, and selects `CODEDEFENSE_UNTRUSTED_EVALUATION[_X...]` absent from the complete payload. Project metadata, analysis summary, question/goal/key points, evidence locations/reasons, answers, and previous evaluation data all occur after matching `BEGIN` and before the single matching `END`.

`answer-evaluation.schema.json` is a strict root object with `additionalProperties: false`; all six properties are required. It permits model verdicts `CORRECT|PARTIAL|INCORRECT`, score 0–100, feedback 10–600, two arrays of 0–6 strings bounded 2–200, and a required string `followUpQuestion` bounded to 500. It contains none of `uniqueItems`, combinators, conditionals, nullable fields, or `$ref`.

`AnswerEvaluationSchemaLoader` strictly decodes/caches at most 256 KiB, normalizes line endings, requires exactly one JSON object with no trailing token, and reports only `Answer evaluation schema resource is unavailable.`

`AnswerEvaluationValidator.Payload` is the Jackson DTO. Validation canonicalizes concepts with strip, repeated-whitespace collapse, and `Locale.ROOT` lowercase; rejects duplicates/intersections; enforces verdict/score bands; forbids model `SKIPPED`; forbids CORRECT follow-up; and rejects a follow-up equal to the primary/current prompt. Every failure becomes `InvalidCodexResponseException("Codex returned an invalid answer evaluation.")`.

`AiAnswerEvaluator` builds exactly one `StructuredCodexRequest("answer-evaluation", ..., LOW, 120s)` and calls `AiProvider.execute` once. It parses with `FAIL_ON_TRAILING_TOKENS` and unknown-field failure. Missing prompt/schema maps before provider invocation to `CodexExecutionException(-1, "Answer evaluation resources are unavailable.")`; provider exceptions propagate unchanged.

---

## Interview state machine

1. Require one `ProjectAnalysis` containing exactly three ordered questions; render the introduction.
2. For each question, render number, evidence path/range, and prompt—never key points or evidence reasons.
3. Read through `UserInput`; reject blank and >8,000-character attempts locally and repeat.
4. Recognize only exact case-insensitive stripped `skip`.
5. For primary skip, create local `AnswerEvaluation.skipped()`, make no evaluator call, score zero, and continue.
6. Otherwise build a PRIMARY request, render evaluating, call evaluator exactly once, and render safe evaluation fields.
7. Ask a follow-up only for PARTIAL/INCORRECT with a nonblank model follow-up and while the one-follow-up limit is unused.
8. A skipped follow-up creates a local FOLLOW_UP skipped turn, makes no second call, and retains the primary score.
9. A normal follow-up builds one FOLLOW_UP request, calls evaluator once, renders it, and ignores any second follow-up proposal.
10. Build each `QuestionResult`, calculate the rounded mean after question three, classify readiness, count skipped primaries, render summary, and return `InterviewSession`.

No recursion is used; at most six evaluator calls can occur.

---

## Scoring rules

`InterviewScorer` is pure Java:

- skipped primary: `0`;
- no evaluated follow-up or skipped follow-up: primary score;
- evaluated follow-up: `max(primary, round(primary * 0.40 + followUp * 0.60))`;
- overall: `round(sum(question scores) / 3.0)`.

`ReadinessClassifier` maps `80..100` to strong understanding, `55..79` to review needed, and `0..54` to knowledge gaps. Inputs outside 0–100 are rejected.

---

## Terminal ports and adapters

```java
public interface UserInput {
    String readAnswer(String prompt);
}

public interface InterviewOutput {
    void renderIntroduction(int questionCount);
    void renderPrimaryQuestion(int current, int total, TechnicalQuestion question);
    void renderInputValidationError(String message);
    void renderEvaluating();
    void renderEvaluation(AnswerEvaluation evaluation);
    void renderFollowUp(String followUpQuestion);
    void renderSkipped(boolean followUp);
    void renderQuestionScore(int questionNumber, int score);
    void renderSummary(InterviewSession session);
}
```

`JLineUserInput` stores a supplier of a package-private functional reader seam. Construction does not create a terminal; the supplier runs only on first `readAnswer`. JLine `UserInterruptException` and `EndOfFileException` map to `InterviewCancelledException("Session cancelled. No report was generated.")`.

`TerminalTextSanitizer.singleLine` removes CSI, OSC, ISO controls, and specified bidi controls; normalizes CRLF/CR and turns line separators/tabs into spaces while preserving readable Unicode and emoji. Both renderers use it.

`ConsoleInterviewOutput` owns only a `PrintWriter`, flushes before reads, and renders the specified introduction, question/evidence, evaluation, optional understood/missing sections, skip messages, per-question score, final local score/readiness, educational disclaimer, and `Report generation will be connected in Iteration 7.`

---

## Shared runtime wiring

`CodeDefenseRuntimeFactory.create()` constructs one each of `JdkProcessExecutor`, `CodexProcessEnvironment`, `CodexEnvironmentChecker`, `CodexProcessRunner`, `CodexCliAiProvider`, `ObjectMapper`, and `CodexRuntimeConfig`. The exact same provider instance is passed to `AiProjectAnalyzer` and `AiAnswerEvaluator`. The evaluator is placed in `InterviewEngine`; `CodeDefenseRuntime` returns the analyzer and runner ports.

Factory construction performs no process, preflight, model call, or JLine initialization. `ProjectAnalysisRuntimeFactory` is deleted so no second production provider graph remains.

`StartCommand` creates the shared runtime once, creates a lazy `JLineUserInput`, renders analysis, then calls `InterviewRunner.conduct`. Dry-run, decline, analysis failure, help, and version return before the first answer read. Interview cancellation maps to exit 130; existing Codex and invalid-response exit mappings remain unchanged.

---

## TDD execution tasks

### Task 1: Domain and scoring

- [ ] Add failing domain/config/scorer/readiness tests for immutability, stripping, bounds, stage invariants, safe `toString`, exact three results, skip counting, score formulas, and readiness boundaries.
- [ ] Run `mvn -Dtest=AnswerEvaluationTest,AnswerEvaluationRequestTest,InterviewSessionTest,InterviewScorerTest,ReadinessClassifierTest test`; expect compilation/test failures because contracts do not exist.
- [ ] Implement the nine domain files plus `InterviewConfig`, `InterviewScorer`, and `ReadinessClassifier` minimally.
- [ ] Re-run the focused command; expect all focused tests to pass.

### Task 2: Prompt, schema, parsing, and evaluator

- [ ] Add failing prompt/schema/loader/validator/evaluator tests covering malicious collision data, CRLF, strict UTF-8, bounds, unsupported schema keywords, semantic failures, one provider call, safe resource mapping, and exception privacy.
- [ ] Run `mvn -Dtest=AnswerEvaluationPromptFactoryTest,AnswerEvaluationSchemaLoaderTest,AnswerEvaluationSchemaTest,AnswerEvaluationValidatorTest,AiAnswerEvaluatorTest test`; expect failures because production classes/resources do not exist.
- [ ] Add the prompt/schema resources and implement the five evaluator-boundary classes.
- [ ] Re-run the focused command; expect all tests to pass with no real provider.

### Task 3: Terminal boundary

- [ ] Add failing sanitizer/JLine/output tests for lazy initialization, interrupt/EOF mapping, Unicode preservation, control stripping, safe rendering, and omitted internal fields.
- [ ] Run `mvn -Dtest=JLineUserInputTest,ConsoleInterviewOutputTest,TerminalTextSanitizerTest,ProjectAnalysisRendererTest test`; expect failures before adapters exist.
- [ ] Implement terminal ports/adapters and make `ProjectAnalysisRenderer` use the shared sanitizer while removing the Iteration 6 future message.
- [ ] Re-run the focused command; expect all tests to pass without a real terminal.

### Task 4: Interview engine

- [ ] Add failing `InterviewEngineTest` scenarios for correct/partial/incorrect, exact skip, non-command skip phrases, blank/overlong retry, follow-up skip, second-follow-up suppression, ordering, failures, cancellation, and output privacy.
- [ ] Run `mvn -Dtest=InterviewEngineTest test`; expect failure before the engine exists.
- [ ] Implement `InterviewRunner`, `InterviewCancelledException`, and the iterative `InterviewEngine` state machine.
- [ ] Re-run engine plus scoring tests; expect all tests to pass and evaluator call counts to prove the six-call ceiling.

### Task 5: Shared runtime and CLI integration

- [ ] Add failing `CodeDefenseRuntimeFactoryTest` and `StartCommandInterviewTest`; update existing analyzer/StartCommand tests for the new constructor graph and output.
- [ ] Run `mvn -Dtest=CodeDefenseRuntimeFactoryTest,StartCommandInterviewTest,StartCommandProjectAnalysisTest,StartCommandSnapshotTest,AiProjectAnalyzerTest test`; expect failures before wiring changes.
- [ ] Add `CodeDefenseRuntime`/factory, add the shared-object constructor to `AiProjectAnalyzer`, delete `ProjectAnalysisRuntimeFactory`, and integrate lazy input/interview output into `StartCommand`.
- [ ] Re-run the focused command; expect success with no process or JLine creation.

### Task 6: Documentation and offline acceptance

- [ ] Update README with interactive behavior, local scoring/readiness, seven-call maximum, skip/cancellation/privacy, dry-run, and no-report status; leave Iteration 6 unchecked in `docs/implementation-checklist.md`.
- [ ] Run all focused groups again.
- [ ] Run `mvn clean verify`; require zero failures/errors, two expected skips, and no live Codex call.
- [ ] Run `mvn package`; require `BUILD SUCCESS` and `target/codedefense.jar`.
- [ ] Run `java -jar target/codedefense.jar --help`; require exit 0 without JLine/Codex.
- [ ] Run `java -jar target/codedefense.jar --version`; require exit 0 without JLine/Codex.
- [ ] Run `java -jar target/codedefense.jar start . --dry-run`; require exit 0 plus `No source content was sent.` and `Codex was not invoked.`
- [ ] Verify no report file was created, Iteration 6 remains unchecked, `git diff --check` passes, and only Iteration 6 files changed.

## Explicitly forbidden acceptance commands

Do not run `java -jar target/codedefense.jar start . --yes`, `scripts/live-smoke-test.ps1`, or `scripts/live-smoke-test.sh`.

