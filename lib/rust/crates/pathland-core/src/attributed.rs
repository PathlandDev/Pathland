//! Attributed text: a node's text plus range-scoped style runs and links.
//!
//! This is the payload carried by `PARAMETER::SET_ATTRIBUTED_TEXT` (see
//! `spec/OPCODE.md`). The text is UTF-8; run/link ranges are UTF-8 **byte**
//! offsets (half-open, `[start, end)`). The payload is arena-backed: the
//! opcode references one length-prefixed entry whose bytes are produced by
//! [`AttributedString::encode`].
//!
//! V1 style attributes are scalar text-format properties only:
//! `FONT_SIZE`, `FONT_WEIGHT`, `COLOR`, `FONT_STYLE`, `FONT_DESIGN`,
//! `UNDERLINE`, `STRIKETHROUGH`. Unknown properties/types are ignored on decode
//! so a newer producer never breaks an older renderer.

use alloc::string::String;
use alloc::vec::Vec;

use crate::{property_id, value_type};

/// An error raised while building, encoding, or decoding attributed text.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AttributedError {
    /// A range's start is after its end.
    InvalidRange,
    /// A range extends past the text or splits a UTF-8 code point.
    RangeOutOfBounds,
    /// The text is longer than the wire's `u32` length field.
    TextTooLong,
    /// The payload is truncated or malformed.
    Malformed,
    /// The text (or a link URL) is not valid UTF-8.
    InvalidUtf8,
}

impl core::fmt::Display for AttributedError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.write_str(match self {
            AttributedError::InvalidRange => "invalid attributed-text range",
            AttributedError::RangeOutOfBounds => "attributed-text range out of bounds",
            AttributedError::TextTooLong => "attributed text exceeds the u32 wire length",
            AttributedError::Malformed => "malformed attributed-text payload",
            AttributedError::InvalidUtf8 => "attributed text is not valid UTF-8",
        })
    }
}

/// A single scalar text-format attribute on a range.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TextAttribute {
    /// The property id (a text-format id from [`property_id`]).
    pub property: u16,
    /// The standard wire value type (`F32`, `COLOR`, `U8`, …).
    pub value_type: u8,
    /// The property's 4-byte wire value (bit pattern for `F32`).
    pub value: u32,
}

impl TextAttribute {
    /// Construct an attribute from raw wire parts.
    #[must_use]
    pub const fn new(property: u16, value_type: u8, value: u32) -> Self {
        Self {
            property,
            value_type,
            value,
        }
    }

    /// `FONT_SIZE` (points).
    #[must_use]
    pub fn font_size(points: f32) -> Self {
        Self::new(property_id::FONT_SIZE, value_type::F32, points.to_bits())
    }

    /// `FONT_WEIGHT` (100–900).
    #[must_use]
    pub fn font_weight(weight: f32) -> Self {
        Self::new(property_id::FONT_WEIGHT, value_type::F32, weight.to_bits())
    }

    /// `COLOR` (`0xAARRGGBB`).
    #[must_use]
    pub const fn color(argb: u32) -> Self {
        Self::new(property_id::COLOR, value_type::COLOR, argb)
    }

    /// `FONT_STYLE = Italic`.
    #[must_use]
    pub fn italic() -> Self {
        Self::new(property_id::FONT_STYLE, value_type::F32, 1.0f32.to_bits())
    }

    /// `FONT_STYLE = Normal`.
    #[must_use]
    pub fn normal_style() -> Self {
        Self::new(property_id::FONT_STYLE, value_type::F32, 0.0f32.to_bits())
    }

    /// `FONT_DESIGN` by enum code (`Default`=0, `Serif`=1, `Rounded`=2,
    /// `Monospaced`=3).
    #[must_use]
    pub fn font_design(code: u8) -> Self {
        Self::new(
            property_id::FONT_DESIGN,
            value_type::F32,
            f32::from(code).to_bits(),
        )
    }

    /// `UNDERLINE` (`0`/`1`).
    #[must_use]
    pub const fn underline(on: bool) -> Self {
        Self::new(property_id::UNDERLINE, value_type::U8, on as u32)
    }

