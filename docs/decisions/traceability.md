# Architecture traceability and drift

This workflow connects implementation to the current contract and decision
rationale. A reference identifies what governs code; it is **not a certification
that the code complies**. Specs remain authoritative for requirements, ADRs for
decision history, and per-project `status.md` for implementation coverage.

## Source references

Put a standalone comment near the relevant declaration or implementation block:

```rust
// @ref spec/MEDIA.md#5-structural-swaps-the-motivating-contract [implements]
```

Use a repository-root-relative Markdown path, an optional heading fragment,
a required relation, and an optional short gloss following an em dash:

```text
@ref path/to/document.md#heading [relation] — Short explanation
```

| Relation | Meaning |
|----------|---------|
| `implements` | Implements the referenced requirement |
| `tests` | Exercises the requirement; does not imply exhaustive coverage |
| `constrained-by` | Shaped by an architectural constraint or decision |
| `explains` | Context, not a claim of implementation |
| `historical` | Deliberate reference to retired guidance, not a current constraint |

Normally code/tests point to a **specific spec section**, and that section links
to its ADR. Link directly to an ADR when the architectural boundary itself is
what matters. Use `historical` only for genuine historical context, not to bypass
updating an implementation's governing reference. Proposed ADRs cannot be
implementation constraints until accepted by a maintainer.

Keep directives in source comments (`//`, `///`, `//!`, block-comment `*`, or
`#`), not string literals. References are human-placed breadcrumbs, not
compiler-enforced attachments. The checker validates standalone directive lines;
it does not parse language syntax or prove which declaration a comment governs.
Use normal Markdown links in documentation. This is Pathland's convention,
inspired by LLP, not compatibility with the upstream LLP skill contracts.

## Adoption and change review

- Prioritize non-obvious invariants, architectural boundaries, and behavior an
  agent might otherwise simplify incorrectly. Do not annotate every function.
- Add accurate references when touching governed code; review scope and target
  meaning, not just whether the path exists.
- Requirement changes include spec updates and behavioral tests. Significant
  architectural changes also propose a new ADR, superseding the prior record
  only after maintainer acceptance.
- Keep references, contract changes, tests, and affected `status.md` updates
  together. Do not mechanically annotate files without reviewing their behavior.
- Existing unreferenced code is a review backlog, not an automatic CI failure.

## Drift resolution

Do not assume the code or the document is right merely because it is newer.
Report the discrepancy before changing the governing decision.

| Finding | Resolution |
|---------|------------|
| Code violates a still-valid contract | Fix the implementation and add a regression test |
| Spec misstates intended behavior | Propose a correction and assess compatibility; do not silently bless the code |
| Architecture intentionally changes | Propose a superseding ADR, then coordinate specs, code, and tests |
| Reference is inaccurate or stale | Repair the reference; investigate whether behavior also drifted |
| Intent is uncertain or documents conflict | Record evidence and request a maintainer decision |
| Requirement lacks adequate tests | Record the coverage gap separately from a demonstrated violation |

A finding records: an identifier, audit date and source revision, governing
spec/ADR, implementation symbols, observed behavior, reproduction or test
evidence, existing coverage, and proposed resolution. Distinguish static review,
executed tests, and hypotheses. Never claim a test ran when it did not.

Store cross-project audit reports under `docs/audits/`. Put unresolved
implementation gaps in affected projects' `status.md`, linking to the report;
the audit is evidence, not a competing implementation-status system. Resolve
findings explicitly with a linked fix or a maintainer-approved contract change.
An audit is a dated snapshot, not an automatically current conformance claim.

## Automated validation

```sh
python3 -m unittest discover -s scripts -p 'test_check_references.py'
python3 scripts/check-references.py
python3 scripts/check-references.py --report-unreferenced
```

The dependency-free checker scans tracked and non-ignored untracked source
files in supported languages; CI checks the committed checkout. It validates
syntax/relations, repository-local Markdown paths and heading fragments, and
references to retired/proposed ADRs. Symlinked source files are not scanned;
targets may not escape the repository. Inline/fenced documentation examples and
external URLs are not source reference targets. Supported extensions are listed
in `SOURCE_SUFFIXES` in the checker; add tests when extending the convention.

Coverage output is informational; generated and ordinary source files both
count, so it is not a quality score. CI cannot establish that a reference is
semantically accurate or that the implementation satisfies it. Review, golden
vectors, engine traces, and regression tests provide that additional evidence.

## Pilot

The first pilot is [media-controller identity](../audits/media-controller-identity.md):
Rust and Kotlin projection/identity implementations and their tests reference
the same media contract. Renderer playback behavior is a separate audit scope.
