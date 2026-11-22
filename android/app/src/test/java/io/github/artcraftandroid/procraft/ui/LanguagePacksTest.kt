package io.github.artcraftandroid.procraft.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LanguagePacksTest {
    @Test
    fun translatesASingleNamedPlaceholder() {
        val template = LanguagePacks.compileTemplate("{count} artworks", "{count} obras")
        assertNotNull(template)
        assertEquals("12 obras", LanguagePacks.renderTemplate("12 artworks", template!!))
    }

    @Test
    fun translatesMultipleNamedPlaceholders() {
        val template = LanguagePacks.compileTemplate("{used} of {maximum}", "{used} из {maximum}")
        assertNotNull(template)
        assertEquals("2 из 5", LanguagePacks.renderTemplate("2 of 5", template!!))
    }

    @Test
    fun allowsTranslatedPlaceholdersToChangeOrder() {
        val template = LanguagePacks.compileTemplate(
            "{used} of {maximum}",
            "{maximum} всего ({used} использовано)",
        )
        assertNotNull(template)
        assertEquals("5 всего (2 использовано)", LanguagePacks.renderTemplate("2 of 5", template!!))
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
    fun rejectsTranslationsThatDropAPlaceholder() {
        val valid = """{
            "format_version": 1,
            "id": "es.demo",
            "locale": "es-ES",
            "name": "Espanol",
            "strings": {
                "Layer {count}": "Capa {count}"
            }
        }"""
        assertEquals("Capa {count}", LanguagePacks.decode(valid).strings["Layer {count}"])

        val invalid = valid.replace("Capa {count}", "Capa")
        try {
            LanguagePacks.decode(invalid)
            throw AssertionError("Expected placeholder mismatch to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected: imported translations must preserve every named placeholder.
        }
    }

    @Test
    fun rejectsUnknownTopLevelFields() {
        val input = """{
            "format_version": 1,
            "id": "es.demo",
            "locale": "es-ES",
            "name": "Espanol",
            "strings": {},
            "script": "must never execute"
        }"""
        try {
            LanguagePacks.decode(input)
            throw AssertionError("Expected an unknown field to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected: packs are strict data-only documents.
        }
    }
}
