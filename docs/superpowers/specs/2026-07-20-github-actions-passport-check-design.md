# Iteration 8.19 — GitHub Actions Passport Check Design

## Goal

Extend the local Change Passport workflow to GitHub Actions with a source-free, model-free fingerprint continuity check for commits in a pull request or push.

The check answers one bounded question: does each commit's exact Git diff fingerprint match its `CodeDefense-Passport` commit trailer? It does not prove who completed a defense, whether answers were correct, whether code is safe, or whether a change should be merged or deployed.

## Chosen approach

The MVP uses the existing commit trailer:

```text
CodeDefense-Passport: sha256:<64 lowercase hex>
```

It does not upload Passport receipts, read the developer's local Passport store, call the GitHub API, create a custom Check Run, require an OpenAI key, authenticate Codex, or run a model.

Alternatives were rejected for this iteration:

- uploading receipts adds artifact lifecycle and privacy decisions;
- GitHub API checks require write permissions and token handling;
- signatures and identity attestation require a separate trust design.

## CLI contract

The new command is:

```text
codedefense passport ci-check \
  --base <BASE_REVISION> \
  --head <HEAD_REVISION> \
  --policy advisory|required \
  --format text|json|github \
  [PATH]
```

Defaults are `policy=advisory`, `format=text`, and `PATH=.`.

The command:

1. validates the repository without following unsafe filesystem links;
2. resolves base and head once to immutable commit IDs;
3. lists at most 50 commits in `base..head`, oldest first;
4. reads each commit message with fixed Git argument tokens;
5. parses exactly one strict trailer from the final trailer block;
6. captures the exact parent-to-commit change through the existing bounded Git adapter;
7. compares the calculated full diff fingerprint with the trailer value;
8. emits a bounded source-free result.

No shell command string, external diff driver, or textconv process is used.

## Status model

Each commit has one status:

- `MATCHED`: one valid trailer equals the calculated fingerprint;
- `MISSING`: no CodeDefense trailer exists;
- `MISMATCH`: the trailer is malformed, duplicated, or differs from the calculated fingerprint;
- `UNAVAILABLE`: the commit change cannot be captured safely or unambiguously.

Merge commits, root commits without an unambiguous parent diff, ranges exceeding 50 commits, shallow history, and Git capture failures are `UNAVAILABLE`.

`MATCHED` must always be described as fingerprint continuity, never verification, certification, authorship, approval, or readiness.

## Policy and exit behavior

### Advisory

- all `MATCHED`, `MISSING`, and `MISMATCH` combinations return exit code 0;
- missing and mismatched commits are visibly reported as warnings;
- invalid usage, invalid repository, unsafe range, or any `UNAVAILABLE` commit returns a documented nonzero exit code.

### Required

- every commit must be `MATCHED` for exit code 0;
- `MISSING`, `MISMATCH`, and `UNAVAILABLE` return a documented nonzero exit code.

The action defaults to advisory. Repository owners may opt into required mode and GitHub branch protection separately.

## Output contracts

Text output is concise terminal output. JSON output is strict, versioned, deterministic, bounded, and suitable for another local adapter.

GitHub output is Markdown suitable for `$GITHUB_STEP_SUMMARY`:

```text
## CodeDefense Passport Continuity

| Commit | Status | Passport |
|---|---|---|
| a12bc34 | MATCHED | bc41e641d947 |
| d45ef67 | MISSING | — |

Source sent by CI: no
Codex invoked by CI: no

Fingerprint continuity only — not merge or deployment approval.
```

All formats omit paths, source, diff text, repository name, Passport scores, readiness, questions, answers, feedback, evidence, model data, user identity, commit messages, and environment values. Commit IDs and Passport fingerprints are shortened for human output; strict JSON may retain full immutable IDs needed by an adapter.

## GitHub composite action

The reusable action lives at:

```text
.github/actions/passport-check/action.yml
```

Inputs:

- `base`: required base commit;
- `head`: required head commit;
- `policy`: `advisory` by default or `required`;
- `repository`: caller workspace, defaulting to `${{ github.workspace }}`.

The composite action:

1. uses `actions/setup-java` for Java 21 with Maven caching;
2. builds the action's pinned CodeDefense source with `-DskipTests`;
3. runs only `passport ci-check` against the caller workspace;
4. appends bounded GitHub-format output to `$GITHUB_STEP_SUMMARY`;
5. preserves the command's policy exit code.

It requests no GitHub write permission and uses no secrets.

Callers must checkout full history:

```yaml
name: CodeDefense Passport

on:
  pull_request:

jobs:
  passport:
    runs-on: ubuntu-latest
    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: DmitriyGrachev/CodeDefense/.github/actions/passport-check@v1
        with:
          base: ${{ github.event.pull_request.base.sha }}
          head: ${{ github.event.pull_request.head.sha }}
          policy: advisory
```

The repository also receives an advisory example workflow that handles pull-request and push base/head values explicitly. It must not run a defense or create a Passport.

The snippet above is intentionally pull-request-only. The repository's complete advisory example handles pull-request and push events in separate steps so that it never reads `pull_request` fields during a push.

## Components

The core adds focused boundaries:

- `PassportTrailerParser`: strict final trailer-block parsing;
- `CommitPassportContinuityChecker`: commit enumeration, bounded Git capture, and comparison;
- `CiPassportStatus` and immutable per-commit/result records;
- deterministic text, JSON, and GitHub renderers;
- `PassportCiCheckCommand`: Picocli adapter and policy-to-exit-code mapping.

Git process execution reuses the existing bounded `ProcessExecutor`, revision resolver, and Git change capture. The domain remains independent from Picocli, GitHub YAML, environment variables, and `ProcessBuilder`.

## Security and reliability

- Base/head and commit IDs are passed as fixed Git tokens, never interpolated into a shell command.
- Commit messages are untrusted, bounded, strictly decoded UTF-8 input and never echoed.
- Duplicate, mixed-case, malformed, embedded, or non-final trailers cannot produce `MATCHED`.
- The action is source-free and model-free.
- No receipt, Passport Markdown, local home directory, Codex session, or credentials are read.
- Output is capped and sanitized for GitHub Markdown/workflow-command injection.
- A forged trailer can still match a publicly computable fingerprint. Documentation must state this explicitly.

## Minimum focused tests

The implementation adds only the essential regression coverage:

1. a fixture staged fingerprint remains equal to the resulting ordinary commit fingerprint and trailer;
2. one checker test covers `MATCHED`, `MISSING`, and `MISMATCH` commits;
3. one policy test proves advisory and required exit behavior differs;
4. one strict parser test rejects duplicate and malformed trailers;
5. one renderer test proves source-free bounded GitHub output;
6. one static action/workflow contract test proves Java 21, full checkout, read-only permission, and absence of secrets or Codex invocation.

No automated test invokes real Codex or requires network access.

## Acceptance

Offline acceptance creates a disposable Git repository, makes defended-style commits with matching, missing, and mismatched trailers, and runs both policies from the packaged JAR.

GitHub acceptance pushes an advisory workflow once and confirms:

- the job completes successfully for advisory `MISSING` or `MISMATCH`;
- the Step Summary contains only bounded source-free continuity results;
- required mode fails the same mismatch;
- no Codex request, secret, Passport upload, source upload, or write permission is used.
