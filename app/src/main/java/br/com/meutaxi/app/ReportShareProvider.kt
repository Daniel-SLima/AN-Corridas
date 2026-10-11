package br.com.meutaxi.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Serves only cached CSV closing reports, under temporary Android read grants. */
class ReportShareProvider:ContentProvider() {
    private val filePattern=Regex("""primo-fechamento-\d{4}-\d{2}-\d{2}_a_\d{4}-\d{2}-\d{2}\.csv""")
    private fun resolve(uri:Uri):File {
        val name=uri.lastPathSegment ?: throw FileNotFoundException("Nome ausente")
        if(!filePattern.matches(name))throw FileNotFoundException("Arquivo inválido")
        return File(File(requireNotNull(context).cacheDir,"primo_shared"),name)
    }
    override fun onCreate():Boolean=true
    override fun getType(uri:Uri):String { resolve(uri); return "text/csv" }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r")throw FileNotFoundException("Somente leitura")
        val file=resolve(uri)
        if(!file.isFile)throw FileNotFoundException("Relatório não encontrado")
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
        selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val f=resolve(uri)
        val fields=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(fields).apply {
            addRow(fields.map { field-> when(field) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }}.toTypedArray())
        }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,
        selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
