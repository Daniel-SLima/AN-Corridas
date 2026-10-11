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

/** Read-only exported files only, with temporary URI access granted by Android. */
class ReportShareProvider:ContentProvider() {
    private fun file(uri:Uri):Pair<File,String> {
        val name=uri.lastPathSegment ?: throw FileNotFoundException("Arquivo ausente")
        val mime=when(name) {
            "fechamento.xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "fechamento.csv" -> "text/csv"
            else -> throw FileNotFoundException("Arquivo não permitido")
        }
        return File(requireNotNull(context).cacheDir,"primo_shared/$name") to mime
    }
    override fun onCreate():Boolean=true
    override fun getType(uri:Uri):String=file(uri).second
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r")throw FileNotFoundException("Somente leitura")
        val f=file(uri).first
        if(!f.isFile)throw FileNotFoundException("Exporte novamente")
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
        selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val f=file(uri).first
        val cols=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(cols).apply { addRow(cols.map { col-> when(col) {
            OpenableColumns.DISPLAY_NAME -> "Primo - Fechamento."+f.extension
            OpenableColumns.SIZE -> f.length()
            else -> null
        }}.toTypedArray()) }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,
        selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
