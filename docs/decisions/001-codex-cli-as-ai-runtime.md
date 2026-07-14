# ADR 001: Use Codex CLI as the AI runtime

## Decision

Use the locally authenticated Codex CLI behind an `AiProvider` boundary.

## Rationale

The MVP must not require an OpenAI API key or persist credentials. The future adapter will invoke `codex exec` through `ProcessBuilder`, pass prompts through standard input, and remain replaceable behind the port.
