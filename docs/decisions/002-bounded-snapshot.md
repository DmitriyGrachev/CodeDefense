# ADR 002: Send a bounded snapshot, not repository access

## Decision

Send only a selected, redacted snapshot capped at 30 files and 120 KiB after preview and confirmation.

## Rationale

This limits exposure, makes the analysis deterministic, and prevents the AI runtime from reading or executing excluded repository files. Symbolic links, secrets files, dependencies, generated output, and binaries will be excluded.
