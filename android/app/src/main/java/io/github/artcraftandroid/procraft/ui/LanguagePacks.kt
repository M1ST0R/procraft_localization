package io.github.artcraftandroid.procraft.ui

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal data class TranslationPack(
    val id: String,
    val locale: String,
    val name: String,
    val author: String,
    val appVersion: String,
    val strings: LinkedHashMap<String, String>,
)

/**
 * Small, data-only .procraft-lang runtime for the community demo.
 *
 * Keys are exact English UI strings. This deliberately keeps the first demo independent from a
 * future stable-message-ID migration: the same pack can already be imported by Android and Desktop.
 */
internal object LanguagePacks {
    private const val FORMAT_VERSION = 1
    private const val PREFS = "community-language-packs"
    private const val ACTIVE = "active_id"
    private val revisionState = mutableIntStateOf(0)
    private var activeId: String = "builtin:ru"
    private var activeStrings: Map<String, String>? = null
    private var activeTemplates: List<Pair<String, String>> = emptyList()
    private val packPlaceholder = Regex("""\\{([A-Za-z][A-Za-z0-9_]*)\\}""")

    /** Read by the Compose text wrapper so selecting a pack invalidates displayed labels. */
    val revision: Int get() = revisionState.intValue

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        activeId = prefs.getString(ACTIVE, "builtin:ru") ?: "builtin:ru"
        activeStrings = if (activeId.startsWith("builtin:")) null else
            runCatching { readPack(File(folder(context), "$activeId.procraft-lang")).strings }.getOrNull()
        if (!activeId.startsWith("builtin:") && activeStrings == null) {
            activeId = "builtin:ru"
            prefs.edit().putString(ACTIVE, activeId).apply()
        }
        refreshTemplates()
        revisionState.intValue++
    }

    /** null means use the compiled Russian dictionary and its dynamic rules. */
    fun override(source: String): String? {
        revisionState.intValue
        return when {
            activeId == "builtin:en" -> source
            activeId == "builtin:ru" -> null
            else -> {
                val strings = activeStrings
                strings?.get(source)?.takeIf { it.isNotBlank() }
                    ?: activeTemplates.firstNotNullOfOrNull { (pattern, translated) ->
                        renderTemplate(source, pattern, translated)
                    }
                    ?: source
            }
        }
    }

    private fun refreshTemplates() {
        activeTemplates = activeStrings?.entries
            ?.filter { packPlaceholder.containsMatchIn(it.key) }
            ?.map { it.key to it.value }
            .orEmpty()
    }

    private fun renderTemplate(source: String, pattern: String, translated: String): String? {
        val placeholders = packPlaceholder.findAll(pattern).toList()
        if (placeholders.isEmpty()) return null
        val expression = buildString {
            append('^')
            var cursor = 0
            placeholders.forEach { placeholder ->
                append(Regex.escape(pattern.substring(cursor, placeholder.range.first)))
                append("(.+?)")
                cursor = placeholder.range.last + 1
            }
            append(Regex.escape(pattern.substring(cursor)))
            append('
    fun currentId(): String = activeId

    fun activate(context: Context, id: String) {
        require(id == "builtin:ru" || id == "builtin:en" || id.matches(Regex("[a-z0-9][a-z0-9._-]{2,95}")))
        if (!id.startsWith("builtin:")) readPack(File(folder(context), "$id.procraft-lang"))
        activeId = id
        activeStrings = if (id.startsWith("builtin:")) null else readPack(File(folder(context), "$id.procraft-lang")).strings
        refreshTemplates()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE, id).apply()
        revisionState.intValue++
    }

    fun installed(context: Context): List<TranslationPack> = folder(context).listFiles()
        .orEmpty().filter { it.isFile && it.extension == "procraft-lang" }
        .mapNotNull { runCatching { readPack(it) }.getOrNull() }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun create(name: String, locale: String, author: String): TranslationPack {
        val cleanName = name.trim()
        val cleanLocale = locale.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) { "Name must contain 1–80 characters." }
        require(Regex("[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*").matches(cleanLocale)) { "Enter a locale such as es-ES or de-DE." }
        val stem = cleanLocale.lowercase(Locale.ROOT).replace('_', '-')
        val id = "$stem.${System.currentTimeMillis().toString().takeLast(8)}"
        return TranslationPack(id, cleanLocale, cleanName, author.trim().take(120), "0.0.7-beta", linkedMapOf())
    }

    fun save(context: Context, pack: TranslationPack): TranslationPack {
        validate(pack)
        val directory = folder(context)
        directory.mkdirs()
        File(directory, "${pack.id}.procraft-lang").writeText(encode(pack), Charsets.UTF_8)
        revisionState.intValue++
        return pack
    }

    fun import(context: Context, uri: Uri): TranslationPack {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Could not read the selected file.")
        require(raw.length <= 5 * 1024 * 1024) { "Language pack is larger than 5 MB." }
        val pack = decode(raw)
        return save(context, pack)
    }

    fun export(context: Context, uri: Uri, pack: TranslationPack) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(encode(pack)) }
            ?: error("Could not write the selected file.")
    }

    fun share(context: Context, pack: TranslationPack) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "${pack.id}.procraft-lang")
        file.writeText(encode(pack), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share language pack"))
    }

    fun delete(context: Context, pack: TranslationPack) {
        File(folder(context), "${pack.id}.procraft-lang").delete()
        if (activeId == pack.id) activate(context, "builtin:ru")
        revisionState.intValue++
    }

    fun catalog(context: Context): List<String> = runCatching {
        val raw = context.assets.open("localization/ui-catalog.json").bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrElse { languageCatalog() }

    fun encode(pack: TranslationPack): String {
        val root = JSONObject()
            .put("format_version", FORMAT_VERSION)
            .put("id", pack.id)
            .put("locale", pack.locale)
            .put("name", pack.name)
            .put("author", pack.author)
            .put("app_version", pack.appVersion)
            .put("catalog_version", "procraft-ui-source-1")
            .put("base_locale", "en")
        val strings = JSONObject()
        pack.strings.toSortedMap().forEach { (key, value) -> strings.put(key, value) }
        root.put("strings", strings)
        return root.toString(2) + "\n"
    }

    fun decode(raw: String): TranslationPack {
        val root = JSONObject(raw)
        require(root.optInt("format_version", -1) == FORMAT_VERSION) { "Unsupported language-pack format version." }
        val stringsObject = root.optJSONObject("strings") ?: error("The pack has no strings dictionary.")
        require(stringsObject.length() <= 20000) { "The pack contains too many strings." }
        val strings = linkedMapOf<String, String>()
        val keys = stringsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = stringsObject.optString(key, "")
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) { "A string exceeds the format limits." }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
            if (value.isNotBlank()) strings[key] = value
        }
        return TranslationPack(
            id = root.optString("id"),
            locale = root.optString("locale"),
            name = root.optString("name"),
            author = root.optString("author"),
            appVersion = root.optString("app_version", "unknown"),
            strings = strings,
        ).also(::validate)
    }

    private fun validate(pack: TranslationPack) {
        require(pack.id.matches(Regex("[a-z0-9][a-z0-9._-]{2,95}"))) { "Invalid pack ID." }
        require(pack.locale.length in 2..35 && pack.name.isNotBlank() && pack.name.length <= 80) { "Invalid pack metadata." }
        require(pack.author.length <= 120 && pack.strings.size <= 20000) { "Pack metadata or string count is too large." }
        pack.strings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) { "A string exceeds the format limits." }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
        }
    }

    private fun placeholders(value: String): List<String> =
        Regex("\\{[^{}]+}").findAll(value).map { it.value }.sorted().toList()

    private fun folder(context: Context) = File(context.filesDir, "language-packs")

    private fun readPack(file: File): TranslationPack = decode(file.readText(Charsets.UTF_8))
}

