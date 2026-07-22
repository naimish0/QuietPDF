#!/usr/bin/env python3
"""Focused technical validation for QuietPDF icon-v3 and Android launcher resources."""

from __future__ import annotations

import csv
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
APP_RES = REPO / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"

REQUIRED = [
    "README.md",
    "research/existing-icon-audit.md",
    "research/competitor-icon-audit.md",
    "research/candidate-scorecard.md",
    "research/conversion-experiment-plan.md",
    "production/source-svg/quietpdf-document-q.svg",
    "production/play-store/quietpdf-play-icon-512.png",
    "production/theme-variants/quietpdf-dark-launcher-512.png",
    "production/adaptive/quietpdf-adaptive-foreground.svg",
    "production/adaptive/quietpdf-adaptive-background.svg",
    "production/monochrome/quietpdf-monochrome.svg",
    "experiments/control/quietpdf-control-512.png",
    "experiments/variant-a-production/quietpdf-variant-a-512.png",
    "experiments/variant-b-runner-up/quietpdf-variant-b-512.png",
    "previews/contact-sheets/candidate-contact-sheet.png",
    "previews/contact-sheets/finalist-contact-sheet.png",
    "previews/small-sizes/production-small-sizes.png",
    "previews/masks/production-mask-sheet.png",
    "previews/launcher-light/production-light.png",
    "previews/launcher-dark/production-dark.png",
    "previews/themed-icons/production-themed-icons.png",
    "previews/splash/android-12-splash-preview.png",
    "qa/technical-validation.md",
    "qa/visual-validation.md",
    "qa/similarity-review.md",
    "qa/asset-inventory.csv",
]


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def check_play_icon(errors: list[str], relative: str) -> None:
    path = ROOT / relative
    if not path.is_file():
        fail(errors, f"Missing Play icon: {relative}")
        return
    try:
        with Image.open(path) as image:
            image.load()
            if image.size != (512, 512):
                fail(errors, f"{relative}: expected 512x512, got {image.size}")
            if image.mode != "RGBA":
                fail(errors, f"{relative}: expected 32-bit RGBA, got {image.mode}")
            if image.info.get("srgb") is None:
                fail(errors, f"{relative}: missing explicit sRGB PNG chunk")
            alpha = image.getchannel("A")
            if alpha.getextrema() != (255, 255):
                fail(errors, f"{relative}: background is not fully opaque")
            for point in ((0, 0), (511, 0), (0, 511), (511, 511)):
                if image.getpixel(point)[3] != 255:
                    fail(errors, f"{relative}: transparent/pre-rounded corner at {point}")
    except Exception as exc:
        fail(errors, f"{relative}: PNG decode failed: {exc}")
    if path.stat().st_size > 1024 * 1024:
        fail(errors, f"{relative}: exceeds 1024 KB")


def check_adaptive_xml(errors: list[str]) -> None:
    for api in ("mipmap-anydpi-v26", "mipmap-anydpi-v33"):
        for name in (
            "ic_launcher.xml",
            "ic_launcher_round.xml",
            "ic_launcher_dark.xml",
            "ic_launcher_dark_round.xml",
        ):
            path = APP_RES / api / name
            try:
                root = ET.parse(path).getroot()
            except Exception as exc:
                fail(errors, f"Invalid adaptive XML {path.relative_to(REPO)}: {exc}")
                continue
            expected = {"background", "foreground"}
            if api.endswith("v33"):
                expected.add("monochrome")
            found = {child.tag.split("}")[-1] for child in root}
            if not expected.issubset(found):
                fail(errors, f"{path.relative_to(REPO)}: missing {sorted(expected - found)}")
            for child in root:
                ref = child.attrib.get(ANDROID + "drawable", "")
                match = re.fullmatch(r"@drawable/([a-z0-9_]+)", ref)
                if not match or not (APP_RES / "drawable" / f"{match.group(1)}.xml").is_file():
                    fail(errors, f"{path.relative_to(REPO)}: unresolved drawable {ref}")


def check_legacy(errors: list[str]) -> None:
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, size in densities.items():
        for stem in ("ic_launcher", "ic_launcher_round", "ic_launcher_dark", "ic_launcher_dark_round"):
            export = ROOT / f"production/legacy/{stem}-{density}.png"
            app = APP_RES / f"mipmap-{density}/{stem}.webp"
            for path in (export, app):
                if not path.is_file():
                    fail(errors, f"Missing legacy icon: {path.relative_to(REPO)}")
                    continue
                try:
                    with Image.open(path) as image:
                        if image.size != (size, size):
                            fail(errors, f"{path.relative_to(REPO)}: expected {size}x{size}, got {image.size}")
                except Exception as exc:
                    fail(errors, f"{path.relative_to(REPO)}: decode failed: {exc}")


def check_inventory(errors: list[str]) -> None:
    inventory_path = ROOT / "qa/asset-inventory.csv"
    if not inventory_path.is_file():
        return
    with inventory_path.open(newline="", encoding="utf-8") as handle:
        listed = {row["path"] for row in csv.DictReader(handle)}
    actual = {
        path.relative_to(ROOT).as_posix()
        for path in ROOT.rglob("*")
        if path.is_file() and path.name != "asset-inventory.csv" and "tools" not in path.parts
    }
    missing = actual - listed
    stale = listed - actual
    if missing:
        fail(errors, f"Inventory missing {sorted(missing)}")
    if stale:
        fail(errors, f"Inventory contains stale paths {sorted(stale)}")


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            fail(errors, f"Missing required file: {relative}")
    for slug in ("candidate-a-document-q", "candidate-b-folded-q", "candidate-c-page-stack-q", "candidate-d-category-first"):
        for name in ("source.svg",) + tuple(f"icon-{size}.png" for size in (512, 128, 64, 48, 32, 24, 16)):
            if not (ROOT / "candidates" / slug / name).is_file():
                fail(errors, f"Missing candidate asset: candidates/{slug}/{name}")
    for relative in (
        "production/play-store/quietpdf-play-icon-512.png",
        "experiments/control/quietpdf-control-512.png",
        "experiments/variant-a-production/quietpdf-variant-a-512.png",
        "experiments/variant-b-runner-up/quietpdf-variant-b-512.png",
    ):
        check_play_icon(errors, relative)
    check_adaptive_xml(errors)
    check_legacy(errors)
    check_inventory(errors)
    raster_names = " ".join(path.name.lower() for path in ROOT.rglob("*.png"))
    for forbidden in ("adobe", "camscanner", "wps", "ilovepdf", "xodo", "foxit", "smallpdf", "pdfelement", "mobioffice"):
        if forbidden in raster_names:
            fail(errors, f"Possible committed competitor artwork filename contains: {forbidden}")
    for path in ROOT.rglob("*"):
        if path.is_file() and "placeholder" in path.name.lower():
            fail(errors, f"Placeholder-like filename remains: {path.relative_to(ROOT)}")
    if errors:
        print("QuietPDF icon-v3 validation: FAIL")
        for error in errors:
            print(f"- {error}")
        return 1
    icon = ROOT / "production/play-store/quietpdf-play-icon-512.png"
    print("QuietPDF icon-v3 validation: PASS")
    print(f"Production Play icon: 512x512 RGBA sRGB, {icon.stat().st_size} bytes")
    print("Adaptive foreground/background/monochrome references: PASS")
    print("Legacy density exports and installed WebP fallbacks: PASS")
    print("Required candidates, previews, experiment assets, and inventory: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
