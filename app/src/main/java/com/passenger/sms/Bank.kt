package com.passenger.sms

import android.Manifest
import android.content.Context
import android.provider.Telephony

data class BankTx(val date: Long, val amount: Long, val credit: Boolean, val addr: String = "")
data class MonthSum(val y: Int, val m: Int, val credit: Long, val debit: Long, val count: Int)
data class BalanceInfo(val address: String, val name: String?, val amount: Long, val date: Long)
data class AccountSum(val tail: String, val credit: Long, val debit: Long, val count: Int)

object Bank {
    private val numRe = Regex("\\d[\\d,]*")
    private val balRe = Regex("(?:مانده|موجودی|balance)[^\\d]{0,15}(\\d[\\d,]*)", RegexOption.IGNORE_CASE)
    private val acctRe = Regex("(?:حساب|کارت|card|acct)[^\\d]{0,8}([\\d*]{4,20})", RegexOption.IGNORE_CASE)
    private val creditWords = listOf("واریز", "دریافت", "deposit", "credit")
    private val debitWords = listOf("برداشت", "خرید", "پرداخت", "انتقال", "هزینه", "withdraw", "purchase", "debit")

    fun parse(date: Long, body: String, addr: String = ""): BankTx? {
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
        return BankTx(date, v, credit, addr)
    }

    private fun inbox(ctx: Context, since: Long, limit: Int, block: (Long, String, String) -> Unit) {
        if (!Sms.granted(ctx, Manifest.permission.READ_SMS)) return
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf("body", "date", "address"),
                "date >= ?", arrayOf(since.toString()), "date DESC" + (if (limit > 0) " LIMIT $limit" else "")
            )?.use { c ->
                while (c.moveToNext()) {
                    block(c.getLong(1), c.getString(0) ?: "", c.getString(2) ?: "")
                }
            }
        } catch (e: Exception) { }
    }

    fun txList(ctx: Context): List<BankTx> {
        val out = ArrayList<BankTx>()
        val since = System.currentTimeMillis() - 366L * 86400000L
        inbox(ctx, since, 0) { date, body, addr ->
            if (Classifier.isBank(body)) parse(date, body, addr)?.let { out.add(it) }
        }
        return out
    }

    fun load(ctx: Context, jalali: Boolean): List<MonthSum> {
        val acc = LinkedHashMap<String, LongArray>()
        for (tx in txList(ctx)) {
            val ymd = Fmt.ymd(tx.date, jalali)
            val k = ymd[0].toString() + "/" + ymd[1]
            val a = acc.getOrPut(k) { LongArray(5) }
            a[0] = ymd[0].toLong(); a[1] = ymd[1].toLong()
            if (tx.credit) { a[2] = a[2] + tx.amount } else { a[3] = a[3] + tx.amount }
            a[4] = a[4] + 1L
        }
        return acc.values.map { MonthSum(it[0].toInt(), it[1].toInt(), it[2], it[3], it[4].toInt()) }
            .sortedWith(compareByDescending<MonthSum> { it.y }.thenByDescending { it.m })
    }

    fun latestBalances(ctx: Context): List<BalanceInfo> {
        val seen = LinkedHashMap<String, BalanceInfo>()
        val since = System.currentTimeMillis() - 120L * 86400000L
        inbox(ctx, since, 600) { date, body, addr ->
            if (seen.size < 4 && Classifier.isBank(body)) {
                val key = Sms.keyOf(addr)
                if (!seen.containsKey(key)) {
                    val m = balRe.find(Fmt.norm(body))
                    val v = m?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
                    if (v != null) {
                        seen[key] = BalanceInfo(addr, Sms.contactName(ctx, addr) ?: Flags.aliases.value[key], v, date)
                    }
                }
            }
        }
        return seen.values.toList()
    }

    fun byAccount(ctx: Context): List<AccountSum> {
        val acc = LinkedHashMap<String, LongArray>()
        val since = System.currentTimeMillis() - 366L * 86400000L
        inbox(ctx, since, 0) { date, body, addr ->
            if (Classifier.isBank(body)) {
                val tx = parse(date, body, addr)
                if (tx != null) {
                    val raw = acctRe.find(Fmt.norm(body))?.groupValues?.get(1) ?: ""
                    val tail = if (raw.length >= 4) raw.takeLast(4) else "—"
                    val a = acc.getOrPut(tail) { LongArray(3) }
                    if (tx.credit) { a[0] = a[0] + tx.amount } else { a[1] = a[1] + tx.amount }
                    a[2] = a[2] + 1L
                }
            }
        }
        return acc.entries.map { AccountSum(it.key, it.value[0], it.value[1], it.value[2].toInt()) }
            .sortedByDescending { it.count }
    }
}
