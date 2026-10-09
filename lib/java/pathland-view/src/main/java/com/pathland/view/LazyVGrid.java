package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;
import java.util.function.Consumer;

/** A virtualized vertical grid (windowed realization of cells). */
public final class LazyVGrid implements View, Configurable<LazyVGrid.Config>, ChildrenView {

    /** {@link LazyVGrid} values. */
    public static final class Config implements View.Config {

        private Alignment alignment;
        private Float spacing;
        private Integer columns;
        private List<GridItem> tracks;

        /** Set the cell alignment. */
        public Config alignment(Alignment alignment) {
            this.alignment = alignment;
            return this;
        }

        /** Set the cell gap. */
        public Config spacing(float spacing) {
            this.spacing = spacing;
            return this;
        }

        /** Set a fixed column count (equal {@code 1fr} tracks). */
        public Config columns(int columns) {
            this.columns = columns;
            return this;
        }

        /** Set explicit per-track sizes. */
        public Config tracks(List<GridItem> tracks) {
            this.tracks = List.copyOf(tracks);
            return this;
        }

        /** Set explicit per-track sizes. */
        public Config tracks(GridItem... tracks) {
            this.tracks = List.of(tracks);
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private LazyVGrid(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    private static LazyVGrid build(Config config, View... children) {
        return new LazyVGrid(config, List.of(children));
    }

    /** A virtualized vertical grid; children are cells. */
    public static LazyVGrid of(View... children) {
        return build(new Config(), children);
    }

    /** A virtualized vertical grid with a fixed column count (equal {@code 1fr} tracks). */
    public static LazyVGrid of(int columns, View... children) {
        return build(new Config().columns(columns), children);
    }

    /** A virtualized vertical grid with constructor layout properties. */
    public static LazyVGrid of(Alignment alignment, float spacing, View... children) {
        return build(new Config().alignment(alignment).spacing(spacing), children);
    }

    /** A virtualized vertical grid with a fixed column count + constructor layout properties. */
    public static LazyVGrid of(int columns, Alignment alignment, float spacing, View... children) {
        return build(new Config().columns(columns).alignment(alignment).spacing(spacing), children);
    }

    /** A virtualized vertical grid with constructor layout properties. */
    public static LazyVGrid of(Alignment alignment, Float spacing, List<View> children) {
        Config config = new Config();
        if (alignment != null) {
            config.alignment(alignment);
        }
        if (spacing != null) {
            config.spacing(spacing);
        }
        return new LazyVGrid(config, List.copyOf(children));
    }

    /** A virtualized vertical grid with per-track sizes ({@code GridItem}). */
    public static LazyVGrid of(List<GridItem> tracks, View... children) {
        return build(new Config().tracks(tracks), children);
    }

    /** A virtualized vertical grid with per-track sizes + constructor layout properties. */
    public static LazyVGrid of(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        return build(new Config().tracks(tracks).alignment(alignment).spacing(spacing), children);
    }

    /** Configure the grid's values. */
    public static ViewBuilder<LazyVGrid, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyVGrid(config, List.of()));
    }

    /** Apply modifiers to an empty grid. */
    public static ViewBuilder<LazyVGrid, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new LazyVGrid(new Config(), List.of())).modifiers(modifiers);
    }

    /** Supply the grid's cells. */
    public static ViewBuilder<LazyVGrid, Config> children(View... children) {
        return new ViewBuilder<>(new LazyVGrid(new Config(), List.of())).children(children);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.LAZY_VGRID);
        if (config.alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) config.alignment.wire());
        }
        if (config.spacing != null) {
            node.properties.put(Properties.SPACING, config.spacing);
        }
        if (config.tracks != null) {
            node.properties.put(Properties.GRID_TRACKS, joinTracks(config.tracks));
        }
        if (config.columns != null) {
            node.properties.put(Properties.GRID_COLUMNS, (float) config.columns);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }

    private static String joinTracks(List<GridItem> tracks) {
        StringBuilder sb = new StringBuilder();
        for (GridItem t : tracks) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.token());
        }
        return sb.toString();
    }
}
