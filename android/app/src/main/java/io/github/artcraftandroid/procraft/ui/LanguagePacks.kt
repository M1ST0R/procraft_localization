package io.github.artcraftandroid.procraft.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

private const val MAX_PACK_BYTES = 5 * 1024 * 1024
private const val MAX_PACK_STRINGS = 20_000
private const val MAX_PACK_JSON_DEPTH = 32
private const val MAX_TEMPLATE_STRINGS = 128
private const val PACK_CATALOG_VERSION = "procraft-ui-source-1"
private const val PACK_APP_VERSION = "0.0.7-beta"

private val PackInk = Color(0xFFFFFFFF)
private val PackDim = Color(0xFF8E8E93)
private val PackBlue = Color(0xFF0A84FF)
private val PackIdPattern = Regex("[a-z0-9][a-z0-9._-]{2,95}")
private val PackLocalePattern = Regex("[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*")
private val PackPlaceholderPattern = Regex("""\{([A-Za-z][A-Za-z0-9_]*)\}""")

internal data class TranslationPack(
    val id: String,
    val locale: String,
    val name: String,
    val author: String,
    val appVersion: String,
    val catalogVersion: String,
    val baseLocale: String,
    val strings: LinkedHashMap<String, String>,
)

/**
 * Data-only .procraft-lang reader, editor storage and runtime.
 *
 * Version 1 uses exact English UI text as keys. Imported files never execute code and are bounded
 * by size/count/field limits before they can be installed.
 */
internal object LanguagePacks {
    private const val FORMAT_VERSION = 1
    private const val PREFS = "community-language-packs"
    private const val ACTIVE = "active_id"

    private val revisionState = mutableIntStateOf(0)
    private var activeId: String = "builtin:ru"
    private var activeStrings: Map<String, String>? = null
    private var activeTemplates: List<CompiledPackTemplate> = emptyList()

