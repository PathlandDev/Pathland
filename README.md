# Pathland

> An open UI protocol — declarative UI, written in your backend. No JavaScript.

**Status: proof of concept.** It works end to end and the tests are green, but the
wire format and APIs may change before 1.0.

[![CI](https://github.com/michaelkrog/Pathland/actions/workflows/ci.yml/badge.svg)](https://github.com/michaelkrog/Pathland/actions/workflows/ci.yml)

## The pitch

You write the **whole UI in your backend language** — Java, C#, Rust, anything.
Pathland renders the first page as server-side HTML, so it appears instantly, then
keeps it alive with tiny binary updates over a WebSocket. The result is a smooth,
reactive UI — delivered by the backend team, without npm, bundlers, an extra build
pipeline, or a separate frontend.

## Pathland is a protocol

The server-rendered path above is the **flagship way to use Pathland today** — not
the whole of it. Underneath, Pathland is a **binary UI protocol**: your view
declaration compiles to tiny fixed-size opcodes that any renderer can apply to
native elements.

That makes it **language- and renderer-agnostic** — a view written in Java, C#, or
Rust produces the same opcode stream, and any renderer (browser DOM, native GTK,
and on-device ones to come) can apply it. The protocol already drives a **native
GTK4 desktop renderer**, and the goal is for your UI code to also run on the
device itself — compiled to WASM in the browser, on a dedicated core of an embedded
chip, or on native mobile and desktop. Because your views are protocol-defined,
they can move to those surfaces later without a rewrite.

Start with the SSR path below and keep it simple — the rest of the protocol is
there when you need it (see [Where it's headed](#where-its-headed)).

## What it removes

Today's server team pays for a BFF, a client-side state model, a SPA framework, and
a JS build toolchain — all to render UI the server already owns. Pathland removes
that layer: the backend *is* the application. There is no frontend contract to
maintain, no client state model, and no JavaScript to write.

## How it works

```
You declare the UI in your backend
        │
        ▼
The server renders it (HTML) and owns it
        │
        ▼
Only the things that change travel to the client
        │
        ▼
The client applies them to native elements (60FPS)
```

- **You declare the UI** with a SwiftUI-shaped DSL — `VStack`, `Text`, `Button`,
  styling — right next to your business logic.
- **The server renders and owns it.** The first page is plain HTML (instant paint,
  SEO-friendly); the server keeps the authoritative copy of the UI.
- **Only changes travel.** A click, a signal update, a navigation — the server sends
  a few tiny binary bytes describing exactly what changed. An unchanged screen
  sends nothing. The client applies them to real native elements (browser DOM, GTK
  widgets), so the platform does the layout, animation, and accessibility.

## The whole app

Add one dependency and one bean, and you have a working app.

**Spring Boot**

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }

    @Bean
    PathlandApp pathlandApp() { return () -> new MyHomeView(); }
}
```

**Quarkus**

```java
@ApplicationScoped
public class MyApp implements PathlandApp {
    public View newRoot() { return new MyHomeView(); }
}
```

`MyHomeView` is just Java views — the same code runs on both:

```java
public final class MyHomeView implements View {

    State<Integer> count = new State<>(0);   // persisted per session, automatically

    @Override
    public View body() {
        return VStack.of(
                Text.of(Signals.computed(() -> "Clicked " + count.get() + " times")),
                Button.of("Click me", () -> count.update(v -> v + 1))
        ).modifier(Padding.of(24));
    }
}
```

That's it — SSR at any path, live updates over `/ws`, per-session state.
*(Imports omitted: `com.pathland.server.PathlandApp`, `com.pathland.view.*`.)*

## Why it's smooth

Pathland is built for smooth 60FPS UIs:

- **Only changes are sent.** A screen that isn't changing transmits zero bytes.
  Updates are tiny binary deltas, not re-rendered pages or JSON trees.
- **Native elements, not a canvas.** The browser or OS lays out and animates real
  elements — you get platform layout, text rendering, and accessibility for free.
- **A thin client.** The client is a small static file that hydrates the server HTML
  and applies updates in place. No framework, no virtual DOM, no re-render cost.

## The part you don't write

The web client is a single prebuilt JavaScript file you copy into your static
resources. It's shipped, not maintained — all the real logic lives in your backend.
(There's also a native GTK renderer for desktop.)

## Where it runs today

- **Java** — Spring Boot and Quarkus, with SSR + WebSocket demos (the flagship, getting-started path), and the same shared demo views under the native GTK4 desktop renderer (`pathland-gtk-demo`).
- **Rust** — a native GTK4 desktop renderer (same protocol, a second surface).

## Where it's headed

The SSR path above is the on-ramp — the protocol isn't limited to it.

Write the app logic once, run it on embedded, mobile, desktop, and browser. Pathland
is an open protocol, so the same code that drives the server-rendered UI today can
also run on the device itself:

- **In the browser**, the app logic compiles to WASM, writes to a shared buffer, and the
  main thread is left to rendering alone.
- **On embedded devices**, the logic runs on one core while another core renders, with
  the ring buffer carrying the protocol between them.
- **On mobile and desktop**, the same logic drives native renderers.

Wherever it runs, only tiny binary updates travel across the ring buffer and are applied
in place — nothing is re-rendered or serialized, which is what keeps the UI stutter-free,
even on small devices.

## Try it

```bash
# one-time: build the Rust HTML renderer (embedded in the jar), then the Java reactor
cd lib/rust && cargo build -p pathland-render-html
cd lib/java && mvn install

# Spring Boot demo
cd lib/java/pathland-spring-boot-demo
mvn -q package && java -jar target/pathland-spring-boot-demo-0.1.0.jar
# → http://localhost:8080

# Quarkus demo (dev mode with hot reload)
cd lib/java/pathland-quarkus-demo
mvn quarkus:dev
# → http://localhost:8080

# Same protocol, native GTK desktop renderer (no browser)
cd lib/rust && cargo run -p pathland-render-gtk-demo

# Same protocol via Java: the shared demo views (SplitNavDemo) under the GTK renderer
cd lib/rust && cargo build -p pathland-core-capi -p pathland-render-gtk
cd lib/java/pathland-gtk-demo
MAVEN_OPTS="-XstartOnFirstThread" mvn -q compile exec:java \
  -Dpathland.core.lib=$PWD/../../lib/rust/target/debug/libpathland_core.dylib \
  -Dpathland.gtk.lib=$PWD/../../lib/rust/target/debug/libpathland_gtk.dylib
# (macOS needs -XstartOnFirstThread; Linux can drop it)
```

## The technical details

This README is the *what*. The *how* — the wire protocol, the DSL contract, the
conformance vectors, and the implementation notes — lives in [`spec/`](./spec/):

- [OPCODE.md](./spec/OPCODE.md) — the binary protocol
- [DSL.md](./spec/DSL.md) — the authoring surface (what you write)
- [PRIMITIVES.md](./spec/PRIMITIVES.md), [MODIFIERS.md](./spec/MODIFIERS.md), [EVENTS.md](./spec/EVENTS.md)
- [CONFORMANCE.md](./spec/CONFORMANCE.md) — golden byte vectors

## Status & license

Proof of concept. See [CONTRIBUTING.md](./CONTRIBUTING.md),
[CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md), and [SECURITY.md](./SECURITY.md).
Licensed under the [Apache License 2.0](LICENSE).