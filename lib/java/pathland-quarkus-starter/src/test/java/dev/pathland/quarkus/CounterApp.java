package dev.pathland.quarkus;

import dev.pathland.server.PathlandApp;
import dev.pathland.view.Button;
import dev.pathland.view.Text;
import dev.pathland.view.View;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import jakarta.enterprise.context.ApplicationScoped;

/** The counter app the multi-session WS test mounts: node 1 = Button, node 2 = Text label. */
@ApplicationScoped
public class CounterApp implements PathlandApp {

    @Override
    public View newRoot() {
        WritableSignal<Integer> count = Signals.signal(0);
        return Button.make(b -> b.action(() -> count.update(i -> i + 1)))
                .children(Text.make(Signals.computed(() -> "n=" + count.get())));
    }
}