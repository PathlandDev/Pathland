# Pathland — Grant Proposal (Restack / Open Internet Stack)

**Fund:** NLnet **Restack** (Open Internet Stack) — call opened **Sept 3, 2026**; deadline **Nov 3, 2026, 12:00 CET**
**Status:** Ready to adapt into the NLnet propose form
**Prepared:** 2026-08-31 · **Updated:** 2026-09-14

> **Proposal name:**
> **Pathland — an implementable open UI-protocol standard: specification, conformance, validation, hardening, evidence.**

---

## 1. Applicant

| Field | Value |
|-------|-------|
| Legal entity | Apaq ApS |
| Country | Denmark (EU) |
| VAT / CVR | DK41717955 |
| Website | https://apaq.dk |
| Contact | Michael Krog · mic@apaq.dk |
| Role | Founder / lead developer (solo) |
| Capacity | Part-time R&D, 10 h/week during the project |
| Background | 25 years building software (financial, insurance, planning); built Previsto (route planning for the service industry, metaheuristic optimisation) from the ground up; designed and shipped **Lumen**, a SwiftUI-shaped design system used internally at SOS International; now domain architect there, expanding Lumen across the frontend teams. |

---

## 2. Problem

Server-driven UI (SDUI) and cross-platform UI are fragmented: each platform
re-invents retained-mode UI inside a closed framework (Flutter, React Native,
Compose Multiplatform, per-vendor SDUI like Airbnb/Lyft/Spotify). The result:

- **No open, language- and platform-agnostic *protocol*** exists for describing
  declarative, retained-mode UI — only frameworks that couple the UI model to a
  specific runtime.
- **No single wire format** serves the full spectrum from embedded
  microcontrollers to in-browser to server-driven enterprise apps, so teams
  build and maintain N different UI transports.
- **Full-tree serialization is wasteful**: typical SDUI re-sends large JSON
  payloads; large, reactive trees cannot sustain high frame rates on the wire or
  on the main thread.
- **Enterprise cost of rich clients**: teams pay for a BFF, a separate frontend
  application, a client-side state model, and a JS toolchain — and still expose
  REST APIs and JWT tokens to the browser, maintain two languages across the
  stack (Java + TypeScript), and struggle with microfrontends and bundle size.
  This is the pain the applicant lives with daily at an enterprise (SOS
  International).

## 3. Innovation — a standard, not another framework

Today's popular UI frameworks never re-invent themselves, and stay locked to
running everything on the main thread. Pathland separates the declarative UI
model from the renderer with a **binary protocol**, and does **signal-based
change detection inside the model**. That yields both better performance than
existing web frameworks and the flexibility to use **any language for any
renderer on any device**.

Pathland describes **WHAT** the UI is — structure + constraint properties
(VStack, HStack, Text, spacing, padding, alignment) — as a stream of **fixed
16-byte opcodes** in a ring buffer, and never **WHERE** (no layout, no rects).
Renderers are **pure functions of the stream**, so the same opcode stream maps
onto native widgets on every platform (GTK4, DOM/HTML, embedded-planned).

The engine is **transport- and process-agnostic**: the producer and consumer
only need to exchange bytes, so it runs identically across embedded dual-core,
pure in-browser (WASM), native desktop (C ABI / JNA over a zero-copy shared
ring), and distributed server-driven modes.

**Emission is diff-based and reactive**: a signal bound to a node re-emits only
that node's deltas; an unchanged tree emits **zero** opcodes. That is what makes
the binary format dramatically smaller than JSON SDUI and fast enough for a
120 FPS interaction target.

The open-standard angle: anyone can implement a Pathland renderer or DSL in any
language against the spec and the golden conformance vectors, with no lock-in to
a runtime.

## 4. The specification (exists — split, not monolithic)

The normative spec is **already present** and is the contract this proposal
matures:

