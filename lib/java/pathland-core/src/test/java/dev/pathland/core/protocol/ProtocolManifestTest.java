package dev.pathland.core.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the hand-written JVM protocol constant classes against the neutral
 * {@code spec/protocol.tsv} manifest emitted from the canonical Rust constants.
 *
 * <p>No Java source is generated. This test is the drift guard: add or renumber a
 * Rust constant, regenerate the manifest, and either update the Java mirror or
 * this suite fails with the exact row that drifted.
 */
class ProtocolManifestTest {

    private static final String COMMANDS = "dev.pathland.core.protocol.Commands$";
    private static final Set<String> KINDS = Set.of(
            "category", "component", "command", "field", "flag", "listener",
            "size", "value_type", "property", "property_value_type");

    @Test
    void manifestIsWellFormed() throws IOException {
        List<Row> rows = rows();
        Set<String> keys = new HashSet<>();
        Set<String> propertyNames = new HashSet<>();
        Set<String> valueTypeNames = new HashSet<>();

        for (Row row : rows) {
            assertTrue(KINDS.contains(row.kind()), "unknown manifest kind: " + row.kind());
            assertTrue(keys.add(row.kind() + "/" + row.group() + "/" + row.name()),
                    "duplicate manifest row: " + row);
            if (row.kind().equals("property")) {
                propertyNames.add(row.name());
            } else if (row.kind().equals("value_type")) {
                valueTypeNames.add(row.name());
            }
        }

        Set<String> mappedProperties = new HashSet<>();
        for (Row row : rows) {
            if (row.kind().equals("property_value_type")) {
                assertTrue(propertyNames.contains(row.name()),
                        "value-type mapping for unknown property: " + row.name());
                assertTrue(valueTypeNames.contains(row.value()),
                        "unknown value type in mapping: " + row);
                assertTrue(mappedProperties.add(row.name()),
                        "duplicate value-type mapping: " + row.name());
            }
        }

        assertEquals(propertyNames, mappedProperties,
                "every property must have exactly one property_value_type row");
    }

    @Test
    void javaConstantsMatchManifest() throws Exception {
        Map<Class<?>, Set<String>> expected = new HashMap<>();
        for (Row row : rows()) {
            if (row.kind().equals("property_value_type")) {
                continue;
            }
            expected.computeIfAbsent(targetClass(row), ignored -> new HashSet<>()).add(row.name());
            Field field = targetClass(row).getField(row.name());
            Object actual = field.get(null);
            Object wanted = field.getType() == float.class
                    ? (Object) Float.parseFloat(row.value())
                    : (Object) Integer.decode(row.value());
            assertEquals(wanted, actual, row.kind() + " " + row.name());
        }

        for (Map.Entry<Class<?>, Set<String>> entry : expected.entrySet()) {
            for (Field field : entry.getKey().getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isPublic(mods)
                        && Modifier.isStatic(mods)
                        && (field.getType() == int.class || field.getType() == float.class)) {
                    assertTrue(entry.getValue().contains(field.getName()),
                            "Java constant has no manifest row: "
                                    + entry.getKey().getSimpleName() + "." + field.getName());
                }
            }
        }
    }

    @Test
    void valueTypeMappingMatchesManifest() throws Exception {
        for (Row row : rows()) {
            if (!row.kind().equals("property_value_type")) {
                continue;
            }
            int property = ((Number) Properties.class.getField(row.name()).get(null)).intValue();
            int expected = ((Number) ValueTypes.class.getField(row.value()).get(null)).intValue();
            assertEquals(expected, ValueTypes.forProperty(property),
                    "ValueTypes.forProperty(" + row.name() + ")");
        }
    }

    private static Class<?> targetClass(Row row) throws ClassNotFoundException {
        return switch (row.kind()) {
            case "category" -> Categories.class;
            case "component" -> Components.class;
            case "property" -> Properties.class;
            case "value_type" -> ValueTypes.class;
            case "command", "field", "flag", "listener", "size" ->
                    Class.forName(COMMANDS + row.group());
            default -> throw new IllegalArgumentException("unknown manifest kind: " + row.kind());
        };
    }

    private static List<Row> rows() throws IOException {
        Path manifest = locateManifest();
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            assertEquals(4, parts.length, "bad manifest row: " + line);
            rows.add(new Row(parts[0], parts[1], parts[2], parts[3]));
        }
        assertFalse(rows.isEmpty(), "manifest is empty: " + manifest);
        return rows;
    }

    private static Path locateManifest() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("spec/protocol.tsv");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate spec/protocol.tsv from " + System.getProperty("user.dir"));
    }

    private record Row(String kind, String group, String name, String value) {
        @Override
        public String toString() {
            return kind + "/" + group + "/" + name + "=" + value;
        }
    }
}
