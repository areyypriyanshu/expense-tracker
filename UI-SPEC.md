# UI-SPEC

## Intent

Refresh the existing Android UI into a minimal, calm expense-tracking experience that feels trustworthy and easy to scan.

## Visual Contract

- Palette: warm off-white surfaces, deep green primary, muted blue secondary, soft gold accent, restrained red/green status colors.
- Shape: cards and controls use compact 8dp radii; circles are reserved for icons, FABs, and status marks.
- Elevation: avoid heavy shadows. Use subtle borders and tonal surfaces for separation.
- Typography: medium weights for hierarchy, bold only for key money values and primary screen titles.
- Density: keep financial information compact and scannable with predictable spacing.
- Motion: use existing lightweight transitions only; avoid decorative motion. Durations, curves and staggers come from `MotionTokens` — one token per gesture, never hand-tuned numbers at a call site.

## Interaction Contract

- Primary actions use icon plus label where space allows.
- Secondary actions remain text or icon buttons with clear affordance.
- Empty/loading/error states stay quiet and action-oriented.
- Bottom navigation should feel anchored but unobtrusive.

## Scope

This pass applies the contract through the shared theme, common components, dashboard, transaction list, and shared header patterns so the current UI becomes cleaner without rewriting every screen.
