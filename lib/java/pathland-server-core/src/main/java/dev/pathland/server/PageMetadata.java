package dev.pathland.server;

/**
 * Transport-agnostic document metadata supplied to SSR: canonical URL, Open
 * Graph/Twitter cards, and JSON-LD fallbacks.
 *
 * <p>Host defaults are built per request (canonical URL + {@code pathland.seo.*}
 * config); {@link PathlandApp#pageMetadata(PageMetadataRequest)} lets the app
 * override/refine them for the active route before the first SSR paint. Null
 * fields in an overlay keep the base value, so callers only set what they mean.
 */
public final class PageMetadata {

    private final String title;
    private final String description;
    private final String canonicalUrl;
    private final String siteName;
    private final String imageUrl;
    private final String ogType;
    private final String twitterCard;
    private final String twitterSite;
    private final Boolean openGraph;
    private final Boolean twitter;
    private final Boolean structuredData;

    private PageMetadata(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.canonicalUrl = builder.canonicalUrl;
        this.siteName = builder.siteName;
        this.imageUrl = builder.imageUrl;
        this.ogType = builder.ogType;
        this.twitterCard = builder.twitterCard;
        this.twitterSite = builder.twitterSite;
        this.openGraph = builder.openGraph;
        this.twitter = builder.twitter;
        this.structuredData = builder.structuredData;
    }

    /** No document metadata (legacy head). */
    public static PageMetadata empty() {
        return builder().build();
    }

    /** Full SEO head for {@code canonicalUrl}: canonical + OG + Twitter + JSON-LD. */
    public static PageMetadata seo(String canonicalUrl) {
        return builder()
                .canonicalUrl(canonicalUrl)
                .openGraph(true)
                .twitter(true)
                .structuredData(true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String canonicalUrl() {
        return canonicalUrl;
    }

    public String siteName() {
        return siteName;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public String ogType() {
        return ogType;
    }

    public String twitterCard() {
        return twitterCard;
    }

    public String twitterSite() {
        return twitterSite;
    }

    public Boolean openGraph() {
        return openGraph;
    }

    public Boolean twitter() {
        return twitter;
    }

    public Boolean structuredData() {
        return structuredData;
    }

    /** Overlay {@code override} on this base: non-null override fields win. */
    public PageMetadata overlay(PageMetadata override) {
        if (override == null) {
            return this;
        }
        return builder()
                .title(override.title != null ? override.title : title)
                .description(override.description != null ? override.description : description)
                .canonicalUrl(override.canonicalUrl != null ? override.canonicalUrl : canonicalUrl)
                .siteName(override.siteName != null ? override.siteName : siteName)
                .imageUrl(override.imageUrl != null ? override.imageUrl : imageUrl)
                .ogType(override.ogType != null ? override.ogType : ogType)
                .twitterCard(override.twitterCard != null ? override.twitterCard : twitterCard)
                .twitterSite(override.twitterSite != null ? override.twitterSite : twitterSite)
                .openGraph(override.openGraph != null ? override.openGraph : openGraph)
                .twitter(override.twitter != null ? override.twitter : twitter)
                .structuredData(
                        override.structuredData != null ? override.structuredData : structuredData)
                .build();
    }

    public static final class Builder {
        private String title;
        private String description;
        private String canonicalUrl;
        private String siteName;
        private String imageUrl;
        private String ogType;
        private String twitterCard;
        private String twitterSite;
        private Boolean openGraph;
        private Boolean twitter;
        private Boolean structuredData;

        public Builder title(String value) {
            this.title = blankToNull(value);
            return this;
        }

        public Builder description(String value) {
            this.description = blankToNull(value);
            return this;
        }

        public Builder canonicalUrl(String value) {
            this.canonicalUrl = blankToNull(value);
            return this;
        }

        public Builder siteName(String value) {
            this.siteName = blankToNull(value);
            return this;
        }

        public Builder imageUrl(String value) {
            this.imageUrl = blankToNull(value);
            return this;
        }

        public Builder ogType(String value) {
            this.ogType = blankToNull(value);
            return this;
        }

        public Builder twitterCard(String value) {
            this.twitterCard = blankToNull(value);
            return this;
        }

        public Builder twitterSite(String value) {
            this.twitterSite = blankToNull(value);
            return this;
        }

        public Builder openGraph(Boolean value) {
            this.openGraph = value;
            return this;
        }

        public Builder twitter(Boolean value) {
            this.twitter = value;
            return this;
        }

        public Builder structuredData(Boolean value) {
            this.structuredData = value;
            return this;
        }

        public PageMetadata build() {
            return new PageMetadata(this);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
