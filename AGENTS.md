# CodeDefense Development Rules

## Product

CodeDefense is a Java CLI application that analyzes a bounded snapshot
of a local repository and conducts an adaptive technical project defense
through the locally authenticated Codex CLI.

## Current MVP constraints

- Use Java 21.
- Use Maven.
- Do not use Spring or Spring Boot.
- Analyze local directories only.
- Do not add GitHub URL ingestion.
- Do not add a web interface.
- Do not add a database.
- Do not require an OpenAI API key.
- Use Codex CLI behind the AiProvider interface.
- Do not execute analyzed source code.
- Ask exactly three primary questions.
- Allow at most one follow-up per primary question.
- Select at most 30 files.
- Limit the repository snapshot to 120 KiB.
- Require preview and confirmation before sending source content.
- Keep domain models independent from Picocli, JLine, Jackson adapters,
  and ProcessBuilder.
- Automated tests must never call real Codex.

## Development process

- Read docs/implementation-plan.md before changing code.
- Implement only the explicitly requested iteration.
- Do not begin the next iteration automatically.
- Use small focused classes.
- Add tests with each behavior.
- Do not claim that a command works without running it.
- Do not expand the scope without explicit approval.

## Required verification

Run before completing an iteration:

```bash
mvn clean verify
```

For CLI or packaging changes also run:

```bash
mvn package
java -jar target/codedefense.jar --help
```

Report the exact commands and their results.