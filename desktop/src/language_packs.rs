//! User-created language packs for the community localization demo.
//!
//! Format v1 uses exact English UI strings as keys. This is intentionally pragmatic: it can be
//! integrated at the existing text-rendering boundary today, while a future upstream migration
//! can move these entries to stable message IDs without changing import/export workflows.

use std::borrow::Cow;
use std::cell::RefCell;
use std::collections::{BTreeMap, BTreeSet};
use std::io::{Read, Write};
use std::process::Command;
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, Receiver};
use std::sync::{Arc, OnceLock, RwLock};
use std::thread;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

const FORMAT_VERSION: u32 = 1;
const MAX_PACK_BYTES: u64 = 5 * 1024 * 1024;
const MAX_STRINGS: usize = 20_000;
const MAX_TEMPLATE_STRINGS: usize = 128;
const MAX_SOURCE_TEXT_BYTES: usize = 4096;
const MAX_SOURCE_KEY_CHARS: usize = 512;

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct LanguagePack {
    format_version: u32,
    id: String,
    locale: String,
    name: String,
    #[serde(default)]
    author: String,
    #[serde(default = "unknown_version")]
    app_version: String,
    #[serde(default = "catalog_version")]
    catalog_version: String,
    #[serde(default = "english")]
    base_locale: String,
    #[serde(default)]
    strings: BTreeMap<String, String>,
}

fn tr(source: &str) -> String {
    crate::translation::translate(source).into_owned()
}

fn unknown_version() -> String {
    "unknown".to_owned()
}
fn catalog_version() -> String {
    "procraft-ui-source-3".to_owned()
}
fn english() -> String {
    "en".to_owned()
}

#[derive(Clone)]
enum Active {
    Unloaded,
    Russian,
    English,
    Custom(Arc<LanguagePack>, Arc<Vec<(String, String)>>),
}

static ACTIVE: OnceLock<RwLock<Active>> = OnceLock::new();

fn active_custom(pack: LanguagePack) -> Active {
    let templates = pack
        .strings
        .iter()
        .filter(|(source, translated)| source.contains('{') && !translated.trim().is_empty())
        .map(|(source, translated)| (source.clone(), translated.clone()))
        .collect();
    Active::Custom(Arc::new(pack), Arc::new(templates))
}

fn active_lock() -> &'static RwLock<Active> {
    ACTIVE.get_or_init(|| RwLock::new(Active::Unloaded))
}

fn packs_dir() -> PathBuf {
    crate::files::app_dir().join("language-packs")
}

fn active_id_path() -> PathBuf {
    packs_dir().join("active-id")
}

fn pack_path(id: &str) -> PathBuf {
    packs_dir().join(format!("{id}.procraft-lang"))
}

fn read_pack(path: &Path) -> Result<LanguagePack, String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let mut bounded = file.take(MAX_PACK_BYTES + 1);
    let mut raw = String::new();
    bounded.read_to_string(&mut raw).map_err(|e| e.to_string())?;
    if raw.len() as u64 > MAX_PACK_BYTES {
        return Err("Language pack is larger than 5 MB.".to_owned());
    }
    let pack: LanguagePack = serde_json::from_str(&raw).map_err(|e| format!("Invalid language pack: {e}"))?;
    validate(&pack)?;
    Ok(pack)
}

fn current() -> Active {
    let lock = active_lock();
    let loaded = lock.read().map(|state| state.clone()).unwrap_or(Active::Russian);
    if !matches!(loaded, Active::Unloaded) {
        return loaded;
    }
    let next = std::fs::read_to_string(active_id_path())
        .ok()
        .and_then(|id| {
            let id = id.trim();
            match id {
                "builtin:en" => Some(Active::English),
                "" | "builtin:ru" => Some(Active::Russian),
                _ if valid_id(id) => read_pack(&pack_path(id)).ok().map(active_custom),
                _ => None,
            }
        })
        .unwrap_or(Active::Russian);
    if let Ok(mut state) = lock.write() {
        *state = next.clone();
    }
    next
}

/// An optional override for the compiled translation dictionary. Custom packs fall back to the
/// English source string when an entry is absent; they never silently inherit another language.
pub fn translate_override<'a>(source: &'a str) -> Option<Cow<'a, str>> {
    match current() {
        Active::Unloaded | Active::Russian => None,
        Active::English => Some(Cow::Borrowed(source)),
        Active::Custom(pack, templates) => {
            let exact = pack.strings.get(source).filter(|value| !value.trim().is_empty()).cloned();
            let translated = exact
                .or_else(|| {
                    if source.len() > MAX_SOURCE_TEXT_BYTES {
                        None
                    } else {
                        templates.iter().find_map(|(pattern, value)| translate_template(source, pattern, value))
                    }
                })
                .unwrap_or_else(|| source.to_owned());
            Some(Cow::Owned(translated))
        }
    }
}

/// Matches a source string against a template such as "{count} artworks" and substitutes the
/// captured values into the translated template. Placeholders are matched by name, not position.
fn translate_template(source: &str, pattern: &str, translated: &str) -> Option<String> {
    let mut spans = Vec::<(String, usize, usize)>::new();
    let mut open = None;
    for (index, ch) in pattern.char_indices() {
        if ch == '{' {
            open = Some(index);
        } else if ch == '}' {
            let start = open.take()?;
            let end = index + ch.len_utf8();
            let name = pattern.get(start + 1..index)?.to_owned();
            if name.is_empty() || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
                return None;
            }
            spans.push((name, start, end));
        }
    }
    if spans.is_empty() || open.is_some() {
        return None;
    }

    let mut remaining = source;
    let mut cursor = 0;
    let mut values = BTreeMap::<String, String>::new();
    for (index, (name, start, end)) in spans.iter().enumerate() {
        let literal = pattern.get(cursor..*start)?;
        remaining = remaining.strip_prefix(literal)?;
        let is_last = index + 1 == spans.len();
        let next_start = spans.get(index + 1).map_or(pattern.len(), |next| next.1);
        let delimiter = pattern.get(*end..next_start)?;
        if !is_last && delimiter.is_empty() {
            return None;
        }
        let value_end = if is_last {
            let suffix = pattern.get(*end..)?;
            if !remaining.ends_with(suffix) {
                return None;
            }
            remaining.len().checked_sub(suffix.len())?
        } else {
            remaining.find(delimiter)?
        };
        let value = remaining.get(..value_end)?;
        if value.is_empty() {
            return None;
        }
        if let Some(previous) = values.insert(name.clone(), value.to_owned())
            && previous != value
        {
            return None;
        }
        if is_last {
            let suffix = pattern.get(*end..)?;
            remaining = remaining.get(value_end + suffix.len()..)?;
        } else {
            remaining = remaining.get(value_end + delimiter.len()..)?;
            cursor = next_start;
        }
    }
    if !remaining.is_empty() {
        return None;
    }

    let mut output = translated.to_owned();
    for (name, value) in values {
        output = output.replace(&format!("{{{name}}}"), &value);
    }
    Some(output)
}

fn active_id() -> String {
    match current() {
        Active::English => "builtin:en".to_owned(),
        Active::Russian | Active::Unloaded => "builtin:ru".to_owned(),
        Active::Custom(pack, _) => pack.id.clone(),
    }
}

