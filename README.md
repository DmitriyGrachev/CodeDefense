# CodeDefense

CodeDefense is a Java 21 command-line application that helps developers prove they understand an AI-assisted local codebase by conducting a bounded, repository-specific technical defense through the locally authenticated Codex CLI.

## MVP boundaries

- Local directories only; no GitHub URL ingestion, web UI, database, or source execution.
- No OpenAI API key and no Spring dependency.
- The eventual defense asks exactly three primary questions, with at most one follow-up each.
- A future scanner will select at most 30 files and a 120 KiB snapshot, then require preview and confirmation before source content is sent.

## Current status

Iterations 0–1 provide the executable CLI foundation. `start`, `sample`, and `report` are intentionally safe placeholders; scanning, Codex invocation, reporting, and the embedded sample are deferred to their planned iterations.

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
```

See [the implementation plan](docs/codedefense-mvp-implementation-plan.md) and [the iteration checklist](docs/implementation-checklist.md).
