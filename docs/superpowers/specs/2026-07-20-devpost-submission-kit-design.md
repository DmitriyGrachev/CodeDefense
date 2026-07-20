# Devpost Submission Kit Design

## Goal

Prepare a complete, truthful, judge-friendly OpenAI Build Week submission kit for CodeDefense before recording and publishing the final demo video.

The kit must make the product understandable in under three minutes, provide a fast model-free testing path, document the optional real-Codex path, and avoid claims that a Change Passport proves correctness, security, authorship, or approval to merge or deploy.

This work prepares submission materials only. It does not submit the Devpost entry, make real Codex calls, or change CodeDefense application behavior.

## Confirmed submission facts

- Project name: `CodeDefense`
- Submitter type: `Individual`
- Country of residence: `Ukraine`
- Category: `Developer Tools`
- Public repository: `https://github.com/DmitriyGrachev/CodeDefense`
- Release: `https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0`
- License: MIT
- Public materials and narration language: English
- Recording tool: OBS
- Video host: public YouTube video
- Feedback session ID: stored only in the private Devpost field and intentionally excluded from repository files, screenshots, release assets, and public copy

## Positioning

The approved elevator pitch is:

> Turn AI-generated code into developer-owned understanding.

The core story is that AI can produce working code faster than a developer can fully understand it. Traditional review examines the code; CodeDefense asks whether the developer can explain the exact change they are about to commit.

The public product summary is:

> CodeDefense is a privacy-first developer tool that asks developers to defend the exact Git change they are about to commit. GPT-5.6, accessed through the locally authenticated Codex CLI, generates evidence-grounded questions and evaluates the answers. CodeDefense then creates a repository-local Change Passport, measures evidence coverage, integrates with JetBrains and Codex, and verifies Passport continuity in CI. Java—not the model—owns the final score, Git identity, and artifact status.

Every public surface must retain this boundary:

> A CodeDefense Passport is evidence of a completed technical defense—not proof of correctness, security, authorship, or approval to merge or deploy.

## Narrative structure

The Devpost story uses six sections:

1. `Inspiration`: AI-assisted code can outpace developer understanding.
2. `What it does`: exact-change capture, adaptive defense, local scoring, Passport, Evidence Coverage, stale-state detection, and CI continuity.
3. `How I built it`: Java 21 CLI, bounded Git/filesystem adapters, structured Codex runtime, JetBrains plugin, Codex plugin, and GitHub Actions.
4. `Challenges`: hunk-oriented evidence, cross-platform Codex launchers, prompt/privacy boundaries, and responsive IDE integration.
5. `What I learned`: AI should provide contextual reasoning while deterministic software owns identity, limits, scoring, persistence, and enforcement.
6. `What's next`: Delta Defense, richer coverage, team policy, and carefully bounded optional provenance.

The story is written in first person because the submission is individual. It explicitly explains both how Codex accelerated development and how GPT-5.6 powers structured question generation and answer evaluation in the product.

## Built-with tags

The proposed Devpost tags are:

- Java
- Java 21
- OpenAI Codex
- GPT-5.6
- JetBrains
- IntelliJ IDEA
- Git
- GitHub Actions
- CLI
- Picocli
- Maven
- JUnit 5
- Jackson
- JLine
- PowerShell
- CI/CD
- Developer Tools
- Privacy

## Public links

The initial `Try it out` links are:

1. source and documentation: `https://github.com/DmitriyGrachev/CodeDefense`
2. ready-to-use downloads: `https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0`
3. automated builds: `https://github.com/DmitriyGrachev/CodeDefense/actions`

The public YouTube URL is added after recording. CodeDefense has no hosted web demo; the GitHub Release is the supported downloadable evaluation path.

## Judge testing path

The fastest path must require no model call:

```text
java -jar codedefense.jar --help
java -jar codedefense.jar sample --dry-run
```

The guide must state that dry-run sends no source, makes no model request, consumes no credits, and requires no OpenAI API key.

The complete staged-change path requires Java 21, Git, and a locally installed Codex CLI authenticated with the judge's own OpenAI account:

```text
java -jar codedefense.jar prove --staged --dry-run <repository>
java -jar codedefense.jar prove --staged <repository>
java -jar codedefense.jar passport show <repository>
java -jar codedefense.jar passport coverage <repository>
java -jar codedefense.jar passport verify <repository>
```

The JetBrains path uses IntelliJ IDEA builds `261.*` or `262.*` on Windows and the release ZIP installed through `Settings -> Plugins -> Install Plugin from Disk`.

The Codex plugin path uses the release ZIP and the repository-local marketplace instructions already documented in the README. No credentials or test account are bundled.

## Supported-platform claims

Public claims remain intentionally narrow:

- CLI: Java 21; verified on Windows, with automated Maven verification on Windows and Ubuntu.
- Codex integration: locally installed and authenticated Codex CLI.
- JetBrains plugin: IntelliJ IDEA builds `261.*` and `262.*` on Windows.
- Codex plugin and advisory Stop hook: verified on Windows.
- GitHub Actions Passport continuity: Ubuntu runner, source-free and model-free.
- Installed macOS/Linux launcher acceptance and experimental Codex provenance remain outside the v0.1.0 acceptance claim.