@Composable
internal fun LanguagePacksPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var packs by remember { mutableStateOf(LanguagePacks.installed(context)) }
    var selected by remember { mutableStateOf(LanguagePacks.currentId()) }
    var editing by remember { mutableStateOf<TranslationPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLocale by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching { LanguagePacks.import(context, uri) }
            .onSuccess { packs = LanguagePacks.installed(context); selected = it.id; LanguagePacks.activate(context, it.id) }
            .onFailure { error = it.message ?: "Import failed." }
    }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val pack = packs.firstOrNull { it.id == selected }
            if (pack != null) runCatching { LanguagePacks.export(context, uri, pack) }
                .onFailure { error = it.message ?: "Export failed." }
        }
    }

    if (editing != null) {
        PackEditorPage(editing!!, onBack = { editing = null }, onSave = { changed ->
            runCatching { LanguagePacks.save(context, changed) }
                .onSuccess {
                    packs = LanguagePacks.installed(context)
                    selected = it.id
                    LanguagePacks.activate(context, it.id)
                    editing = null
                }
                .onFailure { error = it.message ?: "Could not save the pack." }
        })
        error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("Language pack") }, text = { Text(it) }, confirmButton = { Button(onClick = { error = null }) { Text("OK") } }) }
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF000000)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Settings", color = Blue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Text("Language packs", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text("Create, edit, import and export translations. Missing strings fall back to English.", color = Dim, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { create = true }) { Text("New") }
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("Import") }
        }
        Text("BUILT-IN", color = Dim, fontSize = 12.sp)
        PackChoice("English (source strings)", selected == "builtin:en") {
            runCatching { LanguagePacks.activate(context, "builtin:en") }.onSuccess { selected = "builtin:en" }
        }
        PackChoice("Русский — встроенный", selected == "builtin:ru") {
            runCatching { LanguagePacks.activate(context, "builtin:ru") }.onSuccess { selected = "builtin:ru" }
        }
        Text("INSTALLED PACKS", color = Dim, fontSize = 12.sp)
        if (packs.isEmpty()) {
            Text("No custom packs yet. Create one or import a .procraft-lang file.", color = Dim, fontSize = 14.sp)
        }
        LazyColumn(Modifier.weight(1f, fill = true), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(packs, key = { it.id }) { pack ->
                PackChoice("${pack.name} · ${pack.locale} · ${pack.strings.size} strings", selected == pack.id) {
                    runCatching { LanguagePacks.activate(context, pack.id) }
                        .onSuccess { selected = pack.id }
                        .onFailure { error = it.message ?: "Could not activate pack." }
                }
            }
        }
        val selectedPack = packs.firstOrNull { it.id == selected }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { selectedPack?.let { editing = it.copy(strings = LinkedHashMap(it.strings)) } }, enabled = selectedPack != null) {
                Text("Edit")
            }
            Button(onClick = { selectedPack?.let { exportPicker.launch("${it.id}.procraft-lang") } }, enabled = selectedPack != null) {
                Text("Export")
            }
            Button(onClick = { selectedPack?.let { runCatching { LanguagePacks.share(context, it) }.onFailure { error = it.message ?: "Could not share pack." } } }, enabled = selectedPack != null) {
                Text("Share")
            }
            Button(onClick = {
                selectedPack?.let { LanguagePacks.delete(context, it); packs = LanguagePacks.installed(context); selected = LanguagePacks.currentId() }
            }, enabled = selectedPack != null) { Text("Delete") }
        }
        error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("Language pack") }, text = { Text(it) }, confirmButton = { Button(onClick = { error = null }) { Text("OK") } }) }
    }

    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("Create translation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(newLocale, { newLocale = it }, label = { Text("Language code (e.g. es-ES)") }, singleLine = true)
                    OutlinedTextField(newAuthor, { newAuthor = it }, label = { Text("Author (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { LanguagePacks.create(newName, newLocale, newAuthor) }
                        .onSuccess { val saved = LanguagePacks.save(context, it); packs = LanguagePacks.installed(context); selected = saved.id; LanguagePacks.activate(context, saved.id); editing = saved; create = false; newName = ""; newLocale = ""; newAuthor = "" }
                        .onFailure { error = it.message ?: "Could not create the pack." }
                }) { Text("Create") }
            },
            dismissButton = { Button(onClick = { create = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PackChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (active) Color(0xFF222C3D) else Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (active) "●" else "○", color = if (active) Blue else Dim, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun PackEditorPage(pack: TranslationPack, onBack: () -> Unit, onSave: (TranslationPack) -> Unit) {
    val context = LocalContext.current
    var changed by remember(pack.id) { mutableStateOf(LinkedHashMap(pack.strings)) }
    var search by remember { mutableStateOf("") }
    val catalog = remember { LanguagePacks.catalog(context) }
    val visible = remember(search, catalog, changed) {
        if (search.isBlank()) catalog else catalog.filter { it.contains(search, ignoreCase = true) || changed[it]?.contains(search, ignoreCase = true) == true }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Packs", color = Blue, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(pack.name, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("${pack.locale} · ${changed.size} translated strings", color = Dim, fontSize = 12.sp)
            }
            Button(onClick = { onSave(pack.copy(strings = changed)) }) { Text("Save") }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search source strings") }, singleLine = true,
        )
        Text("Translate the English source below. Leave a field empty to use English fallback.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it }) { source ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text(source, color = Color(0xFFD0D0D0), fontSize = 13.sp)
                    OutlinedTextField(
                        value = changed[source] ?: "",
                        onValueChange = { value ->
                            changed = LinkedHashMap(changed).apply {
                                if (value.isBlank()) remove(source) else put(source, value)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Translation") },
                        minLines = 1,
                    )
                }
            }
        }
    }
}
)
        }
        val match = Regex(expression).matchEntire(source) ?: return null
        var result = translated
        placeholders.forEachIndexed { index, placeholder ->
            result = result.replace(placeholder.value, match.groupValues[index + 1])
        }
        return result
    }

    fun currentId(): String = activeId

    fun activate(context: Context, id: String) {
        require(id == "builtin:ru" || id == "builtin:en" || id.matches(Regex("[a-z0-9][a-z0-9._-]{2,95}")))
        if (!id.startsWith("builtin:")) readPack(File(folder(context), "$id.procraft-lang"))
        activeId = id
        activeStrings = if (id.startsWith("builtin:")) null else readPack(File(folder(context), "$id.procraft-lang")).strings
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE, id).apply()
        revisionState.intValue++
    }

    fun installed(context: Context): List<TranslationPack> = folder(context).listFiles()
        .orEmpty().filter { it.isFile && it.extension == "procraft-lang" }
        .mapNotNull { runCatching { readPack(it) }.getOrNull() }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun create(name: String, locale: String, author: String): TranslationPack {
        val cleanName = name.trim()
        val cleanLocale = locale.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) { "Name must contain 1–80 characters." }
        require(Regex("[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*").matches(cleanLocale)) { "Enter a locale such as es-ES or de-DE." }
        val stem = cleanLocale.lowercase(Locale.ROOT).replace('_', '-')
        val id = "$stem.${System.currentTimeMillis().toString().takeLast(8)}"
        return TranslationPack(id, cleanLocale, cleanName, author.trim().take(120), "0.0.7-beta", linkedMapOf())
    }

    fun save(context: Context, pack: TranslationPack): TranslationPack {
        validate(pack)
        val directory = folder(context)
        directory.mkdirs()
        File(directory, "${pack.id}.procraft-lang").writeText(encode(pack), Charsets.UTF_8)
        revisionState.intValue++
        return pack
    }

    fun import(context: Context, uri: Uri): TranslationPack {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Could not read the selected file.")
        require(raw.length <= 5 * 1024 * 1024) { "Language pack is larger than 5 MB." }
        val pack = decode(raw)
        return save(context, pack)
    }

    fun export(context: Context, uri: Uri, pack: TranslationPack) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(encode(pack)) }
            ?: error("Could not write the selected file.")
    }

    fun share(context: Context, pack: TranslationPack) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "${pack.id}.procraft-lang")
        file.writeText(encode(pack), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share language pack"))
    }

    fun delete(context: Context, pack: TranslationPack) {
        File(folder(context), "${pack.id}.procraft-lang").delete()
        if (activeId == pack.id) activate(context, "builtin:ru")
        revisionState.intValue++
    }

    fun catalog(context: Context): List<String> = runCatching {
        val raw = context.assets.open("localization/ui-catalog.json").bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrElse { languageCatalog() }

    fun encode(pack: TranslationPack): String {
        val root = JSONObject()
            .put("format_version", FORMAT_VERSION)
            .put("id", pack.id)
            .put("locale", pack.locale)
            .put("name", pack.name)
            .put("author", pack.author)
            .put("app_version", pack.appVersion)
            .put("catalog_version", "procraft-ui-source-1")
            .put("base_locale", "en")
        val strings = JSONObject()
        pack.strings.toSortedMap().forEach { (key, value) -> strings.put(key, value) }
        root.put("strings", strings)
        return root.toString(2) + "\n"
    }

    fun decode(raw: String): TranslationPack {
        val root = JSONObject(raw)
        require(root.optInt("format_version", -1) == FORMAT_VERSION) { "Unsupported language-pack format version." }
        val stringsObject = root.optJSONObject("strings") ?: error("The pack has no strings dictionary.")
        require(stringsObject.length() <= 20000) { "The pack contains too many strings." }
        val strings = linkedMapOf<String, String>()
        val keys = stringsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = stringsObject.optString(key, "")
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) { "A string exceeds the format limits." }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
            if (value.isNotBlank()) strings[key] = value
        }
        return TranslationPack(
            id = root.optString("id"),
            locale = root.optString("locale"),
            name = root.optString("name"),
            author = root.optString("author"),
            appVersion = root.optString("app_version", "unknown"),
            strings = strings,
        ).also(::validate)
    }

    private fun validate(pack: TranslationPack) {
        require(pack.id.matches(Regex("[a-z0-9][a-z0-9._-]{2,95}"))) { "Invalid pack ID." }
        require(pack.locale.length in 2..35 && pack.name.isNotBlank() && pack.name.length <= 80) { "Invalid pack metadata." }
        require(pack.author.length <= 120 && pack.strings.size <= 20000) { "Pack metadata or string count is too large." }
        pack.strings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) { "A string exceeds the format limits." }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
        }
    }

    private fun placeholders(value: String): List<String> =
        Regex("\\{[^{}]+}").findAll(value).map { it.value }.sorted().toList()

    private fun folder(context: Context) = File(context.filesDir, "language-packs")

    private fun readPack(file: File): TranslationPack = decode(file.readText(Charsets.UTF_8))
}

