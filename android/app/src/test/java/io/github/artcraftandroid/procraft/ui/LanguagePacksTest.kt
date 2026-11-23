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

}
