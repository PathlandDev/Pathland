package com.pathland.demo.spring;

import com.pathland.demo.DemoTheme;
import com.pathland.demo.SplitNavDemo;
import com.pathland.server.PathlandApp;
import com.pathland.view.ThemeData;
import com.pathland.view.View;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The Spring Boot demo — the whole app. The Pathland starter provides SSR, the
 * reserved {@code /_pathland/ws} delta transport, and per-session state; this
 * class only supplies the root view (and the optional theme).
 */
@SpringBootApplication
public class SpringDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringDemoApplication.class, args);
    }

    /** The app's root view factory + theme — the only app-specific wiring. */
    @Bean
    PathlandApp pathlandApp() {
        return new PathlandApp() {
            @Override
            public View newRoot() {
                return new SplitNavDemo();
            }

            @Override
            public ThemeData theme() {
                return DemoTheme.adaptive();
            }
        };
    }
}