package com.passenger.sms

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.util.Locale

data class Found(val kind: String, val value: String)

object Detect {
    private val cardRe = Regex("(?<!\\d)(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})(?!\\d)")
    private val amountRe = Regex("(?<![\\d,])\\d{1,3}(?:,\\d{3})+(?![\\d,])")
    private val trackRe = Regex("(?:کد\\s*رهگیری|شماره\\s*مرسوله|رهگیری|tracking)[^\\d]{0,20}(\\d{8,24})", RegexOption.IGNORE_CASE)
    private val intlRe = Regex("\\b[A-Z]{2}\\d{9}[A-Z]{2}\\b")
    private val addrRe = Regex("(?:آدرس|نشانی)\\s*[:：]?\\s*([^\\n]{8,120})")

    fun find(body: String): List<Found> {
        val s = Fmt.norm(body)
        val out = ArrayList<Found>()
        cardRe.find(s)?.let { out.add(Found("card", it.value.filter { c -> c.isDigit() })) }
        amountRe.find(s)?.let { out.add(Found("amount", it.value)) }
        val tr = trackRe.find(s)
        if (tr != null) out.add(Found("track", tr.groupValues[1]))
        else intlRe.find(s)?.let { out.add(Found("track", it.value)) }
        addrRe.find(body)?.let { out.add(Found("address", it.groupValues[1].trim())) }
        return out
    }

    private val unsubRe = Regex("(?:لغو|cancel|stop)\\s*([0-9۰-۹]{1,3})", RegexOption.IGNORE_CASE)

    /** Returns the code to send back (e.g. "11") when the message looks like a paid-subscription notice. */
    fun unsubscribeCode(address: String, body: String): String? {
        val m = unsubRe.find(body) ?: return null
        val isService = !address.any { it.isLetter() } && !address.startsWith("+") && !address.startsWith("09") && !address.startsWith("0")
        if (!isService && !body.contains("عضویت") && !body.contains("اشتراک") && !body.contains("subscri", true)) return null
        return Fmt.norm(m.groupValues[1])
    }
}

object LinkCheck {
    private val urlRe = Regex("(https?://[^\\s]+|www\\.[^\\s]+)", RegexOption.IGNORE_CASE)
    private val hostRe = Regex("^(?:https?://)?([^/:?#]+)", RegexOption.IGNORE_CASE)
    private val ipRe = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
    private val shorteners = listOf("bit.ly", "tinyurl", "cutt.ly", "t.co", "is.gd", "goo.gl", "shorturl", "rebrand.ly", "ow.ly", "tiny.cc", "s.id")
    private val badTlds = listOf(".xyz", ".top", ".icu", ".click", ".buzz", ".cfd", ".sbs", ".rest", ".gq", ".tk", ".ml", ".cf")

    fun suspicious(body: String): Boolean {
        val bankish = Classifier.isBank(body)
        for (m in urlRe.findAll(body)) {
            val url = m.value
            val host = (hostRe.find(url)?.groupValues?.get(1) ?: "").lowercase()
            if (shorteners.any { host.contains(it) }) return true
            if (badTlds.any { host.endsWith(it) }) return true
            if (ipRe.matches(host)) return true
            if (bankish && url.startsWith("http://", ignoreCase = true)) return true
        }
        return false
    }
}

object Operators {
    fun ofNumber(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("0098")) d = "0" + d.substring(4)
        else if (d.startsWith("98") && d.length == 12) d = "0" + d.substring(2)
        if (d.length != 11 || !d.startsWith("09")) return null
        val p = d.substring(0, 4).toIntOrNull() ?: return null
        return when {
            p in 910..919 || p in 990..999 -> "mci"
            p in 930..939 || p in 900..905 || p == 941 -> "irancell"
            p in 920..922 -> "rightel"
            else -> null
        }
    }

    fun ofSim(label: String): String? {
        val l = label.lowercase()
        return when {
            l.contains("mci") || l.contains("همراه") || l.contains("hamrah") -> "mci"
            l.contains("irancell") || l.contains("ایرانسل") || l.contains("mtn") -> "irancell"
            l.contains("rightel") || l.contains("رایتل") -> "rightel"
            else -> null
        }
    }
}

