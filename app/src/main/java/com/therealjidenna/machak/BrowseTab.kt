package com.therealjidenna.machak

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val count: Int
)

data class MediaItem(
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long
)

class BrowseState {
    var mode by mutableIntStateOf(0) // 0 = files, 1 = photos, 2 = videos
    var root by mutableStateOf(Environment.getExternalStorageDirectory())
    var dir by mutableStateOf(Environment.getExternalStorageDirectory())
    var items by mutableStateOf(listOf<FileItem>())
    var media by mutableStateOf(listOf<MediaItem>())
    var selected by mutableStateOf(setOf<String>())
    var sort by mutableIntStateOf(0) // 0 = name, 1 = size, 2 = date
    var loading by mutableStateOf(false)
    var message by mutableStateOf("")
    var reload by mutableIntStateOf(0)
    var viewer by mutableIntStateOf(-1) // index of the photo or video being viewed
}

private fun loadDir(dir: File, sort: Int): List<FileItem> {
    val list = (dir.listFiles() ?: emptyArray()).map { f ->
        val d = f.isDirectory
        val count = if (d) (f.list()?.size ?: 0) else 0
        val size = if (d) {
            if (sort == 1) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else -1L
        } else {
            f.length()
        }
        FileItem(f, f.name, d, size, f.lastModified(), count)
    }
    return when (sort) {
        1 -> list.sortedByDescending { it.size }
        2 -> list.sortedByDescending { it.modified }
        else -> list.sortedWith(compareBy<FileItem>({ !it.isDir }, { it.name.lowercase() }))
    }
}

private fun loadMedia(ctx: Context, video: Boolean, sort: Int): List<MediaItem> {
    val base = if (video) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val cols = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATA
    )
    val out = mutableListOf<MediaItem>()
    try {
        ctx.contentResolver.query(base, cols, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (c.moveToNext()) {
                val path = c.getString(dataCol) ?: continue
                out.add(
                    MediaItem(
                        uri = android.content.ContentUris.withAppendedId(base, c.getLong(idCol)),
                        path = path,
                        name = c.getString(nameCol) ?: File(path).name,
                        size = c.getLong(sizeCol),
                        modified = c.getLong(dateCol) * 1000
                    )
                )
            }
        }
    } catch (e: Exception) {
        return emptyList()
    }
    return when (sort) {
        1 -> out.sortedByDescending { it.size }
        2 -> out.sortedByDescending { it.modified }
        else -> out.sortedBy { it.name.lowercase() }
    }
}

private fun goUp(s: BrowseState) {
    s.dir = s.dir.parentFile ?: s.dir
    s.selected = emptySet()
    s.message = ""
}

@Composable
private fun Thumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(uri, Size(128, 128), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
        )
    } else {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(Ios.Gray.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun PhotoPage(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(ctx.contentResolver, uri)
                ) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val limit = 2048
                    if (w > limit || h > limit) {
                        val r = limit.toFloat() / maxOf(w, h)
                        decoder.setTargetSize((w * r).toInt(), (h * r).toInt())
                    }
                }.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Loading...", color = Ios.Gray)
        }
    }
}

@Composable
private fun VideoPage(uri: Uri) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            VideoView(c).apply {
                val controller = MediaController(c)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(uri)
                setOnPreparedListener { start() }
            }
        },
        onRelease = { it.stopPlayback() }
    )
}

