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
    private var bubbleLabel: TextView? = null
    private var menu: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var confirming = false
    private var confirmationToken = 0
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
                bubbleLabel = this
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
                .coerceIn(0,(display.widthPixels-diameter).coerceAtLeast(0))
            y = settings.getInt("y", Ui.dp(this@FloatingService,210))
                .coerceIn(Ui.dp(this@FloatingService,24),
                    (display.heightPixels-diameter-Ui.dp(this@FloatingService,72))
                        .coerceAtLeast(Ui.dp(this@FloatingService,24)))
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
                            lp.y = (initialY+dy).coerceIn(
                                Ui.dp(this@FloatingService,24),
                                (display.heightPixels-diameter-Ui.dp(this@FloatingService,72))
                                    .coerceAtLeast(Ui.dp(this@FloatingService,24)))
                            safeUpdate(v,lp)
                            updateMenuPosition()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged && !confirming) toggleMenu()
                        else if (dragged) {
                            settings.edit().putInt("x",lp.x).putInt("y",lp.y).apply()
                            updateMenuPosition()
                        }
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
        val lp = params(boxWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            manager.addView(panel,lp)
            menu = panel
            menuParams = lp
            updateMenuPosition()
            panel.alpha = 0f
            panel.scaleX = 0.95f
            panel.scaleY = 0.95f
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start()
        } catch (_: Exception) {
            menu = null
            menuParams = null
        }
    }

    /** Keep the expanded actions attached to the moving bubble and on-screen. */
    private fun updateMenuPosition() {
        val panel = menu ?: return
        val lp = menuParams ?: return
        val anchor = bubbleParams ?: return
        val display = resources.displayMetrics
        val diameter = Ui.dp(this,58)
        val panelWidth = Ui.dp(this,205)
        val panelHeight = Ui.dp(this,212)
        val gap = Ui.dp(this,10)
        val safeTop = Ui.dp(this,24)
        val safeBottom = Ui.dp(this,72)
        lp.x = (if (anchor.x + diameter / 2 >= display.widthPixels / 2) {
            anchor.x + diameter - panelWidth
        } else {
            anchor.x
        }).coerceIn(0,(display.widthPixels-panelWidth).coerceAtLeast(0))
        val above = anchor.y - panelHeight - gap
        val below = anchor.y + diameter + gap
        val bottomLimit = (display.heightPixels - panelHeight - safeBottom)
            .coerceAtLeast(safeTop)
        lp.y = (if (above >= safeTop) above else below)
            .coerceIn(safeTop,bottomLimit)
        safeUpdate(panel,lp)
    }

    /** The + itself briefly becomes a green check only after the DB confirms save. */
    private fun showSavedOnBubble() {
        val view = bubble ?: return
        confirmationToken += 1
        val currentToken = confirmationToken
        confirming = true
        view.background = Ui.rounded(Ui.GREEN,this,50)
        bubbleLabel?.apply {
            text = "✓"
            setTextColor(Color.WHITE)
            textSize = 32f
            contentDescription = "Registro salvo com sucesso"
        }
        view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(130L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
            }.start()
        view.postDelayed({
            if (bubble === view && confirmationToken == currentToken) {
                view.background = Ui.rounded(Ui.YELLOW,this,50)
                bubbleLabel?.apply {
                    text = "+"
                    setTextColor(Ui.DARK)
                    textSize = 39f
                    contentDescription = "Abrir registro rápido do Primo"
                }
                confirming = false
            }
        }, 1100L)
    }

    private fun openForm(kind: String) {
        closeForm()
        val panel = QuickEntryForm.create(this,kind,
            onSave = { entry ->
                try {
                    db.save(entry)
                    closeMenu()
                    showSavedOnBubble()
                    true
                } catch (e: Exception) {
                    Toast.makeText(this,"Erro ao salvar: ${e.message}",Toast.LENGTH_LONG).show()
                    false
                }
            },
            onCancel = { closeForm(); closeMenu() },
            onSaved = { closeForm(); closeMenu() })
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
    private fun closeMenu() {
        menu?.let { it.animate().cancel(); safelyRemove(it) }
        menu = null
        menuParams = null
    }
    private fun closeForm() { form?.let { safelyRemove(it) }; form = null }
    private fun safelyRemove(view: View) { try { manager.removeView(view) } catch (_: Exception) {} }

    override fun onDestroy() {
        confirmationToken += 1
        closeMenu(); closeForm()
        bubble?.let { it.animate().cancel(); safelyRemove(it) }; bubble = null
        bubbleLabel = null
        getSharedPreferences("state",MODE_PRIVATE).edit().putBoolean("overlay_running",false).apply()
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}
