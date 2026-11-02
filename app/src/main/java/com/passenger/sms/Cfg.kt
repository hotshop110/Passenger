package com.passenger.sms

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.util.Calendar

const val CONTACT_EMAIL = "hotshop110@gmail.com"

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
        Pro.init(app)
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

    val hideLock = mutableStateOf(false)
    val groupNotifs = mutableStateOf(true)
    val quietPromo = mutableStateOf(false)
    val spamRules = mutableStateOf("")
    val swipeStart = mutableStateOf("pin")
    val swipeEnd = mutableStateOf("archive")
    val autoArchiveMonths = mutableStateOf(0)
    val secureScreen = mutableStateOf(false)
    val autoSimByOp = mutableStateOf(false)
    val sig1 = mutableStateOf("")
    val sig2 = mutableStateOf("")
    val showBalances = mutableStateOf(true)
    val budget = mutableStateOf(0L)
    val autoBackupOn = mutableStateOf(false)
    val autoBackupUri = mutableStateOf("")
    val lastBackup = mutableStateOf(0L)

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

        hideLock.value = p.getBoolean("hide_lock", false)
        groupNotifs.value = p.getBoolean("group_notifs", true)
        quietPromo.value = p.getBoolean("quiet_promo", false)
        spamRules.value = p.getString("spam_rules", "") ?: ""
        swipeStart.value = p.getString("swipe_start", "pin") ?: "pin"
        swipeEnd.value = p.getString("swipe_end", "archive") ?: "archive"
        autoArchiveMonths.value = p.getInt("auto_archive", 0)
        secureScreen.value = p.getBoolean("secure_screen", false)
        autoSimByOp.value = p.getBoolean("auto_sim", false)
        sig1.value = p.getString("sig1", "") ?: ""
        sig2.value = p.getString("sig2", "") ?: ""
        showBalances.value = p.getBoolean("show_bal", true)
        budget.value = p.getLong("budget", 0L)
        autoBackupOn.value = p.getBoolean("ab_on", false)
        autoBackupUri.value = p.getString("ab_uri", "") ?: ""
        lastBackup.value = p.getLong("ab_last", 0L)
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

    fun setHideLock(v: Boolean) { hideLock.value = v; sp?.edit()?.putBoolean("hide_lock", v)?.apply() }
    fun setGroupNotifs(v: Boolean) { groupNotifs.value = v; sp?.edit()?.putBoolean("group_notifs", v)?.apply() }
    fun setQuietPromo(v: Boolean) { quietPromo.value = v; sp?.edit()?.putBoolean("quiet_promo", v)?.apply() }
    fun setSpamRules(v: String) { spamRules.value = v; sp?.edit()?.putString("spam_rules", v)?.apply() }
    fun setSwipeStart(v: String) { swipeStart.value = v; sp?.edit()?.putString("swipe_start", v)?.apply() }
    fun setSwipeEnd(v: String) { swipeEnd.value = v; sp?.edit()?.putString("swipe_end", v)?.apply() }
    fun setAutoArchive(v: Int) { autoArchiveMonths.value = v; sp?.edit()?.putInt("auto_archive", v)?.apply() }
    fun setSecureScreen(v: Boolean) { secureScreen.value = v; sp?.edit()?.putBoolean("secure_screen", v)?.apply() }
    fun setAutoSim(v: Boolean) { autoSimByOp.value = v; sp?.edit()?.putBoolean("auto_sim", v)?.apply() }
    fun setSig1(v: String) { sig1.value = v; sp?.edit()?.putString("sig1", v)?.apply() }
    fun setSig2(v: String) { sig2.value = v; sp?.edit()?.putString("sig2", v)?.apply() }
    fun setShowBalances(v: Boolean) { showBalances.value = v; sp?.edit()?.putBoolean("show_bal", v)?.apply() }
    fun setBudget(v: Long) { budget.value = v; sp?.edit()?.putLong("budget", v)?.apply() }
    fun setAutoBackupOn(v: Boolean) { autoBackupOn.value = v; sp?.edit()?.putBoolean("ab_on", v)?.apply() }
    fun setAutoBackupUri(v: String) { autoBackupUri.value = v; sp?.edit()?.putString("ab_uri", v)?.apply() }
    fun setLastBackup(v: Long) { lastBackup.value = v; sp?.edit()?.putLong("ab_last", v)?.apply() }

    fun quietNow(): Boolean {
        if (!quietOn.value) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = quietStart.value
        val e = quietEnd.value
        return if (s <= e) h in s until e else (h >= s || h < e)
    }

    fun spamWords(): List<String> =
        spamRules.value.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

object Spam {
    fun matches(address: String, body: String): Boolean {
        val w = Cfg.spamWords()
        if (w.isEmpty()) return false
        return w.any { address.contains(it, ignoreCase = true) || body.contains(it, ignoreCase = true) }
    }
}