fn activate_builtin(id: &str) -> Result<(), String> {
    if id != "builtin:ru" && id != "builtin:en" {
        return Err("Unknown built-in language.".to_owned());
    }
    std::fs::create_dir_all(packs_dir()).map_err(|e| e.to_string())?;
    std::fs::write(active_id_path(), id).map_err(|e| e.to_string())?;
    if let Ok(mut state) = active_lock().write() {
        *state = if id == "builtin:en" { Active::English } else { Active::Russian };
    }
    Ok(())
}

fn ensure_import_does_not_replace(existing: Option<&LanguagePack>, incoming: &LanguagePack) -> Result<(), String> {
    if existing.is_some_and(|installed| installed != incoming) {
        return Err("A different language pack with this ID is already installed.".to_owned());
    }
    Ok(())
}

fn import_pack(path: &Path) -> Result<LanguagePack, String> {
    let pack = read_pack(path)?;
    let destination = pack_path(&pack.id);
    if destination.exists() {
        let existing = read_pack(&destination)?;
        ensure_import_does_not_replace(Some(&existing), &pack)?;
        std::fs::write(active_id_path(), &pack.id).map_err(|e| e.to_string())?;
        if let Ok(mut state) = active_lock().write() {
            *state = active_custom(pack.clone());
        }
    } else {
        activate_pack(&pack)?;
    }
    Ok(pack)
}

fn activate_pack(pack: &LanguagePack) -> Result<(), String> {
    validate(pack)?;
    save_pack(pack)?;
    std::fs::write(active_id_path(), &pack.id).map_err(|e| e.to_string())?;
    if let Ok(mut state) = active_lock().write() {
        *state = active_custom(pack.clone());
    }
    Ok(())
}

fn write_atomic(path: &Path, content: &[u8]) -> Result<(), String> {
    let parent = path.parent().ok_or_else(|| "Invalid storage path.".to_owned())?;
    std::fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    let nonce = SystemTime::now().duration_since(UNIX_EPOCH)
        .map_err(|error| error.to_string())?.as_nanos();
    let temporary = path.with_extension(format!("tmp-{}-{nonce}", std::process::id()));
    let rollback = path.with_extension(format!("rollback-{}-{nonce}", std::process::id()));

    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temporary)
        .map_err(|error| format!("Could not create a temporary file: {error}"))?;
    if let Err(error) = file.write_all(content).and_then(|()| file.sync_all()) {
        let _ = std::fs::remove_file(&temporary);
        return Err(format!("Could not write a temporary file: {error}"));
    }
    drop(file);

    let had_previous = path.exists();
    if had_previous {
        std::fs::rename(path, &rollback)
            .map_err(|error| format!("Could not preserve the previous file: {error}"))?;
    }
    if let Err(error) = std::fs::rename(&temporary, path) {
        if had_previous {
            let _ = std::fs::rename(&rollback, path);
        }
        let _ = std::fs::remove_file(&temporary);
        return Err(format!("Could not replace the file: {error}"));
    }
    if had_previous {
        let _ = std::fs::remove_file(rollback);
    }
    Ok(())
}

fn save_pack(pack: &LanguagePack) -> Result<(), String> {
    validate(pack)?;
    let content = serde_json::to_vec_pretty(pack).map_err(|e| e.to_string())?;
    write_atomic(&pack_path(&pack.id), &content)
}

fn export_pack(pack: &LanguagePack, reveal: bool) -> Result<(), String> {
    let Some(path) = rfd::FileDialog::new()
        .add_filter("ProCraft language pack", &["procraft-lang"])
        .set_file_name(format!("{}.procraft-lang", pack.id))
        .save_file()
    else {
        return Ok(());
    };
    let content = serde_json::to_string_pretty(pack).map_err(|e| e.to_string())?;
    std::fs::write(&path, content).map_err(|e| e.to_string())?;
    if reveal {
        reveal_pack(&path);
    }
    Ok(())
}

fn reveal_pack(path: &Path) {
    #[cfg(windows)]
    {
        let target = format!("/select,{}", path.display());
        let _ = std::process::Command::new("explorer").arg(target).spawn();
    }
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg("-R").arg(path).spawn();
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        if let Some(parent) = path.parent() {
            let _ = std::process::Command::new("xdg-open").arg(parent).spawn();
        }
    }
}

fn installed() -> Vec<LanguagePack> {
    let Ok(entries) = std::fs::read_dir(packs_dir()) else {
        return Vec::new();
    };
    let mut packs: Vec<LanguagePack> = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let path = entry.path();
            (path.extension().is_some_and(|ext| ext == "procraft-lang")).then(|| read_pack(&path).ok()).flatten()
        })
        .collect();
    packs.sort_by_key(|pack| pack.name.to_lowercase());
    packs
}

fn valid_id(id: &str) -> bool {
    let mut chars = id.chars();
    let Some(first) = chars.next() else { return false };
    id.len() >= 3
        && id.len() <= 96
        && (first.is_ascii_lowercase() || first.is_ascii_digit())
        && chars.all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || matches!(c, '.' | '_' | '-'))
}

fn valid_locale(locale: &str) -> bool {
    let mut parts = locale.split(['-', '_']);
    let Some(language) = parts.next() else { return false };
    (2..=8).contains(&language.len())
        && language.chars().all(|ch| ch.is_ascii_alphabetic())
        && parts.all(|part| (2..=8).contains(&part.len()) && part.chars().all(|ch| ch.is_ascii_alphanumeric()))
}

fn is_rtl_locale(locale: &str) -> bool {
    let parts: Vec<_> = locale.split(['-', '_']).filter(|part| !part.is_empty()).collect();

    // An explicit script subtag takes precedence over the language. For example,
    // ar-Latn is written in Latin script and must remain left-to-right.
    if let Some(script) = parts.iter().find(|part| part.len() == 4) {
        return matches!(
            script.to_ascii_lowercase().as_str(),
            "arab" | "hebr" | "thaa" | "nkoo" | "adlm" | "syrc"
        );
    }

    let language = parts.first().copied().unwrap_or("").to_ascii_lowercase();
    matches!(
        language.as_str(),
        "ar" | "he" | "fa" | "ur" | "ps" | "sd" | "ug" | "yi" | "dv" | "ku" | "ckb" | "nqo" | "syr"
    )
}

pub fn is_rtl_active() -> bool {
    match current() {
        Active::Custom(pack, _) => is_rtl_locale(&pack.locale),
        Active::Unloaded | Active::Russian | Active::English => false,
    }
}

fn placeholders(text: &str) -> Vec<String> {
    let mut values = Vec::new();
    let mut start = None;
    for (index, ch) in text.char_indices() {
        if ch == '{' {
            start = Some(index);
        } else if ch == '}'
            && let Some(begin) = start.take()
            && let Some(value) = text.get(begin..index.saturating_add(ch.len_utf8()))
        {
            values.push(value.to_owned());
        }
    }
    values.sort();
    values
}

