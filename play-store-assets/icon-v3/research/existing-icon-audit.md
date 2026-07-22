# Existing icon audit

Audit date: 2026-07-23

## Wiring before T46

- `AndroidManifest.xml` used `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` correctly.
- API 26 adaptive resources had separate foreground and background drawables.
- API 33 resources declared a dedicated monochrome drawable.
- Android 12+ splash themes used the launcher icon and a named icon-background color.
- No product flavors, launcher shortcuts, or separate shortcut icons were present.
- No notification icon was replaced or repurposed by this work.
- Legacy WebP launcher and round-launcher fallbacks existed at mdpi through xxxhdpi.

The installed launcher artwork and the V2 Play icon did not match. The installed adaptive icon used
a dark navy/teal diagonal field, a white page, and a green shield/check. The Play icon used a bright
blue field, white page, and blue Q. This weakened recognition between acquisition and first launch.

## Visual inspection of the existing Play control

The control was opened at 512 px, reviewed at 16, 24, 32, 48, 64, 96, and 128 px, and inspected
under circle, squircle, rounded-square, square, and teardrop masks.

| Area | Finding |
|---|---|
| Small-size readability | Page and Q survive at 24–48 px; the long diagonal tail becomes visually close to a magnifying-glass handle. |
| Category recognition | Strong document cue; no literal PDF cue. |
| Visual clutter | Low: background, page, fold, Q. |
| Contrast | Excellent white/blue contrast; fold remains secondary. |
| Optical centering | Page/Q mass sits slightly right and low because of the long Q tail. |
| Mask clipping | Safe in circle, squircle, rounded-square, square, and teardrop previews. |
| Generic appearance | The page silhouette is common; the Q is the only owned element. |
| Competitor similarity | Blue differentiates from Adobe/Simple Design red, but the long Q tail can read as generic search. |
| Adaptive resources | Present, but their shield/check artwork did not match the Play control. |
| Monochrome resource | Present, but its page/shield form communicated security more than a PDF utility. |
| Splash behavior | Existing color mismatch risked a dark double-container impression and inconsistent transition. |

## Baseline preservation

The original V2 SVG and PNG are copied unchanged into `baseline/current-source/`. Rendered control
sizes and mask previews are in `baseline/current-rendered/`. The control artwork is not used as a
source for Candidate A; Candidate A uses newly authored geometry and a shorter embedded Q tail.
