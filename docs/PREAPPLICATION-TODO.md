# Restack application — pre-submission checklist

**Deadline:** Nov 3, 2026, 12:00 CET · **Fund:** NLnet **Restack** (Open Internet
Stack) · Apply at https://nlnet.nl/propose/

Everything below is **form-work or evidence of the existing proof-of-concept** —
none of it is funded grant work. Three WP5 items (CI, hosted demo, screenshots) are
deliberately front-loaded as application evidence; see the guardrail section for
what remains *leave-alone* until the award.

## 1. Application form readiness

- [ ] Fill `docs/NLNET-APPLICATION.md` **Background** personal-details placeholder and
  **Contact** placeholders (name / email / phone).
- [ ] Fix the stale repo URL in `docs/NLNET-APPLICATION.md`
  (`github.com/michaelkrog/Pathland` → `github.com/PathlandDev/Pathland`).
- [ ] Re-verify Restack eligibility against the
  [Guide for Applicants](https://nlnet.nl/restack/guideforapplicants/) and
  [Eligibility](https://nlnet.nl/restack/eligibility/) pages — the call is open, so
  confirm now rather than in October.
- [ ] Confirm **WebDevCon** submission status (GRANT.md §14 open item).
- [ ] Decide attachments: protocol diagram + screenshots (optional; the repo is the
  primary evidence).
- [ ] **Practice run:** submit a rough version early, then refine the final text
  before Nov 3.

## 2. Pre-application work (committed)

Front-loaded evidence of the existing POC — not new grant work:

- [ ] **Complete CI incl. demo modules** (#76) — build + test
  `pathland-quarkus-demo` and `pathland-spring-boot-demo` in `ci.yml` (reuse the
  Rust HTML-dylib step for the jar embed); CI green across rust + java.
- [ ] **Host the Quarkus SSR demo** (#71) — `lib/java/pathland-quarkus-demo/Dockerfile`
  (multi-stage: Rust dylib → Java reactor → Quarkus jar → JRE 17) + `docs/DEPLOY.md`;
  deploy to a public URL.
- [ ] **Screenshots** (#69) — SSR kitchensink hero shot + GTK window (from the
  existing demos).
- [ ] Demo video (#96) — record the ~90s clip: SSR first paint + live delta, the
  shared `SplitNavDemo` under both web and GTK4, optional 16-byte opcode overlay;
  host unlisted (YouTube/Vimeo) for a URL in the form.

## 3. Guardrail — do NOT do before applying (funded WP work)

These are the funded deliverables of the Restack proposal (`docs/GRANT.md` §5,
milestone `Restack grant · WP1–WP5`). Doing them early is funded work for free and
weakens the ask:

- WP1 spec index + registry — #48
- WP2 schema + validator + conformance vectors — #43, #73, #57
- WP3 fuzzing / bounds / Miri / no-alloc profiling — #37, #38, #39
- WP4 benchmark suite + CI charts — #41, #68
- WP5 remaining: WCAG/ARIA compliance, form semantics, a11y audit/statement, and
  the evidence pack — #65, #70, #64, #44

CI (#76), the hosted demo (#71), and screenshots (#69) were WP5 items that have
been front-loaded as application evidence — they are **not** part of the funded
ask. The demo *video* (#96) is deliberately **not** in WP5 at all.