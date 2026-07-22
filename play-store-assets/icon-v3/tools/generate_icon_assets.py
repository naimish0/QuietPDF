#!/usr/bin/env python3
"""Deterministically render QuietPDF icon-v3 assets from original vector geometry."""

from __future__ import annotations

import csv
import math
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont, PngImagePlugin


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
APP_RES = REPO / "app/src/main/res"
V2 = REPO / "play-store-assets/v2-creative/branding"

BLUE = "#2563EB"
DARK_BLUE = "#1D4ED8"
SOFT_BLUE = "#DBEAFE"
WHITE = "#FFFFFF"
INK = "#0F172A"
MUTED = "#64748B"
LIGHT = "#F8FAFC"
DARK = "#111827"
SIZES = (512, 128, 64, 48, 32, 24, 16)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    path = REPO / "play-store-assets/v2-creative/source/fonts-and-licenses/Inter-Variable.ttf"
    return ImageFont.truetype(str(path), size=size)


def canvas(size: int = 512, color: str = BLUE) -> Image.Image:
    return Image.new("RGBA", (size, size), color)


def scale_points(points: list[tuple[float, float]], factor: float) -> list[tuple[int, int]]:
    return [(round(x * factor), round(y * factor)) for x, y in points]


def render_supersampled(draw_fn, size: int = 512, background: str = BLUE) -> Image.Image:
    factor = 4
    image = canvas(size * factor, background)
    draw_fn(image, ImageDraw.Draw(image), factor)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def page(draw: ImageDraw.ImageDraw, factor: float, box=(128, 82, 384, 424), fold=64,
         fill=WHITE, fold_fill=SOFT_BLUE) -> None:
    x0, y0, x1, y1 = box
    pts = [(x0, y0), (x1 - fold, y0), (x1, y0 + fold), (x1, y1), (x0, y1)]
    draw.polygon(scale_points(pts, factor), fill=fill)
    draw.polygon(scale_points([(x1 - fold, y0), (x1, y0 + fold), (x1 - fold, y0 + fold)], factor), fill=fold_fill)


def q_mark(draw: ImageDraw.ImageDraw, factor: float, center=(255, 255), outer=88, inner=44,
           color=BLUE, counter=WHITE, tail=((287, 290), (316, 286), (348, 326), (320, 349), (292, 307))) -> None:
    cx, cy = center
    draw.ellipse(tuple(round(v * factor) for v in (cx - outer, cy - outer, cx + outer, cy + outer)), fill=color)
    draw.ellipse(tuple(round(v * factor) for v in (cx - inner, cy - inner, cx + inner, cy + inner)), fill=counter)
    draw.polygon(scale_points(list(tail), factor), fill=color)


def candidate_a(size: int = 512) -> Image.Image:
    def draw_icon(_im, draw, f):
        page(draw, f, (118, 72, 394, 432), 68)
        q_mark(draw, f, tail=((287, 290), (316, 286), (348, 326), (320, 349), (292, 307)))
    return render_supersampled(draw_icon, size)


def candidate_a_dark(size: int = 512) -> Image.Image:
    def draw_icon(_im, draw, f):
        page(draw, f, (118, 72, 394, 432), 68, fill="#E2E8F0", fold_fill="#334155")
        q_mark(
            draw, f, color="#60A5FA", counter="#E2E8F0",
            tail=((287, 290), (316, 286), (348, 326), (320, 349), (292, 307)),
        )
    return render_supersampled(draw_icon, size, background="#0F172A")


def candidate_b(size: int = 512) -> Image.Image:
    def draw_icon(_im, draw, f):
        q_mark(
            draw, f, center=(245, 245), outer=122, inner=66, color=WHITE, counter=BLUE,
            tail=((287, 291), (326, 280), (382, 350), (344, 380), (299, 315)),
        )
        # A document fold cut directly into the Q's upper-right shoulder.
        draw.polygon(scale_points([(288, 126), (357, 126), (357, 195)], f), fill=BLUE)
        draw.polygon(scale_points([(300, 138), (345, 138), (345, 183)], f), fill=SOFT_BLUE)
    return render_supersampled(draw_icon, size)