    /** Read by the Compose Text wrapper to recompose labels when the active pack changes. */
    val revision: Int get() = revisionState.intValue

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedId = prefs.getString(ACTIVE, "builtin:ru") ?: "builtin:ru"
        activeId = when {
            storedId == "builtin:en" || storedId == "builtin:ru" -> storedId
            PackIdPattern.matches(storedId) -> storedId
            else -> "builtin:ru"
        }
        activeStrings = if (activeId.startsWith("builtin:")) {
            null
        } else {
            runCatching { readPack(packFile(context, activeId)).strings }.getOrNull()
        }
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
        return when (activeId) {
            "builtin:en" -> source
            "builtin:ru" -> null
            else -> {
                val strings = activeStrings
                strings?.get(source)?.takeIf(String::isNotBlank)
                    ?: activeTemplates.firstNotNullOfOrNull { template ->
                        renderTemplate(source, template)
                    }
                    ?: source
            }
        }
    }

    fun currentId(): String = activeId

    fun activate(context: Context, id: String) {
        require(id == "builtin:ru" || id == "builtin:en" || PackIdPattern.matches(id)) {
            "Invalid language-pack ID."
        }
        val nextStrings = if (id.startsWith("builtin:")) {
            null
        } else {
            readPack(packFile(context, id)).strings
        }
        activeId = id
        activeStrings = nextStrings
        refreshTemplates()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ACTIVE, id).apply()
        revisionState.intValue++
    }

    fun installed(context: Context): List<TranslationPack> {
        val directory = packsDirectory(context)
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "procraft-lang" }
            .mapNotNull { file -> runCatching { readPack(file) }.getOrNull() }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun create(name: String, locale: String, author: String): TranslationPack {
        val cleanName = name.trim()
        val cleanLocale = locale.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) {
            "Name must contain 1–80 characters."
        }
        require(PackLocalePattern.matches(cleanLocale) && cleanLocale.length <= 35) {
            "Enter a locale such as es-ES or de-DE."
        }
        val normalizedLocale = cleanLocale.replace('_', '-')
        val id = "${normalizedLocale.lowercase(Locale.ROOT)}.${UUID.randomUUID().toString().take(8)}"
        return TranslationPack(
            id = id,
            locale = normalizedLocale,
            name = cleanName,
            author = author.trim().take(120),
            appVersion = PACK_APP_VERSION,
            catalogVersion = PACK_CATALOG_VERSION,
            baseLocale = "en",
            strings = linkedMapOf(),
        )
    }

    fun save(context: Context, pack: TranslationPack): TranslationPack {
        validate(pack)
        val directory = packsDirectory(context)
        require(directory.exists() || directory.mkdirs()) { "Could not create the language-pack folder." }
        val atomic = AtomicFile(packFile(context, pack.id))
        var stream: java.io.FileOutputStream? = null
        try {
            val output = atomic.startWrite()
            stream = output
            output.write(encode(pack).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            stream?.let(atomic::failWrite)
            throw error
        }
        revisionState.intValue++
        return pack
    }

    fun import(context: Context, uri: Uri): TranslationPack {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the selected file.")
        val bytes = input.use { readBounded(it, MAX_PACK_BYTES) }
        val pack = decode(bytes.toString(Charsets.UTF_8))
        return save(context, pack)
    }

    fun export(context: Context, uri: Uri, pack: TranslationPack) {
        validate(pack)
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Could not write the selected file.")
        output.use { it.write(encode(pack).toByteArray(Charsets.UTF_8)) }
    }

    fun share(context: Context, pack: TranslationPack) {
        validate(pack)
        val directory = File(context.cacheDir, "exports")
        require(directory.exists() || directory.mkdirs()) { "Could not create the export folder." }
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
        require(PackIdPattern.matches(pack.id)) { "Invalid language-pack ID." }
        AtomicFile(packFile(context, pack.id)).delete()
        if (activeId == pack.id) activate(context, "builtin:ru")
        revisionState.intValue++
    }

    fun catalog(context: Context): List<String> = runCatching {
        val raw = context.assets.open("localization/ui-catalog.json").bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf(String::isNotBlank)
        }
    }.getOrElse { languageCatalog() }

    fun encode(pack: TranslationPack): String {
        validate(pack)
        val strings = JSONObject()
        pack.strings.toSortedMap().forEach { (key, value) -> strings.put(key, value) }
        return JSONObject()
            .put("format_version", FORMAT_VERSION)
            .put("id", pack.id)
            .put("locale", pack.locale)
            .put("name", pack.name)
            .put("author", pack.author)
            .put("app_version", pack.appVersion)
            .put("catalog_version", pack.catalogVersion)
            .put("base_locale", pack.baseLocale)
            .put("strings", strings)
            .toString(2) + "\n"
    }

    fun decode(raw: String): TranslationPack {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_PACK_BYTES) {
            "Language pack is larger than 5 MB."
        }
        validateJsonDepth(raw)
        val root = JSONObject(raw)
        val allowedFields = setOf(
            "format_version", "id", "locale", "name", "author",
            "app_version", "catalog_version", "base_locale", "strings",
        )
        val actualFields = buildSet {
            val keys = root.keys()
            while (keys.hasNext()) add(keys.next())
        }
        require(actualFields.all { it in allowedFields }) { "The pack contains unsupported fields." }
        val formatVersion = root.get("format_version") as? Number
            ?: error("The pack format version must be an integer.")
        require(formatVersion.toDouble() == FORMAT_VERSION.toDouble()) {
            "Unsupported language-pack format version."
        }

        val stringsObject = root.optJSONObject("strings") ?: error("The pack has no strings dictionary.")
        require(stringsObject.length() <= MAX_PACK_STRINGS) { "The pack contains too many strings." }

        val strings = linkedMapOf<String, String>()
        val keys = stringsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = stringsObject.get(key) as? String
                ?: error("Translation values must be strings.")
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) {
                "A string exceeds the format limits."
            }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
            if (value.isNotBlank()) strings[key] = value
        }

        return TranslationPack(
            id = requiredString(root, "id"),
            locale = requiredString(root, "locale"),
            name = requiredString(root, "name"),
            author = optionalString(root, "author", ""),
            appVersion = optionalString(root, "app_version", "unknown"),
            catalogVersion = optionalString(root, "catalog_version", PACK_CATALOG_VERSION),
            baseLocale = optionalString(root, "base_locale", "en"),
            strings = strings,
        ).also(::validate)
    }

    private fun validate(pack: TranslationPack) {
        require(PackIdPattern.matches(pack.id)) { "Invalid pack ID." }
        require(PackLocalePattern.matches(pack.locale) && pack.locale.length <= 35) {
            "Invalid language locale."
        }
        require(pack.name.isNotBlank() && pack.name.length <= 80) { "Invalid language-pack name." }
        require(
            pack.author.length <= 120 &&
                pack.appVersion.length <= 40 &&
                pack.catalogVersion.length <= 80 &&
                pack.baseLocale.length <= 35 &&
                pack.strings.size <= MAX_PACK_STRINGS &&
                pack.strings.keys.count { PackPlaceholderPattern.containsMatchIn(it) } <= MAX_TEMPLATE_STRINGS
        ) { "Pack metadata or string count is too large." }
        pack.strings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 160 && value.length <= 4000) {
                "A string exceeds the format limits."
            }
            require(placeholders(key) == placeholders(value)) { "Placeholder mismatch in: $key" }
        }
    }

    private fun placeholders(value: String): List<String> =
        Regex("\\{[^{}]+}").findAll(value).map { it.value }.sorted().toList()

    private fun refreshTemplates() {
        activeTemplates = activeStrings?.entries
            ?.filter { PackPlaceholderPattern.containsMatchIn(it.key) && it.value.isNotBlank() }
            ?.mapNotNull { (pattern, translated) -> compileTemplate(pattern, translated) }
            ?.sortedByDescending { it.regex.pattern.length }
            .orEmpty()
    }

    private fun compileTemplate(pattern: String, translated: String): CompiledPackTemplate? {
        val placeholders = PackPlaceholderPattern.findAll(pattern).map { it.value }.toList()
        if (placeholders.isEmpty()) return null
        val ranges = PackPlaceholderPattern.findAll(pattern).toList()
        val expression = buildString {
            append('^')
            var cursor = 0
            ranges.forEach { placeholder ->
                append(Regex.escape(pattern.substring(cursor, placeholder.range.first)))
                append("(.+?)")
                cursor = placeholder.range.last + 1
            }
            append(Regex.escape(pattern.substring(cursor)))
            append('
    private fun readPack(file: File): TranslationPack {
        val bytes = AtomicFile(file).openRead().use { readBounded(it, MAX_PACK_BYTES) }
        return decode(bytes.toString(Charsets.UTF_8))
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Language pack is larger than 5 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun packsDirectory(context: Context) = File(context.filesDir, "language-packs")

    private fun packFile(context: Context, id: String): File {
        require(PackIdPattern.matches(id)) { "Invalid language-pack ID." }
        return File(packsDirectory(context), "$id.procraft-lang")
    }
}

@Composable
internal fun LanguagePacksPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var packs by remember { mutableStateOf(LanguagePacks.installed(context)) }
    var selected by remember { mutableStateOf(LanguagePacks.currentId()) }
    var editing by remember { mutableStateOf<TranslationPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLocale by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val pack = LanguagePacks.import(context, uri)
                LanguagePacks.activate(context, pack.id)
                packs = LanguagePacks.installed(context)
                selected = pack.id
            }.onFailure { error = it.message ?: "Import failed." }
        }
    }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            packs.firstOrNull { it.id == selected }?.let { pack ->
                runCatching { LanguagePacks.export(context, uri, pack) }
                    .onFailure { error = it.message ?: "Export failed." }
            }
        }
    }

    if (editing != null) {
        PackEditorPage(
            pack = requireNotNull(editing),
            onBack = { editing = null },
            onSave = { changed ->
                runCatching {
                    LanguagePacks.save(context, changed)
                    LanguagePacks.activate(context, changed.id)
                    packs = LanguagePacks.installed(context)
                    selected = changed.id
                    editing = null
                }.onFailure { error = it.message ?: "Could not save the pack." }
            },
        )
        error?.let {
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Language pack") },
                text = { Text(it) },
                confirmButton = { Button(onClick = { error = null }) { Text("OK") } },
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Settings",
                color = PackBlue,
                fontSize = 17.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text(
                "Language packs",
                color = PackInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Create, edit, import and export translations. Missing strings fall back to English.",
            color = PackDim,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createDialog = true }) { Text("New") }
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("Import") }
        }
        Text("BUILT-IN", color = PackDim, fontSize = 12.sp)
        PackChoice("English (source strings)", selected == "builtin:en") {
            runCatching { LanguagePacks.activate(context, "builtin:en") }
                .onSuccess { selected = "builtin:en" }
                .onFailure { error = it.message ?: "Could not activate language." }
        }
        PackChoice("Русский — встроенный", selected == "builtin:ru") {
            runCatching { LanguagePacks.activate(context, "builtin:ru") }
                .onSuccess { selected = "builtin:ru" }
                .onFailure { error = it.message ?: "Could not activate language." }
        }
        Text("INSTALLED PACKS", color = PackDim, fontSize = 12.sp)
        if (packs.isEmpty()) {
            Text(
                "No custom packs yet. Create one or import a .procraft-lang file.",
                color = PackDim,
                fontSize = 14.sp,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(packs, key = { it.id }) { pack ->
                PackChoice(
                    "${pack.name} · ${pack.locale} · ${pack.strings.size} strings",
                    selected == pack.id,
                ) {
                    runCatching { LanguagePacks.activate(context, pack.id) }
                        .onSuccess { selected = pack.id }
                        .onFailure { error = it.message ?: "Could not activate pack." }
                }
            }
        }

        val selectedPack = packs.firstOrNull { it.id == selected }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedPack?.let { editing = it.copy(strings = LinkedHashMap(it.strings)) } },
                enabled = selectedPack != null,
            ) { Text("Edit") }
            Button(
                onClick = { selectedPack?.let { exportPicker.launch("${it.id}.procraft-lang") } },
                enabled = selectedPack != null,
            ) { Text("Export") }
            Button(
                onClick = {
                    selectedPack?.let { pack ->
                        runCatching { LanguagePacks.share(context, pack) }
                            .onFailure { error = it.message ?: "Could not share pack." }
                    }
                },
                enabled = selectedPack != null,
            ) { Text("Share") }
            Button(
                onClick = {
                    selectedPack?.let { pack ->
                        runCatching { LanguagePacks.delete(context, pack) }
                            .onSuccess {
                                packs = LanguagePacks.installed(context)
                                selected = LanguagePacks.currentId()
                            }
                            .onFailure { error = it.message ?: "Could not delete pack." }
                    }
                },
                enabled = selectedPack != null,
            ) { Text("Delete") }
        }

        error?.let {
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Language pack") },
                text = { Text(it) },
                confirmButton = { Button(onClick = { error = null }) { Text("OK") } },
            )
        }
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Create translation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newLocale,
                        onValueChange = { newLocale = it },
                        label = { Text("Language code (e.g. es-ES)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Author (optional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { LanguagePacks.create(newName, newLocale, newAuthor) }
                        .onSuccess { pack ->
                            runCatching {
                                LanguagePacks.save(context, pack)
                                packs = LanguagePacks.installed(context)
                                selected = pack.id
                                LanguagePacks.activate(context, pack.id)
                                editing = pack
                                createDialog = false
                                newName = ""
                                newLocale = ""
                                newAuthor = ""
                            }.onFailure { error = it.message ?: "Could not create the pack." }
                        }
                        .onFailure { error = it.message ?: "Could not create the pack." }
                }) { Text("Create") }
            },
            dismissButton = {
                Button(onClick = { createDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PackChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) Color(0xFF222C3D) else Color(0xFF1C1C1E),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (active) "●" else "○",
            color = if (active) PackBlue else PackDim,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(label, color = PackInk, fontSize = 15.sp)
    }
}