    /// `STRIKETHROUGH` (`0`/`1`).
    #[must_use]
    pub const fn strikethrough(on: bool) -> Self {
        Self::new(property_id::STRIKETHROUGH, value_type::U8, on as u32)
    }
}

/// A range of the text with a set of scalar attributes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StyleRun {
    /// Inclusive UTF-8 byte start.
    pub start: u32,
    /// Exclusive UTF-8 byte end.
    pub end: u32,
    /// The attributes applied to the range.
    pub attributes: Vec<TextAttribute>,
}

impl StyleRun {
    /// Create an empty style run. Ranges are validated against the text by
    /// [`AttributedString::new`]/[`AttributedString::push_run`].
    #[must_use]
    pub fn new(start: u32, end: u32) -> Self {
        Self {
            start,
            end,
            attributes: Vec::new(),
        }
    }

    /// Add an attribute (builder style).
    #[must_use]
    pub fn with(mut self, attribute: TextAttribute) -> Self {
        self.attributes.push(attribute);
        self
    }

    /// Mark the range bold.
    #[must_use]
    pub fn bold(self) -> Self {
        self.with(TextAttribute::font_weight(700.0))
    }

    /// Mark the range italic.
    #[must_use]
    pub fn italic(self) -> Self {
        self.with(TextAttribute::italic())
    }

    /// Mark the range monospaced.
    #[must_use]
    pub fn monospaced(self) -> Self {
        self.with(TextAttribute::font_design(3))
    }

    /// Mark the range struck through.
    #[must_use]
    pub fn strikethrough(self) -> Self {
        self.with(TextAttribute::strikethrough(true))
    }

    /// Mark the range underlined.
    #[must_use]
    pub fn underline(self) -> Self {
        self.with(TextAttribute::underline(true))
    }
}

/// A range of the text that is a hyperlink.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LinkRun {
    /// Inclusive UTF-8 byte start.
    pub start: u32,
    /// Exclusive UTF-8 byte end.
    pub end: u32,
    /// The destination URL.
    pub url: String,
}

impl LinkRun {
    /// Create a link range.
    #[must_use]
    pub fn new(start: u32, end: u32, url: impl Into<String>) -> Self {
        Self {
            start,
            end,
            url: url.into(),
        }
    }
}

/// A non-overlapping slice of an [`AttributedString`] with its effective
/// attributes and link. Produced by [`AttributedString::segments`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AttributedSegment<'a> {
    /// Inclusive UTF-8 byte start.
    pub start: u32,
    /// Exclusive UTF-8 byte end.
    pub end: u32,
    /// Effective attributes for this slice (later covering runs win).
    pub attributes: Vec<TextAttribute>,
    /// The effective link URL, if this slice is linked.
    pub link: Option<&'a str>,
}

/// Text with optional style runs and links.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct AttributedString {
    text: String,
    runs: Vec<StyleRun>,
    links: Vec<LinkRun>,
}

impl AttributedString {
    /// Create plain text with no runs or links.
    #[must_use]
    pub fn plain(text: impl Into<String>) -> Self {
        Self {
            text: text.into(),
            runs: Vec::new(),
            links: Vec::new(),
        }
    }

    /// Create an attributed string from parts. Validates every range against
    /// the text (bounds + UTF-8 boundaries).
    pub fn new(
        text: impl Into<String>,
        runs: Vec<StyleRun>,
        links: Vec<LinkRun>,
    ) -> Result<Self, AttributedError> {
        let value = Self {
            text: text.into(),
            runs,
            links,
        };
        value.validate()?;
        Ok(value)
    }

    /// The plain text.
    #[must_use]
    pub fn text(&self) -> &str {
        &self.text
    }

    /// The style runs in wire order.
    #[must_use]
    pub fn runs(&self) -> &[StyleRun] {
        &self.runs
    }

    /// The links in wire order.
    #[must_use]
    pub fn links(&self) -> &[LinkRun] {
        &self.links
    }

    /// Whether this carries no runs or links.
    #[must_use]
    pub fn is_plain(&self) -> bool {
        self.runs.is_empty() && self.links.is_empty()
    }

    /// Append a style run, validating it against the current text.
    pub fn push_run(&mut self, run: StyleRun) -> Result<(), AttributedError> {
        validate_range(&self.text, run.start, run.end)?;
        self.runs.push(run);
        Ok(())
    }

