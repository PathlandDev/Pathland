package dev.pathland.quarkus.deployment;

import dev.pathland.quarkus.devui.PathlandDevUiRpc;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardAction;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.CardText;
import io.quarkus.devui.spi.page.Page;

/**
 * Registers the Pathland card in the Quarkus Dev UI (local development only):
 * a live session inspector backed by {@link PathlandDevUiRpc}, plus links to the
 * running app, health, and metrics endpoints.
 *
 * <p>The build steps are gated by {@link IsLocalDevelopment}, so the RPC bean is
 * only registered (and therefore only instantiated) in dev mode. Production and
 * native builds carry neither the card nor the bean registration.
 */
public final class PathlandDevUiProcessor {

    private static final String SESSIONS_PAGE = "qwc-pathland-sessions.js";

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem card() {
        CardPageBuildItem card = new CardPageBuildItem();
        card.addText(CardText.textBuilder()
                .title("Pathland")
                .icon("font-awesome-solid:layer-group")
                .dynamicText("overviewSummary")
                .build());
        card.addPage(Page.webComponentPageBuilder()
                .title("Sessions")
                .icon("font-awesome-solid:window-restore")
                .componentLink(SESSIONS_PAGE)
                .dynamicLabelJsonRPCMethodName("sessionsLabel"));
        card.addAction(CardAction.actionBuilder()
                .title("Open app")
                .icon("font-awesome-solid:up-right-from-square")
                .url("/")
                .showResultNotification(false)
                .build());
        card.addAction(CardAction.actionBuilder()
                .title("Health")
                .icon("font-awesome-solid:heart-pulse")
                .url("/q/health")
                .showResultNotification(false)
                .build());
        card.addAction(CardAction.actionBuilder()
                .title("Metrics")
                .icon("font-awesome-solid:chart-line")
                .url("/q/metrics")
                .showResultNotification(false)
                .build());
        return card;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem rpcProvider() {
        return new JsonRPCProvidersBuildItem(PathlandDevUiRpc.class);
    }
}