fn validate(pack: &LanguagePack) -> Result<(), String> {
    if pack.format_version != FORMAT_VERSION {
        return Err("Unsupported language-pack format version.".to_owned());
    }
    if !valid_id(&pack.id) {
        return Err("Invalid language-pack ID.".to_owned());
    }
    if !valid_locale(&pack.locale) || pack.locale.len() > 35 || pack.name.trim().is_empty() || pack.name.len() > 80 {
        return Err("Invalid language-pack name or locale.".to_owned());
    }
    if pack.author.len() > 120
        || pack.app_version.len() > 40
        || pack.catalog_version.len() > 80
        || pack.base_locale.len() > 35
        || pack.strings.len() > MAX_STRINGS
    {
        return Err("Language-pack metadata or string count exceeds the format limits.".to_owned());
    }
    if pack.strings.keys().filter(|source| source.contains('{')).count() > MAX_TEMPLATE_STRINGS {
        return Err("Language pack has too many dynamic templates (maximum 128).".to_owned());
    }
    for (source, translated) in &pack.strings {
        if source.is_empty() || source.chars().count() > MAX_SOURCE_KEY_CHARS || translated.len() > 4000 {
            return Err("A string exceeds the format limits.".to_owned());
        }
        if placeholders(source) != placeholders(translated) {
            return Err(format!("Placeholder mismatch in: {source}"));
        }
    }
    Ok(())
}

fn empty_pack(name: &str, locale: &str, author: &str) -> Result<LanguagePack, String> {
    if name.trim().is_empty() || name.len() > 80 {
        return Err("Name must contain 1–80 characters.".to_owned());
    }
    if !valid_locale(locale) || locale.len() > 35 {
        return Err("Enter a locale such as es-ES or de-DE.".to_owned());
    }
    let id = format!(
        "{}.{}",
        locale.to_ascii_lowercase().replace('_', "-"),
        std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map_or(1, |duration| duration.as_nanos())
    );
    let pack = LanguagePack {
        format_version: FORMAT_VERSION,
        id,
        locale: locale.to_owned(),
        name: name.trim().to_owned(),
        author: author.trim().chars().take(120).collect(),
        app_version: "0.0.7-beta".to_owned(),
        catalog_version: catalog_version(),
        base_locale: english(),
        strings: BTreeMap::new(),
    };
    validate(&pack)?;
    Ok(pack)
}


const HUB_INDEX_URL: &str = "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/index.json";
const HUB_RAW_PREFIX: &str = "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/";
const MAX_HUB_INDEX_BYTES: usize = 512 * 1024;
const HUB_CHECK_INTERVAL_SECS: u64 = 6 * 60 * 60;

fn hub_data_dir() -> PathBuf {
    packs_dir().join(".hub")
}

fn hub_baselines_dir() -> PathBuf {
    hub_data_dir().join("baselines")
}

fn hub_metadata_dir() -> PathBuf {
    hub_data_dir().join("metadata")
}

fn hub_backups_dir() -> PathBuf {
    hub_data_dir().join("backups")
}

fn hub_baseline_path(id: &str) -> PathBuf {
    hub_baselines_dir().join(format!("{id}.procraft-lang"))
}

fn hub_metadata_path(id: &str) -> PathBuf {
    hub_metadata_dir().join(format!("{id}.json"))
}

fn hub_catalog_cache_path() -> PathBuf {
    hub_data_dir().join("index.json")
}

fn hub_last_check_path() -> PathBuf {
    hub_data_dir().join("last-check")
}

fn hub_check_due() -> bool {
    let last = std::fs::read_to_string(hub_last_check_path())
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
        .unwrap_or(0);
    let now = SystemTime::now().duration_since(UNIX_EPOCH)
        .map_or(0, |duration| duration.as_secs());
    last == 0 || now < last || now.saturating_sub(last) >= HUB_CHECK_INTERVAL_SECS
}

fn cached_hub_catalog() -> Vec<HubEntry> {
    let path = hub_catalog_cache_path();
    let Ok(metadata) = std::fs::metadata(&path) else { return Vec::new() };
    if metadata.len() > MAX_HUB_INDEX_BYTES as u64 {
        return Vec::new();
    }
    std::fs::read(path)
        .ok()
        .and_then(|raw| parse_hub_catalog(&raw).ok())
        .unwrap_or_default()
}

