---
name: pathland-review
description: Review a Pathland ADR, spec proposal, or architectural diff against current contracts, decision rationale, compatibility, tests, and traceability. Report evidence-based findings without editing or accepting decisions.
---

# Pathland architecture review

## Trigger

When the user requests review of an ADR, spec proposal, or architecture-sensitive
diff. Accept a document or explicit Git range. Without a target, use current
staged/unstaged changes plus relevant untracked files; if there is no change,
ask what should be reviewed. Do not assume comparison with a remote branch.

## Invariants

- Read-only: no code/doc/status edits, automatic approval, commits, or pushes.
- Apply [ADR conventions](../../../docs/decisions/README.md#conventions) and
  [drift resolution](../../../docs/decisions/traceability.md#drift-resolution).
- Review against current specs and accepted decisions; proposals describe a
  possible future, not already binding behavior. Flag conflicting authorities.
- Cite concrete sections and implementation/test evidence for findings. Clearly
  distinguish a demonstrated violation, risk, and missing evidence.
- Never fabricate an independent review, successful test, or external opinion.
  If this session authored the material, label the result a self-review.
- Do not delegate or disclose repository content to external providers without
  explicit user authorization. A review request alone does not authorize either.

## Workflow

1. Find the repository root and read applicable instructions. Orient to the
   target's relevant specs, ADRs, source references, and affected `status.md`.
2. Read the complete proposal and relevant diff, not just its summary. Check
   whether rationale has evidence and alternatives/consequences are honest.
3. Examine architecture boundaries: binary commands/events, declarative native
   layout, application state versus output/input caches, actual-change deltas,
   portability, and native/JVM separation as applicable. Do not impose unrelated
   constraints or assume every performance claim has already been measured.
4. Check wire/ABI compatibility, ID allocation, snapshots/recovery, and ownership
   or lifetime implications where relevant. Verify corresponding spec, manifest,
   golden trace, binding, and test updates are accounted for—not blindly required
   for a documentation-only change.
5. Check that code/tests refer to precise governing sections and that reference
   meaning still matches behavior. Run `python3 scripts/check-references.py`
   from the repository root for a current-checkout review. It does not validate
   some other Git revision; disclose that scope limitation for range reviews.
6. Inspect tests and, when appropriate and permitted, run focused checks without
   editing tracked files. Record exact commands/results and untested areas.
   Avoid tests requiring external services or sensitive disclosure without consent.

## Artifact

Return in the conversation:

- Reviewed target, revision/range or working-tree scope, and self-review disclosure.
- Findings ordered by severity, each with location, governing requirement,
  evidence, consequence, and a concrete suggested resolution.
- Open questions, compatibility risks, and test/traceability gaps.
- Checks actually run and their results; distinguish inspection from execution.
- A recommendation (revise / ready for maintainer decision / needs evidence),
  never an acceptance transition. Say explicitly when no findings were found,
  without implying exhaustive proof.

Do not save a review artifact unless asked. If asked to persist it, preserve the
review scope, date, actual reviewer/session provenance, and evidence; do not
misrepresent a self-review as independent verification.

## Hand-offs

- New/revised architectural choice: `pathland-decision`.
- Existing code/doc drift or broken references: `pathland-maintain`.
- Approval remains with the maintainer; implementation is a separate authorized task.
