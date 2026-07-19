# Iteration 8.9 Portable Change Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let one developer hand a source-free Change Passport attempt timeline to another developer as one bounded local file that can be inspected and matched to an exact local Git change without a server or account.

**Architecture:** Package validated `PassportReceipt` records in a versioned deterministic JSON envelope. The envelope includes repository/change identity, attempt lineage, and a SHA-256 integrity checksum over canonical payload bytes. The checksum detects corruption only; it is not a signature and proves neither authorship nor trust. Import never writes into the trusted local Passport store. A recipient may inspect the package or match it against a separately captured local staged/commit/range identity.

**Tech Stack:** Java 21, Maven, existing Jackson and bounded filesystem/process adapters, Picocli, JUnit 5. No new dependency, network call, GitHub token, or real Codex call.

## Global Constraints

- One `.cdhandoff.json` file, maximum 1 MiB and 20 attempts.
- No source, diff, hunk, answer, question, feedback, concepts, prompt, schema, model output, username, email, or absolute local path.
- A checksum is labeled `Integrity: intact/corrupt`, never `signed`, `verified author`, `trusted`, or `certified`.
- Inspect is Git-free and read-only. Match is Git-read-only and never invokes Codex or JLine.
- Imported packages are untrusted input and never become the local latest Passport.
- Every input/output boundary rejects symlinks, traversal, special files, invalid UTF-8, trailing JSON tokens, and oversized content.

---

### Task 1: Define the portable envelope and integrity vocabulary

**Files:**
- Create: `src/main/java/dev/codedefense/domain/ChangeHandoff.java`
- Create: `src/main/java/dev/codedefense/domain/HandoffIntegrity.java`
- Create: `src/main/java/dev/codedefense/domain/HandoffMatchStatus.java`
- Create: `src/test/java/dev/codedefense/domain/ChangeHandoffTest.java`

```java
public enum HandoffIntegrity { INTACT, CORRUPT }

public enum HandoffMatchStatus {
    MATCHES_LOCAL_CHANGE,
    DIFFERENT_LOCAL_CHANGE,
    LOCAL_CHANGE_UNAVAILABLE
}

public record ChangeHandoff(
        int schemaVersion,
        String handoffId,
        Instant createdAt,
        String repositoryIdentityHash,
        ChangeKind changeKind,
        String baseCommit,
        String sourceIdentity,
        String diffFingerprint,
        List<PassportAttemptSummary> attempts,
        String payloadSha256) { }
```

- [ ] Write failing tests for schema version `1`, UUID handoff ID, lowercase 64-character hashes, newest-last contiguous attempt lineage, one repository/fingerprint across all attempts, maximum 20 attempts, immutable collections, and content-free `toString()`.
- [ ] Assert domain validation rejects duplicate attempt IDs, orphan parents, cross-fingerprint attempts, absolute/traversing file paths inside receipts, and forbidden empty history.
- [ ] Run `mvn -Dtest=ChangeHandoffTest test`; confirm RED.
- [ ] Implement the minimal records and explicit invariants; checksum validation belongs to the codec, not the record constructor.
- [ ] Re-run the focused test; expect green.

### Task 2: Encode canonical JSON and verify corruption locally

**Files:**
- Create: `src/main/java/dev/codedefense/passport/ChangeHandoffJsonCodec.java`
- Create: `src/main/java/dev/codedefense/passport/DecodedChangeHandoff.java`
- Create: `src/main/resources/schemas/change-handoff.schema.json`
- Create: `src/test/java/dev/codedefense/passport/ChangeHandoffJsonCodecTest.java`

```java
public record DecodedChangeHandoff(
        ChangeHandoff handoff,
        HandoffIntegrity integrity) { }

public final class ChangeHandoffJsonCodec {
    public byte[] encode(ChangeHandoff handoff);
    public DecodedChangeHandoff decode(byte[] input);
}
```

- [ ] First write tests for deterministic property/order bytes, LF newline, strict UTF-8, stable checksum, single-byte tamper detection, unknown/missing fields, fractional integers, duplicate JSON keys, trailing tokens, 1-MiB cap, and safe exceptions that never echo JSON.
- [ ] Define checksum input as canonical JSON of every field except `payloadSha256`, encoded UTF-8 with no trailing newline. Hash that byte array with SHA-256 and then encode the complete envelope with one final LF.
- [ ] Do not deserialize a checksum value and then reserialize arbitrary unknown content. Strictly decode the known schema, validate the domain object, reconstruct canonical payload bytes, and compare hashes with `MessageDigest.isEqual`.
- [ ] Treat a mismatch as a successfully parsed `CORRUPT` envelope for `inspect`; prevent `match` from continuing.
- [ ] Run `mvn -Dtest=ChangeHandoffJsonCodecTest test`; expect green.

### Task 3: Add bounded no-follow handoff storage