    /// Append a link, validating it against the current text.
    pub fn push_link(&mut self, link: LinkRun) -> Result<(), AttributedError> {
        validate_range(&self.text, link.start, link.end)?;
        self.links.push(link);
        Ok(())
    }

    /// Validate all ranges.
    pub fn validate(&self) -> Result<(), AttributedError> {
        if self.text.len() > u32::MAX as usize {
            return Err(AttributedError::TextTooLong);
        }
        for run in &self.runs {
            validate_range(&self.text, run.start, run.end)?;
        }
        for link in &self.links {
            validate_range(&self.text, link.start, link.end)?;
        }
        Ok(())
    }

    /// Encode the arena payload (without the outer `[u32 byteLength]` wrapper
    /// the arena adds). The caller allocates the returned bytes in the arena or
    /// the network string section.
    pub fn encode(&self) -> Result<Vec<u8>, AttributedError> {
        self.validate()?;
        let mut out = Vec::with_capacity(self.wire_len());
        self.encode_into(&mut out)?;
        Ok(out)
    }

    /// Encode into an existing buffer.
    pub fn encode_into(&self, out: &mut Vec<u8>) -> Result<(), AttributedError> {
        self.validate()?;
        let text_len = u32::try_from(self.text.len()).map_err(|_| AttributedError::TextTooLong)?;
        put_u32(out, text_len);
        out.extend_from_slice(self.text.as_bytes());

        put_u32(
            out,
            u32::try_from(self.runs.len()).map_err(|_| AttributedError::Malformed)?,
        );
        for run in &self.runs {
            put_u32(out, run.start);
            put_u32(out, run.end);
            let supported: Vec<&TextAttribute> =
                run.attributes.iter().filter(|a| is_supported(a)).collect();
            put_u32(
                out,
                u32::try_from(supported.len()).map_err(|_| AttributedError::Malformed)?,
            );
            for attr in supported {
                put_u16(out, attr.property);
                out.push(attr.value_type);
                out.push(0); // reserved
                put_u32(out, attr.value);
            }
        }

        put_u32(
            out,
            u32::try_from(self.links.len()).map_err(|_| AttributedError::Malformed)?,
        );
        for link in &self.links {
            put_u32(out, link.start);
            put_u32(out, link.end);
            put_u32(
                out,
                u32::try_from(link.url.len()).map_err(|_| AttributedError::Malformed)?,
            );
            out.extend_from_slice(link.url.as_bytes());
        }
        Ok(())
    }

    /// Decode an arena payload (the bytes inside the arena's length wrapper).
    pub fn decode(bytes: &[u8]) -> Result<Self, AttributedError> {
        let mut r = Reader::new(bytes);
        let text_len = r.u32()? as usize;
        let text_bytes = r.take(text_len)?;
        let text = core::str::from_utf8(text_bytes).map_err(|_| AttributedError::InvalidUtf8)?;

        let run_count = r.u32()?;
        let mut runs = Vec::new();
        for _ in 0..run_count {
            let start = r.u32()?;
            let end = r.u32()?;
            let attr_count = r.u32()?;
            validate_range(text, start, end)?;
            let mut attributes = Vec::new();
            for _ in 0..attr_count {
                let property = r.u16()?;
                let value_type = r.u8()?;
                let _reserved = r.u8()?;
                let value = r.u32()?;
                let attr = TextAttribute::new(property, value_type, value);
                if is_supported(&attr) {
                    attributes.push(attr);
                }
            }
            runs.push(StyleRun {
                start,
                end,
                attributes,
            });
        }

        let link_count = r.u32()?;
        let mut links = Vec::new();
        for _ in 0..link_count {
            let start = r.u32()?;
            let end = r.u32()?;
            let url_len = r.u32()? as usize;
            validate_range(text, start, end)?;
            let url =
                core::str::from_utf8(r.take(url_len)?).map_err(|_| AttributedError::InvalidUtf8)?;
            links.push(LinkRun {
                start,
                end,
                url: url.into(),
            });
        }

        if !r.is_empty() {
            return Err(AttributedError::Malformed);
        }
        Ok(Self {
            text: text.into(),
            runs,
            links,
        })
    }

