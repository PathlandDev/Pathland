package com.pathland.view.emit;

/**
 * The emission target for opcodes. Two implementations exist: a
 * {@link FrameOpcodeSink} that builds a self-contained {@link Frame} (SSR/WebSocket),
 * and a ring-backed sink ({@code com.pathland.view.native.RingOpcodeSink}) that writes
 * opcodes zero-copy into the Rust SPSC ring via FFM.
 */
public interface OpcodeSink {

    void beginFrame();

    void endFrame();

    void createNode(int id, int component);

    void deleteNode(int id);

    void insertChild(int parent, int child, int index);

    void removeChild(int parent, int child);

    void moveChild(int parent, int child, int newIndex);

    /**
     * Emit {@code SET_TEXT} for a node. Implementations write the string into their
     * string section (relative offset) or the shared-memory arena (absolute offset)
     * and reference it from the opcode.
     */
    void setText(int nodeId, String text);

    /**
     * Emit {@code SET_PROPERTY}. {@code value} is the typed property value
     * ({@link Float} f32, {@link Integer} raw u32/u8, {@link com.pathland.view.Color},
     * or {@link String} when {@code valueType} is {@code STRING}).
     */
    void setProperty(int nodeId, int property, int valueType, Object value);

    /**
     * Emit {@code SET_DATE} for a {@code DATE_PICKER} node: {@code B}=days since epoch
     * (I32, pre-1970 negative), {@code C}=millis of day (U32, 0..86,400,000).
     */
    void setDate(int nodeId, int days, int millisOfDay);

    /**
     * Emit a global design-token override {@code PARAMETER::SET_DESIGN_TOKEN}
     * (spec/TOKENS.md): {@code A}=arena offset of the token path,
     * {@code B}=valueType, {@code C}=value (for {@code STRING}, the arena offset
     * of the value string). {@code value} is typed like
     * {@link #setProperty}: {@link Float} f32, {@link Integer} raw u32/u8,
     * {@link com.pathland.view.Color} (packed argb), or {@link String}.
     */
    void setDesignToken(String path, int valueType, Object value);
}