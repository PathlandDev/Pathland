package com.pathland.view;

import com.pathland.view.emit.OpcodeSink;

import java.util.ArrayList;
import java.util.List;

/**
 * A batch of **global, renderer-wide design-token overrides** for a single
 * scheme ({@code PARAMETER::SET_DESIGN_TOKEN}, spec/TOKENS.md). Every builder
 * defines exactly **one value** — combine a light and a dark {@link Theme} via
 * {@link AdaptiveTheme} for adaptive theming (the dark one is emitted with the
 * {@code dark.} prefix).
 *
 * <p>Builder methods are self-returning for chaining:
 * <pre>{@code
 * Theme light = new Theme()
 *     .color("color.primary", 0xFF2563EB)
 *     .f32("space.base", 4.0f)
 *     .string("font.body.family", "Inter");
 * Theme dark = new Theme().color("color.primary", 0xFF60A5FA);
 * new AdaptiveTheme(light, dark).emit(sink);
 * }</pre>
 */
public final class Theme implements ThemeData {

    /** A single token override (path, wire value type, typed value). */
    record TokenOverride(String path, int valueType, Object value) {}

    private final List<TokenOverride> overrides = new ArrayList<>();

    /** Override a token with a packed sRGB {@code 0xAARRGGBB} color. */
    public Theme color(String path, int argb) {
        return push(path, ValueTypes.COLOR, Color.of(argb));
    }

    /** Override a token with an F32 value. */
    public Theme f32(String path, float value) {
        return push(path, ValueTypes.F32, value);
    }

    /** Override a token with a U32 value. */
    public Theme u32(String path, int value) {
        return push(path, ValueTypes.U32, value);
    }

    /** Override a token with a U8 value. */
    public Theme u8(String path, int value) {
        return push(path, ValueTypes.U8, value);
    }

    /** Override a token with a STRING value (e.g. {@code font.body.family}). */
    public Theme string(String path, String value) {
        return push(path, ValueTypes.STRING, value);
    }

    private Theme push(String path, int valueType, Object value) {
        overrides.add(new TokenOverride(path, valueType, value));
        return this;
    }

    /** The accumulated overrides (package-private for {@link AdaptiveTheme}). */
    List<TokenOverride> overrides() {
        return overrides;
    }

    @Override
    public void emitInto(OpcodeSink sink) {
        for (TokenOverride o : overrides) {
            sink.setDesignToken(o.path(), o.valueType(), o.value());
        }
    }
}