fn fetch_and_cache_hub_catalog() -> Result<Vec<HubEntry>, String> {
    let raw = fetch_hub_bytes(HUB_INDEX_URL, MAX_HUB_INDEX_BYTES)?;
    let entries = parse_hub_catalog(&raw)?;
    write_atomic(&hub_catalog_cache_path(), &raw)?;
    let now = SystemTime::now().duration_since(UNIX_EPOCH)
        .map_err(|error| format!("System clock error: {error}"))?.as_secs();
    write_atomic(&hub_last_check_path(), now.to_string().as_bytes())?;
    Ok(entries)
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct HubCatalog {
    schema_version: u32,
    catalog_version: String,
    updated_at: String,
    packs: Vec<HubEntry>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct HubEntry {
    id: String,
    locale: String,
    name: String,
    author: String,
    description: String,
    version: String,
    catalog_version: String,
    strings_count: usize,
    updated_at: String,
    download_url: String,
    sha256: String,
}

fn valid_hub_date(value: &str) -> bool {
    if value.len() != 10 {
        return false;
    }
    let mut parts = value.split('-');
    let year = parts.next().and_then(|part| part.parse::<u32>().ok());
    let month = parts.next().and_then(|part| part.parse::<u32>().ok());
    let day = parts.next().and_then(|part| part.parse::<u32>().ok());
    year.is_some_and(|year| year >= 2020)
        && month.is_some_and(|month| (1..=12).contains(&month))
        && day.is_some_and(|day| (1..=31).contains(&day))
        && parts.next().is_none()
}

fn validate_hub_url(url: &str) -> Result<(), String> {
    if url.len() > 512
        || !url.starts_with(HUB_RAW_PREFIX)
        || !url.ends_with(".procraft-lang")
        || url.chars().any(|character| matches!(character, '?' | '#' | '\\'))
    {
        return Err("The catalogue contains an unsafe download URL.".to_owned());
    }
    Ok(())
}

fn parse_hub_catalog(raw: &[u8]) -> Result<Vec<HubEntry>, String> {
    if raw.len() > MAX_HUB_INDEX_BYTES {
        return Err("The community catalogue is larger than 512 KB.".to_owned());
    }
    let catalog: HubCatalog = serde_json::from_slice(raw)
        .map_err(|error| format!("Invalid community catalogue: {error}"))?;
    if catalog.schema_version != 1 {
        return Err("Unsupported community catalogue version.".to_owned());
    }
    if catalog.catalog_version != catalog_version() {
        return Err("The community catalogue targets a different source-string catalogue. Update ProCraft first.".to_owned());
    }
    if !valid_hub_date(&catalog.updated_at) {
        return Err("The community catalogue has an invalid update date.".to_owned());
    }
    if catalog.packs.len() > 100 {
        return Err("The community catalogue contains too many translations.".to_owned());
    }

    let mut ids = std::collections::HashSet::new();
    for entry in &catalog.packs {
        if !valid_id(&entry.id) || !ids.insert(entry.id.as_str()) {
            return Err("The community catalogue contains an invalid or duplicate pack ID.".to_owned());
        }
        if !valid_locale(&entry.locale) || entry.locale.len() > 35 {
            return Err(format!("Invalid locale for {}.", entry.id));
        }
        if entry.name.trim().is_empty() || entry.name.len() > 80 || entry.author.len() > 120 {
            return Err(format!("Invalid metadata for {}.", entry.id));
        }
        if entry.description.trim().is_empty() || entry.description.len() > 240 {
            return Err(format!("Invalid description for {}.", entry.id));
        }
        let mut version_parts = entry.version.split('.');
        let valid_version = version_parts.clone().count() == 3
            && version_parts.all(|part| !part.is_empty() && part.bytes().all(|byte| byte.is_ascii_digit()));
        if !valid_version {
            return Err(format!("Invalid version for {}.", entry.id));
        }
        if entry.catalog_version != catalog_version() {
            return Err(format!("Translation {} targets a different source-string catalogue.", entry.id));
        }
        if !(1..=MAX_STRINGS).contains(&entry.strings_count) {
            return Err(format!("Invalid translated-string count for {}.", entry.id));
        }
        if !valid_hub_date(&entry.updated_at) {
            return Err(format!("Invalid update date for {}.", entry.id));
        }
        validate_hub_url(&entry.download_url)?;
        if entry.sha256.len() != 64
            || !entry.sha256.bytes().all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
        {
            return Err(format!("Invalid SHA-256 digest for {}.", entry.id));
        }
    }
    Ok(catalog.packs)
}

fn fetch_hub_bytes(url: &str, max_bytes: usize) -> Result<Vec<u8>, String> {
    if url != HUB_INDEX_URL {
        validate_hub_url(url)?;
    }
    let max_filesize = max_bytes.to_string();
    let output = Command::new("curl")
        .args([
            "--disable",
            "--fail",
            "--location",
            "--silent",
            "--show-error",
            "--connect-timeout",
            "10",
            "--max-time",
            "25",
            "--max-filesize",
            &max_filesize,
            "--proto",
            "=https",
            "--proto-redir",
            "=https",
            "--tlsv1.2",
            "--user-agent",
            "ProCraft-Translation-Hub/1",
            url,
        ])
        .output()
        .map_err(|error| format!("Could not start curl to access the Translation Hub: {error}"))?;
    if !output.status.success() {
        let detail = String::from_utf8_lossy(&output.stderr);
        return Err(format!("Could not download from the Translation Hub: {}", detail.trim()));
    }
    if output.stdout.len() > max_bytes {
        return Err("A downloaded Hub file exceeds the allowed size.".to_owned());
    }
    Ok(output.stdout)
}

fn digest_from_command_output(output: &[u8], spaced: bool) -> Option<String> {
    let text = String::from_utf8_lossy(output);
    for line in text.lines() {
        let candidate = if spaced {
            line.chars().filter(|character| character.is_ascii_hexdigit()).collect::<String>()
        } else {
            line.split_whitespace().next().unwrap_or_default().to_owned()
        };
        if candidate.len() == 64 && candidate.bytes().all(|byte| byte.is_ascii_hexdigit()) {
            return Some(candidate.to_ascii_lowercase());
        }
    }
    None
}

fn sha256_file(path: &Path) -> Result<String, String> {
    #[cfg(windows)]
    let output = Command::new("certutil")
        .arg("-hashfile")
        .arg(path)
        .arg("SHA256")
        .output()
        .map_err(|error| format!("Could not start certutil for SHA-256 verification: {error}"))?;
    #[cfg(windows)]
    let digest = digest_from_command_output(&output.stdout, true);

    #[cfg(not(windows))]
    let digest = {
        let primary = Command::new("sha256sum").arg(path).output();
        let primary_digest = primary.ok().filter(|result| result.status.success())
            .and_then(|result| digest_from_command_output(&result.stdout, false));
        primary_digest.or_else(|| {
            Command::new("shasum").arg("-a").arg("256").arg(path).output().ok()
                .filter(|result| result.status.success())
                .and_then(|result| digest_from_command_output(&result.stdout, false))
        })
    };

    digest.ok_or_else(|| "No supported SHA-256 utility is available (certutil, sha256sum or shasum).".to_owned())
}

fn verify_hub_pack(entry: &HubEntry) -> Result<LanguagePack, String> {
    validate_hub_url(&entry.download_url)?;
    let bytes = fetch_hub_bytes(&entry.download_url, MAX_PACK_BYTES as usize)?;
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)
        .map_err(|error| format!("System clock error: {error}"))?.as_nanos();
    let temp_path = std::env::temp_dir().join(format!("procraft-hub-{}-{timestamp}.tmp", std::process::id()));
    std::fs::write(&temp_path, &bytes).map_err(|error| format!("Could not verify the downloaded package: {error}"))?;
    let digest_result = sha256_file(&temp_path);
    let _ = std::fs::remove_file(&temp_path);
    let digest = digest_result?;
    if digest != entry.sha256 {
        return Err("The downloaded translation failed its SHA-256 integrity check.".to_owned());
    }
    let raw = String::from_utf8(bytes).map_err(|error| format!("The translation is not valid UTF-8: {error}"))?;
    let pack: LanguagePack = serde_json::from_str(&raw)
        .map_err(|error| format!("Invalid language-pack file: {error}"))?;
    validate(&pack)?;
    if pack.id != entry.id
        || pack.locale != entry.locale
        || pack.name != entry.name
        || pack.author != entry.author
        || pack.catalog_version != entry.catalog_version
        || pack.strings.len() != entry.strings_count
    {
        return Err("The downloaded package metadata does not match the community catalogue.".to_owned());
    }
    Ok(pack)
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct HubInstallMetadata {
    version: String,
    sha256: String,
}

struct HubInstallResult {
    pack: LanguagePack,
    local_changes_preserved: usize,
    conflicts_kept_local: usize,
    backup_name: Option<String>,
}

struct HubStringMerge {
    strings: BTreeMap<String, String>,
    local_changes_preserved: usize,
    conflicts_kept_local: usize,
}

fn merge_hub_strings(
    baseline: Option<&BTreeMap<String, String>>,
    local: &BTreeMap<String, String>,
    remote: &BTreeMap<String, String>,
) -> HubStringMerge {
    let mut keys = BTreeSet::new();
    if let Some(baseline) = baseline {
        keys.extend(baseline.keys().cloned());
    }
    keys.extend(local.keys().cloned());
    keys.extend(remote.keys().cloned());

    let mut strings = BTreeMap::new();
    let mut local_changes = 0;
    let mut conflicts = 0;
    for key in keys {
        let base_value = baseline.and_then(|values| values.get(&key));
        let local_value = local.get(&key);
        let remote_value = remote.get(&key);
        if local_value != base_value {
            local_changes += 1;
        }
        let selected = if local_value == base_value {
            remote_value
        } else if remote_value == base_value || local_value == remote_value {
            local_value
        } else {
            conflicts += 1;
            local_value
        };
        if let Some(value) = selected {
            strings.insert(key, value.clone());
        }
    }
    HubStringMerge {
        strings,
        local_changes_preserved: local_changes,
        conflicts_kept_local: conflicts,
    }
}

fn read_hub_metadata(id: &str) -> Option<HubInstallMetadata> {
    let path = hub_metadata_path(id);
    let metadata = std::fs::metadata(&path).ok()?;
    if metadata.len() > 4096 {
        return None;
    }
    let raw = std::fs::read(path).ok()?;
    let value: HubInstallMetadata = serde_json::from_slice(&raw).ok()?;
    if value.version.split('.').count() != 3
        || !value.version.bytes().all(|byte| byte.is_ascii_digit() || byte == b'.')
        || value.sha256.len() != 64
        || !value.sha256.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        return None;
    }
    Some(value)
}

fn compare_hub_versions(left: &str, right: &str) -> std::cmp::Ordering {
    let left_parts = left.split('.').map(|part| part.parse::<u64>().unwrap_or(0));
    let right_parts = right.split('.').map(|part| part.parse::<u64>().unwrap_or(0));
    left_parts.cmp(right_parts)
}

fn hub_update_available(entry: &HubEntry) -> bool {
    let Some(installed) = read_hub_metadata(&entry.id) else { return true };
    match compare_hub_versions(&entry.version, &installed.version) {
        std::cmp::Ordering::Greater => true,
        std::cmp::Ordering::Equal => entry.sha256 != installed.sha256,
        std::cmp::Ordering::Less => false,
    }
}

fn hub_has_local_changes(id: &str) -> bool {
    let Ok(local) = read_pack(&pack_path(id)) else { return false };
    let Ok(baseline) = read_pack(&hub_baseline_path(id)) else {
        return !local.strings.is_empty();
    };
    local.strings != baseline.strings
}

fn install_hub_entry(entry: &HubEntry) -> Result<HubInstallResult, String> {
    let remote = verify_hub_pack(entry)?;
    if let Some(previous) = read_hub_metadata(&entry.id)
        && compare_hub_versions(&entry.version, &previous.version).is_lt()
    {
        return Err("A downgrade of this community translation was blocked.".to_owned());
    }

    let existing = if pack_path(&entry.id).is_file() {
        Some(read_pack(&pack_path(&entry.id))?)
    } else {
        None
    };
    let baseline = read_pack(&hub_baseline_path(&entry.id)).ok();
    let merge = if let Some(local) = existing.as_ref() {
        merge_hub_strings(baseline.as_ref().map(|pack| &pack.strings), &local.strings, &remote.strings)
    } else {
        HubStringMerge {
            strings: remote.strings.clone(),
            local_changes_preserved: 0,
            conflicts_kept_local: 0,
        }
    };
    let installed = LanguagePack { strings: merge.strings, ..remote.clone() };

    let mut backup_name = None;
    if let Some(local) = existing.as_ref()
        && local != &installed
    {
        let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)
            .map_err(|error| format!("System clock error: {error}"))?.as_nanos();
        let backup = hub_backups_dir().join(format!("{}-{timestamp}.procraft-lang", entry.id));
        let backup_data = serde_json::to_vec_pretty(local).map_err(|error| error.to_string())?;
        write_atomic(&backup, &backup_data)?;
        backup_name = backup.file_name().and_then(|name| name.to_str()).map(str::to_owned);
    }

    save_pack(&installed)?;
    let baseline_data = serde_json::to_vec_pretty(&remote).map_err(|error| error.to_string())?;
    write_atomic(&hub_baseline_path(&entry.id), &baseline_data)?;
    let install_metadata = HubInstallMetadata {
        version: entry.version.clone(),
        sha256: entry.sha256.clone(),
    };
    let metadata_data = serde_json::to_vec_pretty(&install_metadata).map_err(|error| error.to_string())?;
    write_atomic(&hub_metadata_path(&entry.id), &metadata_data)?;

    Ok(HubInstallResult {
        pack: installed,
        local_changes_preserved: merge.local_changes_preserved,
        conflicts_kept_local: merge.conflicts_kept_local,
        backup_name,
    })
}

