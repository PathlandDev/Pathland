package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A virtualized vertical grid (windowed realization of cells). */
public final class LazyVGrid implements View, Configurable<LazyVGrid.Config>, ChildrenView {

    /** {@link LazyVGrid} values. */
    public static final class Config implements View.Config {

        private Signal<Alignment> alignment;
        private Signal<Float> spacing;
        private Signal<Integer> columns;
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

        /** Set a static column count (equal {@code 1fr} tracks). */
        public Config columns(int columns) {
            this.columns = Signals.constant(columns);
            return this;
        }

        /** Bind the column count to a signal. */
        public Config columns(Signal<Integer> columns) {
            this.columns = columns;
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

    private LazyVGrid(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the grid's values. */
/** An empty LazyVGrid (no values). */
    public static ViewBuilder<LazyVGrid, Config> make() {
        return new ViewBuilder<>(new LazyVGrid(new Config(), List.of()));
    }

    public static ViewBuilder<LazyVGrid, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyVGrid(config, List.of()));
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
            node.property(Properties.ALIGNMENT, config.alignment);
        }
        if (config.spacing != null) {
            node.property(Properties.SPACING, config.spacing);
        }
        if (config.tracks != null) {
            node.property(Properties.GRID_TRACKS, GridTracks.signal(config.tracks));
        }
        if (config.columns != null) {
            node.property(Properties.GRID_COLUMNS, config.columns);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
