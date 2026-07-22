# Feature truthfulness review

- Product hero: uses implemented Home actions, recent files, Search PDFs, Offline label, and tool names.
- Scanner: uses implemented crop, enhancement modes, brightness/contrast, per-page workflow, and save.
- Reader: uses implemented smooth reader, search, bookmarks, table of contents, page indicator, and share.
- Compression: uses implemented High Quality, Balanced, and Maximum modes. No fabricated size or
  saving percentage appears; fallback labels are `Original` and `Smaller PDF`.
- Images to PDF: uses implemented reorder, rotation cue, A4 paper size, margins, and creation flow.
- Merge/rearrange: uses implemented multi-document merge and thumbnail page reorder behavior.
- Privacy: states only the repository product guarantee that PDF processing happens locally and
  documents stay on device. It does not claim encryption, anonymity, or immunity from compromise.
- Result: uses implemented Open, Share, Rename, and Open Folder actions with a local saved location.
- Dark reader comparison was omitted even though a night appearance exists; the main reader creative
  has a clearer search/bookmark story without adding a fifth transformation claim.
- No OCR, cloud sync, AI, subscription, install-count, rating, performance, or unsupported-device
  claim appears.
- Embedded application panels are production `QuietPdfApp` Compose renders from
  `PlayStoreUiCaptureTest`, not marketing reconstructions. Deterministic fixtures change data only;
  the controls, strings, layout, theme, and workflow surfaces are the shipping implementation.

Review result: **PASS**.
