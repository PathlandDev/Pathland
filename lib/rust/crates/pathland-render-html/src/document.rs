//! Document-level metadata for the HTML renderer (SEO head) — canonical URL,
//! Open Graph / Twitter cards, and the fallback title/description used by
//! JSON-LD.
//!
//! This is **renderer input**, not protocol state: canonical/OG/Twitter are
//! web-only artifacts and stay host-owned (spec/OPCODE.md §Window metadata).
//! The application-owned presentation metadata (`TITLE`, `DESCRIPTION`,
//! `LOCALE`) still arrives through `META::SET_WINDOW_METADATA`; a
//! [`DocumentMeta`] title/description is only a fallback when that field is
//! unset.

use std::collections::BTreeMap;

use pathland_core::{component_type, property_id, WindowMeta};

use crate::{escape_html_attr, Node};

/// The renderer's default document title when neither window metadata nor
/// [`DocumentMeta`] supplies one.
pub const DEFAULT_TITLE: &str = "Pathland";

/// Optional document metadata supplied by the host for one render call.
///
/// The renderer remains a pure function of the opcode stream **plus this
/// value**: nothing is retained across calls. Title/description set here are
/// fallbacks; `META::SET_WINDOW_METADATA` values win.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct DocumentMeta {
    /// Fallback title when the stream does not set `WINDOW_METADATA::TITLE`.
    pub title: Option<String>,
    /// Fallback description when the stream does not set
    /// `WINDOW_METADATA::DESCRIPTION`.
    pub description: Option<String>,
    /// Absolute canonical URL for the current view.
    pub canonical_url: Option<String>,
    /// `og:site_name`.
    pub site_name: Option<String>,
    /// `og:image` / `twitter:image`; when absent the first `IMAGE` source in
    /// the tree is used.
    pub image_url: Option<String>,
    /// `og:type` (default `website`).
    pub og_type: Option<String>,
    /// `twitter:card` (default `summary`, or `summary_large_image` when an
    /// image is available).
    pub twitter_card: Option<String>,
    /// `twitter:site` (e.g. `@pathland`).
    pub twitter_site: Option<String>,
    /// Emit Open Graph tags.
    pub open_graph: bool,
    /// Emit Twitter card tags.
    pub twitter: bool,
    /// Emit JSON-LD structured data (WebPage + Article).
    pub structured_data: bool,
}

impl DocumentMeta {
    /// No extra metadata — the legacy document head.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// No extra metadata — the legacy document head.
    #[must_use]
    pub fn none() -> Self {
        Self::default()
    }

    /// Full SEO head for `canonical_url`: canonical + Open Graph + Twitter +
    /// structured data. Refine with the other builders/fields.
    #[must_use]
    pub fn seo(canonical_url: impl Into<String>) -> Self {
        Self {
            canonical_url: Some(canonical_url.into()),
            open_graph: true,
            twitter: true,
            structured_data: true,
            ..Self::default()
        }
    }

    /// Set a fallback title.
    #[must_use]
    pub fn with_title(mut self, title: impl Into<String>) -> Self {
        self.title = Some(title.into());
        self
    }

