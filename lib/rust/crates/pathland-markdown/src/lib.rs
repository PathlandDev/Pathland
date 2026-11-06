//! Shared inline Markdown parser for Pathland.
//!
//! Parses a small inline subset into a
//! [`pathland_core::AttributedString`]:
//!
//! - `**bold**` / `__bold__` → `FONT_WEIGHT` 700
//! - `*italic*` / `_italic_` → `FONT_STYLE` Italic
//! - `` `code` `` → `FONT_DESIGN` Monospaced
//! - `~~strikethrough~~` → `STRIKETHROUGH` 1
//! - `[label](url)` → link run (label text is parsed recursively)
//! - `\` escapes the next character
//!
//! Intraword underscores stay literal (`snake_case_name`), matching CommonMark
//! emphasis rules.
//!
//! The parser is `no_std` + `alloc` and is shared by every DSL: the Rust DSL
//! calls [`parse`] directly; foreign hosts (Java/JNA) call the C ABI exported
//! by the `pathland-markdown-capi` cdylib. Block-level Markdown, images, and
//! reference links are out of scope for v1.

#![no_std]

extern crate alloc;

#[cfg(test)]
extern crate std;

use alloc::string::String;
use alloc::vec::Vec;
use pathland_core::{AttributedString, LinkRun, StyleRun};

/// Parse inline Markdown into an [`AttributedString`].
///
/// Returns `None` only if the produced ranges fail the model's validation
/// (which should not happen for well-formed UTF-8 input; the parser never
/// splits code points).
#[must_use]
pub fn parse(input: &str) -> Option<AttributedString> {
    let mut text = String::new();
    let mut runs = Vec::new();
    let mut links = Vec::new();
    parse_into(input, &mut text, &mut runs, &mut links);
    AttributedString::new(text, runs, links).ok()
}

fn parse_into(input: &str, text: &mut String, runs: &mut Vec<StyleRun>, links: &mut Vec<LinkRun>) {
    let bytes = input.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'\\' => {
                let next = next_char(input, i + 1);
                match next {
                    Some((ch, end)) => {
                        text.push(ch);
                        i = end;
                    }
                    None => {
                        text.push('\\');
                        i += 1;
                    }
                }
            }
            b'*' if bytes.get(i + 1) == Some(&b'*') => {
                if let Some(end) = find_delimiter(input, i + 2, "**") {
                    let start = text.len();
                    parse_into(&input[i + 2..end], text, runs, links);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).bold());
                    i = end + 2;
                } else {
                    text.push('*');
                    i += 1;
                }
            }
            b'*' => {
                if let Some(end) = find_delimiter(input, i + 1, "*") {
                    let start = text.len();
                    parse_into(&input[i + 1..end], text, runs, links);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).italic());
                    i = end + 1;
                } else {
                    text.push('*');
                    i += 1;
                }
            }
            b'_' if bytes.get(i + 1) == Some(&b'_') && underscore_can_open(input, i) => {
                if let Some(end) = find_delimiter(input, i + 2, "__") {
                    let start = text.len();
                    parse_into(&input[i + 2..end], text, runs, links);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).bold());
                    i = end + 2;
                } else {
                    text.push('_');
                    i += 1;
                }
            }
            b'_' if underscore_can_open(input, i) => {
                if let Some(end) = find_delimiter(input, i + 1, "_") {
                    let start = text.len();
                    parse_into(&input[i + 1..end], text, runs, links);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).italic());
                    i = end + 1;
                } else {
                    text.push('_');
                    i += 1;
                }
            }
            b'~' if bytes.get(i + 1) == Some(&b'~') => {
                if let Some(end) = find_delimiter(input, i + 2, "~~") {
                    let start = text.len();
                    parse_into(&input[i + 2..end], text, runs, links);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).strikethrough());
                    i = end + 2;
                } else {
                    text.push('~');
                    i += 1;
                }
            }
            b'`' => {
                if let Some(end) = find_delimiter(input, i + 1, "`") {
                    let start = text.len();
                    text.push_str(&input[i + 1..end]);
                    runs.push(StyleRun::new(byte_len(start), byte_len(text.len())).monospaced());
                    i = end + 1;
                } else {
                    text.push('`');
                    i += 1;
                }
            }
            b'[' => {
                if let Some((label_end, url, after)) = parse_link(input, i) {
                    let start = text.len();
                    parse_into(&input[i + 1..label_end], text, runs, links);
                    links.push(LinkRun::new(byte_len(start), byte_len(text.len()), url));
                    i = after;
                } else {
                    text.push('[');
                    i += 1;
                }
            }
            _ => {
                let (ch, end) = next_char(input, i).expect("valid utf-8 boundary");
                text.push(ch);
                i = end;
            }
        }
    }
}

