# GitHub Actions Passport Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a source-free GitHub Actions check that compares commit Passport trailers with deterministic parent-to-commit fingerprints.

**Architecture:** A bounded Git reader resolves a base/head range and produces commit inputs for a strict trailer parser and continuity checker. Picocli renders text, JSON, or GitHub Step Summary output; a composite action builds the pinned CodeDefense source and invokes only this model-free command.

**Tech Stack:** Java 21, Maven, Picocli, Jackson already present, Git CLI through the existing bounded `ProcessExecutor`, GitHub composite actions, JUnit 5.

## Global Constraints

- No Codex invocation, API key, GitHub API call, receipt upload, source upload, or write permission.
- At most 50 commits; commit messages and Git output are bounded and never echoed.
- `MATCHED` means fingerprint continuity only, not identity, correctness, safety, or approval.
- Advisory is the default; required mode is opt-in.
- Tests use disposable local Git repositories only and remain network-free.

---

### Task 1: Commit range and trailer contracts

**Files:**
- Create: `src/main/java/dev/codedefense/ci/CiPassportStatus.java`
- Create: `src/main/java/dev/codedefense/ci/PassportTrailer.java`
- Create: `src/main/java/dev/codedefense/ci/PassportTrailerParser.java`
- Create: `src/main/java/dev/codedefense/ci/CommitContinuityResult.java`
- Create: `src/main/java/dev/codedefense/ci/PassportContinuityResult.java`
- Create: `src/main/java/dev/codedefense/ci/GitCommitRangeReader.java`
- Modify: `src/main/java/dev/codedefense/change/GitCliChangeSource.java`
- Test: `src/test/java/dev/codedefense/ci/PassportTrailerParserTest.java`

**Interfaces:**
- `PassportTrailerParser.parse(String)` distinguishes missing, valid, and malformed/duplicate trailers.
- `GitCommitRangeReader.read(Path, String base, String head)` returns oldest-first immutable commits with bounded messages.
- `GitCliChangeSource.capturePassportFingerprint(Path, CommitSelector)` returns the staged-format parent-to-commit fingerprint used by commit trailers.

- [ ] **Step 1: Write strict parser tests**

Cover one valid final trailer, missing trailer, duplicate trailer, malformed hash, mixed-case key, and a trailer-like line outside the final trailer block.

- [ ] **Step 2: Run parser tests and verify RED**

Run: `mvn -Dtest=PassportTrailerParserTest test`

- [ ] **Step 3: Implement immutable contracts and bounded Git range reader**

Use fixed command tokens for `rev-parse`, `merge-base --is-ancestor`, `rev-list --reverse --max-count=51`, `rev-list --parents -n 1`, and `show -s --format=%B`. Reject more than 50 commits, root/merge commits, shallow/unavailable history, truncated output, and malformed object IDs as unavailable.

- [ ] **Step 4: Run parser tests**

Run: `mvn -Dtest=PassportTrailerParserTest test`

- [ ] **Step 5: Commit the contracts**

```powershell
git add src/main/java/dev/codedefense/ci src/main/java/dev/codedefense/change/GitCliChangeSource.java src/test/java/dev/codedefense/ci/PassportTrailerParserTest.java
git commit -m "feat: read bounded Passport commit ranges"
```

### Task 2: Continuity checker and policies

**Files:**
- Create: `src/main/java/dev/codedefense/ci/CommitPassportContinuityChecker.java`
- Create: `src/main/java/dev/codedefense/ci/CiPassportPolicy.java`
- Test: `src/test/java/dev/codedefense/ci/CommitPassportContinuityCheckerTest.java`

**Interfaces:**
- `CommitPassportContinuityChecker.check(Path, base, head)` returns one result per commit.
- `CiPassportPolicy.exitCode(PassportContinuityResult)` maps advisory/required behavior without Picocli.

- [ ] **Step 1: Write one disposable-repository test**

Create three ordinary commits with matching, missing, and mismatched trailers. Assert statuses and prove the staged fingerprint before a commit equals the fingerprint recalculated from the resulting parent-to-commit diff.

- [ ] **Step 2: Run the checker test and verify RED**

