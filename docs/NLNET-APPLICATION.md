# NLnet / Restack — Application Package (form-ready)

Assembled from the applicant's own words (Sept 2026). Paste each section into the
corresponding NLnet form field at https://nlnet.nl/propose/ (fund: **Restack**).
Character counts are the form's field limits; drafts below are within them.

---

## Fund
`Restack Fund`

## Proposal title
`Pathland — an implementable open UI-protocol standard`

## Project website(s) / repositories
```
https://github.com/michaelkrog/Pathland
https://apaq.dk
```

## Summary (max 1000 chars)

> After years of building web frontends, I kept hitting the same wall: HTML/CSS/JavaScript is a hack next to SwiftUI or Compose — it breaks easily, stutters, bloats bundles, and fights the DOM. I built a SwiftUI-shaped design system for Angular (Lumen, used internally at SOS International): it made teams more productive, but it still carried every weakness of rich clients. The insight came from a worker-thread experiment: separate a declarative UI model from the renderer behind an open binary protocol, so any language can drive any renderer on any device. Pathland is that protocol — fixed 16-byte opcodes in a ring buffer, signal-driven diff emission, renderers as pure functions mapping onto native elements. This project turns the working proof-of-concept (four renderers, two DSLs) into an implementable open standard: a spec registry, a cross-language validator, fuzzing and hardening, benchmarks, WCAG compliance, and a hosted demo.

## Proposed effort — amount
`11700 EUR`

## Budget breakdown (max 4000 chars)

> I request €11,700 at €45/h for 260 hours of part-time R&D (10 h/week over ~6 months). No expenses are claimed; the work is fully remote on my own infrastructure, and there are no subcontractors.
>
> Tasks (all open source, Apache-2.0):
> - **WP1 – Spec index + registry (30 h).** A `spec/README.md` index and a consolidated `0x0000–0xFFFF` property-key registry (component ids, property ids, event ids, listener bits, value types, enum codes), derived and cross-checked against the Rust/Java/TypeScript surfaces so the split spec reads as one implementable whole.
> - **WP2 – Schema + validator (70 h).** A machine-readable ID schema and a cross-language validator CLI that fails CI on any drift between the language surfaces; expanded golden conformance vectors. Validation first; codegen deferred.
> - **WP3 – Hardening (70 h).** Coverage-guided fuzzing of the three binary decoders (Rust, Java, TypeScript), remaining bounds hardening, and one Miri pass over the C ABI — the baseline that lets the Restack independent security audit pass.
> - **WP4 – Benchmark suite (30 h).** 16-byte opcodes vs JSON/Protobuf SDUI and vs htmx + BFF/JSON SPA: payload size, decode latency, memory; a CI chart and a `BENCHMARKS.md`.
> - **WP5 – Evidence (45 h).** WCAG/ARIA compliance on the SSR HTML renderer (an acceptance criterion on all web deliverables), hosted SSR HTML demo with screenshots, and the evidence pack.
> - **PM – Reporting (15 h).** NLnet reporting and MoU tracking.
>
> Rate: €45/h, cost-recovery for a solo maintainer. Total: 260 h / €11,700.

## Compare with other efforts (max 4000 chars)

> Flutter, React Native, and Compose Multiplatform couple the UI model to a single runtime. Per-vendor SDUI (Airbnb, Lyft, Spotify) is proprietary JSON, not an open standard. htmx re-renders markup server-side. Generic JSON/Protobuf is data transport, not a UI instruction stream, and re-sends full trees. None provides an open, language- and platform-agnostic binary protocol for retained-mode UI.
>
> The structural difference: popular UI frameworks never re-invent themselves and stay locked to running everything on the main thread. Pathland separates a declarative UI model from the renderer with a binary protocol and does signal-based change detection inside the model — outperforming existing web frameworks while allowing any language to drive any renderer on any device. Renderers are pure functions of a 16-byte opcode stream; an unchanged tree emits zero bytes.
>
> This also changes enterprise architecture: instead of a BFF, a separate frontend app, client-side tokens, and two languages, a Java backend owns the UI and streams only deltas. The protocol enables a composition proxy that combines several backend applications toward one renderer — a better answer to microfrontends than today's approaches.
>
> I built Lumen, a SwiftUI-shaped design system for Angular used internally at SOS International; Pathland is the generalisation of that experience into an open protocol. I have looked at adjacent efforts (Open UI, UI-specification groups); they focus on design-token/component vocabulary, not a transport-level retained-mode protocol.

## Technical challenges (max 4000 chars)

> The hardest problem is the benchmark: building genuinely heavy, high-performance SSR applications across multiple frameworks (Pathland vs htmx vs a BFF/JSON SPA) so the measurements are fair — comparable trees, identical fidelity, honest methodology. I will seek a methodology review as outside help.
>
> The second-hard problem is security validation of the protocol and the core framework code: proving the three binary decoders (Rust, Java, TypeScript) reject malformed input, and that the unsafe C ABI surface is sound. I plan coverage-guided fuzzing and a Miri pass, and will request the independent security audit Restack offers as practical support, treating the hardening work as its baseline.
>
> WCAG compliance on the SSR HTML renderer is also non-trivial (ARIA mapping, keyboard and focus management, form semantics) and will use an accessibility scan. Cross-language drift is a constant risk; the WP2 validator exists to make it a CI failure instead of a surprise.

