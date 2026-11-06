package dev.pathland.render.html;

/**
 * Document-level metadata for the HTML renderer (SEO head) — canonical URL,
 * Open Graph / Twitter cards, and JSON-LD fallbacks.
 *
 * <p>This is <b>renderer input</b>, not protocol state: canonical/OG/Twitter are
 * web-only artifacts and stay host-owned (spec/OPCODE.md §Window metadata). The
 * application-owned presentation metadata ({@code TITLE}, {@code DESCRIPTION},
 * {@code LOCALE}) still arrives through {@code META::SET_WINDOW_METADATA}; a
 * title/description here is only a fallback when that field is unset.
 */
public final class DocumentMeta {

    String title;
    String description;
    String canonicalUrl;
    String siteName;
    String imageUrl;
    String ogType;
    String twitterCard;
    String twitterSite;
    boolean openGraph;
    boolean twitter;
    boolean structuredData;

    private DocumentMeta() {}

    /** No extra metadata — the legacy document head. */
    public static DocumentMeta none() {
        return new DocumentMeta();
    }

    /** No extra metadata — the legacy document head. */
    public static DocumentMeta create() {
        return new DocumentMeta();
    }

    /**
     * Full SEO head for {@code canonicalUrl}: canonical + Open Graph + Twitter +
     * structured data. Refine with the fluent methods.
     */
    public static DocumentMeta seo(String canonicalUrl) {
        DocumentMeta meta = new DocumentMeta();
        meta.canonicalUrl = blankToNull(canonicalUrl);
        meta.openGraph = true;
        meta.twitter = true;
        meta.structuredData = true;
        return meta;
    }

    /** Fallback title when the stream does not set {@code WINDOW_METADATA::TITLE}. */
    public DocumentMeta title(String value) {
        this.title = blankToNull(value);
        return this;
    }

    /** Fallback description when the stream does not set {@code WINDOW_METADATA::DESCRIPTION}. */
    public DocumentMeta description(String value) {
        this.description = blankToNull(value);
        return this;
    }

    /** Absolute canonical URL for the current view. */
    public DocumentMeta canonicalUrl(String value) {
        this.canonicalUrl = blankToNull(value);
        return this;
    }

    /** {@code og:site_name}. */
    public DocumentMeta siteName(String value) {
        this.siteName = blankToNull(value);
        return this;
    }

    /** {@code og:image} / {@code twitter:image}; absent → the first image source. */
    public DocumentMeta imageUrl(String value) {
        this.imageUrl = blankToNull(value);
        return this;
    }

    /** {@code og:type} (default {@code website}). */
    public DocumentMeta ogType(String value) {
        this.ogType = blankToNull(value);
        return this;
    }

    /** {@code twitter:card} (default {@code summary}/{@code summary_large_image}). */
    public DocumentMeta twitterCard(String value) {
        this.twitterCard = blankToNull(value);
        return this;
    }

    /** {@code twitter:site} (e.g. {@code @pathland}). */
    public DocumentMeta twitterSite(String value) {
        this.twitterSite = blankToNull(value);
        return this;
    }

    /** Enable/disable Open Graph tags. */
    public DocumentMeta openGraph(boolean enabled) {
        this.openGraph = enabled;
        return this;
    }

    /** Enable/disable Twitter card tags. */
    public DocumentMeta twitter(boolean enabled) {
        this.twitter = enabled;
        return this;
    }

    /** Enable/disable JSON-LD structured data. */
    public DocumentMeta structuredData(boolean enabled) {
        this.structuredData = enabled;
        return this;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
