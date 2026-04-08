package tv.parentapproved.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import tv.parentapproved.app.ui.theme.KidAccent
import tv.parentapproved.app.ui.theme.KidFocusRing
import tv.parentapproved.app.ui.theme.KidSurface
import tv.parentapproved.app.ui.theme.KidText
import tv.parentapproved.app.ui.theme.KidTextDim

private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun AppCard(
    appName: String,
    icon: Drawable?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(120.dp)
            .scale(scale)
            .clip(CardShape)
            .background(if (isFocused) KidSurface.copy(alpha = 0.9f) else KidSurface.copy(alpha = 0.5f))
            .then(
                if (isFocused) Modifier.border(2.dp, KidFocusRing, CardShape)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionCenter) {
                    onClick()
                    true
                } else false
            }
            .padding(12.dp)
    ) {
        when {
            leadingIcon != null -> {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = appName,
                    tint = KidAccent,
                    modifier = Modifier.size(64.dp),
                )
            }
            icon != null -> {
                val bitmap = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = appName,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) KidText else KidTextDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
