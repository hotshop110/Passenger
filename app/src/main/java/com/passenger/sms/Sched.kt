package com.passenger.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class SchedItem(
    val id: Long, val type: String, val at: Long,
    val address: String, val body: String, val subId: Int, val extra: String
)

object Sched {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("sched", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<SchedItem> {
        return try {
            val arr = JSONArray(sp(ctx).getString("items", "[]") ?: "[]")
            val out = ArrayList<SchedItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    SchedItem(
                        o.getLong("id"), o.getString("t"), o.getLong("at"),
                        o.getString("a"), o.getString("b"), o.optInt("s", -1), o.optString("x", "")
                    )
                )
            }
            out.sortedBy { it.at }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(ctx: Context, list: List<SchedItem>) {
        val arr = JSONArray()
        for (it in list) {
            val o = JSONObject()
            o.put("id", it.id); o.put("t", it.type); o.put("at", it.at)
            o.put("a", it.address); o.put("b", it.body); o.put("s", it.subId); o.put("x", it.extra)
            arr.put(o)
        }
        sp(ctx).edit().putString("items", arr.toString()).apply()
    }

    private fun pi(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, SchedReceiver::class.java)
        i.putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, (id % 2000000000L).toInt(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun alarm(ctx: Context, item: SchedItem) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = if (item.at < System.currentTimeMillis()) System.currentTimeMillis() + 1500 else item.at
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, item.id))
    }

    fun schedule(ctx: Context, type: String, at: Long, address: String, body: String, subId: Int, extra: String = ""): SchedItem {
        var id = System.currentTimeMillis()
        val cur = all(ctx)
        while (cur.any { it.id == id }) id++
        val item = SchedItem(id, type, at, address, body, subId, extra)
        save(ctx, cur + item)
        alarm(ctx, item)
        return item
    }

    fun remove(ctx: Context, id: Long) {
        try { ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id)) } catch (e: Exception) { }
        save(ctx, all(ctx).filter { it.id != id })
    }

    fun rescheduleAll(ctx: Context) {
        for (item in all(ctx)) alarm(ctx, item)
    }

    fun scheduleRetry(ctx: Context, uri: Uri, to: String, body: String, subId: Int) {
        val rp = ctx.getSharedPreferences("retry", Context.MODE_PRIVATE)
        val k = uri.toString()
        val n = rp.getInt(k, 0)
        if (n >= 3) return
        if (all(ctx).any { it.type == "retry" && it.extra == k }) return
        rp.edit().putInt(k, n + 1).apply()
        schedule(ctx, "retry", System.currentTimeMillis() + 60000L, to, body, subId, k)
    }

    fun fire(ctx: Context, id: Long) {
        Boot.init(ctx)
        val item = all(ctx).firstOrNull { it.id == id } ?: return
        save(ctx, all(ctx).filter { it.id != id })
        when (item.type) {
            "send" -> Sms.send(ctx, item.address, item.body, item.subId)
            "retry" -> Sms.send(ctx, item.address, item.body, item.subId, Uri.parse(item.extra))
            "remind" -> Notifier.reminder(ctx, item)
        }
    }
}

class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Sched.fire(ctx, intent.getLongExtra("id", 0L))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Sched.rescheduleAll(ctx)
    }
}
