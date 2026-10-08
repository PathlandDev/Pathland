//! The `SizeThatFits` fit rule — the single spec'd selection algorithm every
//! renderer and host share (spec/PRIMITIVES.md §SizeThatFits). Given the slot's
//! allocated width and the app-declared candidate thresholds (ascending), it
//! returns the selected candidate index.

/// The fit index for a `SizeThatFits` slot: the **largest threshold the given
/// width meets**, defaulting to index 0 when none do (the always-valid
/// fallback). `thresholds` must be sorted ascending (the app's `FIT_QUERY`);
/// equal thresholds pick the **lower** index (stable). An empty table always
/// yields index 0.
///
/// ```
/// use pathland_core::fit::fit_index;
/// assert_eq!(fit_index(&[0.0, 480.0, 720.0], 500.0), 1); // 480 fits, 720 not
/// assert_eq!(fit_index(&[0.0, 480.0, 720.0], 1000.0), 2); // widest fits
/// assert_eq!(fit_index(&[0.0, 480.0], 100.0), 0);         // only the fallback
/// assert_eq!(fit_index(&[0.0, 480.0, 480.0], 600.0), 1);  // tie -> lower index
/// assert_eq!(fit_index(&[], 500.0), 0);
/// ```
pub fn fit_index(thresholds: &[f32], width: f32) -> usize {
    let mut selected = 0;
    for i in 0..thresholds.len() {
        if thresholds[i] > width {
            break; // sorted ascending — nothing after can fit either
        }
        // Prefer the FIRST of a run of equal thresholds (ascending, stable).
        if i == 0 || thresholds[i - 1] != thresholds[i] {
            selected = i;
        }
    }
    selected
}

#[cfg(test)]
mod tests {
    use super::fit_index;

    #[test]
    fn picks_the_largest_threshold_that_fits() {
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 0.0), 0);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 100.0), 0);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 479.0), 0);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 480.0), 1);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 500.0), 1);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 719.0), 1);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 720.0), 2);
        assert_eq!(fit_index(&[0.0, 480.0, 720.0], 1000.0), 2);
    }

    #[test]
    fn equal_thresholds_prefer_the_lower_index() {
        assert_eq!(fit_index(&[0.0, 480.0, 480.0], 600.0), 1);
        assert_eq!(fit_index(&[0.0, 0.0], 10.0), 0);
    }

    #[test]
    fn empty_table_is_the_fallback() {
        assert_eq!(fit_index(&[], 500.0), 0);
    }
}