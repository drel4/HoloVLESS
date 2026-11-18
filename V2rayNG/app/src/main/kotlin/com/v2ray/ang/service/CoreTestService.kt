package com.v2ray.ang.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import android.preference.PreferenceManager
import com.v2ray.ang.util.ProfileBatch
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.V2rayConfigUtil
import go.Seq
import kotlinx.coroutines.*
import libv2ray.DelayTester
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicInteger

/** Runs in the VPN process to share socket protection, not its running core. */
class CoreTestService : Service() {
    companion object {
        const val RESULT = 101
        const val CANCEL = "cancel"
        private const val NOTIFICATION = 2

        fun start(context: Context, guids: List<String>) {
            val intent = Intent(context, CoreTestService::class.java)
                    .putStringArrayListExtra("guids", ArrayList(guids))
                    .putExtra("concurrency", ProfileBatch.concurrency(
                            PreferenceManager.getDefaultSharedPreferences(context)
                                    .getString(ProfileBatch.PREFERENCE, null)))
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batch: Job? = null
    private var tester: DelayTester? = null

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        // Installs the shared protected system dialer once, including when VPN is stopped.
        V2RayServiceManager.v2rayPoint
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        tester?.cancel()
        batch?.cancel()
        if (intent?.action == CANCEL) {
            report("", 0, 0, 0, true)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val guids = intent?.getStringArrayListExtra("guids").orEmpty().distinct()
        val currentTester = Libv2ray.newDelayTester()
        tester = currentTester
        val concurrency = ProfileBatch.concurrency(intent?.getIntExtra("concurrency",
                ProfileBatch.DEFAULT_CONCURRENCY)?.toString())
        report("", 0, guids.size, 0, false)
        batch = scope.launch {
            val completed = AtomicInteger()
            val working = AtomicInteger()
            try {
                ProfileBatch.run(guids, concurrency) { guid ->
                    ensureActive()
                    if (MmkvManager.hasServerConfig(guid)) {
                        MmkvManager.encodeServerTestDelayMillis(guid, 0)
                    }
                    val delay = try {
                        val config = V2rayConfigUtil.getV2rayConfig(this@CoreTestService, guid)
                        if (!config.status) -1L else currentTester.measure(config.content, "https://myip.wtf").coerceAtLeast(1L)
                    } catch (_: Exception) { -1L }
                    ensureActive()
                    if (delay >= 0) working.incrementAndGet()
                    if (MmkvManager.hasServerConfig(guid)) {
                        MmkvManager.encodeServerTestDelayMillis(guid, delay)
                    }
                    report(guid, completed.incrementAndGet(), guids.size, working.get(), false)
                }
                ensureActive()
                report("", completed.get(), guids.size, working.get(), true)
                withContext(Dispatchers.Main) { stopSelf(startId) }
            } finally {
                currentTester.cancel()
            }
        }
        return START_NOT_STICKY
    }

    private fun report(guid: String, completed: Int, total: Int, working: Int, done: Boolean) {
        sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_ACTIVITY).setPackage(packageName)
                .putExtra("key", RESULT).putExtra("guid", guid).putExtra("completed", completed)
                .putExtra("total", total).putExtra("working", working).putExtra("done", done))
    }

    private fun showNotification() {
        val channel = "holovless-tests"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(NotificationChannel(channel, getString(R.string.title_check_profiles),
                            NotificationManager.IMPORTANCE_LOW))
        }
        val cancel = PendingIntent.getService(this, 2,
                Intent(this, CoreTestService::class.java).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        startForeground(NOTIFICATION, NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_v).setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.connection_test_testing)).setOngoing(true)
                .addAction(R.drawable.ic_close_grey_800_24dp, getString(R.string.cancel_checks), cancel).build())
    }

    override fun onDestroy() {
        tester?.cancel()
        scope.cancel()
        stopForeground(true)
        super.onDestroy()
    }
}
