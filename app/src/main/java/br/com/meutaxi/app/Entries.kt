package br.com.meutaxi.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Entry(
    val id: Long = 0,
    val kind: String,
    val cents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "Rua",
    val payment: String = "Dinheiro",
    val note: String = ""
)

object EntryTypes {
    const val RIDE = "corrida"
    const val FUEL = "gasolina"
    const val REPAIR = "mecanico"
    fun name(kind: String): String = when (kind) {
        RIDE -> "Corrida"
        FUEL -> "Gasolina"
        REPAIR -> "Mecânico"
        else -> kind
    }
}

object Money {
    fun parse(text: String): Long? {
        val cleaned = text.trim().removePrefix("R$").trim().replace('.', ',')
        if (!cleaned.matches(Regex("[0-9]{1,12}(,[0-9]{1,2})?"))) return null
        return try {
            val cents = BigDecimal(cleaned.replace(',', '.'))
                .movePointRight(2).longValueExact()
            cents.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    fun show(cents: Long): String = NumberFormat.getCurrencyInstance(
        Locale("pt", "BR")
    ).format(BigDecimal.valueOf(cents, 2))
}

class EntriesDb(context: Context) : SQLiteOpenHelper(context, "meutaxi.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                cents INTEGER NOT NULL CHECK(cents > 0),
                created_at INTEGER NOT NULL,
                source TEXT NOT NULL,
                payment TEXT NOT NULL,
                note TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_entries_date ON entries(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1. Future migrations must preserve existing financial records.
    }

    private fun values(entry: Entry) = ContentValues().apply {
        put("kind", entry.kind)
        put("cents", entry.cents)
        put("created_at", entry.createdAt)
        put("source", entry.source)
        put("payment", entry.payment)
        put("note", entry.note)
    }

    fun save(entry: Entry): Long {
        require(entry.cents > 0)
        require(entry.kind in setOf(EntryTypes.RIDE, EntryTypes.FUEL, EntryTypes.REPAIR))
        val db = writableDatabase
        return if (entry.id == 0L) {
            db.insertOrThrow("entries", null, values(entry))
        } else {
            val changed=db.update("entries", values(entry), "id=?", arrayOf(entry.id.toString()))
            check(changed==1) { "Lançamento não encontrado para editar" }
            entry.id
        }
    }

    fun delete(id: Long):Boolean {
        return writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))==1
    }

    fun between(start: Long, endExclusive: Long): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.query(
            "entries", null, "created_at >= ? AND created_at < ?",
            arrayOf(start.toString(), endExclusive.toString()),
            null, null, "created_at DESC, id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Entry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    kind = cursor.getString(cursor.getColumnIndexOrThrow("kind")),
                    cents = cursor.getLong(cursor.getColumnIndexOrThrow("cents")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                    payment = cursor.getString(cursor.getColumnIndexOrThrow("payment")),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note"))
                ))
            }
        }
        return result
    }

    fun all(): List<Entry> = between(0L, Long.MAX_VALUE)

    fun backupJson(): String {
        val array = JSONArray()
        all().forEach { e ->
            array.put(JSONObject().apply {
                put("kind", e.kind)
                put("cents", e.cents)
                put("createdAt", e.createdAt)
                put("source", e.source)
                put("payment", e.payment)
                put("note", e.note)
            })
        }
        return JSONObject().put("format", "meutaxi-backup-v1")
            .put("entries", array).toString(2)
    }

    /** Validates the complete backup before replacing any database entries. */
    fun restoreJson(json: String): Int {
        val root = JSONObject(json)
        require(root.getString("format") == "meutaxi-backup-v1") { "Formato de backup desconhecido" }
        val array = root.getJSONArray("entries")
        require(array.length() <= 200_000) { "Backup muito grande" }
        val input = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val kind = obj.getString("kind")
            val cents = obj.getLong("cents")
            val time = obj.getLong("createdAt")
            require(kind in setOf(EntryTypes.RIDE, EntryTypes.FUEL, EntryTypes.REPAIR))
            require(cents > 0 && time > 0)
            Entry(kind = kind, cents = cents, createdAt = time,
                source = obj.optString("source", "Rua"),
                payment = obj.optString("payment", "Dinheiro"),
                note = obj.optString("note", ""))
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", null, null)
            input.forEach { db.insertOrThrow("entries", null, values(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return input.size
    }
}

fun LocalDate.atStartMillis(): Long = atStartOfDay(ZoneId.systemDefault())
    .toInstant().toEpochMilli()
