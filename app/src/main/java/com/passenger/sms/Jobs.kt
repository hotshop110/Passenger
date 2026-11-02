package com.passenger.sms

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBackup {
    fun runIfDue(ctx: Context) {
        if (!Cfg.autoBackupOn.value || !Pro.allowed("autobackup")) return
        val uriStr = Cfg.autoBackupUri.value
        if (uriStr.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - Cfg.lastBackup.value < 22L * 3600 * 1000) return
        try {
            val tree = Uri.parse(uriStr)
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "psms-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"
            val file = DocumentsContract.createDocument(ctx.contentResolver, doc, "application/json", name) ?: return
            Backup.export(ctx, file)
            Cfg.setLastBackup(now)
        } catch (e: Exception) { }
    }
}

object Budget {
    fun spentThisMonth(ctx: Context): Long {
        val ymd = Fmt.ymd(System.currentTimeMillis(), true)
        return Bank.load(ctx, true).firstOrNull { it.y == ymd[0] && it.m == ymd[1] }?.debit ?: 0L
    }

    fun check(ctx: Context) {
        val b = Cfg.budget.value
        if (b <= 0 || !Pro.allowed("budget")) return
        val sp = ctx.getSharedPreferences("budget", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("checked", 0L) < 30 * 60 * 1000L) return
        sp.edit().putLong("checked", now).apply()
        val ymd = Fmt.ymd(now, true)
        val key = ymd[0].toString() + "/" + ymd[1]
        if (sp.getString("notified", "") == key) return
        val spent = spentThisMonth(ctx)
        if (spent >= b) {
            sp.edit().putString("notified", key).apply()
            Notifier.budget(ctx, spent, b)
        }
    }
}

data class CleanupGroup(val id: String, val ids: List<Long>, val bytes: Long)

object Cleanup {
    fun scan(ctx: Context): List<CleanupGroup> {
        val promo = ArrayList<Long>(); var promoB = 0L
        val otp = ArrayList<Long>(); var otpB = 0L
        val old = ArrayList<Long>(); var oldB = 0L
        if (!Sms.granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val now = System.currentTimeMillis()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address", "body", "date"),
                null, null, "date DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val addr = c.getString(1) ?: ""
                    val body = c.getString(2) ?: ""
                    val date = c.getLong(3)
                    val age = now - date
                    val size = body.length.toLong() * 2 + 64
                    val isContact = Sms.contactName(ctx, addr) != null
                    val cat = Classifier.classify(addr, body, isContact, Flags.overrideFor(Sms.keyOf(addr)))
                    if (cat == Cat.PROMO && age > 30L * 86400000L) { promo.add(id); promoB += size }
                    else if (cat == Cat.OTP && age > 7L * 86400000L) { otp.add(id); otpB += size }
                    else if (cat == Cat.UNKNOWN && age > 365L * 86400000L) { old.add(id); oldB += size }
                }
            }
        } catch (e: Exception) { }
        return listOf(
            CleanupGroup("promo", promo, promoB),
            CleanupGroup("otp", otp, otpB),
            CleanupGroup("old", old, oldB)
        )
    }
}

data class StatsData(
    val total: Int, val sent: Int, val received: Int,
    val top: List<Triple<String, String?, Int>>,
    val bySim: Map<Int, Int>,
    val byCat: Map<Cat, Int>,
    val months: List<Triple<Int, Int, Int>>
)

object Stats {
    fun compute(ctx: Context): StatsData {
        var sent = 0
        var recv = 0
        val perAddr = HashMap<String, Int>()
        val addrOf = HashMap<String, String>()
        val bySim = HashMap<Int, Int>()
        val byCat = HashMap<Cat, Int>()
        val perMonth = LinkedHashMap<String, IntArray>()
        if (Sms.granted(ctx, Manifest.permission.READ_SMS)) {
            try {
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI, arrayOf("address", "body", "date", "type", "sub_id"),
                    null, null, "date DESC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val addr = c.getString(0) ?: continue
                        val body = c.getString(1) ?: ""
                        val date = c.getLong(2)
                        val type = c.getInt(3)
                        val sub = c.getInt(4)
                        if (type == 1) recv++ else sent++
                        val key = Sms.keyOf(addr)
                        perAddr[key] = (perAddr[key] ?: 0) + 1
                        if (!addrOf.containsKey(key)) addrOf[key] = addr
                        if (sub >= 0) bySim[sub] = (bySim[sub] ?: 0) + 1
                        if (type == 1) {
                            val isC = Sms.contactName(ctx, addr) != null
                            val cat = Classifier.classify(addr, body, isC, null)
                            byCat[cat] = (byCat[cat] ?: 0) + 1
                        }
                        val ymd = Fmt.ymd(date, true)
                        val mk = ymd[0].toString() + "/" + ymd[1]
                        val a = perMonth.getOrPut(mk) { intArrayOf(ymd[0], ymd[1], 0) }
                        a[2] = a[2] + 1
                    }
                }
            } catch (e: Exception) { }
        }
        val top = perAddr.entries.sortedByDescending { it.value }.take(8).map {
            val addr = addrOf[it.key] ?: it.key
            Triple(addr, Sms.contactName(ctx, addr) ?: Flags.aliases.value[it.key], it.value)
        }
        val months = perMonth.values.map { Triple(it[0], it[1], it[2]) }
            .sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.first }.thenByDescending { it.second })
            .take(6).reversed()
        return StatsData(sent + recv, sent, recv, top, bySim, byCat, months)
    }
}
