package com.therealjidenna.machak

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MachakTheme {
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

    override fun onResume() {
        super.onResume()
        hasAccess = Environment.isExternalStorageManager()
    }
}

object Ios {
    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Green = Color(0xFF34C759)
    val Gray = Color(0xFF8E8E93)

    @Composable
    fun bg(): Color = if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7)

    @Composable
    fun card(): Color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color.White

    @Composable
    fun text(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black
}

@Composable
fun MachakTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Ios.Blue, background = Color.Black, surface = Color(0xFF1C1C1E))
    } else {
        lightColorScheme(primary = Ios.Blue, background = Color(0xFFF2F2F7), surface = Color.White)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun LargeTitle(text: String) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = Ios.text(),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Ios.Gray, fontSize = 13.sp, modifier = modifier)
}

@Composable
fun IosCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ios.card())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ios.Blue,
    filled: Boolean = true
) {
    val back = when {
        !enabled -> Ios.Gray.copy(alpha = 0.25f)
        filled -> color
        else -> color.copy(alpha = 0.14f)
    }
    val fore = when {
        !enabled -> Ios.Gray
        filled -> Color.White
        else -> color
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(back)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fore, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Ios.Green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Ios.Gray.copy(alpha = 0.4f),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun TabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = Ios.Gray.copy(alpha = 0.3f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ios.card())
                .navigationBarsPadding()
                .padding(vertical = 14.dp)
        ) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i == selected) Ios.Blue else Ios.Gray
                    )
                }
            }
        }
    }
}

class FilesState {
    var groups by mutableStateOf(listOf<DupGroup>())
    var selected by mutableStateOf(setOf<String>())
    var scanning by mutableStateOf(false)
    var scanned by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var message by mutableStateOf("")
}

class AppsState {
    var groups by mutableStateOf<List<List<AppItem>>?>(null)
}

class SecState {
    var report by mutableStateOf<SecurityReport?>(null)
    var scanning by mutableStateOf(false)
}

data class RiskApp(
    val label: String,
    val pkg: String,
    val score: Int,
    val reasons: List<String>
)

data class SecurityReport(val apps: List<RiskApp>, val checked: Int)

fun levelOf(score: Int): Pair<String, Color> = when {
    score >= 8 -> "High" to Ios.Red
    score >= 5 -> "Medium" to Ios.Orange
    else -> "Low" to Ios.Blue
}

/**
 * Behaviour check, not a virus-signature scanner. It scores each installed
 * (non-system) app by the powerful access it holds, which is what spyware
 * depends on.
 */
object SecurityScanner {

    private const val SHOW_FROM = 3

    private val stores = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.huawei.appmarket",
        "com.heytap.market",
        "com.oppo.market",
        "com.xiaomi.mipicks",
        "com.transsion.palmstore"
    )

    private val perms = mapOf(
        "android.permission.READ_SMS" to ("Can read your text messages" to 3),
        "android.permission.RECEIVE_SMS" to ("Can intercept incoming SMS" to 2),
        "android.permission.SEND_SMS" to ("Can send SMS" to 2),
        "android.permission.READ_CALL_LOG" to ("Can read your call history" to 2),
        "android.permission.RECORD_AUDIO" to ("Can use the microphone" to 1),
        "android.permission.CAMERA" to ("Can use the camera" to 1),
        "android.permission.ACCESS_FINE_LOCATION" to ("Can track your precise location" to 1),
        "android.permission.READ_CONTACTS" to ("Can read your contacts" to 1),
        "android.permission.SYSTEM_ALERT_WINDOW" to ("Can draw over other apps" to 2),
        "android.permission.REQUEST_INSTALL_PACKAGES" to ("Can install other apps" to 1)
    )

    private val noGrantNeeded = setOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES"
    )

    fun scan(ctx: Context): SecurityReport {
        val pm = ctx.packageManager
        val accessibility = enabledPackages(ctx, "enabled_accessibility_services")
        val listeners = enabledPackages(ctx, "enabled_notification_listeners")
        val admins = adminPackages(ctx)

        val out = mutableListOf<RiskApp>()
        var checked = 0

        for (p in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val ai = p.applicationInfo ?: continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (p.packageName == ctx.packageName) continue
            checked++

            var score = 0
            val reasons = mutableListOf<String>()

            val req = p.requestedPermissions
            val flags = p.requestedPermissionsFlags
            if (req != null && flags != null) {
                for (i in req.indices) {
                    val hit = perms[req[i]] ?: continue
                    val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (granted || req[i] in noGrantNeeded) {
                        score += hit.second
                        reasons.add(hit.first)
                    }
                }
            }

            if (p.packageName in accessibility) {
                score += 4
                reasons.add("Has an active accessibility service (can read your screen)")
            }
            if (p.packageName in listeners) {
                score += 3
                reasons.add("Can read all your notifications")
            }
            if (p.packageName in admins) {
                score += 4
                reasons.add("Is a device administrator")
            }

            val installer = try {
                pm.getInstallSourceInfo(p.packageName).installingPackageName
            } catch (e: Exception) {
                null
            }
            if (installer == null || installer !in stores) {
                score += 2
                reasons.add("Not installed from an app store")
            }
            if (pm.getLaunchIntentForPackage(p.packageName) == null) {
                score += 2
                reasons.add("Has no app icon (hidden)")
            }

            if (score >= SHOW_FROM) {
                out.add(RiskApp(pm.getApplicationLabel(ai).toString(), p.packageName, score, reasons))
            }
        }

        return SecurityReport(out.sortedByDescending { it.score }, checked)
    }

    private fun enabledPackages(ctx: Context, key: String): Set<String> {
        val raw: String? = try {
            Settings.Secure.getString(ctx.contentResolver, key)
        } catch (e: Exception) {
            null
        }
        if (raw == null) return emptySet()
        return raw.split(":")
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .toSet()
    }

    private fun adminPackages(ctx: Context): Set<String> {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            (dpm.activeAdmins ?: emptyList()).map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}

