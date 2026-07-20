# Devpost Submission Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce the complete English-language CodeDefense Devpost submission worksheet, judge guide, visual gallery, video production guide, and reproducible judge ZIP without changing application behavior or submitting the entry.

**Architecture:** Keep public copy under `docs/devpost`, durable visual assets under `docs/assets/devpost`, and generated archives under ignored `target/`. Reuse the signed-off v0.1.0 binaries and current product contracts; the packaging script only assembles and verifies those artifacts. The final YouTube URL is added in a separate post-recording task, after which the kit is rebuilt and inspected from a clean extraction.

**Tech Stack:** Markdown, SVG/PNG, PowerShell 7/Windows PowerShell-compatible packaging, Java 21, Maven, Gradle IntelliJ plugin build, OBS, YouTube.

## Global Constraints

- Do not change CodeDefense application, domain, CLI, bridge, Git, scanner, AI, or plugin behavior.
- Do not add dependencies.
- Do not invoke real Codex during automated or packaging verification.
- One deliberate real-Codex run is allowed only while capturing the final demo.
- Keep all public prose and video narration in English.
- Never write the private `/feedback` session ID to Git, generated archives, screenshots, video, release notes, or public copy.
- Keep the approved pitch exactly: `Turn AI-generated code into developer-owned understanding.`
- Keep the trust boundary explicit: a Passport is not proof of correctness, security, authorship, or approval to merge or deploy.
- Keep platform claims narrow: Windows is the accepted plugin platform; Ubuntu and Windows cover the Maven suite; installed macOS/Linux plugin acceptance remains pending.
- Gallery images must be PNG, 1800 x 1200, below 5 MB each, and free of credentials, personal paths, notifications, and unrelated UI.
- The demo must be public on YouTube, include English audio, and remain below three minutes.
- The generated judge ZIP must remain below 35 MB and stay beneath ignored `target/`.
- Do not submit the Devpost entry as part of this plan.

---

## File map

- `docs/devpost/devpost-submission.md`: copy-and-paste worksheet for every public and private Devpost field, excluding the private session ID value.
- `docs/devpost/README-JUDGES.md`: standalone evaluation path included in the repository and judge ZIP.
- `docs/devpost/video-production-guide.md`: OBS preparation, exact English narration, on-screen actions, edit points, subtitles, and upload checks.
- `docs/assets/devpost/codedefense-logo.svg`: scalable source logo.
- `docs/assets/devpost/codedefense-logo.png`: rendered Devpost/plugin logo.
- `docs/assets/devpost/01-hero.png` through `06-passport-ci.png`: final gallery images.
- `README.md`: approved pitch, hero, fast judge path, and links to the judge guide and later public video.
- `scripts/package-devpost-judge-kit.ps1`: deterministic, bounded judge-kit assembly.
- `target/codedefense-devpost-judge-kit-v0.1.0.zip`: generated, ignored output.

---

### Task 1: Create the Devpost submission worksheet

**Files:**
- Create: `docs/devpost/devpost-submission.md`

**Interfaces:**
- Consumes: approved positioning and narrative from `docs/superpowers/specs/2026-07-20-devpost-submission-kit-design.md`.
- Produces: exact copy for the Devpost form and the canonical public wording reused by the judge guide and video.

- [ ] **Step 1: Create the submission worksheet with confirmed metadata**

Start the file with:

```markdown
# CodeDefense Devpost Submission Worksheet

> Private working document. Copy the appropriate values into Devpost.
> Do not add the private `/feedback` session ID value to this file.

## Project name

CodeDefense

## Elevator pitch

Turn AI-generated code into developer-owned understanding.

## Submitter type

Individual

## Country of residence

Ukraine

## Category

Developer Tools
```

- [ ] **Step 2: Add the complete approved About text**

Use these headings and claims verbatim unless proofreading fixes grammar without changing meaning:

```markdown
## About the project

### Inspiration

AI can produce working code faster than a developer can fully understand it. That creates a new problem: a diff may look correct and pass tests, while the person committing it cannot explain the design decision, predict a boundary case, or describe what should be tested.

Traditional code review examines the code. CodeDefense examines whether the developer understands the exact change they are about to commit.

I built CodeDefense to turn AI-assisted development from "the model generated it" into "the developer can defend it."

### What it does

CodeDefense conducts an evidence-grounded technical defense of an exact Git change.

A developer stages a change and starts a defense from the CLI, JetBrains IDE, or Codex plugin. CodeDefense:

1. captures a bounded snapshot of the staged Git hunks;
2. separates trusted application instructions from untrusted repository content;
3. uses GPT-5.6 through the locally authenticated Codex CLI to generate exactly three questions: the design decision, a counterfactual or boundary case, and a test prediction;
4. evaluates each answer and allows at most one adaptive follow-up;
5. calculates the authoritative final score locally in Java;
6. creates a repository-local Change Passport bound to the exact Git fingerprint;
7. shows which changed hunks were actually referenced through an Evidence Coverage Map;
8. detects when the Passport becomes expired after the staged change is modified;
9. verifies Passport continuity in GitHub Actions without invoking a model.

The Passport contains bounded metadata and structured assessment results, not the captured source snapshot. It is educational evidence of a completed defense—not proof of correctness, security, authorship, or approval to merge.

### How I built it

The core application is a Java 21 CLI built with Maven and Picocli. It uses a deliberately bounded filesystem and Git layer: excluded directories are pruned before traversal, symbolic-link boundaries are enforced, text reads are byte-limited, secrets are redacted, and the complete project snapshot is limited to 30 files and 120 KiB.

GPT-5.6 is accessed through the user's existing local Codex authentication. CodeDefense does not require an OpenAI API key or add an OpenAI SDK dependency. A structured adapter launches Codex with explicit arguments, bounded stdin/stdout handling, timeouts, process cleanup, and validated JSON schemas.

The product is available through three connected surfaces: a standalone executable CLI, a JetBrains plugin with an interactive defense cockpit, and a Codex plugin with a reusable skill and an advisory Stop hook.

A GitHub Actions workflow performs source-free Passport continuity checks. The model helps ask and assess the technical questions, while deterministic Java code owns scores, fingerprints, artifact state, privacy limits, and CI exit codes.

I collaborated with Codex throughout implementation: refining the architecture, testing privacy boundaries, reviewing staged-diff behavior, diagnosing Windows launcher issues, and iterating on the JetBrains and Codex integrations. GPT-5.6 also powers the product's structured question generation and answer evaluation.

### Challenges

The hardest challenge was binding an AI conversation to the exact code being defended. Reading only the beginning of a large file was insufficient because the changed line could be far beyond that prefix. I replaced prefix-oriented context with bounded hunk-oriented evidence so the model sees the relevant changed lines and nearby context.

Cross-platform process execution was another major challenge. Windows npm installations expose Codex through a PowerShell shim, while native launchers use a different stdin contract. The adapter handles both without shell command strings and without exposing prompts in process arguments.

I also had to keep the product honest about privacy and trust. Repository content is treated as untrusted input, snapshots are bounded and redacted, generated artifacts exclude source snapshots, and CI verifies fingerprint continuity rather than claiming that an AI score proves correctness.

Finally, IDE integration required a responsive asynchronous bridge: buttons must not trigger duplicate sessions, long model operations must remain cancellable, and the UI must distinguish CURRENT, EXPIRED, UNDEFENDED, and no-staged-change states clearly.

### What I learned

The most important lesson is that confidence in AI-assisted code should be attached to a concrete artifact, not to a chat transcript.

A useful trust workflow needs both AI and deterministic software. AI can generate context-aware questions and explain knowledge gaps. Deterministic code must control boundaries, identity, scoring rules, persistence, and enforcement. A good developer tool must state what it does not prove as clearly as what it does.

I also learned that integration changes the value of an idea. The same technical defense becomes much more useful when it is available where developers already work: inside JetBrains, inside Codex, at commit time, and in CI.

### What's next

The next step is Delta Defense: comparing consecutive Passports to ask only what changed since the previous successful defense. I would also expand the Evidence Coverage Map, add team-level policy configuration, and explore optional provenance signals while keeping the core Passport independent from unverifiable authorship claims.
```

- [ ] **Step 3: Add tags, links, and private-field instructions**

Append:

```markdown
## Built with

Java, Java 21, OpenAI Codex, GPT-5.6, JetBrains, IntelliJ IDEA, Git,
GitHub Actions, CLI, Picocli, Maven, JUnit 5, Jackson, JLine, PowerShell,
CI/CD, Developer Tools, Privacy

## Try it out

- Source and documentation: https://github.com/DmitriyGrachev/CodeDefense
- Ready-to-use v0.1.0 downloads: https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0
- Automated builds: https://github.com/DmitriyGrachev/CodeDefense/actions

## Repository URL

https://github.com/DmitriyGrachev/CodeDefense

## Judge testing instructions

Use the exact text in `README-JUDGES.md` and the generated judge kit.
No credentials are bundled. A real defense uses the judge's own locally
authenticated Codex installation and may consume Codex credits. Dry-run,
Passport display, Evidence Coverage, and CI continuity invoke no model.

## Feedback Session ID

Enter the privately retained `/feedback` Session ID directly in Devpost.
Do not copy it into this repository or any public field.

## Video demo link

Add the final public YouTube URL only after completing the recording and
public-visibility checks in Task 7.

## Additional-info upload

Upload `codedefense-devpost-judge-kit-v0.1.0.zip` generated by the packaging script.
```