@Composable
internal fun LanguagePacksPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var packs by remember { mutableStateOf(LanguagePacks.installed(context)) }
    var selected by remember { mutableStateOf(LanguagePacks.currentId()) }
    var editing by remember { mutableStateOf<TranslationPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLocale by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching { LanguagePacks.import(context, uri) }
            .onSuccess { packs = LanguagePacks.installed(context); selected = it.id; LanguagePacks.activate(context, it.id) }
            .onFailure { error = it.message ?: "Import failed." }
    }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val pack = packs.firstOrNull { it.id == selected }
            if (pack != null) runCatching { LanguagePacks.export(context, uri, pack) }
                .onFailure { error = it.message ?: "Export failed." }
        }
    }

    if (editing != null) {
        PackEditorPage(editing!!, onBack = { editing = null }, onSave = { changed ->
            runCatching { LanguagePacks.save(context, changed) }
                .onSuccess {
                    packs = LanguagePacks.installed(context)
                    selected = it.id
                    LanguagePacks.activate(context, it.id)
                    editing = null
                }
                .onFailure { error = it.message ?: "Could not save the pack." }
        })
        error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("Language pack") }, text = { Text(it) }, confirmButton = { Button(onClick = { error = null }) { Text("OK") } }) }
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF000000)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Settings", color = Blue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Text("Language packs", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text("Create, edit, import and export translations. Missing strings fall back to English.", color = Dim, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { create = true }) { Text("New") }
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("Import") }
        }
        Text("BUILT-IN", color = Dim, fontSize = 12.sp)
        PackChoice("English (source strings)", selected == "builtin:en") {
            runCatching { LanguagePacks.activate(context, "builtin:en") }.onSuccess { selected = "builtin:en" }
        }
        PackChoice("Русский — встроенный", selected == "builtin:ru") {
            runCatching { LanguagePacks.activate(context, "builtin:ru") }.onSuccess { selected = "builtin:ru" }
        }
        Text("INSTALLED PACKS", color = Dim, fontSize = 12.sp)
        if (packs.isEmpty()) {
            Text("No custom packs yet. Create one or import a .procraft-lang file.", color = Dim, fontSize = 14.sp)
        }
        LazyColumn(Modifier.weight(1f, fill = true), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(packs, key = { it.id }) { pack ->
                PackChoice("${pack.name} · ${pack.locale} · ${pack.strings.size} strings", selected == pack.id) {
                    runCatching { LanguagePacks.activate(context, pack.id) }
                        .onSuccess { selected = pack.id }
                        .onFailure { error = it.message ?: "Could not activate pack." }
                }
            }
        }
        val selectedPack = packs.firstOrNull { it.id == selected }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { selectedPack?.let { editing = it.copy(strings = LinkedHashMap(it.strings)) } }, enabled = selectedPack != null) {
                Text("Edit")
            }
            Button(onClick = { selectedPack?.let { exportPicker.launch("${it.id}.procraft-lang") } }, enabled = selectedPack != null) {
                Text("Export")
            }
            Button(onClick = { selectedPack?.let { runCatching { LanguagePacks.share(context, it) }.onFailure { error = it.message ?: "Could not share pack." } } }, enabled = selectedPack != null) {
                Text("Share")
            }
            Button(onClick = {
                selectedPack?.let { LanguagePacks.delete(context, it); packs = LanguagePacks.installed(context); selected = LanguagePacks.currentId() }
            }, enabled = selectedPack != null) { Text("Delete") }
        }
        error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("Language pack") }, text = { Text(it) }, confirmButton = { Button(onClick = { error = null }) { Text("OK") } }) }
    }

    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("Create translation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(newLocale, { newLocale = it }, label = { Text("Language code (e.g. es-ES)") }, singleLine = true)
                    OutlinedTextField(newAuthor, { newAuthor = it }, label = { Text("Author (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { LanguagePacks.create(newName, newLocale, newAuthor) }
                        .onSuccess { val saved = LanguagePacks.save(context, it); packs = LanguagePacks.installed(context); selected = saved.id; LanguagePacks.activate(context, saved.id); editing = saved; create = false; newName = ""; newLocale = ""; newAuthor = "" }
                        .onFailure { error = it.message ?: "Could not create the pack." }
                }) { Text("Create") }
            },
            dismissButton = { Button(onClick = { create = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PackChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (active) Color(0xFF222C3D) else Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (active) "●" else "○", color = if (active) Blue else Dim, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun PackEditorPage(pack: TranslationPack, onBack: () -> Unit, onSave: (TranslationPack) -> Unit) {
    val context = LocalContext.current
    var changed by remember(pack.id) { mutableStateOf(LinkedHashMap(pack.strings)) }
    var search by remember { mutableStateOf("") }
    val catalog = remember { LanguagePacks.catalog(context) }
    val visible = remember(search, catalog, changed) {
        if (search.isBlank()) catalog else catalog.filter { it.contains(search, ignoreCase = true) || changed[it]?.contains(search, ignoreCase = true) == true }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Packs", color = Blue, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(pack.name, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("${pack.locale} · ${changed.size} translated strings", color = Dim, fontSize = 12.sp)
            }
            Button(onClick = { onSave(pack.copy(strings = changed)) }) { Text("Save") }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search source strings") }, singleLine = true,
        )
        Text("Translate the English source below. Leave a field empty to use English fallback.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it }) { source ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text(source, color = Color(0xFFD0D0D0), fontSize = 13.sp)
                    OutlinedTextField(
                        value = changed[source] ?: "",
                        onValueChange = { value ->
                            changed = LinkedHashMap(changed).apply {
                                if (value.isBlank()) remove(source) else put(source, value)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Translation") },
                        minLines = 1,
                    )
                }
            }
        }
    }
}
