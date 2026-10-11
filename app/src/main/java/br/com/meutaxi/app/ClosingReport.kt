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

object ClosingReport {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt","BR"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale("pt","BR"))
    private val filenameDate = DateTimeFormatter.ISO_LOCAL_DATE

    private fun summarize(rides: List<Entry>, key: (Entry)->String, preferred:List<String>):List<ReportItem> {
        val groups=rides.groupBy { key(it).trim().ifBlank { "Não informado" } }
        val names=preferred.filter { groups.containsKey(it) } +
            groups.keys.filterNot { it in preferred }.sorted()
        return names.map { label ->
            val matches=groups[label].orEmpty()
            ReportItem(label,matches.sumOf { it.cents },matches.size)
        }
    }

    fun calculate(entries:List<Entry>):ClosingTotals {
        val rides=entries.filter { it.kind==EntryTypes.RIDE }
        val fuel=entries.filter { it.kind==EntryTypes.FUEL }
        val repair=entries.filter { it.kind==EntryTypes.REPAIR }
        return ClosingTotals(
            rides.size,rides.sumOf { it.cents },
            fuel.sumOf { it.cents },fuel.size,
            repair.sumOf { it.cents },repair.size,
            summarize(rides,{it.source},listOf("Rua","99","Uber Taxi")),
            summarize(rides,{it.payment},listOf("Dinheiro","Pix","Cartão","App"))
        )
    }

    /** ISO range is based on local dates and an exclusive end timestamp. */
    fun fileName(startInclusive:Long,endExclusive:Long):String {
        require(startInclusive < endExclusive)
        val first=Instant.ofEpochMilli(startInclusive).atZone(ZoneId.systemDefault()).toLocalDate()
        val last=Instant.ofEpochMilli(endExclusive-1).atZone(ZoneId.systemDefault()).toLocalDate()
        return "primo-fechamento-${first.format(filenameDate)}_a_${last.format(filenameDate)}.csv"
    }

    /**
     * Spreadsheet viewers differ on CSV text encoding (especially when shared).
     * Export ASCII transliteration only: it preserves readable Portuguese words
     * in every legacy/UTF-8 parser. Original descriptions remain untouched in DB.
     */
    private fun plainCsv(value:String):String {
        val normalized=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return normalized.map { c ->
            when {
                c=='ç' || c=='Ç' -> 'c'
                c.code in 32..126 -> c
                else -> '?'
            }
        }.joinToString("")
    }

    /** User-supplied text is escaped and cannot become a spreadsheet formula. */
    private fun escape(value:String):String {
        val normalized=plainCsv(value.replace('\r',' ').replace('\n',' ').replace('\t',' '))
        val first=normalized.trimStart().firstOrNull()
        val safe=if(first=='=' || first=='+' || first=='-' || first=='@') "'"+normalized else normalized
        return "\""+safe.replace("\"","\"\"")+"\""
    }

    private fun money(value:Long):String =
        BigDecimal.valueOf(value,2).toPlainString().replace('.',',')

    fun csv(entries:List<Entry>):String = csv(entries,null,null,"Período selecionado")

    /** Stable 8-column layout, three explicit categories and summary with daily dates. */
    fun csv(entries:List<Entry>,startInclusive:Long?,endExclusive:Long?,periodTitle:String):String {
        val report=calculate(entries)
        val result=StringBuilder()
        fun line(vararg fields:String) { result.append(fields.joinToString(";")).append("\r\n") }
        val startText=startInclusive?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
        } ?: ""
        val endText=endExclusive?.let {
            Instant.ofEpochMilli(it-1).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
        } ?: ""
        line("Fechamento Primo",escape(periodTitle),"Inicio",escape(startText),"Fim",escape(endText),"","")
        line("Data","Hora","Categoria","Origem","Pagamento","Descricao","Ganhos (R$)","Gastos (R$)")
        line(escape("RESUMO"),"","","","","","","")
        line(escape("Corridas"),"","","",escape("${report.rides} corridas"),"",money(report.received),"")
        line(escape("Gasolina"),"","","",escape("${report.fuelCount} registros"),"","",money(report.fuel))
        line(escape("Mecânico"),"","","",escape("${report.repairCount} registros"),"","",money(report.repair))
        line(escape("TOTAL"),"","","","","",money(report.received),money(report.expenses))
        line(escape("SALDO PARCIAL"),"","","","","",money(report.balance),"")
        line("","","","","","","","")

        val sections=listOf(
            EntryTypes.RIDE to "CORRIDAS",
            EntryTypes.FUEL to "GASOLINA",
            EntryTypes.REPAIR to "MECÂNICO"
        )
        for((kind,label) in sections) {
            val group=entries.asSequence().filter { it.kind==kind }
                .sortedWith(compareBy<Entry>{it.createdAt}.thenBy { it.id }).toList()
            line(escape("SEÇÃO: "+label),"","","","","","","")
            group.forEach { entry ->
                val time=Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
                val ride=entry.kind==EntryTypes.RIDE
                line(
                    escape(time.format(dateFormat)),escape(time.format(timeFormat)),
                    escape(EntryTypes.name(entry.kind)),
                    escape(if(ride)entry.source else ""),
                    escape(if(ride)entry.payment else ""),
                    escape(entry.note),
                    if(ride)money(entry.cents) else "",
                    if(!ride)money(entry.cents) else ""
                )
            }
            if(group.isEmpty()) line(escape("Nenhum lançamento"),"","","","","","","")
            line("","","","","","","","")
        }
        return result.toString()
    }
}
