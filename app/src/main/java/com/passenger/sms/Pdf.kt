package com.passenger.sms

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat

object Pdf {
    fun export(ctx: Context, uri: Uri, title: String, msgs: List<Msg>, t: GlassTheme) {
        val doc = PdfDocument()
        val pw = 595
        val ph = 842
        val m = 36
        val width = pw - 2 * m
        val tf = ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular)
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG)
        body.textSize = 12f; body.color = android.graphics.Color.BLACK; body.typeface = tf
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG)
        head.textSize = 9f; head.color = android.graphics.Color.GRAY; head.typeface = tf
        val ttl = TextPaint(Paint.ANTI_ALIAS_FLAG)
        ttl.textSize = 16f; ttl.color = android.graphics.Color.BLACK; ttl.typeface = tf

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
        var canvas = page.canvas
        var y = m.toFloat()

        fun layout(text: String, p: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.15f)
                .build()

        fun ensure(h: Float) {
            if (y + h > ph - m) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
                canvas = page.canvas
                y = m.toFloat()
            }
        }

        fun draw(l: StaticLayout) {
            canvas.save()
            canvas.translate(m.toFloat(), y)
            l.draw(canvas)
            canvas.restore()
            y += l.height
        }

        draw(layout(title, ttl))
        y += 12f
        for (msg in msgs.sortedBy { it.date }) {
            val who = if (msg.type == 1) title else T("من", "Me")
            val h = layout(who + "  —  " + Fmt.time(msg.date, t, withDate = true), head)
            val b = layout(msg.body, body)
            ensure((h.height + b.height + 8).toFloat())
            draw(h)
            draw(b)
            y += 8f
        }
        doc.finishPage(page)
        ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }
}
