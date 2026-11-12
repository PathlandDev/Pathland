---
name: pathland-orient
description: Assemble the governing Pathland specs, ADRs, source references, tests, and implementation gaps before a substantial coding task. Read-only and scoped to the requested change.
---

# Pathland orientation

## Trigger

Before substantial work on a Pathland subsystem, or when asked to identify the
constraints governing a file or proposed change. Accept a path, subsystem, or
task description. With no scope, inspect current staged/unstaged changes and
non-ignored untracked files; if none identify a task, ask rather than audit the
whole repository.

## Invariants

- Read-only: do not edit files, install tools, or change branches.
- Follow [document responsibilities](../../../docs/decisions/README.md) and
  [traceability policy](../../../docs/decisions/traceability.md). Specs define
  current requirements; ADRs explain decisions; `status.md` records coverage.
- Read actual target sections, not just filenames, reference glosses, or the ADR
  index. Proposed/Rejected/Superseded ADRs are not accepted current constraints.
- Cite evidence. Do not infer undocumented intent, certify implementation from
  a valid link, or silently choose a winner when documents conflict.
- Do not spawn agents or send code to another provider as part of orientation.

## Workflow

1. Locate the repository root (`git rev-parse --show-toplevel`) and relevant
   `AGENTS.md` instructions. Paths in prose below are repository-root-relative;
   Markdown links in this skill are relative to its own directory.
2. Determine the exact scope. Read `docs/decisions/README.md`, the relevant
   specs, and affected projects' `status.md`. Search the index and source even
   when no `@ref` exists; unannotated code is not necessarily unconstrained.
3. Follow standalone source `@ref` targets in scope, resolving paths from the
   repository root. Read linked ADR rationale and follow supersession links.
   Broaden one hop to related contracts/tests only when relevant to the task.
4. Inspect implementation entry points and existing tests. Find known issues
   in linked audit reports; distinguish a dated audit from current coverage.
5. Report broken references, contradictory contracts, and unexplained choices
   as open questions. Do not repair them during orientation.

## Artifact

Return a compact context pack in the conversation:

- Scope and affected projects.
- Governing spec sections and accepted ADRs, with links and a short explanation.
- Key constraints, each tied to a source; relevant rationale kept distinct.
- Existing tests, known gaps, and any unresolved drift or uncertainty.
- Likely spec/ADR/status/reference updates if the proposed change lands.
- Suggested hand-off, if any. Do not create a context or plan file by default.

## Hand-offs

- A new architectural choice is needed: `pathland-decision`.
- A proposal needs evaluation: `pathland-review`.
- Broken references or suspected code/doc disagreement: `pathland-maintain`.
- Otherwise return to the requested coding task; orientation itself makes no changes.
