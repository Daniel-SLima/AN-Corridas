package br.com.meutaxi.app

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Interactive, read-only spotlight tutorials: never dispatch taps to real buttons. */
class PrimoTour(
    private val activity:Activity,
    private val kind:Kind,
    private val target:(String)->View?,
    private val goToTab:(Int)->Unit,
    private val onFinish:(Kind,Boolean)->Unit
) {
    enum class Kind { APP, BUBBLE }
    data class Step(val tab:Int,val key:String?,val title:String,val speech:String)
    companion object {
        val APP=listOf(
            Step(0,null,"Oi, primo!","Vou te ensinar rapidinho. Toque no brilho ou em Próximo. Nada será salvo."),
            Step(0,"home.ride","Corrida","Recebeu uma corrida? Registre aqui o valor."),
            Step(0,"home.fuel","Gasolina","Abasteceu? Coloque quanto gastou."),
            Step(0,"home.repair","Mecânico","Anote os gastos e o serviço feito na oficina."),
            Step(0,"nav.goals","Metas","Aqui você acompanha seu objetivo do dia e da semana."),
            Step(2,"goals.edit","Configurar meta","Toque em Editar para escolher o valor. Não vamos alterar nada agora."),
            Step(2,"nav.history","Histórico","Veja corridas e gastos antigos. Também pode corrigir valores."),
            Step(1,"nav.more","Mais opções","Aqui ficam o fechamento, o widget e o backup."),
            Step(3,"more.closing","Fechamento","Ver, baixar ou compartilhar: escolha o que precisar."),
            Step(3,"more.backup","Backup","Guarde uma cópia dos seus registros de tempos em tempos."),
            Step(3,null,"Terminamos!","Pronto, primo! Quando quiser, é só rever o tutorial na aba Mais.")
        )
        val BUBBLE=listOf(
            Step(0,null,"Bolinha flutuante","Este exemplo é só para aprender. Nenhuma corrida será registrada."),
            Step(0,"bubble.plus","Abrir o menu","Toque no + amarelo para abrir as opções."),
            Step(0,"bubble.menu","Três atalhos","Corrida, Gasolina e Mecânico: escolha o que deseja registrar."),
            Step(0,"bubble.goal","Meta e X","A meta diz o quanto falta. O X desliga a bolinha."),
            Step(0,"bubble.plus","Arrastar","Segure e arraste o +. Assim ele não atrapalha outros aplicativos."),
            Step(0,null,"Tudo certo!","Para usar de verdade, ative a bolinha no Início ou pelo widget.")
        )
    }
    private val steps=if(kind==Kind.APP)APP else BUBBLE
    private var index=0
    private var closed=false
    private val root=activity.findViewById<FrameLayout>(android.R.id.content)
    private val overlay=FrameLayout(activity)
    private val shade=Spotlight(activity)
    private val card=LinearLayout(activity)
    private var focus:RectF?=null
    private var mock:FrameLayout?=null
    private var mockPlus:TextView?=null
    private var mockMenu:LinearLayout?=null
    private var mockGoal:TextView?=null
    private fun dp(v:Int)=Ui.dp(activity,v)

    fun start() {
        if(overlay.parent!=null)return
        overlay.isClickable=true
        overlay.isFocusable=true
        overlay.contentDescription="Tutorial guiado do Primo"
        overlay.addView(shade,FrameLayout.LayoutParams(-1,-1))
        shade.setOnTouchListener { _,e ->
            if(e.actionMasked==MotionEvent.ACTION_UP && focus?.contains(e.x,e.y)==true)next()
            true
        }
        root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        render()
    }
    private fun render() {
        if(closed)return
        val s=steps[index]
        if(kind==Kind.APP) {
            mock?.let { overlay.removeView(it) }
            mock=null
            goToTab(s.tab)
        } else addMock()
        card.removeAllViews()
        (card.parent as? ViewGroup)?.removeView(card)
        card.orientation=LinearLayout.VERTICAL
        card.setPadding(dp(15),dp(13),dp(15),dp(13))
        card.background=Ui.rounded(Color.WHITE,activity,18)
        card.elevation=dp(14).toFloat()
        val head=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        val avatar=FrameLayout(activity).apply {
            background=GradientDrawable().apply {
                shape=GradientDrawable.OVAL
                setColor(0xFFFFE49E.toInt())
            }
        }
        avatar.addView(ImageView(activity).apply {
            setImageResource(R.drawable.primo_mascot)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5),dp(5),dp(5),dp(5))
            contentDescription="Mascote Primo"
        },FrameLayout.LayoutParams(-1,-1))
        avatar.addView(TextView(activity).apply {
            text=arrayOf("🙂","😄","😉","😊","😎","🎉")[index%6]
            textSize=13f; gravity=Gravity.CENTER
        },FrameLayout.LayoutParams(dp(21),dp(21),Gravity.END or Gravity.BOTTOM))
        head.addView(avatar,LinearLayout.LayoutParams(dp(56),dp(56)))
        val names=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        names.addView(Ui.text(activity,"PRIMO EXPLICA",11f,true).apply { setTextColor(Ui.BLUE) })
        names.addView(Ui.text(activity,s.title,19f,true))
        head.addView(names,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(12) })
        card.addView(head)
        card.addView(Ui.gap(activity,9))
        card.addView(Ui.text(activity,s.speech,15f).apply { setLineSpacing(dp(2).toFloat(),1f) })
        card.addView(Ui.gap(activity,9))
        val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(activity,(index+1).toString()+" de "+steps.size,12f).apply {
            setTextColor(0xFF64748B.toInt())
        },LinearLayout.LayoutParams(0,-2,1f))
        fun button(label:String,width:Int,background:Int,ink:Int,onClick:()->Unit):Button =
            Button(activity).apply {
                text=label; isAllCaps=false; textSize=12f
                minHeight=dp(43); setTextColor(ink)
                this.background=Ui.rounded(background,activity,11)
                setOnClickListener { onClick() }
            }.also { row.addView(it,LinearLayout.LayoutParams(dp(width),dp(43)).apply { leftMargin=dp(3) }) }
        button("Voltar",67,0xFFEAF3FA.toInt(),Ui.BLUE) { move(-1) }.apply {
            isEnabled=index>0; alpha=if(index==0)0.35f else 1f
        }
        button("Pular",58,Color.WHITE,Ui.BLUE) { finish(false) }
        button(if(index==steps.lastIndex)"Concluir" else "Próximo",88,Ui.BLUE,Color.WHITE) { next() }
        card.addView(row)
        overlay.addView(card,FrameLayout.LayoutParams(-2,-2))
        overlay.post { updateFocus() }
    }
    private fun addMock() {
        mock?.let { overlay.removeView(it) }
        val group=FrameLayout(activity)
        mock=group
        overlay.addView(group,FrameLayout.LayoutParams(dp(250),dp(260),
            Gravity.TOP or Gravity.END).apply { topMargin=dp(25); rightMargin=dp(12) })
        val menu=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9),dp(9),dp(9),dp(9))
            background=Ui.rounded(0xFFEAF3FA.toInt(),15)
            elevation=dp(7).toFloat()
            visibility=if(index>=2)View.VISIBLE else View.INVISIBLE
        }
        listOf("Corrida","Gasolina","Mecânico").forEach {
            menu.addView(Ui.text(activity,it,16f,true).apply {
                gravity=Gravity.CENTER; background=Ui.rounded(Color.WHITE,activity,9)
                setOnClickListener { next() }
            },LinearLayout.LayoutParams(-1,dp(39)).apply { bottomMargin=dp(4) })
        }
        val goal=Ui.text(activity,"Meta: R$ 80,00        ×",13f,true).apply {
            gravity=Gravity.CENTER
            background=Ui.rounded(0xFFFFF0BC.toInt(),activity,9)
            setOnClickListener { next() }
        }
        menu.addView(goal,LinearLayout.LayoutParams(-1,dp(39)))
        group.addView(menu,FrameLayout.LayoutParams(dp(190),-2,
            Gravity.TOP or Gravity.START).apply { topMargin=dp(24) })
        mockMenu=menu;mockGoal=goal
        val plus=TextView(activity).apply {
            text="+"; textSize=38f; gravity=Gravity.CENTER
            setTextColor(Ui.DARK)
            background=GradientDrawable().apply {
                shape=GradientDrawable.OVAL; setColor(Ui.YELLOW)
            }
            elevation=dp(8).toFloat()
            contentDescription="Bolinha demonstrativa"
        }
        group.addView(plus,FrameLayout.LayoutParams(dp(58),dp(58),
            Gravity.TOP or Gravity.END).apply { topMargin=dp(20) })
        mockPlus=plus
        var lastX=0f;var lastY=0f
        plus.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {lastX=event.rawX;lastY=event.rawY;true}
                MotionEvent.ACTION_MOVE -> {
                    if(index==4) {
                        plus.translationX=(plus.translationX+event.rawX-lastX)
                            .coerceIn(-dp(155).toFloat(),0f)
                        plus.translationY=(plus.translationY+event.rawY-lastY)
                            .coerceIn(0f,dp(160).toFloat())
                        lastX=event.rawX;lastY=event.rawY
                        overlay.post { updateFocus() }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { next();true }
                else -> true
            }
        }
    }
    private fun findTarget():View? {
        val key=steps[index].key ?: return null
        if(kind==Kind.APP) return target(key)
        return when(key) {
            "bubble.plus" -> mockPlus
            "bubble.menu" -> mockMenu
            "bubble.goal" -> mockGoal
            else -> null
        }
    }
    private fun updateFocus() {
        if(closed || overlay.width<1)return
        val view=findTarget()
        if(kind==Kind.APP && view!=null) view.requestRectangleOnScreen(
            Rect(0,0,view.width,view.height),true)
        overlay.post {
            if(closed)return@post
            val v=findTarget()
            focus=if(v!=null && v.isShown && v.width>0 && v.height>0) {
                val pos=IntArray(2);val parent=IntArray(2)
                v.getLocationOnScreen(pos);overlay.getLocationOnScreen(parent)
                RectF((pos[0]-parent[0]-dp(5)).toFloat(),(pos[1]-parent[1]-dp(5)).toFloat(),
                    (pos[0]-parent[0]+v.width+dp(5)).toFloat(),
                    (pos[1]-parent[1]+v.height+dp(5)).toFloat())
            } else null
            shade.hole=focus;shade.invalidate()
            val width=(overlay.width-dp(24)).coerceAtMost(dp(390))
            card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val h=card.measuredHeight
            val rect=focus
            val below=(rect?.bottom?.toInt() ?: ((overlay.height-h)/2))+dp(13)
            val above=(rect?.top?.toInt() ?: 0)-h-dp(13)
            val top=when {
                rect==null -> (overlay.height-h)/2
                below+h<overlay.height-dp(8) -> below
                above>dp(8) -> above
                else -> (overlay.height-h)/2
            }.coerceIn(dp(4),(overlay.height-h-dp(4)).coerceAtLeast(dp(4)))
            card.layoutParams=FrameLayout.LayoutParams(width,-2,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=top }
            card.bringToFront()
        }
    }
    private fun move(delta:Int) {
        val targetIndex=index+delta
        if(closed || targetIndex !in steps.indices)return
        index=targetIndex
        render()
    }
    private fun next() {
        if(index==steps.lastIndex)finish(true) else move(1)
    }
    private fun finish(done:Boolean) {
        if(closed)return
        closed=true
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        onFinish(kind,done)
    }
    fun dismiss() { finish(false) }

    private class Spotlight(activity:Activity):View(activity) {
        var hole:RectF?=null
        private val dim=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xD1001428.toInt() }
        private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Ui.YELLOW;style=Paint.Style.STROKE
            strokeWidth=Ui.dp(activity,3).toFloat()
        }
        override fun onDraw(canvas:Canvas) {
            val r=hole
            if(r==null) {
                canvas.drawColor(dim.color)
            } else {
                val path=Path().apply {
                    fillType=Path.FillType.EVEN_ODD
                    addRect(0f,0f,width.toFloat(),height.toFloat(),Path.Direction.CW)
                    addRoundRect(r,Ui.dp(context,12).toFloat(),
                        Ui.dp(context,12).toFloat(),Path.Direction.CCW)
                }
                canvas.drawPath(path,dim)
                canvas.drawRoundRect(r,Ui.dp(context,12).toFloat(),
                    Ui.dp(context,12).toFloat(),edge)
            }
        }
    }
}
