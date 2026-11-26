package io.github.artcraftandroid.procraft.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors


private const val COMMUNITY_HUB_INDEX_URL =
    "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/index.json"
private const val COMMUNITY_HUB_RAW_PREFIX =
    "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/"
private const val MAX_HUB_INDEX_BYTES = 512 * 1024

internal data class CommunityTranslation(
    val id: String,
    val locale: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val catalogVersion: String,
    val stringsCount: Int,
    val updatedAt: String,
    val downloadUrl: String,
    val sha256: String,
)

internal object CommunityTranslationHub {
    private const val CHECK_PREFS = "community-language-packs-hub"
    private const val LAST_CHECK_KEY = "last_catalog_check_ms"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private fun cacheFile(context: Context) =
        File(File(context.filesDir, "language-packs/.hub"), "index.json")

    fun shouldCheck(context: Context): Boolean {
        val lastCheck = context.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_CHECK_KEY, 0L)
        val now = System.currentTimeMillis()
        return lastCheck == 0L || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS
    }

    fun cachedCatalog(context: Context): List<CommunityTranslation> = runCatching {
        val file = cacheFile(context)
        require(file.isFile && file.length() <= MAX_HUB_INDEX_BYTES) {
            "No valid cached community catalogue is available."
        }
        decodeCatalog(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun fetchCatalog(context: Context): List<CommunityTranslation> {
        val raw = String(fetchBytes(COMMUNITY_HUB_INDEX_URL, MAX_HUB_INDEX_BYTES), Charsets.UTF_8)
        val entries = decodeCatalog(raw)
        val file = cacheFile(context)
        val parent = file.parentFile
        require(parent != null && (parent.isDirectory || parent.mkdirs())) {
            "Could not create the translation catalogue cache."
        }
        val atomic = AtomicFile(file)
        var output: java.io.FileOutputStream? = null
        try {
            val stream = atomic.startWrite()
            output = stream
            stream.write(raw.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            output = null
        } catch (error: Throwable) {
            output?.let { atomic.failWrite(it) }
            throw error
        }
        context.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply()
        return entries
    }

    internal fun decodeCatalog(raw: String): List<CommunityTranslation> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_HUB_INDEX_BYTES) {
            "The community catalogue is larger than 512 KB."
        }
        val root = JSONObject(raw)
        require(root.optInt("schema_version", -1) == 1) {
            "Unsupported community catalogue version."
        }
        require(root.optString("catalog_version") == PACK_CATALOG_VERSION) {
            "The community catalogue targets a different source-string catalogue. Update ProCraft first."
        }
        require(Regex("""\d{4}-\d{2}-\d{2}""").matches(root.optString("updated_at"))) {
            "The community catalogue has an invalid update date."
        }
        val array = root.optJSONArray("packs") ?: error("The community catalogue has no packs list.")
        require(array.length() <= 100) { "The community catalogue contains too many translations." }

        val ids = HashSet<String>()
        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: error("A community catalogue entry is not an object.")
            val entry = CommunityTranslation(
                id = item.getString("id"),
                locale = item.getString("locale"),
                name = item.getString("name"),
                author = item.optString("author"),
                description = item.getString("description"),
                version = item.getString("version"),
                catalogVersion = item.getString("catalog_version"),
                stringsCount = item.getInt("strings_count"),
                updatedAt = item.getString("updated_at"),
                downloadUrl = item.getString("download_url"),
                sha256 = item.getString("sha256"),
            )
            require(PackIdPattern.matches(entry.id) && ids.add(entry.id)) {
                "The community catalogue contains an invalid or duplicate pack ID."
            }
            require(PackLocalePattern.matches(entry.locale) && entry.locale.length <= 35) {
                "Invalid locale for " + entry.id + "."
            }
            require(entry.name.isNotBlank() && entry.name.length <= 80 && entry.author.length <= 120) {
                "Invalid metadata for " + entry.id + "."
            }
            require(entry.description.isNotBlank() && entry.description.length <= 240) {
                "Invalid description for " + entry.id + "."
            }
            require(Regex("""\d+\.\d+\.\d+""").matches(entry.version)) {
                "Invalid version for " + entry.id + "."
            }
            require(entry.catalogVersion == PACK_CATALOG_VERSION) {
                "Translation " + entry.id + " targets a different source-string catalogue."
            }
            require(entry.stringsCount in 1..MAX_PACK_STRINGS) {
                "Invalid translated-string count for " + entry.id + "."
            }
            require(Regex("""\d{4}-\d{2}-\d{2}""").matches(entry.updatedAt)) {
                "Invalid update date for " + entry.id + "."
            }
            validateDownloadUrl(entry.downloadUrl)
            require(Regex("""[a-f0-9]{64}""").matches(entry.sha256)) {
                "Invalid SHA-256 digest for " + entry.id + "."
            }
            entry
        }
    }

    fun download(entry: CommunityTranslation): TranslationPack {
        validateDownloadUrl(entry.downloadUrl)
        val bytes = fetchBytes(entry.downloadUrl, MAX_PACK_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        require(digest == entry.sha256) {
            "The downloaded translation failed its SHA-256 integrity check."
        }
        val pack = LanguagePacks.decode(LanguagePacks.decodeUtf8Strict(bytes))
        require(
            pack.id == entry.id &&
                pack.locale == entry.locale &&
                pack.name == entry.name &&
                pack.author == entry.author &&
                pack.catalogVersion == entry.catalogVersion &&
                pack.strings.size == entry.stringsCount
        ) {
            "The downloaded package metadata does not match the community catalogue."
        }
        return pack
    }

    private fun validateDownloadUrl(value: String) {
        val uri = URI(value)
        require(
            uri.scheme == "https" &&
                uri.host == "raw.githubusercontent.com" &&
                uri.userInfo == null &&
                uri.port == -1 &&
                uri.query == null &&
                uri.fragment == null &&
                value.startsWith(COMMUNITY_HUB_RAW_PREFIX) &&
                uri.path.endsWith(".procraft-lang")
        ) {
            "A community translation contains an unsafe download URL."
        }
    }

    private fun fetchBytes(value: String, limit: Int): ByteArray {
        val connection = URL(value).openConnection() as? HttpURLConnection
            ?: error("Unsupported network connection.")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "ProCraft-Translation-Hub/1")
        connection.setRequestProperty("Accept", "application/json, application/octet-stream")
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "The community server returned HTTP " + connection.responseCode + "."
            }
            val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= limit) { "The downloaded file is larger than the allowed limit." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}