    /// Split the text into non-overlapping segments, resolving overlapping runs
    /// in wire order (later wins per property; later link wins).
    #[must_use]
    pub fn segments(&self) -> Vec<AttributedSegment<'_>> {
        let mut boundaries: Vec<u32> =
            Vec::with_capacity(2 + self.runs.len() * 2 + self.links.len() * 2);
        boundaries.push(0);
        boundaries.push(self.text.len() as u32);
        for run in &self.runs {
            boundaries.push(run.start);
            boundaries.push(run.end);
        }
        for link in &self.links {
            boundaries.push(link.start);
            boundaries.push(link.end);
        }
        boundaries.sort_unstable();
        boundaries.dedup();

        let mut segments = Vec::with_capacity(boundaries.len().saturating_sub(1));
        for pair in boundaries.windows(2) {
            let (start, end) = (pair[0], pair[1]);
            if start == end {
                continue;
            }
            let mut attributes: Vec<TextAttribute> = Vec::new();
            for run in &self.runs {
                if run.start <= start && run.end >= end {
                    for attr in &run.attributes {
                        if let Some(existing) =
                            attributes.iter_mut().find(|a| a.property == attr.property)
                        {
                            *existing = *attr;
                        } else {
                            attributes.push(*attr);
                        }
                    }
                }
            }
            let mut link = None;
            for candidate in &self.links {
                if candidate.start <= start && candidate.end >= end {
                    link = Some(candidate.url.as_str());
                }
            }
            segments.push(AttributedSegment {
                start,
                end,
                attributes,
                link,
            });
        }
        segments
    }

    fn wire_len(&self) -> usize {
        let mut len = 4 + self.text.len() + 4;
        for run in &self.runs {
            len += 12;
            len += run.attributes.iter().filter(|a| is_supported(a)).count() * 8;
        }
        len += 4;
        for link in &self.links {
            len += 12 + link.url.len();
        }
        len
    }
}

impl From<&str> for AttributedString {
    fn from(value: &str) -> Self {
        Self::plain(value)
    }
}

impl From<String> for AttributedString {
    fn from(value: String) -> Self {
        Self::plain(value)
    }
}

/// Whether an attribute is in the v1 run vocabulary.
#[must_use]
pub fn is_supported(attr: &TextAttribute) -> bool {
    match attr.property {
        property_id::FONT_SIZE | property_id::FONT_WEIGHT => attr.value_type == value_type::F32,
        property_id::FONT_STYLE | property_id::FONT_DESIGN => {
            attr.value_type == value_type::F32 || attr.value_type == value_type::ENUM
        }
        property_id::COLOR => attr.value_type == value_type::COLOR,
        property_id::UNDERLINE | property_id::STRIKETHROUGH => attr.value_type == value_type::U8,
        _ => false,
    }
}

fn validate_range(text: &str, start: u32, end: u32) -> Result<(), AttributedError> {
    if start > end {
        return Err(AttributedError::InvalidRange);
    }
    let (start, end) = (start as usize, end as usize);
    if end > text.len() || !text.is_char_boundary(start) || !text.is_char_boundary(end) {
        return Err(AttributedError::RangeOutOfBounds);
    }
    Ok(())
}

fn put_u16(out: &mut Vec<u8>, value: u16) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn put_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_le_bytes());
}