- [ ] **Step 4: Validate the worksheet**

Run:

```powershell
git diff --check
rg -n "Turn AI-generated code|Developer Tools|Ukraine|About the project|How I built it|What I learned|Feedback Session ID" docs/devpost/devpost-submission.md
rg -n "proof of correctness|approval to merge|OpenAI API key|public YouTube" docs/devpost/devpost-submission.md
```

Expected: no whitespace errors; every required section appears; the trust boundary and no-API-key statement are present; no actual feedback ID appears.

- [ ] **Step 5: Commit**

```powershell
git add docs/devpost/devpost-submission.md
git commit -m "docs: add Devpost submission copy"
```

---

### Task 2: Add the standalone judge guide and sharpen the README entry path

**Files:**
- Create: `docs/devpost/README-JUDGES.md`
- Modify: `README.md:1-31`
- Modify: `README.md:49-82`

**Interfaces:**
- Consumes: v0.1.0 artifact names and commands from the existing README and release.
- Produces: a one-minute model-free path, a complete staged-defense path, and the guide packaged in Task 5.

- [ ] **Step 1: Write the judge guide**

Create `docs/devpost/README-JUDGES.md` with these sections and commands:

````markdown
# CodeDefense Judge Guide

CodeDefense turns an exact staged Git change into an evidence-grounded
technical defense. GPT-5.6 asks and evaluates; deterministic Java owns
the final score, Git fingerprint, Passport state, and CI exit code.

## 60-second model-free evaluation

Requirements: Java 21 only.

1. Download `codedefense.jar` and `SHA256SUMS.txt` from the v0.1.0 release.
2. Verify the checksum.
3. Run:

```powershell
java -jar .\codedefense.jar --version
java -jar .\codedefense.jar --help
java -jar .\codedefense.jar sample --dry-run
```

Expected: successful version/help output and a bounded sample preview ending
with `No source content was sent.`, `No model request was made.`, and
`Codex was not invoked.`

## Full staged-change defense

Additional requirements: Git and a locally installed Codex CLI authenticated
with the judge's own OpenAI account.

```powershell
git add path\to\SupportedSource.java
java -jar .\codedefense.jar prove --staged --dry-run <repository>
java -jar .\codedefense.jar prove --staged <repository>
java -jar .\codedefense.jar passport show <repository>
java -jar .\codedefense.jar passport coverage <repository>
java -jar .\codedefense.jar passport verify <repository>
```

The real defense asks for explicit consent before sending bounded staged Git
context and may consume Codex credits.

## JetBrains plugin

Use IntelliJ IDEA build `261.*` or `262.*` on Windows. Install
`codedefense-jetbrains-0.1.0.zip` through Settings -> Plugins -> Install
Plugin from Disk, restart IntelliJ IDEA, open View -> Tool Windows ->
CodeDefense, and stage a supported source change before selecting Preview.

## Codex plugin

Install `codedefense-codex-plugin.zip` using the repository-local marketplace
instructions in the main README. Review and enable the advisory Stop hook,
then ask `@codedefense` for the source-free status of the staged change.

## Supported platforms

- CLI: Java 21; verified on Windows, with Maven verification on Windows and Ubuntu.
- JetBrains plugin: IntelliJ IDEA `261.*` and `262.*` on Windows.
- Codex plugin and Stop hook: verified on Windows.
- Passport CI check: Ubuntu GitHub Actions runner, source-free and model-free.

Installed macOS/Linux plugin acceptance and experimental Codex provenance are
outside the v0.1.0 acceptance claim.

## Privacy and interpretation

Preview, Passport display, Evidence Coverage, Learning Radar, and CI continuity
do not invoke Codex. The Passport excludes the captured source snapshot.

A Passport is evidence of a completed technical defense—not proof of
correctness, security, authorship, or approval to merge or deploy.

## Links

- Repository: https://github.com/DmitriyGrachev/CodeDefense
- Release: https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0
- Build history: https://github.com/DmitriyGrachev/CodeDefense/actions
- Demo video: added after public YouTube publication
````

- [ ] **Step 2: Update the README headline and hero reference**

Change line 3 to:

```markdown
> **Turn AI-generated code into developer-owned understanding.**
```

Change the hero reference to:

```markdown
![CodeDefense turns an exact staged change into a technical defense, Change Passport, and source-free CI continuity](docs/assets/devpost/01-hero.png)
```

The image is added in Task 4; do not delete the existing Cockpit capture because Task 4 uses it as source material.

