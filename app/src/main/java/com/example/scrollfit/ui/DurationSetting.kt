package com.example.scrollfit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.core.settings.DurationInput
import com.example.scrollfit.core.settings.DurationUnit

internal data class DurationDraft(
    val value: String,
    val unit: DurationUnit,
    val preservedMilliseconds: Long? = null,
) {
    fun milliseconds(positive: Boolean): Long? {
        val preserved = preservedMilliseconds
        if (preserved != null) return preserved.takeIf { it >= 0L && (!positive || it > 0L) }
        return DurationInput.parse(value, unit, positive)
    }

    fun convertedTo(target: DurationUnit, positive: Boolean): DurationDraft? {
        if (target == unit) return this
        val milliseconds = milliseconds(positive) ?: return null
        if (milliseconds % target.milliseconds != 0L) return null
        return copy(value = (milliseconds / target.milliseconds).toString(), unit = target)
    }

    companion object {
        fun fromMilliseconds(milliseconds: Long): DurationDraft {
            require(milliseconds >= 0L)
            return when {
                milliseconds % DurationUnit.MINUTES.milliseconds == 0L -> DurationDraft(
                    value = (milliseconds / DurationUnit.MINUTES.milliseconds).toString(),
                    unit = DurationUnit.MINUTES,
                    preservedMilliseconds = milliseconds,
                )
                milliseconds % DurationUnit.SECONDS.milliseconds == 0L -> DurationDraft(
                    value = (milliseconds / DurationUnit.SECONDS.milliseconds).toString(),
                    unit = DurationUnit.SECONDS,
                    preservedMilliseconds = milliseconds,
                )
                else -> DurationDraft("", DurationUnit.SECONDS, milliseconds)
            }
        }
    }
}

internal val durationDraftSaver: Saver<DurationDraft, ArrayList<String>> = Saver(
    save = { draft ->
        arrayListOf(draft.value, draft.unit.name, draft.preservedMilliseconds?.toString().orEmpty())
    },
    restore = { saved ->
        DurationDraft(
            value = saved.getOrElse(0) { "" },
            unit = saved.getOrNull(1)?.let { runCatching { DurationUnit.valueOf(it) }.getOrNull() }
                ?: DurationUnit.SECONDS,
            preservedMilliseconds = saved.getOrNull(2)?.toLongOrNull(),
        )
    },
)

internal val durationDraftMapSaver: Saver<Map<String, DurationDraft>, ArrayList<String>> = Saver(
    save = { drafts -> ArrayList(drafts.flatMap { (key, draft) ->
        listOf(key, draft.value, draft.unit.name, draft.preservedMilliseconds?.toString().orEmpty())
    }) },
    restore = { saved ->
        saved.chunked(4).mapNotNull { entry ->
            if (entry.size != 4) return@mapNotNull null
            val unit = runCatching { DurationUnit.valueOf(entry[2]) }.getOrNull() ?: return@mapNotNull null
            entry[0] to DurationDraft(entry[1], unit, entry[3].toLongOrNull())
        }.toMap()
    },
)

@Composable
internal fun DurationSetting(
    value: String,
    unit: DurationUnit,
    onValueChange: (String) -> Unit,
    onUnitChange: (DurationUnit) -> Unit,
    @StringRes label: Int,
    positive: Boolean,
    enabled: Boolean,
    preservedMilliseconds: Long? = null,
) {
    var unitError by remember(value, unit, preservedMilliseconds) { mutableStateOf(false) }
    val milliseconds = preservedMilliseconds?.takeIf { !positive || it > 0L }
        ?: if (preservedMilliseconds == null) DurationInput.parse(value, unit, positive) else null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { nextValue ->
                unitError = false
                onValueChange(nextValue)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(label)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            isError = milliseconds == null || unitError,
            supportingText = {
                when {
                    milliseconds == null -> Text(stringResource(if (positive) R.string.product_positive_error else R.string.product_nonnegative_error))
                    unitError -> Text(stringResource(R.string.ux_duration_unit_error), color = MaterialTheme.colorScheme.error)
                    preservedMilliseconds != null && value.isEmpty() -> Text(
                        stringResource(R.string.ux_duration_preserved, duration(preservedMilliseconds)))
                    else -> Text(stringResource(R.string.ux_duration_hint, duration(milliseconds)))
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = unit == DurationUnit.MINUTES,
                onClick = {
                    if (unit == DurationUnit.MINUTES) unitError = false
                    else selectUnit(DurationUnit.MINUTES, milliseconds, unit, onUnitChange) { unitError = true }
                },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(R.string.ux_duration_unit_minutes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                enabled = enabled,
            )
            FilterChip(
                selected = unit == DurationUnit.SECONDS,
                onClick = {
                    if (unit == DurationUnit.SECONDS) unitError = false
                    else selectUnit(DurationUnit.SECONDS, milliseconds, unit, onUnitChange) { unitError = true }
                },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                label = { Text(stringResource(R.string.ux_duration_unit_seconds), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                enabled = enabled,
            )
        }
    }
}

private fun selectUnit(
    target: DurationUnit,
    milliseconds: Long?,
    current: DurationUnit,
    onUnitChange: (DurationUnit) -> Unit,
    onUnrepresentable: () -> Unit,
) {
    if (target == current) return
    if (milliseconds == null || milliseconds % target.milliseconds != 0L) {
        onUnrepresentable()
        return
    }
    onUnitChange(target)
}
