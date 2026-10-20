package dev.pathland.server;

import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.ValueTypes;
import dev.pathland.view.emit.Opcode;

/**
 * Rebases an opcode's relative string-section offsets onto a merged string
 * section (spec/OPCODE.md — a self-contained frame references its own strings by
 * relative offset). Shared by {@link DeltaBatcher} (coalescing a session's
 * per-signal frames) and {@link FrameMerger} (splicing a proxy's origin subtree
 * into an SSR frame).
 *
 * <p>{@code SET_TEXT} ({@code B}), {@code STRING}/{@code DESIGN_TOKEN}/{@code LIST}
 * -typed {@code SET_PROPERTY} ({@code C}), and {@code SET_DESIGN_TOKEN}
 * ({@code A}, plus {@code C} for a STRING-valued token) reference the string
 * section by a relative offset.
 */
final class FrameRebase {

    private FrameRebase() {}

    static Opcode rebase(Opcode op, int base) {
        int a = op.a();
        int b = op.b();
        int c = op.c();
        if (op.category() == Categories.PARAMETER) {
            int command = op.command();
            if (command == Commands.Parameter.SET_TEXT) {
                b += base;
            } else if (command == Commands.Parameter.SET_PROPERTY) {
                int valueType = b >>> 16;
                if (valueType == ValueTypes.STRING || valueType == ValueTypes.DESIGN_TOKEN
                        || valueType == ValueTypes.LIST) {
                    c += base;
                }
            } else if (command == Commands.Parameter.SET_DESIGN_TOKEN) {
                a += base;
                if ((b & 0xFF) == ValueTypes.STRING) {
                    c += base;
                }
            }
        }
        return new Opcode(op.category(), op.command(), op.flags(), a, b, c);
    }
}