- [ ] **Step 3: Add the fast judge path after the release-install section**

Insert after the sentence describing the release plugin archives:

````markdown
## 60-second model-free evaluation

Judges and first-time users can verify the packaged CLI without Codex credentials or a model request:

```powershell
java -jar .\codedefense.jar --version
java -jar .\codedefense.jar --help
java -jar .\codedefense.jar sample --dry-run
```

The dry run sends no source content, makes no model request, and consumes no credits. See the [complete judge guide](docs/devpost/README-JUDGES.md) for the staged-change, JetBrains, Codex plugin, and CI paths.
````

- [ ] **Step 4: Validate documentation contracts**

Run:

```powershell
git diff --check
rg -n "Turn AI-generated code|60-second model-free|README-JUDGES|No source content|not proof of" README.md docs/devpost/README-JUDGES.md
```

Expected: every phrase is present and Markdown fences are balanced in rendered preview.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/devpost/README-JUDGES.md
git commit -m "docs: add CodeDefense judge evaluation path"
```

---

### Task 3: Write the combined video production guide

**Files:**
- Create: `docs/devpost/video-production-guide.md`

**Interfaces:**
- Consumes: the approved three-surface narrative and public claims.
- Produces: the exact OBS setup, narration, capture order, and publication checks used in Task 7.

- [ ] **Step 1: Add the recording preflight**

Document these exact requirements:

```markdown
# CodeDefense Video Production Guide

## Recording target

- Public YouTube video
- English narration and subtitles
- 2:45-2:55 final duration; never 3:00 or longer
- OBS canvas/output: 1920 x 1080
- 30 FPS, H.264, 8-12 Mbps video, 48 kHz audio
- Screen recording only; webcam is optional and omitted from the planned cut

## Preflight

1. Use the v0.1.0 JAR and matching JetBrains/Codex plugins.
2. Close notifications, personal browser tabs, password managers, and unrelated terminals.
3. Use a disposable staged Java fixture with no secrets or personal data.
4. Prepare a current Passport and one intentional staged edit that demonstrates EXPIRED.
5. Open the JetBrains CodeDefense Tool Window, Codex plugin chat, GitHub Actions run, and a clean PowerShell terminal.
6. Set Windows and terminal output to UTF-8.
7. Record a 15-second audio sample and verify clarity before the product run.
8. Capture model waiting separately and remove idle time without implying instant execution.
```

- [ ] **Step 2: Add the exact timed narration and actions**

Use this script as the spoken baseline:

```markdown
## Timed script

### 0:00-0:15 — Problem and promise

**On screen:** Hero image, then a staged Git change in IntelliJ.

**Narration:**
"AI can write working code faster than a developer can fully understand it. CodeDefense turns that exact gap into a workflow: before I keep an AI-assisted change, I defend the decisions, edge cases, and expected tests."

### 0:15-1:15 — JetBrains defense

**On screen:** CodeDefense Tool Window showing UNDEFENDED, Preview defense, bounded counts, Start defense, one question/evaluation, then a cut to the completed session.

**Narration:**
"Here is one staged Java change. CodeDefense fingerprints the exact Git index and previews only bounded, redacted hunks. Nothing is sent until I confirm. GPT-5.6, reached through my locally authenticated Codex CLI, generates three evidence-grounded questions: the design decision, a counterfactual, and a test prediction. It can ask one focused follow-up. The model evaluates the answer, but Java owns the final score and state machine."

### 1:15-1:35 — Passport and Evidence Coverage

**On screen:** CURRENT Passport and the Evidence Coverage card; click one hunk location.

**Narration:**
"The completed defense creates a repository-local Change Passport bound to this fingerprint. Evidence Coverage shows which changed hunks the questions actually referenced. It is evidence use, not a claim of correctness or safety. If I change the staged diff, the Passport becomes expired."

### 1:35-1:58 — Codex-native integration

**On screen:** `@codedefense` returns CURRENT, then show the advisory Stop hook reporting EXPIRED after the prepared edit.

**Narration:**
"The same boundary is available inside Codex. The skill reads only source-free Passport status, and an advisory Stop hook warns when a Codex task ends with an undefended or expired staged change. It never starts a defense or sends source automatically."

### 1:58-2:20 — CLI and CI

**On screen:** `passport ci-check` source-free output and a green GitHub Actions summary.

**Narration:**
"The Passport fingerprint can travel in a commit trailer. GitHub Actions recalculates parent-to-commit continuity without Codex, without an API key, and without uploading a Passport or repository source. Teams can choose advisory or required policy."

### 2:20-2:43 — How Codex and GPT-5.6 were used

