# Current Google Play competitor icon audit

Research and visual-inspection date: 2026-07-23. Install bands are the bands displayed by Google
Play on that date; they are not lifetime-install estimates and can change. Icon observations were
made from the current listing artwork, inspected temporarily outside the repository. No competitor
artwork is included in this package.

| App / publisher | Play listing | Install band | Dominant color and main symbol | Visible elements; document/PDF cue; text | 24–48 px readability and distinctiveness | Weakness / QuietPDF similarity risk |
|---|---|---:|---|---|---|---|
| Adobe Acrobat Reader / Adobe | [Google Play](https://play.google.com/store/apps/details?id=com.adobe.reader) | 500M+ | Deep red; white Acrobat ribbon | 2; abstract PDF ribbon; “PDF” | Highly readable and unmistakably branded | Dense red category convention; QuietPDF must not imitate ribbon geometry or red/white trade dress. |
| CamScanner / INTSIG PTE | [Google Play](https://play.google.com/store/apps/details?id=com.intsig.camscanner) | 500M+ | Navy and teal; large CS | 2; scanner implied by initials; “CS” | Excellent letter readability and high recall | Weak document cue without brand knowledge; no meaningful overlap with blue page/Q. |
| WPS Office / WPS SOFTWARE PTE. LTD. | [Google Play](https://play.google.com/store/apps/details?id=cn.wps.moffice_eng) | 500M+ | Coral red; white W mark | 1; no direct page/PDF cue; letterform | Strong at small sizes and distinctive | Office-suite rather than PDF-specific; avoid its folded-ribbon W. |
| Adobe Scan / Adobe | [Google Play](https://play.google.com/store/apps/details?id=com.adobe.scan.android) | 100M+ | Teal; white Acrobat ribbon | 2; ribbon plus “SCAN” | Strong but relies on tiny word at 24 px | Brand extension of Acrobat; QuietPDF must not use ribbon or scanner labeling. |
| PDF Reader – PDF Editor / Simple Design Ltd. | [Google Play](https://play.google.com/store/apps/details?id=pdf.reader.pdfviewer.pdfeditor) | 50M+ | Red page on white; large PDF | 3; explicit document and PDF cue; “PDF” | Immediate category recognition | Generic and clone-prone; Candidate D tests literal recognition without using red or its page geometry. |
| iLovePDF / ILOVEPDF S.L. | [Google Play](https://play.google.com/store/apps/details?id=com.ilovepdf.www) | 10M+ | White, black, red; heart wordmark | 4; tiny folded page; “I”, heart, “PDF” | Text remains recognizable mainly because of established wordmark | Visually busy and typography-dependent; no heart or wordmark should appear in QuietPDF. |
| Xodo / Apryse Software Inc. | [Google Play](https://play.google.com/store/apps/details?id=com.xodo.pdf.reader) | 10M+ | Cyan, blue, orange; interlocking ribbon | 3+; red corner PDF label; “PDF” | Colorful mark survives; corner label does not | Multiple colors and ribbon complexity; avoid interlocking geometry and red corner label. |
| Foxit PDF Editor / Foxit Software Inc. | [Google Play](https://play.google.com/store/apps/details?id=com.foxit.mobile.pdf.lite) | 10M+ | Purple; white fox/quill | 2; weak document cue; “EDITOR” | Symbol reads; text compresses at 24 px | Loud and label-heavy; no fox/quill similarity with QuietPDF. |
| Smallpdf / Smallpdf AG | [Google Play](https://play.google.com/store/apps/details?id=com.smallpdf.app.android) | 5M+ | Multi-color grid | 9 color tiles; no direct PDF cue; no text | Color block remains visible but meaning is weak | High visual noise and weak category recognition; validates QuietPDF’s three-color limit. |
| PDFelement / Wondershare | [Google Play](https://play.google.com/store/apps/details?id=com.wondershare.pdfelement) | 5M+ | Bright blue; white geometric letter | 2; abstract P/page cue; small corporate mark | Main letter is strong; small lower mark disappears | Closest palette risk. QuietPDF differentiates through a full document silhouette and circular Q, not a modular P. |
| MobiOffice / MobiSystems | [Google Play](https://play.google.com/store/apps/details?id=com.mobisystems.office) | 100M+ | Red, green, cyan hexagons | 3; office-suite cluster; no text | Colored cluster remains legible | Generic productivity-suite signal and no PDF specificity. |
| Google Drive / Google LLC | [Google Play](https://play.google.com/store/apps/details?id=com.google.android.apps.docs) | 10B+ | Google blue/green/yellow triangle | 3; storage triangle; no text | Exceptional silhouette recognition | Blue alone is not enough; QuietPDF must retain the white document to avoid cloud-storage association. |

## Category pattern

The field splits into red PDF literalism, large initials/wordmarks, and multi-color office-suite
symbols. QuietPDF’s blue document/Q combination occupies a calmer position: direct document utility,
an owned letterform, and no security, cloud, scanner, or AI metaphor. The closest color neighbor is
PDFelement, but the silhouette and internal geometry are materially different.

Displayed install bands do **not** demonstrate that an icon caused those installs. Publisher equity,
distribution, pricing, product quality, paid acquisition, listing copy, ratings, and history are
confounding factors. The icon decision therefore remains a hypothesis for a controlled listing test.