enum HubEvent {
    Catalogue(Result<Vec<HubEntry>, String>),
    Installed(Result<Box<HubInstallResult>, String>),
}

fn start_hub_refresh(state: &mut ManagerState) {
    if state.hub_receiver.is_some() {
        return;
    }
    let (sender, receiver) = mpsc::channel();
    state.hub_receiver = Some(receiver);
    state.hub_loading = true;
    state.hub_error = None;
    state.hub_status = None;
    thread::spawn(move || {
        let result = fetch_and_cache_hub_catalog();
        let _ = sender.send(HubEvent::Catalogue(result));
    });
}

fn start_hub_install(state: &mut ManagerState, entry: HubEntry) {
    if state.hub_receiver.is_some() {
        return;
    }
    let (sender, receiver) = mpsc::channel();
    state.hub_receiver = Some(receiver);
    state.hub_downloading = Some(entry.id.clone());
    state.hub_error = None;
    state.hub_status = None;
    thread::spawn(move || {
        let result = install_hub_entry(&entry).map(Box::new);
        let _ = sender.send(HubEvent::Installed(result));
    });
}

fn catalog() -> Vec<String> {
    serde_json::from_str(include_str!("../../assets/localization/ui-catalog.json")).unwrap_or_default()
}

#[derive(Default)]
struct ManagerState {
    open: bool,
    editor: Option<LanguagePack>,
    query: String,
    new_name: String,
    new_locale: String,
    new_author: String,
    making_new: bool,
    error: Option<String>,
    hub_open: bool,
    hub_entries: Vec<HubEntry>,
    hub_loading: bool,
    hub_downloading: Option<String>,
    hub_error: Option<String>,
    hub_confirm: Option<HubEntry>,
    hub_status: Option<String>,
    hub_receiver: Option<Receiver<HubEvent>>,
}

thread_local! {
    static MANAGER: RefCell<ManagerState> = RefCell::new(ManagerState::default());
}

/// Open the manager over the canvas/settings. The editor is deliberately data-only and can never
/// execute plugins, scripts or arbitrary code from an imported pack.
pub fn open() {
    MANAGER.with(|state| {
        let mut state = state.borrow_mut();
        state.open = true;
        if state.hub_entries.is_empty() {
            state.hub_entries = cached_hub_catalog();
        }
        if state.hub_receiver.is_none() && hub_check_due() {
            start_hub_refresh(&mut state);
        }
    });
}

