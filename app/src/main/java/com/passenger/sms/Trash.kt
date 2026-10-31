package com.passenger.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashMsg(
    val address: String, val body: String, val date: Long,
    val type: Int, val subId: Int, val deletedAt: Long
)

object Trash {
    private const val KEEP_MS = 30L * 86400000L

    private fun file(ctx: Context) = File(ctx.filesDir, "trash.json")

    fun load(ctx: Context): List<TrashMsg> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<TrashMsg>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    TrashMsg(
                        o.getString("a"), o.getString("b"), o.getLong("d"),
                        o.getInt("t"), o.optInt("s", -1), o.getLong("x")
                    )
                )
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    private fun save(ctx: Context, list: List<TrashMsg>) {
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("a", m.address); o.put("b", m.body); o.put("d", m.date)
            o.put("t", m.type); o.put("s", m.subId); o.put("x", m.deletedAt)
            arr.put(o)
        }
        try { file(ctx).writeText(arr.toString()) } catch (e: Exception) { }
    }

    fun add(ctx: Context, msgs: List<Msg>) {
        if (msgs.isEmpty()) return
        val now = System.currentTimeMillis()
        val cur = ArrayList(load(ctx).filter { now - it.deletedAt < KEEP_MS })
        for (m in msgs) cur.add(TrashMsg(m.address, m.body, m.date, m.type, m.subId, now))
        save(ctx, cur)
    }

    fun purgeOld(ctx: Context) {
        val now = System.currentTimeMillis()
        val all = load(ctx)
        val keep = all.filter { now - it.deletedAt < KEEP_MS }
        if (keep.size != all.size) save(ctx, keep)
    }

    fun remove(ctx: Context, pred: (TrashMsg) -> Boolean) {
        save(ctx, load(ctx).filter { !pred(it) })
    }

    fun restore(ctx: Context, items: List<TrashMsg>): Int {
        var n = 0
        for (m in items) {
            try {
                val v = ContentValues().apply {
                    put("address", m.address); put("body", m.body); put("date", m.date)
                    put("type", if (m.type == 4 || m.type == 5) 2 else m.type); put("read", 1)
                    if (m.subId >= 0) put("sub_id", m.subId)
                }
                ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, v)
                n++
            } catch (e: Exception) { }
        }
        val set = items.toSet()
        remove(ctx) { it in set }
        return n
    }
}
