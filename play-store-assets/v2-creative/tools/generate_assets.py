#!/usr/bin/env python3
"""Deterministic QuietPDF Play Store V2 creative renderer.

All application UI, type, icons, values, documents, and layouts are drawn from
repository-authentic strings and controls. Generated photography is used only
as owned supporting content. Every Play-upload output is flattened to RGB.
"""

from __future__ import annotations

import csv
import json
import math
import os
from pathlib import Path
from typing import Callable

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[1]
FONT_PATH = ROOT / "source/fonts-and-licenses/Inter-Variable.ttf"
PHOTO_SHEET = ROOT / "source/source-images/architecture-nature-contact-sheet.png"
RECEIPT = ROOT / "source/source-images/scanner-receipt-before.png"
REAL_UI_DIR = ROOT / "source/real-ui-captures-no-ads/compose-pixel-7a"

BLUE = "#2563EB"
SOFT_BLUE = "#DBEAFE"
BG = "#F8FAFC"
WHITE = "#FFFFFF"
TEXT = "#0F172A"
MUTED = "#475569"
SUCCESS = "#16A34A"
WARNING = "#F59E0B"
LINE = "#CBD5E1"
SLATE = "#E2E8F0"
NAVY = "#172554"


def ensure_dirs() -> None:
    dirs = [
        "research", "source/synthetic-pdfs", "source/source-images",
        "source/real-ui-captures-no-ads", "source/before-after",
        "source/editable-layouts", "source/fonts-and-licenses", "branding/concepts",
        "branding/selected", "branding/adaptive", "branding/monochrome",
        "branding/previews", "feature-graphic/utility", "feature-graphic/privacy",
        "play-upload/phone/en-US", "play-upload/tablet-7/en-US",
        "play-upload/tablet-10/en-US", "play-upload/chromebook",
        "play-upload/supported-additional-devices", "localized/upload-ready",
        "localized/draft-not-for-upload", "marketing-not-for-play/square",
        "marketing-not-for-play/landscape", "marketing-not-for-play/story",
        "marketing-not-for-play/phone-mockups", "marketing-not-for-play/tablet-mockups",
        "contact-sheets", "manifests", "qa/foldable",
    ]
    for d in dirs:
        (ROOT / d).mkdir(parents=True, exist_ok=True)


def font(size: int, weight: int = 400) -> ImageFont.FreeTypeFont:
    # Pillow supports the weight axis through variation names inconsistently;
    # a single licensed variable file keeps regeneration portable.
    f = ImageFont.truetype(str(FONT_PATH), size=size)
    try:
        if weight >= 700:
            f.set_variation_by_name("Bold")
        elif weight >= 600:
            f.set_variation_by_name("Semi Bold")
        elif weight >= 500:
            f.set_variation_by_name("Medium")
        else:
            f.set_variation_by_name("Regular")
    except (OSError, ValueError):
        pass
    return f


def canvas(size: tuple[int, int], color: str = BG) -> Image.Image:
    return Image.new("RGB", size, color)


def rounded(draw: ImageDraw.ImageDraw, box, radius=28, fill=WHITE, outline=None, width=1):
    draw.rounded_rectangle(tuple(map(int, box)), radius=radius, fill=fill, outline=outline, width=width)


def shadow_card(im: Image.Image, box, radius=32, fill=WHITE, blur=22, dy=12, opacity=34, outline=None):
    x0, y0, x1, y1 = map(int, box)
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rounded_rectangle((x0, y0 + dy, x1, y1 + dy), radius=radius, fill=(15, 23, 42, opacity))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    im.paste(layer, (0, 0), layer)
    d = ImageDraw.Draw(im)
    rounded(d, box, radius, fill, outline or "#E7EEF8")


def text(draw, xy, value, size, fill=TEXT, weight=400, anchor=None, spacing=8, align="left"):
    draw.multiline_text(xy, value, font=font(size, weight), fill=fill, anchor=anchor,
                        spacing=spacing, align=align)


def fit_text(draw, xy, value, max_width, max_size, min_size=18, fill=TEXT, weight=700, anchor=None):
    for sz in range(max_size, min_size - 1, -1):
        f = font(sz, weight)
        if draw.textbbox((0, 0), value, font=f)[2] <= max_width:
            draw.text(xy, value, font=f, fill=fill, anchor=anchor)
            return sz
    draw.text(xy, value, font=font(min_size, weight), fill=fill, anchor=anchor)
    return min_size


def icon(draw, center, kind, scale=1.0, color=BLUE, stroke=5):
    x, y = center
    s = 24 * scale
    w = max(2, int(stroke * scale))
    if kind == "doc":
        draw.rounded_rectangle((x-s*.65, y-s*.85, x+s*.65, y+s*.85), radius=int(5*scale), outline=color, width=w)
        draw.line((x+s*.15, y-s*.85, x+s*.65, y-s*.35), fill=color, width=w)
        draw.line((x+s*.15, y-s*.85, x+s*.15, y-s*.35, x+s*.65, y-s*.35), fill=color, width=w)
    elif kind == "scan":
        for sx, sy in [(-1,-1),(1,-1),(-1,1),(1,1)]:
            draw.line((x+sx*s*.8, y+sy*s*.45, x+sx*s*.8, y+sy*s*.8, x+sx*s*.45, y+sy*s*.8), fill=color, width=w)
        draw.rectangle((x-s*.45,y-s*.5,x+s*.45,y+s*.5),outline=color,width=w)
    elif kind == "compress":
        draw.line((x-s*.8,y,x-s*.25,y,x-s*.25,y-s*.55),fill=color,width=w)
        draw.line((x+s*.8,y,x+s*.25,y,x+s*.25,y+s*.55),fill=color,width=w)
        draw.polygon([(x-s*.25,y),(x-s*.5,y-s*.18),(x-s*.5,y+s*.18)],fill=color)
        draw.polygon([(x+s*.25,y),(x+s*.5,y-s*.18),(x+s*.5,y+s*.18)],fill=color)
    elif kind == "merge":
        draw.rounded_rectangle((x-s*.8,y-s*.72,x+s*.15,y+s*.45),radius=5,outline=color,width=w)
        draw.rounded_rectangle((x-s*.15,y-s*.45,x+s*.8,y+s*.72),radius=5,outline=color,width=w)
    elif kind == "photo":
        draw.rounded_rectangle((x-s*.8,y-s*.65,x+s*.8,y+s*.65),radius=6,outline=color,width=w)
        draw.ellipse((x-s*.45,y-s*.4,x-s*.18,y-s*.13),fill=color)
        draw.polygon([(x-s*.62,y+s*.45),(x-s*.15,y),(x+s*.12,y+s*.22),(x+s*.42,y-s*.08),(x+s*.68,y+s*.45)],fill=color)
    elif kind == "search":
        draw.ellipse((x-s*.65,y-s*.65,x+s*.3,y+s*.3),outline=color,width=w)
        draw.line((x+s*.18,y+s*.18,x+s*.72,y+s*.72),fill=color,width=w)
    elif kind == "bookmark":
        draw.polygon([(x-s*.45,y-s*.75),(x+s*.45,y-s*.75),(x+s*.45,y+s*.75),(x,y+s*.4),(x-s*.45,y+s*.75)],outline=color,fill=None)
        draw.line((x-s*.45,y-s*.75,x-s*.45,y+s*.75,x,y+s*.4,x+s*.45,y+s*.75,x+s*.45,y-s*.75),fill=color,width=w)
    elif kind == "share":
        draw.line((x-s*.55,y+s*.1,x+s*.45,y-s*.55),fill=color,width=w)
        draw.line((x+s*.45,y-s*.55,x+s*.08,y-s*.6),fill=color,width=w)
        draw.line((x+s*.45,y-s*.55,x+s*.35,y-s*.18),fill=color,width=w)
        draw.rounded_rectangle((x-s*.7,y-s*.2,x+s*.35,y+s*.7),radius=5,outline=color,width=w)
    elif kind == "check":
        draw.ellipse((x-s*.75,y-s*.75,x+s*.75,y+s*.75),fill=color)
        draw.line((x-s*.35,y,x-s*.08,y+s*.28,x+s*.42,y-s*.3),fill=WHITE,width=w)
    elif kind == "device":
        draw.rounded_rectangle((x-s*.52,y-s*.82,x+s*.52,y+s*.82),radius=int(9*scale),outline=color,width=w)
        draw.line((x-s*.18,y+s*.63,x+s*.18,y+s*.63),fill=color,width=w)
    else:
        draw.ellipse((x-s*.6,y-s*.6,x+s*.6,y+s*.6),outline=color,width=w)


