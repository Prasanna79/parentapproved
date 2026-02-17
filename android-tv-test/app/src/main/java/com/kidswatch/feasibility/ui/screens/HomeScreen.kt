package com.kidswatch.feasibility.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kidswatch.feasibility.ui.theme.TvAccent
import com.kidswatch.feasibility.ui.theme.TvBackground
import com.kidswatch.feasibility.ui.theme.TvPrimary
import com.kidswatch.feasibility.ui.theme.TvSuccess
import com.kidswatch.feasibility.ui.theme.TvText
import com.kidswatch.feasibility.ui.theme.TvWarning

@Composable
fun HomeScreen(
    onNavigateToAccountTest: () -> Unit,
    onNavigateToWebViewTest: () -> Unit,
    onNavigateToEmbedTest: () -> Unit,
    onNavigateToNewPipeTest: () -> Unit,
    onNavigateToPlaylistTest: () -> Unit,
    onNavigateToStreamQualityTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "KidsWatch Feasibility Test",
            style = MaterialTheme.typography.headlineLarge,
            color = TvText,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Validate technical hypotheses for the KidsWatch app",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToAccountTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
        ) {
            Text(
                text = "Test 1: AccountManager Token",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToWebViewTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
        ) {
            Text(
                text = "Test 2: WebView Sign-in + Playback",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToEmbedTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvSuccess),
        ) {
            Text(
                text = "Test 3: Embed API (No Sign-in)",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToNewPipeTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvWarning),
        ) {
            Text(
                text = "Test 4: NewPipe + ExoPlayer",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToPlaylistTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvPrimary.copy(alpha = 0.8f)),
        ) {
            Text(
                text = "Test 5: Playlist Resolution",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToStreamQualityTest,
            modifier = Modifier
                .widthIn(min = 400.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TvAccent.copy(alpha = 0.8f)),
        ) {
            Text(
                text = "Test 6: Stream Quality",
                style = MaterialTheme.typography.labelLarge,
                color = TvText,
            )
        }
    }
}