fn byte_len(value: usize) -> u32 {
    u32::try_from(value).unwrap_or(u32::MAX)
}

/// Underscore emphasis is not allowed intraword (`snake_case` stays literal).
fn underscore_can_open(input: &str, at: usize) -> bool {
    input[..at]
        .chars()
        .next_back()
        .is_none_or(|c| !c.is_alphanumeric())
}

fn underscore_can_close(input: &str, after_run: usize) -> bool {
    input[after_run..]
        .chars()
        .next()
        .is_none_or(|c| !c.is_alphanumeric())
}

/// Find the next closing delimiter at or after `from`, returning its byte
/// offset. Escaped characters are skipped.
///
/// Asterisk runs are split so nesting works: for a run of three (`***`) the
/// outer `**` close uses the last two stars and the inner `*` close uses the
/// first. A lone `*` never closes on a `**` run (that is nested bold).
fn find_delimiter(input: &str, from: usize, needle: &str) -> Option<usize> {
    let bytes = input.as_bytes();
    let mut i = from;
    while i < bytes.len() {
        if bytes[i] == b'\\' {
            i += 2;
            continue;
        }
        match needle {
            "**" => {
                if bytes[i] == b'*' {
                    let mut end = i;
                    while end < bytes.len() && bytes[end] == b'*' {
                        end += 1;
                    }
                    if end - i >= 2 {
                        return Some(end - 2);
                    }
                }
            }
            "*" => {
                if bytes[i] == b'*' {
                    let mut end = i;
                    while end < bytes.len() && bytes[end] == b'*' {
                        end += 1;
                    }
                    if end - i == 1 {
                        return Some(i);
                    }
                    i = end;
                    continue;
                }
            }
            "__" => {
                if bytes[i] == b'_' {
                    let mut end = i;
                    while end < bytes.len() && bytes[end] == b'_' {
                        end += 1;
                    }
                    if end - i >= 2 && underscore_can_close(input, end) {
                        return Some(end - 2);
                    }
                }
            }
            "_" => {
                if bytes[i] == b'_' {
                    let mut end = i;
                    while end < bytes.len() && bytes[end] == b'_' {
                        end += 1;
                    }
                    if end - i == 1 && underscore_can_close(input, end) {
                        return Some(i);
                    }
                    i = end;
                    continue;
                }
            }
            "~~" => {
                if bytes[i] == b'~' {
                    let mut end = i;
                    while end < bytes.len() && bytes[end] == b'~' {
                        end += 1;
                    }
                    if end - i >= 2 {
                        return Some(end - 2);
                    }
                }
            }
            _ => {
                if input[i..].starts_with(needle) {
                    return Some(i);
                }
            }
        }
        i = next_char(input, i)?.1;
    }
    None
}

/// Parse `[label](url)` at `open`. Returns `(label_end, url, after_close)`.
fn parse_link(input: &str, open: usize) -> Option<(usize, &str, usize)> {
    let bytes = input.as_bytes();
    let mut i = open + 1;
    while i < bytes.len() {
        if bytes[i] == b'\\' {
            i += 2;
            continue;
        }
        if bytes[i] == b']' && bytes.get(i + 1) == Some(&b'(') {
            let label_end = i;
            let url_start = i + 2;
            let mut j = url_start;
            while j < bytes.len() {
                if bytes[j] == b'\\' {
                    j += 2;
                    continue;
                }
                if bytes[j] == b')' {
                    return Some((label_end, &input[url_start..j], j + 1));
                }
                j = next_char(input, j)?.1;
            }
            return None;
        }
        i = next_char(input, i)?.1;
    }
    None
}

