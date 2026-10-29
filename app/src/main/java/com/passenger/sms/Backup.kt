package com.passenger.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.JsonReader
import android.util.JsonWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object Backup {
    fun export(ctx: Context, uri: Uri): Int {
        var n = 0
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            val w = JsonWriter(OutputStreamWriter(os, "UTF-8"))
            w.beginArray()
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("address", "body", "date", "type", "read"),
                null, null, "date ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    w.beginObject()
                    w.name("a").value(c.getString(0) ?: "")
                    w.name("b").value(c.getString(1) ?: "")
                    w.name("d").value(c.getLong(2))
                    w.name("t").value(c.getInt(3).toLong())
                    w.name("r").value(c.getInt(4).toLong())
                    w.endObject()
                    n++
                }
            }
            w.endArray()
            w.flush()
        }
        return n
    }

    /** returns Pair(added, skipped) */
    fun import(ctx: Context, uri: Uri): Pair<Int, Int> {
        val existing = HashSet<String>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf("body", "date", "type"), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getLong(1)}|${c.getInt(2)}|${(c.getString(0) ?: "").hashCode()}")
            }
        }
        var added = 0
        var skipped = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            val r = JsonReader(InputStreamReader(ins, "UTF-8"))
            r.beginArray()
            while (r.hasNext()) {
                var a = ""; var b = ""; var d = 0L; var t = 1; var rd = 1
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "a" -> a = r.nextString()
                        "b" -> b = r.nextString()
                        "d" -> d = r.nextLong()
                        "t" -> t = r.nextInt()
                        "r" -> rd = r.nextInt()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                val key = "$d|$t|${b.hashCode()}"
                if (a.isBlank() || existing.contains(key)) {
                    skipped++
                } else {
                    try {
                        val v = ContentValues().apply {
                            put("address", a); put("body", b); put("date", d)
                            put("type", t); put("read", rd)
                        }
                        ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, v)
                        existing.add(key)
                        added++
                    } catch (e: Exception) { skipped++ }
                }
            }
            r.endArray()
        }
        return Pair(added, skipped)
    }
}
