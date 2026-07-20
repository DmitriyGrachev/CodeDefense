# Codex app-server compatibility for experimental provenance

Iteration 8.12 is off by default. Compatibility is fail-closed: an unknown Codex CLI version produces `UNAVAILABLE`; CodeDefense does not attempt best-effort transcript parsing.

| Codex CLI | Offline fixture contract | Real local thread acceptance |
|---|---|---|
| 0.144.0 | Handshake, notifications, `thread/read`, experimental `thread/items/list`, bounded fallback, and safe failures covered | Pending separate explicit authorization |
| 0.143.0 | Same previous-version projection contract covered by the compatibility allowlist and version-independent fixtures | Pending separate explicit authorization |
| Any other version | Rejected as `UNAVAILABLE` before app-server launch | Not supported |

The fixture protocol follows the official [Codex app-server documentation](https://learn.chatgpt.com/docs/app-server): newline-delimited JSON over stdio, one `initialize` request followed by `initialized`, and explicit reads of a user-selected thread. The production launcher uses `codex app-server --listen stdio://` without a shell wrapper.

These fixture results prove CodeDefense's own bounds, parsing, fallback, cleanup, and privacy behavior. They do not claim that a future Codex build has an unchanged experimental item schema. One disposable non-sensitive real-thread read is required before Iteration 8.12 can be checked as accepted.
