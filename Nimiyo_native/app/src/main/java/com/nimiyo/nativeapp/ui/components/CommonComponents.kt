package com.nimiyo.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle

@Composable
fun NimiyoStyledCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalNimiyoStyle.current
    val shape = RoundedCornerShape(style.cornerRadius)
    if (style.isGlass && style.glassBrush != null && containerColor == Color.Unspecified) {
        Surface(
            modifier = modifier.clip(shape),
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = style.cardBorder,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(style.glassBrush),
                content = content,
            )
        }
        return
    }
    val resolvedBg = if (containerColor == Color.Unspecified) {
        if (style.cardSurface == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerLow else style.cardSurface
    } else containerColor
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = resolvedBg),
        border = style.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = style.cardElevation),
        content = content,
    )
}

@Composable
fun rememberThumbRequest(data: String, sizePx: Int = 160): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, sizePx) {
        ImageRequest.Builder(context)
            .data(data)
            .apply {
                if (data.contains("pximg.net", ignoreCase = true)) {
                    addHeader("Referer", "https://www.pixiv.net/")
                }
            }
            .size(sizePx)
            .crossfade(false)
            .build()
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

