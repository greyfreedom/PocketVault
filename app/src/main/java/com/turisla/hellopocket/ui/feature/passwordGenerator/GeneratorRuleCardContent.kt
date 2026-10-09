package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.ui.theme.AppSpacing

/** 组合列表与规则库共用同一层次：名称和操作在上，片段类型与内容在下。 */
@Composable
internal fun GeneratorRuleCardContent(
    name: String,
    segment: GeneratorSegment,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val (label, icon) = when (segment) {
        is GeneratorSegment.Text -> R.string.generator_type_text to Icons.AutoMirrored.Filled.ShortText
        is GeneratorSegment.Digits -> R.string.generator_type_digits to Icons.Default.Numbers
        is GeneratorSegment.Letters -> R.string.generator_type_letters to Icons.Default.Abc
        is GeneratorSegment.Symbols -> R.string.generator_type_symbols to Icons.Default.Tag
    }
    val description = when (segment) {
        is GeneratorSegment.Text -> segment.value
        is GeneratorSegment.Digits -> stringResource(R.string.generator_result_length, segment.length)
        is GeneratorSegment.Letters -> stringResource(R.string.generator_result_length, segment.length) + " · " + letterCaseName(segment.letterCase)
        is GeneratorSegment.Symbols -> stringResource(R.string.generator_result_length, segment.length) + " · " + segment.characters.toSet().joinToString("")
    }
    val avoidConfusing = when (segment) {
        is GeneratorSegment.Digits -> segment.avoidConfusing
        is GeneratorSegment.Letters -> segment.avoidConfusing
        else -> false
    }
    Column(Modifier.padding(AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            Text(
                name,
                modifier = Modifier.weight(1f).padding(start = AppSpacing.xs),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trailing()
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (segment is GeneratorSegment.Text || segment is GeneratorSegment.Symbols) FontFamily.Monospace else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (avoidConfusing) Text(stringResource(R.string.avoid_confusing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
