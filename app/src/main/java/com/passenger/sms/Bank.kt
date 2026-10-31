package com.passenger.sms

import android.Manifest
import android.content.Context
import android.provider.Telephony

data class BankTx(val date: Long, val amount: Long, val credit: Boolean)
data class MonthSum(val y: Int, val m: Int, val credit: Long, val debit: Long, val count: Int)

object Bank {
    private val numRe = Regex("\\d[\\d,]*")
    private val creditWords = listOf("واریز", "دریافت", "deposit", "credit")
    private val debitWords = listOf("برداشت", "خرید", "پرداخت", "انتقال", "هزینه", "withdraw", "purchase", "debit")

    fun parse(date: Long, body: String): BankTx? {
        val s = Fmt.norm(body)
        var best = -1
        var credit = false
        for (w in creditWords) {
            val i = s.indexOf(w, ignoreCase = true)
            if (i >= 0 && (best < 0 || i < best)) { best = i; credit = true }
        }
        for (w in debitWords) {
            val i = s.indexOf(w, ignoreCase = true)
            if (i >= 0 && (best < 0 || i < best)) { best = i; credit = false }
        }
        if (best < 0) return null
        var m = numRe.find(s, best)
        if (m != null && m.range.first - best > 40) m = null
        var text = m?.value
        if (text == null) {
            val from = maxOf(0, best - 40)
            text = numRe.findAll(s.substring(from, best)).lastOrNull()?.value
        }
        val v = text?.replace(",", "")?.toLongOrNull() ?: return null
        if (v < 100) return null
        return BankTx(date, v, credit)
    }

    fun load(ctx: Context, jalali: Boolean): List<MonthSum> {
        if (!Sms.granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val since = System.currentTimeMillis() - 366L * 86400000L
        val acc = LinkedHashMap<String, LongArray>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf("body", "date"),
                "date >= ?", arrayOf(since.toString()), "date DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(0) ?: continue
                    if (!Classifier.isBank(body)) continue
                    val tx = parse(c.getLong(1), body) ?: continue
                    val ymd = Fmt.ymd(tx.date, jalali)
                    val k = ymd[0].toString() + "/" + ymd[1]
                    val a = acc.getOrPut(k) { LongArray(5) }
                    a[0] = ymd[0].toLong(); a[1] = ymd[1].toLong()
                    if (tx.credit) { a[2] = a[2] + tx.amount } else { a[3] = a[3] + tx.amount }
                    a[4] = a[4] + 1L
                }
            }
        } catch (e: Exception) { }
        return acc.values.map { MonthSum(it[0].toInt(), it[1].toInt(), it[2], it[3], it[4].toInt()) }
            .sortedWith(compareByDescending<MonthSum> { it.y }.thenByDescending { it.m })
    }
}
