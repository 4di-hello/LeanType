// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.heatmap.TypingHeatmap
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog

/** Number of samples a key needs before its average offset is shown */
private const val MIN_SAMPLES_FOR_AVERAGE = 15

@Composable
fun TypingHeatmapScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val layouts = remember(refresh) { TypingHeatmap.getLayouts(context) }
    var selectedLayoutId by remember { mutableStateOf<String?>(null) }
    var onlyMisses by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val layout = layouts.firstOrNull { it.id == selectedLayoutId } ?: layouts.firstOrNull()

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.typing_heatmap_title),
        settings = emptyList(),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (layout == null) {
                InfoCard(stringResource(R.string.typing_heatmap_no_data))
                return@Column
            }
            if (layouts.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    layouts.forEach {
                        FilterChip(
                            selected = it.id == layout.id,
                            onClick = { selectedLayoutId = it.id },
                            label = { Text(it.id) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyMisses, onClick = { onlyMisses = false },
                    label = { Text(stringResource(R.string.typing_heatmap_all_touches)) })
                FilterChip(selected = onlyMisses, onClick = { onlyMisses = true },
                    label = { Text(stringResource(R.string.typing_heatmap_only_misses)) })
            }

            HeatmapCanvas(layout, onlyMisses)

            val total = layout.sampleCount
            val misses = layout.samples.entries.sumOf { (code, list) -> list.count { it.touchedCode != code } }
            val percent = if (total == 0) 0f else misses * 100f / total
            Text(stringResource(R.string.typing_heatmap_stats, total, misses, percent), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.typing_heatmap_legend), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            val confusions = layout.samples.entries
                .flatMap { (code, list) -> list.filter { it.touchedCode != code }.map { code to it.touchedCode } }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(8)
            if (confusions.isNotEmpty()) {
                Text(stringResource(R.string.typing_heatmap_top_misses), style = MaterialTheme.typography.titleSmall)
                confusions.forEach { (pair, count) ->
                    Text(stringResource(R.string.typing_heatmap_miss_line, cp(pair.first), cp(pair.second), count),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            InfoCard(stringResource(R.string.typing_heatmap_privacy))
            OutlinedButton(onClick = { showClearDialog = true }) {
                Text(stringResource(R.string.typing_heatmap_clear))
            }
        }
    }
    if (showClearDialog) {
        ConfirmationDialog(
            onDismissRequest = { showClearDialog = false },
            onConfirmed = {
                TypingHeatmap.clear(context)
                refresh++
            },
            content = { Text(stringResource(R.string.typing_heatmap_clear_confirm)) }
        )
    }
}

private fun cp(code: Int) = String(Character.toChars(code))

@Composable
private fun InfoCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HeatmapCanvas(layout: TypingHeatmap.LayoutData, onlyMisses: Boolean) {
    val keyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val keyBorder = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val hitColor = MaterialTheme.colorScheme.primary
    val missColor = Color(0xFFE53935)
    val averageColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = labelColor)
    val shapesByCode = remember(layout) { layout.shapes.associateBy { it.code } }
    val aspect = layout.aspectRatio.takeIf { it > 0.05f } ?: 0.35f

    Canvas(Modifier.fillMaxWidth().aspectRatio(1f / aspect)) {
        val w = size.width
        val h = size.height
        val gap = 2.dp.toPx()
        // keys
        layout.shapes.forEach { key ->
            val topLeft = Offset(key.x * w + gap, key.y * h + gap)
            val keySize = Size(key.width * w - 2 * gap, key.height * h - 2 * gap)
            if (keySize.width <= 0 || keySize.height <= 0) return@forEach
            drawRoundRect(keyColor, topLeft, keySize, CornerRadius(6.dp.toPx()))
            drawRoundRect(keyBorder, topLeft, keySize, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
            if (key.label.isNotEmpty() && key.label.length <= 3) {
                val measured = textMeasurer.measure(key.label, labelStyle)
                drawText(measured, topLeft = Offset(
                    key.centerX * w - measured.size.width / 2f,
                    key.centerY * h - measured.size.height / 2f
                ))
            }
        }
        // touches
        val dotRadius = 2.5.dp.toPx()
        layout.samples.forEach { (code, list) ->
            val key = shapesByCode[code] ?: return@forEach
            list.forEach { sample ->
                val miss = sample.touchedCode != code
                if (onlyMisses && !miss) return@forEach
                val x = (key.centerX + sample.dx * key.width) * w
                val y = (key.centerY + sample.dy * key.height) * h
                drawCircle(if (miss) missColor.copy(alpha = 0.8f) else hitColor.copy(alpha = 0.3f), dotRadius, Offset(x, y))
            }
            // average offset of all touches meant for this key
            if (!onlyMisses && list.size >= MIN_SAMPLES_FOR_AVERAGE) {
                val avgX = list.sumOf { it.dx.toDouble() }.toFloat() / list.size
                val avgY = list.sumOf { it.dy.toDouble() }.toFloat() / list.size
                val center = Offset(key.centerX * w, key.centerY * h)
                val avg = Offset((key.centerX + avgX * key.width) * w, (key.centerY + avgY * key.height) * h)
                drawLine(averageColor, center, avg, 2.dp.toPx())
                drawCircle(averageColor, 3.5.dp.toPx(), avg)
            }
        }
    }
}