private val CommunityHubExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "procraft-community-hub").apply { isDaemon = true }
}

private const val MAX_PACK_BYTES = 5 * 1024 * 1024
private const val MAX_PACK_STRINGS = 20_000
private const val MAX_PACK_JSON_DEPTH = 32
private const val MAX_TEMPLATE_STRINGS = 128
private const val PACK_CATALOG_VERSION = "procraft-ui-source-3"
private const val PACK_APP_VERSION = "0.0.7-beta"

private val PackInk get() = Pc.Text
private val PackDim get() = Pc.TextDim
private val PackBlue = Color(0xFF0A84FF)
private val PackBackground get() = if (Pc.light) Color(0xFFF7F7F7) else Color(0xFF000000)
private val PackSurface get() = Pc.Row
private val PackFieldContainer get() = if (Pc.light) Color(0xFFFFFFFF) else Color(0xFF252529)

private fun packTextDirection(locale: String): TextDirection =
    if (LanguagePacks.isRtlLocale(locale)) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr

private fun packEditorTextStyle(locale: String) = TextStyle(
    color = PackInk,
    textDirection = packTextDirection(locale),
    textAlign = if (packTextDirection(locale) == TextDirection.ContentOrRtl) TextAlign.End else TextAlign.Start,
)

@Composable
private fun packFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PackInk,
    unfocusedTextColor = PackInk,
    disabledTextColor = PackDim,
    focusedContainerColor = PackFieldContainer,
    unfocusedContainerColor = PackFieldContainer,
    disabledContainerColor = PackFieldContainer,
    cursorColor = PackBlue,
    focusedLabelColor = PackBlue,
    unfocusedLabelColor = PackDim,
    focusedBorderColor = PackBlue,
    unfocusedBorderColor = PackDim,
    focusedPlaceholderColor = PackDim,
    unfocusedPlaceholderColor = PackDim,
)
private val PackIdPattern = Regex("[a-z0-9][a-z0-9._-]{2,95}")
private val PackLocalePattern = Regex("[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})*")
private val PackPlaceholderPattern = Regex("""\{([A-Za-z][A-Za-z0-9_]*)\}""")

internal data class CompiledPackTemplate(
    val literals: List<String>,
    val placeholders: List<String>,
    val translated: String,
)

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

