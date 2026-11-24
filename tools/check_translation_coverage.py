#!/usr/bin/env python3
"""Fail CI when the Russian dictionaries or the published pack drift apart."""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "assets/localization/ui-catalog.json"
ANDROID_CATALOG = ROOT / "android/app/src/main/assets/localization/ui-catalog.json"
RUST_DICTIONARY = ROOT / "desktop/src/translation.rs"
KOTLIN_DICTIONARY = ROOT / "android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt"
PACK = ROOT / "examples/ru-community.procraft-lang"
PREVIEW = ROOT / "examples/ru-community-preview.json"

RUST_PAIR = re.compile(r'^\s*("(?:\\.|[^"\\])*")\s*=>\s*("(?:\\.|[^"\\])*"),\s*$', re.MULTILINE)
KOTLIN_PAIR = re.compile(r'^\s*("(?:\\.|[^"\\])*")\s+to\s+("(?:\\.|[^"\\])*"),?\s*$', re.MULTILINE)
PLACEHOLDER = re.compile(r"\{[A-Za-z][A-Za-z0-9_]*\}")
DESKTOP_COMPACT_OVERRIDES = {
    "Disc": "Диск",
    "Tap + to start a new canvas": "Нажмите «+», чтобы создать холст",
    "Rectangle": "Прямоуг.",
    "Invert": "Инверт.",
    "Feather": "Растуш.",
    "Save & Load": "Сохр.",
    "Color Fill": "Залив.",
    "Flip Horizontal": "Гориз.",
    "Flip Vertical": "Вертик.",
    "Fill Layer": "Заливочный слой",
    "Alpha Lock": "Альфа-блокировка",
}
EXPECTED_TERMS = {
    "Licenses": "Лицензии",
    "Language packs": "Языковые пакеты",
    "Maximum Speed Distance": "Порог скорости кисти",
    "Cancel": "Отмена",
    "Import": "Импортировать",
    "Basics": "Основные",
    "Ok": "ОК",
    "Combine down": "Объединить с нижним слоем",
    "Combine Down": "Объединить с нижним слоем",
    "Merge down": "Объединить с нижним слоем",
    "Merge Down": "Объединить с нижним слоем",
    "Clipping mask": "Обтравочная маска",
    "Clipping Mask": "Обтравочная маска",
    "Gamma": "Гамма",
    "Red": "Красный",
    "Green": "Зелёный",
    "Blue": "Синий",
    "Cyan": "Голубой",
    "Magenta": "Пурпурный",
    "Yellow": "Жёлтый",
    "Shadows": "Тени",
    "Midtones": "Средние тона",
    "Highlights": "Света",
}


def fail(message: str) -> None:
    print(f"translation audit: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_pairs(pattern: re.Pattern[str], block: str, label: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for key_literal, value_literal in pattern.findall(block):
        try:
            key = json.loads(key_literal)
            value = json.loads(value_literal)
        except json.JSONDecodeError as error:
            fail(f"{label} contains a string literal the audit cannot decode: {error}")
        if key in result:
            fail(f"{label} contains duplicate key {key!r}")
        result[key] = value
    if not result:
        fail(f"could not parse any entries from {label}")
    return result


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {path.relative_to(ROOT)}: {error}")


catalog = read_json(CATALOG)
android_catalog = read_json(ANDROID_CATALOG)
if catalog != android_catalog:
    fail("Android and shared source catalogs are not identical")
if not isinstance(catalog, list) or not all(isinstance(item, str) for item in catalog):
    fail("source catalog must be a JSON array of strings")
if len(catalog) != len(set(catalog)):
    fail("source catalog contains duplicate entries")

rust_text = RUST_DICTIONARY.read_text(encoding="utf-8")
rust_start = rust_text.find("let translated = match source {")
rust_end = rust_text.find("\n    };", rust_start)
if rust_start < 0 or rust_end < 0:
    fail("could not isolate the Desktop translation match block")
desktop = load_pairs(RUST_PAIR, rust_text[rust_start:rust_end], "Desktop dictionary")

kotlin_text = KOTLIN_DICTIONARY.read_text(encoding="utf-8")
kotlin_start = kotlin_text.find("private val russianUi = mapOf(")
kotlin_end = kotlin_text.find("\n)\n\ninternal fun languageCatalog()", kotlin_start)
if kotlin_start < 0 or kotlin_end < 0:
    fail("could not isolate the Android russianUi map")
android = load_pairs(KOTLIN_PAIR, kotlin_text[kotlin_start:kotlin_end], "Android dictionary")

for label, dictionary in (("Desktop", desktop), ("Android", android)):
    missing = [key for key in catalog if key not in dictionary]
    empty = [key for key in catalog if not dictionary.get(key, "").strip()]
    if missing:
        fail(f"{label} dictionary is missing {len(missing)} catalog entries; first: {missing[:8]!r}")
    if empty:
        fail(f"{label} dictionary has {len(empty)} empty translations; first: {empty[:8]!r}")

different = {
    key: (desktop[key], android[key])
    for key in catalog
    if desktop[key] != android[key]
}
unexpected = {
    key: values
    for key, values in different.items()
    if key not in DESKTOP_COMPACT_OVERRIDES or values[0] != DESKTOP_COMPACT_OVERRIDES[key]
}
missing_overrides = set(DESKTOP_COMPACT_OVERRIDES) - set(different)
if unexpected or missing_overrides:
    fail(
        "Desktop-specific compact labels changed unexpectedly; "
        f"unexpected={list(unexpected.items())[:8]!r}, "
        f"no-longer-needed={sorted(missing_overrides)!r}"
    )

for key, expected in EXPECTED_TERMS.items():
    if desktop.get(key) != expected or android.get(key) != expected:
        fail(f"requested UI label {key!r} must be translated as {expected!r}")

pack = read_json(PACK)
preview = read_json(PREVIEW)
if pack != preview:
    fail("the importable .procraft-lang and readable JSON preview are not identical")
if pack.get("format_version") != 1 or not isinstance(pack.get("strings"), dict):
    fail("sample pack does not use the expected v1 strings format")
pack_strings = pack["strings"]
missing_pack = [key for key in android if key not in pack_strings]
if missing_pack:
    fail(f"sample pack is missing {len(missing_pack)} built-in entries; first: {missing_pack[:8]!r}")

for key, expected_value in android.items():
    value = pack_strings.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f"sample pack has an empty or non-string value for {key!r}")
    if value != expected_value:
        fail(f"sample pack does not match the Android built-in dictionary for {key!r}")

for key, value in pack_strings.items():
    if Counter(PLACEHOLDER.findall(key)) != Counter(PLACEHOLDER.findall(value)):
        fail(f"sample pack changes placeholders in {key!r}")
    if len(key) > 512 or len(value) > 4000:
        fail(f"sample pack exceeds the v1 field limits for {key!r}")

print(
    "translation audit: OK — "
    f"{len(catalog)} catalog entries, "
    f"{len(desktop)} Desktop keys, "
    f"{len(android)} Android keys, "
    f"{len(pack_strings)} sample-pack entries, "
    f"{len(DESKTOP_COMPACT_OVERRIDES)} intentional compact Desktop labels; "
    "catalogs, translations and preview are synchronized."
)
