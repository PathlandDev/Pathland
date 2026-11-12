---
name: pathland-decision
description: Draft or propose superseding a Pathland ADR under docs/decisions using the repository template, documented evidence, and maintainer-controlled acceptance. Use for consequential architectural choices, not ordinary implementation details.
---

# Pathland decision authoring

## Trigger

When asked to capture a consequential choice, extract established rationale, or
propose replacing an architectural decision. If the problem or intended choice
is unclear, ask before allocating a number. A request to discuss a decision is
not permission to write files.

## Invariants

- Follow [ADR conventions](../../../docs/decisions/README.md#conventions), the
  [template](../../../docs/decisions/template.md), and
  [drift rules](../../../docs/decisions/traceability.md#drift-resolution).
- New ADRs start **Proposed**. Never accept, reject, or supersede on the
  maintainer's behalf. The initial extraction's Accepted status is not a
  precedent for agent approval of new decisions.
- Preserve accepted decision history. A material reversal needs a new record;
  factual corrections do not require manufacturing a new architectural choice.
- Do not duplicate requirements into ADRs or quietly rewrite specs to legitimize
  drift. A proposed decision does not change the current contract.
- Separate documented evidence, maintainer-confirmed reasoning (with attribution
  and date), and unconfirmed hypotheses. Never invent historical alternatives,
  benchmarks, original dates, or approval.
- Do not overwrite existing files, install upstream LLP, or create its hierarchy.

## Workflow

1. Locate the repository root and read the relevant instructions, ADR index,
   current spec sections, and existing decisions. Use `pathland-orient`'s method
   if context is missing; source paths below are repository-root-relative.
2. Check whether this is already covered, a factual correction, or a new choice.
   For an existing accepted decision, propose a correction or superseding record
   rather than rewriting the historical choice in place.
3. When authorized to draft a file, allocate the next `NNNN` after the highest
   number in current ADR filenames **and locally available Git history**:
   inspect `git log --all --name-only --format= -- docs/decisions` as well as
   current/untracked files. Never reuse deleted numbers. In a shallow or partial
   history, disclose uncertainty and ask before assuming an allocation is safe;
   do not fetch without authorization. Branch numbers remain provisional until
   integration; resolve collisions before merge.
4. Create `docs/decisions/NNNN-short-name.md` using the template. Use the actual
   recording date, affected scope, and a linked `Supersedes` field where relevant.
   Draft context, decision, rationale, alternatives, consequences, and sources.
   For extraction, identify the source revision and unknown historical details.
5. Add the Proposed record to the ADR index. Link to current contracts, but do
   not present proposed requirements as already normative or retire an accepted
   predecessor. Describe the spec/code/test/status updates acceptance would need.
6. Review local paths/anchors and the diff. Run the reference checker if source
   references changed. Stop at the proposal; no commit/push or implementation
   unless separately requested.

## Artifact

Return the proposed ADR (in conversation or authorized file), its status and
allocated path, supporting evidence, open questions, alternatives not yet
evaluated, and the integration checklist. Identify any author-confirmation gaps.

## Hand-offs

- `pathland-review` evaluates the proposal; it does not approve it.
- The maintainer decides acceptance/rejection. Only after explicit acceptance
  should the requested implementation update supersession links, the index,
  current specs, code references, tests, and affected `status.md` together.
- If this was an implementation defect rather than a new choice, use
  `pathland-maintain` to classify the drift instead of creating a justifying ADR.
