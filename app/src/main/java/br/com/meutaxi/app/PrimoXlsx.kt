package br.com.meutaxi.app

import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal offline OOXML workbook: numeric values remain actual numbers. */
object PrimoXlsx {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private data class Cell(val value:String, val numeric:Boolean=false, val bold:Boolean=false)
    private fun text(value:String,bold:Boolean=false) = Cell(value,bold=bold)
    private fun cents(value:Long) = Cell(BigDecimal.valueOf(value,2).toPlainString(),numeric=true)
    private fun count(value:Int) = Cell(value.toString(),numeric=true)

    private fun escape(value:String) = value.filter {
        it=='\t' || it=='\n' || it=='\r' || it.code >= 32
    }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .replace("\"","&quot;").replace("'","&apos;")

    private fun row(index:Int,vararg cells:Cell):String = buildString {
        append("<row r=\"").append(index).append("\">")
        cells.forEachIndexed { col,c ->
            append("<c r=\"").append(('A'.code+col).toChar()).append(index).append("\"")
            if(c.numeric) append(" s=\"1\"><v>").append(c.value).append("</v></c>")
            else {
                if(c.bold) append(" s=\"2\"")
                append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(c.value)).append("</t></is></c>")
            }
        }
        append("</row>")
    }
    private fun sheet(rows:String,details:Boolean,last:Int):String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"").append(NS).append("\">")
        if(details) append("<cols><col min=\"1\" max=\"1\" width=\"15\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"11\" customWidth=\"1\"/><col min=\"3\" max=\"5\" width=\"17\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"38\" customWidth=\"1\"/><col min=\"7\" max=\"7\" width=\"19\" customWidth=\"1\"/></cols>")
        else append("<cols><col min=\"1\" max=\"1\" width=\"42\" customWidth=\"1\"/><col min=\"2\" max=\"3\" width=\"20\" customWidth=\"1\"/></cols>")
        append("<sheetData>").append(rows).append("</sheetData>")
        if(details) append("<autoFilter ref=\"A1:G").append(last).append("\"/>")
        append("</worksheet>")
    }
    private fun summary(entries:List<Entry>,period:String):String {
        val t=ClosingReport.calculate(entries)
        val rows=StringBuilder()
        rows.append(row(1,text("Primo — Fechamento financeiro",true)))
        rows.append(row(2,text("Período"),text(period)))
        rows.append(row(4,text("Recebido",true),cents(t.received)))
        rows.append(row(5,text("Gasolina"),cents(t.fuel)))
        rows.append(row(6,text("Mecânico"),cents(t.repair)))
        rows.append(row(7,text("Total de gastos",true),cents(t.expenses)))
        rows.append(row(8,text("Saldo parcial",true),cents(t.balance)))
        rows.append(row(9,text("Corridas",true),count(t.rides)))
        rows.append(row(10,text("Média por corrida"),cents(t.averageRide)))
        rows.append(row(12,text("Por origem",true),text("Corridas",true),text("Recebido (R$)",true)))
        var n=13
        t.bySource.forEach { rows.append(row(n++,text(it.label),count(it.count),cents(it.amount))) }
        n+=2
        rows.append(row(n++,text("Por pagamento",true),text("Corridas",true),text("Recebido (R$)",true)))
        t.byPayment.forEach { rows.append(row(n++,text(it.label),count(it.count),cents(it.amount))) }
        n+=2
        rows.append(row(n++,text("Despesas",true),text("Registros",true),text("Valor (R$)",true)))
        rows.append(row(n++,text("Gasolina"),count(t.fuelCount),cents(t.fuel)))
        rows.append(row(n++,text("Mecânico"),count(t.repairCount),cents(t.repair)))
        rows.append(row(n,text("Saldo parcial não representa lucro líquido.")))
        return sheet(rows.toString(),false,n)
    }
    private fun details(entries:List<Entry>):String {
        val rows=StringBuilder()
        rows.append(row(1,*listOf("Data","Hora","Categoria","Origem","Pagamento","Descrição","Valor (R$)")
            .map { text(it,true) }.toTypedArray()))
        val date=DateTimeFormatter.ofPattern("dd/MM/yyyy",Locale("pt","BR"))
        val hour=DateTimeFormatter.ofPattern("HH:mm",Locale("pt","BR"))
        entries.sortedWith(compareBy<Entry>{it.createdAt}.thenBy{it.id}).forEachIndexed { i,e ->
            val d=Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault())
            val ride=e.kind==EntryTypes.RIDE
            rows.append(row(i+2,text(d.format(date)),text(d.format(hour)),
                text(EntryTypes.name(e.kind)),text(if(ride)e.source else ""),
                text(if(ride)e.payment else ""),text(e.note),
                cents(if(ride)e.cents else -e.cents)))
        }
        return sheet(rows.toString(),true,entries.size+1)
    }
    private fun ZipOutputStream.part(path:String,xml:String) {
        putNextEntry(ZipEntry(path))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
    fun write(out:OutputStream, entries:List<Entry>,period:String) {
        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml",Types)
            zip.part("_rels/.rels",RootRels)
            zip.part("xl/workbook.xml",Workbook)
            zip.part("xl/_rels/workbook.xml.rels",BookRels)
            zip.part("xl/styles.xml",Styles)
            zip.part("xl/worksheets/sheet1.xml",summary(entries,period))
            zip.part("xl/worksheets/sheet2.xml",details(entries))
        }
    }
    private val Types="""<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
    private val RootRels="""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    private val Workbook="""<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Resumo" sheetId="1" r:id="rId1"/><sheet name="Lançamentos" sheetId="2" r:id="rId2"/></sheets>
</workbook>"""
    private val BookRels="""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    private val Styles="""<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" applyFont="1"/></cellXfs>
</styleSheet>"""
}
