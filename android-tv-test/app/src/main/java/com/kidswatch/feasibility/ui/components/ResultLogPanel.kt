package com.kidswatch.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidswatch.feasibility.ui.theme.TvSuccess
import com.kidswatch.feasibility.ui.theme.TvError
import com.kidswatch.feasibility.ui.theme.TvWarning
import com.kidswatch.feasibility.ui.theme.TvText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

data class LogEntry(val message: String, val level: LogLevel = LogLevel.INFO)

@Composable
fun ResultLogPanel(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 400.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                logs.forEach { entry ->
                    val color = when (entry.level) {
                        LogLevel.INFO -> TvText
                        LogLevel.SUCCESS -> TvSuccess
                        LogLevel.WARNING -> TvWarning
                        LogLevel.ERROR -> TvError
                    }
                    val prefix = when (entry.level) {
                        LogLevel.INFO -> "[INFO] "
                        LogLevel.SUCCESS -> "[OK]   "
                        LogLevel.WARNING -> "[WARN] "
                        LogLevel.ERROR -> "[ERR]  "
                    }
                    withStyle(SpanStyle(color = color)) {
                        append(prefix)
                        append(entry.message)
                        append("\n")
                    }
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.verticalScroll(scrollState),
        )
    }
}