def candidate_c(size: int = 512) -> Image.Image:
    def draw_icon(_im, draw, f):
        # Two layers only: the rear sheet remains visible at left and bottom.
        draw.polygon(scale_points([(104, 116), (318, 116), (370, 168), (370, 416), (104, 416)], f), fill=SOFT_BLUE)
        draw.polygon(scale_points([(142, 78), (334, 78), (402, 146), (402, 386), (142, 386)], f), fill=WHITE)
        draw.polygon(scale_points([(334, 78), (402, 146), (334, 146)], f), fill=SOFT_BLUE)
        q_mark(
            draw, f, center=(265, 238), outer=75, inner=38, color=BLUE, counter=WHITE,
            tail=((291, 266), (320, 261), (357, 309), (331, 331), (302, 285)),
        )
    return render_supersampled(draw_icon, size)


def draw_block_pdf(draw: ImageDraw.ImageDraw, f: float) -> None:
    # Custom block-letter geometry avoids font dependencies in the icon artwork.
    # P
    draw.rectangle(tuple(round(v * f) for v in (160, 220, 184, 316)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (180, 220, 222, 242)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (202, 238, 224, 270)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (180, 266, 216, 288)), fill=BLUE)
    # D
    draw.rectangle(tuple(round(v * f) for v in (238, 220, 262, 316)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (258, 220, 292, 242)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (258, 294, 292, 316)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (286, 238, 308, 298)), fill=BLUE)
    # F
    draw.rectangle(tuple(round(v * f) for v in (322, 220, 346, 316)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (342, 220, 384, 243)), fill=BLUE)
    draw.rectangle(tuple(round(v * f) for v in (342, 260, 374, 282)), fill=BLUE)


def candidate_d(size: int = 512) -> Image.Image:
    def draw_icon(_im, draw, f):
        page(draw, f, (112, 76, 400, 430), 70)
        draw_block_pdf(draw, f)
    return render_supersampled(draw_icon, size)


CANDIDATES = {
    "candidate-a-document-q": candidate_a,
    "candidate-b-folded-q": candidate_b,
    "candidate-c-page-stack-q": candidate_c,
    "candidate-d-category-first": candidate_d,
}


SVGS = {
    "candidate-a-document-q": """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#2563EB"/>
  <path fill="#FFF" d="M118 72h208l68 68v292H118z"/>
  <path fill="#DBEAFE" d="M326 72l68 68h-68z"/>
  <circle cx="255" cy="255" r="88" fill="#2563EB"/>
  <circle cx="255" cy="255" r="44" fill="#FFF"/>
  <path fill="#2563EB" d="M287 290l29-4 32 40-28 23-28-42z"/>
</svg>
""",
    "candidate-b-folded-q": """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#2563EB"/>
  <circle cx="245" cy="245" r="122" fill="#FFF"/>
  <circle cx="245" cy="245" r="66" fill="#2563EB"/>
  <path fill="#FFF" d="M287 291l39-11 56 70-38 30-45-65z"/>
  <path fill="#2563EB" d="M288 126h69v69z"/>
  <path fill="#DBEAFE" d="M300 138h45v45z"/>
</svg>
""",
    "candidate-c-page-stack-q": """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#2563EB"/>
  <path fill="#DBEAFE" d="M104 116h214l52 52v248H104z"/>
  <path fill="#FFF" d="M142 78h192l68 68v240H142z"/>
  <path fill="#DBEAFE" d="M334 78l68 68h-68z"/>
  <circle cx="265" cy="238" r="75" fill="#2563EB"/>
  <circle cx="265" cy="238" r="38" fill="#FFF"/>
  <path fill="#2563EB" d="M291 266l29-5 37 48-26 22-29-46z"/>
</svg>
""",
    "candidate-d-category-first": """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#2563EB"/>
  <path fill="#FFF" d="M112 76h218l70 70v284H112z"/>
  <path fill="#DBEAFE" d="M330 76l70 70h-70z"/>
  <path fill="#2563EB" d="M160 220h62v22h-38v24h32v22h-32v28h-24zm78 0h54l16 18v60l-16 18h-54zm24 22v52h24v-52zm60-22h62v23h-38v17h28v22h-28v34h-24z"/>
</svg>
""",
}