**On screen:** Architecture diagram and bounded-flow labels.

**Narration:**
"GPT-5.6 provides contextual question generation and structured evaluation. Deterministic Java controls privacy limits, scoring, fingerprints, persistence, and exit codes. I also built CodeDefense with Codex: turning review findings into regression tests, diagnosing the Windows PowerShell launcher, and hardening the Git and prompt boundaries."

### 2:43-2:55 — Close

**On screen:** Logo, pitch, repository URL.

**Narration:**
"CodeDefense does not prove that code is correct or safe. It proves something smaller and useful: a developer completed a defense of the exact change. Turn AI-generated code into developer-owned understanding."
```

- [ ] **Step 3: Add editing and upload checks**

Append:

```markdown
## Edit points

- Cut command typing after enough characters establish the action.
- Cut model waiting but retain the confirmation and resulting question.
- Do not speed up spoken evaluation text until it becomes unreadable.
- Use labels `Real product output` and `Waiting time removed` where a cut could be misunderstood.
- Blur or crop local user paths, notifications, account data, and the private feedback session ID.
- Use no copyrighted music; silence behind narration is acceptable.

## Final upload checklist

- [ ] Final duration is below 3:00.
- [ ] English voice is audible and synchronized.
- [ ] English subtitles were reviewed manually.
- [ ] The demo visibly covers JetBrains, Codex, and CLI/CI.
- [ ] The narration explains both product use of GPT-5.6 and development collaboration with Codex.
- [ ] No credentials, private session IDs, personal paths, raw JSON, prompts, schemas, or source snapshots are visible.
- [ ] The video does not claim correctness, security, authorship, or merge approval.
- [ ] YouTube visibility is Public.
- [ ] The title is `CodeDefense — Prove You Understand the Code AI Helped You Write`.
- [ ] The description links the repository and v0.1.0 release.
```

- [ ] **Step 4: Check script length and required claims**

Run:

```powershell
$Text = Get-Content -Raw docs/devpost/video-production-guide.md
$Spoken = [regex]::Matches($Text, '"[^"\r\n]+"') | ForEach-Object { $_.Value.Trim('"') }
$WordCount = (($Spoken -join ' ') -split '\s+' | Where-Object { $_ }).Count
$WordCount
rg -n "GPT-5.6|locally authenticated Codex CLI|I also built CodeDefense with Codex|does not prove|below 3:00|YouTube visibility is Public" docs/devpost/video-production-guide.md
```

Expected: spoken baseline is approximately 300-380 words and every mandatory claim appears.

- [ ] **Step 5: Commit**

```powershell
git add docs/devpost/video-production-guide.md
git commit -m "docs: script CodeDefense hackathon demo"
```

---

### Task 4: Produce the logo, hero, and six-image gallery

**Files:**
- Create: `docs/assets/devpost/codedefense-logo.svg`
- Create: `docs/assets/devpost/codedefense-logo.png`
- Create: `docs/assets/devpost/01-hero.png`
- Create: `docs/assets/devpost/02-jetbrains-defense.png`
- Create: `docs/assets/devpost/03-adaptive-evaluation.png`
- Create: `docs/assets/devpost/04-evidence-coverage.png`
- Create: `docs/assets/devpost/05-codex-plugin.png`
- Create: `docs/assets/devpost/06-passport-ci.png`
- Preserve: `docs/assets/codedefense-jetbrains-cockpit.png`

**Interfaces:**
- Consumes: real product screenshots/output, approved graphite/teal/purple identity, and exact public claims.
- Produces: Devpost logo, README hero, gallery assets, video title card, and judge-kit screenshots.

- [ ] **Step 1: Generate and select the logo concept**

Use the image-generation tool with this exact prompt:

```text
Create a clean flat vector-style app icon for a developer tool named CodeDefense.
Visual concept: a Git fingerprint made from three short diff/hunk lines inside
balanced code brackets, with a small precise check mark at the center. Dark
graphite background, bright teal primary strokes, restrained violet accent.
No shield, no lock, no text, no gradients that reduce small-size clarity, no
3D, no mockup. Geometric, modern, high contrast, legible at 32 px, square 1:1.
```

Inspect the result at full size and at 32 px. Reject any concept resembling a security certification badge or containing generated text.

- [ ] **Step 2: Create the durable SVG and PNG logo**

Rebuild the approved simple geometry as a deterministic SVG with a `1024 1024` viewBox, graphite background, teal bracket/fingerprint paths, violet accent, and centered check. Render the final PNG at 1024 x 1024. The SVG must contain no external URLs, scripts, fonts, embedded metadata, or raster data.

Verify:

```powershell
rg -n "<script|https?://|data:|font-family|metadata" docs/assets/devpost/codedefense-logo.svg
```

Expected: no matches.

- [ ] **Step 3: Build the hero image**

Use the logo and approved exact copy:

```text
CodeDefense
Turn AI-generated code into developer-owned understanding.
Staged Change -> Technical Defense -> Change Passport -> CI
```

Create a 1800 x 1200 composition with the logo on the left, product name and pitch on the right, and the four-stage flow along the bottom. Keep generous margins and ensure all text remains readable at Devpost card size.

- [ ] **Step 4: Capture and compose the five product gallery images**

Use only real product output. Capture these exact states:

1. JetBrains Tool Window with a staged change and Preview/Start controls.
2. One completed evaluation containing verdict, score, feedback, and follow-up.
3. Evidence Coverage card showing referenced and unreferenced hunks plus `Evidence use only — not correctness or safety coverage.`
4. Codex plugin chat showing source-free `CURRENT`, paired with the hook's `EXPIRED` warning.
5. Change Passport summary paired with a green GitHub Actions Passport continuity result.

Place each capture on the shared graphite background, add one short teal title, retain actual output pixels without altering scores/statuses, and remove unrelated UI. Do not include the private feedback session ID, account avatar/name, notifications, local absolute paths, browser bookmarks, or unrelated files.

- [ ] **Step 5: Verify dimensions, size, and privacy**

Run on Windows:

```powershell
Add-Type -AssemblyName System.Drawing
$Images = Get-ChildItem docs/assets/devpost -Filter '*.png'
foreach ($File in $Images) {
    $Image = [System.Drawing.Image]::FromFile($File.FullName)
    try {
        [pscustomobject]@{
            Name = $File.Name
            Width = $Image.Width
            Height = $Image.Height
            Bytes = $File.Length
        }
    } finally {
        $Image.Dispose()
    }
}
```

Expected: logo is 1024 x 1024; gallery files `01`-`06` are 1800 x 1200; every PNG is below 5,242,880 bytes.

Visually inspect every file with the local image viewer at original detail. Search the committed asset directory names and accompanying Markdown for private IDs or absolute user paths; binary screenshots must be inspected visually because text search is insufficient.

- [ ] **Step 6: Commit**

```powershell
git add docs/assets/devpost README.md
git commit -m "docs: add CodeDefense Devpost visual kit"
```

---

### Task 5: Add deterministic judge-kit packaging

**Files:**
- Create: `scripts/package-devpost-judge-kit.ps1`
- Generate: `target/codedefense-devpost-judge-kit-v0.1.0.zip`

**Interfaces:**
- Consumes: `target/codedefense.jar`, `target/codedefense-codex-plugin.zip`, `target/SHA256SUMS.txt`, the JetBrains distribution ZIP, `docs/devpost/README-JUDGES.md`, and six gallery PNGs.
- Produces: one bounded ZIP accepted by Devpost's 35 MB upload field.

- [ ] **Step 1: Write a failing packaging contract check**

Before creating the script, run:

```powershell
Test-Path scripts/package-devpost-judge-kit.ps1
```

Expected: `False`.

- [ ] **Step 2: Implement the packaging script**

The script must:

1. set `$ErrorActionPreference = 'Stop'`;
2. resolve the repository root from `$PSScriptRoot`;
3. require all three release artifacts, `SHA256SUMS.txt`, judge README, and six screenshots;
4. select exactly `codedefense-jetbrains-0.1.0.zip`;
5. verify the staging directory resolves beneath `<root>\target` before deleting it;
6. recreate `target\devpost-judge-kit\CodeDefense-Judge-Kit`;
7. copy the files using `-LiteralPath`;
8. copy gallery images beneath `screenshots`;
9. create `target\codedefense-devpost-judge-kit-v0.1.0.zip` with `Compress-Archive`;
10. fail if the archive is 35 MB or larger;
11. print the absolute archive path and byte count.

Use this concrete structure:

```powershell
$ErrorActionPreference = "Stop"

