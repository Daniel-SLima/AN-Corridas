package br.com.meutaxi.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.EditText
import android.text.InputType
import android.content.res.ColorStateList
import org.json.JSONObject
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: EntriesDb
    private lateinit var goals: GoalsStore
    private lateinit var content: LinearLayout
    private var period = 0
    private var selectedDate: LocalDate = LocalDate.now()
    private val backupExportCode = 501
    private val backupImportCode = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = EntriesDb(this)
        goals = GoalsStore(this)
        window.statusBarColor = 0xFF0B4F8A.toInt()
        window.navigationBarColor = 0xFFF7F9FC.toInt()
        val scroller = ScrollView(this).apply {
            setBackgroundColor(0xFFF7F9FC.toInt())
            isFillViewport = true
        }
        content = Ui.column(this,18)
        scroller.addView(content)
        setContentView(scroller)
        draw()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) draw()
    }

    private fun add(text: String, size: Float = 16f, bold: Boolean = false): TextView =
        Ui.text(this,text,size,bold).also { content.addView(it) }

    private fun action(label: String, color: Int = Ui.BLUE, task: () -> Unit) {
        content.addView(Ui.gap(this,7))
        content.addView(Ui.button(this,label,color).apply { setOnClickListener { task() } })
    }

    private fun draw() {
        content.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.BLUE,this@MainActivity,22)
            setPadding(Ui.dp(this@MainActivity,18),Ui.dp(this@MainActivity,16),
                Ui.dp(this@MainActivity,12),Ui.dp(this@MainActivity,16))
        }
        val title = Ui.column(this,0).apply {
            addView(Ui.text(this@MainActivity,"Primo",34f,true).apply { setTextColor(Color.WHITE) })
            addView(Ui.text(this@MainActivity,"Seu parceiro das contas",14f,true).apply {
                setTextColor(Ui.YELLOW)
            })
        }
        header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.primo_mascot)
            contentDescription = "Primo, seu parceiro das contas"
            scaleType = ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)))
        content.addView(header)
        content.addView(Ui.gap(this,10))
        val greeting = Ui.text(this,"Bom dia, primo! Bora organizar as contas?",15f)
        content.addView(greeting)
        content.addView(Ui.gap(this,9))
        val enabled = getSharedPreferences("state",MODE_PRIVATE).getBoolean("overlay_running",false)
        val canOverlay = Settings.canDrawOverlays(this)
        when {
            !canOverlay -> action("Permitir bolinha flutuante",Ui.YELLOW) {
                AlertDialog.Builder(this)
                    .setTitle("Bolinha do Primo")
                    .setMessage("Na próxima tela, permita ao Primo aparecer sobre outros aplicativos. Depois volte aqui e ative a bolinha.")
                    .setPositiveButton("Abrir configurações") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }.setNegativeButton("Agora não",null).show()
            }
            enabled -> action("Desligar bolinha flutuante",0xFF64748B.toInt()) {
                stopService(Intent(this, FloatingService::class.java))
                getSharedPreferences("state",MODE_PRIVATE).edit().putBoolean("overlay_running",false).apply()
                draw()
            }
            else -> action("Ativar bolinha flutuante",Ui.YELLOW) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),101)
                }
                try {
                    startForegroundService(Intent(this, FloatingService::class.java))
                } catch (e: Exception) {
                    Toast.makeText(this,"Não foi possível iniciar: ${e.message}",Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(Ui.gap(this,18))
        add("Resumo financeiro",22f,true)
        content.addView(Ui.gap(this,10))
        val periodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Hoje","Ontem","Semana","Mês","Dia...").forEachIndexed { i, title ->
            val choose = TextView(this).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (i == period) Ui.DARK else Ui.DARK)
                background = Ui.rounded(if (i == period) Ui.YELLOW else Color.WHITE,this@MainActivity,10)
                setPadding(Ui.dp(this@MainActivity,3),Ui.dp(this@MainActivity,9),
                    Ui.dp(this@MainActivity,3),Ui.dp(this@MainActivity,9))
                setOnClickListener {
                    if (i == 4) selectDate()
                    else { period = i; draw() }
                }
            }
            periodRow.addView(choose,LinearLayout.LayoutParams(0,Ui.dp(this,46),1f).apply {
                setMargins(2,0,2,0)
            })
        }
        content.addView(periodRow)
        val (start, end, periodName) = range()
        val entries = db.between(start,end)
        val income = entries.filter { it.kind == EntryTypes.RIDE }.sumOf { it.cents }
        val expenses = entries.filter { it.kind != EntryTypes.RIDE }.sumOf { it.cents }
        val rides = entries.count { it.kind == EntryTypes.RIDE }
        content.addView(Ui.gap(this,15))
        card("RECEBIDO — $periodName",Money.show(income),"$rides corridas",Ui.GREEN)
        content.addView(Ui.gap(this,7))
        val amounts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        amounts.addView(smallCard("GASTOS",Money.show(expenses),Ui.RED),LinearLayout.LayoutParams(0,-2,1f).apply {
            rightMargin = Ui.dp(this@MainActivity,5)
        })
        amounts.addView(smallCard("SALDO PARCIAL",Money.show(income-expenses),Ui.BLUE),
            LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = Ui.dp(this@MainActivity,5) })
        content.addView(amounts)
        content.addView(Ui.gap(this,4))
        add("Saldo = receitas menos despesas lançadas.",12f)
        showGoalCards()
        content.addView(Ui.gap(this,16))
        add("Registrar agora",20f,true)
        action("🚕  Corrida",Ui.BLUE) { showEntryForm(EntryTypes.RIDE) }
        action("⛽  Gasolina",Ui.YELLOW) { showEntryForm(EntryTypes.FUEL) }
        action("🔧  Mecânico",Ui.DARK) { showEntryForm(EntryTypes.REPAIR) }
        content.addView(Ui.gap(this,18))
        add("Histórico do período",20f,true)
        content.addView(Ui.gap(this,7))
        if (entries.isEmpty()) {
            add("Nada anotado por aqui ainda, primo.",15f)
        } else {
            entries.forEach { entry -> entryRow(entry) }
        }
        content.addView(Ui.gap(this,18))
        add("Mais opções",20f,true)
        action("Configurar metas e mensagens",Ui.BLUE) { showGoalsDialog() }
        action("Exportar backup",0xFF475569.toInt()) { exportBackup() }
        action("Importar backup",0xFF475569.toInt()) { importBackup() }
        content.addView(Ui.gap(this,10))
        add("Funciona sem internet. Exporte um backup regularmente.",13f)
        content.addView(Ui.gap(this,25))
    }

    private fun smallCard(label: String, amount: String, color: Int): LinearLayout =
        Ui.column(this,12).apply {
            background = Ui.rounded(Color.WHITE,this@MainActivity)
            addView(Ui.text(this@MainActivity,label,12f,true))
            addView(Ui.gap(this@MainActivity,6))
            addView(Ui.text(this@MainActivity,amount,20f,true).apply { setTextColor(color) })
        }

    private fun showGoalCards() {
        val settings = goals.read()
        content.addView(Ui.gap(this,16))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(this,"🎯 Minhas metas",20f,true),
            LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Ui.button(this,"Editar",Ui.BLUE).apply {
            textSize = 13f
            setOnClickListener { showGoalsDialog() }
        },LinearLayout.LayoutParams(Ui.dp(this,85),Ui.dp(this,43)))
        content.addView(row)
        if (!settings.dailyEnabled && !settings.weeklyEnabled) {
            content.addView(Ui.gap(this,8))
            content.addView(Ui.text(this,"Sem meta definida. Quer acompanhar quanto falta pra bater seu objetivo?",14f))
            return
        }
        val today = LocalDate.now()
        if (settings.dailyEnabled) {
            goalCard("Meta de hoje",settings.dailyCents,
                GoalMath.received(db,today,today.plusDays(1)))
        }
        if (settings.weeklyEnabled) {
            val week = GoalMath.startOfWeek(today)
            goalCard("Meta da semana",settings.weeklyCents,
                GoalMath.received(db,week,week.plusDays(7)))
        }
    }

    private fun goalCard(title: String, target: Long, received: Long) {
        content.addView(Ui.gap(this,9))
        val block = Ui.column(this,14).apply { background = Ui.rounded(Color.WHITE,this@MainActivity) }
        val percentage = if (target > 0) (received.toDouble()/target*100).toInt() else 0
        block.addView(Ui.text(this,"$title • ${Money.show(target)}",16f,true))
        block.addView(Ui.text(this,"Recebido: ${Money.show(received)}  •  $percentage%",15f))
        block.addView(Ui.gap(this,8))
        block.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = GoalMath.progress(received,target)
            progressTintList = ColorStateList.valueOf(Ui.GREEN)
            progressBackgroundTintList = ColorStateList.valueOf(0xFFE2E8F0.toInt())
            minimumHeight = Ui.dp(this@MainActivity,9)
            contentDescription = "$percentage por cento da meta concluída"
        })
        block.addView(Ui.gap(this,8))
        val remain = GoalMath.remaining(received,target)
        val message = if (remain == 0L) {
            if (goals.read().friendlyMessages) "Aí sim, primo! Meta batida! 🎉" else "Meta atingida."
        } else "Faltam ${Money.show(remain)} pra bater a meta."
        block.addView(Ui.text(this,message,14f))
        content.addView(block)
    }

    private fun showGoalsDialog() {
        val current = goals.read()
        val panel = Ui.column(this,16)
        panel.addView(Ui.text(this,"Metas do Primo",22f,true))
        panel.addView(Ui.text(this,"Escolha suas metas de recebimento. Gastos ficam separados.",14f))
        fun line(label: String, checked: Boolean, value: Long): Pair<Switch,EditText> {
            val toggle = Switch(this).apply {
                text = label
                textSize = 16f
                isChecked = checked
            }
            panel.addView(Ui.gap(this,14))
            panel.addView(toggle)
            val field = EditText(this).apply {
                hint = "Ex.: 300,00"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 21f
                if (value > 0) setText(java.math.BigDecimal.valueOf(value,2).toPlainString().replace('.',','))
                isEnabled = toggle.isChecked
            }
            toggle.setOnCheckedChangeListener { _, enabled -> field.isEnabled = enabled }
            panel.addView(field)
            return toggle to field
        }
        val (dailySwitch,dailyInput) = line("Meta diária (R$)",current.dailyEnabled,current.dailyCents)
        val (weeklySwitch,weeklyInput) = line("Meta semanal (R$)",current.weeklyEnabled,current.weeklyCents)
        panel.addView(Ui.gap(this,14))
        val messages = Switch(this).apply {
            text = "Mensagens bem-humoradas"
            textSize = 15f
            isChecked = current.friendlyMessages
        }
        panel.addView(messages)
        val scroll = ScrollView(this).apply { addView(panel) }
        val dialog = AlertDialog.Builder(this).setView(scroll)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val daily = if (dailySwitch.isChecked) Money.parse(dailyInput.text.toString()) else null
                val weekly = if (weeklySwitch.isChecked) Money.parse(weeklyInput.text.toString()) else null
                if (dailySwitch.isChecked && daily == null) {
                    dailyInput.error = "Digite um valor maior que zero"; return@setOnClickListener
                }
                if (weeklySwitch.isChecked && weekly == null) {
                    weeklyInput.error = "Digite um valor maior que zero"; return@setOnClickListener
                }
                goals.save(GoalSettings(dailySwitch.isChecked,daily ?: current.dailyCents,
                    weeklySwitch.isChecked,weekly ?: current.weeklyCents,messages.isChecked))
                dialog.dismiss()
                draw()
            }
        }
        dialog.show()
    }

    private fun card(label: String, amount: String, foot: String, color: Int) {
        val card = Ui.column(this,14).apply { background = Ui.rounded(Color.WHITE,this@MainActivity) }
        card.addView(Ui.text(this,label,13f,true))
        card.addView(Ui.gap(this,5))
        card.addView(Ui.text(this,amount,27f,true).apply { setTextColor(color) })
        card.addView(Ui.text(this,foot,13f))
        content.addView(card)
    }

    private fun entryRow(e: Entry) {
        val card = Ui.column(this,12).apply { background = Ui.rounded(Color.WHITE,this@MainActivity) }
        val label = EntryTypes.name(e.kind) + if (e.kind == EntryTypes.RIDE) " • ${e.source} • ${e.payment}" else ""
        card.addView(Ui.text(this,label,16f,true))
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date(e.createdAt))
        card.addView(Ui.text(this,timestamp,13f))
        card.addView(Ui.text(this,(if (e.kind == EntryTypes.RIDE) "+ " else "− ") + Money.show(e.cents),20f,true)
            .apply { setTextColor(if (e.kind == EntryTypes.RIDE) Ui.GREEN else Ui.RED) })
        if (e.note.isNotBlank()) card.addView(Ui.text(this,e.note,15f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val edit = Ui.button(this,"Editar",Ui.BLUE)
        val remove = Ui.button(this,"Excluir",Ui.RED)
        controls.addView(edit,LinearLayout.LayoutParams(0,Ui.dp(this,46),1f).apply { rightMargin = Ui.dp(this@MainActivity,8) })
        controls.addView(remove,LinearLayout.LayoutParams(0,Ui.dp(this,46),1f))
        edit.setOnClickListener { showEntryForm(e.kind,e) }
        remove.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Excluir lançamento?")
                .setMessage("${EntryTypes.name(e.kind)} de ${Money.show(e.cents)}. Esta ação não pode ser desfeita.")
                .setPositiveButton("Excluir") { _,_ -> db.delete(e.id); draw() }
                .setNegativeButton("Cancelar",null).show()
        }
        card.addView(Ui.gap(this,6))
        card.addView(controls)
        content.addView(card)
        content.addView(Ui.gap(this,9))
    }

    private fun showEntryForm(kind: String, initial: Entry? = null) {
        val dialog = AlertDialog.Builder(this).create()
        dialog.setView(QuickEntryForm.create(this,kind,initial,
            onSave = { entry ->
                try {
                    db.save(entry)
                    dialog.dismiss()
                    draw()
                    Toast.makeText(this,if (goals.read().friendlyMessages) {
                        when(kind) {
                            EntryTypes.RIDE -> "Boa, primo! Mais uma pra conta."
                            EntryTypes.FUEL -> "Abastecimento anotado. Tá na conta!"
                            else -> "Manutenção anotada, primo!"
                        }
                    } else "Lançamento salvo!",Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Toast.makeText(this,"Erro ao salvar: ${ex.message}",Toast.LENGTH_LONG).show()
                }
            },
            onCancel = { dialog.dismiss() }
        ))
        dialog.show()
    }

    private fun selectDate() {
        val now = selectedDate
        DatePickerDialog(this,{ _, year, month, day ->
            selectedDate = LocalDate.of(year,month+1,day)
            period = 4
            draw()
        },now.year,now.monthValue-1,now.dayOfMonth).show()
    }

    private fun range(): Triple<Long, Long, String> {
        val today = LocalDate.now()
        val start = when(period) {
            1 -> today.minusDays(1)
            2 -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            3 -> today.withDayOfMonth(1)
            4 -> selectedDate
            else -> today
        }
        val end = when(period) {
            2 -> start.plusDays(7)
            3 -> start.plusMonths(1)
            else -> start.plusDays(1)
        }
        val name = when(period) {
            1 -> "ontem"
            2 -> "esta semana"
            3 -> "este mês"
            4 -> selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            else -> "hoje"
        }
        return Triple(start.atStartMillis(),end.atStartMillis(),name)
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE,"primo-backup-${LocalDate.now()}.json")
        }
        startActivityForResult(intent,backupExportCode)
    }

    private fun importBackup() {
        AlertDialog.Builder(this).setTitle("Substituir todos os registros?")
            .setMessage("Ao importar, os registros atuais serão substituídos pelos do arquivo. Faça uma exportação antes de continuar.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Selecionar backup") { _,_ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent,backupImportCode)
            }.show()
    }

    @Deprecated("Using activity result for a framework-only no-dependency app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                backupExportCode -> {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                        val json = JSONObject(db.backupJson()).apply {
                            put("goals",goals.read().asJson())
                        }
                        it.write(json.toString(2))
                    } ?: error("Não foi possível escrever o arquivo")
                    Toast.makeText(this,"Backup exportado!",Toast.LENGTH_LONG).show()
                }
                backupImportCode -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: error("Não foi possível ler o arquivo")
                    val root = JSONObject(json)
                    val restoredGoals = root.optJSONObject("goals")?.let { GoalSettings.fromJson(it) }
                    val count = db.restoreJson(json)
                    if (restoredGoals != null) goals.save(restoredGoals)
                    draw()
                    Toast.makeText(this,"$count registros importados!",Toast.LENGTH_LONG).show()
                }
            }
        } catch (ex: Exception) {
            AlertDialog.Builder(this).setTitle("Não foi possível concluir")
                .setMessage(ex.message ?: "Arquivo inválido ou inacessível")
                .setPositiveButton("OK",null).show()
        }
    }
}
