# CodeDefense Release Candidate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a clean, reproducible CodeDefense 0.1.0 release candidate without adding product features or creating the Devpost submission/video.

**Architecture:** Preserve the current CLI, JetBrains adapter, Codex plugin, and GitHub Passport boundaries. Release work is limited to removing acceptance-only material, recording honest platform status, documenting the product, verifying existing packages, and publishing immutable artifacts from `main`.

**Tech Stack:** Java 21, Maven, Gradle Wrapper for the isolated JetBrains plugin, GitHub Actions, PowerShell packaging scripts.

## Global Constraints

- Add no user-facing feature or third-party dependency.
- Do not invoke a real Codex model during automated verification.
- Preserve experimental Codex provenance behind its off-by-default kill switch.
- Do not claim unperformed macOS/Linux installed-plugin acceptance.
- Do not create the demo video or Devpost description in this iteration.
- Preserve user-authored research and review plans as dated documentation.

---

### Task 1: Clean the release source tree

**Files:**
- Delete: `src/main/java/dev/codedefense/PluginAcceptanceFixture.java`
- Modify: `.gitignore`
- Add: restored dated plans and research already present in the working tree

- [ ] Remove the acceptance-only Java fixture from production sources.
- [ ] Remove the accidental `.docs/` ignore entry.
- [ ] Check all restored documentation for secrets and placeholder markers before tracking it.
- [ ] Run `git diff --check` and confirm no whitespace error.

### Task 2: Record honest acceptance status

**Files:**
- Modify: `docs/implementation-checklist.md`
- Modify: `README.md`

- [ ] Mark only manually observed Windows IntelliJ/Codex plugin behaviors complete.
- [ ] Leave experimental provenance and unperformed installed-platform checks explicitly deferred.
- [ ] Keep Iteration 9 open until the video and Devpost work is complete.

### Task 3: Complete release documentation

**Files:**
- Modify: `README.md`
- Add: `docs/assets/codedefense-jetbrains-cockpit.png`

- [ ] Add a product screenshot, one-sentence problem statement, architecture, limits, supported inputs, testing, troubleshooting, Codex/GPT-5.6 explanation, limitations, roadmap, and MIT license link.
- [ ] Keep privacy and non-approval disclaimers explicit.
- [ ] Document release downloads and exact dry-run commands.

### Task 4: Add clean-machine verification

**Files:**
- Add: `.github/workflows/build.yml`

- [ ] Run `mvn -B clean verify` on Java 21 for Ubuntu and Windows.
- [ ] Grant only `contents: read` and provide no Codex credentials.
- [ ] Verify the workflow on the release pull request.

### Task 5: Verify and package every adapter

- [ ] Run `mvn clean verify` and capture test/failure/error/skip totals.
- [ ] Run `mvn package`, root help/version, `start . --dry-run`, `sample --dry-run`, and local `report`.
- [ ] Inspect the shaded JAR manifest and SHA-256.
- [ ] Run JetBrains `test`, `verifyPlugin`, and `buildPlugin`.
- [ ] Package the Codex plugin and verify its archive contract.
- [ ] Confirm no verification command invokes a real Codex model.

### Task 6: Integrate and publish 0.1.0

- [ ] Commit only reviewed release files to the existing pull-request branch.
- [ ] Wait for all GitHub Actions checks to pass.
- [ ] Mark PR #9 ready and merge it into `main` without rewriting unrelated history.
- [ ] Update local `main` and confirm a clean tracked working tree.
- [ ] Tag the merge commit `v0.1.0`.
- [ ] Publish `codedefense.jar`, the JetBrains plugin ZIP, the Codex plugin ZIP, and `SHA256SUMS.txt` in a GitHub release.
- [ ] Download or inspect published assets and confirm their names and checksums.

## Self-review

- Scope contains only release work already approved by the user.
- Video, Devpost copy, and final submission-link verification remain intentionally pending.
- No acceptance status can imply unperformed provenance or POSIX installed-plugin testing.
- Every published artifact comes from the verified merge commit.
