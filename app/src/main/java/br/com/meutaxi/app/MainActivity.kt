package br.com.meutaxi.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.ImageView
import android.widget.ProgressBar
import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.Switch
import android.widget.EditText
import android.text.InputType
import android.content.res.ColorStateList
import org.json.JSONObject
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
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
import java.io.File
import java.nio.charset.Charset

class MainActivity : Activity() {
    private lateinit var db: EntriesDb
    private lateinit var goals: GoalsStore
    private lateinit var content: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var navigation: LinearLayout
    private var tab = 0
    private var period = 0
    private var selectedDate: LocalDate = LocalDate.now()
    private val backupExportCode = 501
    private val backupImportCode = 502
    private val csvExportCode = 503
    private data class ExportSelection(val kind:String,val start:Long,val end:Long,val title:String)
    private var exportSelection: ExportSelection? = null
    private val midnightHandler = Handler(Looper.getMainLooper())
    private val midnightRefresh = object:Runnable {
        override fun run() {
            if (::content.isInitialized) draw()
            scheduleMidnightRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = EntriesDb(this)
        goals = GoalsStore(this)
        savedInstanceState?.let { state ->
            if (state.containsKey("export_start")) {
                exportSelection = ExportSelection(
                    state.getString("export_kind","xlsx") ?: "xlsx",
                    state.getLong("export_start"),state.getLong("export_end"),
                    state.getString("export_title","Período selecionado") ?: "Período selecionado")
            }
        }
        window.statusBarColor = 0xFF0B4F8A.toInt()
        window.navigationBarColor = 0xFFF7F9FC.toInt()
        scroller = ScrollView(this).apply {
            setBackgroundColor(0xFFF7F9FC.toInt())
            isFillViewport = true
        }
        content = Ui.column(this,18)
        scroller.addView(content)
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,5),
                Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,5))
            setBackgroundColor(Color.WHITE)
            elevation = Ui.dp(this@MainActivity,8).toFloat()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.LIGHT)
        }
        // Android 15/16 can draw app content behind the status bar. Respect
        // system insets and add extra breathing room above the Primo header.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0,bars.top + Ui.dp(this@MainActivity,12),0,bars.bottom)
                insets
            }
        } else {
            root.setPadding(0,Ui.dp(this,12),0,0)
        }
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(navigation,LinearLayout.LayoutParams(-1,Ui.dp(this,71)))
        setContentView(root)
        draw()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) draw()
        scheduleMidnightRefresh()
    }

    override fun onPause() {
        midnightHandler.removeCallbacks(midnightRefresh)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        exportSelection?.let {
            outState.putString("export_kind",it.kind)
            outState.putLong("export_start",it.start)
            outState.putLong("export_end",it.end)
            outState.putString("export_title",it.title)
        }
    }

    private fun scheduleMidnightRefresh() {
        midnightHandler.removeCallbacks(midnightRefresh)
        val milliseconds = (LocalDate.now().plusDays(1).atStartMillis() -
            System.currentTimeMillis() + 300L).coerceAtLeast(1000L)
        midnightHandler.postDelayed(midnightRefresh,milliseconds)
    }

    private fun add(text: String, size: Float = 16f, bold: Boolean = false): TextView =
        Ui.text(this,text,size,bold).also { content.addView(it) }

    private fun action(label: String, color: Int = Ui.BLUE, icon: String? = null, task: () -> Unit) {
        content.addView(Ui.gap(this,7))
        val button = if (icon == null) Ui.button(this,label,color)
                     else Ui.iconButton(this,label,icon,color)
        content.addView(button.apply {
            setOnClickListener { Ui.tapFeedback(this); task() }
        })
    }

    private fun draw() {
        content.removeAllViews()
        when (tab) {
            1 -> drawHistoryPage()
            2 -> drawGoalsPage()
            3 -> drawMorePage()
            4 -> drawClosingPage()
            else -> drawHome()
        }
        drawNavigation()
    }

    private fun drawHome() {
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
            !canOverlay -> action("Permitir bolinha flutuante",Ui.YELLOW,"settings") {
                AlertDialog.Builder(this)
                    .setTitle("Bolinha do Primo")
                    .setMessage("Na próxima tela, permita ao Primo aparecer sobre outros aplicativos. Depois volte aqui e ative a bolinha.")
                    .setPositiveButton("Abrir configurações") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }.setNegativeButton("Agora não",null).show()
            }
            enabled -> action("Desligar bolinha flutuante",0xFF64748B.toInt(),"settings") {
                stopService(Intent(this, FloatingService::class.java))
                getSharedPreferences("state",MODE_PRIVATE).edit().putBoolean("overlay_running",false).apply()
                draw()
            }
            else -> action("Ativar bolinha flutuante",Ui.YELLOW,"settings") {
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
        showPeriodSelector()
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
        content.addView(Ui.gap(this,16))
        add("Registrar agora",20f,true)
        action("Corrida",Ui.BLUE,"ride") { showEntryForm(EntryTypes.RIDE) }
        action("Gasolina",Ui.YELLOW,"fuel") { showEntryForm(EntryTypes.FUEL) }
        action("Mecânico",Ui.DARK,"repair") { showEntryForm(EntryTypes.REPAIR) }
        showCompactGoal()
        content.addView(Ui.gap(this,18))
        content.addView(Ui.text(this,"Histórico e metas estão no menu inferior.",14f))
        content.addView(Ui.gap(this,25))
    }

    private fun showPeriodSelector() {
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
    }

    private fun drawHistoryPage() {
        add("Histórico",28f,true)
        content.addView(Ui.gap(this,8))
        add("Consulte e corrija seus lançamentos.",14f)
        content.addView(Ui.gap(this,14))
        showPeriodSelector()
        val (start,end,_) = range()
        val entries = db.between(start,end)
        content.addView(Ui.gap(this,15))
        if (entries.isEmpty()) {
            val empty = Ui.column(this,16).apply { background = Ui.rounded(Color.WHITE,this@MainActivity) }
            empty.addView(Ui.text(this,"Ainda não há lançamentos neste período.",16f))
            content.addView(empty)
        } else entries.forEach { entryRow(it) }
        content.addView(Ui.gap(this,20))
    }

    private fun drawGoalsPage() {
        add("Minhas metas",28f,true)
        content.addView(Ui.gap(this,5))
        add("Acompanhe seu objetivo diário e semanal.",14f)
        showGoalCards()
        content.addView(Ui.gap(this,20))
    }

    private fun drawMorePage() {
        add("Mais opções",28f,true)
        content.addView(Ui.gap(this,6))
        add("Configurações e segurança dos seus dados.",14f)
        content.addView(Ui.gap(this,17))
        action("Fechamento financeiro",Ui.BLUE,"history") {
            tab = 4
            draw()
            scroller.scrollTo(0,0)
        }
        content.addView(Ui.gap(this,13))
        val widgetCard = Ui.column(this,16).apply {
            background = Ui.rounded(Color.WHITE,this@MainActivity,18)
        }
        widgetCard.addView(Ui.text(this,"Primo na tela inicial",19f,true))
        widgetCard.addView(Ui.gap(this,6))
        widgetCard.addView(Ui.text(this,
            "Abra a bolinha com um toque, sem precisar entrar no aplicativo.",14f))
        widgetCard.addView(Ui.gap(this,10))
        widgetCard.addView(Ui.iconButton(this,"Adicionar widget","home",Ui.YELLOW).apply {
            setOnClickListener { suggestWidget() }
        })
        content.addView(widgetCard)
        action("Configurar metas e mensagens",Ui.BLUE,"settings") { showGoalsDialog() }
        action("Exportar backup",0xFF475569.toInt(),"backup") { exportBackup() }
        action("Importar backup",0xFF475569.toInt(),"restore") { importBackup() }
        content.addView(Ui.gap(this,18))
        add("Seus registros ficam no aparelho e funcionam sem internet. Exporte um backup regularmente.",14f)
    }

    private fun suggestWidget() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = AppWidgetManager.getInstance(this)
            if (manager.isRequestPinAppWidgetSupported) {
                val component = ComponentName(this,PrimoWidgetProvider::class.java)
                if (manager.requestPinAppWidget(component,null,null)) {
                    Toast.makeText(this,"Confirme a adição do widget na tela inicial.",Toast.LENGTH_LONG).show()
                    return
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Colocar o Primo na tela inicial")
            .setMessage("1. Vá até a tela inicial do celular.\n\n2. Segure um espaço vazio.\n\n3. Toque em Widgets.\n\n4. Procure Primo e adicione.\n\nDepois, um toque no widget abrirá as opções da bolinha.")
            .setPositiveButton("Entendi",null).show()
    }

    /**
     * Fechamento financeiro somente de registros locais: sem alterações na base,
     * e sem confundir faturamento registrado com lucro líquido.
     */
    private fun drawClosingPage() {
        add("Fechamento financeiro",28f,true)
        content.addView(Ui.gap(this,6))
        add("Confira de onde veio o dinheiro e onde gastou.",14f)
        content.addView(Ui.gap(this,12))
        showPeriodSelector()
        val (start,end,periodName) = range()
        val entries = db.between(start,end)
        val report = ClosingReport.calculate(entries)
        content.addView(Ui.gap(this,14))
        card("RECEBIDO — $periodName",Money.show(report.received),
            "${report.rides} corridas • média de ${Money.show(report.averageRide)} por corrida",Ui.GREEN)
        content.addView(Ui.gap(this,8))
        val amounts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        amounts.addView(smallCard("GASTOS",Money.show(report.expenses),Ui.RED),
            LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin = Ui.dp(this@MainActivity,5) })
        amounts.addView(smallCard("SALDO PARCIAL",Money.show(report.balance),Ui.BLUE),
            LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = Ui.dp(this@MainActivity,5) })
        content.addView(amounts)
        content.addView(Ui.gap(this,6))
        add("Saldo parcial = corridas − gastos registrados. Não é lucro líquido.",12f)
        content.addView(Ui.gap(this,6))
        val totalCard = Ui.column(this,13).apply {
            background = Ui.rounded(0xFFEAF3FA.toInt(),this@MainActivity,16)
        }
        totalCard.addView(Ui.text(this,"RESUMO DO PERÍODO",13f,true))
        totalCard.addView(Ui.text(this,
            "${report.rides} corridas • gasolina: ${Money.show(report.fuel)} • mecânico: ${Money.show(report.repair)}",14f))
        content.addView(totalCard)
        showBreakdown("De onde vieram as corridas",report.bySource)
        showBreakdown("Como recebeu pelas corridas",report.byPayment)
        showBreakdown("Gastos registrados",listOf(
            ReportItem("Gasolina",report.fuel,report.fuelCount),
            ReportItem("Mecânico",report.repair,report.repairCount)
        ).filter { it.count > 0 })
        if (entries.isNotEmpty()) {
            content.addView(Ui.gap(this,9))
            add("Levar o fechamento",19f,true)
            action("Salvar Excel (.xlsx)",Ui.GREEN,"backup") { startReportExport("xlsx") }
            action("Compartilhar Excel (.xlsx)",Ui.BLUE,"backup") { shareReport("xlsx") }
            action("Salvar CSV compatível (.csv)",0xFF475569.toInt(),"backup") { startReportExport("csv1252") }
            action("Compartilhar CSV compatível",0xFF475569.toInt(),"backup") { shareReport("csv1252") }
            action("Salvar CSV UTF-8 (.csv)",0xFF64748B.toInt(),"backup") { startReportExport("csv8") }
            add("Recomendado: Excel. CSV compatível usa Windows-1252 para planilhas que exibiam acentos errados. CSV UTF-8 é ideal para importadores que permitem selecionar a codificação.",13f)
        } else {
            content.addView(Ui.gap(this,12))
            add("Sem lançamentos neste período.",14f)
        }
        content.addView(Ui.gap(this,12))
        action("Voltar",0xFF64748B.toInt(),"more") {
            tab = 3; draw(); scroller.scrollTo(0,0)
        }
        content.addView(Ui.gap(this,22))
    }

    private fun showBreakdown(title: String, rows: List<ReportItem>) {
        content.addView(Ui.gap(this,17))
        add(title,20f,true)
        content.addView(Ui.gap(this,8))
        val section = Ui.column(this,14).apply {
            background = Ui.rounded(Color.WHITE,this@MainActivity,15)
        }
        if (rows.isEmpty()) {
            section.addView(Ui.text(this,"Nenhum registro no período.",14f))
        } else rows.forEachIndexed { index, item ->
            if (index > 0) section.addView(Ui.gap(this,10))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val suffix = if (item.count == 1) "1 registro" else "${item.count} registros"
            val labelColumn = Ui.column(this,0).apply {
                addView(Ui.text(this@MainActivity,item.label,16f,true))
                addView(Ui.text(this@MainActivity,suffix,12f))
            }
            row.addView(labelColumn,LinearLayout.LayoutParams(0,-2,1f).apply {
                rightMargin = Ui.dp(this@MainActivity,10)
            })
            row.addView(Ui.text(this,Money.show(item.amount),16f,true).apply {
                setTextColor(if (title == "Gastos registrados") Ui.RED else Ui.BLUE)
                gravity = Gravity.END
                maxLines = 2
            },LinearLayout.LayoutParams(-2,-2))
            section.addView(row)
        }
        content.addView(section)
    }

    private fun startReportExport(kind:String) {
        val (start,end,title)=range()
        if (db.between(start,end).isEmpty()) {
            Toast.makeText(this,"Sem registros no período.",Toast.LENGTH_SHORT).show()
            return
        }
        exportSelection=ExportSelection(kind,start,end,title)
        val ext=if(kind=="xlsx")"xlsx" else "csv"
        val mime=if(kind=="xlsx")
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
        val filename="primo-fechamento-"+LocalDate.now().toString()+
            "-"+period+"-"+kind+"."+ext
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type=mime
            putExtra(Intent.EXTRA_TITLE,filename)
        }
        startActivityForResult(intent,csvExportCode)
    }

    private fun writeReport(out:java.io.OutputStream,kind:String,entries:List<Entry>,title:String) {
        if(kind=="xlsx") PrimoXlsx.write(out,entries,title)
        else {
            val csv=ClosingReport.csv(entries)
            val charset=if(kind=="csv1252") Charset.forName("windows-1252") else Charsets.UTF_8
            out.write(csv.toByteArray(charset))
            out.flush()
        }
    }

    private fun shareReport(kind:String) {
        val (start,end,title)=range()
        val entries=db.between(start,end)
        if (entries.isEmpty()) {
            Toast.makeText(this,"Sem registros no período.",Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val ext=if(kind=="xlsx")"xlsx" else "csv"
            val path=File(cacheDir,"primo_shared")
            if(!path.exists() && !path.mkdirs()) error("Sem pasta para compartilhar")
            val file=File(path,"fechamento."+ext)
            file.outputStream().use { writeReport(it,kind,entries,title) }
            val uri=Uri.parse("content://"+packageName+".exports/fechamento."+ext)
            val mime=if(kind=="xlsx")
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
            val send=Intent(Intent.ACTION_SEND).apply {
                type=mime
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=ClipData.newUri(contentResolver,"Fechamento do Primo",uri)
            }
            startActivity(Intent.createChooser(send,"Compartilhar fechamento"))
        } catch (ex:Exception) {
            AlertDialog.Builder(this).setTitle("Não foi possível compartilhar")
                .setMessage(ex.message ?: "Tente novamente.")
                .setPositiveButton("OK",null).show()
        }
    }

    /** This compact card never shows earnings above the target: the detail remains in Metas. */
    private fun showCompactGoal() {
        val settings = goals.read()
        val today = LocalDate.now()
        val isDaily = settings.dailyEnabled && settings.dailyCents > 0
        val isWeekly = !isDaily && settings.weeklyEnabled && settings.weeklyCents > 0
        if (!isDaily && !isWeekly) return
        val goalStart = if (isDaily) today else GoalMath.startOfWeek(today)
        val goalEnd = if (isDaily) today.plusDays(1) else goalStart.plusDays(7)
        val target = if (isDaily) settings.dailyCents else settings.weeklyCents
        val remaining = GoalMath.remaining(GoalMath.received(db,goalStart,goalEnd),target)
        val block = Ui.column(this,13).apply {
            background = Ui.rounded(Color.WHITE,this@MainActivity,16)
            setOnClickListener { tab = 2; draw(); scroller.scrollTo(0,0) }
        }
        content.addView(Ui.gap(this,11))
        val title = if (isDaily) "Meta do dia" else "Meta da semana"
        val label = Ui.text(this,"$title: ${Money.show(target)}",15f,true)
        block.addView(label)
        val message = if (remaining == 0L) "✓ Meta concluída" else "Faltam ${Money.show(remaining)}"
        block.addView(Ui.text(this,message,17f,true).apply {
            setTextColor(if (remaining==0L) Ui.GREEN else Ui.BLUE)
        })
        content.addView(block)
    }

    private fun drawNavigation() {
        navigation.removeAllViews()
        val items = listOf(Triple("Início","home",0),Triple("Histórico","history",1),
            Triple("Metas","target",2),Triple("Mais","more",3))
        items.forEach { (label,icon,index) ->
            val chosen = tab == index || (tab == 4 && index == 3)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = Ui.rounded(if (chosen) 0xFFFFF4C1.toInt() else Color.WHITE,
                    this@MainActivity,13)
                contentDescription = label
                isFocusable = true
                setOnClickListener {
                    if (tab != index) {
                        tab = index
                        draw()
                        scroller.scrollTo(0,0)
                        content.alpha = 0.5f
                        content.animate().alpha(1f).setDuration(175L).start()
                    }
                }
            }
            val tint = if (chosen) Ui.BLUE else 0xFF64748B.toInt()
            item.addView(ImageView(this).apply {
                setImageDrawable(primoIcon(this@MainActivity,icon,tint,23))
                contentDescription = null
            },LinearLayout.LayoutParams(Ui.dp(this,23),Ui.dp(this,23)))
            item.addView(Ui.text(this,label,12f,chosen).apply {
                setTextColor(tint); gravity = Gravity.CENTER
            })
            navigation.addView(item,LinearLayout.LayoutParams(0,-1,1f).apply {
                setMargins(Ui.dp(this@MainActivity,2),0,Ui.dp(this@MainActivity,2),0)
            })
        }
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
        row.addView(Ui.text(this,"Acompanhar metas",20f,true),
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
            val goalPercent = GoalMath.progress(received,target)
            progress = goalPercent
            if (goalPercent > 0) {
                val bar = this
                bar.post {
                    ObjectAnimator.ofInt(bar,"progress",0,goalPercent).apply {
                        duration = 380L
                        interpolator = DecelerateInterpolator()
                    }.start()
                }
            }
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
        val kindIcon = when (e.kind) {
            EntryTypes.RIDE -> "ride"
            EntryTypes.FUEL -> "fuel"
            else -> "repair"
        }
        card.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(primoIcon(this@MainActivity,kindIcon,Ui.BLUE,21))
                contentDescription = null
            },LinearLayout.LayoutParams(Ui.dp(this@MainActivity,21),Ui.dp(this@MainActivity,21)).apply {
                rightMargin = Ui.dp(this@MainActivity,10)
            })
            addView(Ui.text(this@MainActivity,label,16f,true))
        })
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
                    true
                } catch (ex: Exception) {
                    Toast.makeText(this,"Erro ao salvar: ${ex.message}",Toast.LENGTH_LONG).show()
                    false
                }
            },
            onCancel = { dialog.dismiss() },
            onSaved = {
                dialog.dismiss()
                draw()
                if (goals.read().friendlyMessages) {
                    Toast.makeText(this,"Anotado, primo!",Toast.LENGTH_SHORT).show()
                }
            }
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
        if (resultCode != RESULT_OK) {
            if(requestCode==csvExportCode)exportSelection=null
            return
        }
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                csvExportCode -> {
                    val choice=exportSelection ?: error("Volte ao fechamento e exporte novamente")
                    val entries=db.between(choice.start,choice.end)
                    contentResolver.openOutputStream(uri)?.use {
                        writeReport(it,choice.kind,entries,choice.title)
                    } ?: error("Não foi possível escrever o arquivo")
                    exportSelection=null
                    Toast.makeText(this,"Fechamento exportado!",Toast.LENGTH_LONG).show()
                }
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