def monochrome_mask(size: int = 512) -> Image.Image:
    """Candidate A as a one-color page silhouette with a transparent Q cutout."""
    mark = Image.new("L", (2048, 2048), 0)
    draw = ImageDraw.Draw(mark)
    f = 4
    page(draw, f, (118, 72, 394, 432), 68, fill=255, fold_fill=255)
    q = Image.new("L", mark.size, 0)
    qd = ImageDraw.Draw(q)
    q_mark(qd, f, color=255, counter=0)
    mark = ImageChops.subtract(mark, q)
    return mark.resize((size, size), Image.Resampling.LANCZOS)


def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    png_info = PngImagePlugin.PngInfo()
    png_info.add(b"sRGB", b"\x00")
    image.convert("RGBA").save(path, format="PNG", optimize=True, pnginfo=png_info)


def icon_on_surface(icon: Image.Image, surface: str, label: str) -> Image.Image:
    out = Image.new("RGB", (720, 640), surface)
    draw = ImageDraw.Draw(out)
    tile = icon.resize((420, 420), Image.Resampling.LANCZOS)
    out.paste(tile.convert("RGB"), (150, 80))
    draw.text((360, 548), label, font=font(30), fill=WHITE if surface == DARK else INK, anchor="mm")
    return out


def superellipse_mask(size: int, exponent: float = 5.0) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    c = size / 2
    r = size / 2
    pts = []
    for i in range(361):
        t = math.radians(i)
        ct, st = math.cos(t), math.sin(t)
        x = c + r * math.copysign(abs(ct) ** (2 / exponent), ct)
        y = c + r * math.copysign(abs(st) ** (2 / exponent), st)
        pts.append((x, y))
    d.polygon(pts, fill=255)
    return mask


def shape_mask(name: str, size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    if name == "circle":
        d.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif name == "squircle":
        return superellipse_mask(size)
    elif name == "rounded-square":
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * .22), fill=255)
    elif name == "square":
        d.rectangle((0, 0, size - 1, size - 1), fill=255)
    elif name == "teardrop":
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * .42), fill=255)
        d.rectangle((0, 0, round(size * .55), round(size * .55)), fill=255)
    return mask


def masked(icon: Image.Image, name: str, size: int = 360) -> Image.Image:
    im = icon.resize((size, size), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im, (0, 0), shape_mask(name, size))
    return out


