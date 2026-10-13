package dev.pathland.view.router;

import dev.pathland.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A declarative path → destination table (spec DSL.md §4.5): maps absolute path
 * patterns to lazy {@link RouteHandler}s, captures path params, and runs optional
 * guards. Guards are a {@link Predicate} over the captured params; when one fails
 * the match resolves to a **redirect** (the {@link Router} replaces to the redirect
 * target rather than mounting the destination).
 *
 * <p>Pattern segments are either literal path segments or {@code :name} params;
 * {@code /users/:id} matches {@code /users/42} with {@code params = {id: "42"}}.
 * A {@link #fallback} handler (the 404) serves paths that match nothing.
 */
public final class RouteTable {

    private final List<Entry> entries;
    private final RouteHandler fallback;

    private RouteTable(List<Entry> entries, RouteHandler fallback) {
        this.entries = entries;
        this.fallback = fallback;
    }

    /** Start building a route table. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Match an absolute path against the table, returning the first matching entry
     * (or the fallback when nothing matches). A guard failure resolves to a redirect
     * instead of a destination.
     */
    public Optional<RouteMatch> match(String path) {
        String pathOnly = Route.of(path).pathOnly();
        for (Entry entry : entries) {
            Optional<Params> params = entry.match(pathOnly);
            if (params.isEmpty()) {
                continue;
            }
            Params captured = params.get();
            if (entry.guard != null && !entry.guard.test(captured)) {
                return Optional.of(RouteMatch.redirect(entry.redirect));
            }
            return Optional.of(new RouteMatch(pathOnly, captured, entry.handler, null));
        }
        if (fallback != null) {
            return Optional.of(new RouteMatch(pathOnly, Params.none(), fallback, null));
        }
        return Optional.empty();
    }

    /** A match: either a destination (handler + params) or a guard redirect. */
    public record RouteMatch(String path, Params params, RouteHandler handler, String redirect) {

        static RouteMatch redirect(String target) {
            return new RouteMatch(null, Params.none(), null, target);
        }

        /** True when a guard redirected this match (replace, not mount). */
        public boolean isRedirect() {
            return redirect != null;
        }
    }

    /** Builds a {@link RouteTable}. */
    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();
        private RouteHandler fallback;

        /** A path → destination entry (no params). */
        public Builder route(String pattern, View view) {
            entries.add(new Entry(pattern, params -> view, null, null));
            return this;
        }

        /** A path → destination entry (with params). */
        public Builder route(String pattern, RouteHandler handler) {
            entries.add(new Entry(pattern, handler, null, null));
            return this;
        }

        /** A path → destination entry gated by a guard; on failure the router replaces to {@code redirect}. */
        public Builder route(String pattern, Predicate<Params> guard, String redirect, View view) {
            entries.add(new Entry(pattern, params -> view, Objects.requireNonNull(guard, "guard"), redirect));
            return this;
        }

        /** A path → destination entry gated by a guard (params destination). */
        public Builder route(String pattern, Predicate<Params> guard, String redirect, RouteHandler handler) {
            entries.add(new Entry(pattern, handler, Objects.requireNonNull(guard, "guard"), redirect));
            return this;
        }

        /** The catch-all handler for paths that match no pattern (the 404). */
        public Builder fallback(View view) {
            return fallback(params -> view);
        }

        /** The catch-all handler for paths that match no pattern (the 404). */
        public Builder fallback(RouteHandler handler) {
            this.fallback = Objects.requireNonNull(handler, "fallback");
            return this;
        }

        public RouteTable build() {
            return new RouteTable(List.copyOf(entries), fallback);
        }
    }

    private static final class Entry {

        private final List<Segment> segments;
        private final RouteHandler handler;
        private final Predicate<Params> guard;
        private final String redirect;

        Entry(String pattern, RouteHandler handler, Predicate<Params> guard, String redirect) {
            this.segments = parse(pattern);
            this.handler = handler;
            this.guard = guard;
            this.redirect = redirect;
        }

        Optional<Params> match(String path) {
            String[] given = split(path);
            if (given.length != segments.size()) {
                return Optional.empty();
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                if (!segment.matches(given[i], params)) {
                    return Optional.empty();
                }
            }
            return Optional.of(Params.of(params, path));
        }

        static List<Segment> parse(String pattern) {
            List<Segment> segments = new ArrayList<>();
            for (String part : split(pattern)) {
                if (part.startsWith(":")) {
                    segments.add(Segment.param(part.substring(1)));
                } else {
                    segments.add(Segment.literal(part));
                }
            }
            return segments;
        }

        static String[] split(String path) {
            String trimmed = path.startsWith("/") ? path.substring(1) : path;
            return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
        }
    }

    private sealed interface Segment permits Segment.Literal, Segment.Param {

        boolean matches(String part, Map<String, String> params);

        record Literal(String value) implements Segment {
            @Override
            public boolean matches(String part, Map<String, String> params) {
                return value.equals(part);
            }
        }

        record Param(String name) implements Segment {
            @Override
            public boolean matches(String part, Map<String, String> params) {
                params.put(name, part);
                return true;
            }
        }

        static Segment literal(String value) {
            return new Literal(value);
        }

        static Segment param(String name) {
            return new Param(name);
        }
    }
}