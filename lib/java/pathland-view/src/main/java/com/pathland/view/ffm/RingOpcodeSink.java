package com.pathland.view.ffm;

import com.pathland.view.Categories;
import com.pathland.view.Color;
import com.pathland.view.Commands;
import com.pathland.view.ValueTypes;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.OpcodeSink;
import com.pathland.view.emit.ValueEncoder;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;

/**
 * An {@link OpcodeSink} that writes opcodes zero-copy into the Rust SPSC ring via
 * JNA. Strings are allocated into the shared bump arena (absolute offsets). This is the
 * desktop/embedded path; the frame path uses {@code FrameOpcodeSink} instead.
 */
public final class RingOpcodeSink implements OpcodeSink, AutoCloseable {

    private final PathlandCore core;
    private final Pointer handle;

    public RingOpcodeSink() {
        this(PathlandCore.instance());
    }

    public RingOpcodeSink(PathlandCore core) {
        this.core = core;
        this.handle = core.create();
    }

    /** The opaque native handle (for direct ring access). */
    public Pointer handle() {
        return handle;
    }

    @Override
    public void beginFrame() {
        core.beginFrame(handle);
    }

    @Override
    public void endFrame() {
        core.endFrame(handle);
    }

    @Override
    public void createNode(int id, int component) {
        push(Categories.TREE, Commands.Tree.CREATE_NODE, 0, id, component, 0);
    }

    @Override
    public void deleteNode(int id) {
        push(Categories.TREE, Commands.Tree.DELETE_NODE, 0, id, 0, 0);
    }

    @Override
    public void insertChild(int parent, int child, int index) {
        push(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, parent, child, index);
    }

    @Override
    public void removeChild(int parent, int child) {
        push(Categories.TREE, Commands.Tree.REMOVE_CHILD, 0, parent, child, 0);
    }

    @Override
    public void moveChild(int parent, int child, int newIndex) {
        push(Categories.TREE, Commands.Tree.MOVE_CHILD, 0, parent, child, newIndex);
    }

    @Override
    public void setText(int nodeId, String text) {
        int ref = core.arenaAlloc(handle, text.getBytes(StandardCharsets.UTF_8));
        push(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, nodeId, ref, 0);
    }

    @Override
    public void setProperty(int nodeId, int property, int valueType, Object value) {
        int b = (valueType << 16) | (property & 0xFFFF);
        if (valueType == ValueTypes.STRING) {
            int ref = core.arenaAlloc(handle, ((String) value).getBytes(StandardCharsets.UTF_8));
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref);
        } else if (valueType == ValueTypes.DESIGN_TOKEN) {
            int ref = core.arenaAlloc(handle, ((Color) value).token().getBytes(StandardCharsets.UTF_8));
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref);
        } else if (valueType == ValueTypes.LIST) {
            int ref = core.arenaAlloc(handle, encodeList((float[]) value));
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref);
        } else {
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b,
                    ValueEncoder.encodeBits(valueType, value));
        }
    }

    /** Encode a length-prefixed f32 array `[u32 count][f32 × count]` (spec/OPCODE.md). */
    private static byte[] encodeList(float[] elements) {
        byte[] out = new byte[4 + 4 * elements.length];
        int value = elements.length;
        out[0] = (byte) (value & 0xFF);
        out[1] = (byte) ((value >>> 8) & 0xFF);
        out[2] = (byte) ((value >>> 16) & 0xFF);
        out[3] = (byte) ((value >>> 24) & 0xFF);
        for (int i = 0; i < elements.length; i++) {
            int bits = Float.floatToIntBits(elements[i]);
            int o = 4 + 4 * i;
            out[o] = (byte) (bits & 0xFF);
            out[o + 1] = (byte) ((bits >>> 8) & 0xFF);
            out[o + 2] = (byte) ((bits >>> 16) & 0xFF);
            out[o + 3] = (byte) ((bits >>> 24) & 0xFF);
        }
        return out;
    }

    @Override
    public void setDate(int nodeId, int days, int millisOfDay) {
        push(Categories.PARAMETER, Commands.Parameter.SET_DATE, 0, nodeId, days, millisOfDay);
    }

    @Override
    public void setDesignToken(String path, int valueType, Object value) {
        int pathRef = core.arenaAlloc(handle, path.getBytes(StandardCharsets.UTF_8));
        int c;
        if (valueType == ValueTypes.STRING) {
            c = core.arenaAlloc(handle, ((String) value).getBytes(StandardCharsets.UTF_8));
        } else {
            c = ValueEncoder.encodeBits(valueType, value);
        }
        push(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, pathRef, valueType, c);
    }

    private void push(int category, int command, int flags, int a, int b, int c) {
        core.push(handle, new Opcode(category, command, flags, a, b, c).toBytes());
    }

    @Override
    public void close() {
        core.destroy(handle);
    }
}