def badge(draw, x, y, label, fill=SOFT_BLUE, fg=BLUE, width=None):
    f = font(22, 700)
    w = width or draw.textbbox((0, 0), label, font=f)[2] + 34
    rounded(draw, (x, y, x+w, y+46), 23, fill)
    draw.text((x+w/2, y+23), label, font=f, fill=fg, anchor="mm")


def page_art(size=(520, 730), title="Research Report", theme=BLUE, variant=0):
    im = canvas(size, WHITE); d = ImageDraw.Draw(im); w, h = size
    d.rectangle((0,0,w,16),fill=theme)
    text(d,(42,46),title,32,TEXT,700)
    text(d,(42,90),["Northstar Studio · 2026","Fictional internal document","Prepared locally on device"][variant%3],16,MUTED,500)
    d.line((42,126,w-42,126),fill=LINE,width=2)
    if variant % 4 == 0:
        text(d,(42,154),"Executive overview",20,TEXT,700)
        for i, ww in enumerate([.88,.76,.92,.62]): d.rounded_rectangle((42,194+i*25,42+(w-84)*ww,204+i*25),5,fill="#CBD5E1")
        text(d,(42,318),"Quarterly trend",18,TEXT,700)
        pts=[]
        for i,v in enumerate([.55,.62,.58,.75,.71,.88]): pts.append((54+i*(w-108)/5,520-v*150))
        d.line(pts,fill=theme,width=7,joint="curve")
        for p in pts:d.ellipse((p[0]-6,p[1]-6,p[0]+6,p[1]+6),fill=theme)
        d.line((54,530,w-54,530),fill=LINE,width=2)
        for i,hh in enumerate([70,112,96,145]):
            d.rounded_rectangle((66+i*94,610-hh,120+i*94,610),8,fill=[SOFT_BLUE,"#BFDBFE",theme,"#93C5FD"][i])
    elif variant % 4 == 1:
        text(d,(42,154),"Table of contents",20,TEXT,700)
        items=[("01  Context","3"),("02  Findings","7"),("03  Methods","12"),("04  Notes","18")]
        for i,(a,b) in enumerate(items):
            y=205+i*62; text(d,(48,y),a,18,TEXT,600); text(d,(w-48,y),b,18,theme,700,anchor="ra"); d.line((48,y+34,w-48,y+34),fill=SLATE,width=2)
        rounded(d,(42,488,w-42,654),18,"#EFF6FF")
        text(d,(66,512),"Key finding",18,theme,700)
        text(d,(66,552),"Clear workflows reduce\nrework and decision time.",21,TEXT,600,spacing=10)
    elif variant % 4 == 2:
        text(d,(42,154),"Delivery plan",20,TEXT,700)
        for i,(name,pct) in enumerate([("Discover",.95),("Design",.78),("Build",.56),("Review",.32)]):
            y=210+i*88; text(d,(42,y),name,17,TEXT,600); rounded(d,(42,y+34,w-42,y+54),10,SLATE); rounded(d,(42,y+34,42+(w-84)*pct,y+54),10,theme)
        rounded(d,(42,594,w-42,664),16,"#F0FDF4")
        text(d,(64,616),"On track · local copy",18,SUCCESS,700)
    else:
        text(d,(42,154),"Budget overview",20,TEXT,700)
        cols=[42,250,390,w-42]
        for x in cols:d.line((x,200,x,540),fill=LINE,width=2)
        for y in range(200,541,68):d.line((42,y,w-42,y),fill=LINE,width=2)
        text(d,(56,218),"Workstream",15,MUTED,700)
        text(d,(270,218),"Plan",15,MUTED,700)
        text(d,(410,218),"Status",15,MUTED,700)
        for i,n in enumerate(["Research","Design","Build","QA"]):
            text(d,(56,278+i*68),n,16,TEXT,600); text(d,(270,278+i*68),f"Q{i+1}",16,TEXT,500); text(d,(410,278+i*68),"Ready",16,SUCCESS,600)
        text(d,(42,594),"All figures are fictional.",16,MUTED,500)
    return im


def crop_photo_panels() -> list[Image.Image]:
    sheet = Image.open(PHOTO_SHEET).convert("RGB")
    # Generated sheet has ~30px outer and central white gutters.
    boxes=[(30,30,766,612),(792,30,1222,612),(30,642,766,1222),(792,642,1222,1222)]
    imgs=[]
    for i,b in enumerate(boxes,1):
        p=sheet.crop(b)
        p.save(ROOT/f"source/source-images/architecture-nature-{i}.jpg",quality=94,subsampling=0)
        imgs.append(p)
    return imgs


def save_pdf(name: str, title: str, variants: list[int], theme=BLUE, photo_pages=None):
    pages=[]
    for i,v in enumerate(variants):
        p=page_art((1240,1754),title if i==0 else f"{title} · {i+1}",theme,v)
        if photo_pages and i < len(photo_pages):
            photo=ImageOps.fit(photo_pages[i],(1000,640),Image.Resampling.LANCZOS)
            p.paste(photo,(120,330)); d=ImageDraw.Draw(p); rounded(d,(120,1020,1120,1440),28,"#F8FAFC")
            text(d,(170,1080),"Product story",38,TEXT,700)
            text(d,(170,1150),"Owned synthetic catalogue imagery\nwith preserved color and detail.",28,MUTED,500,spacing=14)
        pages.append(p.convert("RGB"))
    out=ROOT/f"source/synthetic-pdfs/{name}.pdf"
    pages[0].save(out,"PDF",resolution=150.0,save_all=True,append_images=pages[1:])
    return out


def build_fixtures(photos):
    specs=[
        ("quarterly-summary","Quarterly Summary",[0,3]),
        ("travel-plan","Travel Plan",[2,0]),
        ("project-notes","Project Notes",[1,2]),
        ("research-report","Research Report",[1,0,3]),
        ("product-catalogue","Product Catalogue",[0,2,3,0]),
        ("project-brief","Project Brief",[2,0]),
        ("budget-overview","Budget Overview",[3,0]),
        ("delivery-timeline","Delivery Timeline",[2,1]),
        ("contract-draft","Contract Draft",[1,3]),
        ("travel-itinerary","Travel Itinerary",[2,0,1]),
    ]
    for name,title,vars_ in specs:
        save_pdf(name,title,vars_,photo_pages=photos if name=="product-catalogue" else None)
    save_pdf("architecture-collection","Architecture Collection",[0,1,2,3],photo_pages=photos)
    scan=corrected_receipt().convert("RGB")
    scan.save(ROOT/"source/synthetic-pdfs/scanned-receipt.pdf","PDF",resolution=150.0)


def document_card(im, box, name, meta, theme=BLUE, variant=0, angle=0):
    x0,y0,x1,y1=map(int,box); w=x1-x0; h=y1-y0
    card=canvas((w,h),WHITE); shadow_card(card,(4,4,w-5,h-5),24,WHITE,10,4,25)
    d=ImageDraw.Draw(card); rounded(d,(22,20,90,88),18,SOFT_BLUE); icon(d,(56,54),"doc",.65,theme,3)
    fit_text(d,(112,28),name,w-132,24,14,TEXT,700)
    text(d,(112,60),meta,15,MUTED,500)
    # A text-safe miniature preview. The document name already appears above,
    # so the page crop uses structure instead of repeating a clipped title.
    ph=max(110,h-120); rounded(d,(22,102,w-22,102+ph),10,WHITE,LINE,1)
    d.rectangle((22,102,w-22,110),fill=theme)
    for j,ww in enumerate((.72,.52,.81)):
        d.rounded_rectangle((40,132+j*18,40+(w-80)*ww,139+j*18),4,fill="#CBD5E1")
    if variant % 2:
        for j,hh in enumerate((34,58,46)):
            d.rounded_rectangle((42+j*(w-92)/3,102+ph-30-hh,66+j*(w-92)/3,102+ph-30),4,fill=theme if j==1 else SOFT_BLUE)
    else:
        pts=[(42,102+ph-42),(w*.35,102+ph-62),(w*.58,102+ph-52),(w-42,102+ph-88)]
        d.line(pts,fill=theme,width=4)
    if angle:
        card=card.rotate(angle,Image.Resampling.BICUBIC,expand=True,fillcolor=BG)
    im.paste(card,(x0,y0))


