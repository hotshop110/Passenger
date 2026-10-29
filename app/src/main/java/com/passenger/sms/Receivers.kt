package com.passenger.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val addr = msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        try {
            val v = ContentValues().apply {
                put("address", addr); put("body", body)
                put("date", System.currentTimeMillis()); put("read", 0); put("type", 1)
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v)
        } catch (e: Exception) { }
        Notifier.show(ctx, addr, body)
        Store.refresh(ctx)
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { /* MMS not supported yet */ }
}

class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

object Notifier {
    fun show(ctx: Context, addr: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && !Sms.granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("sms", "پیامک", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_SENDTO
            data = Uri.parse("smsto:" + Uri.encode(addr))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, addr.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = Sms.contactName(ctx, addr) ?: addr
        val n = NotificationCompat.Builder(ctx, "sms")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(addr.hashCode(), n)
    }
}