@Composable
private fun MediaViewer(s: BrowseState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = s.mode == 2
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pager = rememberPagerState(
        initialPage = s.viewer.coerceIn(0, (s.media.size - 1).coerceAtLeast(0))
    ) { s.media.size }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { index -> s.media.getOrNull(index)?.path ?: index.toString() }
            ) { page ->
                val m = s.media.getOrNull(page)
                if (m != null) {
                    if (isVideo) VideoPage(m.uri) else PhotoPage(m.uri)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Close",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp)
                )
                Text(
                    "${pager.currentPage + 1} / ${s.media.size}",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    "Delete",
                    color = Ios.Red,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { confirmDelete = true }
                        .padding(8.dp)
                )
            }

            val cur = s.media.getOrNull(pager.currentPage)
            if (cur != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(
                        cur.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Formatter.formatFileSize(ctx, cur.size) + "  " +
                            dateFormat.format(Date(cur.modified)),
                        color = Ios.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ${if (isVideo) "video" else "photo"}?") },
            text = { Text("It is deleted straight away and cannot be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val m = s.media.getOrNull(pager.currentPage)
                    if (m != null) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                val done = File(m.path).delete()
                                if (done) {
                                    MediaScannerConnection.scanFile(ctx, arrayOf(m.path), null, null)
                                }
                                done
                            }
                            if (ok) {
                                s.media = s.media.filter { it.path != m.path }
                                s.selected = s.selected - m.path
                                s.message = "Deleted."
                                if (s.media.isEmpty()) onClose()
                            } else {
                                s.message = "Could not delete that file."
                            }
                        }
                    }
                }) { Text("Delete", color = Ios.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BrowseTab(s: BrowseState, hasAccess: Boolean, onGrantAccess: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    var confirm by remember { mutableStateOf(false) }

    val volumes = remember {
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        sm.storageVolumes.mapNotNull { v ->
            val d = v.directory
            if (d == null) null
            else (if (v.isPrimary) "Phone" else (v.getDescription(ctx) ?: "SD card")) to d
        }
    }

    val atRoot = s.dir.absolutePath == s.root.absolutePath
    val volumeLabel = volumes.firstOrNull { it.second.absolutePath == s.root.absolutePath }?.first
        ?: "Storage"
    val allPaths = if (s.mode == 0) {
        s.items.map { it.file.absolutePath }
    } else {
        s.media.map { it.path }
    }

    LaunchedEffect(hasAccess, s.mode, s.dir, s.sort, s.reload) {
        if (hasAccess) {
            s.loading = true
            if (s.mode == 0) {
                s.items = withContext(Dispatchers.IO) { loadDir(s.dir, s.sort) }
            } else {
                s.media = withContext(Dispatchers.IO) { loadMedia(ctx, s.mode == 2, s.sort) }
            }
            s.loading = false
        }
    }

    BackHandler(enabled = hasAccess && s.mode == 0 && !atRoot) { goUp(s) }

    Column(Modifier.fillMaxSize()) {
        if (!hasAccess) {
            IosCard {
                Text(
                    "Allow access",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ios.text()
                )
                Caption(
                    "Machak needs All files access to show everything on your phone.",
                    Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                IosButton("Grant access", onGrantAccess, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Files", "Photos", "Videos").forEachIndexed { i, label ->
                    IosButton(
                        label,
                        {
                            s.mode = i
                            s.selected = emptySet()
                            s.message = ""
                        },
                        modifier = Modifier.weight(1f),
                        filled = s.mode == i
                    )
                }
            }

            if (s.mode == 0 && volumes.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    volumes.forEach { v ->
                        IosButton(
                            v.first,
                            {
                                s.root = v.second
                                s.dir = v.second
                                s.selected = emptySet()
                                s.message = ""
                            },
                            modifier = Modifier.weight(1f),
                            filled = s.root.absolutePath == v.second.absolutePath
                        )
                    }
                }
            }

            if (s.mode == 0) {
                Caption(
                    volumeLabel + s.dir.absolutePath.removePrefix(s.root.absolutePath),
                    Modifier.padding(top = 8.dp)
                )
            } else {
                val total = Formatter.formatFileSize(ctx, s.media.sumOf { it.size })
                Caption(
                    "${s.media.size} items, $total",
                    Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Name", "Size", "Date").forEachIndexed { i, label ->
                    IosButton(
                        label,
                        { s.sort = i },
                        modifier = Modifier.weight(1f),
                        filled = s.sort == i
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (s.mode == 0 && !atRoot) {
                    IosButton("Up", { goUp(s) }, modifier = Modifier.weight(1f), filled = false)
                }
                IosButton(
                    if (allPaths.isNotEmpty() && s.selected.size == allPaths.size) "None" else "All",
                    {
                        s.selected = if (s.selected.size == allPaths.size) {
                            emptySet()
                        } else {
                            allPaths.toSet()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    filled = false
                )
                IosButton(
                    "Delete (${s.selected.size})",
                    { confirm = true },
                    modifier = Modifier.weight(1.4f),
                    enabled = s.selected.isNotEmpty(),
                    color = Ios.Red
                )
            }

            if (s.message.isNotEmpty()) Caption(s.message, Modifier.padding(bottom = 8.dp))
            if (s.loading) Caption("Loading...", Modifier.padding(bottom = 8.dp))
            if (!s.loading && allPaths.isEmpty()) Caption("Nothing here.")

            if (s.mode == 0) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ios.card()),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(s.items, key = { it.file.absolutePath }) { item ->
                        val path = item.file.absolutePath
                        val toggle = {
                            s.selected = if (path in s.selected) s.selected - path else s.selected + path
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDir) {
                                        s.dir = item.file
                                        s.selected = emptySet()
                                        s.message = ""
                                    } else {
                                        toggle()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = path in s.selected, onCheckedChange = { toggle() })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (item.isDir) "\uD83D\uDCC1 " else "") + item.name,
                                    fontSize = 16.sp,
                                    color = Ios.text(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = if (item.isDir) {
                                    val n = "${item.count} items"
                                    if (item.size >= 0) Formatter.formatFileSize(ctx, item.size) + "  " + n else n
                                } else {
                                    Formatter.formatFileSize(ctx, item.size) + "  " +
                                        dateFormat.format(Date(item.modified))
                                }
                                Caption(sub)
                            }
                            if (item.isDir) {
                                Text(
                                    "\u203A",
                                    color = Ios.Gray,
                                    fontSize = 24.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Ios.Gray.copy(alpha = 0.25f),
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ios.card()),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(s.media, key = { _, m -> m.path }) { index, m ->
                        val toggle = {
                            s.selected = if (m.path in s.selected) s.selected - m.path else s.selected + m.path
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { s.viewer = index }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = m.path in s.selected, onCheckedChange = { toggle() })
                            Thumb(m.uri)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    m.name,
                                    fontSize = 16.sp,
                                    color = Ios.text(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Caption(
                                    Formatter.formatFileSize(ctx, m.size) + "  " +
                                        dateFormat.format(Date(m.modified))
                                )
                                Caption(File(m.path).parentFile?.name ?: "")
                            }
                        }
                        HorizontalDivider(
                            color = Ios.Gray.copy(alpha = 0.25f),
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }
            }
        }
    }

    if (s.viewer >= 0 && s.media.isNotEmpty()) {
        MediaViewer(s) { s.viewer = -1 }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${s.selected.size} items?") },
            text = {
                Text("Folders are deleted with everything inside them. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        val mode = s.mode
                        val chosen = s.selected
                        val deleted = withContext(Dispatchers.IO) {
                            chosen.filter { p ->
                                val ok = File(p).deleteRecursively()
                                if (ok && mode != 0) {
                                    MediaScannerConnection.scanFile(ctx, arrayOf(p), null, null)
                                }
                                ok
                            }.toSet()
                        }
                        val failed = chosen.size - deleted.size
                        s.selected = emptySet()
                        if (mode == 0) {
                            s.reload = s.reload + 1
                        } else {
                            s.media = s.media.filter { it.path !in deleted }
                        }
                        s.message = if (failed == 0) "Deleted." else "$failed items could not be deleted."
                    }
                }) { Text("Delete", color = Ios.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}
