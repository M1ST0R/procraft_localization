package io.github.artcraftandroid.procraft.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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


}
