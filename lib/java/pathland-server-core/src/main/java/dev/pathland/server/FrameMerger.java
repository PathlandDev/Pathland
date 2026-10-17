package dev.pathland.server;

import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges self-contained frames into one, rebasing each appended frame's string
 * offsets ({@link FrameRebase}) onto the growing string section.
 *
 * <p>Used to splice a proxy's origin subtree into an SSR frame (spec/PROXY.md §5):
 * the proxying server renders **one** document from its own mount frame plus each
 * proxy's remapped origin snapshot, so the renderer emits the origin's fit band
 * rules and token overrides into the same document head — no renderer change.
 */
final class FrameMerger {

    private FrameMerger() {}

    /** Append {@code extras} onto {@code base}, rebasing their string offsets. */
    static ProtocolFrame merge(ProtocolFrame base, List<ProtocolFrame> extras) {
        if (extras == null || extras.isEmpty()) {
            return base;
        }
        List<Opcode> opcodes = null;
        ByteArrayOutputStream strings = null;
        for (ProtocolFrame extra : extras) {
            if (extra == null || extra.isEmpty()) {
                continue;
            }
            if (opcodes == null) {
                opcodes = new ArrayList<>(base.opcodes());
                strings = new ByteArrayOutputStream();
                strings.writeBytes(base.strings());
            }
            int offset = strings.size();
            for (Opcode op : extra.opcodes()) {
                opcodes.add(FrameRebase.rebase(op, offset));
            }
            strings.writeBytes(extra.strings());
        }
        return opcodes == null ? base : new ProtocolFrame(List.copyOf(opcodes), strings.toByteArray());
    }
}
