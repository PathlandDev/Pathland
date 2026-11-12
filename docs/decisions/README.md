# Architecture decision records

ADRs explain **why** Pathland makes consequential architectural choices.
[`spec/`](../../spec/) defines the **current contract**; each implementing
project's `status.md` tracks **implementation coverage**. ADRs do not introduce
additional mandatory behavior or replace either source of truth.

## Index

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-fixed-width-binary-protocol.md) | Fixed-width binary instructions and transport-independent frames | Accepted |
| [0002](0002-native-declarative-layout.md) | Declarative structure and native renderer-owned layout | Accepted |
| [0003](0003-application-state-and-delta-emission.md) | Application-owned state and diff-based emission | Accepted |
| [0004](0004-binary-input-events.md) | Binary input events rather than payload callbacks | Accepted |
| [0005](0005-semantic-appearance.md) | Application intent and renderer-owned appearance | Accepted |
| [0006](0006-media-controller-identity.md) | Playback identity separate from media surfaces | Accepted |
| [0007](0007-portable-protocol-core.md) | Portable, emitter-free protocol core | Accepted |
| [0008](0008-pure-jvm-server-libraries.md) | Pure-JVM server libraries with opt-in native interop | Accepted |
| [0009](0009-cross-language-conformance.md) | Language-owned bindings checked by shared conformance data | Accepted |
| [0010](0010-server-side-proxy-composition.md) | Server-side composition through ordinary protocol sessions | Accepted |
| [0011](0011-explicit-layout-allocation.md) | Explicit Fixed/Fill/Hug allocation rather than implicit stretching | Accepted |
| [0012](0012-generated-html-base-design.md) | One generated, token-layered HTML base design | Accepted |
| [0013](0013-swiftui-shaped-signal-dsl.md) | SwiftUI-shaped authoring with signal-based reactivity | Accepted |

## Conventions

Source-reference syntax, review obligations, drift resolution, and validation
commands are defined in [Architecture traceability and drift](traceability.md).
The [Pathland architecture skills](skills.md) provide reusable orientation,
decision-authoring, review, and maintenance workflows for these conventions.

- Use `NNNN-short-decision-name.md`, allocated sequentially. Never reuse a
  published number. Resolve provisional number collisions before merging.
- Start with [the template](template.md). Keep context, decision, rationale,
  alternatives, consequences, and source links distinct.
- Status is **Proposed**, **Accepted**, **Rejected**, or **Superseded**. A
  proposal is not binding; only a maintainer can accept a new decision.
- Accepted records preserve decision history. Correct factual errors in place;
  change a decision through a new ADR and mark the old one Superseded, linking
  both records. Update the index and current specs in the same change.
- Link ADRs to the relevant specs, and specs back to the ADRs. Keep complete
  requirements in specs so readers need not follow rationale links to find them.
- `AGENTS.md` retains essential guardrails and points here for rationale.
  READMEs retain orientation and usage instructions.
- Do not invent alternatives, benchmarks, author approval, or recovered intent.
  Identify uncertainty explicitly; an uncertain new proposal stays Proposed.

## Initial extraction provenance

ADRs 0001–0013 were extracted on **2026-11-12** from existing specifications,
READMEs, and `AGENTS.md`. Their Accepted status records decisions already
expressed by those sources; it does not claim a new approval or an original
decision date. Each record links its evidence. Consequences describe implications
of the documented choice, not a reconstructed historical evaluation.

The source baseline is commit
[`e6f89f5f8b03da49f4a6c10488e2512489701a56`](https://github.com/PathlandDev/Pathland/tree/e6f89f5f8b03da49f4a6c10488e2512489701a56).
Consult that revision for original wording moved out of living documents by this
extraction. Accepted describes the architectural decision, not implementation
completeness or stabilization of the draft protocol.

The initial extraction covers cross-cutting architecture, not every property or
implementation detail. Local comments, operational instructions, deployment
guides, and unfinished plans remain in their existing homes. Missing historical
alternatives are explicitly not reconstructed. Future extraction can capture
additional decisions when their rationale is established.

Pathland uses conventional ADRs and existing specs, not a parallel `llp/`
corpus. The media pilot adds Pathland-specific reference validation; the
subsequent [skills](skills.md) operationalize those conventions without adopting
LLP's document hierarchy or installing upstream skills.
