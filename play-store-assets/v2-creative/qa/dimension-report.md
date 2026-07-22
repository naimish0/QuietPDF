# Dimension report

Generated and validated at source resolution.

- Raster assets inventoried: 116
- Phone masters: 8 × 1080×1920 RGB PNG
- 7-inch tablet masters: 8 × 1920×1080 RGB PNG
- 10-inch tablet masters: 8 × 1920×1080 RGB PNG
- Feature graphics: 2 × 1024×500 RGB PNG
- Selected Play icon: 512×512 RGB PNG; validator enforces ≤1024 KB
- Marketing: 1080×1080, 1200×628, 1080×1920, 1400×900, and 1200×1200 outputs

Validation command:

`python3 play-store-assets/v2-creative/tools/validate_assets.py`

Result: **PASS** — 116 raster assets inventoried; all required upload families passed dimension, RGB/decode, naming, inventory, licensing, draft-quarantine, placeholder, and no-ad checks. The Play icon is 2,467 bytes, below the 1,024 KB limit.
