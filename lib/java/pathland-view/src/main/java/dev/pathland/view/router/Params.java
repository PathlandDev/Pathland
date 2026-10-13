package dev.pathland.view.router;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * The path parameters captured from a route pattern, plus the matched path
 * (spec DSL.md §4.5). Replaces the raw {@code Map<String,String>} in
 * {@link RouteHandler} with typed access — {@code /users/:id} → {@code {id:"42"}},
 * read as {@code params.intValue("id")}.
 *
 * <p>Values are stored as strings (the URL is stringly-typed); the typed getters
 * parse on demand and return {@code 0 / false} when absent or unparseable.
 */
public final class Params {

    private static final Params NONE = new Params(Map.of(), null);

    private final Map<String, String> values;
    private final String path;

    private Params(Map<String, String> values, String path) {
        this.values = values;
        this.path = path;
    }

    /** An empty param set (for no-param routes / the fallback). */
    public static Params none() {
        return NONE;
    }

    /** Wrap captured path params plus the matched path. */
    static Params of(Map<String, String> values, String path) {
        return new Params(Collections.unmodifiableMap(values), path);
    }

    /** The raw string value of a param, or {@code null} when absent. */
    public String get(String name) {
        return values.get(name);
    }

    /** The param as an {@code int} (0 when absent/unparseable). */
    public int intValue(String name) {
        try {
            return Integer.parseInt(get(name));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The param as a {@code long} (0 when absent/unparseable). */
    public long longValue(String name) {
        try {
            return Long.parseLong(get(name));
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** The param as a {@code double} (0 when absent/unparseable). */
    public double doubleValue(String name) {
        try {
            return Double.parseDouble(get(name));
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    /** The param as a {@code boolean} ({@code "true"}/{@code "1"} → true). */
    public boolean booleanValue(String name) {
        String v = get(name);
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1"));
    }

    /** The matched path (the absolute path the table resolved). */
    public String path() {
        return path;
    }

    /** Whether a param is present. */
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /** Whether no params were captured. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Params other && values.equals(other.values) && Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, path);
    }

    @Override
    public String toString() {
        return "Params" + values;
    }
}