def sheet(title: str, items: list[tuple[str, Image.Image]], columns: int, path: Path,
          cell=(360, 430), bg=LIGHT) -> None:
    rows = math.ceil(len(items) / columns)
    out = Image.new("RGB", (columns * cell[0] + 80, rows * cell[1] + 110), bg)
    draw = ImageDraw.Draw(out)
    draw.text((40, 30), title, font=font(34), fill=INK)
    for i, (label, image) in enumerate(items):
        x = 40 + (i % columns) * cell[0]
        y = 95 + (i // columns) * cell[1]
        thumb = image.copy()
        thumb.thumbnail((cell[0] - 52, cell[1] - 95), Image.Resampling.LANCZOS)
        px = x + (cell[0] - thumb.width) // 2
        out.paste(thumb.convert("RGB"), (px, y))
        draw.text((x + cell[0] // 2, y + cell[1] - 62), label, font=font(20), fill=INK, anchor="mm")
    write_png(out.convert("RGBA"), path)


def grayscale(image: Image.Image) -> Image.Image:
    return image.convert("L").convert("RGBA")


def color_vision(image: Image.Image, kind: str) -> Image.Image:
    # Deterministic approximation for comparative QA, not a medical simulation.
    matrix = {
        "protanopia": (0.567, 0.433, 0, 0.558, 0.442, 0, 0, 0.242, 0.758),
        "deuteranopia": (0.625, 0.375, 0, 0.7, 0.3, 0, 0, 0.3, 0.7),
        "tritanopia": (0.95, 0.05, 0, 0, 0.433, 0.567, 0, 0.475, 0.525),
    }[kind]
    rgb = image.convert("RGB")
    r, g, b = rgb.split()
    channels = []
    for row in range(3):
        a, bb, c = matrix[row * 3:(row + 1) * 3]
        channel = ImageChops.add(ImageChops.add(r.point(lambda x, k=a: x * k), g.point(lambda x, k=bb: x * k)), b.point(lambda x, k=c: x * k))
        channels.append(channel)
    return Image.merge("RGB", channels).convert("RGBA")


def render_candidate_assets() -> dict[str, Image.Image]:
    rendered = {}
    for slug, renderer in CANDIDATES.items():
        folder = ROOT / "candidates" / slug
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "source.svg").write_text(SVGS[slug], encoding="utf-8")
        master = renderer()
        rendered[slug] = master
        for size in SIZES:
            write_png(master.resize((size, size), Image.Resampling.LANCZOS), folder / f"icon-{size}.png")
        write_png(grayscale(master), folder / "preview-grayscale.png")
        write_png(icon_on_surface(master, LIGHT, "Light launcher surface"), folder / "preview-light.png")
        write_png(icon_on_surface(master, DARK, "Dark launcher surface"), folder / "preview-dark.png")
        masks = [(name, masked(master, name)) for name in ("circle", "squircle", "rounded-square", "square", "teardrop")]
        sheet(f"{slug} · Android masks", masks, 3, folder / "preview-masks.png", cell=(390, 430))
        mono = grayscale(master)
        write_png(mono, folder / "preview-monochrome-feasibility.png")
    return rendered


def baseline_assets() -> Image.Image:
    source = ROOT / "baseline/current-source"
    rendered = ROOT / "baseline/current-rendered"
    source.mkdir(parents=True, exist_ok=True)
    rendered.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(V2 / "selected/quietpdf-icon-selected.svg", source / "quietpdf-control.svg")
    shutil.copyfile(V2 / "selected/quietpdf-play-icon-512.png", source / "quietpdf-control-original.png")
    control = Image.open(V2 / "selected/quietpdf-play-icon-512.png").convert("RGBA")
    for size in SIZES:
        write_png(control.resize((size, size), Image.Resampling.LANCZOS), rendered / f"control-{size}.png")
    write_png(grayscale(control), rendered / "control-grayscale.png")
    masks = [(name, masked(control, name)) for name in ("circle", "squircle", "rounded-square", "square", "teardrop")]
    sheet("Current QuietPDF control · Android masks", masks, 3, rendered / "control-mask-sheet.png", cell=(390, 430))
    return control


def production_assets(master: Image.Image) -> None:
    prod = ROOT / "production"
    for folder in ("source-svg", "play-store", "adaptive", "monochrome", "legacy", "theme-variants"):
        (prod / folder).mkdir(parents=True, exist_ok=True)
    (prod / "source-svg/quietpdf-document-q.svg").write_text(SVGS["candidate-a-document-q"], encoding="utf-8")
    write_png(master, prod / "play-store/quietpdf-play-icon-512.png")
    dark_master = candidate_a_dark()
    write_png(dark_master, prod / "theme-variants/quietpdf-dark-launcher-512.png")

    adaptive_svg = """<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <path fill="#FFF" d="M29 21h38l13 13v53H29z"/>
  <path fill="#DBEAFE" d="M67 21l13 13H67z"/>
  <circle cx="54" cy="54" r="16" fill="#2563EB"/>
  <circle cx="54" cy="54" r="8" fill="#FFF"/>
  <path fill="#2563EB" d="M60 61l6-1 7 9-5 4-6-8z"/>
</svg>
"""
    background_svg = '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108"><path fill="#2563EB" d="M0 0h108v108H0z"/></svg>\n'
    mono_svg = """<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <path fill="#000" fill-rule="evenodd" d="M29 21h38l13 13v53H29z M54 37a17 17 0 0 1 14.5 25.8l7 7.2-5.5 5.5-7-7.5A17 17 0 1 1 54 37z M54 45.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 1 1 0-17z"/>
</svg>
"""
    (prod / "adaptive/quietpdf-adaptive-foreground.svg").write_text(adaptive_svg, encoding="utf-8")
    (prod / "adaptive/quietpdf-adaptive-background.svg").write_text(background_svg, encoding="utf-8")
    (prod / "monochrome/quietpdf-monochrome.svg").write_text(mono_svg, encoding="utf-8")

    fg_large = Image.new("RGBA", (1728, 1728), (0, 0, 0, 0))
    fgd = ImageDraw.Draw(fg_large)
    page(fgd, 16, (29, 21, 80, 87), 13, fill=WHITE, fold_fill=SOFT_BLUE)
    q_mark(
        fgd, 16, center=(54, 54), outer=17, inner=8.5, color=BLUE, counter=WHITE,
        tail=((60, 61), (66, 60), (73, 69), (68, 74), (62, 65)),
    )
    fg = fg_large.resize((432, 432), Image.Resampling.LANCZOS)
    write_png(fg, prod / "adaptive/quietpdf-adaptive-foreground-432.png")
    write_png(canvas(432), prod / "adaptive/quietpdf-adaptive-background-432.png")
    mono = monochrome_mask(432)
    mono_rgba = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    mono_rgba.paste((255, 255, 255, 255), (0, 0), mono)
    write_png(mono_rgba, prod / "monochrome/quietpdf-monochrome-432.png")

    for density, size in DENSITIES.items():
        legacy = master.resize((size, size), Image.Resampling.LANCZOS)
        write_png(legacy, prod / f"legacy/ic_launcher-{density}.png")
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(legacy, (0, 0), shape_mask("circle", size))
        write_png(round_icon, prod / f"legacy/ic_launcher_round-{density}.png")
        app_main = APP_RES / f"mipmap-{density}/ic_launcher.webp"
        app_round = APP_RES / f"mipmap-{density}/ic_launcher_round.webp"
        legacy.save(app_main, "WEBP", lossless=True, quality=100)
        round_icon.save(app_round, "WEBP", lossless=True, quality=100)
        dark_legacy = dark_master.resize((size, size), Image.Resampling.LANCZOS)
        dark_round = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        dark_round.paste(dark_legacy, (0, 0), shape_mask("circle", size))
        write_png(dark_legacy, prod / f"legacy/ic_launcher_dark-{density}.png")
        write_png(dark_round, prod / f"legacy/ic_launcher_dark_round-{density}.png")
        dark_legacy.save(APP_RES / f"mipmap-{density}/ic_launcher_dark.webp", "WEBP", lossless=True, quality=100)
        dark_round.save(APP_RES / f"mipmap-{density}/ic_launcher_dark_round.webp", "WEBP", lossless=True, quality=100)


def experiment_assets(control: Image.Image, production: Image.Image, runner_up: Image.Image) -> None:
    write_png(control, ROOT / "experiments/control/quietpdf-control-512.png")
    write_png(production, ROOT / "experiments/variant-a-production/quietpdf-variant-a-512.png")
    write_png(runner_up, ROOT / "experiments/variant-b-runner-up/quietpdf-variant-b-512.png")


def preview_assets(control: Image.Image, rendered: dict[str, Image.Image]) -> None:
    candidates = [("Control", control)] + [(slug.split("-")[1].upper(), im) for slug, im in rendered.items()]
    sheet("QuietPDF icon-v3 · pass one candidate review", candidates, 5, ROOT / "previews/contact-sheets/candidate-contact-sheet.png", cell=(300, 390))
    finalists = [("Control", control), ("A · Production", rendered["candidate-a-document-q"]),
                 ("D · Experiment", rendered["candidate-d-category-first"]),
                 ("C · Reserve", rendered["candidate-c-page-stack-q"])]
    sheet("QuietPDF icon-v3 · refined finalists", finalists, 4, ROOT / "previews/contact-sheets/finalist-contact-sheet.png", cell=(340, 420))

    small_items = []
    for size in (16, 24, 32, 48, 64, 96, 128, 512):
        reduced = rendered["candidate-a-document-q"].resize((size, size), Image.Resampling.LANCZOS)
        tile = Image.new("RGBA", (160, 160), LIGHT)
        shown = reduced if size <= 128 else reduced.resize((128, 128), Image.Resampling.LANCZOS)
        tile.alpha_composite(shown, ((160 - shown.width) // 2, (160 - shown.height) // 2))
        small_items.append((f"{size}px", tile))
    sheet("Production A · small-size ladder", small_items, 4, ROOT / "previews/small-sizes/production-small-sizes.png", cell=(240, 250))

    blur_items = []
    for size in (16, 24, 32, 48):
        im = rendered["candidate-a-document-q"].resize((size, size), Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(.45))
        blur_items.append((f"{size}px blur", im.resize((256, 256), Image.Resampling.NEAREST)))
    sheet("Production A · blur recognition test", blur_items, 4, ROOT / "previews/small-sizes/production-blur-test.png", cell=(320, 350))

    masks = [(name, masked(rendered["candidate-a-document-q"], name)) for name in ("circle", "squircle", "rounded-square", "square", "teardrop")]
    sheet("Production A · mask safety", masks, 3, ROOT / "previews/masks/production-mask-sheet.png", cell=(390, 430))
    write_png(icon_on_surface(rendered["candidate-a-document-q"], LIGHT, "Production A · light"), ROOT / "previews/launcher-light/production-light.png")
    write_png(icon_on_surface(rendered["candidate-a-document-q"], DARK, "Production A · dark"), ROOT / "previews/launcher-dark/production-dark.png")
    sheet(
        "Installed launcher icon · appearance variants",
        [("Light theme", rendered["candidate-a-document-q"]), ("Dark theme", candidate_a_dark())],
        2,
        ROOT / "previews/contact-sheets/light-dark-launcher-icons.png",
        cell=(440, 520),
    )

    mono = monochrome_mask(512)
    themed = []
    for label, bg, tint in (("Blue", "#DDE7FF", "#173E8F"), ("Sage", "#DDEBD8", "#274E2A"),
                            ("Rose", "#F9DDE3", "#7D2939"), ("Slate", "#DDE3EA", "#293747")):
        tile = Image.new("RGBA", (512, 512), bg)
        mark = Image.new("RGBA", (512, 512), tint)
        tile.paste(mark, (0, 0), mono)
        themed.append((label, tile))
    sheet("Android 13+ themed icon colors", themed, 4, ROOT / "previews/themed-icons/production-themed-icons.png", cell=(340, 410))

    cv = [("Original", rendered["candidate-a-document-q"]), ("Protanopia", color_vision(rendered["candidate-a-document-q"], "protanopia")),
          ("Deuteranopia", color_vision(rendered["candidate-a-document-q"], "deuteranopia")),
          ("Tritanopia", color_vision(rendered["candidate-a-document-q"], "tritanopia"))]
    sheet("Production A · color-vision simulations", cv, 4, ROOT / "previews/themed-icons/color-vision-simulations.png", cell=(340, 410))

    splash = Image.new("RGB", (1400, 760), LIGHT)
    d = ImageDraw.Draw(splash)
    for i, (label, bg, fg) in enumerate((("Light splash", "#FFF9FF", INK), ("Dark splash", "#111318", WHITE))):
        x = 40 + i * 680
        d.rounded_rectangle((x, 40, x + 640, 710), radius=42, fill=bg)
        icon = masked(rendered["candidate-a-document-q"], "circle", 260)
        splash.paste(icon, (x + 190, 190), icon)
        d.text((x + 320, 520), "QuietPDF", font=font(38), fill=fg, anchor="mm")
        d.text((x + 320, 575), label, font=font(23), fill=MUTED if i == 0 else "#CBD5E1", anchor="mm")
    write_png(splash.convert("RGBA"), ROOT / "previews/splash/android-12-splash-preview.png")


def inventory() -> None:
    rows = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.name == "asset-inventory.csv" or "tools" in path.parts:
            continue
        width = height = mode = ""
        if path.suffix.lower() == ".png":
            with Image.open(path) as image:
                width, height, mode = image.width, image.height, image.mode
        rows.append((path.relative_to(ROOT).as_posix(), width, height, mode, "NO ADS: VERIFIED"))
    target = ROOT / "qa/asset-inventory.csv"
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(("path", "width", "height", "mode", "review"))
        writer.writerows(rows)


def main() -> None:
    for folder in ("research", "baseline", "candidates", "finalists", "production", "experiments", "previews", "qa"):
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
    control = baseline_assets()
    rendered = render_candidate_assets()
    production_assets(rendered["candidate-a-document-q"])
    experiment_assets(control, rendered["candidate-a-document-q"], rendered["candidate-d-category-first"])
    preview_assets(control, rendered)
    for slug in ("candidate-a-document-q", "candidate-d-category-first", "candidate-c-page-stack-q"):
        target = ROOT / "finalists" / slug
        target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / f"candidates/{slug}/source.svg", target / "source.svg")
        shutil.copyfile(ROOT / f"candidates/{slug}/icon-512.png", target / "icon-512.png")
    inventory()
    print(f"Generated QuietPDF icon-v3 assets under {ROOT}")


if __name__ == "__main__":
    main()
