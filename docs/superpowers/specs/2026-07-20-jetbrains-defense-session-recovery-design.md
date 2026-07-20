# JetBrains Defense Session Recovery Design

## Goal

Make the CodeDefense Tool Window distinguish the live staged gate, the latest
saved Passport, and the active defense session. Preserve actionable context
when a model evaluation fails, while ensuring every retry requires an explicit
user action.

## Scope

This is a focused JetBrains adapter correction on
`feat/iteration-08-13-defense-cockpit`. It does not change Git capture,
Passport identity, analysis prompts, scoring, persistence, Codex execution, or
the bridge protocol. Automated tests must not invoke Codex.

## User Experience

### Latest saved Passport

The latest saved Passport is rendered in a dedicated, read-only status area
labelled `Latest saved Passport`. Refresh replaces the value in that area; it
never appends the value to the defense-session transcript.

The staged gate remains a separate status at the top of the Tool Window. The
two statuses intentionally answer different questions:

- staged gate: whether the exact current Git index has a matching Passport;
- latest saved Passport: the newest locally stored Passport for the project.

Consequently, `UNDEFENDED` staged state may legitimately coexist with a
`CURRENT | COMMIT` latest Passport.

### Recoverable model failure

When the bridge reports `INVALID_MODEL_RESPONSE`:

- show the existing safe error message;
- end the failed child session normally;
- retain the current question and its evidence links;
- retain the transcript for diagnosis;
- enable the existing start action with the temporary label
  `Retry defense`.

Pressing `Retry defense` starts a fresh defense through the existing workflow.
It does not silently reuse an answer, skip confirmation, or invoke Codex until
the user explicitly proceeds through the normal disclosure boundary.

Starting a new preview/defense or completing a successful session restores the
button label to `Start defense`. Non-recoverable errors keep the existing
cleanup behavior.

## Architecture

The controller continues to own session state and error classification. The
view gains presentation-only operations for replacing latest-Passport status,
setting the start-action label, and preserving or clearing evidence according
to controller instructions.

No model-controlled text is persisted by the plugin. The retry state contains
only a boolean/presentation state; answers remain cleared after submission.

## Error and Privacy Rules

- No automatic retry and no hidden Codex call.
- No raw JSON, prompt, source content, answer, or evidence reason is logged.
- The safe `Codex returned an invalid response.` message remains unchanged.
- Evidence navigation retains its existing path-containment, symlink, and
  stale-range validation.
- Project disposal and cancellation retain bounded child-process cleanup.

## Tests

Add focused tests proving:

1. repeated Passport refreshes replace one dedicated status value and do not
   append to the session transcript;
2. staged `UNDEFENDED` and latest `CURRENT | COMMIT` can be displayed
   simultaneously without conflation;
3. `INVALID_MODEL_RESPONSE` preserves current evidence and changes the action
   label to `Retry defense`;
4. clicking retry starts one fresh workflow and does not itself bypass preview
   or confirmation;
5. successful completion and a new preview restore `Start defense`;
6. other error classes continue to clear evidence;
7. all tests use fakes and no test invokes Codex.

## Non-goals

- Retrying only one evaluation inside a terminated bridge session.
- Automatic retry, retry backoff, or retry counters.
- Bridge protocol changes.
- New Passport, Git, learning-radar, or provenance behavior.
- Changes to the Maven CLI outside what a regression test proves necessary.
