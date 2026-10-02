package fan.lightningroulette.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fan.lightningroulette.R
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Kartı ekran sınırları içinde tutar (sürükleme / döndürme sonrası tuşlar ekran dışında kalmasın). */
fun clampOverlayPos(x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int): IntArray {
    val maxX = (screenW - w).coerceAtLeast(0)
    val maxY = (screenH - h).coerceAtLeast(0)
    return intArrayOf(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
}

/** Ön plan servisi: ekran üstü sonuç girişi + kilitli tahmin. Bildirim: durum + kilitli tahmin kimliği, [Gizle] [Kapat]. */
class OverlayService : LifecycleService() {

    companion object {
        private const val CHANNEL = "lr_overlay"
        private const val NOTIF_ID = 4201
        const val ACTION_HIDE = "fan.lightningroulette.overlay.HIDE"
        const val ACTION_CLOSE = "fan.lightningroulette.overlay.CLOSE"
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun canDraw(ctx: Context) = AndroidSettings.canDrawOverlays(ctx)
        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }

    private lateinit var wm: WindowManager
    private var view: OverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var builtKey = ""
    private var lastCode = ""

    private fun screenSize(): IntArray {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground("hazırlanıyor")
        _running.value = true
        lifecycleScope.launch {
            combine(Engine.ui, Engine.settings.version) { ui, _ -> ui }.collect { ui ->
                val s = Engine.settings
                val key = s.ovMode + "|" + s.ovScale + "|" + s.ovAlpha + "|" + s.ovNext + "|" + s.ovKeyboard + "|" + s.ovWarn + "|" + s.ovVibrate + "|" + s.tableMask
                if (view == null || key != builtKey) buildView(key)
                view?.update(ui)
                val code = ui.pred?.code ?: "kilit yok"
                if (code != lastCode) { lastCode = code; startAsForeground(code) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_HIDE -> view?.toggleMinimize()
            ACTION_CLOSE -> { Engine.settings.ovEnabled = false; stopSelf() }
        }
        return START_STICKY
    }

    private fun startAsForeground(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(a: String, rc: Int) = PendingIntent.getService(this, rc, Intent(this, OverlayService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_running) + " · " + status)
            .setSmallIcon(R.drawable.ic_lightning)
            .setContentIntent(open)
            .addAction(0, "Gizle", act(ACTION_HIDE, 1))
            .addAction(0, "Kapat", act(ACTION_CLOSE, 2))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun clampIntoScreen() {
        val v = view ?: return
        val p = params ?: return
        val scr = screenSize()
        val w = if (v.width > 0) v.width else v.measuredWidth
        val h = if (v.height > 0) v.height else v.measuredHeight
        if (w <= 0 || h <= 0) return
        val c = clampOverlayPos(p.x, p.y, w, h, scr[0], scr[1])
        if (c[0] != p.x || c[1] != p.y) { p.x = c[0]; p.y = c[1]; try { wm.updateViewLayout(v, p) } catch (_: Exception) { } }
    }

    private fun buildView(key: String) {
        view?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        val s = Engine.settings
        val v = OverlayView(this, s,
            onDrag = { dx, dy ->
                val p = params; val cur = view
                if (p != null && cur != null) {
                    val scr = screenSize()
                    val w = if (cur.width > 0) cur.width else cur.measuredWidth
                    val h = if (cur.height > 0) cur.height else cur.measuredHeight
                    val c = clampOverlayPos(p.x + dx, p.y + dy, w, h, scr[0], scr[1])
                    p.x = c[0]; p.y = c[1]
                    try { wm.updateViewLayout(cur, p) } catch (_: Exception) { }
                }
            },
            onDragEnd = {
                val p = params; val cur = view
                if (p != null && cur != null) {
                    val scr = screenSize()
                    val w = if (cur.width > 0) cur.width else cur.measuredWidth
                    if (s.ovSnap) { p.x = if (p.x + w / 2 < scr[0] / 2) 0 else (scr[0] - w).coerceAtLeast(0); try { wm.updateViewLayout(cur, p) } catch (_: Exception) { } }
                    s.ovX = p.x; s.ovY = p.y
                }
            },
            onClose = { Engine.settings.ovEnabled = false; stopSelf() },
            onMenu = { anchor -> showMenu(anchor) }
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = if (s.ovX >= 0) s.ovX else 40; y = if (s.ovY >= 0) s.ovY else 240 }
        try {
            wm.addView(v, p)
            view = v; params = p; builtKey = key
            v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clampIntoScreen() }
            v.post { clampIntoScreen() }
        } catch (e: Exception) {
            Engine.store?.log("ERROR", null, "OVERLAY_ADD_FAILED", e.message ?: "")
            Engine.settings.ovEnabled = false
            stopSelf()
        }
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Kompakt görünüme geç")
        m.menu.add(0, 2, 1, "Dikey görünüme geç")
        m.menu.add(0, 3, 2, "Konumu sıfırla")
        m.menu.add(0, 4, 3, "Ayarlar")
        m.menu.add(0, 5, 4, "Overlay’i kapat")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> Engine.settings.ovMode = "compact"
                2 -> Engine.settings.ovMode = "vertical"
                3 -> { Engine.settings.ovX = -1; Engine.settings.ovY = -1; params?.let { p -> p.x = 40; p.y = 240; view?.let { v -> try { wm.updateViewLayout(v, p) } catch (_: Exception) { } } } }
                4 -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                5 -> { Engine.settings.ovEnabled = false; stopSelf() }
            }
            true
        }
        try { m.show() } catch (_: Exception) { }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.let { v -> v.requestLayout(); v.post { clampIntoScreen() } }
    }

    override fun onDestroy() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        view = null
        _running.value = false
        try { Engine.py?.save() } catch (_: Exception) { }
        super.onDestroy()
    }
}
