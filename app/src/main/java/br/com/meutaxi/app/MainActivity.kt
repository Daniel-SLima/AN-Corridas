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
    private data class ExportSelection(val start:Long,val end:Long,val title:String)
    private var exportSelection: ExportSelection? = null
    private val tourTargets = mutableMapOf<String,View>()
    private var tutorial: PrimoTour? = null
    private val tutorialPrefs by lazy { getSharedPreferences("primo_tutorial",MODE_PRIVATE) }
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
        // Ask once only; the tutorial itself never touches database or goals.
        content.post {
            if (!isFinishing && !tutorialPrefs.getBoolean("first_prompt_09",false)) {
                tutorialPrefs.edit().putBoolean("first_prompt_09",true).apply()
                AlertDialog.Builder(this)
                    .setTitle("Conheça o Primo!")
                    .setMessage("O Primo te mostra os botões com um brilho na tela. Quer aprender agora? Não vamos salvar nenhum dado durante o guia.")
                    .setPositiveButton("Começar") { _,_ -> startPrimoTour(PrimoTour.Kind.APP) }
                    .setNegativeButton("Agora não",null).show()
            }
        }
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
        val guideKey = when(label) {
            "Corrida" -> "home.ride"
            "Gasolina" -> "home.fuel"
            "Mecânico" -> "home.repair"
            "Exportar backup" -> "more.backup"
            else -> null
        }
        if(guideKey!=null)tourTargets[guideKey]=button
    }

    private fun sectionAction(panel:LinearLayout,label:String,icon:String,
        primary:Boolean=false,guide:String?=null,onClick:()->Unit) {
        panel.addView(Ui.gap(this,8))
        val control=Ui.sectionButton(this,label,icon,primary).apply {
            setOnClickListener { Ui.tapFeedback(this); onClick() }
        }
        panel.addView(control,LinearLayout.LayoutParams(-1,Ui.dp(this,52)))
        if(guide!=null)tourTargets[guide]=control
    }

    private fun draw() {
        tourTargets.clear()
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
        action("Resumo do dia",Ui.BLUE,"history") { showDailySummary() }
        content.addView(Ui.gap(this,12))
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
            val empty = Ui.panel(this,16)
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

        val (startReport,endReport,titleReport)=range()
        val report=ClosingReport.calculate(db.between(startReport,endReport))
        val closing=Ui.panel(this)
        closing.addView(Ui.sectionTitle(this,"Fechamento financeiro"))
        closing.addView(Ui.gap(this,5))
        closing.addView(Ui.text(this,"Período: "+titleReport+" • "+report.rides+
            " corridas • "+Money.show(report.received)+" recebidos",14f))
        closing.addView(Ui.gap(this,13))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf(Triple("Ver","eye",0),Triple("Baixar","download",1),
            Triple("Compartilhar","share",2)).forEachIndexed { i,(label,icon,task) ->
            val tile=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                background=Ui.rounded(if(i==0)0xFFFFF4C1.toInt() else
                    0xFFEAF3FA.toInt(),this@MainActivity,13)
                contentDescription=label+" fechamento"
                isFocusable=true
                setOnClickListener {
                    Ui.tapFeedback(this)
                    when(task) {
                        0 -> {tab=4;draw();scroller.scrollTo(0,0)}
                        1 -> downloadClosingCsv()
                        else -> shareClosingCsv()
                    }
                }
            }
            tile.addView(ImageView(this).apply {
                setImageDrawable(primoIcon(this@MainActivity,icon,Ui.BLUE,23))
                contentDescription=null
            },LinearLayout.LayoutParams(Ui.dp(this,23),Ui.dp(this,23)))
            tile.addView(Ui.gap(this,5))
            tile.addView(Ui.text(this,label,13f,true).apply {
                gravity=Gravity.CENTER;setTextColor(Ui.BLUE);maxLines=1
            })
            row.addView(tile,LinearLayout.LayoutParams(0,Ui.dp(this,78),1f).apply {
                if(i>0)leftMargin=Ui.dp(this@MainActivity,6)
            })
        }
        closing.addView(row)
        content.addView(closing)
        tourTargets["more.closing"]=closing
        content.addView(Ui.gap(this,12))

        val correct=Ui.panel(this)
        correct.addView(Ui.sectionTitle(this,"Corrigir registros"))
        correct.addView(Ui.gap(this,5))
        correct.addView(Ui.text(this,
            "Para corrigir valores, use o Histórico ou confira o último lançamento.",14f))
        sectionAction(correct,"Desfazer último lançamento","history") { confirmUndoLastEntry() }
        content.addView(correct)
        content.addView(Ui.gap(this,12))

        drawPilotReadiness()
        content.addView(Ui.gap(this,12))

        val metas=Ui.panel(this)
        metas.addView(Ui.sectionTitle(this,"Metas e preferências"))
        metas.addView(Ui.gap(this,5))
        metas.addView(Ui.text(this,
            "Escolha a meta do dia ou da semana.",14f))
        sectionAction(metas,"Configurar metas","target") { showGoalsDialog() }
        content.addView(metas)
        content.addView(Ui.gap(this,12))

        val learn=Ui.panel(this)
        learn.addView(Ui.sectionTitle(this,"Aprenda com o Primo"))
        learn.addView(Ui.gap(this,5))
        learn.addView(Ui.text(this,
            "O mascote explica os botões. Reveja quando quiser.",14f))
        sectionAction(learn,"Tutorial do aplicativo","home",true) {
            startPrimoTour(PrimoTour.Kind.APP)
        }
        sectionAction(learn,"Tutorial da bolinha","target") {
            startPrimoTour(PrimoTour.Kind.BUBBLE)
        }
        content.addView(learn)
        content.addView(Ui.gap(this,12))

        val safety=Ui.panel(this)
        safety.addView(Ui.sectionTitle(this,"Segurança dos dados"))
        safety.addView(Ui.gap(this,5))
        val last=getSharedPreferences("primo_pilot",MODE_PRIVATE)
            .getLong("last_backup_at",0L)
        val lastText=if(last<=0L) "Último backup: ainda não registrado neste aparelho"
            else "Último backup: "+SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR"))
                .format(Date(last))
        safety.addView(Ui.text(this,lastText,14f))
        safety.addView(Ui.gap(this,4))
        safety.addView(Ui.text(this,
            "Seus registros ficam neste aparelho e funcionam sem internet.",13f))
        sectionAction(safety,"Exportar backup","backup",true,"more.backup") {
            exportBackup()
        }
        sectionAction(safety,"Importar backup","restore") { importBackup() }
        content.addView(safety)
        content.addView(Ui.gap(this,22))
    }

    /**
     * Informational pilot controls: do not promise the Android service will be
     * kept alive by Xiaomi/HyperOS battery management.
     */
    private fun drawPilotReadiness() {
        val panel=Ui.panel(this)
        panel.addView(Ui.sectionTitle(this,"Bolinha e tela inicial"))
        panel.addView(Ui.gap(this,5))
        val allowed=Settings.canDrawOverlays(this)
        val requested=getSharedPreferences("state",MODE_PRIVATE)
            .getBoolean("overlay_running",false)
        val status=when {
            !allowed -> "Bolinha: permissão não autorizada"
            requested -> "Bolinha: ativada (confira se aparece)"
            else -> "Bolinha: desativada"
        }
        panel.addView(Ui.text(this,status,14f))
        panel.addView(Ui.gap(this,4))
        panel.addView(Ui.text(this,
            "Registre rapidamente usando o + amarelo.",13f))
        sectionAction(panel,"Reativar bolinha","settings",true) { reactivateBubble() }
        sectionAction(panel,"Dicas sobre bateria","settings") { showBatteryAdvice() }
        sectionAction(panel,"Adicionar widget","home") { suggestWidget() }
        content.addView(panel)
    }

    private fun reactivateBubble() {
        if(!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Autorizar a bolinha")
                .setMessage("Permita que o Primo apareça sobre outros aplicativos.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Abrir permissão") { _,_ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }.show()
            return
        }
        try {
            startForegroundService(Intent(this,FloatingService::class.java))
            Toast.makeText(this,"Bolinha solicitada. Verifique se ela apareceu.",Toast.LENGTH_LONG).show()
            // The service sets the real requested state after the overlay is added.
            content.postDelayed({ if(!isFinishing && tab==3) draw() },550L)
        } catch(ex:Exception) {
            AlertDialog.Builder(this).setTitle("Não foi possível abrir a bolinha")
                .setMessage(ex.message ?: "Abra as configurações do aplicativo e tente novamente.")
                .setPositiveButton("OK",null).show()
        }
    }

    private fun showBatteryAdvice() {
        AlertDialog.Builder(this).setTitle("Bolinha durante o expediente")
            .setMessage(
                "Se a bolinha sumir, volte ao Primo ou toque no widget para reativá-la.\n\n"+
                "No Xiaomi/Redmi, confira as configurações de bateria do aplicativo e "+
                "permita a atividade em segundo plano quando essa opção existir.\n\n"+
                "O Android ainda pode interromper serviços por segurança ou bateria. "+
                "Não use o celular enquanto dirige."
            )
            .setNegativeButton("Fechar",null)
            .setPositiveButton("Configurações do Primo") { _,_ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")))
                } catch(_ : Exception) {
                    Toast.makeText(this,"Abra Configurações > Aplicativos > Primo.",
                        Toast.LENGTH_LONG).show()
                }
            }.show()
    }


    private fun startPrimoTour(mode: PrimoTour.Kind) {
        tutorial?.dismiss()
        // The bubble lesson is a mock, not the actual SYSTEM_ALERT_WINDOW overlay.
        if(mode==PrimoTour.Kind.APP && tab!=0) {
            tab=0; draw(); scroller.scrollTo(0,0)
        }
        val tour=PrimoTour(this,mode,
            target={ key -> tourTargets[key] },
            goToTab={ dest ->
                if(tab!=dest) {
                    tab=dest
                    draw()
                    scroller.scrollTo(0,0)
                }
            },
            onFinish={ finishedMode,completed ->
                tutorial=null
                if(completed && finishedMode==PrimoTour.Kind.APP && !isFinishing) {
                    AlertDialog.Builder(this)
                        .setTitle("Quer conhecer a bolinha?")
                        .setMessage("O Primo também tem um tutorial separado para o botão + flutuante. É só uma demonstração, sem salvar nada.")
                        .setNegativeButton("Depois",null)
                        .setPositiveButton("Quero ver") { _,_ ->
                            startPrimoTour(PrimoTour.Kind.BUBBLE)
                        }.show()
                }
            }
        )
        tutorial=tour
        tour.start()
    }

    @Deprecated("Handled to safely exit the tutorial before navigating back")
    override fun onBackPressed() {
        val guide=tutorial
        if(guide!=null) {
            guide.dismiss()
            tutorial=null
        } else super.onBackPressed()
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
        content.addView(Ui.gap(this,8))
        val topActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        topActions.addView(Ui.iconButton(this,"Baixar","download",Ui.BLUE).apply {
            textSize=14f; setOnClickListener { downloadClosingCsv() }
        },LinearLayout.LayoutParams(0,Ui.dp(this,52),1f).apply {
            rightMargin=Ui.dp(this@MainActivity,5)
        })
        topActions.addView(Ui.iconButton(this,"Compartilhar","share",Ui.BLUE).apply {
            textSize=14f; setOnClickListener { shareClosingCsv() }
        },LinearLayout.LayoutParams(0,Ui.dp(this,52),1f).apply {
            leftMargin=Ui.dp(this@MainActivity,5)
        })
        content.addView(topActions)
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
        val totalCard = Ui.panel(this,13)
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
        if (entries.isEmpty()) {
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
        val section = Ui.panel(this,14)
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

    private fun csvBytes(items:List<Entry>,start:Long,end:Long,title:String):ByteArray =
        ClosingReport.csv(items,start,end,title).toByteArray(Charset.forName("windows-1252"))

    private fun downloadClosingCsv() {
        val (start,end,title)=range()
        if(db.between(start,end).isEmpty()) {
            Toast.makeText(this,"Sem registros neste período.",Toast.LENGTH_SHORT).show()
            return
        }
        exportSelection=ExportSelection(start,end,title)
        val filename=ClosingReport.fileName(start,end)
        val picker=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,filename)
        }
        startActivityForResult(picker,csvExportCode)
    }

    private fun shareClosingCsv() {
        val (start,end,title)=range()
        val items=db.between(start,end)
        if(items.isEmpty()) {
            Toast.makeText(this,"Sem registros neste período.",Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val directory=File(cacheDir,"primo_shared")
            if(!directory.exists() && !directory.mkdirs())error("Não foi possível preparar o arquivo")
            val filename=ClosingReport.fileName(start,end)
            File(directory,filename).writeBytes(csvBytes(items,start,end,title))
            val uri=Uri.parse("content://"+packageName+".exports/"+filename)
            val send=Intent(Intent.ACTION_SEND).apply {
                type="text/csv"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=ClipData.newUri(contentResolver,filename,uri)
            }
            startActivity(Intent.createChooser(send,"Compartilhar fechamento"))
        } catch(ex:Exception) {
            AlertDialog.Builder(this).setTitle("Não foi possível compartilhar")
                .setMessage(ex.message ?: "Tente novamente")
                .setPositiveButton("OK",null).show()
        }
    }

    /** Read-only summary, never clears a driver's financial history. */
    private fun showDailySummary() {
        val date=LocalDate.now()
        val day=ClosingReport.calculate(db.between(date.atStartMillis(),date.plusDays(1).atStartMillis()))
        val text="Dia: "+date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+
            "\nCorridas: "+day.rides+
            "\nRecebido: "+Money.show(day.received)+
            "\nGasolina: "+Money.show(day.fuel)+
            "\nMecânico: "+Money.show(day.repair)+
            "\nGastos totais: "+Money.show(day.expenses)+
            "\nSaldo parcial: "+Money.show(day.balance)+
            "\n\nEste saldo não inclui despesas não registradas."
        val latestToday=db.between(date.atStartMillis(),date.plusDays(1).atStartMillis())
            .maxByOrNull { it.id }
        val builder=AlertDialog.Builder(this).setTitle("Resumo do dia")
            .setMessage(text).setPositiveButton("Fechar",null)
        if(latestToday!=null) {
            builder.setNeutralButton("Corrigir última") { _,_ ->
                showEntryForm(latestToday.kind,latestToday)
            }
        }
        builder.show()
    }

    /** Require confirmation to prevent accidental loss of the newest entry. */
    private fun confirmUndoLastEntry() {
        val last=db.latest()
        if(last==null) {
            Toast.makeText(this,"Ainda não há lançamentos.",Toast.LENGTH_SHORT).show()
            return
        }
        val moment=java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR"))
            .format(java.util.Date(last.createdAt))
        AlertDialog.Builder(this).setTitle("Desfazer último lançamento?")
            .setMessage(EntryTypes.name(last.kind)+" de "+Money.show(last.cents)+
                "\n"+moment+"\n\nEsse lançamento será excluído. Confirme somente se foi registrado por engano.")
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Editar") { _,_ -> showEntryForm(last.kind,last) }
            .setPositiveButton("Excluir registro") { _,_ ->
                if(db.delete(last.id)) {
                    draw()
                    Toast.makeText(this,"Registro excluído.",Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this,"Registro não encontrado.",Toast.LENGTH_SHORT).show()
            }.show()
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
        val block = Ui.panel(this,13).apply {
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
            tourTargets["nav."+when(index) {
                0 -> "home"
                1 -> "history"
                2 -> "goals"
                else -> "more"
            }]=item
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
        val editGoal = Ui.button(this,"Editar",Ui.BLUE).apply {
            textSize=13f
            setOnClickListener { showGoalsDialog() }
        }
        tourTargets["goals.edit"]=editGoal
        row.addView(editGoal,LinearLayout.LayoutParams(Ui.dp(this,85),Ui.dp(this,43)))
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
        val block = Ui.panel(this,14)
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
        val card = Ui.panel(this,14)
        card.addView(Ui.text(this,label,13f,true))
        card.addView(Ui.gap(this,5))
        card.addView(Ui.text(this,amount,27f,true).apply { setTextColor(color) })
        card.addView(Ui.text(this,foot,13f))
        content.addView(card)
    }

    private fun entryRow(e: Entry) {
        val card = Ui.panel(this,12)
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
                        it.write(csvBytes(entries,choice.start,choice.end,choice.title))
                        it.flush()
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
                    getSharedPreferences("primo_pilot",MODE_PRIVATE).edit()
                        .putLong("last_backup_at",System.currentTimeMillis()).apply()
                    if(tab==3) draw()
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
                    // A previous export timestamp refers to a different data set.
                    getSharedPreferences("primo_pilot",MODE_PRIVATE).edit()
                        .remove("last_backup_at").apply()
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
