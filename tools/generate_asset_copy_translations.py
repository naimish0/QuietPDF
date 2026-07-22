#!/usr/bin/env python3
"""Generate editable localized copy used by the QuietPDF creative renderer."""

from __future__ import annotations

import json
from pathlib import Path

from generate_android_translations import LOCALES, ROOT, translate_entries

PHONE = [
    ("All your PDF tools.\nOne calm app.", "Open, scan, organize and compress."),
    ("From crooked photo\nto clean PDF.", "Crop, correct and enhance."),
    ("Read without distractions.", "Smooth zoom, search and bookmarks."),
    ("Smaller file. Clear pages.", "Compare size before saving."),
    ("Turn photos into\npolished PDFs.", "Arrange, rotate and choose your layout."),
    ("Merge and reorder\nwith confidence.", "Preview every page before saving."),
    ("Your documents stay\non your device.", "PDF processing happens locally."),
    ("Saved. Shared.\nEasy to find.", "Open, rename, share or view folder."),
]
LABELS = {
    "before":"BEFORE", "after":"AFTER", "utility":"Everyday PDF tools.\nBeautifully simple.",
    "privacy":"Your PDFs stay\non your device.", "scan":"SCAN", "clean":"CLEAN",
    "local":"LOCAL", "processing":"PROCESSING",
}
CONTENT = {
    "quarterly":"Quarterly Summary", "travel":"Travel Plan", "notes":"Project Notes",
    "research":"Research Report", "catalogue":"Product Catalogue", "brief":"Project Brief",
    "budget":"Budget Overview", "timeline":"Delivery Timeline", "contract":"Contract Draft",
    "itinerary":"Travel Itinerary", "pages":"{n} pages", "original":"Original",
    "smaller":"Smaller PDF", "completed":"Completed", "local_pdf":"Local PDF",
    "contents":"CONTENTS", "context":"Context", "findings":"Findings", "methods":"Methods",
    "search":"Search", "privacy_query":"privacy", "result_count":"3 of 8",
    "output_note":"Output measured in app when saving", "page":"Page {n}",
    "project":"Project", "pavilion":"Pavilion", "pine":"Pine trail", "stairs":"Blue stairs", "lake":"Lake house",
    "processing":"Local processing", "stays":"Stays on device", "workflow":"LOCAL WORKFLOW",
    "open":"Open", "process":"Process", "save":"Save", "no_upload":"No document upload",
    "ready":"Ready to share", "stored":"Stored locally", "share":"Share PDF",
    "home":"Home", "files":"Files", "tools":"Tools", "history":"History",
    "receipt_title":"NORTHSTAR RECEIPT", "receipt_total":"TOTAL  84.60",
}


def main() -> None:
    phone_keys=[f"phone_{index}_{part}" for index in range(8) for part in ("headline","support")]
    source_values=[value for pair in PHONE for value in pair] + list(LABELS.values()) + list(CONTENT.values())
    keys=phone_keys + list(LABELS) + list(CONTENT)
    output={"localized_copy":{},"content_copy":{}}
    for locale,(target,_) in LOCALES.items():
        translated=dict(zip(keys,translate_entries(source_values,target)))
        output["localized_copy"][locale]={
            "phone":[[translated[f"phone_{i}_headline"],translated[f"phone_{i}_support"]] for i in range(8)],
            **{key:translated[key] for key in LABELS},
        }
        output["content_copy"][locale]={key:translated[key] for key in CONTENT}
        print(f"{locale}: creative copy translated")
    destination=ROOT/"play-store-assets/v2-creative/source/editable-layouts/localized-copy.generated.json"
    destination.write_text(json.dumps(output,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(destination.relative_to(ROOT))


if __name__ == "__main__":
    main()
