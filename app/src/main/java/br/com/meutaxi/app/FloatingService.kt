package br.com.meutaxi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.min

/** User-activated foreground service: a movable quick-entry overlay for other apps. */
class FloatingService : Service() {
    companion object {
        const val ACTION_STOP = "br.com.meutaxi.app.STOP_OVERLAY"
        private const val CHANNEL = "meutaxi_overlay"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var manager: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var form: View? = null
    private lateinit var db: EntriesDb

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        db = EntriesDb(this)
        createNotificationChannel()
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Toast.makeText(this,"Erro ao iniciar a bolinha",Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) {
            try { showBubble() } catch (_: Exception) { stopSelf() }
        }
        return START_NOT_STICKY // Do not resurrect the overlay unexpectedly after process death.
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL,"Bolinha do Primo",NotificationManager.IMPORTANCE_LOW)
        channel.description = "Mantém o botão de registro de corridas disponível"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this,0,
            Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this,1,
            Intent(this,FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Primo está ativo")
            .setContentText("Toque na bolinha para registrar uma corrida")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_delete,"Desligar bolinha",stop).build())
            .setOngoing(true)
            .build()
    }

    private fun params(width: Int, height: Int, flags: Int) =
        WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,PixelFormat.TRANSLUCENT)

    private fun showBubble() {
        val diameter = Ui.dp(this,58)
        val button = FrameLayout(this).apply {
            background = Ui.rounded(Ui.YELLOW,this@FloatingService,50)
            elevation = Ui.dp(this@FloatingService,6).toFloat()
            addView(TextView(this@FloatingService).apply {
                text = "+"
                setTextColor(Ui.DARK)
                textSize = 39f
                gravity = Gravity.CENTER
                contentDescription = "Abrir registro rápido do Primo"
            },FrameLayout.LayoutParams(-1,-1))
        }
        val display = resources.displayMetrics
        val settings = getSharedPreferences("bubble",MODE_PRIVATE)
        val lp = params(diameter,diameter, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.getInt("x", display.widthPixels - diameter - Ui.dp(this@FloatingService,12))
            y = settings.getInt("y", Ui.dp(this@FloatingService,210))
        }
        bubble = button
        bubbleParams = lp
        button.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var initialX = 0
            var initialY = 0
            var dragged = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        initialX = lp.x; initialY = lp.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX-downX).toInt()
                        val dy = (event.rawY-downY).toInt()
                        if (abs(dx) > Ui.dp(this@FloatingService,8) || abs(dy) > Ui.dp(this@FloatingService,8)) {
                            dragged = true
                        }
                        if (dragged) {
                            lp.x = (initialX+dx).coerceIn(0, (display.widthPixels-diameter).coerceAtLeast(0))
                            lp.y = (initialY+dy).coerceIn(0, (display.heightPixels-diameter).coerceAtLeast(0))
                            safeUpdate(v,lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) toggleMenu()
                        else settings.edit().putInt("x",lp.x).putInt("y",lp.y).apply()
                        return true
                    }
                }
                return false
            }
        })
        manager.addView(button,lp)
        getSharedPreferences("state",MODE_PRIVATE).edit().putBoolean("overlay_running",true).apply()
    }

    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        closeForm()
        val panel = Ui.column(this,10).apply {
            background = Ui.rounded(Color.WHITE,this@FloatingService)
            elevation = Ui.dp(this@FloatingService,8).toFloat()
        }
        listOf(
            Triple("🚕 Corrida",EntryTypes.RIDE,Ui.BLUE),
            Triple("⛽ Gasolina",EntryTypes.FUEL,Ui.YELLOW),
            Triple("🔧 Mecânico",EntryTypes.REPAIR,Ui.DARK)
        ).forEach { (label,kind,color) ->
            panel.addView(Ui.button(this,label,color).apply {
                setOnClickListener { closeMenu(); openForm(kind) }
            },LinearLayout.LayoutParams(-1,Ui.dp(this,52)).apply {
                bottomMargin = Ui.dp(this@FloatingService,5)
            })
        }
        val boxWidth = Ui.dp(this,205)
        val boxHeight = Ui.dp(this,212)
        val display = resources.displayMetrics
        val current = bubbleParams
        val lp = params(boxWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (current?.x ?: 10).coerceIn(0,(display.widthPixels-boxWidth).coerceAtLeast(0))
            val proposedY = (current?.y ?: 100) - boxHeight - Ui.dp(this@FloatingService,10)
            y = if (proposedY > 30) proposedY else ((current?.y ?: 100) + Ui.dp(this@FloatingService,65))
        }
        try {
            manager.addView(panel,lp)
            menu = panel
        } catch (_: Exception) { menu = null }
    }

    private fun openForm(kind: String) {
        closeForm()
        val panel = QuickEntryForm.create(this,kind,
            onSave = { entry ->
                try {
                    db.save(entry)
                    closeForm()
                    if (GoalsStore(this).read().friendlyMessages) {
                        val message = when(kind) {
                            EntryTypes.RIDE -> "Boa, primo! Mais uma pra conta."
                            EntryTypes.FUEL -> "Abastecimento anotado. Tá na conta!"
                            else -> "Manutenção anotada, primo!"
                        }
                        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this,"Erro ao salvar: ${e.message}",Toast.LENGTH_LONG).show()
                }
            }, onCancel = { closeForm() })
        val availableWidth = resources.displayMetrics.widthPixels - Ui.dp(this,24)
        val lp = params(min(availableWidth,Ui.dp(this,350)),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = Ui.dp(this@FloatingService,64)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        try {
            manager.addView(panel,lp)
            form = panel
        } catch (_: Exception) {
            Toast.makeText(this,"Não foi possível abrir o formulário",Toast.LENGTH_SHORT).show()
        }
    }

    private fun safeUpdate(view: View, lp: WindowManager.LayoutParams) {
        try { manager.updateViewLayout(view,lp) } catch (_: Exception) { }
    }
    private fun closeMenu() { menu?.let { safelyRemove(it) }; menu = null }
    private fun closeForm() { form?.let { safelyRemove(it) }; form = null }
    private fun safelyRemove(view: View) { try { manager.removeView(view) } catch (_: Exception) {} }

    override fun onDestroy() {
        closeMenu(); closeForm()
        bubble?.let { safelyRemove(it) }; bubble = null
        getSharedPreferences("state",MODE_PRIVATE).edit().putBoolean("overlay_running",false).apply()
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}
