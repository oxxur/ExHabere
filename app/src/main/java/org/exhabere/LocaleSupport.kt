package org.exhabere

import java.util.Locale

/** Languages with complete app translations; unsupported system languages use English. */
internal fun supportedAppLocale(systemLocale: Locale = Locale.getDefault()): Locale =
    if (systemLocale.language.equals(Locale.GERMAN.language, ignoreCase = true)) Locale.GERMAN
    else Locale.ENGLISH