**Files:**
- Create: `src/main/java/dev/codedefense/passport/ChangeHandoffFileStore.java`
- Create: `src/main/java/dev/codedefense/passport/FileSystemChangeHandoffStore.java`
- Create: `src/main/java/dev/codedefense/passport/ChangeHandoffPersistenceException.java`
- Create: `src/test/java/dev/codedefense/passport/FileSystemChangeHandoffStoreTest.java`

```java
public interface ChangeHandoffFileStore {
    Path write(Path output, byte[] content, boolean overwrite);
    byte[] read(Path input);
}
```

- [ ] Write `@TempDir` tests for atomic publication, no overwrite by default, explicit overwrite, missing/unreadable file, final symlink, parent symlink, directory input, output traversal, cleanup after failed move, and exact 1-MiB read limit.
- [ ] Normalize and real-path validate the existing parent; open the final input with `NOFOLLOW_LINKS`; write to a random sibling temp file and move atomically with a non-atomic replacement fallback.
- [ ] Never create or mutate `.codedefense/latest-*` pointers while reading or writing handoffs.
- [ ] Run the focused store test and keep all error text path-safe.

### Task 4: Create, inspect, and match application use cases

**Files:**
- Create: `src/main/java/dev/codedefense/application/CreateChangeHandoffUseCase.java`
- Create: `src/main/java/dev/codedefense/application/InspectChangeHandoffUseCase.java`
- Create: `src/main/java/dev/codedefense/application/MatchChangeHandoffUseCase.java`
- Create: `src/main/java/dev/codedefense/passport/ChangeHandoffTerminalRenderer.java`
- Create: `src/test/java/dev/codedefense/application/ChangeHandoffUseCaseTest.java`

```java
public final class CreateChangeHandoffUseCase {
    public int create(Path repository, Path output, boolean overwrite,
            PrintWriter out, PrintWriter err);
}

public final class InspectChangeHandoffUseCase {
    public int inspect(Path input, PrintWriter out, PrintWriter err);
}

public final class MatchChangeHandoffUseCase {
    public int match(Path input, Path repository, PrintWriter out, PrintWriter err);
}
```

- [ ] With hand-written fakes, prove `create` loads at most 20 validated receipts for the latest fingerprint and never calls Git, Codex, or JLine.
- [ ] Prove `inspect` renders integrity, mode, short fingerprint, attempt count, latest category scores, overall score/readiness, and the explicit sentence `Integrity is a corruption check, not proof of authorship.`
- [ ] Prove `match` order is decode -> require INTACT -> resolve stored immutable selector identity -> capture local identity -> compare repository hash/fingerprint -> render result.
- [ ] A corrupt package, unavailable object, wrong repository, or changed diff must be a typed non-success result with no raw payload in output.
- [ ] The use cases must not add imported receipts to local history.

### Task 5: Add the `passport handoff` CLI group

**Files:**
- Create: `src/main/java/dev/codedefense/cli/PassportHandoffCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportHandoffCreateCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportHandoffInspectCommand.java`
- Create: `src/main/java/dev/codedefense/cli/PassportHandoffMatchCommand.java`
- Modify: `src/main/java/dev/codedefense/cli/PassportCommand.java`
- Modify: `src/main/java/dev/codedefense/CodeDefenseApplication.java`
- Create: `src/test/java/dev/codedefense/cli/PassportHandoffCommandTest.java`

```powershell
codedefense passport handoff create . --output change.cdhandoff.json
codedefense passport handoff inspect change.cdhandoff.json
codedefense passport handoff match change.cdhandoff.json .
```

- [ ] Test help without storage/Git/Codex initialization, required paths, `--overwrite`, Unicode/spaces, invalid combinations, safe exit codes, and exact use-case delegation.
- [ ] Use Picocli output/error writers and never print the package body.
- [ ] Keep `passport export --format json` as a single-receipt export; handoff is the explicit timeline envelope.

### Task 6: Offline acceptance and documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/implementation-checklist.md`

- [ ] Document the threat model: local transport, source-free data, corruption detection, no identity/signature claim, and no trusted-store import.
- [ ] Run focused domain/codec/store/use-case/CLI tests.
- [ ] Run `mvn clean verify`, `mvn package`, root help, and `passport handoff --help`.
- [ ] Create an offline fixture with two attempts, export it, inspect it in an empty directory, match it in the correct repository, mutate one byte, and prove the corrupt package is rejected for matching.
- [ ] Search the serialized package for seeded source, diff, answer, question, feedback, prompt, and username markers; all must be absent.
- [ ] No real Codex call is required or permitted for Iteration 8.9 acceptance.

## Suggested commits

```text
feat: add source-free change handoff envelopes
feat: add local handoff inspection and matching
docs: document portable change handoffs
```

## Stop rule

Do not add signing, accounts, network transfer, GitHub ingestion, focus modes, JetBrains code, or Codex session access in Iteration 8.9.
