package com.pathland.demo.quarkus;

import com.pathland.demo.DemoTheme;
import com.pathland.demo.SplitNavDemo;
import com.pathland.server.PathlandApp;
import com.pathland.view.ThemeData;
import com.pathland.view.View;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The Quarkus demo — the whole app. The Pathland starter provides SSR, the reserved
 * {@code /_pathland/ws} delta transport, and per-session state; this bean only supplies
 * the root view (and the optional theme).
 */
@ApplicationScoped
public class QuarkusDemoApp implements PathlandApp {

    @Override
    public View newRoot() {
        return new SplitNavDemo();
    }

    @Override
    public ThemeData theme() {
        return DemoTheme.adaptive();
    }
}