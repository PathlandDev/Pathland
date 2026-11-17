// Coalesces scroll/resize-driven lazy visible-range reports. A fast scroll
// emits at most one report per interval plus a trailing report and an
// end-of-gesture forced report (spec/EVENTS.md §Visible range, ADR-0017). The
// server applies an additional latest-wins coalescing pass; reports after
// hydration, batch application, or reconnect are not routed through this.

export const LAZY_REPORT_INTERVAL_MS = 100;
export const LAZY_FINAL_REPORT_DELAY_MS = 150;

export interface LazyRangeThrottle {
  /** A scroll/resize tick: report now or schedule the next allowed report. */
  scroll(): void;
  /** The gesture ended: cancel pending timers and force one final report. */
  end(): void;
}

export function createLazyRangeThrottle(
  refresh: (force: boolean) => void,
  intervalMs = LAZY_REPORT_INTERVAL_MS,
  finalDelayMs = LAZY_FINAL_REPORT_DELAY_MS,
): LazyRangeThrottle {
  let lastReport = Number.NEGATIVE_INFINITY;
  let trailingTimer = 0;
  let finalTimer = 0;

  const clearTrailing = (): void => {
    if (trailingTimer) {
      clearTimeout(trailingTimer);
      trailingTimer = 0;
    }
  };
  const clearFinal = (): void => {
    if (finalTimer) {
      clearTimeout(finalTimer);
      finalTimer = 0;
    }
  };
  const report = (force: boolean): void => {
    lastReport = Date.now();
    refresh(force);
  };

  return {
    scroll(): void {
      const now = Date.now();
      const elapsed = now - lastReport;
      if (elapsed >= intervalMs) {
        clearTrailing();
        report(false);
      } else if (!trailingTimer) {
        trailingTimer = window.setTimeout(() => {
          trailingTimer = 0;
          report(false);
        }, intervalMs - elapsed);
      }
      // A final report follows the last scroll even if the range is unchanged,
      // so the engine can release prefetched rows.
      clearFinal();
      finalTimer = window.setTimeout(() => {
        finalTimer = 0;
        report(true);
      }, finalDelayMs);
    },
    end(): void {
      clearTrailing();
      clearFinal();
      report(true);
    },
  };
}