struct Reader<'a> {
    bytes: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, pos: 0 }
    }

    fn take(&mut self, len: usize) -> Result<&'a [u8], AttributedError> {
        let end = self
            .pos
            .checked_add(len)
            .ok_or(AttributedError::Malformed)?;
        if end > self.bytes.len() {
            return Err(AttributedError::Malformed);
        }
        let slice = &self.bytes[self.pos..end];
        self.pos = end;
        Ok(slice)
    }

    fn u8(&mut self) -> Result<u8, AttributedError> {
        Ok(self.take(1)?[0])
    }

    fn u16(&mut self) -> Result<u16, AttributedError> {
        let b = self.take(2)?;
        Ok(u16::from_le_bytes([b[0], b[1]]))
    }

    fn u32(&mut self) -> Result<u32, AttributedError> {
        let b = self.take(4)?;
        Ok(u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
    }

    fn is_empty(&self) -> bool {
        self.pos == self.bytes.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec;

    fn bold_and_link() -> AttributedString {
        AttributedString::new(
            "Hi",
            vec![StyleRun::new(0, 2).bold()],
            vec![LinkRun::new(1, 2, "x")],
        )
        .unwrap()
    }

    #[test]
    fn encodes_the_conformance_payload() {
        let value = bold_and_link();
        let bytes = value.encode().unwrap();
        assert_eq!(
            bytes,
            vec![
                0x02, 0x00, 0x00, 0x00, // text len
                0x48, 0x69, // "Hi"
                0x01, 0x00, 0x00, 0x00, // run count
                0x00, 0x00, 0x00, 0x00, // start
                0x02, 0x00, 0x00, 0x00, // end
                0x01, 0x00, 0x00, 0x00, // attr count
                0x08, 0x10, 0x04, 0x00, 0x00, 0x00, 0x2F, 0x44, // FONT_WEIGHT 700
                0x01, 0x00, 0x00, 0x00, // link count
                0x01, 0x00, 0x00, 0x00, // start
                0x02, 0x00, 0x00, 0x00, // end
                0x01, 0x00, 0x00, 0x00, // url len
                0x78, // "x"
            ]
        );
    }

    #[test]
    fn round_trips() {
        let value = bold_and_link();
        assert_eq!(
            AttributedString::decode(&value.encode().unwrap()).unwrap(),
            value
        );
    }

    #[test]
    fn rejects_out_of_bounds_and_split_ranges() {
        assert_eq!(
            AttributedString::new("a", vec![StyleRun::new(0, 2)], vec![]),
            Err(AttributedError::RangeOutOfBounds)
        );
        assert_eq!(
            AttributedString::new("é", vec![StyleRun::new(1, 2)], vec![]),
            Err(AttributedError::RangeOutOfBounds)
        );
        assert_eq!(
            AttributedString::new("a", vec![StyleRun::new(1, 0)], vec![]),
            Err(AttributedError::InvalidRange)
        );
    }

    #[test]
    fn ignores_unknown_attributes_and_truncated_payloads_fail() {
        let mut value = bold_and_link();
        value.runs[0]
            .attributes
            .push(TextAttribute::new(0x9999, value_type::F32, 0));
        let bytes = value.encode().unwrap();
        let decoded = AttributedString::decode(&bytes).unwrap();
        assert_eq!(decoded.runs()[0].attributes.len(), 1);
        assert_eq!(
            AttributedString::decode(&bytes[..bytes.len() - 1]),
            Err(AttributedError::Malformed)
        );
    }

    #[test]
    fn segments_resolve_overlaps_and_links() {
        let value = AttributedString::new(
            "abcd",
            vec![
                StyleRun::new(0, 3).bold(),
                StyleRun::new(1, 4).italic(),
                StyleRun::new(1, 2).with(TextAttribute::font_weight(400.0)),
            ],
            vec![LinkRun::new(2, 4, "x")],
        )
        .unwrap();
        let segments = value.segments();
        assert_eq!(segments.len(), 4);
        assert_eq!((segments[0].start, segments[0].end), (0, 1));
        assert_eq!(segments[0].attributes.len(), 1);
        assert_eq!((segments[1].start, segments[1].end), (1, 2));
        assert_eq!(segments[1].attributes.len(), 2);
        assert!(segments[1]
            .attributes
            .iter()
            .any(|a| a.property == property_id::FONT_WEIGHT && a.value == 400.0f32.to_bits()));
        assert_eq!((segments[2].start, segments[2].end), (2, 3));
        assert_eq!(segments[2].link, Some("x"));
        assert_eq!((segments[3].start, segments[3].end), (3, 4));
        assert_eq!(segments[3].link, Some("x"));
    }

    #[test]
    fn rejects_trailing_bytes() {
        let mut bytes = bold_and_link().encode().unwrap();
        bytes.push(0);
        assert_eq!(
            AttributedString::decode(&bytes),
            Err(AttributedError::Malformed)
        );
    }

    #[test]
    fn plain_text_is_empty_payload() {
        assert!(AttributedString::plain("hi").is_plain());
        assert_eq!(
            AttributedString::decode(&AttributedString::plain("hi").encode().unwrap())
                .unwrap()
                .text(),
            "hi"
        );
    }
}
