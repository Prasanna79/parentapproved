package com.kidswatch.tv.ui.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidswatch.tv.ui.theme.TvError
import com.kidswatch.tv.ui.theme.TvSuccess
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvWarning
import com.kidswatch.tv.util.AppLogger
import com.kidswatch.tv.util.LogLine

@Composable
fun LogPanel(modifier: Modifier = Modifier) {
    val logs = AppLogger.lines
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                logs.forEach { line ->
                    val color = when (line.level) {
                        "OK" -> TvSuccess
                        "WARN" -> TvWarning
                        "ERROR" -> TvError
                        else -> TvText
                    }
                    withStyle(SpanStyle(color = color)) {
                        append("[${line.level}] ${line.message}\n")
                    }
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.verticalScroll(scrollState),
        )
    }
}
