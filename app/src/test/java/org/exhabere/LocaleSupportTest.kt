package org.exhabere

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleSupportTest {
    @Test
    fun germanIsSupported() {
        assertEquals(Locale.GERMAN, supportedAppLocale(Locale.GERMANY))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(Locale.ENGLISH, supportedAppLocale(Locale.FRENCH))
    }
}
