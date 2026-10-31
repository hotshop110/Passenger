package com.passenger.sms

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

private fun SharedPreferences.getMap(name: String): Map<String, String> {
    val m = HashMap<String, String>()
    for (s in getStringSet(name, emptySet()) ?: emptySet()) {
        val i = s.indexOf('\t')
        if (i > 0) m[s.substring(0, i)] = s.substring(i + 1)
    }
    return m
}

private fun SharedPreferences.Editor.putMap(name: String, m: Map<String, String>): SharedPreferences.Editor {
    val set = HashSet<String>()
    for ((k, v) in m) set.add(k + "\t" + v.replace('\t', ' '))
    return putStringSet(name, set)
}

object Flags {
    private var sp: SharedPreferences? = null
    val pinned = mutableStateOf<Set<String>>(emptySet())
    val archived = mutableStateOf<Set<String>>(emptySet())
    val blocked = mutableStateOf<Set<String>>(emptySet())
    val starred = mutableStateOf<Set<String>>(emptySet())
    val privateSet = mutableStateOf<Set<String>>(emptySet())
    val overrides = mutableStateOf<Map<String, String>>(emptyMap())
    val drafts = mutableStateOf<Map<String, String>>(emptyMap())
    val aliases = mutableStateOf<Map<String, String>>(emptyMap())
    val simFor = mutableStateOf<Map<String, String>>(emptyMap())
    val convBg = mutableStateOf<Map<String, String>>(emptyMap())

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("flags", Context.MODE_PRIVATE)
        sp = p
        pinned.value = HashSet(p.getStringSet("pinned", emptySet()) ?: emptySet())
        archived.value = HashSet(p.getStringSet("archived", emptySet()) ?: emptySet())
        blocked.value = HashSet(p.getStringSet("blocked", emptySet()) ?: emptySet())
        starred.value = HashSet(p.getStringSet("starred", emptySet()) ?: emptySet())
        privateSet.value = HashSet(p.getStringSet("private", emptySet()) ?: emptySet())
        overrides.value = p.getMap("overrides")
        drafts.value = p.getMap("drafts")
        aliases.value = p.getMap("aliases")
        simFor.value = p.getMap("simfor")
        convBg.value = p.getMap("convbg")
    }

    private fun update(state: MutableState<Set<String>>, name: String, keys: Collection<String>, on: Boolean) {
        state.value = if (on) state.value + keys else state.value - keys.toSet()
        sp?.edit()?.putStringSet(name, HashSet(state.value))?.apply()
    }

    private fun updateMap(state: MutableState<Map<String, String>>, name: String, key: String, value: String?) {
        val m = HashMap(state.value)
        if (value == null || value.isEmpty()) m.remove(key) else m[key] = value
        state.value = m
        sp?.edit()?.putMap(name, m)?.apply()
    }

    fun setPinned(keys: Collection<String>, on: Boolean) = update(pinned, "pinned", keys, on)
    fun setArchived(keys: Collection<String>, on: Boolean) = update(archived, "archived", keys, on)
    fun setBlocked(keys: Collection<String>, on: Boolean) = update(blocked, "blocked", keys, on)
    fun setStarred(ids: Collection<String>, on: Boolean) = update(starred, "starred", ids, on)
    fun setPrivate(keys: Collection<String>, on: Boolean) = update(privateSet, "private", keys, on)

    fun setDraft(key: String, text: String) = updateMap(drafts, "drafts", key, if (text.isBlank()) null else text)
    fun setAlias(key: String, name: String) = updateMap(aliases, "aliases", key, name.trim())
    fun setSim(key: String, subId: Int) = updateMap(simFor, "simfor", key, subId.toString())
    fun setConvBg(key: String, idx: Int?) = updateMap(convBg, "convbg", key, idx?.toString())

    fun setOverride(keys: Collection<String>, cat: Cat?) {
        val m = HashMap(overrides.value)
        for (k in keys) { if (cat == null) m.remove(k) else m[k] = cat.name }
        overrides.value = m
        sp?.edit()?.putMap("overrides", m)?.apply()
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
        Cat.GOV to listOf(
            "سازمان", "وزارت", "ثبت احوال", "مالیات", "سامانه", "شهرداری", "پلیس", "راهور",
            "تامین اجتماعی", "تأمین اجتماعی", "بیمه", "سجام", "ثنا", "قوه قضائیه", "ابلاغ", "دولت"
        ),
        Cat.PROMO to listOf(
            "لغو 11", "لغو۱۱", "لغو عضویت", "تخفیف", "جشنواره", "فروش ویژه", "عضویت", "اپلیکیشن", "دانلود"
        )
    )
    val editable = listOf(Cat.OTP, Cat.BANK, Cat.GOV, Cat.PROMO)
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

data class NotifPref(
    val mute: Boolean = false,
    val vibrate: Boolean = true,
    val sound: String? = null,
    val color: Int = 0
)

object NotifPrefs {
    val map = mutableStateOf<Map<String, NotifPref>>(emptyMap())
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("notif", Context.MODE_PRIVATE)
        sp = p
        val out = HashMap<String, NotifPref>()
        try {
            val o = JSONObject(p.getString("json", "{}") ?: "{}")
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val j = o.getJSONObject(k)
                out[k] = NotifPref(
                    mute = j.optBoolean("m", false),
                    vibrate = j.optBoolean("v", true),
                    sound = if (j.has("s")) j.getString("s") else null,
                    color = j.optInt("c", 0)
                )
            }
        } catch (e: Exception) { }
        map.value = out
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in map.value) {
            val j = JSONObject()
            j.put("m", v.mute); j.put("v", v.vibrate); j.put("c", v.color)
            if (v.sound != null) j.put("s", v.sound)
            o.put(k, j)
        }
        sp?.edit()?.putString("json", o.toString())?.apply()
    }

    fun set(id: String, p: NotifPref) {
        val m = HashMap(map.value)
        m[id] = p
        map.value = m
        save()
    }

    fun clear(id: String) {
        val m = HashMap(map.value)
        m.remove(id)
        map.value = m
        save()
    }

    fun get(id: String): NotifPref = map.value[id] ?: NotifPref()

    fun resolve(convKey: String, cat: Cat): NotifPref =
        map.value["k:$convKey"] ?: map.value["c:${cat.name}"] ?: NotifPref()
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