Run: `mvn -Dtest=CommitPassportContinuityCheckerTest test`

- [ ] **Step 3: Implement checker and policy mapping**

Use:

```java
public enum CiPassportStatus { MATCHED, MISSING, MISMATCH, UNAVAILABLE }
public enum CiPassportPolicy { ADVISORY, REQUIRED }
```

Advisory returns success for matched/missing/mismatch and failure for unavailable. Required returns success only when every commit is matched.

- [ ] **Step 4: Run the checker test and verify GREEN**

Run: `mvn -Dtest=CommitPassportContinuityCheckerTest test`

- [ ] **Step 5: Commit the checker**

```powershell
git add src/main/java/dev/codedefense/ci src/test/java/dev/codedefense/ci/CommitPassportContinuityCheckerTest.java
git commit -m "feat: check commit Passport continuity"
```

### Task 3: Picocli command and bounded renderers

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportCiCheckCommand.java`
- Create: `src/main/java/dev/codedefense/ci/PassportContinuityRenderer.java`
- Test: `src/test/java/dev/codedefense/cli/PassportCiCheckCommandTest.java`

**Interfaces:**
- Adds `passport ci-check --base BASE --head HEAD [--policy advisory|required] [--format text|json|github] [PATH]`.

- [ ] **Step 1: Write one command/renderer test**

Assert advisory mismatch exits 0, required mismatch exits nonzero, and GitHub output contains shortened commit/fingerprint values plus source-free/model-free disclaimer while excluding paths, messages, source, questions, scores, and workflow commands.

- [ ] **Step 2: Implement command and renderers**

Use Picocli output/error writers. Sanitize GitHub Markdown and cap output. Invalid range/repository maps to existing safe Git exit codes.

- [ ] **Step 3: Run focused CLI tests**

Run: `mvn -Dtest=PassportCiCheckCommandTest test`

- [ ] **Step 4: Commit the CLI slice**

```powershell
git add src/main/java/dev/codedefense/cli src/main/java/dev/codedefense/ci/PassportContinuityRenderer.java src/test/java/dev/codedefense/cli/PassportCiCheckCommandTest.java
git commit -m "feat: add Passport CI continuity command"
```

### Task 4: Composite action and example workflow

**Files:**
- Create: `.github/actions/passport-check/action.yml`
- Create: `.github/workflows/codedefense-passport.yml`
- Test: `src/test/java/dev/codedefense/ci/GitHubActionContractTest.java`

- [ ] **Step 1: Write one static action contract test**

Assert Java 21, Maven caching, full checkout in the example, `contents: read`, explicit PR/push base/head handling, Step Summary output, and absence of secrets, Codex commands, API calls, or write permissions.

- [ ] **Step 2: Implement the composite action**

Build the action checkout with `mvn -DskipTests package`, invoke the packaged JAR against `${{ inputs.repository }}`, capture GitHub Markdown, append it to `$GITHUB_STEP_SUMMARY`, and preserve the Java command exit code.

- [ ] **Step 3: Run the action contract test**

Run: `mvn -Dtest=GitHubActionContractTest test`

- [ ] **Step 4: Commit GitHub integration**

```powershell
git add .github src/test/java/dev/codedefense/ci/GitHubActionContractTest.java
git commit -m "ci: add advisory Passport continuity check"
```

### Task 5: Documentation and offline acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] **Step 1: Document limitations and opt-in enforcement**

Explain advisory versus required, full-history checkout, the forged-trailer limitation, and the exact statement: fingerprint continuity is not identity, correctness, safety, merge approval, or deployment approval.

- [ ] **Step 2: Run full offline verification**

```powershell
mvn clean verify
mvn package
java -jar target/codedefense.jar --help
java -jar target/codedefense.jar passport ci-check --help
java -jar target/codedefense.jar prove --staged --dry-run .
```

Then create a disposable Git fixture and run advisory and required checks. Expected: advisory mismatch exits 0; required mismatch exits nonzero; neither invokes Codex.

- [ ] **Step 3: Commit documentation**

```powershell
git add README.md docs/implementation-checklist.md
git commit -m "docs: explain Passport continuity CI"
```
