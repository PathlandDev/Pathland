package dev.pathland.render.html;

import dev.pathland.view.core.Roles;
import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.protocol.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Component-derived JSON-LD structured data. Byte-identical to the Rust
 * renderer's {@code structured_data.rs}: a {@code WebPage} plus one
 * {@code Article} per {@code ROLE_ARTICLE} region.
 */
final class StructuredData {

    private StructuredData() {}

    /** The JSON-LD script element, or {@code null} when structured data is disabled. */
    static String render(
            DocumentMeta meta, WindowMeta window, TreeMap<Integer, RenderNode> nodes, int root) {
        if (!meta.structuredData) {
            return null;
        }
        String title = DocumentHead.title(window, meta);
        String description = DocumentHead.description(window, meta);
        String canonical = DocumentMeta.blankToNull(meta.canonicalUrl);
        String locale = DocumentMeta.blankToNull(window.locale);

        List<String> graph = new ArrayList<>();
        StringBuilder webPage = new StringBuilder("{\"@type\":\"WebPage\"");
        pushString(webPage, "name", title);
        if (notBlank(description)) {
            pushString(webPage, "description", description);
        }
        if (canonical != null) {
            pushString(webPage, "url", canonical);
        }
        if (locale != null) {
            pushString(webPage, "inLanguage", locale);
        }
        webPage.append('}');
        graph.add(webPage.toString());

        for (int id : articleIds(nodes, root)) {
            String article = articleJson(nodes, id, canonical, locale);
            if (article != null) {
                graph.add(article);
            }
        }

        return "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@graph\":["
                + String.join(",", graph) + "]}</script>\n";
    }

    private static List<Integer> articleIds(TreeMap<Integer, RenderNode> nodes, int root) {
        List<Integer> ids = new ArrayList<>();
        collectArticles(nodes, root, ids);
        return ids;
    }

    private static void collectArticles(
            TreeMap<Integer, RenderNode> nodes, int id, List<Integer> out) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return;
        }
        if (node.roleCode() == Roles.ARTICLE) {
            out.add(id);
        }
        for (int child : node.children) {
            collectArticles(nodes, child, out);
        }
    }

    private static String articleJson(
            TreeMap<Integer, RenderNode> nodes, int id, String canonical, String locale) {
        String headline = findHeading(nodes, id);
        String description = findParagraph(nodes, id);
        String imageSource = DocumentHead.firstImageSource(nodes, id);
        String image = imageSource == null
                ? null
                : DocumentHead.resolveUrl(canonical, imageSource);
        if (!notBlank(headline) && !notBlank(description) && !notBlank(image)) {
            return null;
        }

        StringBuilder article = new StringBuilder("{\"@type\":\"Article\"");
        if (notBlank(headline)) {
            pushString(article, "headline", headline);
        }
        if (notBlank(description)) {
            pushString(article, "description", description);
        }
        if (notBlank(image)) {
            pushString(article, "image", image);
        }
        if (canonical != null) {
            article.append(",\"mainEntityOfPage\":{\"@type\":\"WebPage\",\"@id\":\"")
                    .append(Format.jsonEscape(canonical))
                    .append("\"}");
        }
        if (locale != null) {
            pushString(article, "inLanguage", locale);
        }
        article.append('}');
        return article.toString();
    }

    private static String findHeading(TreeMap<Integer, RenderNode> nodes, int id) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return null;
        }
        if (node.component == Components.TEXT && isHeading(node)) {
            if (notBlank(node.text)) {
                return node.text;
            }
        }
        for (int child : node.children) {
            String text = findHeading(nodes, child);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String findParagraph(TreeMap<Integer, RenderNode> nodes, int id) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return null;
        }
        if (node.component == Components.TEXT && node.roleCode() == Roles.PARAGRAPH) {
            if (notBlank(node.text)) {
                return node.text;
            }
        }
        for (int child : node.children) {
            String text = findParagraph(nodes, child);
            if (text != null) {
                return text;
            }
        }
        return findFirstBodyText(nodes, id);
    }

    private static String findFirstBodyText(TreeMap<Integer, RenderNode> nodes, int id) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return null;
        }
        if (node.component == Components.TEXT && !isHeading(node) && notBlank(node.text)) {
            return node.text;
        }
        for (int child : node.children) {
            String text = findFirstBodyText(nodes, child);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static boolean isHeading(RenderNode node) {
        Integer style = node.textStyleCode();
        return (style != null && style >= 0 && style <= 4) || node.roleCode() == Roles.HEADER;
    }

    private static void pushString(StringBuilder out, String name, String value) {
        out.append(",\"")
                .append(name)
                .append("\":\"")
                .append(Format.jsonEscape(value))
                .append('"');
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isEmpty();
    }
}
