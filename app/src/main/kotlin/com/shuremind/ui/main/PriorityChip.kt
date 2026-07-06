package com.shuremind.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shuremind.engine.PriorityBand
import com.shuremind.engine.PriorityEngine

private data class PriorityChipColors(val background: Color, val content: Color)

/** D-39: fixed, dark-theme-safe color pairs per band — deliberately not Material color roles, since
 * the bands are a semantic traffic-light coding independent of the (possibly dynamic-color) theme. */
private fun colorsFor(band: PriorityBand, darkTheme: Boolean): PriorityChipColors = when (band) {
    PriorityBand.NEUTRAL -> if (darkTheme) {
        PriorityChipColors(Color(0xFF49454F), Color(0xFFE6E0E9))
    } else {
        PriorityChipColors(Color(0xFFE0E0E0), Color(0xFF3A3A3A))
    }
    PriorityBand.BLUE -> if (darkTheme) {
        PriorityChipColors(Color(0xFF1A4270), Color(0xFFC7DEFF))
    } else {
        PriorityChipColors(Color(0xFFD3E3FD), Color(0xFF0B3D91))
    }
    PriorityBand.ORANGE -> if (darkTheme) {
        PriorityChipColors(Color(0xFF7A4100), Color(0xFFFFDDB0))
    } else {
        PriorityChipColors(Color(0xFFFFE0B2), Color(0xFF7A3E00))
    }
    PriorityBand.RED -> if (darkTheme) {
        PriorityChipColors(Color(0xFF7A1F1F), Color(0xFFFFD9D6))
    } else {
        PriorityChipColors(Color(0xFFFFCDD2), Color(0xFF7F1D1D))
    }
}

/** D-39: main-list priority chip — score number on a color-banded rounded background. */
@Composable
fun PriorityChip(score: Int, modifier: Modifier = Modifier) {
    val colors = colorsFor(PriorityEngine.bandFor(score), isSystemInDarkTheme())
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = score.toString(),
            color = colors.content,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
