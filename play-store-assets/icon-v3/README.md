# QuietPDF icon v3

This package contains the deterministic T46 launcher-icon redesign and the assets needed for a
controlled Google Play Store Listing Experiment. The production hypothesis is **the blue document
with the Q**: a full-bleed `#2563EB` field, one white document silhouette, a pale-blue fold, and a
short embedded Q tail that is less likely to read as a search symbol.

## Selected assets

- Google Play upload: `production/play-store/quietpdf-play-icon-512.png`
- Editable production source: `production/source-svg/quietpdf-document-q.svg`
- Adaptive sources: `production/adaptive/`
- Themed-icon source: `production/monochrome/quietpdf-monochrome.svg`
- Legacy launcher exports: `production/legacy/`
- Light/dark launcher artwork: `production/play-store/quietpdf-play-icon-512.png` and
  `production/theme-variants/quietpdf-dark-launcher-512.png`
- Experiment control and variants: `experiments/`
- Candidate and finalist review sheets: `previews/contact-sheets/`

The existing Play icon is preserved under `baseline/` and used as the experiment control. The
experiment copy is pixel-identical in visible RGB content and normalized to opaque 32-bit sRGB PNG
so it independently meets the current Play upload format requirement.

## Regenerate and validate

```bash
python3 play-store-assets/icon-v3/tools/generate_icon_assets.py
python3 play-store-assets/icon-v3/tools/validate_icon_assets.py
```

The generator uses Pillow and original geometry mirrored in editable SVG sources. It does not use
image generation, stock artwork, competitor artwork, decorative gradients, or baked launcher masks.
No ad UI or ad container appears in any output.

QuietPDF Settings can switch between the light and dark launcher variants through manifest activity
aliases. The Play Store listing icon remains the blue production icon; only the installed launcher
icon changes with the in-app appearance preference.

The icon is a conversion hypothesis, not a promise of increased installs. Follow
`research/conversion-experiment-plan.md` before adopting it as the long-term store icon.