pub fn show(ctx: &egui::Context) {
    MANAGER.with(|state| {
        let mut state = state.borrow_mut();
        if !state.open {
            return;
        }
        let (event, disconnected) = match state.hub_receiver.as_ref() {
            Some(receiver) => match receiver.try_recv() {
                Ok(event) => (Some(event), false),
                Err(std::sync::mpsc::TryRecvError::Empty) => (None, false),
                Err(std::sync::mpsc::TryRecvError::Disconnected) => (None, true),
            },
            None => (None, false),
        };
        if event.is_some() || disconnected {
            state.hub_receiver = None;
        }
        if disconnected {
            state.hub_loading = false;
            state.hub_downloading = None;
            state.hub_error = Some("The Translation Hub task stopped unexpectedly.".to_owned());
        }
        if let Some(event) = event {
            match event {
                HubEvent::Catalogue(Ok(entries)) => {
                    state.hub_entries = entries;
                    state.hub_loading = false;
                    state.hub_error = None;
                    state.hub_status = Some("Community catalogue updated.".to_owned());
                }
                HubEvent::Catalogue(Err(error)) => {
                    state.hub_loading = false;
                    state.hub_error = Some(error);
                    state.hub_status = None;
                }
                HubEvent::Installed(Ok(result)) => {
                    state.hub_downloading = None;
                    match activate_pack(&result.pack) {
                        Ok(()) => {
                            state.hub_error = None;
                            let mut message = format!("{} is installed and active.", result.pack.name);
                            if result.local_changes_preserved > 0 {
                                message.push_str(&format!(" Preserved {} local edit(s).", result.local_changes_preserved));
                            }
                            if result.conflicts_kept_local > 0 {
                                message.push_str(&format!(" Kept {} conflicting local value(s).", result.conflicts_kept_local));
                            }
                            if let Some(backup) = result.backup_name {
                                message.push_str(&format!(" Backup: {backup}."));
                            }
                            state.hub_status = Some(message);
                        }
                        Err(error) => {
                            state.hub_error = Some(error);
                            state.hub_status = None;
                        }
                    }
                }
                HubEvent::Installed(Err(error)) => {
                    state.hub_downloading = None;
                    state.hub_error = Some(error);
                    state.hub_status = None;
                }
            }
        }
        if state.hub_receiver.is_some() {
            ctx.request_repaint_after(std::time::Duration::from_millis(250));
        }
        let mut open = state.open;
        egui::Window::new(tr("Community translations"))
            .id(egui::Id::unique("community_language_packs_manager"))
            .open(&mut open)
            .order(egui::Order::Foreground)
            .anchor(egui::Align2::CENTER_CENTER, egui::Vec2::ZERO)
            .default_width(760.0)
            .default_height(640.0)
            .min_width(640.0)
            .min_height(420.0)
            .resizable(true)
            .show(ctx, |ui| {
                if let Some(error) = state.error.take() {
                    ui.colored_label(egui::Color32::LIGHT_RED, error);
                }
                if let Some(mut pack) = state.editor.take() {
                    let mut close_editor = false;
                    let rtl = is_rtl_locale(&pack.locale);
                    let editor_layout = if rtl {
                        egui::Layout::right_to_left(egui::Align::Center)
                    } else {
                        egui::Layout::left_to_right(egui::Align::Center)
                    };
                    ui.with_layout(editor_layout, |ui| {
                        if ui.button(tr("← Packs")).clicked() {
                            close_editor = true;
                        }
                        ui.heading(format!("{} · {}", pack.name, pack.locale));
                        ui.with_layout(editor_layout, |ui| {
                            if ui.button(tr("Save")).clicked() {
                                match activate_pack(&pack) {
                                    Ok(()) => close_editor = true,
                                    Err(error) => state.error = Some(error),
                                }
                            }
                            if ui.button(tr("Cancel")).clicked() {
                                close_editor = true;
                            }
                        });
                    });
                    ui.label(tr("Translate English source strings. Empty values use English fallback."));
                    ui.add(egui::TextEdit::singleline(&mut state.query).hint_text(tr("Search source text")));
                    let search = state.query.to_lowercase();
                    let entries = catalog();
                    egui::ScrollArea::vertical().max_height(510.0).show(ui, |ui| {
                        for source in
                            entries.iter().filter(|source| search.is_empty() || source.to_lowercase().contains(&search))
                        {
                            ui.with_layout(editor_layout, |ui| {
                                ui.add_sized([310.0, 28.0], egui::Label::new(source));
                                let mut value = pack.strings.get(source).cloned().unwrap_or_default();
                                let text_align = if rtl { egui::Align2::RIGHT_CENTER } else { egui::Align2::LEFT_CENTER };
                                if ui
                                    .add_sized(
                                        [ui.available_width(), 28.0],
                                        egui::TextEdit::singleline(&mut value).align(text_align),
                                    )
                                    .changed()
                                {
                                    if value.trim().is_empty() {
                                        pack.strings.remove(source);
                                    } else {
                                        pack.strings.insert(source.clone(), value);
                                    }
                                }
                            });
                        }
                    });
                    ui.label(format!("{} translated strings", pack.strings.len()));
                    if !close_editor {
                        state.editor = Some(pack);
                    }
                } else if state.hub_open {
                    ui.horizontal_wrapped(|ui| {
                        if ui.button(tr("← Installed packs")).clicked() {
                            state.hub_open = false;
                        }
                        if ui.add_enabled(state.hub_receiver.is_none(), egui::Button::new(tr("Refresh catalogue"))).clicked() {
                            start_hub_refresh(&mut state);
                        }
                        if state.hub_loading {
                            ui.spinner();
                            ui.label(tr("Loading community catalogue…"));
                        }
                    });
                    if let Some(error) = state.hub_error.as_ref() {
                        ui.colored_label(egui::Color32::LIGHT_RED, error);
                    }
                    if let Some(status) = state.hub_status.as_ref() {
                        ui.colored_label(egui::Color32::LIGHT_GREEN, status);
                    }
                    if let Some(entry) = state.hub_confirm.clone() {
                        ui.group(|ui| {
                            ui.label(format!("Update the installed copy of {}?", entry.name));
                            ui.label(tr("Local changes are preserved. Conflicts keep your local text, and a backup is created before changed content is saved."));
                            ui.horizontal(|ui| {
                                if ui.button(tr("Update and merge")).clicked() && state.hub_receiver.is_none() {
                                    state.hub_confirm = None;
                                    start_hub_install(&mut state, entry.clone());
                                }
                                if ui.button(tr("Cancel")).clicked() {
                                    state.hub_confirm = None;
                                }
                            });
                        });
                    }
                    let entries = state.hub_entries.clone();
                    let installed_packs = installed();
                    if entries.is_empty() && !state.hub_loading && state.hub_error.is_none() {
                        ui.label(tr("No community translations have been published yet. New languages will appear here after review."));
                    }
                    egui::ScrollArea::vertical().max_height(480.0).show(ui, |ui| {
                        for entry in &entries {
                            ui.group(|ui| {
                                ui.horizontal(|ui| {
                                    ui.heading(&entry.name);
                                    ui.label(&entry.locale);
                                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                                        ui.label(format!("v{}", entry.version));
                                    });
                                });
                                ui.label(format!("{} · {} strings · updated {}", entry.author, entry.strings_count, entry.updated_at));
                                ui.label(&entry.description);
                                let is_installed = installed_packs.iter().any(|pack| pack.id == entry.id);
                                let update_available = is_installed && hub_update_available(entry);
                                let has_local_changes = is_installed && hub_has_local_changes(&entry.id);
                                let is_downloading = state.hub_downloading.as_deref() == Some(entry.id.as_str());
                                if update_available {
                                    ui.colored_label(egui::Color32::LIGHT_BLUE, tr("Update available"));
                                } else if is_installed {
                                    ui.label(tr("Installed version is up to date"));
                                }
                                if has_local_changes {
                                    ui.label(tr("Local edits detected; updates will preserve them and make a backup."));
                                }
                                ui.horizontal(|ui| {
                                    if is_downloading {
                                        ui.spinner();
                                        ui.label(tr("Verifying and merging…"));
                                    } else if is_installed {
                                        let label = if update_available { tr("Update and merge") } else { tr("Reinstall safely") };
                                        if ui.add_enabled(state.hub_receiver.is_none(), egui::Button::new(label)).clicked() {
                                            state.hub_confirm = Some(entry.clone());
                                        }
                                    } else if ui.add_enabled(state.hub_receiver.is_none(), egui::Button::new(tr("Install"))).clicked() {
                                        start_hub_install(&mut state, entry.clone());
                                    }
                                });
                            });
                        }
                    });
                    if state.hub_receiver.is_some() {
                        ctx.request_repaint_after(std::time::Duration::from_millis(250));
                    }
                } else {
                    ui.label(tr("Create, edit, import and export translations. Missing strings fall back to English."));
                    ui.horizontal_wrapped(|ui| {
                        let installed_for_updates = installed();
                        let available_updates = state.hub_entries.iter()
                            .filter(|entry| installed_for_updates.iter().any(|pack| pack.id == entry.id))
                            .filter(|entry| hub_update_available(entry))
                            .count();
                        let hub_label = if available_updates > 0 {
                            format!("{} · {} update(s)", tr("Community translations"), available_updates)
                        } else {
                            tr("Community translations")
                        };
                        if ui.button(hub_label).clicked() {
                            state.hub_open = true;
                            if state.hub_entries.is_empty() && !state.hub_loading {
                                start_hub_refresh(&mut state);
                            }
                        }
                        if state.hub_loading {
                            ui.label(tr("Checking the translation catalogue…"));
                        }
                        if ui.button(tr("New translation")).clicked() {
                            state.making_new = !state.making_new;
                        }
                        if ui.button(tr("Import .procraft-lang")).clicked()
                            && let Some(path) = rfd::FileDialog::new()
                                .add_filter("ProCraft language pack", &["procraft-lang"])
                                .pick_file()
                        {
                            match import_pack(&path) {
                                Ok(_) => state.editor = None,
                                Err(error) => state.error = Some(error),
                            }
                        }
                        if ui.button(tr("English (source strings)")).clicked()
                            && let Err(error) = activate_builtin("builtin:en")
                        {
                            state.error = Some(error);
                        }
                        if ui.button(tr("Русский — встроенный")).clicked()
                            && let Err(error) = activate_builtin("builtin:ru")
                        {
                            state.error = Some(error);
                        }
                    });
                    if state.making_new {
                        ui.horizontal(|ui| {
                            ui.label(tr("Name"));
                            ui.text_edit_singleline(&mut state.new_name);
                            ui.label(tr("Language code (e.g. es-ES)"));
                            ui.text_edit_singleline(&mut state.new_locale);
                        });
                        ui.horizontal(|ui| {
                            ui.label(tr("Author (optional)"));
                            ui.text_edit_singleline(&mut state.new_author);
                            if ui.button(tr("Create and edit")).clicked() {
                                match empty_pack(&state.new_name, &state.new_locale, &state.new_author) {
                                    Ok(pack) => {
                                        if let Err(error) = activate_pack(&pack) {
                                            state.error = Some(error);
                                        } else {
                                            state.editor = Some(pack);
                                            state.making_new = false;
                                            state.new_name.clear();
                                            state.new_locale.clear();
                                            state.new_author.clear();
                                        }
                                    }
                                    Err(error) => state.error = Some(error),
                                }
                            }
                        });
                    }
                    ui.separator();
                    ui.label(format!("{}: {}", tr("Active"), active_id()));
                    let packs = installed();
                    egui::ScrollArea::vertical().max_height(330.0).show(ui, |ui| {
                        for pack in &packs {
                            ui.horizontal(|ui| {
                                if ui
                                    .selectable_label(
                                        active_id() == pack.id,
                                        format!("{} · {} · {} strings", pack.name, pack.locale, pack.strings.len()),
                                    )
                                    .clicked()
                                    && let Err(error) = activate_pack(pack)
                                {
                                    state.error = Some(error);
                                }
                            });
                        }
                    });
                    let active = active_id();
                    let selected = packs.iter().find(|pack| pack.id == active).cloned();
                    ui.horizontal(|ui| {
                        if ui.add_enabled(selected.is_some(), egui::Button::new(tr("Edit"))).clicked() {
                            state.editor = selected.clone();
                            state.query.clear();
                        }
                        if ui.add_enabled(selected.is_some(), egui::Button::new(tr("Export"))).clicked()
                            && let Some(pack) = selected.as_ref()
                            && let Err(error) = export_pack(pack, false)
                        {
                            state.error = Some(error);
                        }
                        if ui.add_enabled(selected.is_some(), egui::Button::new(tr("Share"))).clicked()
                            && let Some(pack) = selected.as_ref()
                            && let Err(error) = export_pack(pack, true)
                        {
                            state.error = Some(error);
                        }
                        if ui.add_enabled(selected.is_some(), egui::Button::new(tr("Delete"))).clicked()
                            && let Some(pack) = selected.as_ref()
                            && let Err(error) = std::fs::remove_file(pack_path(&pack.id))
                                .map_err(|error| error.to_string())
                                .and_then(|()| activate_builtin("builtin:ru"))
                        {
                            state.error = Some(error);
                        }
                    });
                }
            });
        state.open = open;
    });
}

