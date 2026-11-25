#!/usr/bin/env python3
"""Validate the curated Community Translation Hub index and every published pack.

This checker deliberately uses only the Python standard library so it can run in CI
without installing additional packages.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import Counter
from datetime import date
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "community-translations/index.json"
SOURCE_CATALOG = ROOT / "assets/localization/ui-catalog.json"
MAX_INDEX_BYTES = 512 * 1024
MAX_PACK_BYTES = 5 * 1024 * 1024
MAX_PACKS = 100
MAX_STRINGS = 20_000
MAX_TEMPLATES = 128
PACK_ID = re.compile(r"[a-z0-9][a-z0-9._-]{2,95}\Z")
LOCALE = re.compile(r"[A-Za-z]{2,8}(?:[_-][A-Za-z0-9]{2,8})*\Z")
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+\Z")
DATE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}\Z")
PLACEHOLDER = re.compile(r"\{[^{}]+\}")
INDEX_URL_PREFIX = (
    "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/"
    "community-translations/"
)
PACK_FIELDS = {
    "format_version", "id", "locale", "name", "author", "app_version",
    "catalog_version", "base_locale", "strings",
}
INDEX_FIELDS = {"schema_version", "catalog_version", "updated_at", "packs"}
ENTRY_FIELDS = {
    "id", "locale", "name", "author", "description", "version",
    "catalog_version", "strings_count", "updated_at", "download_url", "sha256",
}


def fail(message: str) -> None:
    print(f"community Hub audit: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def read_json(path: Path, max_bytes: int) -> object:
    try:
        raw = path.read_bytes()
    except OSError as error:
        fail(f"cannot read {path.relative_to(ROOT)}: {error}")
    if len(raw) > max_bytes:
        fail(f"{path.relative_to(ROOT)} exceeds {max_bytes} bytes")
    try:
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=reject_duplicate_keys,
        )
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")


def valid_date(value: object, label: str) -> None:
    if not isinstance(value, str) or not DATE.fullmatch(value):
        fail(f"{label} must use YYYY-MM-DD")
    try:
        date.fromisoformat(value)
    except ValueError:
        fail(f"{label} is not a real calendar date: {value!r}")


def current_catalog_revision() -> str:
    android = ROOT / (
        "android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt"
    )
    desktop = ROOT / "desktop/src/language_packs.rs"
    try:
        android_text = android.read_text(encoding="utf-8")
        desktop_text = desktop.read_text(encoding="utf-8")
    except OSError as error:
        fail(f"cannot read platform catalogue revision: {error}")
    android_match = re.search(
        r'private const val PACK_CATALOG_VERSION = "([^"]+)"',
        android_text,
    )
    desktop_match = re.search(
        r'fn catalog_version\(\)\s*->\s*String\s*\{\s*"([^"]+)"',
        desktop_text,
    )
    if not android_match or not desktop_match:
        fail("could not locate Android/Desktop catalogue revision constants")
    if android_match.group(1) != desktop_match.group(1):
        fail("Android and Desktop catalogue revision constants differ")
    return android_match.group(1)


def validate_pack_file(entry: dict[str, object], source_catalog: set[str]) -> None:
    pack_id = entry["id"]
    url = entry["download_url"]
    parsed = urlsplit(url)

    if (
        parsed.scheme != "https"
        or parsed.hostname != "raw.githubusercontent.com"
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port is not None
        or parsed.query
        or parsed.fragment
        or not url.startswith(INDEX_URL_PREFIX)
        or not parsed.path.endswith(".procraft-lang")
    ):
        fail(f"{pack_id}: download URL is outside the official raw GitHub catalogue path")

    relative = parsed.path.removeprefix(
        "/M1ST0R/procraft_localization/main/"
    )
    if (
        not relative.startswith("community-translations/")
        or relative.count("/") != 1
        or relative.rsplit("/", 1)[-1] in {"", ".", ".."}
    ):
        fail(f"{pack_id}: download URL must point to a pack directly inside community-translations/")

    pack_path = ROOT / relative
    try:
        pack_path.resolve().relative_to((ROOT / "community-translations").resolve())
    except ValueError:
        fail(f"{pack_id}: package path escapes the community-translations directory")
    if not pack_path.is_file():
        fail(f"{pack_id}: package file does not exist: {relative}")

    try:
        raw = pack_path.read_bytes()
    except OSError as error:
        fail(f"{pack_id}: cannot read package: {error}")
    if len(raw) > MAX_PACK_BYTES:
        fail(f"{pack_id}: package exceeds 5 MiB")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != entry["sha256"]:
        fail(f"{pack_id}: SHA-256 mismatch (index={entry['sha256']}, file={digest})")

    try:
        pack = json.loads(raw.decode("utf-8"), object_pairs_hook=reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        fail(f"{pack_id}: package is not valid UTF-8 JSON: {error}")
    if not isinstance(pack, dict):
        fail(f"{pack_id}: package root must be a JSON object")
    if set(pack) - PACK_FIELDS:
        fail(f"{pack_id}: package has unsupported fields: {sorted(set(pack) - PACK_FIELDS)}")
    if pack.get("format_version") != 1 or type(pack.get("format_version")) is not int:
        fail(f"{pack_id}: unsupported language-pack format version")
    strings = pack.get("strings")
    if not isinstance(strings, dict) or not 1 <= len(strings) <= MAX_STRINGS:
        fail(f"{pack_id}: strings must be an object with 1..{MAX_STRINGS} entries")

    if pack.get("id") != entry["id"]:
        fail(f"{pack_id}: package ID does not match the index")
    if pack.get("locale") != entry["locale"]:
        fail(f"{pack_id}: package locale does not match the index")
    if pack.get("name") != entry["name"]:
        fail(f"{pack_id}: package name does not match the index")
    if pack.get("author", "") != entry["author"]:
        fail(f"{pack_id}: package author does not match the index")
    if pack.get("catalog_version") != entry["catalog_version"]:
        fail(f"{pack_id}: package catalogue revision does not match the index")
    if len(strings) != entry["strings_count"]:
        fail(f"{pack_id}: string count does not match the index")

    for field, limit in (("app_version", 40), ("catalog_version", 80), ("base_locale", 35)):
        value = pack.get(field, "unknown" if field == "app_version" else "en")
        if not isinstance(value, str) or len(value) > limit:
            fail(f"{pack_id}: invalid {field} metadata")
    if not isinstance(pack.get("name"), str) or not 1 <= len(pack["name"]) <= 80:
        fail(f"{pack_id}: invalid package name")
    if not isinstance(pack.get("author", ""), str) or len(pack.get("author", "")) > 120:
        fail(f"{pack_id}: invalid package author")
    if not isinstance(pack.get("locale"), str) or not LOCALE.fullmatch(pack["locale"]):
        fail(f"{pack_id}: invalid package locale")

    template_count = 0
    for source, translation in strings.items():
        if (
            not isinstance(source, str)
            or not source.strip()
            or len(source) > 512
            or not isinstance(translation, str)
            or len(translation) > 4000
            or not translation.strip()
        ):
            fail(f"{pack_id}: invalid source/translation entry")
        if source not in source_catalog:
            fail(f"{pack_id}: source string is not in the current catalogue: {source!r}")
        source_placeholders = Counter(PLACEHOLDER.findall(source))
        translation_placeholders = Counter(PLACEHOLDER.findall(translation))
        if source_placeholders != translation_placeholders:
            fail(f"{pack_id}: placeholder mismatch in {source!r}")
        if "{" in source:
            template_count += 1
    if template_count > MAX_TEMPLATES:
        fail(f"{pack_id}: more than {MAX_TEMPLATES} dynamic-template strings")


def main() -> None:
    catalog_revision = current_catalog_revision()
    source_catalog_data = read_json(SOURCE_CATALOG, 10 * 1024 * 1024)
    if not isinstance(source_catalog_data, list) or not all(
        isinstance(value, str) for value in source_catalog_data
    ):
        fail("source UI catalogue must be an array of strings")
    if len(source_catalog_data) != len(set(source_catalog_data)):
        fail("source UI catalogue contains duplicate strings")
    source_catalog = set(source_catalog_data)

    index = read_json(INDEX, MAX_INDEX_BYTES)
    if not isinstance(index, dict) or set(index) != INDEX_FIELDS:
        fail("index must contain exactly schema_version, catalog_version, updated_at and packs")
    if type(index["schema_version"]) is not int or index["schema_version"] != 1:
        fail("unsupported Hub index schema_version")
    if index["catalog_version"] != catalog_revision:
        fail("Hub index catalogue revision does not match Android/Desktop")
    valid_date(index["updated_at"], "index.updated_at")
    packs = index["packs"]
    if not isinstance(packs, list) or len(packs) > MAX_PACKS:
        fail(f"index.packs must be an array with at most {MAX_PACKS} entries")

    seen_ids: set[str] = set()
    seen_locales: set[str] = set()
    for position, entry in enumerate(packs):
        label = f"index.packs[{position}]"
        if not isinstance(entry, dict) or set(entry) != ENTRY_FIELDS:
            fail(f"{label} must contain exactly the required entry fields")
        for field in ("id", "locale", "name", "author", "description", "version",
                      "catalog_version", "updated_at", "download_url", "sha256"):
            if not isinstance(entry[field], str):
                fail(f"{label}.{field} must be a string")
        if not PACK_ID.fullmatch(entry["id"]) or entry["id"] in seen_ids:
            fail(f"{label}: invalid or duplicate pack ID")
        seen_ids.add(entry["id"])
        if not LOCALE.fullmatch(entry["locale"]):
            fail(f"{label}: invalid locale")
        normalized_locale = entry["locale"].replace("_", "-").lower()
        if normalized_locale in seen_locales:
            fail(f"{label}: duplicate locale {entry['locale']}")
        seen_locales.add(normalized_locale)
        if not 1 <= len(entry["name"]) <= 80:
            fail(f"{label}: name must contain 1..80 characters")
        if len(entry["author"]) > 120:
            fail(f"{label}: author is too long")
        if not 1 <= len(entry["description"]) <= 240:
            fail(f"{label}: description must contain 1..240 characters")
        if not VERSION.fullmatch(entry["version"]):
            fail(f"{label}: version must use MAJOR.MINOR.PATCH")
        if entry["catalog_version"] != catalog_revision:
            fail(f"{label}: package catalogue revision is stale")
        if type(entry["strings_count"]) is not int or not 1 <= entry["strings_count"] <= MAX_STRINGS:
            fail(f"{label}: invalid strings_count")
        valid_date(entry["updated_at"], f"{label}.updated_at")
        if not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]):
            fail(f"{label}: SHA-256 must be 64 lowercase hexadecimal characters")
        if not entry["download_url"].startswith(INDEX_URL_PREFIX):
            fail(f"{label}: download URL is outside the official catalogue path")
        validate_pack_file(entry, source_catalog)

    print(
        "community Hub audit: OK — "
        f"{len(packs)} published packs, "
        f"{len(source_catalog)} source strings, "
        f"catalogue revision {catalog_revision}; "
        "index, packages, metadata, SHA-256 and placeholders are valid."
    )


if __name__ == "__main__":
    main()
