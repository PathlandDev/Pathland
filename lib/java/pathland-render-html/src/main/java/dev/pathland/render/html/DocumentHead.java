package dev.pathland.render.html;

import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.protocol.Properties;
import java.util.TreeMap;

/**
 * SEO/document-head construction for {@link DocumentMeta}. Kept byte-identical
 * to the Rust renderer's {@code document.rs}.
 */
final class DocumentHead {

    static final String DEFAULT_TITLE = "Pathland";

    private DocumentHead() {}

    /** The effective title: window metadata wins, then the host fallback. */
    static String title(WindowMeta window, DocumentMeta meta) {
        if (window.title != null) {
            return window.title;
        }
        return meta.title == null ? DEFAULT_TITLE : meta.title;
    }

    /** The effective description: window metadata wins, then the host fallback. */
    static String description(WindowMeta window, DocumentMeta meta) {
        return window.description != null ? window.description : meta.description;
    }

    /** The image used by OG/Twitter/Article JSON-LD. */
    static String imageUrl(DocumentMeta meta, TreeMap<Integer, RenderNode> nodes, int root) {
        if (meta.imageUrl != null) {
            return meta.imageUrl;
        }
        return firstImageSource(nodes, root);
    }

    /** Canonical + Open Graph + Twitter tags, in the Rust renderer's fixed order. */
    static String headTags(
            DocumentMeta meta, String title, String description, String locale, String image) {
        StringBuilder out = new StringBuilder();
        String canonical = DocumentMeta.blankToNull(meta.canonicalUrl);
        if (canonical != null) {
            out.append("<link rel=\"canonical\" href=\"")
                    .append(Format.escapeHtmlAttr(canonical))
                    .append("\">\n");
        }

        if (meta.openGraph) {
            String ogType = meta.ogType == null ? "website" : meta.ogType;
            out.append("<meta property=\"og:type\" content=\"")
                    .append(Format.escapeHtmlAttr(ogType))
                    .append("\">\n");
            addProperty(out, "og:title", title);
            if (description != null) {
                addProperty(out, "og:description", description);
            }
            if (canonical != null) {
                addProperty(out, "og:url", canonical);
            }
            if (notBlank(meta.siteName)) {
                addProperty(out, "og:site_name", meta.siteName);
            }
            if (notBlank(image)) {
                addProperty(out, "og:image", image);
            }
            if (notBlank(locale)) {
                addProperty(out, "og:locale", locale);
            }
        }

        if (meta.twitter) {
            String card = notBlank(meta.twitterCard)
                    ? meta.twitterCard
                    : (notBlank(image) ? "summary_large_image" : "summary");
            addName(out, "twitter:card", card);
            addName(out, "twitter:title", title);
            if (description != null) {
                addName(out, "twitter:description", description);
            }
            if (notBlank(image)) {
                addName(out, "twitter:image", image);
            }
            if (notBlank(meta.twitterSite)) {
                addName(out, "twitter:site", meta.twitterSite);
            }
        }
        return out.toString();
    }

    /** Resolve a possibly root-relative asset URL against the canonical origin. */
    static String resolveUrl(String canonical, String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
            return url;
        }
        if (canonical != null && url.startsWith("/")) {
            String origin = originOf(canonical);
            if (origin != null) {
                return origin + url;
            }
        }
        return url;
    }

    private static String originOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int start = scheme + 3;
        int slash = url.indexOf('/', start);
        return slash < 0 ? url : url.substring(0, slash);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isEmpty();
    }

    private static void addProperty(StringBuilder out, String property, String value) {
        out.append("<meta property=\"")
                .append(property)
                .append("\" content=\"")
                .append(Format.escapeHtmlAttr(value))
                .append("\">\n");
    }

    private static void addName(StringBuilder out, String name, String value) {
        out.append("<meta name=\"")
                .append(name)
                .append("\" content=\"")
                .append(Format.escapeHtmlAttr(value))
                .append("\">\n");
    }

    /** The first {@code IMAGE}'s {@code IMAGE_SOURCE} in depth-first document order. */
    static String firstImageSource(TreeMap<Integer, RenderNode> nodes, int root) {
        RenderNode node = nodes.get(root);
        if (node == null) {
            return null;
        }
        if (node.component == Components.IMAGE) {
            String src = node.strings.get(Properties.IMAGE_SOURCE);
            if (src != null && !src.isEmpty()) {
                return src;
            }
        }
        for (int child : node.children) {
            String src = firstImageSource(nodes, child);
            if (src != null) {
                return src;
            }
        }
        return null;
    }
}
