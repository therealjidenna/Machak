package com.therealjidenna.machak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MachakScreen(
                        hasAccess = hasAccess,
                        onGrantAccess = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onAskNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = Environment.isExternalStorageManager()
    }
}

private fun setAutoScan(ctx: Context, on: Boolean) {
    val wm = WorkManager.getInstance(ctx)
    if (on) {
        wm.enqueueUniquePeriodicWork(
            "machak_auto",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS).build()
        )
    } else {
        wm.cancelUniqueWork("machak_auto")
    }
}

@Composable
fun MachakScreen(
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onAskNotifications: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("machak", Context.MODE_PRIVATE) }

    var tab by remember { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(prefs.getBoolean("auto", false)) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf(listOf<DupGroup>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var appGroups by remember { mutableStateOf<List<List<AppItem>>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Machak  מחק",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Files") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Apps") })
        }

        if (tab == 0) {
            if (!hasAccess) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Machak needs All files access to find duplicates on your phone.")
                    Button(onClick = onGrantAccess, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Grant access")
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-scan daily")
                    Switch(checked = auto, onCheckedChange = {
                        auto = it
                        prefs.edit().putBoolean("auto", it).apply()
                        if (it) onAskNotifications()
                        setAutoScan(ctx, it)
                    })
                }

                Button(
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scanning = true
                        scanned = false
                        message = ""
                        selected = emptySet()
                        scope.launch {
                            val found = withContext(Dispatchers.IO) {
                                FileScanner.scan(onProgress = { progress = it })
                            }
                            groups = found
                            scanning = false
                            scanned = true
                        }
                    }
                ) {
                    Text(if (scanning) "Scanning... $progress files" else "Scan now")
                }

                if (scanned) {
                    val extra = groups.sumOf { it.size * (it.files.size - 1) }
                    Text(
                        if (groups.isEmpty()) "No duplicates found."
                        else "${groups.size} groups, ${Formatter.formatFileSize(ctx, extra)} can be freed",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (message.isNotEmpty()) Text(message)

                if (groups.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            // keep the oldest file in each group, select the rest
                            selected = groups.flatMap { g ->
                                g.files.sortedBy { it.modified }.drop(1).map { it.path }
                            }.toSet()
                        }) { Text("Select extras") }

                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = { confirm = true }
                        ) { Text("Delete (${selected.size})") }
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    groups.forEach { g ->
                        item(key = "h" + g.files.first().path) {
                            Text(
                                "${Formatter.formatFileSize(ctx, g.size)} x ${g.files.size}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        items(g.files, key = { it.path }) { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = f.path in selected,
                                    onCheckedChange = { on ->
                                        if (on) {
                                            // never allow every copy in a group to be selected
                                            val already = g.files.count { it.path in selected }
                                            if (already + 1 < g.files.size) selected = selected + f.path
                                            else message = "Keep at least one copy in each group."
                                        } else {
                                            selected = selected - f.path
                                        }
                                    }
                                )
                                Text(
                                    f.path.removePrefix(Environment.getExternalStorageDirectory().absolutePath),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            appGroups = withContext(Dispatchers.IO) { AppScanner.scan(ctx) }
                        }
                    }
                ) { Text("Scan apps") }

                val ag = appGroups
                if (ag != null) {
                    Text(
                        if (ag.isEmpty()) "No duplicate apps found."
                        else "${ag.size} apps installed more than once",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LazyColumn {
                        ag.forEach { group ->
                            items(group, key = { it.pkg }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${app.pkg}  ${app.version}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(onClick = {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                                        )
                                    }) { Text("Uninstall") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${selected.size} files?") },
            text = { Text("They are deleted straight away and cannot be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) {
                            selected.count { !File(it).delete() }
                        }
                        groups = groups
                            .map { g -> g.copy(files = g.files.filter { File(it.path).exists() }) }
                            .filter { it.files.size > 1 }
                        selected = emptySet()
                        message = if (failed == 0) "Deleted." else "$failed files could not be deleted."
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}
