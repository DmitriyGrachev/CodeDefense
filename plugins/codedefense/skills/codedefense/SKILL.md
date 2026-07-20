---
name: codedefense
description: Inspect source-free CodeDefense staged Passport status, preview a bounded staged defense, show the latest Passport score card, or summarize repository learning insights. Use when the user asks whether staged work is defended, wants CodeDefense status, or wants the exact safe next action. Do not use it to answer a defense for the user.
---

# CodeDefense

Resolve this plugin root from the location of this `SKILL.md`; the bundled CLI is `cli/codedefense.jar`. Require Java 21.

Use only the command needed for the user's request:

- Status: `java -jar <plugin-root>/cli/codedefense.jar codex-hook status`
- Safe preview: `java -jar <plugin-root>/cli/codedefense.jar prove --staged --dry-run .`
- Latest score card: `java -jar <plugin-root>/cli/codedefense.jar passport show .`
- Learning Radar: `java -jar <plugin-root>/cli/codedefense.jar passport insights . --format json --limit 20`

Treat an empty status response as either no staged change or a non-Git working directory. Explain `UNDEFENDED`, `CURRENT`, `EXPIRED`, and `UNAVAILABLE` as educational source-free states, never as merge or deployment approval.

To start an actual defense, instruct the user to open the CodeDefense IntelliJ Tool Window or run `java -jar <plugin-root>/cli/codedefense.jar prove --staged .` in an interactive terminal. Preserve preview and explicit confirmation.

Never start a source-sending run automatically. Never add a confirmation-bypass option. Never answer defense questions, read a transcript, infer a thread, expose raw JSON or source/diffs, or modify Git, Passport artifacts, commit messages, or settings unless the user separately requests an already-supported local CodeDefense action.