## Visual identity

The approved visual direction uses:

- dark graphite backgrounds;
- teal as the primary product color;
- purple as an AI/Codex accent;
- a Git fingerprint, code brackets, and check motif;
- no generic shield that could imply a security guarantee.

The logo must remain legible both as a small plugin icon and a large Devpost logo. Source SVG and a rendered PNG are retained.

## Image gallery

The gallery contains six 3:2 PNG images at 1800 x 1200 and below Devpost's 5 MB per-image limit:

1. hero: name, elevator pitch, and `Staged Change -> Technical Defense -> Change Passport -> CI` flow;
2. JetBrains Defense Cockpit: staged selector, focus, question, and controls;
3. adaptive evaluation: verdict, score, feedback, and follow-up;
4. Evidence Coverage Map: referenced and unreferenced hunks plus the evidence-only disclaimer;
5. Codex-native integration: `@codedefense` current status and the advisory Stop hook;
6. Passport and CI continuity: Change Passport paired with a successful GitHub Actions result.

Raw full-screen captures are not submitted. Screenshots are cropped, sensitive or irrelevant UI is removed, important content is enlarged, and short explanatory captions are added. Screenshots must not expose credentials, private session IDs, local personal paths, notifications, or unrelated application content.

## Video design

The video is recorded with OBS at 1920 x 1080, 30 FPS, with English narration and English subtitles. It is edited to 2:45-2:55 and published publicly on YouTube.

The chosen sequence is one connected workflow across three surfaces:

- `0:00-0:15`: problem and elevator pitch;
- `0:15-1:20`: JetBrains staged preview, defense, and resulting Passport/Evidence Coverage;
- `1:20-1:50`: Codex plugin source-free status and advisory hook;
- `1:50-2:15`: CLI/GitHub Actions continuity check;
- `2:15-2:40`: architecture, privacy boundary, and the division of responsibility between GPT-5.6 and deterministic Java;
- `2:40-2:55`: closing statement and repository link.

Model waiting, command typing, and repetitive questions are shortened with transparent editing cuts. The demo still uses real captured product output. It does not imply that all model work completed instantaneously.

The working video document is a single file, `docs/devpost/video-production-guide.md`, combining preparation, narration, on-screen actions, edit points, subtitle text, and final upload checks.

The proposed YouTube title is:

> CodeDefense — Prove You Understand the Code AI Helped You Write

The description links the repository and v0.1.0 release and states that the demo covers JetBrains, adaptive defense, Change Passports, Evidence Coverage, Codex-native status, and source-free CI continuity.

## Judge package

The generated file is:

```text
target/codedefense-devpost-judge-kit-v0.1.0.zip
```

It remains below 35 MB and is not committed. Its planned contents are:

```text
CodeDefense-Judge-Kit/
|-- README-JUDGES.md
|-- codedefense.jar
|-- codedefense-codex-plugin.zip
|-- codedefense-jetbrains-0.1.0.zip
|-- SHA256SUMS.txt
`-- screenshots/
    |-- 01-hero.png
    |-- 02-jetbrains-defense.png
    |-- 03-adaptive-evaluation.png
    |-- 04-evidence-coverage.png
    |-- 05-codex-plugin.png
    `-- 06-passport-ci.png
```

`README-JUDGES.md` contains the fast and full testing paths, prerequisites, platform matrix, privacy limits, known limitations, artifact checksums, and public links. It never contains credentials or the private feedback session ID.

## Repository artifacts

The implementation phase creates or updates:

```text
README.md
docs/devpost/devpost-submission.md
docs/devpost/README-JUDGES.md
docs/devpost/video-production-guide.md
docs/assets/devpost/codedefense-logo.svg
docs/assets/devpost/codedefense-logo.png
docs/assets/devpost/01-hero.png
docs/assets/devpost/02-jetbrains-defense.png
docs/assets/devpost/03-adaptive-evaluation.png
docs/assets/devpost/04-evidence-coverage.png
docs/assets/devpost/05-codex-plugin.png
docs/assets/devpost/06-passport-ci.png
scripts/package-devpost-judge-kit.ps1
```

The private feedback session ID is entered manually into Devpost and must not be written to any of these files.

## Verification

Before the materials are considered complete:

1. validate every public claim against the packaged v0.1.0 behavior;
2. confirm every public URL resolves without authentication;
3. ensure each image is 3:2, below 5 MB, and free of private data;
4. build the judge ZIP and confirm it remains below 35 MB;
5. extract the ZIP into a clean temporary directory and verify its checksums and documented dry-run commands;
6. proofread the English copy manually so it sounds personal rather than generic;
7. verify the final YouTube video is public, under three minutes, includes audio, and explains both Codex collaboration and GPT-5.6 product usage;
8. add the final YouTube URL to Devpost, README, judge guide, and release notes;
9. submit only after the Devpost preview contains every required field and the entry is no longer saved as a draft.

No automated verification may invoke real Codex. One deliberate product recording run may use Codex, but it is a separate manual acceptance action.
