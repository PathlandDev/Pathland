package dev.pathland.markdown;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dev.pathland.view.core.AttributedString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java binding to Pathland's shared inline Markdown parser
 * ({@code libpathland_markdown}).
 *
 * <p>Parses the inline subset — {@code **bold**} / {@code __bold__},
 * {@code *italic*} / {@code _italic_}, {@code `code`},
 * {@code ~~strikethrough~~}, {@code [label](url)}, and backslash escapes —
 * into a view-core {@link AttributedString}. The native parser is the same one
 * the Rust DSL uses; the returned model is pure Java.
 *
 * <p>Mirrors the {@code pathland-render-html} JNA shim: the jar embeds the
 * cdylib under {@code META-INF/native/} and extracts it at runtime, so no
 * {@code java.library.path} is needed.
 */
public final class Markdown {

    /** The native C ABI. */
    private interface NativeMarkdown extends Library {
        Pointer pathland_markdown_parse(Pointer input, int inputLen, IntByReference outLen);

        void pathland_markdown_free(Pointer ptr, int len);
    }

    private static final String LIBRARY = "libpathland_markdown";
    private static final String SYSTEM_PROPERTY = "pathland.markdown.lib";
    private static final int CACHE_LIMIT = 512;
    private static final Map<String, AttributedString> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AttributedString> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private Markdown() {}

    private static final class Holder {
        static final NativeMarkdown INSTANCE = load();
    }

    private static NativeMarkdown instance() {
        return Holder.INSTANCE;
    }

    /** Whether the native parser can be loaded on this platform. */
    public static boolean isAvailable() {
        try {
            instance();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Parse inline Markdown into an attributed string. */
    public static AttributedString parse(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Memory input = new Memory(Math.max(1, bytes.length));
        if (bytes.length > 0) {
            input.write(0, bytes, 0, bytes.length);
        }
        IntByReference outLen = new IntByReference();
        NativeMarkdown nativeParser = instance();
        Pointer payload = nativeParser.pathland_markdown_parse(input, bytes.length, outLen);
        if (payload == null) {
            throw new IllegalArgumentException("Pathland markdown parse failed");
        }
        try {
            byte[] encoded = payload.getByteArray(0, outLen.getValue());
            return AttributedString.decode(encoded);
        } finally {
            nativeParser.pathland_markdown_free(payload, outLen.getValue());
        }
    }

    /**
     * Whether {@code text} contains any inline-Markdown marker. A cheap scan
     * lets plain strings skip the native parser entirely.
     */
    public static boolean containsMarkdownSyntax(String text) {
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '*':
                case '_':
                case '~':
                case '`':
                case '[':
                case '\\':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * Parse inline Markdown, falling back to plain text when the native parser
     * is unavailable or the input has no Markdown markers. Results are cached
     * (bounded) so static literals and repeated values parse once.
     */
    public static AttributedString parseOrPlain(String text) {
        if (!containsMarkdownSyntax(text)) {
            return AttributedString.plain(text);
        }
        AttributedString cached = CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        AttributedString value;
        try {
            value = parse(text);
        } catch (RuntimeException | LinkageError e) {
            // The released jar embeds the dylib; an unsupported platform or a
            // local build without it degrades to literal text rather than
            // failing the whole view.
            value = AttributedString.plain(text);
        }
        CACHE.put(text, value);
        return value;
    }

    private static NativeMarkdown load() {
        String path = resolveLibraryPath();
        if (path == null) {
            throw new IllegalStateException(
                    LIBRARY + " not found (not embedded in the jar, not on java.library.path, "
                            + SYSTEM_PROPERTY + " not set); build it first: cargo build -p pathland-markdown-capi");
        }
        return Native.load(path, NativeMarkdown.class);
    }

    private static String resolveLibraryPath() {
        String override = System.getProperty(SYSTEM_PROPERTY);
        if (override != null) {
            return override;
        }
        String embedded = extractEmbedded();
        if (embedded != null) {
            return embedded;
        }
        String fileName = LIBRARY + platformLibraryExtension();
        File devDir = new File(System.getProperty("user.dir"), "../../rust/target/debug");
        File devLib = new File(devDir, fileName);
        if (devLib.isFile()) {
            return devLib.getAbsolutePath();
        }
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath == null) {
            return null;
        }
        for (String dir : libraryPath.split(File.pathSeparator)) {
            File candidate = new File(dir, fileName);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static String extractEmbedded() {
        String resource = embeddedResourcePath();
        if (resource == null) {
            return null;
        }
        String fileName = LIBRARY + platformLibraryExtension();
        try (var stream = Markdown.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            File dir = new File(System.getProperty("java.io.tmpdir"),
                    "pathland-native-markdown-" + System.nanoTime());
            if (!dir.mkdirs()) {
                return null;
            }
            File out = new File(dir, fileName);
            try (var copy = new FileOutputStream(out)) {
                stream.transferTo(copy);
            }
            out.setExecutable(true, false);
            return out.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private static String embeddedResourcePath() {
        String fileName = LIBRARY + platformLibraryExtension();
        String platform = platformKey();
        if (platform != null) {
            String platformPath = "/META-INF/native/" + platform + "/" + fileName;
            if (Markdown.class.getResource(platformPath) != null) {
                return platformPath;
            }
        }
        String flat = "/META-INF/native/" + fileName;
        return Markdown.class.getResource(flat) != null ? flat : null;
    }

    private static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        String archKey = switch (arch) {
            case "aarch64", "arm64" -> "aarch64";
            case "x86_64", "amd64" -> "x86_64";
            default -> arch;
        };
        if (os.contains("win")) {
            return "windows-" + archKey;
        }
        if (os.contains("mac")) {
            return "macos-" + archKey;
        }
        if (os.contains("linux") || os.contains("nux")) {
            return "linux-" + archKey;
        }
        return null;
    }

    private static String platformLibraryExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            return ".dylib";
        }
        if (os.contains("win")) {
            return ".dll";
        }
        return ".so";
    }
}
