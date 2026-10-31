package com.passenger.sms

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Store {
    val convs = mutableStateOf<List<Conv>>(emptyList())
    val pendingOpen = mutableStateOf<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        Boot.init(app)
        scope.launch {
            Sim.refresh(app)
            val list = withContext(Dispatchers.IO) {
                try {
                    Sms.autoClean(app)
                    Sms.loadConvs(app)
                } catch (e: Exception) { emptyList() }
            }
            convs.value = list
            try { UnreadWidget.updateAll(app) } catch (e: Exception) { }
        }
    }
}

object Sms {
    const val ACT_SENT = "com.passenger.sms.SENT"
    const val ACT_DELIVERED = "com.passenger.sms.DELIVERED"

    private val BASE = arrayOf("_id", "address", "body", "date", "type", "read")
    private val FULL = BASE + arrayOf("sub_id", "status")

    fun granted(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isDefault(ctx: Context) = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun keyOf(a: String): String {
        val hasLetter = a.any { it.isLetter() }
        val d = a.filter { it.isDigit() }
        return if (!hasLetter && d.length >= 9) d.takeLast(9) else a.lowercase()
    }

    fun likeArg(address: String): String {
        val key = keyOf(address)
        return "%" + (if (key == address.lowercase()) address else key)
    }

    private fun q(ctx: Context, uri: Uri, sel: String?, args: Array<String>?, order: String?): Cursor? {
        return try {
            ctx.contentResolver.query(uri, FULL, sel, args, order)
        } catch (e: Exception) {
            try { ctx.contentResolver.query(uri, BASE, sel, args, order) } catch (e2: Exception) { null }
        }
    }

    private fun read(c: Cursor): Msg {
        val si = c.getColumnIndex("sub_id")
        val st = c.getColumnIndex("status")
        return Msg(
            id = c.getLong(0),
            address = c.getString(1) ?: "",
            body = c.getString(2) ?: "",
            date = c.getLong(3),
            type = c.getInt(4),
            read = c.getInt(5) == 1,
            subId = if (si >= 0) c.getInt(si) else -1,
            status = if (st >= 0) c.getInt(st) else -1
        )
    }

    // ---------- contacts ----------

    private class Info(val name: String?, val photo: ImageBitmap?)

    private val infoCache = HashMap<String, Info>()
    private var infoAt = 0L

    fun contactInfo(ctx: Context, address: String): Pair<String?, ImageBitmap?> {
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return Pair(null, null)
        if (!address.any { it.isDigit() }) return Pair(null, null)
        val now = System.currentTimeMillis()
        synchronized(infoCache) {
            if (now - infoAt > 5 * 60 * 1000L) { infoCache.clear(); infoAt = now }
            val hit = infoCache[address]
            if (hit != null) return Pair(hit.name, hit.photo)
        }
        var name: String? = null
        var photo: ImageBitmap? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)
            )
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                    val p = it.getString(1)
                    if (p != null) {
                        try {
                            ctx.contentResolver.openInputStream(Uri.parse(p))?.use { s ->
                                photo = BitmapFactory.decodeStream(s)?.asImageBitmap()
                            }
                        } catch (e: Exception) { }
                    }
                }
            }
        } catch (e: Exception) { }
        synchronized(infoCache) { infoCache[address] = Info(name, photo) }
        return Pair(name, photo)
    }

    fun contactName(ctx: Context, number: String): String? = contactInfo(ctx, number).first

    // ---------- reading ----------

    fun loadConvs(ctx: Context): List<Conv> {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val groups = LinkedHashMap<String, MutableList<Msg>>()
        q(ctx, Telephony.Sms.CONTENT_URI, null, null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                val m = read(c)
                if (m.address.isBlank()) continue
                groups.getOrPut(keyOf(m.address)) { mutableListOf() }.add(m)
            }
        }
        val out = ArrayList<Conv>()
        for ((k, list) in groups) {
            val last = list.first()
            val info = contactInfo(ctx, last.address)
            val unread = list.count { !it.read && it.type == 1 }
            out.add(
                Conv(
                    key = k, address = last.address,
                    name = info.first ?: Flags.aliases.value[k],
                    last = last, count = list.size, unread = unread,
                    cat = Classifier.classify(last.address, last.body, info.first != null, Flags.overrideFor(k)),
                    subId = last.subId, photo = info.second
                )
            )
        }
        return out.sortedByDescending { it.last.date }
    }

    fun loadThread(ctx: Context, address: String): List<Msg> {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val out = ArrayList<Msg>()
        q(ctx, Telephony.Sms.CONTENT_URI, "address LIKE ?", arrayOf(likeArg(address)), "date DESC")
            ?.use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

    fun loadByIds(ctx: Context, ids: Collection<Long>): List<Msg> {
        if (ids.isEmpty() || !granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val out = ArrayList<Msg>()
        for (chunk in ids.toList().chunked(400)) {
            q(ctx, Telephony.Sms.CONTENT_URI, "_id IN (" + chunk.joinToString(",") + ")", null, "date DESC")
                ?.use { c -> while (c.moveToNext()) out.add(read(c)) }
        }
        return out
    }

    fun search(ctx: Context, text: String, from: Long, to: Long, subId: Int): List<Msg> {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val sel = StringBuilder("1=1")
        val args = ArrayList<String>()
        if (text.isNotBlank()) { sel.append(" AND body LIKE ?"); args.add("%" + text.trim() + "%") }
        if (from > 0) { sel.append(" AND date >= ?"); args.add(from.toString()) }
        if (to > 0) { sel.append(" AND date < ?"); args.add(to.toString()) }
        if (subId >= 0) { sel.append(" AND sub_id = ?"); args.add(subId.toString()) }
        val out = ArrayList<Msg>()
        q(ctx, Telephony.Sms.CONTENT_URI, sel.toString(), args.toTypedArray(), "date DESC LIMIT 300")
            ?.use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

    fun unreadCount(ctx: Context): Int {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return -1
        return try {
            ctx.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"), "read=0", null, null)
                ?.use { it.count } ?: 0
        } catch (e: Exception) { -1 }
    }

    // ---------- writing ----------

    fun markRead(ctx: Context, address: String) {
        if (!isDefault(ctx)) return
        try {
            val v = ContentValues().apply { put("read", 1) }
            ctx.contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI, v,
                "address LIKE ? AND read = 0", arrayOf(likeArg(address))
            )
        } catch (e: Exception) { }
    }

    fun deleteMessages(ctx: Context, ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        Trash.add(ctx, loadByIds(ctx, ids))
        var n = 0
        for (chunk in ids.toList().chunked(400)) {
            try {
                n += ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, "_id IN (" + chunk.joinToString(",") + ")", null
                )
            } catch (e: Exception) { }
        }
        return n
    }

    fun deleteConvs(ctx: Context, addresses: Collection<String>): Int {
        var n = 0
        for (a in addresses) {
            try {
                Trash.add(ctx, loadThread(ctx, a))
                n += ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, "address LIKE ?", arrayOf(likeArg(a))
                )
            } catch (e: Exception) { }
        }
        return n
    }

    fun autoClean(ctx: Context) {
        val days = Cfg.otpDays.value
        if (days <= 0 || !isDefault(ctx) || !granted(ctx, Manifest.permission.READ_SMS)) return
        val sp = ctx.getSharedPreferences("clean", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0L) < 6 * 3600 * 1000L) return
        sp.edit().putLong("last", now).apply()
        val cutoff = now - days * 86400000L
        val ids = ArrayList<Long>()
        q(ctx, Telephony.Sms.Inbox.CONTENT_URI, "date < ?", arrayOf(cutoff.toString()), "date ASC")?.use { c ->
            while (c.moveToNext()) {
                val m = read(c)
                if (Classifier.isOtp(m.body)) ids.add(m.id)
            }
        }
        for (chunk in ids.chunked(400)) {
            try {
                ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, "_id IN (" + chunk.joinToString(",") + ")", null
                )
            } catch (e: Exception) { }
        }
    }

    // ---------- sending ----------

    fun smsManager(ctx: Context, subId: Int): SmsManager {
        if (subId >= 0) {
            return if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        return if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
    }

    private fun statusPi(ctx: Context, action: String, uri: Uri, to: String, text: String, subId: Int): PendingIntent {
        val i = Intent(ctx, SmsStatusReceiver::class.java)
        i.action = action
        i.data = uri
        i.putExtra("to", to)
        i.putExtra("body", text)
        i.putExtra("sub", subId)
        return PendingIntent.getBroadcast(
            ctx, (uri.toString() + action).hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun currentType(ctx: Context, uri: Uri): Int {
        return try {
            ctx.contentResolver.query(uri, arrayOf("type"), null, null, null)?.use {
                if (it.moveToFirst()) it.getInt(0) else -1
            } ?: -1
        } catch (e: Exception) { -1 }
    }

    fun updateType(ctx: Context, uri: Uri, type: Int) {
        try {
            if (type == 2 && currentType(ctx, uri) == 5) return
            ctx.contentResolver.update(uri, ContentValues().apply { put("type", type) }, null, null)
        } catch (e: Exception) { }
    }

    fun updateDelivered(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.update(uri, ContentValues().apply { put("status", 0) }, null, null)
        } catch (e: Exception) { }
    }

    /** Sends a text. If this app is the default SMS app the message is stored and tracked. */
    fun send(ctx: Context, to: String, text: String, subId: Int = -1, reuse: Uri? = null): Boolean {
        var uri: Uri? = reuse
        try {
            if (uri == null && isDefault(ctx)) {
                val v = ContentValues().apply {
                    put("address", to); put("body", text)
                    put("date", System.currentTimeMillis()); put("type", 4); put("read", 1)
                    if (subId >= 0) put("sub_id", subId)
                }
                uri = ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, v)
            } else if (uri != null) {
                ctx.contentResolver.update(uri, ContentValues().apply { put("type", 4) }, null, null)
            }
            val mgr = smsManager(ctx, subId)
            val parts = mgr.divideMessage(text)
            if (uri == null) {
                mgr.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                val sentPi = statusPi(ctx, ACT_SENT, uri, to, text, subId)
                val delPi = statusPi(ctx, ACT_DELIVERED, uri, to, text, subId)
                val sents = ArrayList<PendingIntent>()
                val dels = ArrayList<PendingIntent>()
                for (i in parts.indices) { sents.add(sentPi); dels.add(delPi) }
                mgr.sendMultipartTextMessage(to, null, parts, sents, dels)
            }
            return true
        } catch (e: Exception) {
            if (uri != null) updateType(ctx, uri, 5)
            return false
        }
    }
}