object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    private fun speakNow(text: String) {
        val t = tts ?: return
        try { t.language = Locale(if (Lang.isFa) "fa" else "en") } catch (e: Exception) { }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "psms")
    }

    fun speak(ctx: Context, text: String) {
        if (tts == null) {
            pending = text
            tts = TextToSpeech(ctx.applicationContext) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    ready = true
                    pending?.let { speakNow(it) }
                    pending = null
                }
            }
        } else if (ready) {
            speakNow(text)
        } else {
            pending = text
        }
    }
}

object CardImage {
    fun create(ctx: Context, title: String, body: String, whenText: String): File {
        val w = 1080
        val m = 72
        val pad = 56
        val tf = ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular)
        val tfb = ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = 46f; tp.color = 0xFF1F2A30.toInt(); tp.typeface = tf
        val hp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        hp.textSize = 40f; hp.color = 0xFF5A7D0A.toInt(); hp.typeface = tfb
        val sp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        sp.textSize = 30f; sp.color = 0xFF78909C.toInt(); sp.typeface = tf
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fp.textSize = 34f; fp.color = 0xFFFFFFFF.toInt(); fp.typeface = tfb

        val inner = w - 2 * m - 2 * pad
        fun lay(t: String, p: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(t, 0, t.length, p, inner)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.25f).build()

        val lh = lay(title, hp)
        val lb = lay(body, tp)
        val lw = lay(whenText, sp)
        val cardH = pad + lh.height + 24 + lb.height + 28 + lw.height + pad
        val h = m + cardH + 150 + m

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1F2A30.toInt(), 0xFF7CB518.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val card = Paint(Paint.ANTI_ALIAS_FLAG)
        card.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(m.toFloat(), m.toFloat(), (w - m).toFloat(), (m + cardH).toFloat()), 56f, 56f, card)

        var y = (m + pad).toFloat()
        c.save(); c.translate((m + pad).toFloat(), y); lh.draw(c); c.restore(); y += lh.height + 24
        c.save(); c.translate((m + pad).toFloat(), y); lb.draw(c); c.restore(); y += lb.height + 28
        c.save(); c.translate((m + pad).toFloat(), y); lw.draw(c); c.restore()

        val brand = "PSMS  ·  " + T("امنیت و سرعت", "Security & speed")
        c.drawText(brand, (m + 8).toFloat(), (m + cardH + 90).toFloat(), fp)

        val dir = File(ctx.cacheDir, "share")
        dir.mkdirs()
        val f = File(dir, "psms_card.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND)
        i.type = "image/png"
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null))
    }
}

object Csv {
    private fun q(s: String): String =
        "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    fun exportMsgs(ctx: Context, uri: Uri, msgs: List<Msg>): Int {
        var n = 0
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val w = os.bufferedWriter(Charsets.UTF_8)
            w.write("date,time,date_jalali,address,direction,sim,text\r\n")
            val cal = java.util.Calendar.getInstance()
            for (m in msgs.sortedBy { it.date }) {
                cal.timeInMillis = m.date
                val g = String.format(Locale.US, "%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                val tm = String.format(Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                val j = Fmt.gregorianToJalali(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                val jd = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
                val sim = Sim.slotLabel(m.subId) ?: ""
                w.write(g + "," + tm + "," + jd + "," + q(m.address) + "," + (if (m.type == 1) "in" else "out") + "," + sim + "," + q(m.body) + "\r\n")
                n++
            }
            w.flush()
        }
        return n
    }

    fun exportBank(ctx: Context, uri: Uri, txs: List<BankTx>): Int {
        var n = 0
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val w = os.bufferedWriter(Charsets.UTF_8)
            w.write("date,date_jalali,sender,type,amount\r\n")
            val cal = java.util.Calendar.getInstance()
            for (t in txs.sortedBy { it.date }) {
                cal.timeInMillis = t.date
                val g = String.format(Locale.US, "%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                val j = Fmt.gregorianToJalali(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                val jd = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
                w.write(g + "," + jd + "," + q(t.addr) + "," + (if (t.credit) "credit" else "debit") + "," + t.amount + "\r\n")
                n++
            }
            w.flush()
        }
        return n
    }
}
