package com.nimiyo.nativeapp.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nimiyo.nativeapp.core.AudioQualityPref
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.QuickMediaType
import com.nimiyo.nativeapp.core.QuickVideoAudioFormat
import com.nimiyo.nativeapp.core.QuickVideoCodec
import com.nimiyo.nativeapp.core.QuickVideoResolution
import com.nimiyo.nativeapp.core.Strings

/**
 * Shared media/format/resolution/codec selector used by Home Direct panel,
 * QuickSave Direct dialog mode, and QuickSave "Apply This Format" settings.
 */
@Composable
fun QuickFormatSelectorContent(
    mediaType: QuickMediaType,
    videoCodec: QuickVideoCodec,
    videoResolution: QuickVideoResolution,
    videoAudioFormat: QuickVideoAudioFormat,
    audioQuality: AudioQualityPref,
    photoMode: BatchPhotoMode,
    strings: Strings,
    onMediaTypeChange: (QuickMediaType) -> Unit,
    onVideoCodecChange: (QuickVideoCodec) -> Unit,
    onVideoResolutionChange: (QuickVideoResolution) -> Unit,
    onVideoAudioFormatChange: (QuickVideoAudioFormat) -> Unit,
    onAudioQualityChange: (AudioQualityPref) -> Unit,
    onPhotoModeChange: (BatchPhotoMode) -> Unit,
) {
    var showResolutionMenu by remember { mutableStateOf(false) }

    fun resolutionButtonLabel(res: QuickVideoResolution): String = when (res) {
        QuickVideoResolution.BEST -> strings.quick.resBestDefault
        QuickVideoResolution.LOW -> strings.quick.resLow
        else -> "${strings.quick.resPrefix} ${res.id}p"
    }

    fun resolutionOptionLabel(res: QuickVideoResolution): String = when (res) {
        QuickVideoResolution.BEST -> strings.quick.resBestDefault
        QuickVideoResolution.LOW -> strings.quick.resLow
        QuickVideoResolution.P2160 -> "2160p (4K)"
        QuickVideoResolution.P1440 -> "1440p (2K)"
        else -> "${res.id}p"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickMediaType.entries.forEach { mType ->
                val label = when (mType) {
                    QuickMediaType.VIDEO -> strings.quick.mediaVideo
                    QuickMediaType.AUDIO -> strings.quick.mediaAudio
                    QuickMediaType.PHOTO -> strings.quick.mediaPhoto
                }
                FilterChip(
                    selected = mediaType == mType,
                    onClick = { onMediaTypeChange(mType) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        when (mediaType) {
            QuickMediaType.VIDEO -> {
                Text(
                    text = strings.quick.formatPrefLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = strings.quick.codecLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickVideoCodec.entries.forEach { codec ->
                        val lbl = when (codec) {
                            QuickVideoCodec.LEGACY_H264 -> strings.quick.codecLegacyH264
                            QuickVideoCodec.MODERN_AV1_VP9_H265 -> strings.quick.codecModern
                        }
                        FilterChip(
                            selected = videoCodec == codec,
                            onClick = { onVideoCodecChange(codec) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = lbl,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                Text(
                    text = strings.quick.resolutionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showResolutionMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = resolutionButtonLabel(videoResolution),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showResolutionMenu,
                        onDismissRequest = { showResolutionMenu = false },
                    ) {
                        QuickVideoResolution.entries.forEach { resOpt ->
                            DropdownMenuItem(
                                text = { Text(resolutionOptionLabel(resOpt)) },
                                onClick = {
                                    onVideoResolutionChange(resOpt)
                                    showResolutionMenu = false
                                },
                            )
                        }
                    }
                }

                Text(
                    text = strings.quick.audioFormatLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickVideoAudioFormat.entries.forEach { audFmt ->
                        val lbl = when (audFmt) {
                            QuickVideoAudioFormat.M4A -> strings.quick.audioM4a
                            QuickVideoAudioFormat.OPUS -> strings.quick.audioOpus
                            QuickVideoAudioFormat.AUTO -> strings.quick.audioAuto
                        }
                        FilterChip(
                            selected = videoAudioFormat == audFmt,
                            onClick = { onVideoAudioFormatChange(audFmt) },
                            label = { Text(lbl, maxLines = 1) },
                        )
                    }
                }
            }

            QuickMediaType.AUDIO -> {
                Text(
                    text = strings.quick.audioFormatLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AudioQualityPref.entries.forEach { pref ->
                        FilterChip(
                            selected = audioQuality == pref,
                            onClick = { onAudioQualityChange(pref) },
                            label = { Text(pref.label, maxLines = 1) },
                        )
                    }
                }
            }

            QuickMediaType.PHOTO -> {
                Text(
                    text = strings.quick.photoModeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BatchPhotoMode.entries.forEach { mode ->
                        val lbl = when (mode) {
                            BatchPhotoMode.ALL -> strings.quick.photoAll
                            BatchPhotoMode.FIRST -> strings.quick.photoFirst
                        }
                        FilterChip(
                            selected = photoMode == mode,
                            onClick = { onPhotoModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = lbl,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

