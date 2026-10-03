package com.pathland.quarkus;

import com.pathland.server.PathlandApp;
import com.pathland.view.Button;
import com.pathland.view.Text;
import com.pathland.view.View;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import jakarta.enterprise.context.ApplicationScoped;

/** The counter app the multi-session WS test mounts: node 1 = Button, node 2 = Text label. */
@ApplicationScoped
public class CounterApp implements PathlandApp {

    @Override
    public View newRoot() {
        WritableSignal<Integer> count = Signals.signal(0);
        return Button.of(Text.of(Signals.computed(() -> "n=" + count.get())),
                () -> count.update(i -> i + 1));
    }
}