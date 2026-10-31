package com.passenger.sms

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.util.Calendar

object Lang {
    val code = mutableStateOf("fa")
    val isFa: Boolean get() = code.value == "fa"
}

fun T(fa: String, en: String): String = if (Lang.code.value == "fa") fa else en

object Boot {
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        Cfg.init(app)
        Flags.init(app)
        Rules.init(app)
        NotifPrefs.init(app)
        Security.init(app)
    }
}

object Cfg {
    private var sp: SharedPreferences? = null
    val undoSecs = mutableStateOf(0)
    val otpDays = mutableStateOf(0)
    val quietOn = mutableStateOf(false)
    val quietStart = mutableStateOf(23)
    val quietEnd = mutableStateOf(7)
    val autoReplyOn = mutableStateOf(false)
    val autoReplyText = mutableStateOf("")
    val templates = mutableStateOf<List<String>>(emptyList())

    private fun defaultTemplates() = listOf(
        T("باشه", "OK"),
        T("الان مشغولم، بعداً زنگ می‌زنم", "Busy now, I'll call you later"),
        T("ممنون", "Thanks"),
        T("تو راهم", "On my way")
    )

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        sp = p
        Lang.code.value = p.getString("lang", "fa") ?: "fa"
        undoSecs.value = p.getInt("undo", 0)
        otpDays.value = p.getInt("otp_days", 0)
        quietOn.value = p.getBoolean("quiet_on", false)
        quietStart.value = p.getInt("quiet_start", 23)
        quietEnd.value = p.getInt("quiet_end", 7)
        autoReplyOn.value = p.getBoolean("ar_on", false)
        autoReplyText.value = p.getString("ar_text", null)
            ?: T("الان نمی‌تونم جواب بدم، بعداً تماس می‌گیرم.", "I can't reply right now. I'll get back to you later.")
        val tpl = p.getString("tpl", null)
        templates.value = if (tpl == null) defaultTemplates()
        else tpl.split('\u0001').filter { it.isNotBlank() }
    }

    fun setLang(c: String) { Lang.code.value = c; sp?.edit()?.putString("lang", c)?.apply() }
    fun setUndo(v: Int) { undoSecs.value = v; sp?.edit()?.putInt("undo", v)?.apply() }
    fun setOtpDays(v: Int) { otpDays.value = v; sp?.edit()?.putInt("otp_days", v)?.apply() }
    fun setQuietOn(v: Boolean) { quietOn.value = v; sp?.edit()?.putBoolean("quiet_on", v)?.apply() }
    fun setQuietStart(v: Int) { quietStart.value = v; sp?.edit()?.putInt("quiet_start", v)?.apply() }
    fun setQuietEnd(v: Int) { quietEnd.value = v; sp?.edit()?.putInt("quiet_end", v)?.apply() }
    fun setAutoReplyOn(v: Boolean) { autoReplyOn.value = v; sp?.edit()?.putBoolean("ar_on", v)?.apply() }
    fun setAutoReplyText(v: String) { autoReplyText.value = v; sp?.edit()?.putString("ar_text", v)?.apply() }
    fun setTemplates(l: List<String>) {
        templates.value = l
        sp?.edit()?.putString("tpl", l.joinToString("\u0001"))?.apply()
    }

    fun quietNow(): Boolean {
        if (!quietOn.value) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = quietStart.value
        val e = quietEnd.value
        return if (s <= e) h in s until e else (h >= s || h < e)
    }
}
