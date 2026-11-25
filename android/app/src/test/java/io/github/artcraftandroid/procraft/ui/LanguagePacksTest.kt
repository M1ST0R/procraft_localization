package io.github.artcraftandroid.procraft.ui

import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePacksTest {
    @Test
    fun translatesASingleNamedPlaceholder() {
        val template = LanguagePacks.compileTemplate("{count} artworks", "{count} obras")
        assertEquals("12 obras", LanguagePacks.renderTemplate("12 artworks", requireNotNull(template)))
    }

    @Test
    fun translatesMultipleNamedPlaceholders() {
        val template = LanguagePacks.compileTemplate("{used} of {maximum}", "{used} из {maximum}")
        assertEquals("2 из 5", LanguagePacks.renderTemplate("2 of 5", requireNotNull(template)))
    }

    @Test
    fun allowsTranslatedPlaceholdersToChangeOrder() {
        val template = LanguagePacks.compileTemplate(
            "{used} of {maximum}",
            "{maximum} всего ({used} использовано)",
        )
        assertEquals("5 всего (2 использовано)", LanguagePacks.renderTemplate("2 of 5", requireNotNull(template)))
    }

    @Test
    fun repeatedPlaceholderMustCaptureTheSameValue() {
        val template = requireNotNull(
            LanguagePacks.compileTemplate("{item} then {item}", "repeat {item}")
        )
        assertEquals("repeat a", LanguagePacks.renderTemplate("a then a", template))
        assertNull(LanguagePacks.renderTemplate("a then b", template))
    }

    @Test
    fun rejectsAdjacentPlaceholdersWithoutADelimiter() {
        assertNull(LanguagePacks.compileTemplate("{first}{second}", "{first}-{second}"))
    }

    @Test
    fun rejectsMalformedUtf8InsteadOfReplacingBytes() {
        val invalidUtf8 = byteArrayOf(0x7b, 0x22, 0x69, 0x64, 0x22, 0x3a, 0x22, 0xff.toByte(), 0x22, 0x7d)
        val error = runCatching { LanguagePacks.decodeUtf8Strict(invalidUtf8) }.exceptionOrNull()
        assertTrue(error is CharacterCodingException)
    }

    @Test
    fun rejectsFloatingPointFormatVersions() {
        val raw = """{"format_version":1.0,"strings":{}}"""
        val error = runCatching { LanguagePacks.decode(raw) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("integer") == true)
    }

    @Test
    fun acceptsLongSourceKeysUsedByTheFullCatalog() {
        val key = "A".repeat(175)
        val raw = "{\"format_version\":1,\"id\":\"ru.community.demo\",\"locale\":\"ru-RU\",\"name\":\"Русский — Community Demo\",\"author\":\"ProCraft Community\",\"app_version\":\"0.0.7-beta\",\"catalog_version\":\"procraft-ui-source-3\",\"base_locale\":\"en\",\"strings\":{\"$key\":\"Перевод\"}}"

        val decoded = LanguagePacks.decode(raw)
        assertEquals("Перевод", decoded.strings[key])
    }


    @Test
    fun acceptsNamedPlaceholdersInJapaneseTranslations() {
        val raw = """{"format_version":1,"id":"ja-jp.community","locale":"ja-JP","name":"日本語","author":"Community","app_version":"0.0.7-beta","catalog_version":"procraft-ui-source-3","base_locale":"en","strings":{"{count} artworks":"作品数: {count}"}}"""

        val decoded = LanguagePacks.decode(raw)
        assertEquals("作品数: {count}", decoded.strings["{count} artworks"])
    }

    @Test
    fun rejectsJapaneseTranslationsThatDropNamedPlaceholders() {
        val raw = """{"format_version":1,"id":"ja-jp.community","locale":"ja-JP","name":"日本語","author":"Community","app_version":"0.0.7-beta","catalog_version":"procraft-ui-source-3","base_locale":"en","strings":{"{count} artworks":"作品数"}}"""

        val error = runCatching { LanguagePacks.decode(raw) }.exceptionOrNull()
        assertTrue(error?.message?.contains("Placeholder mismatch") == true)
    }


    @Test
    fun acceptsAnEmptyCommunityHubCatalogue() {
        val raw = """{"schema_version":1,"catalog_version":"procraft-ui-source-3","updated_at":"2026-11-25","packs":[]}"""
        assertTrue(CommunityTranslationHub.decodeCatalog(raw).isEmpty())
    }

    @Test
    fun rejectsCommunityPacksFromUntrustedHosts() {
        val raw = """{"schema_version":1,"catalog_version":"procraft-ui-source-3","updated_at":"2026-11-25","packs":[{"id":"ja-jp.community","locale":"ja-JP","name":"日本語","author":"Contributor","description":"Japanese community translation.","version":"1.0.0","catalog_version":"procraft-ui-source-3","strings_count":1200,"updated_at":"2026-11-25","download_url":"https://example.com/ja-jp.procraft-lang","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}"""
        val error = runCatching { CommunityTranslationHub.decodeCatalog(raw) }.exceptionOrNull()
        assertTrue(error?.message?.contains("unsafe download URL") == true)
    }

    @Test
    fun rejectsCommunityCataloguesForDifferentSourceRevisions() {
        val raw = """{"schema_version":1,"catalog_version":"old-revision","updated_at":"2026-11-25","packs":[]}"""
        val error = runCatching { CommunityTranslationHub.decodeCatalog(raw) }.exceptionOrNull()
        assertTrue(error?.message?.contains("different source-string catalogue") == true)
    }

}
