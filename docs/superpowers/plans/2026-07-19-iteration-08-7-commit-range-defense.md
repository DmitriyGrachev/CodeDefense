# Iteration 8.7 Commit and Range Change Defense Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Defend an exact staged index, one commit, or a merge-base range through one generalized bounded Git-change pipeline.

**Architecture:** Replace staged-specific domain/application names with `GitChange` abstractions and sealed selectors. Resolve every user ref to immutable commit IDs before diff capture; all later Git commands use only validated object IDs and literal paths. Persist selector kind and resolved identities in the 8.6 receipt.

**Tech Stack:** Java 21, Maven, existing bounded `ProcessExecutor`, Picocli, JUnit 5. No JGit or new dependency.

## Global Constraints

- Do not use GitHub APIs.
- Never pass an unresolved ref to diff/numstat/hunk commands.
- Use `--end-of-options` for revision resolution and `--literal-pathspecs` for paths.
- Disable aliases, external diff, and textconv.
- Preserve staged CLI compatibility and all 30-file/120-KiB limits.

---

### Task 1: Introduce selectors and generalized identity

**Files:**
- Create: `src/main/java/dev/codedefense/domain/ChangeSelector.java`
- Create: `src/main/java/dev/codedefense/domain/StagedSelector.java`
- Create: `src/main/java/dev/codedefense/domain/CommitSelector.java`
- Create: `src/main/java/dev/codedefense/domain/RangeSelector.java`
- Create: `src/main/java/dev/codedefense/domain/GitChangeIdentity.java`
- Create: `src/main/java/dev/codedefense/domain/GitChange.java`
- Test: `src/test/java/dev/codedefense/domain/GitChangeTest.java`

```java
public sealed interface ChangeSelector permits StagedSelector, CommitSelector, RangeSelector { }
public record StagedSelector() implements ChangeSelector { }
public record CommitSelector(String revision) implements ChangeSelector { }
public record RangeSelector(String baseRevision, String headRevision) implements ChangeSelector { }

public record GitChangeIdentity(
        ChangeKind kind,
        String baseCommit,
        String targetIdentity,
        String diffFingerprint) { }
```

- [ ] Test nonblank bounded revisions, reject leading `-`, control characters and ambiguous range syntax, immutable IDs, safe `toString`, sorted changed files, and non-negative counts.
- [ ] Implement `RangeSelector.parse("BASE...HEAD")` requiring exactly one three-dot delimiter.
- [ ] Add `COMMIT` and `RANGE` to `ChangeKind`; update receipt tests.

### Task 2: Resolve revisions safely

**Files:**
- Create: `src/main/java/dev/codedefense/change/GitRevisionResolver.java`
- Create: `src/main/java/dev/codedefense/change/ResolvedChangeSelector.java`
- Create: `src/test/java/dev/codedefense/change/GitRevisionResolverTest.java`

```java
public interface GitRevisionResolver {
    ResolvedChangeSelector resolve(Path repositoryRoot, ChangeSelector selector);
}
```

- [ ] Write fake-process tests asserting token arrays for `rev-parse --verify --end-of-options <revision>^{commit}`, commit parent resolution, and `merge-base <baseSha> <headSha>`.
- [ ] Reject root commits in 8.7 with a fixed message rather than inventing an empty-tree path.
- [ ] Reject missing/ambiguous objects, non-commit objects, truncation, timeout, and malformed output without including Git output.
- [ ] After resolution, assert every subsequent fake command contains only lowercase 40–64 hex IDs.

### Task 3: Generalize bounded capture

**Files:**
- Create: `src/main/java/dev/codedefense/change/GitChangeSource.java`
- Create: `src/main/java/dev/codedefense/change/CapturedGitChange.java`
- Create: `src/main/java/dev/codedefense/change/GitCliChangeSource.java`
- Migrate: staged implementations under `src/main/java/dev/codedefense/change/`
- Create: `src/test/java/dev/codedefense/change/GitCliChangeSourceTest.java`

```java
public interface GitChangeSource {
    CapturedGitChange capture(Path requestedPath, ChangeSelector selector);
    GitChangeIdentity captureIdentity(Path requestedPath, ResolvedChangeSelector selector);
}
```

- [ ] Port every staged capture regression first: literal magic paths, rename old/new, deletion-only, large hunk beyond prefix, 30 subprocess cap, binary/symlink exclusions, atomic identity check, invalid UTF-8, and no worktree reads.
- [ ] Add commit and range fixtures with add/modify/delete/rename and merge-base semantics.
- [ ] For staged capture, compare identity before/after hunks. Commit/range captures use immutable IDs but still verify the object IDs remain readable.
- [ ] Canonical diff fingerprint must depend on kind, base ID, target identity, sorted raw entries, and normalized hunk bytes.
- [ ] Remove staged-only production classes only after all consumers compile against the generalized port.

### Task 4: Generalize context, analysis, preview, and runner

**Files:**
- Create: `src/main/java/dev/codedefense/change/GitChangeContextBuilder.java`
- Create: `src/main/java/dev/codedefense/change/GitChangePreviewRenderer.java`
- Create: `src/main/java/dev/codedefense/analysis/GitChangeAnalyzer.java`
- Create: `src/main/java/dev/codedefense/application/GitChangeDefenseRunner.java`
- Create: `src/main/java/dev/codedefense/application/DefaultGitChangeDefenseRunner.java`
- Migrate corresponding staged tests to generalized names.

- [ ] Copy existing behavior with tests before deleting staged-specific types.
- [ ] Preview exact mode and resolved short IDs; never print raw user refs after resolution.
- [ ] Keep the same prompt/schema and category IDs; change only typed metadata labels from staged-only to Git-change.
- [ ] Save `ChangeKind`, base ID and target identity through `PassportReceipt`.
- [ ] Recapture before save: staged may become EXPIRED; immutable commit/range objects must match their captured fingerprint.

### Task 5: Add mutually exclusive CLI selectors

**Files:**
- Modify: `src/main/java/dev/codedefense/cli/ProveCommand.java`
- Modify: `src/test/java/dev/codedefense/cli/ProveCommandTest.java`
- Modify: `src/test/java/dev/codedefense/cli/CliFoundationTest.java`

```java
static final class SelectorOptions {
    @Option(names = "--staged") boolean staged;
    @Option(names = "--commit") String commit;
    @Option(names = "--range") String range;
}
```

- [ ] Use a required exclusive Picocli `@ArgGroup(multiplicity = "1")`.
- [ ] Cover default path, spaces/Unicode paths, malformed three-dot range, conflicting selectors, help without Git/Codex, and exact delegation.
- [ ] Keep `prove --staged PATH` unchanged.

### Task 6: Verification and migration cleanup

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`
- Remove staged-only classes/tests after generalized equivalents cover all behavior.

- [ ] Run focused domain/resolver/source/context/runner/CLI groups.
- [ ] Run `mvn clean verify` and `mvn package`.
- [ ] Create offline repos proving equivalent staged/commit/range hunks reach the same bounded context while identities remain mode-specific.
- [ ] Run help commands and all three dry-runs; assert no Codex.
- [ ] One real commit/range run requires separate authorization.

## Suggested commits

```text
feat: model selectable Git changes
feat: capture commit and range hunks
feat: generalize change defense workflow
docs: document commit and range defense
```

## Stop rule

Do not add retries, history lineage, portable handoff import, focus modes, IDE code, or Codex session access.