| File | Content |
|------|---------|
| `spec/OPCODE.md` | 16-byte opcode format, ring buffer, arena, frame lifecycle, value types, design tokens |
| `spec/PRIMITIVES.md` | Primitive views + component IDs |
| `spec/MODIFIERS.md` | Core modifiers/properties + value types + enum codes |
| `spec/EVENTS.md` | Core events + listener bits |
| `spec/CONFORMANCE.md` | Golden byte vectors |
| `spec/DSL.md` | Authoring surface (SwiftUI-shaped DSL contract) — informative |

Gap this proposal fills: a **spec index + consolidated property-key registry**
and a **cross-language validator** so the split files read as one implementable
whole and cannot drift.

## 5. Scope & work packages (core-first, Java + SSR first)

The **core is the deliverable** (an implementable open standard); the visual
layer is cheap proof, not the product.

**Deliberately narrow first scope.** Cross-platform and polyglot deliverables
require great tooling, so this first grant targets **Java only, and SSR only**
(HTML first paint + delta updates over WebSocket) — the flagship path that
directly addresses enterprise Java teams. The core deliverable is the open
**standard layer** (spec, registry, conformance, validator). No scope-creep into
additional DSLs or renderers in this grant; embedded/native/mobile surfaces are
future phases.

### Out of scope this grant — post-grant roadmap

**Web evidence / adoption (tracked in the *Enterprise SRE · HTMX & BFF
replacement* milestone; done during the assessment window and as the hosted
demo + docs story grows):** #45 SEO-hardened SSR, #46 View Transitions, #47
publishable web client, #58 reconnect/heartbeat, #59 batch coalescing, #60 no-JS
degradation, #61 class-toggle, #62 observability, #63 web security, #66 docs
bundle (getting-started + migration + enterprise deployment), #67
production-shaped Spring/Quarkus reference apps.