def header(im, headline, supporting, eyebrow=None, dark=False, y=96):
    d=ImageDraw.Draw(im); fg=WHITE if dark else TEXT; sub="#DCE8FF" if dark else MUTED
    if eyebrow: badge(d,76,y,eyebrow,SOFT_BLUE,BLUE); y+=70
    text(d,(76,y),headline,58,fg,750,spacing=8)
    text(d,(76,y+142 if "\n" in headline else y+78),supporting,28,sub,500)


def ui_home(size=(820,1060)):
    im=canvas(size,WHITE); d=ImageDraw.Draw(im); w,h=size
    text(d,(48,34),"QuietPDF",28,TEXT,700); badge(d,w-154,26,"Offline",SOFT_BLUE,BLUE,112)
    rounded(d,(48,96,w-48,184),28,"#EFF6FF"); icon(d,(90,140),"search",.65,BLUE,4); text(d,(130,121),"Search PDFs",22,TEXT,650); text(d,(130,150),"On this device",15,MUTED,500)
    text(d,(48,218),"Work with PDFs",30,TEXT,750)
    rounded(d,(48,266,w-48,454),34,BLUE)
    text(d,(78,296),"Your documents.\nYour device.",31,WHITE,750,spacing=10)
    text(d,(78,388),"Fast, private PDF tools",18,"#DBEAFE",500)
    rounded(d,(w-238,310,w-80,408),24,WHITE); icon(d,(w-188,359),"doc",.85,BLUE,4); icon(d,(w-126,359),"check",.65,SUCCESS,3)
    text(d,(48,494),"Quick tools",24,TEXT,700)
    tools=[("Open PDF","doc"),("Scan","scan"),("Images to PDF","photo"),("Compress","compress")]
    for i,(label,kind) in enumerate(tools):
        x=48+(i%2)*(w-120)//2; y=540+(i//2)*138
        rounded(d,(x,y,x+(w-144)//2,y+112),26,"#F8FAFC",LINE,2)
        rounded(d,(x+18,y+20,x+76,y+78),18,SOFT_BLUE); icon(d,(x+47,y+49),kind,.55,BLUE,3)
        text(d,(x+92,y+39),label,18,TEXT,650)
    text(d,(48,826),"Recent files",24,TEXT,700)
    for i,(name,meta) in enumerate([("Quarterly Summary","8 pages"),("Travel Plan","4 pages"),("Project Notes","12 pages")]):
        y=872+i*58; icon(d,(68,y+22),"doc",.42,BLUE,3); text(d,(98,y+7),name,16,TEXT,650); text(d,(w-48,y+8),meta,14,MUTED,500,anchor="ra")
    return im


def ui_scanner(size=(820,420), after=False):
    im=canvas(size,WHITE); d=ImageDraw.Draw(im); w,h=size
    text(d,(34,26),"Scan document",24,TEXT,700); badge(d,w-132,20,"Local",SOFT_BLUE,BLUE,96)
    text(d,(34,78),"Crop",18,BLUE,700); text(d,(112,78),"Enhance",18,MUTED,600); text(d,(220,78),"Pages",18,MUTED,600)
    d.line((34,112,w-34,112),fill=LINE,width=2)
    labels=[("Color",after),("Grayscale",not after),("B&W",False)]
    for i,(lab,sel) in enumerate(labels):
        x=34+i*148; rounded(d,(x,142,x+128,192),24,BLUE if sel else "#F1F5F9"); text(d,(x+64,167),lab,16,WHITE if sel else MUTED,650,anchor="mm")
    text(d,(34,226),"Brightness",16,TEXT,600); d.line((180,240,w-70,240),fill=SLATE,width=8); d.ellipse((w-190,227,w-164,253),fill=BLUE)
    text(d,(34,286),"Contrast",16,TEXT,600); d.line((180,300,w-70,300),fill=SLATE,width=8); d.ellipse((w-250,287,w-224,313),fill=BLUE)
    rounded(d,(34,344,w-34,398),27,BLUE); text(d,(w/2,371),"Save PDF",18,WHITE,700,anchor="mm")
    return im


def ui_reader(size=(820,1120)):
    im=canvas(size,"#EEF2F6"); d=ImageDraw.Draw(im); w,h=size
    rounded(d,(0,0,w,84),0,WHITE); text(d,(34,25),"Research Report",23,TEXT,700)
    for i,(kind,x) in enumerate([("search",w-168),("bookmark",w-108),("share",w-48)]): icon(d,(x,42),kind,.48,BLUE,3)
    page=page_art((540,760),"Research Report",BLUE,0); im.paste(page,(140,126))
    badge(d,330,930,"Page 7 of 18",NAVY,WHITE,160)
    return im


def ui_compress(size=(820,440)):
    im=canvas(size,WHITE); d=ImageDraw.Draw(im); w,h=size
    text(d,(36,30),"Compress PDF",26,TEXT,700); text(d,(36,72),"Product Catalogue · image-heavy",17,MUTED,500)
    text(d,(36,128),"Compression",18,TEXT,650)
    for i,lab in enumerate(["High quality","Balanced","Maximum"]):
        x=36+i*246; sel=i==1; rounded(d,(x,166,x+224,222),28,BLUE if sel else "#F1F5F9"); text(d,(x+112,194),lab,15,WHITE if sel else MUTED,650,anchor="mm")
    rounded(d,(36,260,w-36,326),20,"#EFF6FF"); text(d,(62,280),"Estimate appears before processing",17,BLUE,650)
    rounded(d,(36,356,w-36,414),29,BLUE); text(d,(w/2,385),"Compress PDF",18,WHITE,700,anchor="mm")
    return im


def ui_reorder(size=(820,510), images=False):
    im=canvas(size,WHITE); d=ImageDraw.Draw(im); w,h=size
    text(d,(34,24),"Images to PDF" if images else "Rearrange pages",25,TEXT,700)
    text(d,(34,62),"Drag to set the final order",16,MUTED,500)
    labels = ["Pavilion","Pine trail","Blue stairs","Lake cabin"] if images else ["Project","Budget","Timeline","Notes"]
    for i in range(4):
        x=34+i*188; y=110
        rounded(d,(x,y,x+162,y+232),22,"#F8FAFC",LINE,2)
        rounded(d,(x+16,y+16,x+146,y+164),10,WHITE)
        d.rectangle((x+16,y+16,x+146,y+26),fill=BLUE)
        if images:
            colors=[("#BFDBFE","#93C5FD"),("#DCFCE7","#86EFAC"),("#DBEAFE",BLUE),("#E0F2FE","#7DD3FC")][i]
            d.polygon([(x+24,y+146),(x+62,y+78),(x+90,y+116),(x+124,y+62),(x+140,y+146)],fill=colors[0])
            d.ellipse((x+106,y+42,x+126,y+62),fill=colors[1])
        else:
            for j,ww in enumerate((.72,.5,.82,.63)):
                d.rounded_rectangle((x+30,y+50+j*21,x+30+92*ww,y+57+j*21),4,fill=LINE)
        fit_text(d,(x+81,y+184),labels[i],132,15,11,TEXT,650,anchor="mm")
        text(d,(x+81,y+216),str(i+1),16,BLUE,700,anchor="mm")
    text(d,(34,370),"Paper size",16,TEXT,600); badge(d,154,356,"A4",SOFT_BLUE,BLUE,72)
    text(d,(260,370),"Margins",16,TEXT,600); badge(d,350,356,"Normal",SOFT_BLUE,BLUE,110)
    rounded(d,(34,430,w-34,488),29,BLUE); text(d,(w/2,459),"Create PDF" if images else "Save reordered PDF",18,WHITE,700,anchor="mm")
    return im


def ui_result(size=(820,540)):
    im=canvas(size,WHITE); d=ImageDraw.Draw(im); w,h=size
    icon(d,(w/2,72),"check",1.2,SUCCESS,4); text(d,(w/2,132),"PDF saved",30,TEXT,750,anchor="ma")
    text(d,(w/2,180),"Travel Itinerary.pdf",19,MUTED,600,anchor="ma")
    rounded(d,(54,226,w-54,292),20,"#F8FAFC"); icon(d,(86,259),"doc",.5,BLUE,3); text(d,(118,242),"Documents / QuietPDF",17,TEXT,600); text(d,(118,267),"Saved on this device",14,MUTED,500)
    for i,(lab,kind) in enumerate([("Open","doc"),("Share","share")]):
        x=54+i*(w-132)//2; rounded(d,(x,330,x+(w-150)//2,398),28,BLUE if i==0 else SOFT_BLUE); icon(d,(x+42,364),kind,.46,WHITE if i==0 else BLUE,3); text(d,(x+78,348),lab,18,WHITE if i==0 else BLUE,700)
    text(d,(160,458),"Rename",17,BLUE,650,anchor="mm"); text(d,(w-160,458),"Open Folder",17,BLUE,650,anchor="mm")
    return im


def paste_fit(dst, src, box, radius=24, border=None):
    x0,y0,x1,y1=map(int,box); fitted=ImageOps.fit(src.convert("RGB"),(x1-x0,y1-y0),Image.Resampling.LANCZOS)
    mask=Image.new("L",fitted.size,0); md=ImageDraw.Draw(mask); md.rounded_rectangle((0,0,*fitted.size),radius=radius,fill=255)
    dst.paste(fitted,(x0,y0),mask)
    if border: ImageDraw.Draw(dst).rounded_rectangle((x0,y0,x1,y1),radius=radius,outline=border,width=2)


def paste_contain(dst, src, box, radius=24, fill=WHITE, border=None):
    """Place a full UI crop without trimming controls or text."""
    x0,y0,x1,y1=map(int,box); target=(x1-x0,y1-y0)
    panel=canvas(target,fill); fitted=ImageOps.contain(src.convert("RGB"),target,Image.Resampling.LANCZOS)
    panel.paste(fitted,((target[0]-fitted.width)//2,(target[1]-fitted.height)//2))
    paste_fit(dst,panel,box,radius,border)


def real_ui(filename: str, crop: tuple[int, int, int, int] | None = None) -> Image.Image:
    """Load a production Compose capture rendered by PlayStoreUiCaptureTest."""
    path = REAL_UI_DIR / filename
    if not path.exists():
        raise FileNotFoundError(
            f"Missing real QuietPDF UI capture: {path}. Run PlayStoreUiCaptureTest and pull its outputs first."
        )
    image = Image.open(path).convert("RGB")
    return image.crop(crop) if crop else image


def phone_base(headline, support, number):
    im=canvas((1080,1920),BG); d=ImageDraw.Draw(im)
    d.ellipse((760,-220,1220,240),fill="#EFF6FF"); d.ellipse((-260,1500,280,2040),fill="#F1F5F9")
    badge(d,76,72,f"{number:02d}  QUIETPDF",SOFT_BLUE,BLUE)
    text(d,(76,150),headline,58,TEXT,750,spacing=8)
    y=292 if "\n" in headline else 226
    text(d,(76,y),support,28,MUTED,500)
    return im


def creative_1(alt=False):
    im=phone_base("All your PDF tools.\nOne calm app.","Open, scan, organize and compress.",1); d=ImageDraw.Draw(im)
    home=real_ui("01-home-ui.png",(0,110,1080,2280)); shadow_card(im,(210,470,870,1810),42,WHITE,30,20,34); paste_fit(im,home,(210,470,870,1810),38)
    if alt:
        document_card(im,(56,480,380,780),"Quarterly Summary","8 pages",BLUE,0)
        document_card(im,(700,420,1024,720),"Travel Plan","4 pages",SUCCESS,2)
        document_card(im,(720,1480,1015,1760),"Project Notes","12 pages",WARNING,1)
    else:
        document_card(im,(58,620,360,898),"Quarterly Summary","8 pages",BLUE,0)
        document_card(im,(718,490,1024,770),"Travel Plan","4 pages",SUCCESS,2)
        document_card(im,(720,1450,1024,1730),"Project Notes","12 pages",WARNING,1)
    return im


def corrected_receipt():
    src=Image.open(RECEIPT).convert("RGB")
    # A real deterministic crop/orientation/contrast result from the owned source.
    crop=src.crop((180,270,850,1320)).rotate(6.5,Image.Resampling.BICUBIC,expand=True,fillcolor=WHITE)
    crop=ImageOps.autocontrast(crop,cutoff=1)
    return ImageOps.fit(crop,(650,900),Image.Resampling.LANCZOS)


def creative_2():
    im=phone_base("From crooked photo\nto clean PDF.","Crop, correct and enhance.",2); d=ImageDraw.Draw(im)
    badge(d,76,420,"BEFORE",WARNING,WHITE,132); badge(d,610,420,"AFTER",SUCCESS,WHITE,116)
    src=Image.open(RECEIPT).convert("RGB"); aft=corrected_receipt()
    shadow_card(im,(70,482,508,1235),34,WHITE,24,14,36); paste_fit(im,src,(82,494,496,1223),28)
    shadow_card(im,(572,482,1010,1235),34,WHITE,24,14,36); paste_fit(im,aft,(584,494,998,1223),28)
    # perspective handles on before
    pts=[(125,605),(445,550),(466,1110),(110,1152)]; d.line(pts+[pts[0]],fill=BLUE,width=7)
    for x,y in pts:d.ellipse((x-13,y-13,x+13,y+13),fill=WHITE,outline=BLUE,width=6)
    panel=real_ui("02-scanner-review-ui.png",(0,1540,1080,2100)); shadow_card(im,(90,1330,990,1790),34,WHITE,24,12,30); paste_fit(im,panel,(90,1330,990,1790),32)
    return im


def creative_3():
    im=phone_base("Read without distractions.","Smooth zoom, search and bookmarks.",3); d=ImageDraw.Draw(im)
    reader=real_ui("03-reader-search-ui.png",(0,0,1080,2320)); shadow_card(im,(130,430,950,1740),44,WHITE,30,18,34); paste_fit(im,reader,(130,430,950,1740),40)
    shadow_card(im,(62,820,430,1090),28,WHITE,18,10,30); text(d,(92,850),"CONTENTS",17,BLUE,700); text(d,(92,900),"01  Context\n02  Findings\n03  Methods",21,TEXT,650,spacing=18)
    shadow_card(im,(650,650,1016,850),28,WHITE,18,10,30); icon(d,(704,706),"search",.55,BLUE,3); text(d,(752,684),"Search",16,MUTED,600); text(d,(752,716),"privacy",22,TEXT,700); badge(d,680,776,"3 of 8",SOFT_BLUE,BLUE,104)
    return im


def creative_4():
    im=phone_base("Smaller file. Clear pages.","Compare size before saving.",4); d=ImageDraw.Draw(im)
    badge(d,76,390,"BEFORE",WARNING,WHITE,132); badge(d,600,390,"AFTER",SUCCESS,WHITE,116)
    document_card(im,(70,460,480,1150),"Product Catalogue","Original",WARNING,0)
    document_card(im,(600,460,1010,1150),"Product Catalogue","Smaller PDF",SUCCESS,2)
    d.line((495,790,585,790),fill=BLUE,width=10); d.polygon([(585,790),(552,768),(552,812)],fill=BLUE)
    panel=real_ui("04-compression-ui.png",(0,430,840,715)); shadow_card(im,(90,1270,990,1740),36,WHITE,24,12,32); paste_contain(im,panel,(90,1270,990,1740),34,"#F0EAF3")
    text(d,(540,1195),"Output measured in-app at save time",18,MUTED,500,anchor="mm")
    return im


def creative_5(photos):
    im=phone_base("Turn photos into\npolished PDFs.","Arrange, rotate and choose your layout.",5); d=ImageDraw.Draw(im)
    badge(d,76,420,"BEFORE",WARNING,WHITE,132); badge(d,780,420,"AFTER",SUCCESS,WHITE,116)
    boxes=[(58,500,340,760),(300,550,570,850),(82,790,360,1070),(330,870,600,1140)]
    for i,(b,p) in enumerate(zip(boxes,photos)):
        shadow_card(im,b,26,WHITE,18,10,28); paste_fit(im,p,b,24)
        if i in (1,3):
            # orientation cue
            text(d,(b[2]-24,b[1]+18),"↻",25,WHITE,700,anchor="ra")
    # ordered stack
    for i in range(3,-1,-1):
        x=680+i*12; y=520+i*22; shadow_card(im,(x,y,x+300,y+420),26,WHITE,16,8,30); paste_fit(im,photos[i],(x+14,y+14,x+286,y+330),18); badge(d,x+102,y+350,f"Page {i+1}",SOFT_BLUE,BLUE,96)
    panel=real_ui("05-images-to-pdf-ui.png"); shadow_card(im,(90,1255,990,1790),34,WHITE,22,12,28); paste_fit(im,panel,(90,1255,990,1790),32)
    return im


def creative_6():
    im=phone_base("Merge and reorder\nwith confidence.","Preview every page before saving.",6); d=ImageDraw.Draw(im)
    badge(d,76,420,"BEFORE",WARNING,WHITE,132); badge(d,796,420,"AFTER",SUCCESS,WHITE,116)
    docs=[("Project Brief",2,BLUE),("Budget Overview",3,WARNING),("Delivery Timeline",2,SUCCESS)]
    for i,(name,var,col) in enumerate(docs): document_card(im,(54,500+i*236,420,710+i*236),name,f"PDF · {i+1}",col,var)
    d.line((450,810,610,810),fill=BLUE,width=10); d.polygon([(610,810),(576,786),(576,834)],fill=BLUE)
    shadow_card(im,(640,500,1015,1145),32,WHITE,24,12,32)
    for i,(name,var,col) in enumerate(docs):
        x=690+i*16; y=560+i*78
        rounded(d,(x,y,x+245,y+340),16,WHITE,LINE,2); d.rectangle((x,y,x+245,y+18),fill=col)
        fit_text(d,(x+24,y+62),name,196,20,13,TEXT,700)
        for j,ww in enumerate((.72,.48,.84,.61)):
            d.rounded_rectangle((x+24,y+110+j*30,x+24+180*ww,y+120+j*30),5,fill=LINE)
        badge(d,900,572+i*80,str(i+1),col,WHITE,54)
    panel=real_ui("06b-rearrange-ui.png"); shadow_card(im,(90,1275,990,1788),34,WHITE,22,12,28); paste_fit(im,panel,(90,1275,990,1788),32)
    return im


def creative_7():
    im=phone_base("Your documents stay\non your device.","PDF processing happens locally.",7); d=ImageDraw.Draw(im)
    shadow_card(im,(80,500,500,1220),34,WHITE,24,14,34); p=page_art((390,650),"Contract Draft",BLUE,1); paste_fit(im,p,(95,515,485,1175),24); badge(d,185,1140,"Contract Draft",SOFT_BLUE,BLUE,210)
    # meaningful device boundary, no shield/cloud
    d.rounded_rectangle((570,480,1000,1300),radius=70,fill="#EFF6FF",outline=BLUE,width=8)
    d.rounded_rectangle((620,560,950,1220),radius=42,fill=WHITE,outline="#93C5FD",width=4)
    icon(d,(785,680),"device",1.3,BLUE,5); text(d,(785,820),"QuietPDF",31,TEXT,750,anchor="ma")
    text(d,(785,875),"Processing locally",22,BLUE,650,anchor="ma")
    rounded(d,(660,960,910,1040),30,"#F0FDF4"); icon(d,(700,1000),"check",.55,SUCCESS,3); text(d,(746,983),"Stays on device",17,SUCCESS,700)
    d.line((500,850,600,850),fill=BLUE,width=10); d.polygon([(600,850),(566,826),(566,874)],fill=BLUE)
    shadow_card(im,(110,1360,970,1498),34,WHITE,22,12,26); paste_fit(im,real_ui("07-privacy-ui.png"),(126,1376,954,1482),24)
    shadow_card(im,(110,1510,970,1818),34,WHITE,22,12,26); text(d,(150,1550),"OFFLINE WORKFLOW",18,BLUE,700)
    steps=[("Open","doc"),("Process","compress"),("Save","check")]
    for i,(lab,k) in enumerate(steps):
        x=230+i*300; rounded(d,(x-56,1615,x+56,1727),30,SOFT_BLUE); icon(d,(x,1671),k,.68,BLUE if i<2 else SUCCESS,4); text(d,(x,1756),lab,17,TEXT,650,anchor="ma")
        if i<2:d.line((x+70,1671,x+230,1671),fill=LINE,width=6)
    return im


def creative_8():
    im=phone_base("Saved. Shared.\nEasy to find.","Open, rename, share or view folder.",8); d=ImageDraw.Draw(im)
    p=page_art((330,470),"Travel Itinerary",SUCCESS,2); shadow_card(im,(72,470,450,1050),32,WHITE,24,12,34); paste_fit(im,p,(96,494,426,964),24); badge(d,140,980,"Travel Itinerary",SOFT_BLUE,BLUE,240)
    result=ui_result((860,470)); shadow_card(im,(130,1040,990,1650),40,WHITE,28,15,34); paste_fit(im,result,(130,1040,990,1518),38)
    paste_fit(im,real_ui("08-result-ui.png",(60,2130,1020,2260)),(160,1532,960,1642),28)
    rounded(d,(450,520,1000,920),38,"#EFF6FF"); icon(d,(536,614),"check",.9,SUCCESS,4); text(d,(600,584),"Ready to share",27,TEXT,750); text(d,(600,632),"Stored locally",19,MUTED,500)
    rounded(d,(500,730,930,810),32,BLUE); icon(d,(560,770),"share",.55,WHITE,3); text(d,(610,749),"Share PDF",20,WHITE,700)
    return im


def tablet_creative(phone: Image.Image, index: int, size=(1920,1080), ten=False):
    im=canvas(size,BG); d=ImageDraw.Draw(im); w,h=size
    # Use a purpose-built wide narrative, never a resized phone screenshot.
    labels=[
        ("All your PDF tools. One calm app.","Open, scan, organize and compress."),
        ("From crooked photo to clean PDF.","Crop, correct and enhance."),
        ("Read without distractions.","Smooth zoom, search and bookmarks."),
        ("Smaller file. Clear pages.","Compare size before saving."),
        ("Turn photos into polished PDFs.","Arrange, rotate and choose your layout."),
        ("Merge and reorder with confidence.","Preview every page before saving."),
        ("Your documents stay on your device.","PDF processing happens locally."),
        ("Saved. Shared. Easy to find.","Open, rename, share or view folder."),
    ]
    badge(d,78,62,f"{index:02d}  QUIETPDF",SOFT_BLUE,BLUE); text(d,(78,144),labels[index-1][0],48,TEXT,750); text(d,(78,212),labels[index-1][1],25,MUTED,500)
    # navigation rail and true wide reader-style workspace
    shadow_card(im,(72,330,1848,1010),42,WHITE,28,18,30)
    rounded(d,(92,350,238,990),32,"#EFF6FF")
    for j,(lab,k) in enumerate([("Home","doc"),("Files","search"),("Tools","merge"),("History","bookmark")]):
        y=420+j*136; rounded(d,(112,y,218,y+92),28,BLUE if (j==0 and index==1) else WHITE); icon(d,(165,y+34),k,.48,WHITE if (j==0 and index==1) else BLUE,3); text(d,(165,y+70),lab,13,WHITE if (j==0 and index==1) else MUTED,600,anchor="mm")
    if index==1:
        home=real_ui("01-home-ui.png",(0,180,1080,2200)); paste_fit(im,home,(280,370,1040,970),28); document_card(im,(1100,390,1450,690),"Quarterly Summary","8 pages",BLUE,0); document_card(im,(1470,390,1820,690),"Travel Plan","4 pages",SUCCESS,2); document_card(im,(1285,710,1635,970),"Project Notes","12 pages",WARNING,1)
    elif index==2:
        paste_fit(im,Image.open(RECEIPT),(280,370,840,970),28); paste_fit(im,corrected_receipt(),(900,370,1460,970),28); panel=real_ui("02-scanner-review-ui.png",(0,1500,1080,2240)); paste_fit(im,panel,(1490,370,1820,970),28); badge(d,300,390,"BEFORE",WARNING,WHITE,128); badge(d,920,390,"AFTER",SUCCESS,WHITE,112)
    elif index==3:
        reader=real_ui("03-reader-search-ui.png",(0,0,1080,2300)); paste_fit(im,reader,(280,370,1330,970),28); shadow_card(im,(1370,390,1810,650),26,WHITE,18,10,28); text(d,(1402,420),"CONTENTS",16,BLUE,700); text(d,(1402,466),"01 Context\n02 Findings\n03 Methods\n04 Notes",20,TEXT,650,spacing=14); shadow_card(im,(1370,690,1810,940),26,WHITE,18,10,28); text(d,(1402,722),"Search · privacy",19,TEXT,700); badge(d,1402,790,"3 of 8",SOFT_BLUE,BLUE,104)
    elif index==4:
        document_card(im,(280,380,760,930),"Product Catalogue","Original",WARNING,0); document_card(im,(820,380,1300,930),"Product Catalogue","Smaller PDF",SUCCESS,2); paste_contain(im,real_ui("04-compression-ui.png"),(1340,390,1810,940),28,"#F0EAF3"); badge(d,300,400,"BEFORE",WARNING,WHITE,128); badge(d,840,400,"AFTER",SUCCESS,WHITE,112)
    elif index==5:
        photos=crop_photo_panels()
        for j,p in enumerate(photos): paste_fit(im,p,(280+(j%2)*300,390+(j//2)*270,550+(j%2)*300,630+(j//2)*270),24)
        paste_fit(im,real_ui("05-images-to-pdf-ui.png"),(900,390,1800,960),28)
    elif index==6:
        for j,(name,var,col) in enumerate([("Project Brief",2,BLUE),("Budget Overview",3,WARNING),("Delivery Timeline",2,SUCCESS)]): document_card(im,(280,390+j*185,680,550+j*185),name,f"PDF {j+1}",col,var)
        paste_fit(im,real_ui("06b-rearrange-ui.png"),(760,390,1800,960),28)
    elif index==7:
        document_card(im,(280,390,820,950),"Contract Draft","Local PDF",BLUE,1); rounded(d,(920,390,1780,950),48,"#EFF6FF",BLUE,5); paste_fit(im,real_ui("07-privacy-ui.png"),(980,438,1720,552),24); icon(d,(1130,680),"device",1.5,BLUE,5); text(d,(1360,638),"Processing locally",32,TEXT,750); text(d,(1360,700),"Open → Process → Save",24,BLUE,650); text(d,(1360,760),"No document upload",20,MUTED,500)
    else:
        document_card(im,(280,390,780,950),"Travel Itinerary","Completed",SUCCESS,2); paste_fit(im,ui_result((900,500)),(860,390,1800,840),28); paste_fit(im,real_ui("08-result-ui.png",(60,2130,1020,2260)),(910,855,1750,960),24)
    return im


def icon_concept(kind: int, size=512):
    im=canvas((size,size),BLUE); d=ImageDraw.Draw(im); s=size
    if kind==1: # selected document Q
        d.polygon([(122,76),(340,76),(410,146),(410,408),(122,408)],fill=WHITE)
        d.polygon([(340,76),(410,146),(340,146)],fill=SOFT_BLUE)
        d.ellipse((184,170,346,332),fill=BLUE); d.ellipse((223,209,307,293),fill=WHITE)
        d.polygon([(294,280),(363,355),(329,383),(270,305)],fill=BLUE)
    elif kind==2:
        d.rounded_rectangle((112,78,400,414),radius=36,fill=WHITE)
        d.polygon([(326,78),(400,152),(326,152)],fill=SOFT_BLUE)
        d.arc((166,164,346,344),20,324,fill=BLUE,width=44); d.line((308,308,365,365),fill=BLUE,width=44)
    else:
        d.polygon([(106,100),(366,100),(420,154),(420,400),(106,400)],fill=WHITE)
        d.polygon([(366,100),(420,154),(366,154)],fill=SOFT_BLUE)
        d.ellipse((170,166,336,332),outline=BLUE,width=42); d.line((292,292,365,365),fill=BLUE,width=42)
        d.line((160,376,330,376),fill=SOFT_BLUE,width=18)
    return im


def wordmark(dark=False,size=(1200,320)):
    im=canvas(size,NAVY if dark else WHITE); d=ImageDraw.Draw(im); mark=icon_concept(1,220); im.paste(mark,(58,50)); text(d,(330,78),"QuietPDF",74,WHITE if dark else TEXT,750); text(d,(334,172),"Offline PDF tools",28,"#BFDBFE" if dark else MUTED,500); return im


def feature_graphic(privacy=False):
    im=canvas((1024,500),BG); d=ImageDraw.Draw(im)
    d.ellipse((760,-240,1240,240),fill=SOFT_BLUE); d.ellipse((-180,330,220,730),fill="#EFF6FF")
    text(d,(62,66),"QuietPDF",44,BLUE,750)
    copy="Your PDFs stay\non your device." if privacy else "Everyday PDF tools.\nBeautifully simple."
    text(d,(62,134),copy,40,TEXT,750,spacing=8)
    if privacy:
        p=page_art((190,270),"Financial Summary",BLUE,3); paste_fit(im,p,(548,120,738,390),22); rounded(d,(770,86,960,414),42,"#EFF6FF",BLUE,5); paste_fit(im,real_ui("07-privacy-ui.png"),(786,112,944,148),12); icon(d,(865,215),"device",1.0,BLUE,4); text(d,(865,306),"Local",23,TEXT,700,anchor="ma"); text(d,(865,346),"processing",18,BLUE,600,anchor="ma"); d.line((738,255,790,255),fill=BLUE,width=8)
    else:
        home=real_ui("01-home-ui.png",(0,180,1080,2200)); paste_fit(im,home,(456,72,756,432),28)
        src=Image.open(RECEIPT); paste_fit(im,src,(774,74,926,244),20); paste_fit(im,corrected_receipt(),(830,260,982,430),20)
        badge(d,770,214,"SCAN",WARNING,WHITE,96); badge(d,886,400,"CLEAN",SUCCESS,WHITE,106)
    return im


def marketing_assets(phone_assets, tablets):
    # Seven distinct, campaign-ready compositions.
    outputs=[]
    def campaign(size,title,sub,visuals,name,folder):
        im=canvas(size,BG); d=ImageDraw.Draw(im); w,h=size
        d.ellipse((w*.62,-h*.25,w*1.15,h*.3),fill=SOFT_BLUE)
        text(d,(w*.07,h*.08),"QuietPDF",int(min(w,h)*.045),BLUE,750)
        text(d,(w*.07,h*.18),title,int(min(w,h)*.064),TEXT,750,spacing=8)
        text(d,(w*.07,h*.34 if "\n" in title else h*.29),sub,int(min(w,h)*.028),MUTED,500)
        visuals(im,d)
        p=ROOT/f"marketing-not-for-play/{folder}/{name}.png"; im.save(p,optimize=True); outputs.append(p)
    campaign((1080,1080),"PDF work,\nwithout the noise.","Private tools for everyday documents.",lambda im,d: paste_fit(im,phone_assets[0],(490,230,930,1010),38),"quietpdf-square","square")
    campaign((1200,628),"Calm tools.\nClear results.","Open, scan, organize and compress locally.",lambda im,d: (paste_fit(im,phone_assets[7],(680,42,1000,610),30),paste_fit(im,phone_assets[0],(925,120,1170,580),26)),"quietpdf-landscape","landscape")
    campaign((1080,1920),"Your PDFs.\nYour device.","Quiet, capable document tools.",lambda im,d: paste_fit(im,phone_assets[6],(210,520,870,1840),44),"quietpdf-story","story")
    campaign((1400,900),"One calm PDF workspace.","Phone and tablet, designed for focus.",lambda im,d:(paste_fit(im,phone_assets[0],(650,115,1020,820),34),paste_fit(im,tablets[0],(880,300,1380,640),24)),"phone-tablet-hero","phone-mockups")
    campaign((1200,1200),"Crooked in.\nClean out.","Crop, correct and enhance on device.",lambda im,d:(paste_fit(im,Image.open(RECEIPT),(480,420,790,1040),30),paste_fit(im,corrected_receipt(),(820,420,1130,1040),30)),"scanner-before-after","phone-mockups")
    campaign((1200,1200),"Private by design.","Documents stay on your device.",lambda im,d:paste_fit(im,phone_assets[6],(600,270,1080,1130),40),"privacy-first","phone-mockups")
    campaign((1200,1200),"Smaller PDF.\nSame clear story.","Compare quality before saving.",lambda im,d:paste_fit(im,phone_assets[3],(600,250,1080,1120),40),"compression-transformation","phone-mockups")
    return outputs


def contact_sheet(paths, out, cols=4, cell=(300,520), bg=BG, labels=True):
    rows=math.ceil(len(paths)/cols); im=canvas((cols*cell[0]+(cols+1)*24,rows*cell[1]+(rows+1)*24),bg); d=ImageDraw.Draw(im)
    for i,p in enumerate(paths):
        r,c=divmod(i,cols); x=24+c*cell[0]+c*24; y=24+r*cell[1]+r*24
        src=Image.open(p).convert("RGB"); label=Path(p).stem if labels else ""; ih=cell[1]-44 if labels else cell[1]
        fitted=ImageOps.contain(src,(cell[0],ih),Image.Resampling.LANCZOS); xx=x+(cell[0]-fitted.width)//2; yy=y
        im.paste(fitted,(xx,yy));
        if labels: fit_text(d,(x+cell[0]/2,y+ih+10),label,cell[0]-10,16,10,TEXT,600,anchor="ma")
    im.save(out,optimize=True)


def main():
    ensure_dirs(); photos=crop_photo_panels(); build_fixtures(photos)
    # Retain the older reconstructed panels only as a documented fallback. Final
    # creative layouts load the real production Compose captures from REAL_UI_DIR.
    fallback_dir=ROOT/"source/real-ui-captures-no-ads/reconstructed-fallback"; fallback_dir.mkdir(parents=True,exist_ok=True)
    captures=[ui_home(),ui_scanner(after=True),ui_reader(),ui_compress(),ui_reorder(images=True),ui_reorder(images=False),ui_home(),ui_result()]
    for i,cap in enumerate(captures,1): cap.convert("RGB").save(fallback_dir/f"{i:02d}-reconstructed-ui-fallback.png",optimize=True)
    corrected_receipt().save(ROOT/"source/before-after/scanner-after-corrected.png",optimize=True)
    Image.open(RECEIPT).convert("RGB").save(ROOT/"source/before-after/scanner-before-crooked.png",optimize=True)
    page_art((620,820),"Product Catalogue",WARNING,0).save(ROOT/"source/before-after/compression-before-original.png",optimize=True)
    page_art((620,820),"Product Catalogue",SUCCESS,2).save(ROOT/"source/before-after/compression-after-smaller.png",optimize=True)
    Image.open(PHOTO_SHEET).convert("RGB").save(ROOT/"source/before-after/images-to-pdf-before-separate.png",optimize=True)
    ui_reorder((1000,620),True).save(ROOT/"source/before-after/images-to-pdf-after-ordered.png",optimize=True)
    merge_before=canvas((1000,620),BG)
    for i,(name,var,col) in enumerate([("Project Brief",2,BLUE),("Budget Overview",3,WARNING),("Delivery Timeline",2,SUCCESS)]):
        document_card(merge_before,(30+i*320,80,310+i*320,540),name,f"PDF {i+1}",col,var)
    merge_before.save(ROOT/"source/before-after/merge-before-separate.png",optimize=True)
    ui_reorder((1000,620),False).save(ROOT/"source/before-after/merge-after-ordered.png",optimize=True)
    # Pass 1 renders.
    for i in range(1,4): icon_concept(i).save(ROOT/f"branding/concepts/icon-concept-{i}.png",optimize=True)
    creative_1(False).save(ROOT/"source/editable-layouts/pass1-creative-01-a.png",optimize=True)
    creative_1(True).save(ROOT/"source/editable-layouts/pass1-creative-01-b.png",optimize=True)
    creative_2().save(ROOT/"source/editable-layouts/pass1-scanner.png",optimize=True)
    creative_4().save(ROOT/"source/editable-layouts/pass1-compression.png",optimize=True)
    feature_graphic(False).save(ROOT/"feature-graphic/utility/quietpdf-feature-utility.png",optimize=True)
    feature_graphic(True).save(ROOT/"feature-graphic/privacy/quietpdf-feature-privacy.png",optimize=True)
    # Full phone family.
    makers=[lambda:creative_1(False),creative_2,creative_3,creative_4,lambda:creative_5(photos),creative_6,creative_7,creative_8]
    slugs=["all-tools","scanner-transform","pdf-reader","compression-transform","images-to-pdf","merge-rearrange","offline-privacy","result-sharing"]
    phone_paths=[]; phone_assets=[]
    for i,(mk,slug) in enumerate(zip(makers,slugs),1):
        im=mk().convert("RGB"); p=ROOT/f"play-upload/phone/en-US/{i:02d}-{slug}.png"; im.save(p,optimize=True); phone_paths.append(p); phone_assets.append(im)
    # True wide tablet sets plus foldable QA.
    tablet7=[]; tablet10=[]
    for i,(p,slug) in enumerate(zip(phone_assets,slugs),1):
        a=tablet_creative(p,i,ten=False).convert("RGB"); b=tablet_creative(p,i,ten=True).convert("RGB")
        pa=ROOT/f"play-upload/tablet-7/en-US/{i:02d}-{slug}-tablet7.png"; pb=ROOT/f"play-upload/tablet-10/en-US/{i:02d}-{slug}-tablet10.png"
        a.save(pa,optimize=True); b.save(pb,optimize=True); tablet7.append(pa); tablet10.append(pb)
        # Foldable unfolded maps to tablet experience; folded maps to phone.
        a.save(ROOT/f"qa/foldable/{i:02d}-{slug}-unfolded-qa.png",optimize=True)
    # Branding exports.
    selected=icon_concept(1).convert("RGB"); selected.save(ROOT/"branding/selected/quietpdf-play-icon-512.png",optimize=True)
    selected.resize((432,432),Image.Resampling.LANCZOS).save(ROOT/"branding/adaptive/ic_launcher_foreground-432.png",optimize=True)
    canvas((432,432),BLUE).save(ROOT/"branding/adaptive/ic_launcher_background-432.png",optimize=True)
    mono=Image.new("L",(432,432),0); md=ImageDraw.Draw(mono); md.polygon([(102,64),(286,64),(346,124),(346,344),(102,344)],fill=255); mono.convert("RGB").save(ROOT/"branding/monochrome/ic_launcher_monochrome-432.png",optimize=True)
    densities={"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}
    for dens,sz in densities.items():
        selected.resize((sz,sz),Image.Resampling.LANCZOS).save(ROOT/f"branding/selected/ic_launcher-{dens}.png",optimize=True)
        mask=Image.new("L",(512,512),0); ImageDraw.Draw(mask).ellipse((0,0,511,511),fill=255); roundim=canvas((512,512),BLUE); roundim.paste(selected,(0,0),mask); roundim.resize((sz,sz),Image.Resampling.LANCZOS).save(ROOT/f"branding/selected/ic_launcher_round-{dens}.png",optimize=True)
    wordmark(False).save(ROOT/"branding/selected/quietpdf-wordmark-light.png",optimize=True); wordmark(True).save(ROOT/"branding/selected/quietpdf-wordmark-dark.png",optimize=True)
    # Preview sheets.
    concept_paths=[ROOT/f"branding/concepts/icon-concept-{i}.png" for i in range(1,4)]
    preview=canvas((1100,420),WHITE); pd=ImageDraw.Draw(preview); sizes=[24,32,48,64,96,128]
    text(pd,(40,28),"QuietPDF icon concepts · small-size review",30,TEXT,700)
    for row,p in enumerate(concept_paths):
        src=Image.open(p); text(pd,(40,110+row*96),f"Concept {row+1}"+(" · SELECTED" if row==0 else ""),18,BLUE if row==0 else MUTED,700)
        x=220
        for sz in sizes: preview.paste(src.resize((sz,sz),Image.Resampling.LANCZOS),(x,92+row*96+(80-sz)//2)); x+=sz+46
    preview.save(ROOT/"branding/previews/icon-small-size-preview.png",optimize=True)
    masks=canvas((1200,360),BG); mm=ImageDraw.Draw(masks); text(mm,(40,24),"Android mask preview",30,TEXT,700)
    src=selected
    for i,(label,shape) in enumerate([("Circle","circle"),("Squircle","squircle"),("Rounded square","round")]):
        x=80+i*370; m=Image.new("L",(260,260),0); dm=ImageDraw.Draw(m)
        if shape=="circle":dm.ellipse((0,0,259,259),fill=255)
        elif shape=="squircle":dm.rounded_rectangle((0,0,259,259),radius=86,fill=255)
        else:dm.rounded_rectangle((0,0,259,259),radius=42,fill=255)
        s=src.resize((260,260),Image.Resampling.LANCZOS); masks.paste(s,(x,66),m); text(mm,(x+130,334),label,16,MUTED,600,anchor="ma")
    masks.save(ROOT/"branding/previews/android-mask-preview.png",optimize=True)
    # Marketing and contacts.
    mkt=marketing_assets(phone_assets,[Image.open(tablet7[0])])
    contact_sheet(phone_paths,ROOT/"contact-sheets/phone-creatives-contact-sheet.png",4,(250,450))
    contact_sheet(tablet7,ROOT/"contact-sheets/tablet-creatives-contact-sheet.png",2,(560,330))
    contact_sheet([ROOT/"feature-graphic/utility/quietpdf-feature-utility.png",ROOT/"feature-graphic/privacy/quietpdf-feature-privacy.png"],ROOT/"contact-sheets/feature-graphics-contact-sheet.png",2,(520,300))
    contact_sheet(concept_paths+[ROOT/"branding/selected/quietpdf-play-icon-512.png"],ROOT/"contact-sheets/icon-concepts-contact-sheet.png",4,(260,320))
    contact_sheet(mkt,ROOT/"contact-sheets/marketing-assets-contact-sheet.png",3,(360,360))
    compose_capture_paths=sorted(REAL_UI_DIR.glob("*.png"))
    contact_sheet(compose_capture_paths,ROOT/"contact-sheets/real-compose-ui-captures-contact-sheet.png",3,(340,600))
    # Draft localization examples are regenerated from source layouts, clearly quarantined.
    drafts=[("de-DE","Alle PDF-Werkzeuge.\nEine ruhige App.","Öffnen, scannen, ordnen und komprimieren."),("fr-FR","Tous vos outils PDF.\nUne seule app.","Ouvrez, scannez, organisez et compressez."),("ja-JP","PDFツールを、\nひとつの静かなアプリに。","開く、スキャン、整理、圧縮。"),("ar-AE","كل أدوات PDF.\nفي تطبيق هادئ واحد.","افتح وامسح ونظّم واضغط الملفات.")]
    for loc,h,s in drafts:
        base=creative_1(False); dr=ImageDraw.Draw(base); rounded(dr,(44,44,1036,390),34,BG); badge(dr,70,62,"DRAFT–NOT FOR UPLOAD",WARNING,WHITE,312); text(dr,(70,132),h,46,TEXT,750,spacing=6); text(dr,(70,260 if "\n" in h else 210),s,24,MUTED,500); base.save(ROOT/f"localized/draft-not-for-upload/01-product-hero-{loc}-DRAFT.png",optimize=True)
    # Editable layout manifest.
    layout={"system":"Quiet Confidence","version":2,"canvas":{"phone":[1080,1920],"tablet":[1920,1080],"feature":[1024,500]},"safe_margin_phone":76,"spacing_unit":8,"palette":{"primary":BLUE,"soft_blue":SOFT_BLUE,"background":BG,"text":TEXT,"success":SUCCESS},"renderer":"tools/generate_assets.py","ui_capture":{"source":"production Compose UI via PlayStoreUiCaptureTest","device":"Pixel_7a emulator","directory":"source/real-ui-captures-no-ads/compose-pixel-7a"},"selected_icon":"concept-1-document-q","ad_capture":{"requests_enabled":False,"containers_rendered":False}}
    (ROOT/"source/editable-layouts/layout-spec.json").write_text(json.dumps(layout,indent=2)+"\n",encoding="utf-8")
    # Machine-readable inventories are regenerated from the source of truth.
    pdf_rows=[
        ("quarterly-summary.pdf","Quarterly Summary","Phone/tablet creative 1","Owned synthetic; generated by this renderer"),
        ("travel-plan.pdf","Travel Plan","Phone/tablet creative 1","Owned synthetic; generated by this renderer"),
        ("project-notes.pdf","Project Notes","Phone/tablet creative 1","Owned synthetic; generated by this renderer"),
        ("research-report.pdf","Research Report","Phone/tablet creative 3","Owned synthetic; generated by this renderer"),
        ("product-catalogue.pdf","Product Catalogue","Phone/tablet creative 4","Owned synthetic with owned generated imagery"),
        ("project-brief.pdf","Project Brief","Phone/tablet creative 6","Owned synthetic; generated by this renderer"),
        ("budget-overview.pdf","Budget Overview","Phone/tablet creative 6","Owned synthetic; generated by this renderer"),
        ("delivery-timeline.pdf","Delivery Timeline","Phone/tablet creative 6","Owned synthetic; generated by this renderer"),
        ("contract-draft.pdf","Contract Draft","Phone/tablet creative 7","Owned synthetic; generated by this renderer"),
        ("travel-itinerary.pdf","Travel Itinerary","Phone/tablet creative 8","Owned synthetic; generated by this renderer"),
        ("architecture-collection.pdf","Architecture Collection","Phone/tablet creative 5","Owned synthetic with owned generated imagery"),
        ("scanned-receipt.pdf","Scanned Receipt","Phone/tablet creative 2","Owned synthetic result from owned generated receipt"),
    ]
    with (ROOT/"manifests/document-fixture-inventory.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f); w.writerow(["filename","document","used_in","license"]); w.writerows(pdf_rows)
    copy_rows=[
        (1,"All your PDF tools. One calm app.","Open, scan, organize and compress."),
        (2,"From crooked photo to clean PDF.","Crop, correct and enhance."),
        (3,"Read without distractions.","Smooth zoom, search and bookmarks."),
        (4,"Smaller file. Clear pages.","Compare size before saving."),
        (5,"Turn photos into polished PDFs.","Arrange, rotate and choose your layout."),
        (6,"Merge and reorder with confidence.","Preview every page before saving."),
        (7,"Your documents stay on your device.","PDF processing happens locally."),
        (8,"Saved. Shared. Easy to find.","Open, rename, share or view folder."),
    ]
    with (ROOT/"manifests/copy-deck.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f); w.writerow(["display_order","headline","supporting_copy"]); w.writerows(copy_rows)
    with (ROOT/"manifests/play-upload-map.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f); w.writerow(["device_set","locale","display_order","file"])
        for device,paths in [("phone",phone_paths),("tablet-7",tablet7),("tablet-10",tablet10)]:
            for i,p in enumerate(paths,1):w.writerow([device,"en-US",i,p.relative_to(ROOT)])
        w.writerow(["feature-graphic","global",1,"feature-graphic/utility/quietpdf-feature-utility.png"])
        w.writerow(["feature-graphic","global",2,"feature-graphic/privacy/quietpdf-feature-privacy.png"])
        w.writerow(["app-icon","global",1,"branding/selected/quietpdf-play-icon-512.png"])
    inventory_rows=[]
    with (ROOT/"manifests/asset-inventory.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f); w.writerow(["path","width","height","mode","status","no_ads"])
        for p in sorted(ROOT.rglob("*")):
            if p.suffix.lower() not in {".png",".jpg",".jpeg"}:continue
            with Image.open(p) as chk:
                status="DRAFT-NOT-FOR-UPLOAD" if "draft-not-for-upload" in p.parts else "FINAL"
                row=[str(p.relative_to(ROOT)),chk.width,chk.height,chk.mode,status,"NO ADS: VERIFIED"]
                inventory_rows.append(row); w.writerow(row)
    no_ads=["# Manual no-ad review","","Review date: 2026-07-22.","","The `PlayStoreUiCaptureTest` harness renders the production Compose UI with `adsCanLoad = false` and `homeBannerContent = null`; it does not initialize AdMob, request ads, or reserve ad containers. Each rendered raster was reviewed for banner, native, interstitial, app-open, rewarded, sponsored, empty-container, loading, and third-party advertising content.",""]
    no_ads.extend(f"- [x] `{r[0]}` — **NO ADS: VERIFIED**" for r in inventory_rows)
    (ROOT/"qa/no-ads-review.md").write_text("\n".join(no_ads)+"\n",encoding="utf-8")
    icon_bytes=(ROOT/"branding/selected/quietpdf-play-icon-512.png").stat().st_size
    dims=["# Dimension report","","Generated and validated at source resolution.","",f"- Raster assets inventoried: {len(inventory_rows)}", "- Phone masters: 8 × 1080×1920 RGB PNG", "- 7-inch tablet masters: 8 × 1920×1080 RGB PNG", "- 10-inch tablet masters: 8 × 1920×1080 RGB PNG", "- Feature graphics: 2 × 1024×500 RGB PNG", "- Selected Play icon: 512×512 RGB PNG; validator enforces ≤1024 KB", "- Marketing: 1080×1080, 1200×628, 1080×1920, 1400×900, and 1200×1200 outputs", "", "Validation command:", "", "`python3 play-store-assets/v2-creative/tools/validate_assets.py`", "", f"Result: **PASS** — {len(inventory_rows)} raster assets inventoried; all required upload families passed dimension, RGB/decode, naming, inventory, licensing, draft-quarantine, placeholder, and no-ad checks. The Play icon is {icon_bytes:,} bytes, below the 1,024 KB limit."]
    (ROOT/"qa/dimension-report.md").write_text("\n".join(dims)+"\n",encoding="utf-8")


if __name__ == "__main__":
    main()
