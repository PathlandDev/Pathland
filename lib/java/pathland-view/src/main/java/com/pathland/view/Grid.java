package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;
import java.util.function.Consumer;

/** A static 2D matrix grid ({@code Grid}); children are cells, row-major. */
public final class Grid implements View, Configurable<Grid.Config>, ChildrenView {

    /** {@link Grid} values. */
    public static final class Config implements View.Config {

        private Alignment alignment;
        private Float spacing;
        private Integer columns;
        private Integer rows;
        private List<GridItem> tracks;

        /** Set the 2D alignment. */
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

        /** Set a fixed row count (equal {@code 1fr} tracks). */
        public Config rows(int rows) {
            this.rows = rows;
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

    private Grid(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    private static Grid build(Config config, View... children) {
        return new Grid(config, List.of(children));
    }

    /** A static 2D matrix grid; children are cells, row-major. */
    public static Grid of(View... children) {
        return build(new Config(), children);
    }

    /** A static 2D matrix grid with a fixed column count (equal {@code 1fr} tracks). */
    public static Grid of(int columns, View... children) {
        return build(new Config().columns(columns), children);
    }

    /** A static 2D matrix grid with fixed column and row counts (equal {@code 1fr} tracks). */
    public static Grid of(int columns, int rows, View... children) {
        return build(new Config().columns(columns).rows(rows), children);
    }

    /** A static 2D matrix grid with constructor layout properties. */
    public static Grid of(Alignment alignment, float spacing, View... children) {
        return build(new Config().alignment(alignment).spacing(spacing), children);
    }

    /** A static 2D matrix grid with a fixed column count + constructor layout properties. */
    public static Grid of(int columns, Alignment alignment, float spacing, View... children) {
        return build(new Config().columns(columns).alignment(alignment).spacing(spacing), children);
    }

    /** A static 2D matrix grid with fixed column and row counts + constructor layout properties. */
    public static Grid of(int columns, int rows, Alignment alignment, float spacing, View... children) {
        return build(new Config().columns(columns).rows(rows).alignment(alignment).spacing(spacing), children);
    }

    /** A static 2D matrix grid with constructor layout properties. */
    public static Grid of(Alignment alignment, Float spacing, List<View> children) {
        Config config = new Config();
        if (alignment != null) {
            config.alignment(alignment);
        }
        if (spacing != null) {
            config.spacing(spacing);
        }
        return new Grid(config, List.copyOf(children));
    }

    /** A static 2D matrix grid with per-track sizes ({@code GridItem}). */
    public static Grid of(List<GridItem> tracks, View... children) {
        return build(new Config().tracks(tracks), children);
    }

    /** A static 2D matrix grid with per-track sizes + constructor layout properties. */
    public static Grid of(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        return build(new Config().tracks(tracks).alignment(alignment).spacing(spacing), children);
    }

    /** Configure the grid's values. */
    public static ViewBuilder<Grid, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Grid(config, List.of()));
    }

    /** Apply modifiers to an empty grid. */
    public static ViewBuilder<Grid, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Grid(new Config(), List.of())).modifiers(modifiers);
    }

    /** Supply the grid's cells (row-major). */
    public static ViewBuilder<Grid, Config> children(View... children) {
        return new ViewBuilder<>(new Grid(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.GRID);
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
        if (config.rows != null) {
            node.properties.put(Properties.GRID_ROWS, (float) config.rows);
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
