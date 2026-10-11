package br.com.meutaxi.app

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReportItem(val label: String, val amount: Long, val count: Int)

data class ClosingTotals(
    val rides: Int,
    val received: Long,
    val fuel: Long,
    val fuelCount: Int,
    val repair: Long,
    val repairCount: Int,
    val bySource: List<ReportItem>,
    val byPayment: List<ReportItem>
) {
    val expenses: Long get() = fuel + repair
    val balance: Long get() = received - expenses
    val averageRide: Long get() = if (rides == 0) 0L else received / rides
}

/** Read-only financial summaries: recorded revenue, not net profit. */
object ClosingReport {
    private fun summarize(rides: List<Entry>, key: (Entry) -> String, preferred: List<String>): List<ReportItem> {
        val groups = rides.groupBy { key(it).trim().ifBlank { "Não informado" } }
        val labels = preferred.filter { groups.containsKey(it) } +
            groups.keys.filterNot { it in preferred }.sorted()
        return labels.map { label ->
            val entries = groups[label].orEmpty()
            ReportItem(label, entries.sumOf { it.cents }, entries.size)
        }
    }

    fun calculate(entries: List<Entry>): ClosingTotals {
        val rides = entries.filter { it.kind == EntryTypes.RIDE }
        val fuel = entries.filter { it.kind == EntryTypes.FUEL }
        val repair = entries.filter { it.kind == EntryTypes.REPAIR }
        return ClosingTotals(
            rides.size, rides.sumOf { it.cents },
            fuel.sumOf { it.cents }, fuel.size,
            repair.sumOf { it.cents }, repair.size,
            summarize(rides, { it.source }, listOf("Rua","99","Uber Taxi")),
            summarize(rides, { it.payment }, listOf("Dinheiro","Pix","Cartão","App"))
        )
    }

    /** Escape spreadsheet formula injection from driver-provided text. */
    private fun csvText(value: String): String {
        val normalized = value.replace('\r',' ').replace('\n',' ').replace('\t',' ')
        val dangerous = normalized.trimStart().firstOrNull() in listOf('=','+','-','@')
        val escaped = (if (dangerous) "'" else "") + normalized
        return "\"" + escaped.replace("\"","\"\"") + "\""
    }

    fun csv(entries: List<Entry>): String {
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt","BR"))
        val hour = DateTimeFormatter.ofPattern("HH:mm", Locale("pt","BR"))
        val result = StringBuilder("\uFEFFData;Hora;Categoria;Origem;Pagamento;Descrição;Valor (R$)\r\n")
        entries.sortedWith(compareBy<Entry> { it.createdAt }.thenBy { it.id }).forEach { entry ->
            val time = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
            val signed = if (entry.kind == EntryTypes.RIDE) entry.cents else -entry.cents
            // This number is app-generated and cannot contain a formula.
            val money = BigDecimal.valueOf(signed,2).toPlainString().replace('.',',')
            result.append(listOf(
                csvText(time.format(date)), csvText(time.format(hour)),
                csvText(EntryTypes.name(entry.kind)),
                csvText(if (entry.kind == EntryTypes.RIDE) entry.source else ""),
                csvText(if (entry.kind == EntryTypes.RIDE) entry.payment else ""),
                csvText(entry.note), money
            ).joinToString(";"))
            result.append("\r\n")
        }
        return result.toString()
    }
}
