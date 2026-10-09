package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A virtualized horizontal grid (windowed realization of cells). */
public final class LazyHGrid implements View, Configurable<LazyHGrid.Config>, ChildrenView {

    /** {@link LazyHGrid} values. */
    public static final class Config implements View.Config {

        private Signal<Alignment> alignment;
        private Signal<Float> spacing;
        private Signal<Integer> rows;
        private Signal<List<GridItem>> tracks;

        /** Set a static cell alignment. */
        public Config alignment(Alignment alignment) {
            this.alignment = Signals.constant(alignment);
            return this;
        }

        /** Bind the cell alignment to a signal. */
        public Config alignment(Signal<Alignment> alignment) {
            this.alignment = alignment;
            return this;
        }

        /** Set a static cell gap. */
        public Config spacing(float spacing) {
            this.spacing = Signals.constant(spacing);
            return this;
        }

        /** Bind the cell gap to a signal. */
        public Config spacing(Signal<Float> spacing) {
            this.spacing = spacing;
            return this;
        }

        /** Set a static row count (equal {@code 1fr} rows). */
        public Config rows(int rows) {
            this.rows = Signals.constant(rows);
            return this;
        }

        /** Bind the row count to a signal. */
        public Config rows(Signal<Integer> rows) {
            this.rows = rows;
            return this;
        }

        /** Set static per-track sizes. */
        public Config tracks(List<GridItem> tracks) {
            this.tracks = Signals.constant(List.copyOf(tracks));
            return this;
        }

        /** Set static per-track sizes. */
        public Config tracks(GridItem... tracks) {
            this.tracks = Signals.constant(List.of(tracks));
            return this;
        }

        /** Bind per-track sizes to a signal. */
        public Config tracks(Signal<List<GridItem>> tracks) {
            this.tracks = tracks;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private LazyHGrid(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    private static LazyHGrid build(Config config, View... children) {
        return new LazyHGrid(config, List.of(children));
    }

    /** A virtualized horizontal grid; children are cells. */
    public static LazyHGrid of(View... children) {
        return build(new Config(), children);
    }

    /** A virtualized horizontal grid with a fixed row count (equal {@code 1fr} rows). */
    public static LazyHGrid of(int rows, View... children) {
        return build(new Config().rows(rows), children);
    }

    /** A virtualized horizontal grid with constructor layout properties. */
    public static LazyHGrid of(Alignment alignment, float spacing, View... children) {
        return build(new Config().alignment(alignment).spacing(spacing), children);
    }

    /** A virtualized horizontal grid with a fixed row count + constructor layout properties. */
    public static LazyHGrid of(int rows, Alignment alignment, float spacing, View... children) {
        return build(new Config().rows(rows).alignment(alignment).spacing(spacing), children);
    }

    /** A virtualized horizontal grid with constructor layout properties. */
    public static LazyHGrid of(Alignment alignment, Float spacing, List<View> children) {
        Config config = new Config();
        if (alignment != null) {
            config.alignment(alignment);
        }
        if (spacing != null) {
            config.spacing(spacing);
        }
        return new LazyHGrid(config, List.copyOf(children));
    }

    /** A virtualized horizontal grid with per-track sizes ({@code GridItem}). */
    public static LazyHGrid of(List<GridItem> tracks, View... children) {
        return build(new Config().tracks(tracks), children);
    }

    /** A virtualized horizontal grid with per-track sizes + constructor layout properties. */
    public static LazyHGrid of(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        return build(new Config().tracks(tracks).alignment(alignment).spacing(spacing), children);
    }

    /** Configure the grid's values. */
    public static ViewBuilder<LazyHGrid, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyHGrid(config, List.of()));
    }

    /** Apply modifiers to an empty grid. */
    public static ViewBuilder<LazyHGrid, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new LazyHGrid(new Config(), List.of())).modifiers(modifiers);
    }

    /** Supply the grid's cells. */
    public static ViewBuilder<LazyHGrid, Config> children(View... children) {
        return new ViewBuilder<>(new LazyHGrid(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.LAZY_HGRID);
        if (config.alignment != null) {
            node.property(Properties.ALIGNMENT, config.alignment);
        }
        if (config.spacing != null) {
            node.property(Properties.SPACING, config.spacing);
        }
        if (config.tracks != null) {
            node.property(Properties.GRID_TRACKS, GridTracks.signal(config.tracks));
        }
        if (config.rows != null) {
            node.property(Properties.GRID_ROWS, config.rows);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
