#!/usr/bin/env python3
"""Generate complete QuietPDF Android string resources for supported locales.

The source of truth remains app/src/main/res/values/strings.xml. Android format
arguments are masked before translation and verified after restoration.
"""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/values/strings.xml"
CACHE = ROOT / "build/quietpdf-translation-cache.json"
LOCALES = {"de": "German", "fr": "French", "ja": "Japanese", "hi": "Hindi"}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]|%%")


def mask(value: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def replace(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"⟦QPDF{len(tokens) - 1}⟧"

    return PLACEHOLDER.sub(replace, value), tokens


def restore(value: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        marker = f"⟦QPDF{index}⟧"
        if marker not in value:
            raise ValueError(f"Translation dropped Android placeholder {marker}: {value}")
        value = value.replace(marker, token)
    if "⟦QPDF" in value:
        raise ValueError(f"Translation retained an unknown placeholder: {value}")
    return value


def request_translation(value: str, locale: str) -> str:
    if not value or value == "QuietPDF":
        return value
    protected, tokens = mask(value)
    query = urllib.parse.urlencode(
        {"client": "gtx", "sl": "en", "tl": locale, "dt": "t", "q": protected}
    )
    url = f"https://translate.googleapis.com/translate_a/single?{query}"
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                payload = json.loads(response.read())
            translated = "".join(segment[0] for segment in payload[0] if segment[0])
            return restore(translated, tokens)
        except Exception as error:  # Network retries are intentionally bounded.
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Could not translate {value!r} to {locale}") from last_error


def android_escape(value: str) -> str:
    # AAPT treats straight apostrophes and quotes specially even inside XML text.
    value = value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    return escape(value)


def main() -> None:
    root = ET.parse(SOURCE).getroot()
    entries: list[tuple[str, str, str]] = []
    for element in root:
        if element.tag == "string":
            entries.append((element.attrib["name"], "string", element.text or ""))
        elif element.tag == "plurals":
            for item in element:
                entries.append(
                    (f"{element.attrib['name']}::{item.attrib['quantity']}", "plural", item.text or "")
                )

    CACHE.parent.mkdir(parents=True, exist_ok=True)
    cache = json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else {}
    jobs: dict[tuple[str, str], tuple[str, str]] = {}
    for locale in LOCALES:
        for key, kind, value in entries:
            cache_key = f"{locale}:{key}:{value}"
            if cache_key not in cache:
                jobs[(locale, cache_key)] = (kind, value)

    with ThreadPoolExecutor(max_workers=12) as executor:
        futures = {
            executor.submit(request_translation, value, locale): (locale, cache_key)
            for (locale, cache_key), (_, value) in jobs.items()
        }
        for count, future in enumerate(as_completed(futures), 1):
            locale, cache_key = futures[future]
            cache[cache_key] = future.result()
            if count % 100 == 0:
                CACHE.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
                print(f"Translated {count}/{len(futures)} missing values")
    CACHE.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")

    for locale, language in LOCALES.items():
        output = ROOT / f"app/src/main/res/values-{locale}/strings.xml"
        output.parent.mkdir(parents=True, exist_ok=True)
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            f"<!-- Complete {language} localization generated from values/strings.xml. -->",
            "<resources>",
        ]
        for element in root:
            name = element.attrib["name"]
            if element.tag == "string":
                source_value = element.text or ""
                value = cache[f"{locale}:{name}:{source_value}"]
                lines.append(f'    <string name="{name}">{android_escape(value)}</string>')
            elif element.tag == "plurals":
                lines.append(f'    <plurals name="{name}">')
                for item in element:
                    source_value = item.text or ""
                    key = f"{name}::{item.attrib['quantity']}"
                    value = cache[f"{locale}:{key}:{source_value}"]
                    lines.append(
                        f'        <item quantity="{item.attrib["quantity"]}">{android_escape(value)}</item>'
                    )
                lines.append("    </plurals>")
        lines.append("</resources>")
        output.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"Wrote {output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
