# @pathland/view — status

**Last updated:** October 22, 2026

## Implemented

- Legacy `@view` class decorator marker.
- Retained `ViewElement` model with immutable modifier chaining.
- Primitives: `VStack`, `HStack`, `ZStack`, `Text`, `Image`, `Icon`, `Spacer`,
  `Divider`, `ScrollView`, `TextField`, `Button`, `Slider`.
- Modifiers: `padding`, `cornerRadius`, `background`, `fontSize`, `fontWeight`,
  `lineLimit`, `foregroundStyle`, width/height/min/max frames, content mode, plus
  CSS-like color packing. String/number properties may be reactive signals.
- `AppContext` (`state`, `platform`, `route`) with `useAppContext()` and
  `withAppContext()`.
- `InputSignal` re-export.
- `MediaController` (commands + reports + `attach()`) for app-driven audio/video.
- Tests cover modifier chaining, colors, reactive text, context scoping, and the
  decorator marker.

## Not implemented / gaps

- Structural/conditional view selection (planned as the next phase).
- A full component catalog (only the reference primitives are implemented).
- `VIDEO` surfaces and the `SIZE_THAT_FITS` layout primitive (audio uses
  `MediaController` + ordinary controls today).
- Styles/environment propagation and custom view protocols.
