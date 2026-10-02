package com.pathland.view.emit;

import com.pathland.view.Categories;
import com.pathland.view.Color;
import com.pathland.view.Commands;
import com.pathland.view.ValueTypes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link OpcodeSink} that accumulates a self-contained {@link Frame} (opcodes +
 * string section) entirely in the JVM. This is the SSR / WebSocket path: the frame is
 * rendered to HTML or encoded to the network batch wire format.
 */
public class FrameOpcodeSink implements OpcodeSink {

    private final List<Opcode> opcodes = new ArrayList<>();
    private final ByteArrayOutputStream strings = new ByteArrayOutputStream();
    private Frame frame = Frame.EMPTY;
    private int framesProduced;

    /** The frame built by the last {@code beginFrame/endFrame} pair. */
    public Frame frame() {
        return frame;
    }

    /** Number of completed frames so far (unchanged signals do not produce a frame). */
    public int framesProduced() {
        return framesProduced;
    }

    public boolean hasChanges() {
        return !opcodes.isEmpty();
    }

    @Override
    public void beginFrame() {
        opcodes.clear();
        strings.reset();
    }

    @Override
    public void endFrame() {
        frame = new Frame(List.copyOf(opcodes), strings.toByteArray());
        framesProduced++;
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
        int offset = strings.size();
        writeString(text);
        push(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, nodeId, offset, 0);
    }

    @Override
    public void setProperty(int nodeId, int property, int valueType, Object value) {
        int b = (valueType << 16) | (property & 0xFFFF);
        if (valueType == ValueTypes.STRING) {
            int offset = strings.size();
            writeString((String) value);
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset);
        } else if (valueType == ValueTypes.DESIGN_TOKEN) {
            // C = arena offset of the token path (spec/TOKENS.md).
            int offset = strings.size();
            writeString(((Color) value).token());
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset);
        } else {
            push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ValueEncoder.encodeBits(valueType, value));
        }
    }

    @Override
    public void setDate(int nodeId, int days, int millisOfDay) {
        push(Categories.PARAMETER, Commands.Parameter.SET_DATE, 0, nodeId, days, millisOfDay);
    }

    @Override
    public void setDesignToken(String path, int valueType, Object value) {
        int pathOffset = strings.size();
        writeString(path);
        int c;
        if (valueType == ValueTypes.STRING) {
            c = strings.size();
            writeString((String) value);
        } else {
            c = ValueEncoder.encodeBits(valueType, value);
        }
        push(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, pathOffset, valueType, c);
    }

    /** Append a length-prefixed string to the string section, returning its offset. */
    public int writeString(String text) {
        int offset = strings.size();
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        writeIntLE(utf8.length);
        strings.writeBytes(utf8);
        return offset;
    }

    private void push(int category, int command, int flags, int a, int b, int c) {
        opcodes.add(new Opcode(category, command, flags, a, b, c));
    }

    private void writeIntLE(int value) {
        strings.write(value & 0xFF);
        strings.write((value >>> 8) & 0xFF);
        strings.write((value >>> 16) & 0xFF);
        strings.write((value >>> 24) & 0xFF);
    }
}