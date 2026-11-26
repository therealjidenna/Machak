package com.therealjidenna.machak

import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

class BrowseState {
    var dir by mutableStateOf(Environment.getExternalStorageDirectory())
    var items by mutableStateOf(listOf<FileItem>())
    var selected by mutableStateOf(setOf<String>())
    var sort by mutableIntStateOf(0) // 0 = name, 1 = size, 2 = date
    var loading by mutableStateOf(false)
    var message by mutableStateOf("")
    var reload by mutableIntStateOf(0)
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

private fun goUp(s: BrowseState) {
    s.dir = s.dir.parentFile ?: s.dir
    s.selected = emptySet()
    s.message = ""
}

@Composable
fun BrowseTab(s: BrowseState, hasAccess: Boolean, onGrantAccess: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory().absolutePath }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    var confirm by remember { mutableStateOf(false) }
    val atRoot = s.dir.absolutePath == root

    LaunchedEffect(hasAccess, s.dir, s.sort, s.reload) {
        if (hasAccess) {
            s.loading = true
            s.items = withContext(Dispatchers.IO) { loadDir(s.dir, s.sort) }
            s.loading = false
        }
    }

    BackHandler(enabled = hasAccess && !atRoot) { goUp(s) }

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
            Caption("Internal storage" + s.dir.absolutePath.removePrefix(root))

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
                if (!atRoot) {
                    IosButton("Up", { goUp(s) }, modifier = Modifier.weight(1f), filled = false)
                }
                IosButton(
                    if (s.items.isNotEmpty() && s.selected.size == s.items.size) "None" else "All",
                    {
                        s.selected = if (s.selected.size == s.items.size) {
                            emptySet()
                        } else {
                            s.items.map { it.file.absolutePath }.toSet()
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
            if (!s.loading && s.items.isEmpty()) Caption("This folder is empty.")

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
        }
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
                        val failed = withContext(Dispatchers.IO) {
                            s.selected.count { !File(it).deleteRecursively() }
                        }
                        s.selected = emptySet()
                        s.message = if (failed == 0) "Deleted." else "$failed items could not be deleted."
                        s.reload = s.reload + 1
                    }
                }) { Text("Delete", color = Ios.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}