$RepositoryRoot = Split-Path -Parent $PSScriptRoot
$TargetRoot = Join-Path $RepositoryRoot "target"
$StageRoot = Join-Path $TargetRoot "devpost-judge-kit"
$KitRoot = Join-Path $StageRoot "CodeDefense-Judge-Kit"
$ScreenshotsRoot = Join-Path $KitRoot "screenshots"
$Archive = Join-Path $TargetRoot "codedefense-devpost-judge-kit-v0.1.0.zip"
$MaximumArchiveBytes = 35MB

$RequiredFiles = [ordered]@{
    "codedefense.jar" = Join-Path $TargetRoot "codedefense.jar"
    "codedefense-codex-plugin.zip" = Join-Path $TargetRoot "codedefense-codex-plugin.zip"
    "codedefense-jetbrains-0.1.0.zip" = Join-Path $RepositoryRoot "jetbrains-plugin\build\distributions\codedefense-jetbrains-0.1.0.zip"
    "SHA256SUMS.txt" = Join-Path $TargetRoot "SHA256SUMS.txt"
    "README-JUDGES.md" = Join-Path $RepositoryRoot "docs\devpost\README-JUDGES.md"
}

$ScreenshotNames = 1..6 | ForEach-Object { "{0:D2}-" -f $_ }
$GalleryRoot = Join-Path $RepositoryRoot "docs\assets\devpost"
$GalleryFiles = foreach ($Prefix in $ScreenshotNames) {
    $Matches = @(Get-ChildItem -LiteralPath $GalleryRoot -File -Filter "$Prefix*.png")
    if ($Matches.Count -ne 1) {
        throw "Expected exactly one gallery image with prefix $Prefix"
    }
    $Matches[0]
}