## Ecosystem (max 2000 chars)

> Primary users are enterprise Java backend developers: teams that want professional, performant UIs without a separate frontend team, a separate frontend application, or an extra BFF layer. Secondary audiences are tooling authors — the DSL is defined by the specification, so new DSLs can be produced from it — and later, embedded developers.
>
> Dependencies are deliberately modest. The protocol core is a `no_std` + `alloc` Rust crate with no runtime dependencies. The Java DSL runs on every LTS from Java 17 and plugs into Spring Boot/Quarkus as host frameworks (not dependencies). The native desktop renderer uses GTK4 + libadwaita; cross-language interop uses JNA. Nothing is proprietary or single-vendor.
>
> The first concrete adopter is internal: SOS International, where Lumen (my earlier design system) is in production and where the Java + SSR path directly addresses the microfrontend/BFF pain. Near term, I aim to present the protocol at WebDevCon (if accepted) and am considering FOSDEM (free, volunteer-run) as an open-source venue. Embedded outreach is deferred until embedded enters the roadmap in a later phase.

## Background (max 2000 chars) — *applicant to personalise*

> I have 25 years of experience building software, mostly in financial, insurance, and planning domains. I built Previsto, a route-planning product for the service industry using metaheuristic optimisation for driving routes, from the ground up. At SOS International I designed and shipped Lumen, a SwiftUI-shaped design system for Angular, now in production and being expanded across the frontend teams as I move into the domain architect role.
>
> For Pathland, the design is mine: the fixed-size ring buffer and 16-byte opcode slot (chosen for zero-copy, cache-line-friendly transport across x86/ARM/RISC-V/ESP32), the "declarative, not positioned" model, renderer statelessness, signal-based diff emission, the single opcode format for both directions, the specification, and the choice of Java and Rust as the first DSLs. I write and steer the specification, which locks in every design decision. I am the sole maintainer and accountable for the correctness and reproducibility of the project.
>
> **[Add your personal details: name, years/roles you're comfortable stating, any publications/talks.]**

## Other funding (max 1000 chars)

> No other funding sources, past or present. After the grant, as the protocol and framework gain traction, further grants may be needed to keep momentum, and Apaq ApS may offer enterprise services and support to fund continued development of Pathland.

## AI disclosure

> **Did you use generative AI in writing this proposal?** Yes.
>
> **Which model(s) and what for:** Gemini — high-level brainstorming of design ideas and reasoning about presumed benchmarks for different technical approaches. DeepSeek v4.0 Flash and Pro — code assistance and grant-application assistance.
>
> **How it was used:** drafting and editing this proposal and much of the code. Design decisions were mine; all AI output was reviewed, steered, and validated by me, and the specification locks in the decisions I accepted. I remain accountable for correctness, clarity, and reproducibility.
>
> **Prompt provenance log:** attached / available on request — opencode session exports (JSON) for the code and grant-drafting sessions, plus a best-effort summary of the Gemini design discussions (see `GENAI.md` in the repository for the project-level transparency statement).

## Contact information — *placeholders*
```
Name:        <your name>
Email:       <your email>
Phone:       <+country code and number>
Type:        SME company
Organisation: Apaq ApS
Country:     Denmark
```

## Attachments — *not required; optional*
- [ ] Protocol diagram (one page) — not ready
- [ ] Screenshots (SSR demo + GTK window) — not ready

---

## Submission checklist

1. **Fund:** Restack Fund (not "NGI"; the successor is the Open Internet Stack).
2. **Deadline:** Nov 3, 2026, 12:00 CET. The **last complete version before the deadline** is what is scored — submit early, refine later.
3. **Assessment:** 30% technical excellence / 40% relevance-impact-strategic / 30% value-for-money; pass > 5.0/7 to reach stage 2. Stage 2 may ask clarifying questions and a revised proposal — keep the repo and evidence current.
4. **Scaling path:** a first proposal may be up to €50k; anything larger later requires a successfully-concluded prior project whose software was WCAG-compliant and passed the independent security audit. This proposal's WP3/WP5 explicitly build those prerequisites.
5. **GenAI:** the form forces a Yes/No. Answer **Yes** (see AI disclosure above) and keep the prompt log.
6. **European dimension:** Denmark is Horizon-Europe eligible; the proposal (§9) states the sovereignty/open-standards angle.
7. **Practice run:** submit a rough version early, then re-submit the final text before Nov 3.

## Prompt-provenance log — how to extract

- **DeepSeek/opencode sessions:** `opencode session` to list sessions; `opencode export <sessionID> > log.json` for the full prompts + outputs. The Pathland dev sessions are filtered by the project directory/title.
- **Gemini sessions:** Google Takeout → "Gemini Apps" activity, or a best-effort written summary of the design/benchmark topics discussed (the policy accepts equivalent alternative logging).