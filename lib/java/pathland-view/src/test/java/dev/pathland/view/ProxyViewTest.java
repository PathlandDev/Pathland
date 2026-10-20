package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.emit.RenderResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static dev.pathland.view.Modifiers.*;

/**
 * The {@code ProxyView} authoring surface (spec/PROXY.md, spec/DSL.md §4.2): it
 * mounts a {@code PROXY_VIEW} node with no authored children and surfaces its
 * origin config to the host through {@code RenderResult.proxyViews()}.
 */
class ProxyViewTest {

    @Test
    void mountsAProxyViewAndSurfacesItsConfig() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        View root = VStack.children(
                Text.make(t -> t.text("local")),
                ProxyView.make(p -> p.endpoint("http://b.example").route("/music").credential("svc")));

        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        ProtocolFrame frame = sink.frame();

        Opcode proxyNode = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> (o.b() & 0xffff) == Components.PROXY_VIEW)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no PROXY_VIEW node emitted"));

        assertEquals(1, result.proxyViews().size(), "the proxy config is surfaced to the host");
        ProxyConfig config = result.proxyViews().get(proxyNode.a());
        assertEquals("http://b.example", config.endpoint());
        assertEquals("/music", config.route());
        assertEquals("svc", config.credential());

        // The proxy authors no children: the origin subtree is spliced in at runtime.
        assertFalse(frame.opcodes().stream()
                        .anyMatch(o -> o.category() == Categories.TREE
                                && o.command() == Commands.Tree.INSERT_CHILD
                                && o.a() == proxyNode.a()),
                "the proxy node has no locally-authored children");
    }

    @Test
    void proxyViewTakesSizeModifiers() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        emitter.mount(
                ProxyView.make(p -> p.endpoint("http://b.example"))
                        .modifiers(frame(f -> f.height(320f))),
                Environment.DEFAULT);

        List<Opcode> ops = sink.frame().opcodes();
        int proxyId = ops.stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> (o.b() & 0xffff) == Components.PROXY_VIEW)
                .mapToInt(Opcode::a)
                .findFirst()
                .orElseThrow();
        assertTrue(ops.stream().anyMatch(o -> o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_PROPERTY
                        && o.a() == proxyId && (o.b() & 0xffff) == Properties.HEIGHT),
                "the proxy takes a size modifier like any view");
    }
}
