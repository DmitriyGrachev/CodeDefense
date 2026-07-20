# CodeDefense Judge Guide

CodeDefense turns an exact staged Git change into an evidence-grounded
technical defense. GPT-5.6 asks and evaluates; deterministic Java owns
the final score, Git fingerprint, Passport state, and CI exit code.

## 60-second model-free evaluation

Requirements: Java 21 only.

1. Download `codedefense.jar` and `SHA256SUMS.txt` from the [v0.1.0 release](https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0).
2. Verify the checksum.
3. Run:

```powershell
java -jar .\codedefense.jar --version
java -jar .\codedefense.jar --help
java -jar .\codedefense.jar sample --dry-run
```

Expected: successful version/help output and a bounded sample preview ending
with `No source content was sent.` and `Codex was not invoked.`

## Full staged-change defense

Additional requirements: Git and a locally installed Codex CLI authenticated
with the judge's own OpenAI account.

```powershell
git add path\to\SupportedSource.java
java -jar .\codedefense.jar prove --staged --dry-run <repository>
java -jar .\codedefense.jar prove --staged <repository>
java -jar .\codedefense.jar passport show <repository>
java -jar .\codedefense.jar passport coverage <repository>
java -jar .\codedefense.jar passport verify <repository>
```

The real defense asks for explicit consent before sending bounded staged Git
context and may consume Codex credits.

## JetBrains plugin

Use IntelliJ IDEA build `261.*` or `262.*` on Windows. Install
`codedefense-jetbrains-0.1.0.zip` through **Settings → Plugins → Install Plugin
from Disk**, restart IntelliJ IDEA, open **View → Tool Windows → CodeDefense**,
and stage a supported source change before selecting **Preview defense**.

## Codex plugin

Install `codedefense-codex-plugin.zip` using the repository-local marketplace
instructions in the main README. Review and enable the advisory Stop hook,
then ask `@codedefense` for the source-free status of the staged change.

## Supported platforms

- CLI: Java 21; verified on Windows, with Maven verification on Windows and Ubuntu.
- JetBrains plugin: IntelliJ IDEA `261.*` and `262.*` on Windows.
- Codex plugin and Stop hook: verified on Windows.
- Passport CI check: Ubuntu GitHub Actions runner, source-free and model-free.

Installed macOS/Linux plugin acceptance and experimental Codex provenance are
outside the v0.1.0 acceptance claim.

## Privacy and interpretation

Preview, Passport display, Evidence Coverage, Learning Radar, and CI continuity
do not invoke Codex. The Passport excludes the captured source snapshot.

A Passport is evidence of a completed technical defense—not proof of
correctness, security, authorship, or approval to merge or deploy.

## Links

- Repository: https://github.com/DmitriyGrachev/CodeDefense
- Release: https://github.com/DmitriyGrachev/CodeDefense/releases/tag/v0.1.0
- Build history: https://github.com/DmitriyGrachev/CodeDefense/actions
- Demo video: added after public YouTube publication