internal data class CommunityPackInstallResult(
    val pack: TranslationPack,
    val localChangesPreserved: Int,
    val conflictsKeptLocal: Int,
    val backupName: String?,
)

/**
 * Data-only .procraft-lang reader, editor storage and runtime.
 *
 * Version 1 uses exact English UI text as keys. Imported files never execute code and are bounded
 * by size, nesting, entry-count and field-length limits before they can be installed.
 */
internal object LanguagePacks {
    private const val FORMAT_VERSION = 1
    private const val PREFS = "community-language-packs"
    private const val ACTIVE = "active_id"

    private val revisionState = mutableIntStateOf(0)
    private var activeId = "builtin:ru"
    private var activeLocale = "ru"
    private var activeStrings: Map<String, String>? = null
    private var activeTemplates: List<CompiledPackTemplate> = emptyList()

    /** Compose Text reads this state so changing the active pack redraws existing labels. */
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
        activeLocale = when (activeId) {
            "builtin:en" -> "en"
            "builtin:ru" -> "ru"
            else -> runCatching { readPack(packFile(context, activeId)).locale }.getOrDefault("ru")
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
                activeStrings?.get(source)?.takeIf { it.isNotBlank() }
                    ?: if (source.length <= 4096) {
                        activeTemplates.firstNotNullOfOrNull { renderTemplate(source, it) } ?: source
                    } else {
                        source
                    }
            }
        }
    }

    fun currentId(): String = activeId

    fun currentLocale(): String = activeLocale

    fun isRtlLocale(locale: String): Boolean {
        val subtags = locale.lowercase(Locale.ROOT).replace('_', '-').split('-')
        val script = subtags.firstOrNull { it.length == 4 }
        if (script != null) return script in setOf("arab", "hebr", "thaa", "nkoo", "adlm", "syrc")
        return subtags.firstOrNull() in setOf("ar", "fa", "he", "ur", "ps", "sd", "ug", "yi", "dv", "ckb", "nqo", "syr")
    }

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
        activeLocale = when (id) {
            "builtin:en" -> "en"
            "builtin:ru" -> "ru"
            else -> runCatching { readPack(packFile(context, id)).locale }.getOrDefault("ru")
        }
        refreshTemplates()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ACTIVE, id).apply()
        revisionState.intValue++
    }

    fun installed(context: Context): List<TranslationPack> {
        return packsDirectory(context).listFiles().orEmpty()
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
        require(directory.exists() || directory.mkdirs()) {
            "Could not create the language-pack folder."
        }

        writeAtomic(packFile(context, pack.id), encode(pack).toByteArray(Charsets.UTF_8))
        revisionState.intValue++
        return pack
    }

    private data class HubMetadata(val version: String, val sha256: String)

    private fun hubDirectory(context: Context) = File(packsDirectory(context), ".hub")

    private fun hubBaselineFile(context: Context, id: String) =
        File(hubDirectory(context), "$id.baseline.procraft-lang")

    private fun hubMetadataFile(context: Context, id: String) =
        File(hubDirectory(context), "$id.json")

    private fun hubBackupDirectory(context: Context) =
        File(hubDirectory(context), "backups")

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val parent = file.parentFile
        require(parent != null && (parent.isDirectory || parent.mkdirs())) {
            "Could not create the language-pack data folder."
        }
        val atomic = AtomicFile(file)
        var output: java.io.FileOutputStream? = null
        try {
            val stream = atomic.startWrite()
            output = stream
            stream.write(bytes)
            atomic.finishWrite(stream)
            output = null
        } catch (error: Throwable) {
            output?.let { atomic.failWrite(it) }
            throw error
        }
    }

    private fun readHubMetadata(context: Context, id: String): HubMetadata? = runCatching {
        val file = hubMetadataFile(context, id)
        if (!file.isFile || file.length() > 4096L) return@runCatching null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val version = json.getString("version")
        val digest = json.getString("sha256")
        require(Regex("""\d+\.\d+\.\d+""").matches(version))
        require(Regex("""[a-f0-9]{64}""").matches(digest))
        HubMetadata(version, digest)
    }.getOrNull()

    private fun compareHubVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toLongOrNull() ?: 0L }
        val b = right.split('.').map { it.toLongOrNull() ?: 0L }
        for (index in 0..2) {
            val comparison = a[index].compareTo(b[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun communityUpdateAvailable(context: Context, entry: CommunityTranslation): Boolean {
        val installed = readHubMetadata(context, entry.id) ?: return true
        return compareHubVersions(entry.version, installed.version) > 0 ||
            entry.sha256 != installed.sha256
    }

    fun hasCommunityLocalChanges(context: Context, id: String): Boolean {
        if (!PackIdPattern.matches(id)) return false
        val installed = runCatching { readPack(packFile(context, id)) }.getOrNull() ?: return false
        val baseline = runCatching { readPack(hubBaselineFile(context, id)) }.getOrNull()
            ?: return installed.strings.isNotEmpty()
        return installed.strings != baseline.strings
    }

    private data class PackMerge(
        val pack: TranslationPack,
        val localChangesPreserved: Int,
        val conflictsKeptLocal: Int,
    )

    private fun mergeCommunityPack(
        baseline: TranslationPack?,
        local: TranslationPack,
        remote: TranslationPack,
    ): PackMerge {
        val keys = LinkedHashSet<String>().apply {
            baseline?.strings?.keys?.let(::addAll)
            addAll(local.strings.keys)
            addAll(remote.strings.keys)
        }
        val merged = LinkedHashMap<String, String>()
        var localChanges = 0
        var conflicts = 0

        for (key in keys) {
            val baseValue = baseline?.strings?.get(key)
            val localValue = local.strings[key]
            val remoteValue = remote.strings[key]
            if (localValue != baseValue) localChanges++

            val selected = when {
                localValue == baseValue -> remoteValue
                remoteValue == baseValue || localValue == remoteValue -> localValue
                else -> {
                    conflicts++
                    localValue
                }
            }
            if (selected != null) merged[key] = selected
        }
        return PackMerge(
            pack = remote.copy(strings = merged),
            localChangesPreserved = localChanges,
            conflictsKeptLocal = conflicts,
        )
    }

    fun installCommunityPack(
        context: Context,
        entry: CommunityTranslation,
        downloaded: TranslationPack,
    ): CommunityPackInstallResult {
        require(entry.id == downloaded.id && entry.sha256.matches(Regex("""[a-f0-9]{64}"""))) {
            "Community package metadata is inconsistent."
        }
        validate(downloaded)
        val previousMetadata = readHubMetadata(context, entry.id)
        if (previousMetadata != null && compareHubVersions(entry.version, previousMetadata.version) < 0) {
            error("A downgrade of this community translation was blocked.")
        }

        val destination = packFile(context, entry.id)
        val existing = if (destination.isFile) readPack(destination) else null
        val baseline = runCatching { readPack(hubBaselineFile(context, entry.id)) }.getOrNull()
        val merge = if (existing == null) {
            PackMerge(downloaded, 0, 0)
        } else {
            mergeCommunityPack(baseline, existing, downloaded)
        }

        var backupName: String? = null
        if (existing != null && existing != merge.pack) {
            val backupDirectory = hubBackupDirectory(context)
            require(backupDirectory.isDirectory || backupDirectory.mkdirs()) {
                "Could not create a backup for the local translation."
            }
            val backup = File(
                backupDirectory,
                "${entry.id}-${System.currentTimeMillis()}.procraft-lang",
            )
            writeAtomic(backup, encode(existing).toByteArray(Charsets.UTF_8))
            backupName = backup.name
        }

        save(context, merge.pack)
        writeAtomic(
            hubBaselineFile(context, entry.id),
            encode(downloaded).toByteArray(Charsets.UTF_8),
        )
        writeAtomic(
            hubMetadataFile(context, entry.id),
            JSONObject()
                .put("version", entry.version)
                .put("sha256", entry.sha256)
                .toString()
                .toByteArray(Charsets.UTF_8),
        )
        return CommunityPackInstallResult(
            pack = merge.pack,
            localChangesPreserved = merge.localChangesPreserved,
            conflictsKeptLocal = merge.conflictsKeptLocal,
            backupName = backupName,
        )
    }

    fun import(context: Context, uri: Uri): TranslationPack {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the selected file.")
        val bytes = input.use { readBounded(it, MAX_PACK_BYTES) }
        val pack = decode(decodeUtf8Strict(bytes))
        val destination = packFile(context, pack.id)
        if (destination.exists()) {
            val existing = readPack(destination)
            require(existing == pack) {
                "A different language pack with this ID is already installed."
            }
            return existing
        }
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
        val raw = context.assets.open("localization/ui-catalog.json")
            .bufferedReader().use { it.readText() }
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
        val versionToken = Regex("""(?s)"format_version"\s*:\s*([^,\s}]+)""")
            .find(raw)?.groupValues?.get(1)
        require(versionToken != null && Regex("""(?:0|[1-9]\d*)""").matches(versionToken)) {
            "The pack format version must be an integer."
        }
        val root = JSONObject(raw)
        val allowedFields = setOf(
            "format_version", "id", "locale", "name", "author",
            "app_version", "catalog_version", "base_locale", "strings",
        )
        val actualFields = buildSet {
            val keys = root.keys()
            while (keys.hasNext()) add(keys.next())
        }
        require(actualFields.all { it in allowedFields }) {
            "The pack contains unsupported fields."
        }

        val formatVersion = root.get("format_version")
        require(formatVersion is Int || formatVersion is Long) {
            "The pack format version must be an integer."
        }
        require((formatVersion as Number).toLong() == FORMAT_VERSION.toLong()) {
            "Unsupported language-pack format version."
        }

        val stringsObject = root.optJSONObject("strings")
            ?: error("The pack has no strings dictionary.")
        require(stringsObject.length() <= MAX_PACK_STRINGS) {
            "The pack contains too many strings."
        }

        val strings = linkedMapOf<String, String>()
        val keys = stringsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = stringsObject.get(key) as? String
                ?: error("Translation values must be strings.")
            require(key.isNotBlank() && key.length <= 512 && value.length <= 4000) {
                "A string exceeds the format limits."
            }
            require(placeholders(key) == placeholders(value)) {
                "Placeholder mismatch in: $key"
            }
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
        require(pack.name.isNotBlank() && pack.name.length <= 80) {
            "Invalid language-pack name."
        }
        val templateCount = pack.strings.keys.count { it.contains('{') }
        require(
            pack.author.length <= 120 &&
                pack.appVersion.length <= 40 &&
                pack.catalogVersion.length <= 80 &&
                pack.baseLocale.length <= 35 &&
                pack.strings.size <= MAX_PACK_STRINGS &&
                templateCount <= MAX_TEMPLATE_STRINGS
        ) {
            "Pack metadata, string count or dynamic-template count exceeds the format limits."
        }
        pack.strings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 512 && value.length <= 4000) {
                "A string exceeds the format limits."
            }
            require(placeholders(key) == placeholders(value)) {
                "Placeholder mismatch in: $key"
            }
        }
    }

    private fun placeholders(value: String): List<String> =
        Regex("""\{[^{}]+\}""").findAll(value).map { it.value }.sorted().toList()

    private fun refreshTemplates() {
        activeTemplates = activeStrings?.entries
            ?.filter { it.key.contains('{') && it.value.isNotBlank() }
            ?.mapNotNull { (pattern, translated) -> compileTemplate(pattern, translated) }
            ?.sortedByDescending { template -> template.literals.sumOf { it.length } }
            .orEmpty()
    }

    internal fun compileTemplate(pattern: String, translated: String): CompiledPackTemplate? {
        val matches = PackPlaceholderPattern.findAll(pattern).toList()
        if (matches.isEmpty()) return null
        val literals = ArrayList<String>(matches.size + 1)
        var cursor = 0
        matches.forEach { placeholder ->
            val literal = pattern.substring(cursor, placeholder.range.first)
            if (literals.isNotEmpty() && literal.isEmpty()) return null
            literals.add(literal)
            cursor = placeholder.range.last + 1
        }
        literals.add(pattern.substring(cursor))
        return CompiledPackTemplate(
            literals = literals,
            placeholders = matches.map { it.value },
            translated = translated,
        )
    }

    internal fun renderTemplate(source: String, template: CompiledPackTemplate): String? {
        var remaining = source
        val values = linkedMapOf<String, String>()
        template.placeholders.forEachIndexed { index, placeholder ->
            val prefix = if (index == 0) template.literals[index] else ""
            if (!remaining.startsWith(prefix)) return null
            remaining = remaining.substring(prefix.length)

            val suffix = template.literals[index + 1]
            val isLast = index == template.placeholders.lastIndex
            val valueEnd = if (isLast) {
                if (!remaining.endsWith(suffix)) return null
                remaining.length - suffix.length
            } else {
                if (suffix.isEmpty()) return null
                remaining.indexOf(suffix)
            }
            if (valueEnd <= 0) return null
            val value = remaining.substring(0, valueEnd)
            val name = placeholder.substring(1, placeholder.length - 1)
            val previous = values.put(name, value)
            if (previous != null && previous != value) return null
            remaining = remaining.substring(valueEnd + suffix.length)
        }
        if (remaining.isNotEmpty()) return null

        var result = template.translated
        values.forEach { (name, value) -> result = result.replace("{${name}}", value) }
        return result
    }

    internal fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
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
                    require(depth <= MAX_PACK_JSON_DEPTH) {
                        "The language pack JSON is nested too deeply."
                    }
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
        return decode(decodeUtf8Strict(bytes))
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var packs by remember { mutableStateOf(LanguagePacks.installed(context)) }
    var selected by remember { mutableStateOf(LanguagePacks.currentId()) }
    var showHub by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TranslationPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLocale by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }
    var hubEntries by remember { mutableStateOf(CommunityTranslationHub.cachedCatalog(context)) }
    var catalogChecking by remember { mutableStateOf(false) }
    var catalogCheckFailed by remember { mutableStateOf(false) }

    fun checkCatalogIfDue() {
        if (catalogChecking || !CommunityTranslationHub.shouldCheck(context)) return
        catalogChecking = true
        CommunityHubExecutor.execute {
            val result = runCatching { CommunityTranslationHub.fetchCatalog(context) }
            mainHandler.post {
                result.onSuccess { hubEntries = it; catalogCheckFailed = false }
                    .onFailure { catalogCheckFailed = true }
                catalogChecking = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { checkCatalogIfDue() }

    if (showHub) {
        CommunityHubPage(
            installedPacks = packs,
            initialEntries = hubEntries,
            onCatalogUpdated = { hubEntries = it },
            onBack = { showHub = false },
            onInstalled = { pack ->
                packs = LanguagePacks.installed(context)
                selected = pack.id
            },
        )
        return
    }

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
        modifier = Modifier.fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .background(PackBackground)
            .padding(20.dp),
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
        Button(
            onClick = { showHub = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Community translations") }
        val installedIds = packs.mapTo(HashSet()) { it.id }
        val updateCount = hubEntries.count { it.id in installedIds && LanguagePacks.communityUpdateAvailable(context, it) }
        val newTranslationCount = hubEntries.count { it.id !in installedIds }
        if (catalogChecking) {
            Text("Checking the translation catalogue…", color = PackDim, fontSize = 12.sp)
        }
        if (updateCount > 0) {
            Text(
                "$updateCount translation update(s) available. Open Community translations to review them.",
                color = PackBlue,
                fontSize = 13.sp,
            )
        } else if (newTranslationCount > 0) {
            Text(
                "$newTranslationCount new community translation(s) available.",
                color = PackDim,
                fontSize = 12.sp,
            )
        }
        if (catalogCheckFailed) {
            Text("Could not check for updates. The last cached catalogue is still available.", color = PackDim, fontSize = 12.sp)
        }
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
                        textStyle = packEditorTextStyle(LanguagePacks.currentLocale()),
                        colors = packFieldColors(),
                    )
                    OutlinedTextField(
                        value = newLocale,
                        onValueChange = { newLocale = it },
                        label = { Text("Language code (e.g. es-ES)") },
                        singleLine = true,
                        textStyle = packEditorTextStyle("en"),
                        colors = packFieldColors(),
                    )
                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Author (optional)") },
                        singleLine = true,
                        textStyle = packEditorTextStyle(LanguagePacks.currentLocale()),
                        colors = packFieldColors(),
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
private fun CommunityHubPage(
    installedPacks: List<TranslationPack>,
    initialEntries: List<CommunityTranslation>,
    onCatalogUpdated: (List<CommunityTranslation>) -> Unit,
    onBack: () -> Unit,
    onInstalled: (TranslationPack) -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var entries by remember { mutableStateOf(initialEntries) }
    var loading by remember { mutableStateOf(false) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingReplacement by remember { mutableStateOf<CommunityTranslation?>(null) }

    fun refreshCatalog() {
        if (loading) return
        loading = true
        error = null
        CommunityHubExecutor.execute {
            val result = runCatching { CommunityTranslationHub.fetchCatalog(context) }
            mainHandler.post {
                result.onSuccess {
                    entries = it
                    onCatalogUpdated(it)
                }.onFailure { error = it.message ?: "Could not load the community catalogue." }
                loading = false
            }
        }
    }

    fun install(entry: CommunityTranslation) {
        if (installingId != null) return
        installingId = entry.id
        error = null
        CommunityHubExecutor.execute {
            val result = runCatching {
                val downloaded = CommunityTranslationHub.download(entry)
                LanguagePacks.installCommunityPack(context, entry, downloaded)
            }
            mainHandler.post {
                result.onSuccess { installed ->
                    runCatching { LanguagePacks.activate(context, installed.pack.id) }
                        .onSuccess {
                            onInstalled(installed.pack)
                            error = null
                        }
                        .onFailure { error = it.message ?: "Could not activate this translation." }
                }.onFailure {
                    error = it.message ?: "Could not download this translation."
                }
                installingId = null
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (entries.isEmpty() || CommunityTranslationHub.shouldCheck(context)) refreshCatalog()
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .background(PackBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Language packs",
                color = PackBlue,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text(
                "Community translations",
                color = PackInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { refreshCatalog() }, enabled = !loading) {
                Text(if (loading) "Loading…" else "Refresh")
            }
        }
        Text(
            "Reviewed community translations. Packages are checked before installation.",
            color = PackDim,
            fontSize = 13.sp,
        )
        if (loading && entries.isEmpty()) {
            Text("Connecting to the community catalogue…", color = PackDim, fontSize = 14.sp)
        } else if (entries.isEmpty()) {
            Text(
                "No translations are currently listed. Check back after new packs are reviewed.",
                color = PackDim,
                fontSize = 14.sp,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                val installedPack = installedPacks.firstOrNull { it.id == entry.id }
                val isInstalled = installedPack != null
                val updateAvailable = isInstalled && LanguagePacks.communityUpdateAvailable(context, entry)
                val hasLocalChanges = isInstalled && LanguagePacks.hasCommunityLocalChanges(context, entry.id)
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(PackSurface, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.name,
                            color = PackInk,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(entry.locale, color = PackBlue, fontSize = 12.sp)
                    }
                    Text("by " + entry.author + " · v" + entry.version, color = PackDim, fontSize = 12.sp)
                    Text(entry.description, color = PackInk, fontSize = 14.sp)
                    Text(
                        entry.stringsCount.toString() + " translated strings · Updated " + entry.updatedAt,
                        color = PackDim,
                        fontSize = 12.sp,
                    )
                    if (updateAvailable) {
                        Text("Update available", color = PackBlue, fontSize = 12.sp)
                    } else if (isInstalled) {
                        Text("Installed version is up to date", color = PackDim, fontSize = 12.sp)
                    }
                    if (hasLocalChanges) {
                        Text(
                            "Local edits detected. Updates preserve them and keep a backup.",
                            color = PackDim,
                            fontSize = 12.sp,
                        )
                    }
                    Button(
                        onClick = {
                            if (isInstalled) pendingReplacement = entry else install(entry)
                        },
                        enabled = installingId == null,
                    ) {
                        Text(
                            when {
                                installingId == entry.id -> "Installing…"
                                isInstalled && updateAvailable -> "Update and merge"
                                isInstalled -> "Reinstall safely"
                                else -> "Install"
                            },
                        )
                    }
                }
            }
        }
        error?.let {
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("Community translations") },
                text = { Text(it) },
                confirmButton = { Button(onClick = { error = null }) { Text("OK") } },
            )
        }
    }

    pendingReplacement?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingReplacement = null },
            title = { Text("Update installed translation?") },
            text = {
                Text(
                    "The app compares your installed pack with its previous Hub version. " +
                        "Your local changes are preserved, conflicting local values win, and a backup is created before changed content is saved.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingReplacement = null
                    install(entry)
                }) { Text("Update and merge") }
            },
            dismissButton = {
                Button(onClick = { pendingReplacement = null }) { Text("Cancel") }
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
                if (active) (if (Pc.light) Color(0xFFB7D7FF) else Color(0xFF222C3D)) else PackSurface,
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

    val isRtl = LanguagePacks.isRtlLocale(pack.locale)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
            else androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .background(PackBackground)
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
            textStyle = packEditorTextStyle(pack.locale),
            colors = packFieldColors(),
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
                        .background(PackSurface, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text(source, color = PackDim, fontSize = 13.sp)
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
                        textStyle = packEditorTextStyle(pack.locale),
                        colors = packFieldColors(),
                    )
                }
            }
        }
    }
    }
}
