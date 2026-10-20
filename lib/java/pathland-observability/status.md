# pathland-observability — implementation status

**Last updated:** October 3, 2026

Observability adapters for the framework-agnostic `pathland-server-core`
telemetry seam. The core emits lifecycle/throughput events through
`PathlandTelemetry` (default `NOOP`); this module turns them into metrics and
spans. Protocol/contract context: `spec/OPCODE.md`; the seam itself is in
`pathland-server-core`.

## Implemented

- **`MicrometerTelemetry`** — a `PathlandTelemetry` over any Micrometer
  `MeterRegistry` (Prometheus, OTLP, …). Metric catalog:
  - `pathland.sessions.active` — gauge (pull-based, bound by the host)
  - `pathland.sessions.opened` / `pathland.sessions.closed` — counters
  - `pathland.events.received` — counter (decoded inbound events)
  - `pathland.batches.sent` / `pathland.frames.sent` / `pathland.bytes.sent` — counters
  - `pathland.batch.size` — distribution summary (opcodes per batch)
  - `pathland.batch.flush` — timer (merge + encode)
  - `pathland.ssr.render` — timer (SSR render round-trip)
  - `pathland.resync.requested` — counter
  - `pathland.ws.failures` — counter
  - Every meter is tagged by `mount` (bounded). Registrations are idempotent, so
    a builder per event is cheap and cannot leak meters.
- **`TracingTelemetry`** — an OpenTelemetry decorator over another
  `PathlandTelemetry`: emits a span per frame send (`pathland.frame.send`), per
  inbound event batch (`pathland.event.batch`), and per SSR render
  (`pathland.ssr.render`), with the wrapped telemetry still receiving every
  event. Spans are back-dated by the measured duration so they render with the
  real latency. Opt-in — the starters build it only when a `Tracer`/`OpenTelemetry`
  bean is available.

## Not implemented / gaps

- No client-side (browser) telemetry — server-side only.
- `ws.failures` does not tag the failure reason (unbounded cardinality).

## Verified by

`mvn test -pl pathland-observability` — `MicrometerTelemetryTest` (the full
metric catalog + per-mount tagging) and `TracingTelemetryTest` (span names via an
in-memory OTel exporter, plus delegation). The starters' tests assert the wiring
end to end (`/q/health` + `/q/metrics`, `/actuator/health` + `pathland.*` meters).