**Later grants (the future scaling path):** #42 Visual Inspector and #72
governance/release/RFC + docs site; the embedded/LVGL renderer and the `no_std`
embedded target (from #39); desktop and mobile renderers. These are the
candidates for a subsequent, larger Restack project — the >€50k path whose
prerequisites (WCAG-compliant deliverables, satisfied security audit) WP3/WP5
explicitly build.

| WP | Deliverable | Issue | Effort (h) | Cost @€45/h |
|----|-------------|-------|-----------|-------------|
| WP1 | `spec/README.md` index + consolidated `0x0000–0xFFFF` property-key registry (derived, cross-checked against all language surfaces) | #48 | 30 | €1,350 |
| WP2 | Machine-readable ID schema + cross-language validator CLI (drift guard) + expanded golden conformance vectors (incl. the DOM renderer full-catalog pass and HTML golden vectors; validation only — codegen deferred) | #43, #73, #57 | 70 | €3,150 |
| WP3 | Coverage-guided fuzz harnesses (Rust/JS/Java), remaining decoder bounds hardening, one Miri pass on the C ABI, zero-allocation/no-leak profiling for the `no_std` surface — the hardening baseline for an independent security audit | #37, #38, #39 | 70 | €3,150 |
| WP4 | Benchmark suite: 16-byte vs JSON/Protobuf SDUI, and vs htmx + BFF/JSON SPA (payload size, decode latency, memory) + one CI chart | #41, #68 | 30 | €1,350 |
| WP5 | **WCAG/ARIA compliance on the SSR HTML renderer** (an acceptance criterion on all web deliverables, incl. form semantics + the formal audit/statement) + evidence pack (CI, the hosted demo, and screenshots pre-date the grant — delivered as application evidence) | #44, #65, #64, #70 | 45 | €2,025 |
| PM / reporting (10%) | NLnet reporting, MoU tracking | — | 15 | €675 |
| **Total** | | | **260 h** | **€11,700** |

## 6. Budget & rates

- **Cost-recovery**, rate **€45/h**, **260 h**, total ask **€11,700**.
- NLnet may adjust ineligible costs; the final amount is settled in the MoU.
- No other funding sources at present.
- **Security audit and accessibility scan** are requested as Restack practical
  support (not budgeted); WP3/WP5 deliver the baseline those services need.

## 7. Evidence layer (the demo is proof, not the product)

The web path already exists and now runs across **four renderers** from one
protocol:

| Surface | Path |
|---------|------|
| Spring Boot SSR + WebSocket | `lib/java/pathland-spring-boot-demo` |
| Quarkus SSR + WebSocket | `lib/java/pathland-quarkus-demo` |
| Native GTK4 desktop (Rust DSL) | `lib/rust/crates/pathland-render-gtk-demo` |
| Native GTK4 desktop (Java DSL, shared ring) | `lib/java/pathland-gtk-demo` |

Two DSLs (Rust `pathland-view`, Java `com.pathland.view`) drive the same
renderers over the same opcode stream, proving the polyglot claim. The hosted
Quarkus SSR demo, screenshots, and CI already exist as application evidence
(submitted with the proposal). WP5 adds the **WCAG/ARIA compliance pass** and the
evidence pack, so reviewers can click a working multi-renderer demo without the
core work being a "black box".

## 8. Prior-art comparison

Airbnb, Lyft and Spotify ship proprietary, per-app SDUI JSON schemas — closed,
not an open standard. Flutter, React Native and Compose Multiplatform are
frameworks that couple the UI model to one runtime. Native toolkits (GTK,
SwiftUI, WinUI) are per-platform and closed. Generic serialization (JSON,
Protobuf) is schema-driven data transport, not a UI instruction stream, and
re-sends full trees. Pathland is a **16-byte command stream** (tree mutations +
properties), open-spec, process/transport-agnostic, with native-element renderers
and zero-copy, diff-based emission — an open standard, not another framework.

The structural difference: a framework cannot escape its runtime. A protocol
lets any language drive any renderer, which is what enables, for example, a
**composition proxy** that combines several backend applications toward one
renderer — a materially better answer to microfrontends than today's
BFF/frontend-app stacks.

## 9. European dimension & ecosystem

- **Digital sovereignty**: an open, Apache-2.0 UI protocol reduces dependence on
  non-European UI stacks — a "made in Europe" contribution to the Open Internet
  Stack's *independent and cross-platform development framework* area. Open
  standards let all companies participate, rather than a few foreign vendors.
- **Open standards / interoperability**: spec + conformance vectors let any
  vendor implement renderers/DSLs; the validator and conformance suite become a
  reusable tool for any SDUI adopter.
- **Efficiency/frugality**: 16-byte opcodes + zero-delta emission cut bandwidth
  and embedded power vs JSON SDUI.
- **Engagement**: the first concrete adopter is **internal** (SOS International,
  where Lumen is in production). Near-term, the applicant aims to present the
  protocol at **WebDevCon** (if accepted) and is considering **FOSDEM** (free,
  volunteer-run) as an open-source venue. Embedded outreach is deferred until
  embedded enters the roadmap in a later phase.

## 10. Timeline

| Milestone | Date |
|-----------|------|
| Call opened | Sept 3, 2026 |
| Proposal submitted (early) | Sept 2026 |
| Final version before deadline | Nov 3, 2026, 12:00 CET |
| Assessment (3–5 months from deadline) | ~Feb–Apr 2027 |
| Decision / MoU | ~Feb–Apr 2027 |
| Project (6 months, 10 h/week) | ~Apr–Oct 2027 |
| Deliverables + final report | ~Oct 2027 |

*Restack's FAQ states the review takes three to five months from the call
deadline. The MoU allows a 12-month project window; the 6-month plan fits
comfortably.*

## 11. Deliverables & acceptance

All software FOSS (Apache-2.0); all open outcomes open access. Deliverables are
**WCAG-compliant** where they include web surfaces (this is an explicit
acceptance criterion, and the prerequisite for any later, larger Restack
project).

- [ ] `spec/README.md` + consolidated property registry (WP1)
- [ ] Schema + validator CLI + expanded conformance vectors, CI-gated (WP2)
- [ ] Fuzz harnesses (Rust/JS/Java) + decoder bounds + C ABI Miri pass (WP3)
- [ ] Benchmark suite + comparison chart (WP4)
- [ ] **WCAG/ARIA compliance on the SSR HTML renderer** (WP5)
- [ ] Pre-existing application evidence (verified at submission): CI green incl.
  demo modules, hosted demo URL, screenshots
- [ ] NLnet final report + MoU deliverables (PM)

## 12. Two-page narrative (copy-ready for the NLnet form)

**Proposal name:** Pathland — an implementable open UI-protocol standard.

**What it is.** Pathland is an open, Apache-2.0 protocol for declarative,
retained-mode UI that is language- and platform-agnostic. It describes *what* a
UI is — VStack, HStack, Text, spacing, padding, alignment — as a stream of fixed
**16-byte opcodes**, never *where*. Emission is diff-based and reactive: only the
nodes that changed emit, an unchanged tree emits zero opcodes, and renderers are
pure functions of the stream that map onto native elements. The engine is
transport- and process-agnostic, running from embedded to in-browser WASM to
distributed server-driven backends. The spec already exists (split across
`spec/OPCODE`, `PRIMITIVES`, `MODIFIERS`, `EVENTS`, `CONFORMANCE`, plus the `DSL`
authoring surface) and is enforced by golden byte vectors.

**What we will build.** This project turns the working proof-of-concept into an
**implementable open standard**: (1) a spec index and consolidated property-key
registry; (2) a machine-readable ID schema and cross-language validator so the
language surfaces cannot drift; (3) coverage-guided fuzzing and a Miri pass
proving the decoders and the C ABI are robust against malformed input — the
baseline for an independent security audit; (4) a benchmark suite quantifying
the 16-byte-vs-JSON/Protobuf and vs-htmx/BFF claims; and (5) **WCAG/ARIA
compliance on the HTML renderer** and the evidence pack (CI, a hosted SSR HTML
demo, and screenshots already exist as application evidence). A solo maintainer
delivers this in six months at 260 hours (cost-recovery, €11,700).

**Why it matters.** An open UI *protocol* — rather than another UI *framework* —
lets any vendor build renderers and DSLs against one spec, reducing lock-in to
non-European stacks and supporting the Open Internet Stack's cross-platform
development goal. The 16-byte, zero-delta wire format is an efficiency answer to
bloated JSON SDUI. Prior art (Airbnb/Lyft/Spotify SDUI, Flutter, React Native)
is closed or runtime-coupled; nothing provides an open, diff-based binary
instruction stream across embedded, browser, desktop, and server.

**European dimension.** Made in Denmark; Apache-2.0; the standard and conformance
suite become a reusable tool for any EU SDUI adopter, reducing dependence on
foreign UI stacks.

## 13. Human contribution & Generative AI

Pathland's design is human-directed. The applicant made the architectural
decisions — the fixed-size ring buffer and 16-byte slot, "declarative, not
positioned", renderer statelessness, signal-based diff emission, the portability
guards, the single opcode format for both directions, the spec, and the choice of
Java + Rust as the first DSLs. Generative AI was used as a tool under the
applicant's direction:

- **Gemini** — high-level brainstorming of design ideas and reasoning about
  presumed benchmarks for different technical approaches.
- **DeepSeek v4.0 Flash / Pro** — code assistance and grant-application
  assistance.

All AI output was reviewed, steered, and validated by the applicant; the spec
**locks in** the decisions the applicant accepted, and the applicant remains
accountable for correctness, clarity, and reproducibility. A prompt-provenance
log is maintained and will be submitted with the application (opencode session
exports for the code work; a best-effort log/summary for the Gemini
discussions). Repository transparency: see [`GENAI.md`](../GENAI.md).

## 14. Open items (to confirm before submission)

- [x] Fund identified: **Restack** (Open Internet Stack), deadline Nov 3, 2026.
- [x] Applicant type: **SME company** (Apaq ApS), Denmark — Horizon-Europe
  eligible.
- [x] GenAI disclosure: **Yes** (see §13); prompt log prepared via
  `opencode export` + Gemini history.
- [ ] Fill contact details (name, email, phone) in the form.
- [ ] Decide attachments: optional protocol diagram and/or two screenshots
  (attachments are not required; the repo is the primary evidence).
- [ ] Confirm WebDevCon submission status ("aiming/considering").
