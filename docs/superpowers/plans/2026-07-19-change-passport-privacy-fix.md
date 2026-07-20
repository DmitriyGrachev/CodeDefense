# Change Passport Privacy Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make persisted Change Passports structurally source-free even when model-controlled evaluation text repeats staged code.

**Architecture:** `MarkdownChangePassportRenderer` will persist only deterministic change metadata and structured evaluation facts. Model-controlled question text, feedback, concepts, and follow-up prompts will remain available during the terminal interview but will not cross the persistence boundary. The privacy statement will use generic wording that does not reproduce the forbidden acceptance markers.

**Tech Stack:** Java 21, Maven, JUnit 5; no new dependencies.

## Global Constraints

- Do not invoke real Codex.
- Do not change staged capture, analysis, interview scoring, or verification identity.
- Preserve separate primary and follow-up evaluation sections.
- Preserve verdicts, evaluation scores, local category scores, evidence paths/ranges, overall score, and readiness.
- Persist no model-controlled prose or user answers.

---

### Task 1: Enforce a structurally source-free Passport renderer

**Files:**
- Modify: `src/main/java/dev/codedefense/passport/MarkdownChangePassportRenderer.java`
- Modify: `src/test/java/dev/codedefense/passport/MarkdownChangePassportRendererTest.java`

**Interfaces:**
- Consumes: `ChangePassport`, `QuestionResult`, and `InterviewTurn`.
- Produces: `String MarkdownChangePassportRenderer.render(ChangePassport)` with deterministic source-free Markdown.

- [x] **Step 1: Write failing regression tests**

Add a Passport whose question, follow-up question, feedback, and concepts contain `STAGED_HUNK_VALUE`, then assert that neither that literal nor its Markdown-escaped form is present. Assert that primary/follow-up headings, verdicts, evaluation scores, evidence paths, and local final scores remain. Also assert that `expected key points`, `evidence reasons`, and `raw model JSON` do not occur anywhere in the rendered Markdown.

- [x] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
mvn -Dtest=MarkdownChangePassportRendererTest test
```

Expected: assertion failures showing the model-controlled staged literal and forbidden privacy-note terms in the rendered Markdown.

- [x] **Step 3: Implement the minimal persistence-boundary fix**

Remove persisted question text, follow-up prompt, feedback, understood concepts, and knowledge gaps from `MarkdownChangePassportRenderer`. Keep evidence metadata and structured verdict/score fields. Replace the privacy note with:

```text
This educational artifact contains only bounded change metadata and structured local assessment results.
It is not approval to merge or deploy and does not claim Codex authored the change.
```

- [x] **Step 4: Re-run the focused test and confirm GREEN**

Run:

```powershell
mvn -Dtest=MarkdownChangePassportRendererTest test
```

Expected: all renderer tests pass with zero failures and errors.

- [x] **Step 5: Run offline regression verification**

Run:

```powershell
mvn clean verify
mvn package
```

Expected: both builds succeed; the opt-in live Codex test remains skipped.

## Plan self-review

- [x] The plan removes every model-controlled prose field from the persisted artifact.
- [x] Primary and follow-up structured assessment remain separately visible.
- [x] No staged capture, scoring, CLI, or verification behavior changes.
- [x] No placeholder or future work is included.
