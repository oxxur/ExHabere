package org.exhabere.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.exhabere.R

private const val DEFAULT_REPLACEMENT_PLAN =
    "When I feel Bored in Bed, I will Read 2 pages of a physical book."

@Composable
internal fun String?.localizedDefaultPlan(): String? = when (this) {
    DEFAULT_REPLACEMENT_PLAN -> stringResource(R.string.default_replacement_plan)
    else -> this
}
