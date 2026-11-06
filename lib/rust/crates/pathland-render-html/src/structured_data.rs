//! Component-derived JSON-LD structured data (`schema.org`).
//!
//! The document always contributes a `WebPage` node (name/description/url/
//! language), and every `ROLE_ARTICLE` region contributes an `Article` node
//! (headline from the first heading, description from the first paragraph,
//! image from the first `IMAGE` source). The mapping is deterministic and
//! deliberately conservative — the protocol has no schema-specific component,
//! so the renderer only publishes semantics the application already expressed
//! through `ROLE`/`TEXT_STYLE`.

use std::collections::BTreeMap;

use pathland_core::{component_type, role, WindowMeta};

use crate::{document, role_spec, DocumentMeta, Node};

/// Render the JSON-LD payload for `root`, or `None` when structured data is
/// disabled.
pub(crate) fn render_json_ld(
    meta: &DocumentMeta,
    window: &WindowMeta,
    nodes: &BTreeMap<u32, Node>,
    root: u32,
) -> Option<String> {
    if !meta.structured_data {
        return None;
    }
    let (title, description) = document::resolve(window, meta);
    let canonical = meta.canonical_url.as_deref().filter(|url| !url.is_empty());
    let locale = window.locale.as_deref().filter(|value| !value.is_empty());

    let mut graph = Vec::new();
    let mut web_page = String::from("{\"@type\":\"WebPage\"");
    push_string_field(&mut web_page, "name", &title);
    if let Some(description) = description.as_deref().filter(|value| !value.is_empty()) {
        push_string_field(&mut web_page, "description", description);
    }
    if let Some(canonical) = canonical {
        push_string_field(&mut web_page, "url", canonical);
    }
    if let Some(locale) = locale {
        push_string_field(&mut web_page, "inLanguage", locale);
    }
    web_page.push('}');
    graph.push(web_page);

    for id in article_ids(nodes, root) {
        if let Some(article) = article_json(nodes, id, canonical, locale) {
            graph.push(article);
        }
    }

    Some(format!(
        "<script type=\"application/ld+json\">{{\"@context\":\"https://schema.org\",\"@graph\":[{}]}}</script>\n",
        graph.join(",")
    ))
}

/// Every `ROLE_ARTICLE` node in depth-first document order (including `root`).
fn article_ids(nodes: &BTreeMap<u32, Node>, root: u32) -> Vec<u32> {
    let mut ids = Vec::new();
    collect_articles(nodes, root, &mut ids);
    ids
}

fn collect_articles(nodes: &BTreeMap<u32, Node>, id: u32, out: &mut Vec<u32>) {
    let Some(node) = nodes.get(&id) else {
        return;
    };
    if node.role_code() == role::ARTICLE {
        out.push(id);
    }
    for child in &node.children {
        collect_articles(nodes, *child, out);
    }
}

fn article_json(
    nodes: &BTreeMap<u32, Node>,
    id: u32,
    canonical: Option<&str>,
    locale: Option<&str>,
) -> Option<String> {
    let headline = find_heading(nodes, id);
    let description = find_paragraph(nodes, id);
    let image = document::first_image_source(nodes, id)
        .map(|src| document::resolve_url(canonical, src))
        .filter(|src| !src.is_empty());
    if headline.is_none() && description.is_none() && image.is_none() {
        return None;
    }

    let mut article = String::from("{\"@type\":\"Article\"");
    if let Some(headline) = headline.as_deref().filter(|value| !value.is_empty()) {
        push_string_field(&mut article, "headline", headline);
    }
    if let Some(description) = description.as_deref().filter(|value| !value.is_empty()) {
        push_string_field(&mut article, "description", description);
    }
    if let Some(image) = image.as_deref().filter(|value| !value.is_empty()) {
        push_string_field(&mut article, "image", image);
    }
    if let Some(canonical) = canonical {
        article.push_str(",\"mainEntityOfPage\":{\"@type\":\"WebPage\",\"@id\":\"");
        article.push_str(&document::json_escape(canonical));
        article.push_str("\"}");
    }
    if let Some(locale) = locale {
        push_string_field(&mut article, "inLanguage", locale);
    }
    article.push('}');
    Some(article)
}

fn find_heading(nodes: &BTreeMap<u32, Node>, id: u32) -> Option<String> {
    let node = nodes.get(&id)?;
    if node.component == component_type::TEXT {
        let is_heading = node
            .text_style_code()
            .and_then(role_spec::heading_level)
            .is_some()
            || node.role_code() == role::HEADER;
        if is_heading {
            if let Some(text) = node.text.as_deref().filter(|value| !value.is_empty()) {
                return Some(text.to_string());
            }
        }
    }
    node.children
        .iter()
        .find_map(|child| find_heading(nodes, *child))
}

fn find_paragraph(nodes: &BTreeMap<u32, Node>, id: u32) -> Option<String> {
    let node = nodes.get(&id)?;
    if node.component == component_type::TEXT && node.role_code() == role::PARAGRAPH {
        if let Some(text) = node.text.as_deref().filter(|value| !value.is_empty()) {
            return Some(text.to_string());
        }
    }
    if let Some(text) = node
        .children
        .iter()
        .find_map(|child| find_paragraph(nodes, *child))
    {
        return Some(text);
    }
    // No semantic paragraph: use the first non-heading text as a conservative
    // fallback, so a plain (unlabelled) body still contributes a description.
    find_first_body_text(nodes, id)
}

fn find_first_body_text(nodes: &BTreeMap<u32, Node>, id: u32) -> Option<String> {
    let node = nodes.get(&id)?;
    if node.component == component_type::TEXT {
        let is_heading = node
            .text_style_code()
            .and_then(role_spec::heading_level)
            .is_some()
            || node.role_code() == role::HEADER;
        if !is_heading {
            if let Some(text) = node.text.as_deref().filter(|value| !value.is_empty()) {
                return Some(text.to_string());
            }
        }
    }
    node.children
        .iter()
        .find_map(|child| find_first_body_text(nodes, *child))
}

/// Append `,"name":"value"` (the caller opens the object with `{"@type":...`).
fn push_string_field(out: &mut String, name: &str, value: &str) {
    out.push_str(",\"");
    out.push_str(name);
    out.push_str("\":\"");
    out.push_str(&document::json_escape(value));
    out.push('"');
}
