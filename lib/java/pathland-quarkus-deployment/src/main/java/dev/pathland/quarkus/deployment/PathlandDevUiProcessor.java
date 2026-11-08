package dev.pathland.quarkus.deployment;

import dev.pathland.quarkus.devui.PathlandDevTraceStore;
import dev.pathland.quarkus.devui.PathlandDevUiRpc;
import dev.pathland.view.core.protocol.ProtocolCatalog;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardAction;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.CardText;
import io.quarkus.devui.spi.page.Page;
import jakarta.inject.Singleton;
import org.jboss.jandex.DotName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the Pathland card in the Quarkus Dev UI (local development only):
 * a live session inspector backed by {@link PathlandDevUiRpc}, wire/SSR/protocol
 * tools, and links to the running app, health, and metrics endpoints.
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
        card.addPage(Page.webComponentPageBuilder()
                .title("Wire")
                .icon("font-awesome-solid:code")
                .componentLink("qwc-pathland-wire.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("SSR")
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-pathland-ssr.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Protocol")
                .icon("font-awesome-solid:book")
                .componentLink("qwc-pathland-protocol.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Topics")
                .icon("font-awesome-solid:comments")
                .componentLink("qwc-pathland-topics.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("State")
                .icon("font-awesome-solid:database")
                .componentLink("qwc-pathland-state.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Theme")
                .icon("font-awesome-solid:palette")
                .componentLink("qwc-pathland-theme.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Proxies")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-pathland-proxies.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Tree")
                .icon("font-awesome-solid:sitemap")
                .componentLink("qwc-pathland-tree.js"));
        card.addBuildTimeData("protocol", ProtocolCatalog.rows());
        card.addBuildTimeData("components", idsByName("component"));
        card.addBuildTimeData("properties", idsByName("property"));
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

    /** Register the dev-only trace ring (the host wires it when present). */
    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem traceStore() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(PathlandDevTraceStore.class)
                .setDefaultScope(DotName.createSimple(Singleton.class.getName()))
                .setUnremovable()
                .build();
    }

    /** Map numeric protocol ids to names for the tree inspector. */
    private static Map<Integer, String> idsByName(String kind) {
        Map<Integer, String> ids = new LinkedHashMap<>();
        for (ProtocolCatalog.Row row : ProtocolCatalog.rows()) {
            if (!kind.equals(row.getKind()) || !row.getValue().startsWith("0x")) {
                continue;
            }
            try {
                ids.put(Integer.parseInt(row.getValue().substring(2), 16), row.getName());
            } catch (NumberFormatException ignored) {
                // a non-numeric value is not an id
            }
        }
        return ids;
    }
}
