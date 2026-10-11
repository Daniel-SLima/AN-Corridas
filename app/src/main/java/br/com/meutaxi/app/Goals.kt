package br.com.meutaxi.app

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

/** Revenue targets are optional and are never confused with profit. */
data class GoalSettings(
    val dailyEnabled: Boolean = false,
    val dailyCents: Long = 0,
    val weeklyEnabled: Boolean = false,
    val weeklyCents: Long = 0,
    val friendlyMessages: Boolean = false
) {
    fun asJson(): JSONObject = JSONObject().apply {
        put("dailyEnabled",dailyEnabled); put("dailyCents",dailyCents)
        put("weeklyEnabled",weeklyEnabled); put("weeklyCents",weeklyCents)
        put("friendlyMessages",friendlyMessages)
    }
    companion object {
        fun fromJson(json: JSONObject): GoalSettings {
            val daily = json.optLong("dailyCents",0)
            val weekly = json.optLong("weeklyCents",0)
            require(daily in 0..100_000_000_000L && weekly in 0..100_000_000_000L) {
                "Meta fora do limite permitido"
            }
            val dailyOn = json.optBoolean("dailyEnabled",false)
            val weeklyOn = json.optBoolean("weeklyEnabled",false)
            require(!dailyOn || daily > 0) { "Meta diária inválida" }
            require(!weeklyOn || weekly > 0) { "Meta semanal inválida" }
            return GoalSettings(dailyOn,daily,weeklyOn,weekly,json.optBoolean("friendlyMessages",false))
        }
    }
}

class GoalsStore(context: Context) {
    private val prefs = context.getSharedPreferences("primo_goals",Context.MODE_PRIVATE)
    init {
        // v0.2 used true by default. Disable automatically once on upgrade; after
        // migration the driver can explicitly re-enable the messages at any time.
        if (!prefs.getBoolean("silent_default_v3_migrated", false)) {
            prefs.edit()
                .putBoolean("friendlyMessages",false)
                .putBoolean("silent_default_v3_migrated",true)
                .apply()
        }
    }
    fun read(): GoalSettings = GoalSettings(
        prefs.getBoolean("dailyEnabled",false), prefs.getLong("dailyCents",0),
        prefs.getBoolean("weeklyEnabled",false),prefs.getLong("weeklyCents",0),
        prefs.getBoolean("friendlyMessages",false)
    )
    fun save(settings: GoalSettings) {
        prefs.edit().putBoolean("dailyEnabled",settings.dailyEnabled)
            .putLong("dailyCents",settings.dailyCents)
            .putBoolean("weeklyEnabled",settings.weeklyEnabled)
            .putLong("weeklyCents",settings.weeklyCents)
            .putBoolean("friendlyMessages",settings.friendlyMessages).apply()
    }
}

object GoalMath {
    fun startOfWeek(date: LocalDate = LocalDate.now()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun received(db: EntriesDb, start: LocalDate, endExclusive: LocalDate): Long =
        db.between(start.atStartMillis(),endExclusive.atStartMillis())
            .asSequence().filter { it.kind == EntryTypes.RIDE }.sumOf { it.cents }

    fun progress(received: Long,target: Long): Int =
        if (target <= 0) 0 else min(100,((received.toDouble()/target)*100).toInt())

    fun remaining(received: Long,target: Long): Long = (target-received).coerceAtLeast(0)
}
