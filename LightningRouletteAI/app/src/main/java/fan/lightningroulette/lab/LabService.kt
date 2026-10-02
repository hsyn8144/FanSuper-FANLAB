package fan.lightningroulette.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fan.lightningroulette.R
import fan.lightningroulette.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * LAB ön plan servisi: arka plan deneyleri sürerken süreç öldürülmesin. Bildirim: "LAB çalışıyor %63 · İptal".
 * Bildirim izni yoksa ilerleme yalnızca uygulama içinde görünür; işlev bozulmaz.
 */
class LabService : LifecycleService() {
    companion object {
        private const val CHANNEL = "lr_lab"
        private const val NOTIF_ID = 4202
        const val ACTION_CANCEL = "fan.lightningroulette.lab.CANCEL"
    }
    private var seenRunning = false

    override fun onCreate() {
        super.onCreate()
        show("LAB hazırlanıyor", 0)
        lifecycleScope.launch {
            LabManager.ui.collect { u ->
                if (u.running) { seenRunning = true; show("LAB çalışıyor %${u.progress} · ${u.jobCode.takeLast(7)}", u.progress) }
                else if (seenRunning) { stopSelf() }
            }
        }
        lifecycleScope.launch { kotlinx.coroutines.delay(25000); if (!seenRunning) stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL) LabManager.cancelCurrent()
        return START_NOT_STICKY
    }

    private fun show(text: String, progress: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.lab_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 3, Intent(this, LabService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name) + " · LAB")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lightning)
            .setContentIntent(open)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .addAction(0, "İptal", cancel)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }
}