@Composable
fun MachakScreen(
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onAskNotifications: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val files = remember { FilesState() }
    val apps = remember { AppsState() }
    val sec = remember { SecState() }
    val browse = remember { BrowseState() }
    val conn = remember { ConnState() }
    val titles = listOf("Clean", "Browse", "Apps", "Security", "Connect")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ios.bg())
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "MACHAK  מחק",
                color = Ios.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            LargeTitle(titles[tab])

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> FilesTab(files, hasAccess, onGrantAccess, onAskNotifications)
                    1 -> BrowseTab(browse, hasAccess, onGrantAccess)
                    2 -> AppsTab(apps)
                    3 -> SecurityTab(sec)
                    else -> ConnectionsTab(conn, onAskNotifications)
                }
            }
        }

        TabBar(titles, tab) { tab = it }
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
fun FilesTab(
    s: FilesState,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onAskNotifications: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("machak", Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean("auto", false)) }
    var confirm by remember { mutableStateOf(false) }
    val root = Environment.getExternalStorageDirectory().absolutePath

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
                    "Machak needs All files access to find duplicates on your phone.",
                    Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                IosButton("Grant access", onGrantAccess, modifier = Modifier.fillMaxWidth())
            }
        } else {
            IosCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Daily auto-scan",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ios.text()
                        )
                        Caption("Notifies you when new duplicates appear")
                    }
                    IosSwitch(auto) {
                        auto = it
                        prefs.edit().putBoolean("auto", it).apply()
                        if (it) onAskNotifications()
                        setAutoScan(ctx, it)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            IosButton(
                if (s.scanning) "Scanning... ${s.progress} files" else "Scan now",
                {
                    s.scanning = true
                    s.scanned = false
                    s.message = ""
                    s.selected = emptySet()
                    scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            FileScanner.scan(onProgress = { s.progress = it })
                        }
                        s.groups = found
                        s.scanning = false
                        s.scanned = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !s.scanning
            )

            if (s.scanned) {
                val extra = s.groups.sumOf { it.size * (it.files.size - 1) }
                Caption(
                    if (s.groups.isEmpty()) "No duplicates found."
                    else "${s.groups.size} groups. ${Formatter.formatFileSize(ctx, extra)} can be freed.",
                    Modifier.padding(top = 12.dp)
                )
            }
            if (s.message.isNotEmpty()) Caption(s.message, Modifier.padding(top = 4.dp))

            if (s.groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosButton(
                        "Select extras",
                        {
                            // keep the oldest file in each group, select the rest
                            s.selected = s.groups.flatMap { g ->
                                g.files.sortedBy { it.modified }.drop(1).map { it.path }
                            }.toSet()
                        },
                        modifier = Modifier.weight(1f),
                        filled = false
                    )
                    IosButton(
                        "Delete (${s.selected.size})",
                        { confirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = s.selected.isNotEmpty(),
                        color = Ios.Red
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                s.groups.forEach { g ->
                    item(key = "g" + g.files.first().path) {
                        IosCard(Modifier.padding(top = 12.dp)) {
                            Text(
                                "${Formatter.formatFileSize(ctx, g.size)} x ${g.files.size}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ios.text()
                            )
                            g.files.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = f.path in s.selected,
                                        onCheckedChange = { on ->
                                            if (on) {
                                                // never allow every copy in a group to be selected
                                                val already = g.files.count { it.path in s.selected }
                                                if (already + 1 < g.files.size) s.selected = s.selected + f.path
                                                else s.message = "Keep at least one copy in each group."
                                            } else {
                                                s.selected = s.selected - f.path
                                            }
                                        }
                                    )
                                    Text(
                                        f.path.removePrefix(root),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        color = Ios.Gray
                                    )
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
            title = { Text("Delete ${s.selected.size} files?") },
            text = { Text("They are deleted straight away and cannot be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) {
                            s.selected.count { !File(it).delete() }
                        }
                        s.groups = s.groups
                            .map { g -> g.copy(files = g.files.filter { File(it.path).exists() }) }
                            .filter { it.files.size > 1 }
                        s.selected = emptySet()
                        s.message = if (failed == 0) "Deleted." else "$failed files could not be deleted."
                    }
                }) { Text("Delete", color = Ios.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AppsTab(s: AppsState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        IosButton(
            "Scan apps",
            {
                scope.launch {
                    s.groups = withContext(Dispatchers.IO) { AppScanner.scan(ctx) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        val ag = s.groups
        if (ag != null) {
            Caption(
                if (ag.isEmpty()) "No duplicate apps found."
                else "${ag.size} apps are installed more than once",
                Modifier.padding(top = 12.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                ag.forEach { group ->
                    item(key = "g" + group.first().pkg) {
                        IosCard(Modifier.padding(top = 12.dp)) {
                            group.forEachIndexed { i, app ->
                                if (i > 0) HorizontalDivider(color = Ios.Gray.copy(alpha = 0.3f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            app.label,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Ios.text()
                                        )
                                        Caption("${app.pkg}  ${app.version}")
                                    }
                                    Text(
                                        "Uninstall",
                                        color = Ios.Red,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable {
                                                ctx.startActivity(
                                                    Intent(
                                                        Intent.ACTION_DELETE,
                                                        Uri.parse("package:${app.pkg}")
                                                    )
                                                )
                                            }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityTab(s: SecState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        IosCard {
            Text(
                "Security check",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ios.text()
            )
            Caption(
                "Looks for spyware-like behaviour in your installed apps. It only runs when you tap the button.",
                Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            IosButton(
                if (s.scanning) "Checking..." else "Run security check",
                {
                    s.scanning = true
                    scope.launch {
                        s.report = withContext(Dispatchers.IO) { SecurityScanner.scan(ctx) }
                        s.scanning = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !s.scanning
            )
        }

        val r = s.report
        if (r != null) {
            Caption(
                if (r.apps.isEmpty()) "No risky apps found. ${r.checked} apps checked."
                else "${r.apps.size} apps need attention. ${r.checked} apps checked.",
                Modifier.padding(top = 12.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(r.apps, key = { it.pkg }) { app ->
                    val level = levelOf(app.score)
                    IosCard(Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                app.label,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ios.text(),
                                modifier = Modifier.weight(1f)
                            )
                            Pill(level.first, level.second)
                        }
                        Caption(app.pkg)
                        Spacer(Modifier.height(8.dp))
                        app.reasons.forEach {
                            Text("• $it", fontSize = 14.sp, color = Ios.text())
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IosButton(
                                "App info",
                                {
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.pkg}")
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                filled = false
                            )
                            IosButton(
                                "Uninstall",
                                {
                                    ctx.startActivity(
                                        Intent(
                                            Intent.ACTION_DELETE,
                                            Uri.parse("package:${app.pkg}")
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                color = Ios.Red
                            )
                        }
                    }
                }
            }
            Caption(
                "This checks behaviour, not virus signatures. It works best alongside Google Play Protect.",
                Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
