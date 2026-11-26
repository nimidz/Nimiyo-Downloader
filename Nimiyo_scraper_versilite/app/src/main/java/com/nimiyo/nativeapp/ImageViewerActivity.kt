package com.nimiyo.nativeapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nimiyo.nativeapp.core.StringsI18n
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.nimiyo.nativeapp.ui.components.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val path = intent.getStringExtra("path").orEmpty()
        val uriStr = intent.getStringExtra("uri").orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Nimiyo Photo" }
        val fileObj = path.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }

        val targetUri: Uri? = when {
            fileObj != null -> FileProvider.getUriForFile(this, "$packageName.fileprovider", fileObj)
            uriStr.isNotBlank() -> Uri.parse(uriStr)
            else -> null
        }

        if (targetUri == null) {
            finish()
            return
        }

        val mimeType = contentResolver.getType(targetUri) ?: "image/*"

        setContent {
            BackHandler { finish() }
            val strings = remember {
                StringsI18n.get(NimiyoAppContainer.get(this@ImageViewerActivity).settings.current.language)
            }
            val scope = rememberCoroutineScope()
            var imgDimensions by remember { mutableStateOf("-") }
            var fileSizeText by remember { mutableStateOf("-") }
            var modifiedText by remember { mutableStateOf("-") }

            LaunchedEffect(targetUri, path) {
                val (dims, sz, mod) = withContext(Dispatchers.IO) {
                    val d = runCatching {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        if (fileObj != null) {
                            BitmapFactory.decodeFile(fileObj.absolutePath, opts)
                        } else {
                            contentResolver.openInputStream(targetUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        }
                        if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth} × ${opts.outHeight} px" else "-"
                    }.getOrDefault("-")
                    val s = runCatching {
                        val bytes = fileObj?.length()
                            ?: contentResolver.openAssetFileDescriptor(targetUri, "r")?.use { it.length }
                            ?: 0L
                        formatFileSize(bytes).ifBlank { "-" }
                    }.getOrDefault("-")
                    val m = runCatching {
                        val ms = fileObj?.lastModified() ?: 0L
                        if (ms > 0L) SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms)) else "-"
                    }.getOrDefault("-")
                    Triple(d, s, m)
                }
                imgDimensions = dims
                fileSizeText = sz
                modifiedText = mod
            }

            var showControls by remember { mutableStateOf(true) }
            var scale by remember { mutableFloatStateOf(1f) }
            var rotationDeg by remember { mutableFloatStateOf(0f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var showInfoDialog by remember { mutableStateOf(false) }
            var showMoreMenu by remember { mutableStateOf(false) }

            val state = rememberTransformableState { zoomChange, panChange, _ ->
                val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                scale = nextScale
                if (nextScale > 1f) {
                    val maxX = (containerSize.width * (nextScale - 1f)) / 2f
                    val maxY = (containerSize.height * (nextScale - 1f)) / 2f
                    offset = Offset(
                        x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                        y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
                    )
                } else {
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    },
            ) {
                AsyncImage(
                    model = targetUri,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotationDeg,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )

                // Top Bar
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.40f))
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.viewer.backCd, tint = Color.White)
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Bottom Bar: Bagikan | Putar | Hapus | Info | Lainnya
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.40f))
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ImageActionItem(
                            icon = Icons.Rounded.Share,
                            label = strings.viewer.shareLabel,
                            onClick = {
                                runCatching {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, targetUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(share, title))
                                }.onFailure {
                                    Toast.makeText(this@ImageViewerActivity, strings.viewer.noAppToast, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )

                        ImageActionItem(
                            icon = Icons.AutoMirrored.Rounded.RotateRight,
                            label = strings.viewer.rotateLabel,
                            onClick = { rotationDeg = (rotationDeg + 90f) % 360f },
                        )

                        ImageActionItem(
                            icon = Icons.Rounded.Delete,
                            label = strings.viewer.deleteLabel,
                            onClick = { showDeleteConfirm = true },
                        )

                        ImageActionItem(
                            icon = Icons.Rounded.Info,
                            label = strings.viewer.infoLabel,
                            onClick = { showInfoDialog = true },
                        )

                        Box {
                            ImageActionItem(
                                icon = Icons.Rounded.MoreVert,
                                label = strings.viewer.moreLabel,
                                onClick = { showMoreMenu = true },
                            )
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.viewer.setAs) },
                                    leadingIcon = { Icon(Icons.Rounded.Wallpaper, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        runCatching {
                                            val attach = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                                addCategory(Intent.CATEGORY_DEFAULT)
                                                setDataAndType(targetUri, mimeType)
                                                putExtra("mimeType", mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            startActivity(Intent.createChooser(attach, strings.viewer.setAs))
                                        }.onFailure {
                                            Toast.makeText(this@ImageViewerActivity, strings.viewer.noAppToast, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.viewer.openWith) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        runCatching {
                                            val openWith = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(targetUri, mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            startActivity(Intent.createChooser(openWith, strings.viewer.openWith))
                                        }.onFailure {
                                            Toast.makeText(this@ImageViewerActivity, strings.viewer.noAppToast, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(strings.viewer.deleteImgTitle) },
                    text = { Text(strings.viewer.deleteImgMsg) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    var ok = false
                                    if (uriStr.startsWith("content://", ignoreCase = true)) {
                                        ok = runCatching {
                                            contentResolver.delete(Uri.parse(uriStr), null, null) > 0
                                        }.getOrDefault(false)
                                    }
                                    if (!ok && fileObj != null && fileObj.exists()) {
                                        ok = runCatching { fileObj.delete() }.getOrDefault(false)
                                        if (ok) {
                                            runCatching {
                                                MediaScannerConnection.scanFile(
                                                    this@ImageViewerActivity,
                                                    arrayOf(fileObj.absolutePath),
                                                    null,
                                                    null,
                                                )
                                            }
                                        }
                                    }
                                    ok
                                }
                                if (deleted) {
                                    Toast.makeText(this@ImageViewerActivity, strings.viewer.deletedToast, Toast.LENGTH_SHORT).show()
                                    finish()
                                } else {
                                    Toast.makeText(this@ImageViewerActivity, strings.viewer.deleteFailToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Text(strings.viewer.deleteLabel, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(strings.cancelBtn)
                        }
                    },
                )
            }

            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text(strings.viewer.imgInfoTitle) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${strings.viewer.nameLabel}: ${fileObj?.name ?: title}", fontSize = 13.sp)
                            Text("${strings.viewer.resLabel}: $imgDimensions", fontSize = 13.sp)
                            Text("${strings.viewer.sizeLabel}: $fileSizeText", fontSize = 13.sp)
                            Text("${strings.viewer.formatLabel}: $mimeType", fontSize = 13.sp)
                            Text("${strings.viewer.dateLabel}: $modifiedText", fontSize = 13.sp)
                            Text("${strings.viewer.pathLabel}: ${fileObj?.absolutePath ?: uriStr}", fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text(strings.closeBtn)
                        }
                    },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ImageActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