#[cfg(test)]
mod tests {
    use super::*;


    #[test]
    fn empty_community_hub_catalogue_is_valid() {
        let raw = br#"{"schema_version":1,"catalog_version":"procraft-ui-source-3","updated_at":"2026-11-25","packs":[]}"#;
        assert!(parse_hub_catalog(raw).is_ok_and(|packs| packs.is_empty()));
    }

    #[test]
    fn community_hub_rejects_downloads_outside_the_official_repository() {
        let raw = r#"{"schema_version":1,"catalog_version":"procraft-ui-source-3","updated_at":"2026-11-25","packs":[{"id":"ja-jp.community","locale":"ja-JP","name":"日本語","author":"Contributor","description":"Japanese community translation.","version":"1.0.0","catalog_version":"procraft-ui-source-3","strings_count":1200,"updated_at":"2026-11-25","download_url":"https://example.com/ja-jp.procraft-lang","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}"#.as_bytes();
        assert!(parse_hub_catalog(raw).is_err());
    }

    #[test]
    fn community_hub_rejects_stale_catalogue_revisions() {
        let raw = br#"{"schema_version":1,"catalog_version":"old-revision","updated_at":"2026-11-25","packs":[]}"#;
        assert!(parse_hub_catalog(raw).is_err());
    }

    #[test]
    fn community_hub_requires_a_sha256_digest() {
        let raw = r#"{"schema_version":1,"catalog_version":"procraft-ui-source-3","updated_at":"2026-11-25","packs":[{"id":"ja-jp.community","locale":"ja-JP","name":"日本語","author":"Contributor","description":"Japanese community translation.","version":"1.0.0","catalog_version":"procraft-ui-source-3","strings_count":1200,"updated_at":"2026-11-25","download_url":"https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/ja-jp.procraft-lang","sha256":"not-a-digest"}]}"#.as_bytes();
        assert!(parse_hub_catalog(raw).is_err());
    }

    #[test]
    fn shared_android_sample_is_a_valid_desktop_language_pack() {
        let pack: LanguagePack = serde_json::from_str(include_str!("../../examples/ru-community.procraft-lang"))
            .expect("sample JSON must parse");
        validate(&pack).expect("sample pack must satisfy v1 validation");
        assert_eq!(pack.locale, "ru-RU");
        assert_eq!(pack.strings.get("Gallery").map(String::as_str), Some("Галерея"));
    }

    #[test]
    fn a_pack_cannot_drop_or_change_named_placeholders() {
        let mut pack = LanguagePack {
            format_version: FORMAT_VERSION,
            id: "es.demo".to_owned(),
            locale: "es-ES".to_owned(),
            name: "Español Demo".to_owned(),
            author: "Test".to_owned(),
            app_version: "0.0.7-beta".to_owned(),
            catalog_version: catalog_version(),
            base_locale: english(),
            strings: BTreeMap::from([("Layer {count}".to_owned(), "Capa".to_owned())]),
        };
        assert!(validate(&pack).is_err());
        pack.strings.insert("Layer {count}".to_owned(), "Capa {count}".to_owned());
        assert!(validate(&pack).is_ok());
    }