    /// Set a fallback description.
    #[must_use]
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.description = Some(description.into());
        self
    }

    /// Set the canonical URL.
    #[must_use]
    pub fn with_canonical_url(mut self, canonical_url: impl Into<String>) -> Self {
        self.canonical_url = Some(canonical_url.into());
        self
    }

    /// Set `og:site_name`.
    #[must_use]
    pub fn with_site_name(mut self, site_name: impl Into<String>) -> Self {
        self.site_name = Some(site_name.into());
        self
    }

    /// Set `og:image` / `twitter:image`.
    #[must_use]
    pub fn with_image_url(mut self, image_url: impl Into<String>) -> Self {
        self.image_url = Some(image_url.into());
        self
    }

    /// Set `og:type`.
    #[must_use]
    pub fn with_og_type(mut self, og_type: impl Into<String>) -> Self {
        self.og_type = Some(og_type.into());
        self
    }

    /// Set `twitter:card`.
    #[must_use]
    pub fn with_twitter_card(mut self, twitter_card: impl Into<String>) -> Self {
        self.twitter_card = Some(twitter_card.into());
        self
    }

    /// Set `twitter:site`.
    #[must_use]
    pub fn with_twitter_site(mut self, twitter_site: impl Into<String>) -> Self {
        self.twitter_site = Some(twitter_site.into());
        self
    }

    /// Enable/disable Open Graph tags.
    #[must_use]
    pub fn with_open_graph(mut self, on: bool) -> Self {
        self.open_graph = on;
        self
    }

    /// Enable/disable Twitter card tags.
    #[must_use]
    pub fn with_twitter(mut self, on: bool) -> Self {
        self.twitter = on;
        self
    }

    /// Enable/disable JSON-LD structured data.
    #[must_use]
    pub fn with_structured_data(mut self, on: bool) -> Self {
        self.structured_data = on;
        self
    }
}

/// The effective title/description for a render: window metadata wins, then
/// the host fallback, then the renderer default title.
pub(crate) fn resolve(window: &WindowMeta, meta: &DocumentMeta) -> (String, Option<String>) {
    let title = window
        .title
        .clone()
        .filter(|value| !value.is_empty())
        .or_else(|| meta.title.clone().filter(|value| !value.is_empty()))
        .unwrap_or_else(|| DEFAULT_TITLE.to_string());
    let description = window
        .description
        .clone()
        .filter(|value| !value.is_empty())
        .or_else(|| meta.description.clone().filter(|value| !value.is_empty()));
    (title, description)
}

/// The image used by `og:image` / `twitter:image` / Article JSON-LD: the
/// explicit host value, else the first `IMAGE` source in the rendered tree.
pub(crate) fn image_url(
    meta: &DocumentMeta,
    nodes: &BTreeMap<u32, Node>,
    root: u32,
) -> Option<String> {
    meta.image_url
        .clone()
        .filter(|value| !value.is_empty())
        .or_else(|| first_image_source(nodes, root).map(str::to_string))
}

/// The canonical link (emitted independently of the Open Graph toggle).
fn canonical_tag(meta: &DocumentMeta) -> String {
    meta.canonical_url
        .as_deref()
        .filter(|url| !url.is_empty())
        .map(|url| {
            format!(
                "<link rel=\"canonical\" href=\"{}\">\n",
                escape_html_attr(url)
            )
        })
        .unwrap_or_default()
}

