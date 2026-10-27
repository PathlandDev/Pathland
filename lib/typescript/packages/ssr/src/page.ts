export interface PageOptions {
  base: string;
  bundleName: string;
  serviceWorker?: boolean;
  ssrScoped?: boolean;
}

function attr(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

/** Inject the Pathland base attributes and the DOM-client bundle into SSR HTML. */
export function renderPage(html: string, options: PageOptions): string {
  const base = options.base.replace(/\/+$/, "") || "/_pathland";
  const attributes = [
    ` data-pathland-base="${attr(base)}"`,
    options.serviceWorker ? ' data-pathland-service-worker="true"' : "",
    options.ssrScoped ? ' data-pathland-ssr-scoped="true"' : "",
  ].join("");
  const withBase = html.replace("<html>", `<html${attributes}>`);
  const script = `<script src="${attr(base)}/${attr(options.bundleName)}" defer></script>`;
  return withBase.replace("</body>", `${script}</body>`);
}
