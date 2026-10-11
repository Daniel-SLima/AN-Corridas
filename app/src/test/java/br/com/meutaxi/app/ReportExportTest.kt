package br.com.meutaxi.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ReportExportTest {
    private val records=listOf(
        Entry(id=1,kind=EntryTypes.RIDE,cents=15000,createdAt=1791700000000L,
              source="Rua",payment="Dinheiro"),
        Entry(id=2,kind=EntryTypes.RIDE,cents=10000,createdAt=1791700100000L,
              source="99",payment="Pix",note="Praça da Sé"),
        Entry(id=3,kind=EntryTypes.FUEL,cents=5000,createdAt=1791700200000L),
        Entry(id=4,kind=EntryTypes.REPAIR,cents=12000,createdAt=1791700300000L,
              note="Revisão de óleo = filtro")
    )
    @Test fun totalsMatchEntriesAndRecalculateAfterEdits() {
        val t=ClosingReport.calculate(records)
        assertEquals(2,t.rides)
        assertEquals(25000L,t.received)
        assertEquals(17000L,t.expenses)
        assertEquals(8000L,t.balance)
        assertEquals(12500L,t.averageRide)
        assertEquals(25000L,t.bySource.sumOf { it.amount })
        assertEquals(25000L,t.byPayment.sumOf { it.amount })
        val edited=records.map { if(it.id==1L)it.copy(cents=20000) else it }
        assertEquals(13000L,ClosingReport.calculate(edited).balance)
        val removed=edited.filterNot { it.id==2L }
        assertEquals(3000L,ClosingReport.calculate(removed).balance)
        assertEquals(1,ClosingReport.calculate(removed).rides)
        assertEquals(8000L,t.balance) // input reports and data unaffected
    }
    @Test fun utf8CsvHasNoBomAndPreservesTextSafely() {
        val csv=ClosingReport.csv(records+Entry(
            kind=EntryTypes.REPAIR,cents=100,createdAt=1791700400000L,note="=1+1"))
        assertTrue(csv.startsWith("Data;Hora;Categoria"))
        assertFalse(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Descrição;Valor (R$)"))
        assertTrue(csv.contains("Mecânico"))
        assertTrue(csv.contains("Revisão de óleo"))
        assertTrue(csv.contains("'=1+1"))
        assertTrue(csv.contains("-120,00"))
        assertTrue(csv.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8).contains("Descrição"))
    }
    @Test fun xlsxContainsTwoValidSheetsWithNumericMoneyAndAccents() {
        val data=ByteArrayOutputStream()
        PrimoXlsx.write(data,records,"11/10/2026")
        val files=mutableMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(data.toByteArray())).use { zip ->
            while(true) {
                val item=zip.nextEntry ?: break
                files[item.name]=zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(7,files.size)
        assertTrue(files.containsKey("[Content_Types].xml"))
        assertTrue(files["xl/workbook.xml"].orEmpty().contains("Lançamentos"))
        val summary=files["xl/worksheets/sheet1.xml"].orEmpty()
        val rows=files["xl/worksheets/sheet2.xml"].orEmpty()
        assertTrue(summary.contains("Saldo parcial"))
        assertTrue(summary.contains("<v>80.00</v>"))
        assertTrue(rows.contains("Mecânico"))
        assertTrue(rows.contains("Praça da Sé"))
        assertTrue(rows.contains("Revisão de óleo = filtro"))
        assertTrue(rows.contains("<v>-120.00</v>"))
        assertFalse(rows.contains("<f>")) // no executable spreadsheet formulas
        assertTrue(files["xl/styles.xml"].orEmpty().contains("numFmtId=\"164\""))
    }
}
