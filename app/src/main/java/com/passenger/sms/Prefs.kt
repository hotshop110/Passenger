package com.passenger.sms

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

object Flags {
    private var sp: SharedPreferences? = null
    val pinned = mutableStateOf<Set<String>>(emptySet())
    val archived = mutableStateOf<Set<String>>(emptySet())
    val blocked = mutableStateOf<Set<String>>(emptySet())
    val overrides = mutableStateOf<Map<String, String>>(emptyMap())

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("flags", Context.MODE_PRIVATE)
        sp = p
        pinned.value = HashSet(p.getStringSet("pinned", emptySet()) ?: emptySet())
        archived.value = HashSet(p.getStringSet("archived", emptySet()) ?: emptySet())
        blocked.value = HashSet(p.getStringSet("blocked", emptySet()) ?: emptySet())
        val m = HashMap<String, String>()
        for (s in p.getStringSet("overrides", emptySet()) ?: emptySet()) {
            val i = s.lastIndexOf('\t')
            if (i > 0) m[s.substring(0, i)] = s.substring(i + 1)
        }
        overrides.value = m
    }

    private fun update(state: androidx.compose.runtime.MutableState<Set<String>>, name: String,
                       keys: Collection<String>, on: Boolean) {
        state.value = if (on) state.value + keys else state.value - keys.toSet()
        sp?.edit()?.putStringSet(name, HashSet(state.value))?.apply()
    }

    fun setPinned(keys: Collection<String>, on: Boolean) = update(pinned, "pinned", keys, on)
    fun setArchived(keys: Collection<String>, on: Boolean) = update(archived, "archived", keys, on)
    fun setBlocked(keys: Collection<String>, on: Boolean) = update(blocked, "blocked", keys, on)

    fun setOverride(keys: Collection<String>, cat: Cat?) {
        val m = HashMap(overrides.value)
        for (k in keys) { if (cat == null) m.remove(k) else m[k] = cat.name }
        overrides.value = m
        val set = HashSet<String>()
        for ((k, v) in m) set.add(k + "\t" + v)
        sp?.edit()?.putStringSet("overrides", set)?.apply()
    }

    fun overrideFor(key: String): Cat? =
        overrides.value[key]?.let { n -> Cat.values().firstOrNull { it.name == n } }
}

object Rules {
    val defaults: Map<Cat, List<String>> = mapOf(
        Cat.OTP to listOf(
            "کد تایید", "کد تأیید", "کد فعال", "کد ورود", "کد یکبار", "کد یک بار", "کد امنیتی",
            "رمز یکبار", "رمز یک بار", "رمز پویا", "رمز دوم", "verification", "otp", "code"
        ),
        Cat.BANK to listOf(
            "مانده", "موجودی", "واریز", "برداشت", "تراکنش", "بانک", "کارت", "حساب",
            "انتقال", "پرداخت", "مبلغ", "ریال", "پایا", "ساتنا"
        ),
        Cat.PROMO to listOf(
            "لغو 11", "لغو۱۱", "لغو عضویت", "تخفیف", "جشنواره", "فروش ویژه", "عضویت", "اپلیکیشن", "دانلود"
        )
    )
    val editable = listOf(Cat.OTP, Cat.BANK, Cat.PROMO)
    val map = mutableStateOf(defaults)
    private var sp: SharedPreferences? = null

    private fun parse(s: String): List<String> =
        s.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("rules", Context.MODE_PRIVATE)
        sp = p
        val m = HashMap(defaults)
        for (c in editable) p.getString(c.name, null)?.let { m[c] = parse(it) }
        map.value = m
    }

    fun text(c: Cat): String = map.value[c].orEmpty().joinToString("، ")

    fun set(c: Cat, text: String) {
        val m = HashMap(map.value)
        m[c] = parse(text)
        map.value = m
        sp?.edit()?.putString(c.name, text)?.apply()
    }

    fun reset() {
        map.value = defaults
        sp?.edit()?.clear()?.apply()
    }
}

object Security {
    val enabled = mutableStateOf(false)
    val locked = mutableStateOf(false)
    private var sp: SharedPreferences? = null
    private var lastStop = 0L

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("security", Context.MODE_PRIVATE)
        sp = p
        enabled.value = p.getBoolean("lock", false)
        if (enabled.value) locked.value = true
    }

    fun setEnabled(on: Boolean) {
        enabled.value = on
        sp?.edit()?.putBoolean("lock", on)?.apply()
    }

    fun onStop() { lastStop = System.currentTimeMillis() }

    fun onStart() {
        if (enabled.value && lastStop > 0 && System.currentTimeMillis() - lastStop > 15000) {
            locked.value = true
        }
    }
}
