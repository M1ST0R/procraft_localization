//! User-created language packs for the community localization demo.
//!
//! Format v1 uses exact English UI strings as keys. This is intentionally pragmatic: it can be
//! integrated at the existing text-rendering boundary today, while a future upstream migration
//! can move these entries to stable message IDs without changing import/export workflows.

use std::borrow::Cow;
use std::cell::RefCell;
use std::collections::BTreeMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock, RwLock};

use serde::{Deserialize, Serialize};

const FORMAT_VERSION: u32 = 1;
const MAX_PACK_BYTES: u64 = 5 * 1024 * 1024;
const MAX_STRINGS: usize = 20_000;
const MAX_TEMPLATE_STRINGS: usize = 128;
const MAX_SOURCE_TEXT_BYTES: usize = 4096;

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
    "procraft-ui-source-2".to_owned()
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

fn save_pack(pack: &LanguagePack) -> Result<(), String> {
    validate(pack)?;
    std::fs::create_dir_all(packs_dir()).map_err(|e| e.to_string())?;
    let content = serde_json::to_string_pretty(pack).map_err(|e| e.to_string())?;
    std::fs::write(pack_path(&pack.id), content).map_err(|e| e.to_string())
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
        if source.is_empty() || source.len() > 160 || translated.len() > 4000 {
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
}

thread_local! {
    static MANAGER: RefCell<ManagerState> = RefCell::new(ManagerState::default());
}

/// Open the manager over the canvas/settings. The editor is deliberately data-only and can never
/// execute plugins, scripts or arbitrary code from an imported pack.
pub fn open() {
    MANAGER.with(|state| state.borrow_mut().open = true);
}

pub fn show(ctx: &egui::Context) {
    MANAGER.with(|state| {
        let mut state = state.borrow_mut();
        if !state.open {
            return;
        }
        let mut open = state.open;
        egui::Window::new(tr("Community language packs"))
            .id(egui::Id::new("community_language_packs_manager"))
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
                    ui.horizontal(|ui| {
                        if ui.button(tr("← Packs")).clicked() {
                            close_editor = true;
                        }
                        ui.heading(format!("{} · {}", pack.name, pack.locale));
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
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
                            ui.horizontal(|ui| {
                                ui.add_sized([310.0, 28.0], egui::Label::new(source));
                                let mut value = pack.strings.get(source).cloned().unwrap_or_default();
                                if ui
                                    .add_sized([ui.available_width(), 28.0], egui::TextEdit::singleline(&mut value))
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
                } else {
                    ui.label(tr("Create, edit, import and export translations. Missing strings fall back to English."));
                    ui.horizontal_wrapped(|ui| {
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
