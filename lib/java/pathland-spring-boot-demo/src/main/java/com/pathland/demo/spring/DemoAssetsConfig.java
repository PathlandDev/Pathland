package com.pathland.demo.spring;

import com.pathland.demo.assets.DemoAssets;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves the demo's media + icon assets from a **temp-dir extraction** of the
 * shared {@code pathland-demo-views} jar — fast disk serving with `Range` support
 * (audio seek) and caching — instead of Spring's default jar-based
 * {@code classpath:/META-INF/resources/} static serving, which reads the (larger)
 * media entries from inside the jar on every request.
 *
 * <p>Extraction happens once at construction (transient, per boot); the embedded
 * copy stays the single source of truth.
 */
@Configuration
public class DemoAssetsConfig implements WebMvcConfigurer {

    private final Path assetRoot = DemoAssets.extractToTemp();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/_pathland/assets/**")
                .addResourceLocations("file:" + assetRoot.resolve("assets") + "/");
    }
}