fn next_char(input: &str, at: usize) -> Option<(char, usize)> {
    let ch = input[at..].chars().next()?;
    Some((ch, at + ch.len_utf8()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::{property_id, value_type};

    fn decoded(markdown: &str) -> AttributedString {
        parse(markdown).expect("parser output is valid")
    }

    #[test]
    fn plain_text_has_no_runs() {
        let value = decoded("hello");
        assert_eq!(value.text(), "hello");
        assert!(value.runs().is_empty());
        assert!(value.links().is_empty());
    }

    #[test]
    fn maps_each_inline_marker() {
        let value = decoded("**b** *i* `c` ~~s~~");
        assert_eq!(value.text(), "b i c s");
        assert_eq!(value.runs().len(), 4);
        assert_eq!(value.runs()[0].start, 0);
        assert_eq!(value.runs()[0].end, 1);
        assert!(value.runs()[0]
            .attributes
            .iter()
            .any(|a| { a.property == property_id::FONT_WEIGHT && a.value == 700.0f32.to_bits() }));
        assert!(value.runs()[1]
            .attributes
            .iter()
            .any(|a| { a.property == property_id::FONT_STYLE && a.value == 1.0f32.to_bits() }));
        assert!(value.runs()[2]
            .attributes
            .iter()
            .any(|a| { a.property == property_id::FONT_DESIGN && a.value == 3.0f32.to_bits() }));
        assert!(value.runs()[3].attributes.iter().any(|a| {
            a.property == property_id::STRIKETHROUGH && a.value_type == value_type::U8
        }));
    }

    #[test]
    fn nests_formats() {
        let value = decoded("**bold *and italic***");
        assert_eq!(value.text(), "bold and italic");
        assert_eq!(value.runs().len(), 2);
        assert_eq!(value.runs()[0].start, 5);
        assert_eq!(value.runs()[0].end, 15);
        assert_eq!(value.runs()[1].start, 0);
        assert_eq!(value.runs()[1].end, 15);
    }

    #[test]
    fn links_carry_label_runs_and_url() {
        let value = decoded("[**hi**](https://example.com)");
        assert_eq!(value.text(), "hi");
        assert_eq!(value.links().len(), 1);
        assert_eq!(value.links()[0].url, "https://example.com");
        assert_eq!(value.links()[0].start, 0);
        assert_eq!(value.links()[0].end, 2);
        assert_eq!(value.runs().len(), 1);
    }

    #[test]
    fn code_spans_do_not_parse_inner_markup() {
        let value = decoded("`**not bold**`");
        assert_eq!(value.text(), "**not bold**");
        assert_eq!(value.runs().len(), 1);
        assert_eq!(
            value.runs()[0].attributes[0].property,
            property_id::FONT_DESIGN
        );
    }

    #[test]
    fn underscore_emphasis_matches_asterisk_forms() {
        let value = decoded("_italic_ and __bold__");
        assert_eq!(value.text(), "italic and bold");
        assert_eq!(value.runs().len(), 2);
        assert!(value.runs()[0]
            .attributes
            .iter()
            .any(|a| a.property == property_id::FONT_STYLE));
        assert!(value.runs()[1]
            .attributes
            .iter()
            .any(|a| a.property == property_id::FONT_WEIGHT));
    }

    #[test]
    fn intraword_underscores_stay_literal() {
        let value = decoded("snake_case_name");
        assert_eq!(value.text(), "snake_case_name");
        assert!(value.runs().is_empty());
    }

    #[test]
    fn escaped_underscores_are_literal() {
        let value = decoded(r"\_not italic\_");
        assert_eq!(value.text(), "_not italic_");
        assert!(value.runs().is_empty());
    }

    #[test]
    fn escapes_are_literal() {
        let value = decoded(r"\*not italic\*");
        assert_eq!(value.text(), "*not italic*");
        assert!(value.runs().is_empty());
    }

    #[test]
    fn unmatched_markers_stay_literal() {
        let value = decoded("a * b ** c");
        assert_eq!(value.text(), "a * b ** c");
        assert!(value.runs().is_empty());
    }

    #[test]
    fn utf8_offsets_are_bytes() {
        let value = decoded("é**ü**");
        assert_eq!(value.text(), "éü");
        assert_eq!(value.runs()[0].start, 2);
        assert_eq!(value.runs()[0].end, 4);
    }
}
