package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

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

    /** Configure the grid's values. */
/** An empty LazyHGrid (no values). */
    public static ViewBuilder<LazyHGrid, Config> make() {
        return new ViewBuilder<>(new LazyHGrid(new Config(), List.of()));
    }

    public static ViewBuilder<LazyHGrid, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyHGrid(config, List.of()));
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