/// Canonical + Open Graph + Twitter tags for the document head, in a fixed
/// order shared with the Java renderer.
pub(crate) fn head_tags(
    meta: &DocumentMeta,
    title: &str,
    description: Option<&str>,
    locale: Option<&str>,
    image: Option<&str>,
) -> String {
    let mut out = canonical_tag(meta);
    let canonical = meta.canonical_url.as_deref().filter(|url| !url.is_empty());

    if meta.open_graph {
        let og_type = meta
            .og_type
            .as_deref()
            .filter(|value| !value.is_empty())
            .unwrap_or("website");
        out.push_str(&format!(
            "<meta property=\"og:type\" content=\"{}\">\n",
            escape_html_attr(og_type)
        ));
        out.push_str(&format!(
            "<meta property=\"og:title\" content=\"{}\">\n",
            escape_html_attr(title)
        ));
        if let Some(description) = description {
            out.push_str(&format!(
                "<meta property=\"og:description\" content=\"{}\">\n",
                escape_html_attr(description)
            ));
        }
        if let Some(canonical) = canonical {
            out.push_str(&format!(
                "<meta property=\"og:url\" content=\"{}\">\n",
                escape_html_attr(canonical)
            ));
        }
        if let Some(site_name) = meta.site_name.as_deref().filter(|v| !v.is_empty()) {
            out.push_str(&format!(
                "<meta property=\"og:site_name\" content=\"{}\">\n",
                escape_html_attr(site_name)
            ));
        }
        if let Some(image) = image.filter(|v| !v.is_empty()) {
            out.push_str(&format!(
                "<meta property=\"og:image\" content=\"{}\">\n",
                escape_html_attr(image)
            ));
        }
        if let Some(locale) = locale.filter(|v| !v.is_empty()) {
            out.push_str(&format!(
                "<meta property=\"og:locale\" content=\"{}\">\n",
                escape_html_attr(locale)
            ));
        }
    }

    if meta.twitter {
        let card = meta
            .twitter_card
            .as_deref()
            .filter(|v| !v.is_empty())
            .unwrap_or(if image.is_some() {
                "summary_large_image"
            } else {
                "summary"
            });
        out.push_str(&format!(
            "<meta name=\"twitter:card\" content=\"{}\">\n",
            escape_html_attr(card)
        ));
        out.push_str(&format!(
            "<meta name=\"twitter:title\" content=\"{}\">\n",
            escape_html_attr(title)
        ));
        if let Some(description) = description {
            out.push_str(&format!(
                "<meta name=\"twitter:description\" content=\"{}\">\n",
                escape_html_attr(description)
            ));
        }
        if let Some(image) = image.filter(|v| !v.is_empty()) {
            out.push_str(&format!(
                "<meta name=\"twitter:image\" content=\"{}\">\n",
                escape_html_attr(image)
            ));
        }
        if let Some(site) = meta.twitter_site.as_deref().filter(|v| !v.is_empty()) {
            out.push_str(&format!(
                "<meta name=\"twitter:site\" content=\"{}\">\n",
                escape_html_attr(site)
            ));
        }
    }

    out
}

/// Resolve a possibly root-relative asset URL against the canonical origin.
pub(crate) fn resolve_url(canonical: Option<&str>, url: &str) -> String {
    if url.is_empty() {
        return String::new();
    }
    if url.starts_with("http://") || url.starts_with("https://") || url.starts_with("//") {
        return url.to_string();
    }
    if let (Some(canonical), true) = (canonical, url.starts_with('/')) {
        if let Some(origin) = origin_of(canonical) {
            return format!("{origin}{url}");
        }
    }
    url.to_string()
}

/// The `scheme://authority` prefix of an absolute URL.
fn origin_of(url: &str) -> Option<&str> {
    let scheme = url.find("://")?;
    let rest = &url[scheme + 3..];
    let end = rest.find('/').map_or(url.len(), |i| scheme + 3 + i);
    Some(&url[..end])
}

/// Escape a value for embedding inside a JSON string (no surrounding quotes).
/// `<`, `>`, and `&` are unicode-escaped so application text can never close
/// the surrounding `<script>` element.
pub(crate) fn json_escape(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\u{08}' => out.push_str("\\b"),
            '\u{0C}' => out.push_str("\\f"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '<' => out.push_str("\\u003C"),
            '>' => out.push_str("\\u003E"),
            '&' => out.push_str("\\u0026"),
            '\u{2028}' => out.push_str("\\u2028"),
            '\u{2029}' => out.push_str("\\u2029"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04X}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

/// The first `IMAGE` component's `IMAGE_SOURCE` in document order (depth-first
/// from `root`).
pub(crate) fn first_image_source<'a>(nodes: &'a BTreeMap<u32, Node>, root: u32) -> Option<&'a str> {
    let node = nodes.get(&root)?;
    if node.component == component_type::IMAGE {
        if let Some(src) = node.strings.get(&property_id::IMAGE_SOURCE) {
            if !src.is_empty() {
                return Some(src);
            }
        }
    }
    node.children
        .iter()
        .find_map(|child| first_image_source(nodes, *child))
}
