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

SLUGS = [
    "all-tools", "scanner-transform", "pdf-reader", "compression-transform",
    "images-to-pdf", "merge-rearrange", "offline-privacy", "result-sharing",
]
PHONE_NAMES = [f"{i:02d}-en-US-{slug}.png" for i, slug in enumerate(SLUGS, 1)]
LOCALES = ["de-DE","fr-FR","ja-JP","hi-IN","ru-RU","es-ES","pt-PT","pt-BR","it-IT","id-ID","ar","ko-KR","ur-PK"]
CONTACT_LOCALES = LOCALES
LOCALIZED_UI_CAPTURES = [
    "01-home-ui.png", "02-scanner-review-ui.png", "03-reader-search-ui.png",
    "04-compression-ui.png", "05-images-to-pdf-ui.png", "06-merge-ui.png",
    "06b-rearrange-ui.png", "07-privacy-ui.png", "08-result-ui.png",
    "09-settings-ui.png", "10-language-chooser-ui.png",
]


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
    screenshot_sets = [(PHONE, "en-US"), (T7, "en-US"), (T10, "en-US")]
    for locale in LOCALES:
        localized_base = ROOT / f"localized/upload-ready/{locale}"
        screenshot_sets.extend(
            (localized_base / device, locale)
            for device in ("phone", "tablet-7", "tablet-10")
        )
    for screenshot_dir, locale in screenshot_sets:
        screenshots = sorted(screenshot_dir.glob("*.png"))
        if len(screenshots) != 8:
            fail(errors, f"wrong screenshot count: {screenshot_dir.relative_to(ROOT)} has {len(screenshots)}")
        expected_prefix = re.compile(rf"\d{{2}}-{re.escape(locale)}-")
        for screenshot in screenshots:
            if not expected_prefix.match(screenshot.name):
                fail(errors, f"locale missing from screenshot filename: {screenshot.relative_to(ROOT)}")
    for name in PHONE_NAMES:
        image_check(errors,PHONE/name,(1080,1920),True)
    for base,label in [(T7,"tablet7"),(T10,"tablet10")]:
        for i,slug in enumerate(SLUGS,1):
            image_check(errors,base/f"{i:02d}-en-US-{slug}-{label}.png",(1920,1080),True)
    image_check(errors,ROOT/"feature-graphic/utility/quietpdf-feature-utility.png",(1024,500),True)
    image_check(errors,ROOT/"feature-graphic/privacy/quietpdf-feature-privacy.png",(1024,500),True)
    for locale in LOCALES:
        base=ROOT/f"localized/upload-ready/{locale}"
        capture_base=ROOT/f"source/real-ui-captures-no-ads/localized/{locale}"
        for name in LOCALIZED_UI_CAPTURES:
            image_check(errors,capture_base/name)
        for i,slug in enumerate(SLUGS,1):
            image_check(errors,base/"phone"/f"{i:02d}-{locale}-{slug}.png",(1080,1920),True)
        for folder,label in [("tablet-7","tablet7"),("tablet-10","tablet10")]:
            for i,slug in enumerate(SLUGS,1):
                image_check(errors,base/folder/f"{i:02d}-{locale}-{slug}-{label}.png",(1920,1080),True)
        image_check(errors,base/"feature-graphic/utility/quietpdf-feature-utility.png",(1024,500),True)
        image_check(errors,base/"feature-graphic/privacy/quietpdf-feature-privacy.png",(1024,500),True)
    image_check(errors,ROOT/"contact-sheets/quietpdf-all-features-contact-sheet.png",(2400,3600),True)
    for locale in CONTACT_LOCALES:
        image_check(errors,ROOT/f"contact-sheets/quietpdf-all-features-{locale}-contact-sheet.png",(2400,3600),True)
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
    prod_roots=[ROOT/"play-upload",ROOT/"feature-graphic",ROOT/"branding/selected",ROOT/"marketing-not-for-play",ROOT/"localized/upload-ready"]
    for base in prod_roots:
        for p in base.rglob("*"):
            if p.is_file() and "DRAFT" in p.name.upper():fail(errors,f"DRAFT in production path: {p.relative_to(ROOT)}")
            if p.suffix.lower() in {".png",".jpg",".jpeg"} and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*",p.name):
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
    print(f"Localized upload-ready sets: {len(LOCALES)} locales × 26 RGB assets")
    print(f"Play icon: 512x512 RGB, {icon.stat().st_size} bytes")
    print("NO ADS: VERIFIED for every inventoried raster asset")
    return 0


if __name__ == "__main__":
    sys.exit(main())
