package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A static 2D matrix grid ({@code Grid}); children are cells, row-major. */
public final class Grid implements View, Configurable<Grid.Config>, ChildrenView {

    /** {@link Grid} values. */
    public static final class Config implements View.Config {

        private Signal<Alignment> alignment;
        private Signal<Float> spacing;
        private Signal<Integer> columns;
        private Signal<Integer> rows;
        private Signal<List<GridItem>> tracks;

        /** Set a static 2D alignment. */
        public Config alignment(Alignment alignment) {
            this.alignment = Signals.constant(alignment);
            return this;
        }

        /** Bind the 2D alignment to a signal. */
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

        /** Set a static row count (equal {@code 1fr} tracks). */
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

    private Grid(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the grid's values. */
/** An empty Grid (no values). */
    public static ViewBuilder<Grid, Config> make() {
        return new ViewBuilder<>(new Grid(new Config(), List.of()));
    }

    public static ViewBuilder<Grid, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Grid(config, List.of()));
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
        if (config.rows != null) {
            node.property(Properties.GRID_ROWS, config.rows);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
