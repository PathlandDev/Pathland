package dev.pathland.view;

/**
 * A video style ({@code VideoStyle}). A style turns a {@link Video}
 * surface's media configuration into a body view — the app-defined control UI
 * (transport buttons, seek/volume sliders). Injected down the tree via
 * {@code View.videoStyle(VideoStyle)}.
 */
public interface VideoStyle extends Style {

    /** Build the styled content for {@code config}. */
    View makeBody(Configuration config);

    /** Scopes this style down the wrapped subtree ({@code .modifiers(VideoStyle)}). */
    @Override
    default View body(View content) {
        return EnvironmentView.of(content, Environment.VIDEO_STYLE, this);
    }

    /**
     * The media configuration of the video being styled: the {@link MediaController}
     * the surface renders. The style's control UI binds the controller's transport
     * signals (spec/MEDIA.md); it may be {@code null} (an unbound surface).
     */
    record Configuration(MediaController controller) {}
}
