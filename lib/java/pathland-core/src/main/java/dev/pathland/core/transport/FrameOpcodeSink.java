package dev.pathland.core.transport;

import dev.pathland.core.protocol.Categories;
import dev.pathland.core.protocol.Commands;
import dev.pathland.core.protocol.Opcode;
import dev.pathland.core.protocol.TokenPath;
import dev.pathland.core.protocol.ValueEncoder;
import dev.pathland.core.protocol.ValueTypes;
import dev.pathland.core.text.AttributedString;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** An {@link OpcodeSink} that accumulates a self-contained {@link ProtocolFrame}. */
public class FrameOpcodeSink implements OpcodeSink {
    private final List<Opcode> opcodes = new ArrayList<>();
    private final ByteArrayOutputStream strings = new ByteArrayOutputStream();
    private ProtocolFrame frame = ProtocolFrame.EMPTY;
    private int framesProduced = 0;

    public ProtocolFrame frame() {
        return frame;
    }

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
        frame = new ProtocolFrame(new ArrayList<>(opcodes), strings.toByteArray());
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
    public void setAttributedText(int nodeId, AttributedString value) {
        int offset = strings.size();
        byte[] payload = value.encode();
        writeIntLE(payload.length);
        strings.writeBytes(payload);
        push(Categories.PARAMETER, Commands.Parameter.SET_ATTRIBUTED_TEXT, 0, nodeId, offset, 0);
    }

    @Override
    public void setProperty(int nodeId, int property, int valueType, Object value) {
        int b = (valueType << 16) | (property & 0xFFFF);
        switch (valueType) {
            case ValueTypes.STRING: {
                int offset = strings.size();
                writeString((String) value);
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset);
                break;
            }
            case ValueTypes.DESIGN_TOKEN: {
                int offset = strings.size();
                String path = ((TokenPath) value).tokenPath();
                writeString(path == null ? "" : path);
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset);
                break;
            }
            case ValueTypes.LIST: {
                int offset = strings.size();
                float[] elements = (float[]) value;
                writeIntLE(elements.length);
                for (float element : elements) {
                    int bits = Float.floatToRawIntBits(element);
                    strings.write(bits & 0xFF);
                    strings.write((bits >>> 8) & 0xFF);
                    strings.write((bits >>> 16) & 0xFF);
                    strings.write((bits >>> 24) & 0xFF);
                }
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset);
                break;
            }
            default:
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b,
                        ValueEncoder.encodeBits(valueType, value));
                break;
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
            int offset = strings.size();
            writeString((String) value);
            c = offset;
        } else {
            c = ValueEncoder.encodeBits(valueType, value);
        }
        push(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, pathOffset, valueType, c);
    }

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

    @Override
    public void resetTheme() {
        push(Categories.META, Commands.Meta.RESET_THEME, 0, 0, 0, 0);
    }

    @Override
    public void setColorScheme(int mode) {
        push(Categories.META, Commands.Meta.SET_COLOR_SCHEME, 0, mode, 0, 0);
    }

    @Override
    public void setWindowMetadata(int field, String value) {
        int offset = strings.size();
        writeString(value);
        push(Categories.META, Commands.Meta.SET_WINDOW_METADATA, 0, field, offset, 0);
    }
}