@Composable
private fun PackEditorPage(
    pack: TranslationPack,
    onBack: () -> Unit,
    onSave: (TranslationPack) -> Unit,
) {
    val context = LocalContext.current
    var changed by remember(pack.id) { mutableStateOf(LinkedHashMap(pack.strings)) }
    var search by remember { mutableStateOf("") }
    val catalog = remember { LanguagePacks.catalog(context) }
    val visible = remember(search, catalog, changed) {
        if (search.isBlank()) {
            catalog
        } else {
            catalog.filter {
                it.contains(search, ignoreCase = true) ||
                    changed[it]?.contains(search, ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Packs",
                color = PackBlue,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, color = PackInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${pack.locale} · ${changed.size} translated strings",
                    color = PackDim,
                    fontSize = 12.sp,
                )
            }
            Button(onClick = { onSave(pack.copy(strings = LinkedHashMap(changed))) }) {
                Text("Save")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search source strings") },
            singleLine = true,
        )
        Text(
            "Translate the English source below. Leave a field empty to use English fallback.",
            color = PackDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it }) { source ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
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
        return CompiledPackTemplate(Regex(expression), placeholders, translated)
    }

    private fun renderTemplate(source: String, template: CompiledPackTemplate): String? {
        val match = template.regex.matchEntire(source) ?: return null
        var result = template.translated
        template.placeholders.forEachIndexed { index, placeholder ->
            result = result.replace(placeholder, match.groupValues[index + 1])
        }
        return result
    }

    private fun requiredString(root: JSONObject, key: String): String =
        root.get(key) as? String ?: error("The pack field '$key' must be a string.")

    private fun optionalString(root: JSONObject, key: String, default: String): String {
        if (!root.has(key)) return default
        return root.get(key) as? String ?: error("The pack field '$key' must be a string.")
    }

    private fun validateJsonDepth(raw: String) {
        var depth = 0
        var inString = false
        var escaped = false
        for (character in raw) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    require(depth <= MAX_PACK_JSON_DEPTH) { "The language pack JSON is nested too deeply." }
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "The language pack JSON is malformed." }
                }
            }
        }
    }

    private fun readPack(file: File): TranslationPack {
        val bytes = AtomicFile(file).openRead().use { readBounded(it, MAX_PACK_BYTES) }
        return decode(bytes.toString(Charsets.UTF_8))
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Language pack is larger than 5 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun packsDirectory(context: Context) = File(context.filesDir, "language-packs")

    private fun packFile(context: Context, id: String): File {
        require(PackIdPattern.matches(id)) { "Invalid language-pack ID." }
        return File(packsDirectory(context), "$id.procraft-lang")
    }
}

