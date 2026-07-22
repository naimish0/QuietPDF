#!/usr/bin/env python3
"""Fail-fast validator for QuietPDF V2 store and marketing assets."""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PHONE = ROOT / "play-upload/phone/en-US"
T7 = ROOT / "play-upload/tablet-7/en-US"
T10 = ROOT / "play-upload/tablet-10/en-US"

PHONE_NAMES = [
    "01-all-tools.png", "02-scanner-transform.png", "03-pdf-reader.png",
    "04-compression-transform.png", "05-images-to-pdf.png",
    "06-merge-rearrange.png", "07-offline-privacy.png", "08-result-sharing.png",
]
SLUGS = [p[3:-4] for p in PHONE_NAMES]


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def image_check(errors, path: Path, size=None, require_rgb=False):
    if not path.is_file():
        return fail(errors, f"missing: {path.relative_to(ROOT)}")
    try:
        with Image.open(path) as im:
            im.verify()
        with Image.open(path) as im:
            if size and im.size != size:
                fail(errors, f"wrong dimensions: {path.relative_to(ROOT)} {im.size} != {size}")
            if require_rgb and im.mode != "RGB":
                fail(errors, f"alpha/non-RGB upload asset: {path.relative_to(ROOT)} mode={im.mode}")
    except Exception as exc:
        fail(errors, f"cannot decode: {path.relative_to(ROOT)}: {exc}")


def main() -> int:
    errors=[]
    for name in PHONE_NAMES:
        image_check(errors,PHONE/name,(1080,1920),True)
    for base,label in [(T7,"tablet7"),(T10,"tablet10")]:
        for i,slug in enumerate(SLUGS,1):
            image_check(errors,base/f"{i:02d}-{slug}-{label}.png",(1920,1080),True)
    image_check(errors,ROOT/"feature-graphic/utility/quietpdf-feature-utility.png",(1024,500),True)
    image_check(errors,ROOT/"feature-graphic/privacy/quietpdf-feature-privacy.png",(1024,500),True)
    icon=ROOT/"branding/selected/quietpdf-play-icon-512.png"
    image_check(errors,icon,(512,512),True)
    if icon.is_file() and icon.stat().st_size > 1024*1024:
        fail(errors,f"Play icon exceeds 1 MiB: {icon.stat().st_size} bytes")
    marketing={
        "marketing-not-for-play/square/quietpdf-square.png":(1080,1080),
        "marketing-not-for-play/landscape/quietpdf-landscape.png":(1200,628),
        "marketing-not-for-play/story/quietpdf-story.png":(1080,1920),
        "marketing-not-for-play/phone-mockups/phone-tablet-hero.png":(1400,900),
        "marketing-not-for-play/phone-mockups/scanner-before-after.png":(1200,1200),
        "marketing-not-for-play/phone-mockups/privacy-first.png":(1200,1200),
        "marketing-not-for-play/phone-mockups/compression-transformation.png":(1200,1200),
    }
    for rel,size in marketing.items():image_check(errors,ROOT/rel,size,True)
    # Quarantine and naming checks.
    prod_roots=[ROOT/"play-upload",ROOT/"feature-graphic",ROOT/"branding/selected",ROOT/"marketing-not-for-play"]
    for base in prod_roots:
        for p in base.rglob("*"):
            if p.is_file() and "DRAFT" in p.name.upper():fail(errors,f"DRAFT in production path: {p.relative_to(ROOT)}")
            if p.suffix.lower() in {".png",".jpg",".jpeg"} and not re.fullmatch(r"[a-z0-9][a-z0-9._-]*",p.name):
                fail(errors,f"inconsistent filename: {p.relative_to(ROOT)}")
    # Placeholder residue check in editable/text sources.
    for p in ROOT.rglob("*"):
        if p.suffix.lower() not in {".md",".csv",".json",".svg",".py"}:continue
        if p.resolve() == Path(__file__).resolve():continue
        value=p.read_text(encoding="utf-8",errors="ignore")
        if re.search(r"\b(LOREM IPSUM|TBD PLACEHOLDER|INSERT SCREENSHOT|YOUR TEXT HERE)\b",value,re.I):
            fail(errors,f"placeholder remains: {p.relative_to(ROOT)}")
    # Every source PDF must have an explicit owned/license declaration.
    fixture_csv=ROOT/"manifests/document-fixture-inventory.csv"
    if not fixture_csv.is_file():
        fail(errors,"missing document fixture inventory")
    else:
        with fixture_csv.open(newline="",encoding="utf-8") as f: rows=list(csv.DictReader(f))
        listed={r["filename"] for r in rows if r.get("license") and "Owned" in r["license"]}
        actual={p.name for p in (ROOT/"source/synthetic-pdfs").glob("*.pdf")}
        for name in sorted(actual-listed):fail(errors,f"source document lacks owned license: {name}")
        for name in sorted(listed-actual):fail(errors,f"inventory references missing source document: {name}")
    # Inventory must include every raster output and all files must decode.
    inventory=ROOT/"manifests/asset-inventory.csv"
    if not inventory.is_file():
        fail(errors,"missing asset inventory")
    else:
        with inventory.open(newline="",encoding="utf-8") as f: rows=list(csv.DictReader(f))
        listed={r["path"] for r in rows}; actual={str(p.relative_to(ROOT)) for p in ROOT.rglob("*") if p.suffix.lower() in {".png",".jpg",".jpeg"}}
        for rel in sorted(actual-listed):fail(errors,f"asset inventory incomplete: {rel}")
        for row in rows:
            p=ROOT/row["path"]
            image_check(errors,p)
            if row.get("no_ads") != "NO ADS: VERIFIED":fail(errors,f"no-ad verification missing: {row['path']}")
    if errors:
        print("ASSET VALIDATION: FAILED")
        for e in errors:print(f"- {e}")
        return 1
    print(f"ASSET VALIDATION: PASSED ({len(listed)} raster assets inventoried)")
    print("Phone: 8/8 RGB 1080x1920")
    print("Tablet 7-inch: 8/8 RGB 1920x1080")
    print("Tablet 10-inch: 8/8 RGB 1920x1080")
    print("Feature graphics: 2/2 RGB 1024x500")
    print(f"Play icon: 512x512 RGB, {icon.stat().st_size} bytes")
    print("NO ADS: VERIFIED for every inventoried raster asset")
    return 0


if __name__ == "__main__":
    sys.exit(main())
