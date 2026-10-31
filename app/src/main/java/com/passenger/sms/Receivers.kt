package com.passenger.sms

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        Boot.init(ctx)
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val addr = msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        var sub = intent.getIntExtra("subscription", -1)
        if (sub < 0) sub = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        try {
            val v = ContentValues().apply {
                put("address", addr); put("body", body)
                put("date", System.currentTimeMillis()); put("read", 0); put("type", 1)
                if (sub >= 0) put("sub_id", sub)
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v)
        } catch (e: Exception) { }
        val key = Sms.keyOf(addr)
        if (key !in Flags.blocked.value) Notifier.show(ctx, addr, body, sub)
        maybeAutoReply(ctx, addr, key, sub)
        Store.refresh(ctx)
    }

    private fun maybeAutoReply(ctx: Context, addr: String, key: String, sub: Int) {
        if (!Cfg.autoReplyOn.value) return
        if (Sms.contactName(ctx, addr) == null) return
        val sp = ctx.getSharedPreferences("autoreply", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong(key, 0L) < 30 * 60 * 1000L) return
        sp.edit().putLong(key, now).apply()
        Sms.send(ctx, addr, Cfg.autoReplyText.value, sub)
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { /* MMS not supported */ }
}

class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val uri = intent.data ?: return
        when (intent.action) {
            Sms.ACT_SENT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Sms.updateType(ctx, uri, 2)
                } else {
                    Sms.updateType(ctx, uri, 5)
                    Sched.scheduleRetry(
                        ctx, uri, intent.getStringExtra("to") ?: "",
                        intent.getStringExtra("body") ?: "", intent.getIntExtra("sub", -1)
                    )
                }
            }
            Sms.ACT_DELIVERED -> Sms.updateDelivered(ctx, uri)
        }
    }
}

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Boot.init(ctx)
        val addr = intent.getStringExtra("addr") ?: return
        val nid = intent.getIntExtra("nid", 0)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            Notifier.ACT_COPY -> {
                val code = intent.getStringExtra("code") ?: ""
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                toast(ctx, T("کپی شد", "Copied"))
                nm.cancel(nid)
            }
            Notifier.ACT_READ -> {
                Sms.markRead(ctx, addr)
                nm.cancel(nid)
                Store.refresh(ctx)
            }
            Notifier.ACT_REPLY -> {
                val txt = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.toString()
                if (!txt.isNullOrBlank()) {
                    Sms.send(ctx, addr, txt, intent.getIntExtra("sub", -1))
                    Sms.markRead(ctx, addr)
                }
                nm.cancel(nid)
                Store.refresh(ctx)
            }
        }
    }
}

object Notifier {
    const val ACT_COPY = "com.passenger.sms.COPY"
    const val ACT_READ = "com.passenger.sms.READ"
    const val ACT_REPLY = "com.passenger.sms.REPLY"

    private fun canPost(ctx: Context) =
        !(Build.VERSION.SDK_INT >= 33 && !Sms.granted(ctx, Manifest.permission.POST_NOTIFICATIONS))

    private fun channel(ctx: Context, nm: NotificationManager, p: NotifPref): String {
        val id = "ch_" + (if (p.vibrate) "v" else "n") + "_" + (p.sound?.hashCode() ?: 0)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, T("پیامک", "Messages"), NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(p.vibrate)
            if (p.sound == "silent") {
                ch.setSound(null, null)
            } else if (p.sound != null) {
                ch.setSound(
                    Uri.parse(p.sound),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
            nm.createNotificationChannel(ch)
        }
        return id
    }

    private fun openIntent(ctx: Context, addr: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_SENDTO
            data = Uri.parse("smsto:" + Uri.encode(addr))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, addr.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPi(ctx: Context, action: String, addr: String, nid: Int, sub: Int, code: String?, mutable: Boolean): PendingIntent {
        val i = Intent(ctx, NotifActionReceiver::class.java)
        i.action = action
        i.putExtra("addr", addr); i.putExtra("nid", nid); i.putExtra("sub", sub)
        if (code != null) i.putExtra("code", code)
        val flag = if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, (action + addr).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or flag)
    }

    fun show(ctx: Context, addr: String, body: String, sub: Int) {
        if (!canPost(ctx)) return
        val key = Sms.keyOf(addr)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val nid = addr.hashCode()
        val isPrivate = key in Flags.privateSet.value
        val info = Sms.contactInfo(ctx, addr)
        val name = info.first ?: Flags.aliases.value[key] ?: addr
        val cat = Classifier.classify(addr, body, info.first != null, Flags.overrideFor(key))
        val pref = NotifPrefs.resolve(key, cat)
        if (pref.mute) return
        val code = if (cat == Cat.OTP && !isPrivate) Classifier.extractCode(body) else null
        val b = NotificationCompat.Builder(ctx, channel(ctx, nm, pref))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(openIntent(ctx, addr))
            .setAutoCancel(true)
            .setSilent(Cfg.quietNow())
        if (pref.color != 0) b.setColor(pref.color)
        if (isPrivate) {
            b.setContentTitle("Passenger").setContentText(T("پیام جدید", "New message"))
        } else {
            val title = if (code != null) T("کد: ", "Code: ") + code + "  —  " + name else name
            b.setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            val reply = RemoteInput.Builder("reply").setLabel(T("پاسخ", "Reply")).build()
            b.addAction(
                NotificationCompat.Action.Builder(
                    0, T("پاسخ", "Reply"), actionPi(ctx, ACT_REPLY, addr, nid, sub, null, true)
                ).addRemoteInput(reply).build()
            )
            b.addAction(0, T("خوانده شد", "Mark read"), actionPi(ctx, ACT_READ, addr, nid, sub, null, false))
            if (code != null) {
                b.addAction(0, T("کپی کد", "Copy code"), actionPi(ctx, ACT_COPY, addr, nid, sub, code, false))
            }
        }
        nm.notify(nid, b.build())
    }

    fun reminder(ctx: Context, item: SchedItem) {
        if (!canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val p = NotifPref()
        val b = NotificationCompat.Builder(ctx, channel(ctx, nm, p))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(T("یادآوری", "Reminder"))
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setAutoCancel(true)
        if (item.address.isNotBlank()) b.setContentIntent(openIntent(ctx, item.address))
        nm.notify(item.id.hashCode(), b.build())
    }
}

class UnreadWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, views(ctx))
    }

    companion object {
        fun views(ctx: Context): RemoteViews {
            Cfg.init(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_unread)
            val n = Sms.unreadCount(ctx)
            v.setTextViewText(R.id.w_count, if (n < 0) "—" else n.toString())
            v.setTextViewText(R.id.w_label, T("پیام نخوانده", "Unread"))
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_root, pi)
            return v
        }

        fun updateAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, UnreadWidget::class.java))
            for (id in ids) m.updateAppWidget(id, views(ctx))
        }
    }
}
