package br.com.meutaxi.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

object Ui {
    const val BLUE: Int = 0xFF0B4F8A.toInt()
    const val GREEN: Int = 0xFF15965D.toInt()
    const val RED: Int = 0xFFD84B4B.toInt()
    const val DARK: Int = 0xFF172B3A.toInt()
    const val YELLOW: Int = 0xFFFFC928.toInt()
    const val LIGHT: Int = 0xFFF7F9FC.toInt()

    fun dp(context: Context, num: Int): Int =
        (num * context.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, context: Context, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(context, radius).toFloat() }

    fun column(context: Context, pad: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context,pad),dp(context,pad),dp(context,pad),dp(context,pad))
    }

    fun text(context: Context, content: String, size: Float = 17f, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = content
            textSize = size
            setTextColor(DARK)
            if (bold) setTypeface(null,Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    fun button(context: Context, content: String, color: Int = BLUE): Button =
        Button(context).apply {
            text = content
            textSize = 16f
            isAllCaps = false
            setTextColor(if (color == YELLOW) DARK else Color.WHITE)
            background = rounded(color,context)
            minHeight = dp(context,52)
        }

    fun gap(context: Context, height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context,height))
    }

    fun spinner(context: Context, values: List<String>, current: String): Spinner =
        Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
            val index = values.indexOf(current)
            setSelection(if (index >= 0) index else 0)
            minimumHeight = dp(context,46)
        }
}

object QuickEntryForm {
    fun create(
        context: Context,
        kind: String,
        original: Entry? = null,
        onSave: (Entry) -> Unit,
        onCancel: () -> Unit
    ): View {
        val outer = ScrollView(context).apply { isFillViewport = false }
        val content = Ui.column(context, 16).apply {
            background = Ui.rounded(Color.WHITE,context,18)
        }
        outer.addView(content)
        content.addView(Ui.text(context,
            if (original == null) "Adicionar ${EntryTypes.name(kind).lowercase()}" else "Editar lançamento",
            21f,true))
        content.addView(Ui.gap(context,12))
        content.addView(Ui.text(context,"Valor em reais",15f,true))
        val amount = EditText(context).apply {
            hint = "Ex.: 35,00"
            textSize = 23f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (original != null) {
                val digits = original.cents.toString().padStart(3,'0')
                setText(digits.dropLast(2) + "," + digits.takeLast(2))
            }
        }
        content.addView(amount)
        var source: Spinner? = null
        var payment: Spinner? = null
        var note: EditText? = null
        if (kind == EntryTypes.RIDE) {
            content.addView(Ui.gap(context,10))
            content.addView(Ui.text(context,"Origem da corrida",15f,true))
            source = Ui.spinner(context,listOf("Rua","99","Uber Taxi"),original?.source ?: "Rua")
            content.addView(source)
            content.addView(Ui.text(context,"Como recebeu?",15f,true))
            payment = Ui.spinner(context,listOf("Dinheiro","Pix","Cartão","App"), original?.payment ?: "Dinheiro")
            content.addView(payment)
        } else if (kind == EntryTypes.REPAIR) {
            content.addView(Ui.gap(context,10))
            content.addView(Ui.text(context,"O que foi feito no mecânico?",15f,true))
            note = EditText(context).apply {
                hint = "Ex.: troca de óleo e filtro"
                minLines = 2
                maxLines = 4
                setText(original?.note ?: "")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            content.addView(note)
        }
        content.addView(Ui.gap(context,16))
        val save = Ui.button(context,"SALVAR",Ui.GREEN)
        content.addView(save)
        content.addView(Ui.gap(context,8))
        val cancel = Ui.button(context,"Cancelar",0xFF64748B.toInt())
        content.addView(cancel)
        cancel.setOnClickListener { onCancel() }
        save.setOnClickListener {
            val cents = Money.parse(amount.text.toString())
            if (cents == null) {
                amount.error = "Digite um valor válido. Ex.: 35,00"
                return@setOnClickListener
            }
            val entry = Entry(
                id = original?.id ?: 0L,
                kind = kind,
                cents = cents,
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
                source = source?.selectedItem?.toString() ?: original?.source ?: "Rua",
                payment = payment?.selectedItem?.toString() ?: original?.payment ?: "Dinheiro",
                note = note?.text?.toString()?.trim()?.take(500) ?: original?.note ?: ""
            )
            onSave(entry)
        }
        return outer
    }
}
