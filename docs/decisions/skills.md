# Pathland architecture skills

Four repository-local skills operationalize the ADR and traceability conventions.
Their source is `.agents/skills/`; OpenCode discovers that directory directly.
No global installation, config change, network access, or upstream LLP install
is required. Other harnesses can use the same Markdown contracts if they support
that location, or load the files explicitly; discovery varies by harness.

| Skill | Purpose | Changes files? |
|-------|---------|----------------|
| [pathland-orient](../../.agents/skills/pathland-orient/SKILL.md) | Assemble task-specific contracts, rationale, tests, and known gaps | No |
| [pathland-decision](../../.agents/skills/pathland-decision/SKILL.md) | Draft an ADR or proposed supersession using the existing template | Only when drafting files is requested; always Proposed |
| [pathland-review](../../.agents/skills/pathland-review/SKILL.md) | Evaluate an ADR, spec proposal, or architecture-sensitive diff | No |
| [pathland-maintain](../../.agents/skills/pathland-maintain/SKILL.md) | Find drift and propose reference/code/doc/status repairs | No |

## Use

In OpenCode, mention the skill by ID, for example:

- `Use @pathland-orient before changing media controller projection.`
- `Use @pathland-decision to draft an ADR for this proposed transport change.`
- `Use @pathland-review to review docs/decisions/0013-swiftui-shaped-signal-dsl.md.`
- `Use @pathland-maintain to audit media identity, or do a pre-pr check of this diff.`

These are prompts, not shell commands or installed slash commands. Skill
discovery depends on the harness/session; if a newly added skill is not yet
advertised, open a new session or ask the agent to read its `SKILL.md` explicitly.
Agents can select relevant skills from their descriptions. Merely loading a
skill does not authorize unrelated edits, acceptance, delegation, external model
calls, commits, or publication. Plan-mode restrictions still take precedence.

## Ownership and boundaries

The [ADR conventions](README.md) and [traceability policy](traceability.md) own
project policy. Skills provide reusable workflows and output shapes; they must
not invent a second authority for requirements or decision approval. Revise them
alongside policy changes and verify their links remain valid.

The design is inspired by [LLP's skills](https://github.com/ccheever/llp/tree/main/skills),
but these are Pathland-specific workflows, not installed upstream copies. They
use existing specs, historical ADRs, `status.md`, and audit evidence—not numbered
LLP documents, foundation/current overlays, or upstream update receipts.
There is no automatic multi-model review, shipping, or decision-acceptance loop.

## Validation and smoke scenarios

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check-references.py
```

CI checks skill names/descriptions, expected sections, and Markdown target
paths/anchors alongside reference-checker tests. These are structural checks,
not proof that an agent will follow prose. For a behavioral smoke review:

| Scenario | Expected behavior |
|----------|-------------------|
| Orient to media identity | Find MEDIA spec, ADR-0006, Rust/Kotlin references/tests, and the known ordering gap; make no edits |
| Draft a new ADR | Search existing decisions/history, allocate a safe provisional number, use the template, keep Proposed; don't retire the predecessor |
| Review material authored in the same session | Disclose self-review, cite evidence, do not claim independent approval |
| Audit the Rust surface-first layout | Recheck MEDIA-001 against current code; distinguish its violation from playback test coverage; propose a fix, don't apply it |
| Find a broken source anchor | Report the bad target and likely relevant section; do not silently retarget or redefine the contract |
| Encounter conflicting code and spec | Classify the discrepancy and seek a maintainer decision if intent is uncertain; do not bless code by editing the spec |

These scenarios are evaluation guidance, not a claim that live agent executions
have already passed them.
