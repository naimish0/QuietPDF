# Localization review

Upload-ready locales: **en-US, de-DE, fr-FR, ja-JP, hi-IN, ru-RU, es-ES, pt-PT, pt-BR, it-IT,
id-ID, ar, ko-KR, ur-PK**.

Each localized family is regenerated from the editable composition rather than translated by
painting over a finished raster. It includes eight phone images, eight 7-inch tablet images, eight
10-inch tablet images, and two feature graphics. No status or review-warning label is embedded.

Checks completed:

- Locale-specific headline wrapping and safe-area fit
- Native punctuation and complete glyph rendering
- German and French diacritics
- Japanese kana/kanji coverage
- Hindi Devanagari shaping and matra placement
- Localized before/after labels
- RGB/no-alpha upload format and required dimensions
- Complete Android resource parity for all thirteen non-English listing locales
- Distinct European and Brazilian Portuguese resources and exact regional locale matching
- Arabic and Urdu right-to-left layout, shaping, and alignment
- Russian Cyrillic, Korean Hangul, and Indonesian resource coverage
- Authentic no-ad localized Compose captures reviewed across all eight screenshot narratives
- Embedded app UI, document names, PDF preview titles, controls, navigation, and result labels use
  the same locale as the surrounding marketing copy
- Localized feature-graphic UI and document fragments contain no English fallback copy
- No ads, empty ad containers, sponsored labels, or ad-loading states

The Samsung SM-S928B capture run passed all eleven deterministic no-ad workflow tests for each of
the nine newly added locales, producing 99 new authentic localized UI captures.

Review result: **TECHNICAL LOCALIZATION QA PASSED**.
