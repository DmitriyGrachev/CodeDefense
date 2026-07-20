# CodeDefense Product Expansion Research

Research date: 2026-07-18. This is a product/technical investigation, not an implementation plan.

> Historical note: this snapshot predates the implemented Change Passport, JetBrains adapter, Codex plugin, Evidence Coverage Map, and Passport CI. It records the research that informed those later iterations; the current product status is documented in the root README and implementation checklist.

## 1. Executive conclusion

**Recommendation: no new feature before release; move expansion to Iteration 10.**

The current MVP has a coherent, unusual thesis: it measures whether a person can explain a bounded local codebase, rather than judging the code itself. However, the market is no longer empty: Comprende, Contral's Defense Mode, Bridgekeeper, and Engramme all market comprehension checks for AI-assisted code. A generic PR quiz, merge gate, or AI summary would therefore weaken differentiation rather than strengthen it.

The strongest post-release direction is **Codex Change Defense**: a local, Git-diff-bound defense that asks counterfactual and test-prediction questions about a precisely identified change. It should make only the narrow claim that a named person demonstrated a particular level of understanding of a named local diff at a point in time. It must not claim that code is secure, compliant, safe to merge, or that Codex authored the change.

Do not make it Iteration 9. The release work still has higher expected value: the current `main` does not yet contain the Iteration 8 sample workflow, reliability/packaging documentation is still the declared Iteration 9 scope, and the proposed differentiation needs demand validation before adding Git/process/persistence complexity. A manual five-user experiment can validate the workflow without a model call or a feature branch.

The answer to “what makes this more than an LLM quiz?” is not merely more questions. It is a verifiable local chain:

```text
exact Git change identity + bounded/redacted context
        + Codex-generated evidence-backed questions
        + human explanation and local scoring
        + explicit, limited local attestation
```

Codex session provenance can enrich this later, but a prior session cannot yet be *reliably* attributed to a Git change through a release-stable Java interface. Treat it as an opt-in experimental research path, never as an authorship proof.

## 2. Current product map

### Repository boundary examined

The required baseline is `main` at `2ccaa61af7bd14b7d2e8aad742d442872bafc46f` (`doc : iter 7 done`). It contains Iterations 0–7. The checked-out `feat/iteration-08-embedded-sample` branch has the unmerged Iteration 8 candidate (`ProjectDefenseRunner`, `DefaultProjectDefenseRunner`, `SampleProjectExtractor` and the embedded archive). This report describes that candidate separately; it does not claim that it is in `main`.

### User journey and calls

1. `codedefense start [PATH]` validates a local directory, discovers supported files, excludes common generated/secret/binary material, creates a bounded snapshot, and previews counts, selected paths, truncation and redactions.
2. `--dry-run` stops before confirmation, runtime creation, JLine, Codex, and report persistence. `--yes` bypasses the explicit source-send confirmation.
3. After approval, the runtime performs one project-analysis request, exactly three primary answer evaluations, at most one follow-up evaluation per primary question, and one report-narrative request. The documented maximum is eight model calls.
4. Java owns the three-question state machine, final scores, readiness, exit codes, Markdown structure, and report persistence. Codex supplies validated project analysis, answer evaluation, and optional narrative text.
5. `report` shows the latest local Markdown report. On `main`, `sample` is still a placeholder; the Iteration 8 branch refactors `start`/`sample` through a shared runner and extracts a bounded text-only ZIP into a temporary directory.

### Trust and privacy boundaries

| Boundary | Current design | Consequence for expansion |
|---|---|---|
| Filesystem intake | Scanner does not follow links, filters sensitive/binary/generated material, sorts deterministically; bounded reader enforces real-path containment and UTF-8-safe prefix handling. | Reuse for a diff context; do not create an unbounded `git show` escape hatch. |
| Source disclosure | `ProjectSnapshotBuilder` caps files and snapshot bytes, redacts secret-like assignments, and sends source only after confirmation. | A diff mode needs its own byte/path/secret budget and the same confirmation. |
| Untrusted model/source material | Snapshot content is bounded; prompt factories use collision-safe markers; schemas and validators constrain model output. | Git commit messages, branch names, PR text and Codex exports are also untrusted input. |
| Local authority | `InterviewEngine`, scorer, readiness classifier, report metadata and renderer are Java-owned. | A future attestation status must remain locally computed and descriptive. |
| Codex process | `CodexProcessRunner` starts `codex exec` in an empty temporary workspace with read-only sandbox, no approval, sanitized environment, bounded streams and schema file. | Do not give a future task the repository working directory merely to obtain provenance. |
| Retention | Snapshot `promptContent` exists only for the run and is not retained in `ReportMetadata`; reports retain selected file metadata, questions, answers (safely fenced), evaluations, local scores and narrative. They omit source snapshots, expected key points, evidence metadata/reasons, prompts/templates/schemas and raw model JSON. | Any longitudinal feature must be opt-in and store a minimal factual record, not source or full Codex transcripts. |

### Existing seams and limitations

* `AiProvider` and `StructuredCodexRequest` isolate the Codex adapter; analysis, evaluation and report narrative share one lazy `CodexCliAiProvider`.
* `ProjectScanner`, snapshot builder, report service/store, terminal interfaces and the Iteration 8 `ProjectDefenseRunner` candidate are useful application seams. There is no Git abstraction, diff source, PR connector, identity model, or attestation domain yet.
* The model prompt never directly executes analyzed source. Default tests use hand-written fakes and the one live smoke test is property-gated.
* Maven builds one Java 21 shaded JAR. There is no installer, signed release, CI workflow, GitHub app, PR integration, cross-machine identity, team tenancy, or release telemetry pipeline.
* Current commands cannot select staged changes, an uncommitted diff, a commit/range/branch/PR, a Codex session, a named developer, or an attestation output.

