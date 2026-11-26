package com.therealjidenna.machak

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConnStatus(
    val source: String,
    val pct: Int,
    val usb: String,
    val devices: List<String>,
    val fileTransfer: Boolean,
    val debugging: Boolean
) {
    val power: String
        get() = if (pct >= 0) "$source, $pct%" else source
}

object Conn {

    private const val USB_STATE = "android.hardware.usb.action.USB_STATE"

    fun filter(): IntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(USB_STATE)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    }

    fun read(ctx: Context): ConnStatus {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1

        val source = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "Wall charger"
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Wireless charger"
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB port (computer or power bank)"
            plugged != 0 -> "Dock"
            else -> "Not plugged in"
        }

        var usb = "No USB data link"
        var transfer = false
        var debug = false
        val u = ctx.registerReceiver(null, IntentFilter(USB_STATE))
        if (u != null && u.getBooleanExtra("connected", false)) {
            val parts = mutableListOf<String>()
            val mtp = u.getBooleanExtra("mtp", false)
            val ptp = u.getBooleanExtra("ptp", false)
            val mass = u.getBooleanExtra("mass_storage", false)
            if (mtp) parts.add("File transfer (MTP)")
            if (ptp) parts.add("Photo transfer (PTP)")
            if (mass) parts.add("Mass storage")
            if (u.getBooleanExtra("rndis", false)) parts.add("USB tethering")
            if (u.getBooleanExtra("midi", false)) parts.add("MIDI")
            if (u.getBooleanExtra("adb", false)) {
                parts.add("USB debugging")
                debug = true
            }
            transfer = mtp || ptp || mass
            usb = if (parts.isEmpty()) "Connected, charge only (no data)" else parts.joinToString(" + ")
        }

        val devices = try {
            val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            um.deviceList.values.map { it.productName ?: it.deviceName }
        } catch (e: Exception) {
            emptyList()
        }

        return ConnStatus(source, pct, usb, devices, transfer, debug)
    }

    fun logChanges(ctx: Context, prev: ConnStatus?, now: ConnStatus) {
        if (prev == null) return
        if (prev.source != now.source) ConnLog.add(ctx, "Power: ${now.source}")
        if (prev.usb != now.usb) ConnLog.add(ctx, "USB: ${now.usb}")
        if (prev.devices != now.devices) {
            ConnLog.add(
                ctx,
                if (now.devices.isEmpty()) "USB device removed"
                else "USB device: ${now.devices.joinToString()}"
            )
        }
        if (now.fileTransfer && !prev.fileTransfer) {
            alert(ctx, "File transfer connection", now.usb)
        }
        if (now.debugging && !prev.debugging) {
            alert(ctx, "USB debugging connected", now.usb)
        }
    }
}

object ConnLog {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("machak_conn", Context.MODE_PRIVATE)

    fun read(ctx: Context): List<String> =
        (prefs(ctx).getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun add(ctx: Context, text: String) {
        val stamp = SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date())
        val all = (listOf("$stamp  $text") + read(ctx)).take(60)
        prefs(ctx).edit().putString("log", all.joinToString("\n")).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove("log").apply()
    }
}

fun isGuardOn(ctx: Context): Boolean =
    ctx.getSharedPreferences("machak_conn", Context.MODE_PRIVATE).getBoolean("guard", false)

fun setGuard(ctx: Context, on: Boolean) {
    ctx.getSharedPreferences("machak_conn", Context.MODE_PRIVATE)
        .edit().putBoolean("guard", on).apply()
    val i = Intent(ctx, ConnectionService::class.java)
    if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
}

private fun openApp(ctx: Context): PendingIntent =
    PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

private fun alert(ctx: Context, title: String, text: String) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
        NotificationChannel("machak_alert", "Connection alerts", NotificationManager.IMPORTANCE_HIGH)
    )
    val n = Notification.Builder(ctx, "machak_alert")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openApp(ctx))
        .setAutoCancel(true)
        .build()
    nm.notify(2, n)
    ConnLog.add(ctx, "ALERT: $title")
}

class ConnectionService : Service() {

    private var last: ConnStatus? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = Conn.read(c)
            Conn.logChanges(c, last, now)
            last = now
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("machak_guard", "Connection watch", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "machak_guard")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Machak is watching connections")
            .setContentText("Charger and USB changes are logged")
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        last = Conn.read(this)
        ContextCompat.registerReceiver(
            this, receiver, Conn.filter(), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && isGuardOn(ctx)) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConnectionService::class.java))
        }
    }
}

class ConnState {
    var status by mutableStateOf<ConnStatus?>(null)
    var log by mutableStateOf(listOf<String>())
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Ios.Gray, fontSize = 15.sp)
        Text(
            value,
            color = Ios.text(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Composable
fun ConnectionsTab(s: ConnState, onAskNotifications: () -> Unit) {
    val ctx = LocalContext.current
    var guard by remember { mutableStateOf(isGuardOn(ctx)) }

    DisposableEffect(Unit) {
        val refresh = {
            val prev = s.status
            val now = Conn.read(ctx)
            s.status = now
            if (!isGuardOn(ctx)) Conn.logChanges(ctx, prev, now)
            s.log = ConnLog.read(ctx)
        }
        refresh()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                refresh()
            }
        }
        ContextCompat.registerReceiver(
            ctx, receiver, Conn.filter(), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    val st = s.status

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Right now",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ios.text()
                )
                if (st != null && st.fileTransfer) {
                    Pill("File transfer active", Ios.Red)
                } else if (st != null && st.debugging) {
                    Pill("USB debugging", Ios.Orange)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (st != null) {
                StatusRow("Power", st.power)
                HorizontalDivider(color = Ios.Gray.copy(alpha = 0.3f))
                StatusRow("USB data", st.usb)
                HorizontalDivider(color = Ios.Gray.copy(alpha = 0.3f))
                StatusRow(
                    "USB devices",
                    if (st.devices.isEmpty()) "None" else st.devices.joinToString()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Always-on alerts",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ios.text()
                    )
                    Caption("Warns you when a file-transfer connection starts, even when the app is closed")
                }
                IosSwitch(guard) {
                    guard = it
                    if (it) onAskNotifications()
                    setGuard(ctx, it)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        IosCard {
            Text(
                "History",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ios.text()
            )
            Spacer(Modifier.height(8.dp))
            if (s.log.isEmpty()) {
                Caption("Nothing yet. Plug in a charger or a cable to see it here.")
            } else {
                s.log.forEach {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = Ios.text(),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IosButton(
                    "Refresh",
                    {
                        s.status = Conn.read(ctx)
                        s.log = ConnLog.read(ctx)
                    },
                    modifier = Modifier.weight(1f),
                    filled = false
                )
                IosButton(
                    "Clear log",
                    {
                        ConnLog.clear(ctx)
                        s.log = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                    filled = false,
                    color = Ios.Red
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