@Composable
internal fun LanguagePacksPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var packs by remember { mutableStateOf(LanguagePacks.installed(context)) }
    var selected by remember { mutableStateOf(LanguagePacks.currentId()) }
    var editing by remember { mutableStateOf<TranslationPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLocale by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val pack = LanguagePacks.import(context, uri)
                LanguagePacks.activate(context, pack.id)
                packs = LanguagePacks.installed(context)
                selected = pack.id
            }.onFailure { error = it.message ?: "Import failed." }
        }
    }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            packs.firstOrNull { it.id == selected }?.let { pack ->
                runCatching { LanguagePacks.export(context, uri, pack) }
                    .onFailure { error = it.message ?: "Export failed." }
            }
        }
    }

    if (editing != null) {
        PackEditorPage(
            pack = requireNotNull(editing),
            onBack = { editing = null },
            onSave = { changed ->
                runCatching {
                    LanguagePacks.save(context, changed)
                    LanguagePacks.activate(context, changed.id)
                    packs = LanguagePacks.installed(context)
                    selected = changed.id
                    editing = null
                }.onFailure { error = it.message ?: "Could not save the pack." }
            },
        )
        error?.let {
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Language pack") },
                text = { Text(it) },
                confirmButton = { Button(onClick = { error = null }) { Text("OK") } },
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Settings",
                color = PackBlue,
                fontSize = 17.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text(
                "Language packs",
                color = PackInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Create, edit, import and export translations. Missing strings fall back to English.",
            color = PackDim,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createDialog = true }) { Text("New") }
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("Import") }
        }
        Text("BUILT-IN", color = PackDim, fontSize = 12.sp)
        PackChoice("English (source strings)", selected == "builtin:en") {
            runCatching { LanguagePacks.activate(context, "builtin:en") }
                .onSuccess { selected = "builtin:en" }
                .onFailure { error = it.message ?: "Could not activate language." }
        }
        PackChoice("Русский — встроенный", selected == "builtin:ru") {
            runCatching { LanguagePacks.activate(context, "builtin:ru") }
                .onSuccess { selected = "builtin:ru" }
                .onFailure { error = it.message ?: "Could not activate language." }
        }
        Text("INSTALLED PACKS", color = PackDim, fontSize = 12.sp)
        if (packs.isEmpty()) {
            Text(
                "No custom packs yet. Create one or import a .procraft-lang file.",
                color = PackDim,
                fontSize = 14.sp,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(packs, key = { it.id }) { pack ->
                PackChoice(
                    "${pack.name} · ${pack.locale} · ${pack.strings.size} strings",
                    selected == pack.id,
                ) {
                    runCatching { LanguagePacks.activate(context, pack.id) }
                        .onSuccess { selected = pack.id }
                        .onFailure { error = it.message ?: "Could not activate pack." }
                }
            }
        }

        val selectedPack = packs.firstOrNull { it.id == selected }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedPack?.let { editing = it.copy(strings = LinkedHashMap(it.strings)) } },
                enabled = selectedPack != null,
            ) { Text("Edit") }
            Button(
                onClick = { selectedPack?.let { exportPicker.launch("${it.id}.procraft-lang") } },
                enabled = selectedPack != null,
            ) { Text("Export") }
            Button(
                onClick = {
                    selectedPack?.let { pack ->
                        runCatching { LanguagePacks.share(context, pack) }
                            .onFailure { error = it.message ?: "Could not share pack." }
                    }
                },
                enabled = selectedPack != null,
            ) { Text("Share") }
            Button(
                onClick = {
                    selectedPack?.let { pack ->
                        runCatching { LanguagePacks.delete(context, pack) }
                            .onSuccess {
                                packs = LanguagePacks.installed(context)
                                selected = LanguagePacks.currentId()
                            }
                            .onFailure { error = it.message ?: "Could not delete pack." }
                    }
                },
                enabled = selectedPack != null,
            ) { Text("Delete") }
        }

        error?.let {
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Language pack") },
                text = { Text(it) },
                confirmButton = { Button(onClick = { error = null }) { Text("OK") } },
            )
        }
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Create translation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newLocale,
                        onValueChange = { newLocale = it },
                        label = { Text("Language code (e.g. es-ES)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Author (optional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { LanguagePacks.create(newName, newLocale, newAuthor) }
                        .onSuccess { pack ->
                            runCatching {
                                LanguagePacks.save(context, pack)
                                packs = LanguagePacks.installed(context)
                                selected = pack.id
                                LanguagePacks.activate(context, pack.id)
                                editing = pack
                                createDialog = false
                                newName = ""
                                newLocale = ""
                                newAuthor = ""
                            }.onFailure { error = it.message ?: "Could not create the pack." }
                        }
                        .onFailure { error = it.message ?: "Could not create the pack." }
                }) { Text("Create") }
            },
            dismissButton = {
                Button(onClick = { createDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PackChoice(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) Color(0xFF222C3D) else Color(0xFF1C1C1E),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (active) "●" else "○",
            color = if (active) PackBlue else PackDim,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(label, color = PackInk, fontSize = 15.sp)
    }
}

@Composable
private fun PackEditorPage(
    pack: TranslationPack,
    onBack: () -> Unit,
    onSave: (TranslationPack) -> Unit,
) {
    val context = LocalContext.current
    var changed by remember(pack.id) { mutableStateOf(LinkedHashMap(pack.strings)) }
    var search by remember { mutableStateOf("") }
    val catalog = remember { LanguagePacks.catalog(context) }
    val visible = remember(search, catalog, changed) {
        if (search.isBlank()) {
            catalog
        } else {
            catalog.filter {
                it.contains(search, ignoreCase = true) ||
                    changed[it]?.contains(search, ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Packs",
                color = PackBlue,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, color = PackInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${pack.locale} · ${changed.size} translated strings",
                    color = PackDim,
                    fontSize = 12.sp,
                )
            }
            Button(onClick = { onSave(pack.copy(strings = LinkedHashMap(changed))) }) {
                Text("Save")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search source strings") },
            singleLine = true,
        )
        Text(
            "Translate the English source below. Leave a field empty to use English fallback.",
            color = PackDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it }) { source ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
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
