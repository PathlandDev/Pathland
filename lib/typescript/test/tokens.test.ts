import { beforeEach, describe, expect, it } from "vitest";
import {
  createTokenSink,
  isDarkToken,
  resolveTokenCssRef,
  tokenCssValue,
  tokenToCssVar,
} from "../src/tokens";
import { VAL_COLOR, VAL_F32, VAL_U32 } from "../src/constants";
import { bitsFromF32 } from "../src/format";

beforeEach(() => {
  document.head.querySelectorAll("style[data-pathland-tokens]").forEach((s) => s.remove());
});

describe("design tokens", () => {
  it("maps dot paths to canonical --pl-* CSS variable names", () => {
    expect(tokenToCssVar("color.primary")).toBe("--pl-color-primary");
    expect(tokenToCssVar("space.2")).toBe("--pl-space-2");
    expect(tokenToCssVar("font.body")).toBe("--pl-font-body");
  });

  it("strips the dark. scheme prefix from the variable name", () => {
    expect(tokenToCssVar("dark.color.primary")).toBe("--pl-color-primary");
    expect(isDarkToken("dark.color.primary")).toBe(true);
    expect(isDarkToken("color.primary")).toBe(false);
  });

  it("renders COLOR values as rgba", () => {
    expect(tokenCssValue(VAL_COLOR, 0xff0000ff)).toBe("rgba(0,0,255,1.000)");
  });

  it("renders F32 values as numbers", () => {
    expect(tokenCssValue(VAL_F32, bitsFromF32(1.5))).toBe("1.5");
  });

  it("resolves plain token references to var()", () => {
    expect(resolveTokenCssRef("color.primary")).toBe("var(--pl-color-primary, unset)");
    expect(resolveTokenCssRef("dark.color.primary")).toBe("var(--pl-dark-color-primary, unset)");
  });

  it("resolves generative space.<N> references to calc()", () => {
    expect(resolveTokenCssRef("space.2")).toBe("calc(var(--pl-space-base) * 2)");
    expect(resolveTokenCssRef("space.0.5")).toBe("calc(var(--pl-space-base) * 0.5)");
  });

  it("applies base overrides as :root rules", () => {
    const sink = createTokenSink();
    sink.setToken("color.primary", VAL_COLOR, 0xff2196f3);
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toBe(":root{--pl-color-primary:rgba(33,150,243,1.000);}@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){--pl-color-primary:rgba(33,150,243,1.000);}}:root[data-pathland-scheme=\"dark\"]{--pl-color-primary:rgba(33,150,243,1.000);}:root{--pl-dark-color-primary:rgba(33,150,243,1.000);}");
  });

  it("applies dark.* overrides inside the dark media query", () => {
    const sink = createTokenSink();
    sink.setToken("dark.color.primary", VAL_COLOR, 0xff60a5fa);
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toBe(
      "@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){--pl-color-primary:rgba(96,165,250,1.000);}}:root[data-pathland-scheme=\"dark\"]{--pl-color-primary:rgba(96,165,250,1.000);}:root{--pl-dark-color-primary:rgba(96,165,250,1.000);}",
    );
  });

  it("emits F32 length tokens with px", () => {
    const sink = createTokenSink();
    sink.setToken("space.base", VAL_F32, bitsFromF32(4));
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toBe(":root{--pl-space-base:4px;}@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){--pl-space-base:4px;}}:root[data-pathland-scheme=\"dark\"]{--pl-space-base:4px;}:root{--pl-dark-space-base:4px;}");
  });

  it("keeps both layers and regenerates the style block", () => {
    const sink = createTokenSink();
    sink.setToken("color.primary", VAL_COLOR, 0xff2563eb);
    sink.setToken("dark.color.primary", VAL_COLOR, 0xff60a5fa);
    const style = document.head.querySelector("style[data-pathland-tokens]");
    expect(style?.textContent).toBe(
      ":root{--pl-color-primary:rgba(37,99,235,1.000);}@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){--pl-color-primary:rgba(37,99,235,1.000);--pl-color-primary:rgba(96,165,250,1.000);}}:root[data-pathland-scheme=\"dark\"]{--pl-color-primary:rgba(37,99,235,1.000);--pl-color-primary:rgba(96,165,250,1.000);}:root{--pl-dark-color-primary:rgba(37,99,235,1.000);--pl-dark-color-primary:rgba(96,165,250,1.000);}",
    );
    sink.setToken("space.2", VAL_U32, 8);
    expect(style?.textContent).toContain(":root{--pl-space-2:8;}");
  });
});