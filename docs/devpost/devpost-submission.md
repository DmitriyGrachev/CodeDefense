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
public-visibility checks in Task 7 of the submission-kit plan.

## Additional-info upload

Upload `codedefense-devpost-judge-kit-v0.1.0.zip` generated by the packaging script.
