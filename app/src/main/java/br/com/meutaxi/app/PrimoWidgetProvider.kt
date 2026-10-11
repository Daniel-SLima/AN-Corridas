package br.com.meutaxi.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast

/** One tap on the launcher widget activates the bubble with its actions expanded. */
class PrimoWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_OPEN = "br.com.meutaxi.app.action.WIDGET_OPEN"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val intent = Intent(context, PrimoWidgetProvider::class.java).apply {
                action = ACTION_OPEN
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val pendingIntent = PendingIntent.getBroadcast(context,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName,R.layout.primo_widget).apply {
                setOnClickPendingIntent(R.id.primo_widget_root,pendingIntent)
            }
            manager.updateAppWidget(id,views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        if (intent.action != ACTION_OPEN) return
        if (!Settings.canDrawOverlays(context)) {
            openApp(context)
            return
        }
        try {
            val service = Intent(context,FloatingService::class.java).apply {
                action = FloatingService.ACTION_OPEN_MENU
            }
            // A deliberate launcher-widget tap is a user-initiated foreground-service start.
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service)
            else context.startService(service)
        } catch (_: Exception) {
            Toast.makeText(context,"Abra o Primo para ativar a bolinha.",Toast.LENGTH_SHORT).show()
            openApp(context)
        }
    }

    private fun openApp(context: Context) {
        val open = Intent(context,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(open)
    }
}
