---
name: pathland-maintain
description: Audit Pathland code/spec/ADR drift, stale references, and missing test or status coverage; propose evidence-based repairs before a PR or for an explicit audit or reconciliation scope. Propose-only, never silently rewrite contracts.
---

# Pathland traceability maintenance

## Trigger

When asked to audit drift, check documentation co-evolution before a PR, reconcile
a change with its contract, or investigate broken references. Accept an intent
and scope in ordinary language; this is not an installed CLI command.

| Intent | Default scope |
|--------|---------------|
| `pre-pr` | Current staged/unstaged changes and relevant non-ignored untracked files |
| `audit` | The requested feature/subsystem; ask if none is specified |
| `reconcile` | An explicit revision range, document, or finding |

## Invariants

- Propose only: do not repair code/references, edit reports/status, retire ADRs,
  commit, or push. An implementation request after findings is a separate task.
- Use [traceability and drift policy](../../../docs/decisions/traceability.md)
  and [ADR lifecycle conventions](../../../docs/decisions/README.md#conventions).
- Do not assume newer code is right or normalize drift by silently rewriting a
  spec. Contradictory sources or uncertain intent require a maintainer decision.
- Distinguish demonstrated violations, static evidence, hypotheses, and coverage
  limitations. A passing reference checker is not semantic conformance.
- Preserve accepted ADR history and report outdated targets, not automatic
  mass retargeting. Unreferenced files are not automatically defects.
- Do not delegate or send repository content to external providers automatically.

## Workflow

1. Locate the repository root, read instructions, and state the intent and exact
   scope. Use current contracts, relevant accepted ADRs, affected `status.md`,
   existing tests, and prior linked audits. Do not treat an old audit as current
   evidence without rechecking its finding against the audited revision.
2. Run `python3 scripts/check-references.py` from the root. It checks the current
   checkout, not an arbitrary comparison revision. Inspect references in scope
   for semantic relevance, stale glosses, displaced comments, and unnecessarily
   broad targets. Look for important unlinked invariants without demanding density.
3. Compare actual behavior to the exact contract. For each mismatch identify
   whether code, specification, reference, or decision needs reconsideration;
   use the policy's resolution table. Do not infer the historical rationale.
4. Inspect tests for what they actually demonstrate. When safe and authorized,
   run focused existing tests. A counterexample can be reported from static
   evidence; label it unexecuted unless actually run. Ask before introducing
   probe files, running intrusive integrations, or making behavior changes.
5. Check co-evolution: missing spec/test/status changes, incomplete supersession
   links, or language binding/golden data changes implied by the diff. A changed
   section should prompt review of references to it even when its anchor survived.
6. Propose minimal repairs and follow-up tests. Keep a confirmed implementation
   bug separate from a proposal to change architecture. Do not apply any repair
   or create a report while executing this propose-only skill.

## Artifact

Return a scoped finding list and repair checklist in the conversation:

- Audit date and actual source revision plus relevant working-tree modifications.
- Stable finding identifier, classification, location/symbol, governing spec/ADR.
- Observed discrepancy, reproduction or evidence, and existing test coverage.
- Commands actually run/results and areas not checked.
- Proposed resolution, affected projects, tests/reference updates, and any
  maintainer decision required. Separate confirmed defects from coverage gaps.

If the user subsequently authorizes recording findings, use `docs/audits/` and
link unresolved implementation gaps from affected `status.md` as the policy
requires. The [media identity pilot](../../../docs/audits/media-controller-identity.md)
is an example of evidence and coverage disclosure, not a template for declaring
all audits conformant or automatically current.

## Hand-offs

- A genuinely new design direction: `pathland-decision`, then maintainer approval.
- A proposal requiring evaluation: `pathland-review`.
- A still-valid contract with defective code: propose a separately authorized
  implementation fix and regression test; do not rewrite the governing contract.
