# Generative AI transparency

Pathland is a **human-directed** engineering project. This statement documents how
generative AI is used in its development, in line with the [NLnet Restack GenAI
policy](https://nlnet.nl/foundation/policies/generativeAI/).

## How AI is used

- **Design decisions are human.** The architectural choices — the fixed-size ring
  buffer and 16-byte opcode slot, "declarative, not positioned", renderer
  statelessness, signal-based diff emission, the single opcode format for both
  directions, the specification and conformance vectors, and the choice of the
  first DSLs (Rust + Java) — were made and steered by the maintainer. The spec
  (`spec/`) is the authority and **locks in** accepted decisions.
- **AI assists with implementation and drafting.** Most code, tests, and
  documentation were drafted with generative-AI assistance under the maintainer's
  direction and then reviewed, validated, and integrated by the maintainer.
  Tools used: **Gemini** (high-level design brainstorming, reasoning about
  technical approaches) and **DeepSeek v4.0 Flash / Pro** (code and document
  assistance).
- **Human accountability.** The maintainer understands and can explain the design
  and code decisions, and is responsible for correctness, clarity, and
  reproducibility. AI output is never merged unexamined.

## Contribution policy

- All code is contributed under the [Apache License 2.0](LICENSE) and must be
  original or compatible with that license (AI-assisted contributions must not
  reproduce copyrighted or incompatible material).
- If you use AI assistance in a pull request, say so in the PR description and
  ensure a human has reviewed and understood the contribution before submitting.

## Provenance

Prompt-provenance logs for the code and grant-drafting sessions are available on
request (opencode session exports; a best-effort summary of the Gemini
discussions). See also [`docs/GRANT.md`](docs/GRANT.md) §13 and
[`docs/NLNET-APPLICATION.md`](docs/NLNET-APPLICATION.md).