# CodeDefense Video Production Guide

## Recording target

- Public YouTube video
- English narration and subtitles
- 2:45–2:55 final duration; never 3:00 or longer
- OBS canvas/output: 1920 × 1080
- 30 FPS, H.264, 8–12 Mbps video, 48 kHz audio
- Screen recording only; webcam is optional and omitted from the planned cut

## Preflight

1. Use the v0.1.1 JAR and matching JetBrains/Codex plugins.
2. Close notifications, personal browser tabs, password managers, and unrelated terminals.
3. Use a disposable staged Java fixture with no secrets or personal data.
4. Prepare a current Passport and one intentional staged edit that demonstrates `EXPIRED`.
5. Open the JetBrains CodeDefense Tool Window, Codex plugin chat, GitHub Actions run, and a clean PowerShell terminal.
6. Set Windows and terminal output to UTF-8.
7. Record a 15-second audio sample and verify clarity before the product run.
8. Capture model waiting separately and remove idle time without implying instant execution.

## Timed script

### 0:00–0:15 — Problem and promise

**On screen:** Hero image, then a staged Git change in IntelliJ.

**Narration:**

> AI can write working code faster than a developer can fully understand it. CodeDefense turns that exact gap into a workflow: before I keep an AI-assisted change, I defend the decisions, edge cases, and expected tests.

### 0:15–1:15 — JetBrains defense

**On screen:** CodeDefense Tool Window showing `UNDEFENDED`, **Preview defense**, bounded counts, **Start defense**, one question/evaluation, then a cut to the completed session.

**Narration:**

> Here is one staged Java change. CodeDefense fingerprints the exact Git index and previews only bounded, redacted hunks. Nothing is sent until I confirm. GPT-5.6, reached through my locally authenticated Codex CLI, generates three evidence-grounded questions: the design decision, a counterfactual, and a test prediction. It can ask one focused follow-up. The model evaluates the answer, but Java owns the final score and state machine.

### 1:15–1:35 — Passport and Evidence Coverage

**On screen:** `CURRENT` Passport and the Evidence Coverage card; click one hunk location.

**Narration:**

> The completed defense creates a repository-local Change Passport bound to this fingerprint. Evidence Coverage shows which changed hunks the questions actually referenced. It is evidence use, not a claim of correctness or safety. If I change the staged diff, the Passport becomes expired.

### 1:35–1:58 — Codex-native integration

**On screen:** `@codedefense` returns `CURRENT`, then show the advisory Stop hook reporting `EXPIRED` after the prepared edit.

**Narration:**

> The same boundary is available inside Codex. The skill reads only source-free Passport status, and an advisory Stop hook warns when a Codex task ends with an undefended or expired staged change. It never starts a defense or sends source automatically.

### 1:58–2:20 — CLI and CI

**On screen:** `passport ci-check` source-free output and a green GitHub Actions summary.

**Narration:**

> The Passport fingerprint can travel in a commit trailer. GitHub Actions recalculates parent-to-commit continuity without Codex, without an API key, and without uploading a Passport or repository source. Teams can choose advisory or required policy.

### 2:20–2:43 — How Codex and GPT-5.6 were used

**On screen:** Architecture diagram and bounded-flow labels.

**Narration:**

> GPT-5.6 provides contextual question generation and structured evaluation. Deterministic Java controls privacy limits, scoring, fingerprints, persistence, and exit codes. The CLI, JetBrains plugin, Codex skill, and CI all reuse the same bounded core instead of duplicating trust rules. I also built CodeDefense with Codex: turning review findings into regression tests, diagnosing the Windows PowerShell launcher, and hardening the Git and prompt boundaries.

### 2:43–2:55 — Close

**On screen:** Logo, pitch, repository URL.

**Narration:**

> CodeDefense does not prove that code is correct or safe. It proves something smaller and useful: a developer completed a defense of the exact change. Turn AI-generated code into developer-owned understanding.

## Edit points

- Cut command typing after enough characters establish the action.
- Cut model waiting but retain the confirmation and resulting question.
- Do not speed up spoken evaluation text until it becomes unreadable.
- Use labels `Real product output` and `Waiting time removed` where a cut could be misunderstood.
- Blur or crop local user paths, notifications, account data, and the private feedback session ID.
- Use no copyrighted music; silence behind narration is acceptable.

## OBS scene order

1. `Hero` — static title card.
2. `JetBrains` — repository editor and CodeDefense Tool Window only.
3. `Codex` — CodeDefense plugin conversation only.
4. `Terminal` — large PowerShell font with one prepared command per shot.
5. `GitHub` — Passport workflow summary with unrelated browser chrome cropped.
6. `Architecture` — README diagram or the hero flow.
7. `Closing` — logo, pitch, repository URL.

## YouTube metadata

**Title**

```text
CodeDefense — Prove You Understand the Code AI Helped You Write
```

**Description**

```text
CodeDefense turns an exact staged Git change into an evidence-grounded
technical defense powered by GPT-5.6 through the locally authenticated
Codex CLI.

The demo shows the JetBrains Defense Cockpit, adaptive questions, Change
Passports, Evidence Coverage, Codex-native status checks, and source-free
GitHub Actions continuity verification.

Repository:
https://github.com/DmitriyGrachev/CodeDefense

Release:
https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.1
```

## Final upload checklist

- [ ] Final duration is below 3:00.
- [ ] English voice is audible and synchronized.
- [ ] English subtitles were reviewed manually.
- [ ] The demo visibly covers JetBrains, Codex, and CLI/CI.
- [ ] The narration explains both product use of GPT-5.6 and development collaboration with Codex.
- [ ] No credentials, private session IDs, personal paths, raw JSON, prompts, schemas, or source snapshots are visible.
- [ ] The video does not claim correctness, security, authorship, or merge approval.
- [ ] YouTube visibility is **Public**.
- [ ] The title is `CodeDefense — Prove You Understand the Code AI Helped You Write`.
- [ ] The description links the repository and v0.1.1 release.