foreach ($Entry in $RequiredFiles.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $Entry.Value -PathType Leaf)) {
        throw "Missing judge-kit input: $($Entry.Value)"
    }
}

$ResolvedTarget = [IO.Path]::GetFullPath($TargetRoot).TrimEnd('\') + '\'
$ResolvedStage = [IO.Path]::GetFullPath($StageRoot)
if (-not $ResolvedStage.StartsWith($ResolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe judge-kit staging path."
}

if (Test-Path -LiteralPath $StageRoot) {
    Remove-Item -LiteralPath $StageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $ScreenshotsRoot -Force | Out-Null

foreach ($Entry in $RequiredFiles.GetEnumerator()) {
    Copy-Item -LiteralPath $Entry.Value -Destination (Join-Path $KitRoot $Entry.Key)
}
foreach ($File in $GalleryFiles) {
    Copy-Item -LiteralPath $File.FullName -Destination (Join-Path $ScreenshotsRoot $File.Name)
}

if (Test-Path -LiteralPath $Archive) {
    Remove-Item -LiteralPath $Archive -Force
}
Compress-Archive -LiteralPath $KitRoot -DestinationPath $Archive

$ArchiveFile = Get-Item -LiteralPath $Archive
if ($ArchiveFile.Length -ge $MaximumArchiveBytes) {
    throw "Judge kit exceeds the 35 MB Devpost limit: $($ArchiveFile.Length) bytes."
}

Write-Output $ArchiveFile.FullName
Write-Output "Bytes: $($ArchiveFile.Length)"
```

- [ ] **Step 3: Build required inputs without Codex**

Run:

```powershell
mvn package
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\scripts\package-codex-plugin.ps1
Push-Location .\jetbrains-plugin
.\gradlew.bat buildPlugin
Pop-Location
```

Expected: all three release artifacts exist; no command invokes `codex`.

- [ ] **Step 4: Run the packaging script and inspect the archive**

Run:

```powershell
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\scripts\package-devpost-judge-kit.ps1
Get-Item .\target\codedefense-devpost-judge-kit-v0.1.0.zip | Select-Object Name, Length
```

Expected: exit code 0 and length below 36,700,160 bytes.

- [ ] **Step 5: Commit**

```powershell
git add scripts/package-devpost-judge-kit.ps1
git commit -m "build: package Devpost judge kit"
```

---

### Task 6: Perform complete offline submission-kit verification

**Files:**
- Verify: all files from Tasks 1-5
- Generate only beneath: `target/`

**Interfaces:**
- Consumes: completed public materials and packaged v0.1.0 outputs.
- Produces: exact offline verification log and a clean, extractable judge ZIP.

- [ ] **Step 1: Run core verification**

```powershell
mvn clean verify
mvn package
java -jar target\codedefense.jar --help
java -jar target\codedefense.jar --version
java -jar target\codedefense.jar sample --dry-run
```

Expected: Maven BUILD SUCCESS with the repository's current expected skips; CLI commands exit 0; dry-run contains `No source content was sent.`, `No model request was made.`, and `Codex was not invoked.`

- [ ] **Step 2: Run plugin packaging and offline tests**

```powershell
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\scripts\package-codex-plugin.ps1
Push-Location .\jetbrains-plugin
.\gradlew.bat test buildPlugin verifyPlugin
Pop-Location
```

Expected: BUILD SUCCESSFUL; verifier reports compatibility for IntelliJ IDEA `261.*` and `262.*`; no real Codex call occurs.

- [ ] **Step 3: Build and extract the judge kit safely**

```powershell
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\scripts\package-devpost-judge-kit.ps1
$ExtractRoot = Join-Path $env:TEMP "codedefense-devpost-judge-kit-check"
Remove-Item -LiteralPath $ExtractRoot -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive -LiteralPath .\target\codedefense-devpost-judge-kit-v0.1.0.zip -DestinationPath $ExtractRoot
Get-ChildItem -LiteralPath $ExtractRoot -Recurse | Select-Object FullName, Length
```

Expected: one `CodeDefense-Judge-Kit` directory with five root files and six screenshots.

- [ ] **Step 4: Run the documented model-free path from the extraction**

```powershell
$Kit = Join-Path $ExtractRoot "CodeDefense-Judge-Kit"
java -jar (Join-Path $Kit "codedefense.jar") --version
java -jar (Join-Path $Kit "codedefense.jar") --help
java -jar (Join-Path $Kit "codedefense.jar") sample --dry-run
```

Expected: exit code 0 for each command; no Codex invocation.

- [ ] **Step 5: Verify public links and repository hygiene**

Open these URLs without an authenticated browser session:

```text
https://github.com/DmitriyGrachev/CodeDefense
https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0
https://github.com/DmitriyGrachev/CodeDefense/actions
```

Then run:

```powershell
git diff --check
git status --short
git ls-files target
$Forbidden = @(
    'T' + 'BD',
    'T' + 'ODO',
    [string]::Concat('re', 'place me'),
    [string]::Concat('example', '.com'),
    [string]::Concat('local', 'host'),
    [string]::Concat('C:', '\Users\'),
    'AppData'
)
Select-String -Path README.md,docs/devpost/*.md -Pattern $Forbidden
```

Expected: no tracked files beneath `target`; no temporary markers, localhost links, or personal Windows paths in submission materials.

- [ ] **Step 6: Save exact verification results in the final task report**

Report Maven test counts, Gradle test counts, skips, artifact sizes, image dimensions, ZIP size, CLI exit codes, and confirmation that no automated command invoked Codex. Do not create a repository log file unless explicitly requested.

---

### Task 7: Record, publish, and link the final demo

**Files:**
- Modify: `docs/devpost/devpost-submission.md`
- Modify: `docs/devpost/README-JUDGES.md`
- Modify: `README.md`
- Regenerate: `target/codedefense-devpost-judge-kit-v0.1.0.zip`

**Interfaces:**
- Consumes: the approved video guide, completed visual kit, and one deliberate real product run.
- Produces: a public sub-three-minute YouTube demo linked consistently from every judge surface.

- [ ] **Step 1: Prepare the disposable recording change**

Use a small supported Java change with no secrets. Capture UNDEFENDED, preview, explicit confirmation, the three-question defense, CURRENT Passport, Evidence Coverage, and one separate edit that demonstrates EXPIRED. Never use the private feedback session ID or a personal repository path on screen.

- [ ] **Step 2: Record the one deliberate real-Codex product run**

Follow `docs/devpost/video-production-guide.md`. Record JetBrains, Codex plugin, and CLI/CI segments separately so waiting time can be cut cleanly. Do not rerun merely to obtain a preferred score; the demo explains the workflow, not a perfect grade.

- [ ] **Step 3: Edit and validate the final video**

Apply English subtitles, required disclosure labels around cuts, and the exact approved title card. Confirm locally that duration is below 3:00, audio is intelligible, no private data appears, and every product claim matches captured behavior.

- [ ] **Step 4: Publish publicly on YouTube**

Use this exact title:

```text
CodeDefense — Prove You Understand the Code AI Helped You Write
```

Use this description:

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
https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0
```

Set visibility to `Public`, open the URL in a signed-out/incognito browser, and confirm playback with audio.

- [ ] **Step 5: Add the exact public URL to all materials**

Replace the pre-publication video note in `docs/devpost/devpost-submission.md` and `docs/devpost/README-JUDGES.md` with the exact public YouTube URL. Add a `Demo video` link near the top of `README.md`. Do not use a shortened URL.

- [ ] **Step 6: Rebuild and re-verify the judge package**

```powershell
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\scripts\package-devpost-judge-kit.ps1
Get-Item .\target\codedefense-devpost-judge-kit-v0.1.0.zip | Select-Object Name, Length
git diff --check
```

Expected: archive remains below 35 MB; all three documents contain the identical public YouTube URL.

- [ ] **Step 7: Commit the final demo links**

```powershell
git add README.md docs/devpost/devpost-submission.md docs/devpost/README-JUDGES.md
git commit -m "docs: add CodeDefense hackathon demo"
```

- [ ] **Step 8: Stop before Devpost submission**

Report the public video URL, duration, final ZIP size, and ready-to-copy worksheet. Do not click `Submit project`; the user will review the Devpost preview and perform the final submission separately.
