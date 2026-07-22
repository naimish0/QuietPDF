# QuietPDF Play Store Creatives V2

This folder contains the rendered **Quiet Confidence** creative system. The English masters are
deterministic, editable through `tools/generate_assets.py`, and derived from QuietPDF's implemented
Material 3 workflows. All store screenshots and feature graphics are flattened RGB images.

## Final upload sets

- Phone: `play-upload/phone/en-US/` — eight 1080×1920 PNGs in display order, with `en-US` in every filename.
- 7-inch tablet: `play-upload/tablet-7/en-US/` — eight purpose-built 1920×1080 PNGs, with `en-US` in every filename.
- 10-inch tablet: `play-upload/tablet-10/en-US/` — eight purpose-built 1920×1080 PNGs, with `en-US` in every filename.
- Feature graphics: `feature-graphic/utility/` and `feature-graphic/privacy/` — 1024×500 PNGs.
- Play icon: `branding/selected/quietpdf-play-icon-512.png`.
- All-features overview: `contact-sheets/quietpdf-all-features-contact-sheet.png` — one polished
  2400×3600 image presenting eight visual workflows, all 20 production PDF tools, and 16 reader/file essentials.
- Localized upload-ready families: `localized/upload-ready/{de-DE,fr-FR,ja-JP,hi-IN,ru-RU,es-ES,pt-PT,pt-BR,it-IT,id-ID,ar,ko-KR,ur-PK}/` — eight
  phone, eight 7-inch tablet, eight 10-inch tablet, and two feature-graphic files per locale. Every
  screenshot filename includes its corresponding locale code immediately after the display order.
- Localized all-features overviews: `contact-sheets/quietpdf-all-features-{locale}-contact-sheet.png`
  for all thirteen localized listing languages.
- Russian, Spanish, European Portuguese, Brazilian Portuguese, Italian, Indonesian, Arabic,
  Korean, and Urdu now have complete Android resources, authentic no-ad captures, upload-ready
  creative families, and all-features contact sheets.

The app manifest does not declare TV, Wear OS, Automotive, or XR distribution categories. No fake
assets were created for them. Folded layouts map to phone and unfolded layouts map to tablet; local
unfolded QA references live under `qa/foldable/`, not a claimed Play Console slot. Chromebook is
left documented as unsupported rather than populated with resized phone art.

## Regeneration

Requires Python 3 and Pillow. Inter is included under the SIL Open Font License in
`source/fonts-and-licenses/`.

```bash
python3 tools/generate_android_translations.py
python3 tools/generate_asset_copy_translations.py
python3 play-store-assets/v2-creative/tools/generate_assets.py
python3 play-store-assets/v2-creative/tools/validate_assets.py
```

The source renderer creates synthetic PDFs, before/after states, branding exports, upload images,
marketing compositions, contact sheets, and manifests. Final creatives use production Compose UI
captured by `PlayStoreUiCaptureTest`: English masters use the Pixel 7a profile and localized masters
use a Samsung SM-S928B. The older reconstructed panels remain under
`source/real-ui-captures-no-ads/reconstructed-fallback/` for comparison only. Generated
photography is limited to owned supporting content, and all marketing typography is deterministic.

## No-ad capture profile

The androidTest capture harness hosts the real production `QuietPdfApp` Compose UI with deterministic
fixture state. Every capture passes `adsCanLoad = false` and `homeBannerContent = null`; it does not
initialize the Mobile Ads SDK, request an ad, or render/reserve an ad container. This test-only
configuration does not modify production advertising code or behavior.

Every raster output is listed in `manifests/asset-inventory.csv` with `NO ADS: VERIFIED`, and the
complete manual checklist is in `qa/no-ads-review.md`.

## Truthfulness note

The no-ad ingredients are real production Compose renders captured at 1080×2400 (plus isolated
dialog-node captures) on the Pixel 7a profile and Samsung SM-S928B. They cover Home, scanner review,
reader search, compression, images-to-PDF layout, merge/rearrange, privacy, result, Settings, and
language-picker states. Compression marketing
comparisons retain the truthful **Original** and **Smaller PDF** labels rather than inventing sizes.

## Localized listing assets

All thirteen listing languages are generated from locale-specific editable
compositions. Headlines, supporting copy, comparison labels, document names, PDF previews,
workflow labels, buttons, navigation, and result UI are rendered in the selected listing language;
they are not painted over English master screenshots. Each locale has eleven ad-free production
Compose workflow captures under `source/real-ui-captures-no-ads/localized/{locale}/`, plus localized
owned document and receipt fixtures. No draft, review-warning, or publish-warning label is embedded
in any image.
