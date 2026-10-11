package br.com.meutaxi.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class ReportExportTest {
    private val entries=listOf(
        Entry(id=1,kind=EntryTypes.RIDE,cents=15000,createdAt=1791700000000L,
            source="Rua",payment="Dinheiro"),
        Entry(id=2,kind=EntryTypes.RIDE,cents=10000,createdAt=1791700100000L,
            source="99",payment="Pix",note="Praça da Sé"),
        Entry(id=3,kind=EntryTypes.FUEL,cents=5000,createdAt=1791700200000L),
        Entry(id=4,kind=EntryTypes.REPAIR,cents=12000,createdAt=1791700300000L,
            note="Troca de óleo e filtro")
    )

    @Test fun totalsAreRecomputedAfterEditingAndDeleting() {
        val initial=ClosingReport.calculate(entries)
        assertEquals(2,initial.rides)
        assertEquals(25000L,initial.received)
        assertEquals(5000L,initial.fuel)
        assertEquals(12000L,initial.repair)
        assertEquals(8000L,initial.balance)
        assertEquals(12500L,initial.averageRide)
        assertEquals(25000L,initial.bySource.sumOf { it.amount })
        assertEquals(25000L,initial.byPayment.sumOf { it.amount })
        val edited=entries.map { if(it.id==1L)it.copy(cents=20000L) else it }
        assertEquals(13000L,ClosingReport.calculate(edited).balance)
        val deleted=edited.filterNot { it.id==2L }
        assertEquals(3000L,ClosingReport.calculate(deleted).balance)
        assertEquals(1,ClosingReport.calculate(deleted).rides)
        assertEquals(8000L,initial.balance)
    }

    @Test fun csvCompatibleCharsetIsReadableAndDoesNotHaveBom() {
        val source=ClosingReport.csv(entries)
        val bytes=source.toByteArray(Charset.forName("windows-1252"))
        val reopened=String(bytes,Charset.forName("windows-1252"))
        assertEquals(source,reopened)
        assertTrue(reopened.startsWith("Data;Hora;Categoria"))
        assertFalse(reopened.startsWith("\uFEFF"))
        assertTrue(reopened.contains("Descrição"))
        assertTrue(reopened.contains("Mecânico"))
        assertTrue(reopened.contains("Praça da Sé"))
        assertTrue(reopened.contains("Troca de óleo"))
        assertTrue(reopened.contains("-120,00"))
    }

    @Test fun csvQuotesSemicolonsAndNeutralizesSpreadsheetFormulas() {
        val malicious=Entry(kind=EntryTypes.REPAIR,cents=1234,
            createdAt=1791700400000L,note="=SUM(1;2)\nOutra frase")
        val source=ClosingReport.csv(listOf(malicious))
        assertTrue(source.contains("\"'=SUM(1;2) Outra frase\""))
        assertTrue(source.contains("-12,34"))
        val quoted=ClosingReport.csv(listOf(malicious.copy(note="Troca \"especial\"; óleo")))
        assertTrue(quoted.contains("\"Troca \"\"especial\"\"; óleo\""))
    }

    @Test fun emptyAndExpenseOnlyPeriodsAreWellDefined() {
        val empty=ClosingReport.calculate(emptyList())
        assertEquals(0,empty.rides)
        assertEquals(0L,empty.balance)
        assertTrue(empty.byPayment.isEmpty())
        val expense=ClosingReport.calculate(entries.filter { it.kind!=EntryTypes.RIDE })
        assertEquals(0,expense.rides)
        assertEquals(-17000L,expense.balance)
        assertEquals(0L,expense.averageRide)
    }
}