    #[test]
    fn ambiguous_or_conflicting_placeholders_are_not_applied() {
        assert_eq!(translate_template("ab", "{first}{second}", "{first}-{second}"), None);
        assert_eq!(translate_template("a b", "{item} {item}", "{item}!"), None);
        assert_eq!(translate_template("a a", "{item} {item}", "{item}!"), Some("a!".to_owned()));
    }

    #[test]
    fn named_placeholders_are_substituted_in_runtime_templates() {
        assert_eq!(translate_template("3 artworks", "{count} artworks", "{count} obras"), Some("3 obras".to_owned()),);
        assert_eq!(
            translate_template("Delete 12 artworks?", "Delete {count} artworks?", "¿Eliminar {count} obras?"),
            Some("¿Eliminar 12 obras?".to_owned()),
        );
        assert_eq!(
            translate_template("4 of 8", "{used} of {maximum}", "{used} de {maximum}"),
            Some("4 de 8".to_owned()),
        );
    }

    #[test]
    fn importing_an_existing_id_never_replaces_different_translation_data() {
        let mut existing = LanguagePack {
            format_version: FORMAT_VERSION,
            id: "es.demo".to_owned(),
            locale: "es-ES".to_owned(),
            name: "Español".to_owned(),
            author: "A".to_owned(),
            app_version: "0.0.7-beta".to_owned(),
            catalog_version: catalog_version(),
            base_locale: english(),
            strings: BTreeMap::from([("Gallery".to_owned(), "Galería".to_owned())]),
        };
        let same = existing.clone();
        assert!(ensure_import_does_not_replace(Some(&existing), &same).is_ok());
        existing.strings.insert("Gallery".to_owned(), "Galerie".to_owned());
        assert!(ensure_import_does_not_replace(Some(&existing), &same).is_err());
        assert!(ensure_import_does_not_replace(None, &same).is_ok());
    }

    #[test]
    fn language_codes_and_pack_ids_follow_the_shared_schema() {
        assert!(valid_id("es.community"));
        assert!(!valid_id("a"));
        assert!(!valid_id("../bad"));
        assert!(valid_locale("es-ES"));
        assert!(valid_locale("zh-Hant"));
        assert!(!valid_locale("e-ES"));
        assert!(!valid_locale("es-INVALIDCODE"));
    }

    #[test]
    fn rtl_locales_are_detected_for_editor_and_app_direction() {
        for locale in ["ar", "ar-SA", "fa-IR", "ur-PK", "he", "yi", "ug-CN", "az-Arab", "nqo", "ckb"] {
            assert!(is_rtl_locale(locale), "{locale} should use RTL layout");
        }
        for locale in ["en", "ru-RU", "ja-JP", "zh-Hant", "ar-Latn"] {
            assert!(!is_rtl_locale(locale), "{locale} should use LTR layout");
        }
    }


    #[test]
    fn hub_update_merge_applies_remote_changes_and_preserves_local_conflicts() {
        let baseline = BTreeMap::from([
            ("Gallery".to_owned(), "Galería".to_owned()),
            ("Layers".to_owned(), "Capas".to_owned()),
            ("Removed remotely".to_owned(), "Antiguo".to_owned()),
        ]);
        let local = BTreeMap::from([
            ("Gallery".to_owned(), "Galería local".to_owned()),
            ("Layers".to_owned(), "Capas".to_owned()),
            ("Local-only".to_owned(), "Mi texto".to_owned()),
            ("Removed remotely".to_owned(), "Antiguo".to_owned()),
        ]);
        let remote = BTreeMap::from([
            ("Gallery".to_owned(), "Galería revisada".to_owned()),
            ("Layers".to_owned(), "Capas nuevas".to_owned()),
            ("Remote-only".to_owned(), "Nueva cadena".to_owned()),
        ]);

        let result = merge_hub_strings(Some(&baseline), &local, &remote);
        assert_eq!(result.strings.get("Gallery").map(String::as_str), Some("Galería local"));
        assert_eq!(result.strings.get("Layers").map(String::as_str), Some("Capas nuevas"));
        assert_eq!(result.strings.get("Local-only").map(String::as_str), Some("Mi texto"));
        assert_eq!(result.strings.get("Remote-only").map(String::as_str), Some("Nueva cadena"));
        assert!(!result.strings.contains_key("Removed remotely"));
        assert_eq!(result.local_changes_preserved, 2);
        assert_eq!(result.conflicts_kept_local, 1);
    }

    #[test]
    fn first_hub_update_preserves_imported_strings_without_a_baseline() {
        let local = BTreeMap::from([
            ("Gallery".to_owned(), "Mi galería".to_owned()),
            ("Local-only".to_owned(), "Texto local".to_owned()),
        ]);
        let remote = BTreeMap::from([
            ("Gallery".to_owned(), "Galería".to_owned()),
            ("Layers".to_owned(), "Capas".to_owned()),
        ]);
        let result = merge_hub_strings(None, &local, &remote);
        assert_eq!(result.strings.get("Gallery").map(String::as_str), Some("Mi galería"));
        assert_eq!(result.strings.get("Local-only").map(String::as_str), Some("Texto local"));
        assert_eq!(result.strings.get("Layers").map(String::as_str), Some("Capas"));
        assert_eq!(result.local_changes_preserved, 2);
        assert_eq!(result.conflicts_kept_local, 1);
    }

    #[test]
    fn hub_version_comparison_is_numeric_and_monotonic() {
        assert!(compare_hub_versions("1.10.0", "1.9.9").is_gt());
        assert_eq!(compare_hub_versions("2.0.0", "2.0.0"), std::cmp::Ordering::Equal);
        assert!(compare_hub_versions("1.0.9", "1.1.0").is_lt());
    }

    #[test]
    fn v1_pack_id_and_size_limits_are_checked() {
        let mut pack = LanguagePack {
            format_version: FORMAT_VERSION,
            id: "../bad".to_owned(),
            locale: "es-ES".to_owned(),
            name: "Español".to_owned(),
            author: String::new(),
            app_version: "0.0.7-beta".to_owned(),
            catalog_version: catalog_version(),
            base_locale: english(),
            strings: BTreeMap::new(),
        };
        assert!(validate(&pack).is_err());
        pack.id = "es.community".to_owned();
        pack.strings.insert("X".to_owned(), "x".repeat(4001));
        assert!(validate(&pack).is_err());

        pack.strings.clear();
        pack.app_version = "v".repeat(41);
        assert!(validate(&pack).is_err());
        pack.app_version = "0.0.7-beta".to_owned();
        pack.catalog_version = "c".repeat(81);
        assert!(validate(&pack).is_err());
        pack.catalog_version = catalog_version();
        pack.base_locale = "b".repeat(36);
        assert!(validate(&pack).is_err());
        pack.base_locale = english();
        pack.locale = format!("{}aa", "aa-".repeat(12));
        assert!(valid_locale(&pack.locale));
        assert!(validate(&pack).is_err());
    }
}
