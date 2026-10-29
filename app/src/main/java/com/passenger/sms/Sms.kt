package com.passenger.sms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Store {
    val convs = mutableStateOf<List<Conv>>(emptyList())
    val pendingOpen = mutableStateOf<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        Flags.init(app)
        Rules.init(app)
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                try { Sms.loadConvs(app) } catch (e: Exception) { emptyList() }
            }
            convs.value = list
        }
    }
}

object Sms {
    private val COLS = arrayOf("_id", "address", "body", "date", "type", "read")

    fun granted(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isDefault(ctx: Context) = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun keyOf(a: String): String {
        val hasLetter = a.any { it.isLetter() }
        val d = a.filter { it.isDigit() }
        return if (!hasLetter && d.length >= 9) d.takeLast(9) else a.lowercase()
    }

    private fun likeArg(address: String): String {
        val key = keyOf(address)
        return "%" + (if (key == address.lowercase()) address else key)
    }

    private fun read(c: android.database.Cursor) = Msg(
        id = c.getLong(0),
        address = c.getString(1) ?: "",
        body = c.getString(2) ?: "",
        date = c.getLong(3),
        type = c.getInt(4),
        read = c.getInt(5) == 1
    )

    fun contactName(ctx: Context, number: String): String? {
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null
        if (!number.any { it.isDigit() }) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) { null }
    }

    fun loadConvs(ctx: Context): List<Conv> {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val groups = LinkedHashMap<String, MutableList<Msg>>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, COLS, null, null, "date DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val m = read(c)
                if (m.address.isBlank()) continue
                groups.getOrPut(keyOf(m.address)) { mutableListOf() }.add(m)
            }
        }
        val out = ArrayList<Conv>()
        for ((k, list) in groups) {
            val last = list.first()
            val name = contactName(ctx, last.address)
            val unread = list.count { !it.read && it.type == 1 }
            out.add(
                Conv(
                    key = k, address = last.address, name = name, last = last,
                    count = list.size, unread = unread,
                    cat = Classifier.classify(last.address, last.body, name != null, Flags.overrideFor(k))
                )
            )
        }
        return out.sortedByDescending { it.last.date }
    }

    fun loadThread(ctx: Context, address: String): List<Msg> {
        if (!granted(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val out = ArrayList<Msg>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, COLS, "address LIKE ?", arrayOf(likeArg(address)), "date DESC"
        )?.use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

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
        return try {
            ctx.contentResolver.delete(
                Telephony.Sms.CONTENT_URI, "_id IN (" + ids.joinToString(",") + ")", null
            )
        } catch (e: Exception) { 0 }
    }

    fun deleteConvs(ctx: Context, addresses: Collection<String>): Int {
        var n = 0
        for (a in addresses) {
            try {
                n += ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, "address LIKE ?", arrayOf(likeArg(a))
                )
            } catch (e: Exception) { }
        }
        return n
    }

    fun send(ctx: Context, to: String, text: String) {
        val sm: SmsManager =
            if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        val parts = sm.divideMessage(text)
        sm.sendMultipartTextMessage(to, null, parts, null, null)
        try {
            val v = ContentValues().apply {
                put("address", to); put("body", text)
                put("date", System.currentTimeMillis()); put("type", 2); put("read", 1)
            }
            ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, v)
        } catch (e: Exception) { }
    }
}