These constraints are strengths for a local educational tool. They are also why an immediate GitHub merge gate or enterprise scorecard is inappropriate.

## 3. Current Codex integration landscape

The distinction below is important: “documented” does not mean a good release dependency for a Java CLI. The product should prefer the same narrow, local `codex exec` contract it already tests.

| Official surface | Status and availability | Auth / data available | Java and release assessment |
|---|---|---|---|
| `codex exec` | Documented non-interactive CLI for scripts/CI. Read-only sandbox is the default; `--ephemeral` avoids local rollout persistence. | Reuses saved CLI auth; an API key is also supported for one `exec` invocation. Final output, schema output and process status are available. | **Release-stable and already used.** Cross-platform only through the existing resolver. [Official docs](https://learn.chatgpt.com/docs/non-interactive-mode), accessed 2026-07-18. |
| `codex exec --json` and `--output-schema` | Documented JSONL event stream and JSON Schema final-output mode. JSONL includes `thread.started`, turns, items, tool events, errors and usage. | It exposes data for the run that CodeDefense launches, not a magic index of every prior Codex task. Events may contain commands, diffs, tool arguments and sensitive context. | Good for an opt-in diagnostic/receipt prototype; too privacy-heavy to enable by default. [Official docs](https://learn.chatgpt.com/docs/non-interactive-mode), accessed 2026-07-18. |
| CLI persistence and `codex exec resume` | Documented: non-ephemeral runs can resume `--last` or a session ID. | Session identity/final response are available for the caller's stored session. Current CodeDefense intentionally invokes `--ephemeral`, so it creates no reusable rollout. | Do not scrape `$CODEX_HOME` files. A session feature must use a supported protocol and explicit consent. [Official docs](https://learn.chatgpt.com/docs/non-interactive-mode), accessed 2026-07-18. |
| Codex SDK | Documented TypeScript SDK (Node 18+) controls threads and resume. Python SDK controls app-server but is explicitly beta. | Local Codex authentication/runtime; thread final responses/events through SDK surface. | No official Java SDK. TypeScript needs a companion process; Python is beta. Not a core Java dependency. [Official SDK docs](https://learn.chatgpt.com/docs/codex-sdk), accessed 2026-07-18. |
| `codex app-server` | Documented JSON-RPC interface behind rich Codex clients. Stdio JSONL is supported; WebSocket is expressly experimental/unsupported. Stable and experimental schemas can be generated for the installed CLI version. | Threads, turns, item events, approvals, skills, apps and account state are exposed locally. `thread/list`/`thread/read` can inspect persisted interactive CLI/VS Code sessions. | Java can speak JSON-RPC with Jackson, but there is no official Java client and protocol drift is real. Suitable only for opt-in research behind a version capability check. [Official docs](https://learn.chatgpt.com/docs/app-server) and [official protocol repository](https://github.com/openai/codex/tree/main/codex-rs/app-server), accessed 2026-07-18. |
| Codex as MCP server (`codex mcp-server`) | Documented. It exposes `codex` and `codex-reply` tools; structured content returns a `threadId`. | Prompt/configuration and thread continuation. It is a way to orchestrate Codex, not automatically to expose CodeDefense to Codex. | Requires an MCP client or a TypeScript/Python orchestrator; not a free provenance bridge for Java. [Official docs](https://learn.chatgpt.com/docs/mcp-server), accessed 2026-07-18. |
| Skills, plugins, AGENTS.md, hooks/apps | Documented in app-server: skills may be listed/invoked; apps/plugins are discoverable and policy-controlled. `AGENTS.md`/rules are instruction sources, not a provenance API. | Local configuration and possibly third-party connector data. | A CodeDefense skill can improve invocation, but cannot safely turn an interactive oral defense into a background cloud task. Treat as UX, not evidence. [Official app-server skills/apps documentation](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md), accessed 2026-07-18. |
| GitHub Action | `openai/codex-action@v1` is documented for CI, reviews and patches. | Requires an OpenAI API key secret; Linux/macOS are the supported safe runners, with Windows requiring `unsafe` safety strategy. | Incompatible with the current no-API-key/local-first MVP as a core feature. It is a later enterprise/CI option. [Official docs](https://learn.chatgpt.com/docs/github-action), accessed 2026-07-18. |
| GitHub, cloud, Slack, scheduled/background work, computer use, worktrees and multi-agent | Current Codex/ChatGPT documentation presents these as product surfaces. Cloud/connector workflows are external and policy/account dependent; Agents SDK orchestration and remote tasks are not a Java-local integration contract. Worktrees/multi-agent are app-server/SDK workflows. | Data may leave the machine or tenant; availability varies by plan/admin policy. | **Do not make a release dependency.** No official generic Java API was found for importing arbitrary cloud task, Slack, GitHub or desktop-session provenance. [Codex documentation index](https://learn.chatgpt.com/docs/non-interactive-mode), accessed 2026-07-18. |
| Telemetry, audit, approvals, sandbox and network | Approval policy and sandbox restrict commands/files/network. OpenTelemetry export and Compliance Platform activity logs are documented for governance; Compliance access is Enterprise/Edu oriented. | Telemetry can include prompts, approvals, tool executions, MCP use and network policy events. | Never enable as a personal-product data channel. Existing empty-workspace/read-only model is safer. [OpenAI safety guidance](https://openai.com/index/running-codex-safely/), accessed 2026-07-18. |

### Confidence labels

* **Documented and stable enough now:** existing non-interactive CLI, schemas, JSONL for an owned run, explicit sandbox/approval controls, local Git.
* **Documented but product/SDK-dependent:** TypeScript SDK, MCP server, skills/plugins, GitHub Action.
* **Documented but experimental/beta or version-sensitive:** Python SDK beta, app-server experimental fields/transports, multi-agent/app-server extensions.
* **Observable but not a supported CodeDefense contract:** raw local rollout files, UI state, desktop task state, uncorrelated session-to-commit inference.
* **Unknown/unsupported for this product:** a cross-platform, subscription-only, release-stable Java API that proves a prior Codex session authored a particular commit.

### Session provenance

#### Direct answer

**No supported path currently proves that a specific previous Codex session authored a specific Git change.** The strongest supported evidence is either (a) a structured event stream collected while CodeDefense itself owns a Codex run, or (b) an explicit app-server thread chosen by the user and verified against the local diff. Neither is a cryptographic authorship proof. A commit/PR can state provenance, but its metadata is editable.

App-server is unusually capable: stored threads can be listed/read/resumed, and thread items include messages, commands, file changes/diffs, MCP calls and streamed status. It is still a client protocol with stable and experimental regions, not a Java provenance service. [Official thread lifecycle](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md) and [event item model](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md), accessed 2026-07-18.

| Path | Supported data / constraints | ChatGPT, local-first, cross-platform | Smallest safe POC and disqualifier |
|---|---|---|---|
| A. CLI JSONL | **Yes, for an owned `exec --json` run.** Thread ID, prompt, item/command/file-change/tool events, final response, status and usage can be captured. Current CodeDefense's read-only empty workspace means it cannot observe a patch made elsewhere. | Saved CLI auth can work; local process; resolver makes it cross-platform. JSONL may contain sensitive context. | Capture a new opt-in evidence receipt and retain only hashes/selected facts. Disqualify if it is used to infer an earlier task. |
| B. Codex SDK | **Yes, for SDK-created/resumed threads.** TypeScript is the mature documented option; Python is beta. It has richer thread control than `exec`. | Auth is local Codex/SDK dependent; requires Node or Python, not Java. | Node sidecar creates a thread and emits a minimized receipt. Disqualify for MVP if a companion/runtime is required. |
| C. App-server | **Yes, conditionally.** `thread/list`/`thread/read` can return persisted local history; item types include messages, command executions and file changes/diffs. A human must select the thread and CodeDefense must compare paths/hunks/hashes. | Local ChatGPT-managed auth is supported by Codex app-server. Stdio is portable; Unix socket is not Windows portable; WebSocket is experimental. | Read one user-selected local thread over stdio, compare its completed file-change diff hashes with `git diff`, discard unmatched/transcript text. Disqualify if the required session is not listed, cannot be parsed with the installed schema, or does not match the diff. |
| D. Git diff after session | **Yes, standard Git; no agent attribution.** It provides exact paths/hunks, base/head IDs and timestamps, not task/prompt/commands/tests/reasoning. | No Codex/auth/network; strongest local and cross-platform base. | `git diff --cached` or explicit `BASE..HEAD`, bounded/redacted, then a defense. Disqualify any claim that Git alone proves AI authorship. |
| E. Commit metadata | **Yes, standard Git; weak provenance.** Message, author/committer/time and optional trailers are mutable declarations. | Fully local/cross-platform; no Codex. | Optional `Codex-Thread:` trailer displayed as unverified user-supplied metadata. Disqualify as evidence of authorship. |
| F. GitHub PR metadata | **Yes through GitHub APIs/actions, not by current app.** PR, checks, commits/comments/timestamps can be obtained, but agent-session data requires a separate integration. | Network, token/app permissions and repository disclosure; no subscription-only guarantee. | Import a PR diff only after explicit GitHub integration. Disqualify from local MVP because it violates present scope/privacy. |
| G. Codex cloud task metadata | Public product support exists, but **no verified supported export/API was found** that supplies an arbitrary cloud task's complete provenance to this Java CLI. | Cloud/account/admin policy; source leaves machine. | Do not build against it. Reconsider only if OpenAI documents a stable export/API with consent and retention semantics. |
| H. Explicit Skill export | A Skill can ask Codex to produce a structured handoff/export, but it is model-generated and user-editable, not authoritative runtime evidence. | Can stay local if the Skill/CLI run is local; interaction depends on terminal/client. | JSON declaration with task, changed-path hashes and tests; label it “developer-declared”. Disqualify if presented as verified provenance. |
| I. CodeDefense launched as a Skill | Plausible as a documented skill invoking the existing JAR, but it primarily improves discoverability. Interactive answer entry/confirmation is awkward in unattended/cloud execution. | Local CLI works; cloud/task use is not guaranteed. | A local terminal Skill prints a handoff command and pauses for the human. Disqualify if it hides confirmation or turns the defense into agent-to-agent self-attestation. |
| J. MCP/composable CLI | Codex can be an MCP server; a future CodeDefense MCP server is possible but is a new protocol surface. It does not itself yield prior-session provenance. | Requires an MCP client/adapter and strong consent boundaries. | Expose only `prepare_diff_defense` returning non-secret facts; human still runs the interview. Disqualify if it auto-answers or bypasses user confirmation. |

#### Provenance policy proposed for any future work

1. Label source independently: `GIT_FACT`, `CODEDEFENSE_EXEC_EVENT`, `USER_DECLARED`, or `EXPERIMENTAL_APP_SERVER_MATCH`.
2. Store only a diff identity (base, head/index state, selected path/hunk hashes), timestamps, model/version and local score; never default-store source, full prompts, full event JSON or reasoning.
3. Require an explicit “import this session” choice and display exactly what would be retained.
4. A mismatch is **unlinked**, not suspicious. No workplace surveillance inference.
5. Never use an attestation as a merge approval, security certification, compliance certification or authorship proof.

## 4. Competitive landscape

### Market comparison

`✓` means publicly described; `—` means not a primary public promise, not necessarily absent. Pricing is intentionally not normalized because plans and limits change frequently.

| Product / category | Unit | Asks developer questions / understanding score | Merge gate / learning history | Closest overlap and difference |
|---|---|---|---|---|
| OpenAI Codex | task, repo, cloud/PR workflows | — | Review/action workflows, not a human-comprehension product | Supplies the runtime; CodeDefense's value is human verification. [Source](https://learn.chatgpt.com/docs/non-interactive-mode) |
| GitHub Copilot Code Review | PR/repo | — | PR feedback; can run agentic context gathering and MCP | Code review, not author defense. [Source](https://docs.github.com/en/copilot/concepts/agents/code-review) |
| CodeRabbit | PR/IDE/CLI | — | PR automation/planning | Contextual review and fixes; no stated oral defense. [Source](https://docs.coderabbit.ai/) |
| Qodo | PR, IDE, cross-repo | — | Rule/governance workflows | Multi-agent review/rules, not developer assessment. [Source](https://docs.qodo.ai/code-review) |
| Graphite | PR/codebase | — | Review workflow | Codebase-aware AI review, not comprehension verification. [Source](https://graphite.com/docs/get-started) |
| Sourcegraph | repository/codebase | — | Knowledge/search platform | Grounded code understanding for navigation, not a defense. [Source](https://sourcegraph.com/solutions/code-understanding) |
| Cursor Bugbot | PR diff | — | Automated review/comment/fix handoff | PR bug review, not user explanation. [Source](https://docs.cursor.com/bugbot) |
| Claude Code Action | issue/PR/repo | — | Can review and make changes in Actions | Agent/task automation, not a local understanding signal. [Source](https://github.com/anthropics/claude-code-action) |
| JetBrains Qodana | repository/CI | — | Quality gates/reports | Static analysis/quality, not a quiz. [Source](https://www.jetbrains.com/help/qodana/about-qodana.html) |
| Snyk / Semgrep | code/dependency/CI | — | Security findings/gates | Security scanning and agent guardrails; a security defense must complement them. [Snyk](https://docs.snyk.io/scan-with-snyk/snyk-code), [Semgrep](https://semgrep.dev/) |
| CodeScene | repository/history | — | Health metrics/gates | Behavioral code-health analysis, not individual learning. [Source](https://codescene.com/product/code-health) |
| Swimm | repository/team knowledge | — | Documentation/knowledge base | Explains codebase knowledge rather than verifying it. [Source](https://swimm.io/home) |
| PR-Agent / Reviewpad (open source) | PR | Mostly — | Review summaries/automation | Open-source review baseline; not a comprehension assessment. [PR-Agent](https://github.com/The-PR-Agent/pr-agent), [Reviewpad](https://docs.reviewpad.com/) |
| **Comprende** | file/function/session | **✓**, stated understanding grade | **✓**, stated real-time gate/history | Direct competitor to comprehension gates. Its existence prevents an originality claim for generic scoring. [Source](https://www.comprende.dev/) |
| **Contral Defense Mode** | AI-generated change/developer | **✓**, targeted explanations | “Pass to ship” is stated; progressive learning is stated | Direct IDE-native comprehension defense. CodeDefense differs only if it is bounded/local/auditable and not another IDE agent. [Source](https://contral.ai/guides/defense-mode-prove-understanding) |
| **Bridgekeeper** | PR/reviewer/team | **✓**, Socratic questions | Marketed as a merge decision and cognitive-debt tool | Direct PR gate competitor; high risk of workplace surveillance/gaming. [Source](https://bridgekeeper.io/) |
| **Engramme** | branch/PR developer memory | **✓**, recall questions | Learning/weekly memory framing | Closest lightweight learning overlap. [Source](https://open-vsx.org/extension/memorymachines/engramme-code) |

### Search coverage and uncertainty

Searches explicitly covered “prove you understand AI-generated code”, “AI code comprehension assessment”, “pull request comprehension quiz”, and “explain code before merge”, as well as the named vendors. The direct products above are vendor claims, not independently benchmarked evidence of adoption or effectiveness. Their presence proves overlap, not market success. The broader review market is crowded; research also finds that automated review impact varies by tool and workflow, reinforcing the need for a specific learning/ownership outcome rather than another comment bot. [Industrial study](https://arxiv.org/abs/2412.18531), [large-scale Actions study](https://arxiv.org/abs/2508.18771), accessed 2026-07-18.

## 5. Opportunity map

| Group | Opportunities | Decision boundary |
|---|---|---|
| **Immediate release candidate** | None. | Iteration 9 should finish reliability, packaging, documentation and release readiness; no research finding outweighs that work. |
| **Near-term roadmap** | A Change Defense, G Counterfactual Defense and L Test Prediction Challenge; optionally E Handoff Package and O7 Failure-Mode Ticket after discovery. | Keep the unit local Git diff, preserve preview/redaction/budgets, use local scoring, and make no merge, security, compliance or authorship claim. |
| **Long-term product** | B Session Attestation, O1 bounded receipt, O2 thread-to-diff matching, K replay, N onboarding and carefully opt-in H knowledge drift. | Require explicit consent, data minimization, version-capability checks and a proven cross-platform protocol. Enterprise/team use needs separate legal and product discovery. |
| **Rejected or deliberately deferred** | C merge gate as authority, I team map, F as a scanner, D/O5 as standalone value, GitHub/cloud task import for the local MVP. | They either become governance/surveillance, duplicate mature products, or weaken the no-key/local-first boundary. |

The viable differentiation is a workflow rather than another model-generated quiz: exact local change identity, bounded context, counterfactual/test-prediction questions, deterministic local scoring, and a deliberately limited local receipt. Codex session data may enrich an explicitly consented future flow, but it cannot honestly be used as prior-session authorship proof today.

## 6. Candidate evaluations

### Evaluation method

Weighted score (0–10) = `0.20U + 0.15C + 0.15V + 0.15T + 0.10D + 0.10F + 0.05P + 0.05S + 0.05A`, where `U` uniqueness, `C` Codex depth, `V` user value, `T` thesis fit, `D` three-minute demo, `F` one-week feasibility, `P` local-first privacy, `S` maintenance stability, and `A` adoption potential are each scored 1–10. Complexity and risk are deliberately separate. A high numeric score does not bypass a release-safety gate.

| ID | Concept, workflow and unique value | Codex/architecture fit | Privacy, risk and validation experiment | Weighted / complexity |
|---|---|---|---|---|
| A | **Codex Change Defense:** defend staged/uncommitted/commit/range diff; explain intent, interactions, failure/rollback/tests. A change is a better cognitive unit than a whole repo. | Reuse scanner/redactor/budget/interview/report; add `GitChangeSource`, diff metadata and change-analysis schema. Codex is valuable for context-specific questions, not attribution. | Local Git only; never claim authorship. Paper prototype: manually run five developers through one diff. | **8.55 / M** |
| B | **Codex Session Attestation:** explicit session export plus diff match; distinguish Codex facts, code facts and human answers. | App-server or SDK sidecar; new provenance port and matching rules. Deepest Codex fit. | Transcript/consent/version drift/mismatch risk. POC only with one user-selected app-server thread. | **8.10 / XL — not release eligible** |
| G | **Counterfactual Defense:** ask what breaks under retries, crashes, concurrency and rollback. | Extends question-generation policy; no new external surface. | Avoid false security claims; compare answers before/after explanation in a manual study. | **8.25 / S–M** |
| L | **Test Prediction Challenge:** developer predicts tests, failures and missing cases before results. | Reuse interview/scoring; optional local Maven/Git test-result import later. | Do not execute source by default; ask prediction first. Pilot with ten change examples. | **8.10 / S–M** |
| O1 | **Bounded Change Receipt:** event receipt for a CodeDefense-owned `exec --json` run, storing only event/diff hashes and local result. | Existing process runner needs a JSONL parser and opt-in record model. | Useful provenance for its own run only; no historical attribution. | **7.90 / M** |
| J | **Explain-Before-Accept:** optional one-question checkpoint after a prepared patch. | Best as a local Skill/CLI handoff, never auto-accept. | Directly overlaps Contral/Comprende; usability is fragile in unattended tasks. Test with a mocked handoff. | **7.80 / M** |
| E | **Codex Handoff Package:** a second developer defends a concise change map and risks. | Reuse report renderer/store; optional Codex structured handoff generation. | Value depends on teams; label agent facts separately. Test with a pair-programming handoff. | **7.75 / M** |
| O7 | **Failure-Mode Ticket:** turn each selected hunk into one operational hypothesis and an answerable “what would you observe?” question. | Codex question generation plus deterministic hunk/line facts. | Strong demo, but not an incident predictor. Validate with maintainers' relevance ratings. | **7.70 / S** |
| M | **Architecture Decision Defense:** for architectural diffs, defend trade-offs, migration and rollback; draft ADR only after defense. | New change classifier and ADR renderer; no external integration. | “Architecture-changing” is subjective; avoid automatic ADRs. Test classification precision manually. | **7.45 / M** |
| O6 | **Patch-proof ledger:** local, expiry-based record of diff hash, questions and result; no source. | Reuse report store and local score; optional Codex context. | Not cryptographic identity and not compliance. Validate whether users return to it during review. | **7.40 / S** |
| N | **Codex-assisted onboarding:** progressive active defenses for a new contributor. | Reuse all current interview machinery; needs curriculum selection/history. | Useful educationally but overlaps learning products and needs consent. Pilot with two newcomers. | **7.25 / M** |
| K | **Defense Replay:** another teammate replays a sanitized fixture using paths/hashes/questions. | New replay domain, deterministic fixture parser and report variant. | Hashes can leak limited structure; no source. Validate inter-rater consistency. | **7.20 / M** |
| F | **Security-sensitive Defense:** deeper questions for auth, crypto, SQL, paths, processes, concurrency, etc. | Rule-based risk tags plus the question generator. | Risks becoming a scanner and implying security coverage. Compare against Semgrep/Snyk, do not duplicate findings. | **7.15 / M** |
| O2 | **Thread-to-diff matcher:** app-server selected thread and local diff are matched by normalized patch hashes. | App-server JSON-RPC port; capability/version checks. | High privacy and drift risk; no match must be neutral. POC with three known sessions. | **7.15 / XL** |
| C | **Understanding Merge Gate:** machine-readable PASS/REVIEW_NEEDED/INCOMPLETE/EXPIRED. | Local JSON output first; GitHub Action only later. | High gaming/employment/compliance risk. Test voluntary local pre-push artifact; never block initially. | **6.90 / M–L** |
| O3 | **Blind Explanation First:** show only a bounded change intent/risk card before revealing the diff; compare prediction with code. | Reuse diff source and questions; Codex can generate the card. | Unconventional but may frustrate users; test completion and perceived fairness. | **6.85 / S** |
| O10 | **Adversarial Plausibility Drill:** Codex creates one believable wrong explanation; user identifies the violated invariant. | New structured alternative-explanation schema. | Must clearly label synthetic text; may become a trick quiz. Test false-frustration rate. | **6.75 / M** |
| O5 | **CodeDefense MCP tool:** other agents request a bounded defense preparation, then a human runs it. | New MCP server/adapter; no current Java client. | Composability, but no new human signal by itself. POC as a static tool result. | **6.60 / L** |
| D | **CodeDefense Codex Skill:** package an explicit post-task handoff. | Skill invokes existing JAR/local CLI; no new model schema necessary. | Good discoverability, weak new value; interactive UX is uncertain. Test a local terminal-only skill. | **6.35 / S** |
| H | **Knowledge Drift:** retain opted-in topic/module scores and generate reassessment. | New local history model and targeting policy. | Surveillance and stale-score risks; avoid team dashboards. Test whether users opt in and return. | **6.35 / M** |
| O4 | **MCP-owned Codex defense conversation:** an orchestrator calls Codex, records its thread ID, then asks CodeDefense to prepare a human checkpoint. | Codex MCP plus a companion orchestrator. | Strong Codex story but needs external runtime and is not Java-first. | **6.30 / L** |
| O8 | **Ownership Relay:** outgoing/incoming developer each answer complementary change questions; report overlap/gaps, not rank. | Reuse handoff package/interview; optional Codex prompts. | Useful for handoffs, but two-human coordination is costly. | **6.25 / M** |
| I | **Team Understanding Map:** aggregate opt-in scores/modules to spot bus factor. | New identity/team/storage product, not a CLI increment. | Highest employment/gaming/privacy risk; reject for MVP. | **5.80 / XL** |
| O9 | **AI-abstention drill:** user states which parts of a change they cannot explain and asks a targeted learning follow-up. | Minimal extension to answer state; Codex may tailor explanation. | Honest and educational, but weak differentiation/metrics. | **5.95 / S** |

### Why apparently attractive options are rejected or deferred

* **B** scores highly because session-aware provenance is genuinely Codex-native, but it is not safe enough to anchor a release feature until a supported, versioned, consented path has been proven across installed CLI versions.
* **C** and **I** may sound commercially attractive but easily imply employment surveillance, merge authority or compliance. A local educational signal should not become a personnel score.
* **F** risks becoming a low-coverage vulnerability scanner in a market already served by Semgrep, Snyk and Codex Security. Counterfactual questions can use risk tags without asserting detection coverage.
* **D/O5** improve workflow composition but do not add a new human-understanding loop alone.

## 7. Ranked shortlist

The numeric ranking is intentionally separate from release eligibility.

| Rank | Candidate | Score | Release eligible now? | Why |
|---:|---|---:|---|---|
| 1 | A. Codex Change Defense | 8.55 | Yes, after discovery | Anchors the assessment to a concrete owned change. |
| 2 | G. Counterfactual Defense | 8.25 | Yes | Makes “understanding” operational rather than descriptive. |
| 3 | B. Codex Session Attestation | 8.10 | **No** | Strongest Codex-native story; blocked by provenance and Java/stability constraints. |
| 4 | L. Test Prediction Challenge | 8.10 | Yes | Cheap, observable learning signal; pairs well with A. |
| 5 | O1. Bounded Change Receipt | 7.90 | Research only | Honest provenance for owned runs, not historical authorship. |
| 6 | J. Explain-Before-Accept | 7.80 | Conditional | Good checkpoint but direct competitor overlap and UX uncertainty. |
| 7 | E. Codex Handoff Package | 7.75 | Yes | Strong peer-ownership workflow without merge claims. |
| 8 | O7. Failure-Mode Ticket | 7.70 | Yes | Fast, memorable operational demonstration. |
| 9 | M. Architecture Decision Defense | 7.45 | Yes | Valuable for material changes; classifier quality is the risk. |
| 10 | O6. Patch-proof ledger | 7.40 | Yes | Useful support artifact, not an authority. |
| 11 | N. Onboarding Mode | 7.25 | Later | Continues the thesis but demands careful history/consent design. |
| 12 | K. Defense Replay | 7.20 | Later | Adds team transferability without source retention. |

## 8. Top three

### 1. Codex Change Defense (A)

* **Pitch:** “Before you ship an AI-assisted change, defend the exact diff you are about to send.”
* **Target user:** an individual developer using Codex/Cursor/Claude/Copilot who wants confidence before review; later, a reviewer receiving an agent-authored change.
* **Workflow:** select `--staged`, `BASE..HEAD`, or a commit; CodeDefense obtains only the local diff plus bounded related context; it previews disclosure; Codex prepares three evidence-backed questions; the human answers; Java scores; a local report identifies the exact diff hash and the limited educational signal.
* **Why unique:** current review tools judge the patch. This workflow binds a human explanation to the patch without requiring GitHub, an API key, or a browser.
* **Why Codex matters:** Codex can connect a hunk to architectural context and formulate questions a static diff parser cannot. Git is still the source of truth for exact change identity.
* **Smallest viable prototype:** staged-diff only, Java `GitChangeSource` using tokenized `git diff --cached`, existing 30/120-KiB boundaries, one change-analysis schema, existing interview/report. No PR, session import, gate, GitHub or history.
* **Estimate:** 2–3 focused weeks after a one-week discovery prototype; around 20–30 production/test files because it needs Git process safety, diff parsing, prompt/report privacy rules and fixture tests.
* **Risks:** complex changes need surrounding context; commits can contain sensitive data; developers can rehearse. Mitigate with preview, hashes, a fixed question budget and precise wording.
* **Kill criteria:** fewer than three of five target developers choose it before review; they prefer whole-repo defense; questions are routinely judged irrelevant; or safe diff context cannot fit the existing disclosure budget.

### 2. Counterfactual Defense (G)

* **Pitch:** “Do not just describe the patch—predict how it fails.”
* **Target user:** anyone changing retries, persistence, concurrency, API behavior or operational paths.
* **Workflow:** after a normal or change defense, one primary/follow-up question asks about retry, duplicate, crash, timeout, rollback or test observability; the human answers before receiving feedback.
* **Why unique:** it transforms recall into a demonstration of a usable mental model. It is complementary to A and avoids competing as a generic AI reviewer.
* **Why Codex matters:** it proposes context-sensitive counterfactuals from the bounded code/diff; local rules ensure the question remains in scope.
* **Smallest viable prototype:** add a question-policy flag and a deterministic fixture corpus; no new external integration.
* **Estimate:** one week as an A companion; several days as a manual fake-output experiment.
* **Risks:** false premise or trick-question perception. Require evidence paths and a “not enough context” answer path.
* **Kill criteria:** maintainers cannot distinguish it from generic interview trivia, or evidence review finds frequent false premises.

### 3. Codex Session Attestation (B)

* **Pitch:** “With explicit consent, compare a selected local Codex thread to the local diff, then separate what the agent did from what the human can explain.”
* **Target user:** advanced Codex desktop/CLI users who want a handoff/ownership record after an agent task.
* **Workflow:** user selects a local app-server thread; a version-aware adapter reads its completed file-change data; CodeDefense compares normalized patch/path hashes to a selected Git diff; only an exact match enables a report with source labels; the human completes the normal defense.
* **Why unique:** this is the only option that uses Codex runtime events rather than simply using a model to create questions.
* **Why Codex matters:** thread/item data can include task messages, file changes, commands, tool calls and final response. [Official app-server items](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md), accessed 2026-07-18.
* **Smallest viable prototype:** a throwaway local tool, not the product: app-server stdio handshake, `thread/list`, explicit one-thread selection, a one-file patch-hash match, no persistence.
* **Estimate:** 3–5 weeks for an experimental prototype plus a compatibility matrix; more for release hardening. A TypeScript/Python companion may be cheaper than a custom Java JSON-RPC client but violates the current single-JAR simplicity.
* **Risks:** missing threads, protocol evolution, sensitive transcripts, accidental surveillance, false matches, unsupported UI/cloud session shapes.
* **Kill criteria:** no stable end-to-end match works on CLI + desktop across Windows/macOS/Linux; only raw rollout scraping makes it work; or consented users reject transcript access.

## 9. Recommended direction

Choose **move all expansion to Iteration 10**.

Iteration 9 should remain reliability, packaging, documentation, demo/release preparation. The project’s release story is already stronger when it demonstrates a bounded local workflow, embedded sample, explicit preview and a safe report than when it introduces an unvalidated Git/session feature.

Iteration 10 discovery should first validate A + G + L as one coherent workflow, called **Change Defense**, with an explicit non-goal: it is neither a code review, an automatic merge gate nor provenance certification. Session Attestation is a separate experimental spike, not a dependency of Change Defense.

## 10. Proposed experiment

Run a seven-day, no-code product experiment with five developers who used an AI coding tool recently.

1. Select five real, non-sensitive local changes of 30–300 changed lines (one bug fix, API change, retry/transaction change, test-only change, refactor).
2. Produce a **manual fake CLI transcript** that names the local diff, shows a privacy preview, then asks three questions: intent/interaction, one counterfactual, and test prediction. Do not claim an actual score or Codex provenance.
3. Run a 12-minute moderated defense per developer; ask whether they would run it before a PR and what information they would permit it to retain.
4. Measure: voluntary completion, relevance rating per question, time-to-first-use, willingness to use again, willingness to store diff hashes, and whether they prefer IDE/PR/local CLI.
5. Compare with a control prompt that merely summarizes the diff. The hypothesis is that counterfactual + prediction questions are materially more useful than a summary.

Success threshold: at least three of five ask to use it again before review and rate at least two questions as change-specific/useful; no participant believes it is a security or merge certification. Failure means release the MVP without the expansion and do not build an Iteration 10 feature by momentum.

## 11. Revised roadmap options

### A. Conservative release

* Iteration 9: reliability, Windows/macOS/Linux packaging checks, documentation, embedded sample demo, release artifact and clear privacy wording.
* Post-release: collect user feedback; no Git, GitHub, session, history or team data.
* Best when: a solo-maintained local CLI needs credibility and a predictable demo.

### B. Codex-native differentiation

* Iteration 9: same release scope.
* Iteration 10A: staged-diff Change Defense with counterfactual/test-prediction questions and local change receipt.
* Iteration 10B: optional app-server provenance spike behind an experimental flag, zero persistence by default.
* Best when: five-user experiment validates the change workflow and Codex-session consent is acceptable.

### C. Team/enterprise expansion

* After B succeeds: opt-in handoff package, replay fixture and machine-readable educational result.
* Only later: GitHub Action/GitHub App integration, account/role model, policy, retention configuration and legal/HR review.
* Explicitly exclude until then: employee ranking, mandatory merge blocks, security/compliance certification and hidden agent monitoring.

## 12. Open questions

1. Does `codex app-server` list/read the same persisted sessions across the current Windows desktop, CLI and IDE configurations, and which item fields remain available after a turn completes?
2. Can a stable app-server schema/version capability check be implemented in Java without silently accepting an incompatible installed CLI?
3. Which exact Git units do target users want: staged diff, worktree diff, one commit, range, or PR? Start with one.
4. How much related source context is needed for useful questions, and can it remain inside the existing 120-KiB privacy limit?
5. Should user answers remain in a change report, or should change reports default to scores/feedback only while whole-project reports preserve answers?
6. What is the appropriate expiry model for a local educational attestation after further changes to the diff?
7. Can “not enough information” be scored as good epistemic behavior rather than a failure?
8. Would a developer trust local hashes and transparent source labels, or want cryptographic signing/identity that would pull the product toward governance/compliance?
9. Can the product remain valuable to users of non-Codex agents while still honestly differentiating on a Codex-native optional receipt?
10. Does a Skill improve adoption enough to justify maintaining an additional distribution format?

## 13. Sources

All links were accessed 2026-07-18. Product capability and pricing/availability can change; this report treats links as a point-in-time record.

### Official OpenAI sources

* [Codex non-interactive mode (`exec`, JSONL, schema, resume, sandbox, CI)](https://learn.chatgpt.com/docs/non-interactive-mode)
* [Codex SDK (TypeScript and beta Python)](https://learn.chatgpt.com/docs/codex-sdk)
* [Codex app-server documentation](https://learn.chatgpt.com/docs/app-server)
* [OpenAI Codex app-server protocol repository](https://github.com/openai/codex/tree/main/codex-rs/app-server)
* [Codex as MCP server](https://learn.chatgpt.com/docs/mcp-server)
* [Codex GitHub Action](https://learn.chatgpt.com/docs/github-action)
* [Running Codex safely at OpenAI](https://openai.com/index/running-codex-safely/)
* [Codex Security overview](https://help.openai.com/en/articles/20001107-codex-security)

### Official competitor/product sources

* [GitHub Copilot Code Review](https://docs.github.com/en/copilot/concepts/agents/code-review)
* [CodeRabbit documentation](https://docs.coderabbit.ai/)
* [Qodo Code Review](https://docs.qodo.ai/code-review)
* [Graphite documentation](https://graphite.com/docs/get-started)
* [Sourcegraph code understanding](https://sourcegraph.com/solutions/code-understanding)
* [Cursor Bugbot](https://docs.cursor.com/bugbot)
* [Claude Code Action](https://github.com/anthropics/claude-code-action)
* [JetBrains Qodana](https://www.jetbrains.com/help/qodana/about-qodana.html)
* [Snyk Code](https://docs.snyk.io/scan-with-snyk/snyk-code)
* [Semgrep](https://semgrep.dev/)
* [CodeScene Code Health](https://codescene.com/product/code-health)
* [Swimm](https://swimm.io/home)
* [PR-Agent](https://github.com/The-PR-Agent/pr-agent) and [Reviewpad](https://docs.reviewpad.com/)
* [Comprende](https://www.comprende.dev/)
* [Contral Defense Mode](https://contral.ai/guides/defense-mode-prove-understanding)
* [Bridgekeeper](https://bridgekeeper.io/)
* [Engramme](https://open-vsx.org/extension/memorymachines/engramme-code)

### Research papers

* [AI-Assisted Code Review as a Scaffold for Code Quality and Self-Regulated Learning](https://arxiv.org/abs/2604.23251) (ICSE-SEET 2026)
* [Automated Code Review in Practice](https://arxiv.org/abs/2412.18531)
* [Does AI Code Review Lead to Code Changes? A Case Study of GitHub Actions](https://arxiv.org/abs/2508.18771)
* [A Task-Level Evaluation of AI Agents in Open-Source Projects](https://arxiv.org/abs/2602.02345)

### Community evidence

Community/vendor landing pages were used only to discover products and their public positioning; they are not independent evidence of efficacy. The direct product links in the competitor section document this limitation. No social-media adoption claim, price comparison, or unverified session-export claim is used as a recommendation premise.
