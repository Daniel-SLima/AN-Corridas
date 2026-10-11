package br.com.meutaxi.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** A small coherent outlined icon set. Drawn locally; no font, network or third-party assets. */
class PrimoIcon(private val kind: String, private val ink: Int, private val px: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.05f
    }
    override fun getIntrinsicWidth(): Int = px
    override fun getIntrinsicHeight(): Int = px
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    @Deprecated("Android Drawable compatibility")
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Android Drawable compatibility")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg coords: Float) {
            val path = Path().apply {
                moveTo(coords[0], coords[1]); var i = 2
                while (i + 1 < coords.size) { lineTo(coords[i],coords[i+1]); i += 2 }
            }
            canvas.drawPath(path,paint)
        }
        fun box(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 2f) {
            canvas.drawRoundRect(RectF(left,top,right,bottom),radius,radius,paint)
        }
        when (kind) {
            "ride" -> {
                line(4f,13f,6.4f,7.2f,8f,6f,16f,6f,17.6f,7.2f,20f,13f)
                box(3f,12f,21f,19f,2f)
                line(6f,15f,8f,15f); line(16f,15f,18f,15f)
                line(6f,19f,6f,21f); line(18f,19f,18f,21f)
            }
            "fuel" -> {
                box(4f,4f,14.5f,21f,1.5f)
                box(6f,6.5f,12.5f,11f,0.8f)
                line(6f,17f,12.5f,17f)
                line(14.5f,9f,17f,11.2f,17f,17f)
                canvas.drawArc(RectF(17f,15.5f,21.5f,20.5f),0f,190f,false,paint)
                line(17f,4.5f,20f,7.5f)
            }
            "repair" -> {
                val p=Path().apply {
                    moveTo(14.5f,4f); lineTo(17.5f,7f); lineTo(15.3f,9.2f)
                    lineTo(12.4f,8.4f); lineTo(11.4f,5.4f)
                    cubicTo(7f,5f,5f,8.4f,6f,12f)
                    lineTo(3.7f,17f); cubicTo(2.5f,20.5f,6.5f,22f,8.4f,19f)
                    lineTo(12.5f,14f); cubicTo(16.7f,15f,20.7f,10.5f,19f,6.4f)
                    close()
                }
                canvas.drawPath(p,paint)
                canvas.drawCircle(6f,18f,0.8f,paint)
            }
            "target" -> {
                canvas.drawCircle(11f,13f,8f,paint)
                canvas.drawCircle(11f,13f,4f,paint)
                canvas.drawCircle(11f,13f,1f,paint)
                line(11f,13f,21f,3f); line(17f,3f,21f,3f,21f,7f)
            }
            "history" -> {
                canvas.drawCircle(12f,12f,9f,paint)
                line(12f,6.5f,12f,12f,16f,14.3f)
            }
            "home" -> {
                line(3f,11f,12f,3.5f,21f,11f)
                line(5.5f,10.5f,5.5f,21f,18.5f,21f,18.5f,10.5f)
                line(10f,21f,10f,15f,14f,15f,14f,21f)
            }
            "more" -> {
                for (x in listOf(5f,12f,19f)) {
                    paint.style = Paint.Style.FILL; canvas.drawCircle(x,12f,1.5f,paint)
                }
                paint.style = Paint.Style.STROKE
            }
            "backup" -> {
                line(4f,16f,4f,21f,20f,21f,20f,16f)
                line(12f,17f,12f,3f); line(7f,8f,12f,3f,17f,8f)
            }
            "restore" -> {
                line(4f,8f,4f,3f,9f,3f)
                canvas.drawArc(RectF(4f,4f,21f,21f),200f,286f,false,paint)
                line(4f,3f,9f,8f)
            }
            "settings" -> {
                canvas.drawCircle(12f,12f,4f,paint)
                canvas.drawCircle(12f,12f,9f,paint)
                line(12f,1.5f,12f,4f);line(12f,20f,12f,22.5f)
                line(1.5f,12f,4f,12f);line(20f,12f,22.5f,12f)
            }
            "check" -> line(4f,12.5f,10f,18f,20f,6f)
            "calendar" -> {
                box(3f,5f,21f,21f,2f)
                line(3f,10f,21f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f)
            }
            else -> canvas.drawCircle(12f,12f,8f,paint)
        }
        canvas.restore()
    }
}

fun primoIcon(context: Context, kind: String, tint: Int, dp: Int = 22): Drawable =
    PrimoIcon(kind,tint,Ui.dp(context,dp))
