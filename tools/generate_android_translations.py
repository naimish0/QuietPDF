#!/usr/bin/env python3
"""Generate complete QuietPDF Android string resources for supported locales.

The script batches non-sensitive application copy through Google Translate's public web endpoint,
while masking Android format arguments, escaped newlines, product names, and technical terms. It is
intended for deterministic resource scaffolding; committed XML files are the runtime source of truth.
"""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/values/strings.xml"
LOCALES = {
    "ru-RU": ("ru", "values-ru"),
    "es-ES": ("es", "values-es"),
    "pt-PT": ("pt-PT", "values-pt-rPT"),
    "pt-BR": ("pt", "values-pt-rBR"),
    "it-IT": ("it", "values-it"),
    "id-ID": ("id", "values-in"),
    "ar": ("ar", "values-ar"),
    "ko-KR": ("ko", "values-ko"),
    "ur-PK": ("ur", "values-ur-rPK"),
}
MASK_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]|\{n\}|\\n|QuietPDF|AES-256|AdMob|Android|PDF|\bIP\b")
MARKER_RE = re.compile(r"<<<QPDF_(\d{4})>>>")


def mask(value: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def replace(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"ZXQPDFTOKEN{len(tokens)-1:02d}QXZ"

    return MASK_RE.sub(replace, value.replace("\\'", "'")), tokens


def unmask(value: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        value = value.replace(f"ZXQPDFTOKEN{index:02d}QXZ", token)
        value = value.replace(f"ZXQ PDF TOKEN {index:02d} QXZ", token)
    return value.strip().replace("'", "\\'")


def request_translation(value: str, target: str) -> str:
    query = urllib.parse.urlencode({
        "client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": value,
    })
    request = urllib.request.Request(
        f"https://translate.googleapis.com/translate_a/single?{query}",
        headers={"User-Agent": "QuietPDF localization renderer"},
    )
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.loads(response.read())
            return "".join(part[0] for part in payload[0] if part[0])
        except Exception as error:  # network retries are intentionally bounded
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Translation request failed for {target}") from last_error


def translate_entries(values: list[str], target: str) -> list[str]:
    masked: list[str] = []
    token_sets: list[list[str]] = []
    for value in values:
        protected, tokens = mask(value)
        masked.append(protected)
        token_sets.append(tokens)

    translated = [""] * len(values)
    start = 0
    while start < len(masked):
        end = start
        size = 0
        while end < len(masked):
            addition = len(masked[end]) + 24
            if end > start and size + addition > 3200:
                break
            size += addition
            end += 1
        payload = "\n".join(f"<<<QPDF_{i:04d}>>>\n{masked[i]}" for i in range(start, end))
        result = request_translation(payload, target)
        parts = MARKER_RE.split(result)
        found: dict[int, str] = {}
        for index in range(1, len(parts), 2):
            found[int(parts[index])] = parts[index + 1].strip()
        missing = [i for i in range(start, end) if i not in found]
        if missing:
            raise RuntimeError(f"Translation markers missing for {target}: {missing}")
        for i in range(start, end):
            translated[i] = unmask(found[i], token_sets[i])
        start = end
        time.sleep(0.08)
    return translated


PLURAL_OVERRIDES = {
    "ru-RU": {
        "history_entry_count": {"one":"%1$d выполненная операция","few":"%1$d выполненные операции","many":"%1$d выполненных операций","other":"%1$d выполненной операции"},
        "smart_home_recent_count": {"one":"%1$d недавний PDF","few":"%1$d недавних PDF","many":"%1$d недавних PDF","other":"%1$d недавнего PDF"},
        "recent_file_pages": {"one":"%d страница","few":"%d страницы","many":"%d страниц","other":"%d страницы"},
    },
    "ar": {
        "history_entry_count": {"zero":"لا عمليات مكتملة","one":"عملية مكتملة واحدة","two":"عمليتان مكتملتان","few":"%1$d عمليات مكتملة","many":"%1$d عملية مكتملة","other":"%1$d عملية مكتملة"},
        "smart_home_recent_count": {"zero":"لا ملفات PDF حديثة","one":"ملف PDF حديث واحد","two":"ملفا PDF حديثان","few":"%1$d ملفات PDF حديثة","many":"%1$d ملف PDF حديث","other":"%1$d ملف PDF حديث"},
        "recent_file_pages": {"zero":"لا صفحات","one":"صفحة واحدة","two":"صفحتان","few":"%d صفحات","many":"%d صفحة","other":"%d صفحة"},
    },
}


def write_locale(locale: str, target: str, folder: str) -> None:
    source_root = ET.parse(SOURCE).getroot()
    entries: list[tuple[ET.Element, str]] = []
    values: list[str] = []
    for node in source_root:
        if node.tag == "string":
            entries.append((node, "string"))
            values.append(node.text or "")
        elif node.tag == "plurals":
            for item in node.findall("item"):
                entries.append((item, node.get("name", "")))
                values.append(item.text or "")
    translated = translate_entries(values, target)
    translated_by_node = {id(node): text for (node, _), text in zip(entries, translated)}

    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for node in source_root:
        if node.tag == "string":
            name = node.get("name")
            value = escape(translated_by_node[id(node)], {'"': '&quot;'})
            lines.append(f'    <string name="{name}">{value}</string>')
        elif node.tag == "plurals":
            name = node.get("name", "")
            override = PLURAL_OVERRIDES.get(locale, {}).get(name)
            lines.append(f'    <plurals name="{name}">')
            if override:
                for quantity, text in override.items():
                    lines.append(f'        <item quantity="{quantity}">{escape(text)}</item>')
            else:
                for item in node.findall("item"):
                    text = escape(translated_by_node[id(item)])
                    lines.append(f'        <item quantity="{item.get("quantity")}">{text}</item>')
            lines.append('    </plurals>')
    lines.append('</resources>')
    output = ROOT / "app/src/main/res" / folder / "strings.xml"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{locale}: {output.relative_to(ROOT)} ({len(values)} translated entries)")


def main() -> None:
    for locale, (target, folder) in LOCALES.items():
        write_locale(locale, target, folder)


if __name__ == "__main__":
    main()
