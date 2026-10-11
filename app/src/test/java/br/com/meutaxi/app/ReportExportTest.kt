package br.com.meutaxi.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.ZoneId

class ReportExportTest {
    private val charset=Charset.forName("windows-1252")
    private val records=listOf(
        Entry(id=1,kind=EntryTypes.RIDE,cents=15000,createdAt=1791700000000L,
            source="Rua",payment="Dinheiro"),
        Entry(id=2,kind=EntryTypes.RIDE,cents=10000,createdAt=1791700100000L,
            source="99",payment="Pix",note="Praça da Sé"),
        Entry(id=3,kind=EntryTypes.FUEL,cents=5000,createdAt=1791700200000L),
        Entry(id=4,kind=EntryTypes.REPAIR,cents=12000,createdAt=1791700300000L,
            note="Troca de óleo e filtro")
    )
    @Test fun financialTotalsRecalculateAfterEditsAndDeletes() {
        val initial=ClosingReport.calculate(records)
        assertEquals(2,initial.rides)
        assertEquals(25000L,initial.received)
        assertEquals(17000L,initial.expenses)
        assertEquals(8000L,initial.balance)
        assertEquals(12500L,initial.averageRide)
        assertEquals(25000L,initial.bySource.sumOf { it.amount })
        assertEquals(25000L,initial.byPayment.sumOf { it.amount })
        val edited=records.map { if(it.id==1L)it.copy(cents=20000L) else it }
        assertEquals(13000L,ClosingReport.calculate(edited).balance)
        val deleted=edited.filterNot { it.id==2L }
        assertEquals(3000L,ClosingReport.calculate(deleted).balance)
        assertEquals(1,ClosingReport.calculate(deleted).rides)
        assertEquals(8000L,initial.balance)
    }

    @Test fun csvHasSelectedPeriodDateAndSeparateGainsExpenses() {
        val start=LocalDate.of(2026,10,5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end=LocalDate.of(2026,10,12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("primo-fechamento-2026-10-05_a_2026-10-11.csv",ClosingReport.fileName(start,end))
        val csv=ClosingReport.csv(records,start,end,"esta semana")
        val reopened=String(csv.toByteArray(charset),charset)
        assertEquals(csv,reopened)
        assertTrue(csv.startsWith("Fechamento Primo;"))
        assertTrue(csv.contains("Início"))
        assertTrue(csv.contains("05/10/2026"))
        assertTrue(csv.contains("11/10/2026"))
        assertTrue(csv.contains("Data;Hora;Categoria;Origem;Pagamento;Descrição;Ganhos (R$);Gastos (R$)"))
        assertTrue(csv.contains("\"SEÇÃO: CORRIDAS\""))
        assertTrue(csv.contains("\"SEÇÃO: GASOLINA\""))
        assertTrue(csv.contains("\"SEÇÃO: MECÂNICO\""))
        assertTrue(csv.contains("Praça da Sé"))
        assertTrue(csv.contains("Troca de óleo"))
        assertTrue(csv.contains("\"TOTAL\";;;;;;250,00;170,00"))
        assertTrue(csv.contains("\"SALDO PARCIAL\";;;;;;80,00;"))
        assertFalse(csv.contains("\uFEFF"))
        assertFalse(csv.contains("\n\n")) // CRLF format
    }

    @Test fun csvEscapesSemicolonsAndDisablesFormulas() {
        val bad=Entry(kind=EntryTypes.REPAIR,cents=1234,createdAt=1791700400000L,
            note="=SUM(1;2)\nOutra frase")
        val csv=ClosingReport.csv(listOf(bad))
        assertTrue(csv.contains("\"'=SUM(1;2) Outra frase\""))
        assertTrue(csv.contains(";12,34\r\n"))
        assertTrue(csv.contains("\"SEÇÃO: MECÂNICO\""))
        val quoted=ClosingReport.csv(listOf(bad.copy(note="Troca \"especial\"; óleo")))
        assertTrue(quoted.contains("\"Troca \"\"especial\"\"; óleo\""))
    }

    @Test fun emptyAndExpenseOnlyAreHandled() {
        val empty=ClosingReport.calculate(emptyList())
        assertEquals(0,empty.rides)
        assertEquals(0L,empty.balance)
        assertTrue(empty.byPayment.isEmpty())
        val expense=ClosingReport.calculate(records.filter { it.kind!=EntryTypes.RIDE })
        assertEquals(0,expense.rides)
        assertEquals(-17000L,expense.balance)
        assertEquals(0L,expense.averageRide)
        assertTrue(ClosingReport.csv(records.filter { it.kind!=EntryTypes.RIDE }).contains(";-170,00;"))
    }

    @Test fun handlesThousandRecordsWithoutMiscount() {
        val many=(1..1000).map { i ->
            Entry(id=i.toLong(),kind=when {
                i%10==0 -> EntryTypes.FUEL
                i%25==0 -> EntryTypes.REPAIR
                else -> EntryTypes.RIDE
            },cents=1000,createdAt=1791700000000L+i*1000L,
                source=if(i%3==0)"99" else "Rua",payment="Pix",
                note=if(i%17==0)"Óleo; filtro" else "")
        }
        val report=ClosingReport.calculate(many)
        assertEquals(880,report.rides)
        assertEquals(880000L,report.received)
        assertEquals(120000L,report.expenses)
        assertEquals(760000L,report.balance)
        val csv=ClosingReport.csv(many)
        assertEquals(1000,csv.lines().count { it.contains("10,00") && !it.contains("TOTAL") && !it.contains("SALDO") })
        assertEquals(csv,String(csv.toByteArray(charset),charset))
    }
}
