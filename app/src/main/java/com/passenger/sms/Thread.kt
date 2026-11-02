package com.passenger.sms

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.telephony.SmsMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Replies {
    private val dueRe = Regex("(1[34]\\d{2})/(\\d{1,2})/(\\d{1,2})")
    private val linkRe = Regex("(https?://\\S+|www\\.\\S+|(?:\\+98|0)9\\d{9}|0\\d{2}[\\- ]?\\d{8})")

    fun suggest(body: String): List<String> {
        val b = body.lowercase()
        return when {
            b.contains("سلام") || b.contains("hello") || b.startsWith("hi") ->
                listOf(T("سلام", "Hi"), T("سلام، چطوری؟", "Hi, how are you?"))
            b.contains("؟") || b.contains("?") ->
                listOf(T("باشه", "OK"), T("نه", "No"), T("بعداً خبر می‌دم", "I'll let you know"))
            b.contains("ممنون") || b.contains("thank") || b.contains("مرسی") ->
                listOf(T("خواهش می‌کنم", "You're welcome"), "👍")
            b.contains("کجایی") || b.contains("where") ->
                listOf(T("دارم میام", "On my way"), T("تو راهم", "Almost there"))
            else -> listOf(T("باشه", "OK"), T("مرسی", "Thanks"), "👍")
        }
    }

    fun dueDate(body: String): Long {
        val m = dueRe.find(Fmt.norm(body))
        if (m != null) {
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            if (mo in 1..12 && d in 1..31) return Fmt.midnight(y, mo, d, true)
        }
        return System.currentTimeMillis() + 86400000L
    }

    fun links(body: String): List<String> = linkRe.findAll(body).map { it.value }.toList()
}

class ThreadRow(val msg: Msg?, val header: String?, val id: String)

private fun dayKey(ts: Long): Int {
    val a = Fmt.ymd(ts, false)
    return a[0] * 10000 + a[1] * 100 + a[2]
}

private fun dayLabel(ts: Long, t: GlassTheme): String {
    val now = System.currentTimeMillis()
    return when (dayKey(ts)) {
        dayKey(now) -> T("امروز", "Today")
        dayKey(now - 86400000L) -> T("دیروز", "Yesterday")
        else -> {
            val j = Fmt.ymd(ts, t.jalali)
            Fmt.digits("${j[2]} ", t.jalali) + Fmt.monthName(j[1], t.jalali) + Fmt.digits(" ${j[0]}", t.jalali)
        }
    }
}

private fun buildRows(list: List<Msg>, t: GlassTheme): List<ThreadRow> {
    val out = ArrayList<ThreadRow>()
    for (i in list.indices) {
        val m = list[i]
        out.add(ThreadRow(m, null, "m" + m.id))
        val dk = dayKey(m.date)
        val nextKey = if (i + 1 < list.size) dayKey(list[i + 1].date) else -1
        if (dk != nextKey) out.add(ThreadRow(null, dayLabel(m.date, t), "h$dk"))
    }
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(t: GlassTheme, address: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val convs by Store.convs
    val sims by Sim.sims
    val key = remember(address) { Sms.keyOf(address) }
    val conv = remember(address, convs) { convs.firstOrNull { it.key == key } }
    val name = conv?.name
    val starred by Flags.starred
    val pinnedSet by Flags.pinned
    val archivedSet by Flags.archived
    val blockedSet by Flags.blocked
    val privSet by Flags.privateSet
    val vipSet by Flags.vip
    val simMap by Flags.simFor
    val bgMap by Flags.convBg
    val aliasMap by Flags.aliases
    val pinMsgMap by Flags.pinnedMsg
    val autoSim by Cfg.autoSimByOp
    val chatBg = bgMap[key]?.toIntOrNull()?.let { Palettes.chatBgs.getOrNull(it) }
    val tx = chatBg?.text ?: t.text

    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var text by remember(address) { mutableStateOf(Store.sharedText.value ?: Flags.drafts.value[key] ?: "") }
    var multi by remember { mutableStateOf(setOf<Long>()) }
    var menuMsg by remember { mutableStateOf<Msg?>(null) }
    var askDelMsgs by remember { mutableStateOf<List<Long>?>(null) }
    var askDelConv by remember { mutableStateOf(false) }
    var askForward by remember { mutableStateOf<Msg?>(null) }
    var forwardTo by remember { mutableStateOf("") }
    var askRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var askBg by remember { mutableStateOf(false) }
    var askNotif by remember { mutableStateOf(false) }
    var askTemplates by remember { mutableStateOf(false) }
    var askUnsub by remember { mutableStateOf<String?>(null) }
    var askRemind by remember { mutableStateOf(false) }
    var pickMode by remember { mutableStateOf<String?>(null) }
    var pickBody by remember { mutableStateOf("") }
    var pickInitial by remember { mutableStateOf(0L) }
    var schedAt by remember { mutableStateOf<Long?>(null) }
    var pendingJob by remember { mutableStateOf<Job?>(null) }
    var pendingText by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    var trayOpen by remember { mutableStateOf(false) }

    val sendSub: Int = run {
        if (sims.size < 2) return@run -1
        val saved = simMap[key]?.toIntOrNull()
        if (saved != null && sims.any { it.subId == saved }) return@run saved
        if (autoSim) {
            val op = Operators.ofNumber(address)
            if (op != null) {
                val s = sims.firstOrNull { Operators.ofSim(it.label) == op }
                if (s != null) return@run s.subId
            }
        }
        val cand = msgs.firstOrNull { it.subId >= 0 }?.subId ?: Sim.defaultSub()
        if (sims.any { it.subId == cand }) cand else sims.first().subId
    }

    LaunchedEffect(address) {
        if (Store.sharedText.value != null) Store.sharedText.value = null
    }
    LaunchedEffect(convs, address) {
        val list = withContext(Dispatchers.IO) { Sms.loadThread(ctx, address) }
        msgs = list
        if (list.any { !it.read && it.type == 1 }) {
            withContext(Dispatchers.IO) { Sms.markRead(ctx, address) }
        }
    }
    LaunchedEffect(text) {
        delay(400)
        Flags.setDraft(key, text)
    }

    androidx.activity.compose.BackHandler(enabled = multi.isNotEmpty() || searchOpen) {
        if (multi.isNotEmpty()) {
            multi = emptySet()
        } else {
            searchOpen = false
            searchQ = ""
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try { Pdf.export(app, uri, name ?: address, Sms.loadThread(app, address), t); true } catch (e: Exception) { false }
                }
                toast(app, if (ok) T("فایل PDF ذخیره شد", "PDF saved") else T("ساخت PDF ناموفق بود", "PDF export failed"))
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    try { Csv.exportMsgs(app, uri, Sms.loadThread(app, address)) } catch (e: Exception) { -1 }
                }
                toast(app, if (n >= 0) T("$n پیام ذخیره شد", "$n messages exported") else T("خروجی ناموفق بود", "Export failed"))
            }
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val s = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (s != null) text = if (text.isBlank()) s else text + " " + s
    }

    fun signatureFor(sub: Int): String {
        val slot = sims.firstOrNull { it.subId == sub }?.slot ?: 0
        return if (slot == 1) Cfg.sig2.value else Cfg.sig1.value
    }

    fun actualSend(body: String, sub: Int) {
        if (!Sms.granted(app, Manifest.permission.SEND_SMS)) {
            toast(app, T("مجوز ارسال پیامک داده نشده", "SMS permission not granted"))
            return
        }
        val ok = Sms.send(app, address, body, sub)
        if (!ok) toast(app, T("ارسال ناموفق بود", "Send failed"))
        else if (!Sms.isDefault(app)) toast(app, T("ارسال شد (برای ذخیره، برنامه پیش‌فرض کنید)", "Sent (set as default app to store messages)"))
        Store.refresh(app)
    }

    fun submit() {
        val raw = text.trim()
        if (raw.isEmpty()) return
        val sig = signatureFor(sendSub)
        val body = if (sig.isNotBlank()) raw + "\n" + sig else raw
        text = ""
        Flags.setDraft(key, "")
        val d = Cfg.undoSecs.value
        val sub = sendSub
        if (d <= 0) {
            actualSend(body, sub)
        } else {
            pendingText = raw
            pendingJob = Store.scope.launch {
                delay(d * 1000L)
                actualSend(body, sub)
                pendingJob = null
            }
        }
    }

    fun pinShortcut() {
        try {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
                val intent = Intent(ctx, MainActivity::class.java)
                intent.action = Intent.ACTION_SENDTO
                intent.data = Uri.parse("smsto:" + Uri.encode(address))
                val info = ShortcutInfoCompat.Builder(ctx, "chat_$key")
                    .setShortLabel(aliasMap[key] ?: name ?: address)
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build()
                ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
            } else {
                toast(ctx, T("لانچر گوشی از این قابلیت پشتیبانی نمی‌کند", "Your launcher does not support pinned shortcuts"))
            }
        } catch (e: Exception) {
            toast(ctx, T("ساخت میانبر ناموفق بود", "Could not create shortcut"))
        }
    }

    fun remindIn(ms: Long) {
        Sched.schedule(app, "remind", System.currentTimeMillis() + ms, address,
            T("پاسخ به ", "Reply to ") + (aliasMap[key] ?: name ?: address), -1)
        toast(app, T("یادآوری تنظیم شد", "Reminder set"))
    }

    val shownMsgs = if (searchOpen && searchQ.isNotBlank()) msgs.filter { it.body.contains(searchQ, true) } else msgs
    val rows = remember(shownMsgs, t.jalali, Lang.code.value) { buildRows(shownMsgs, t) }
    val bgModifier = if (chatBg != null) Modifier.background(Brush.verticalGradient(chatBg.colors)) else Modifier
    val fg = if (t.flat) t.barText else t.text
    val pinnedMsgId = pinMsgMap[key]?.toLongOrNull()
    val pinnedMsg = if (pinnedMsgId != null) msgs.firstOrNull { it.id == pinnedMsgId } else null
    val lastIn = msgs.firstOrNull()?.takeIf { it.type == 1 }
    val unsubCode = lastIn?.let { Detect.unsubscribeCode(address, it.body) }

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        if (multi.isEmpty()) {
            AppBar(t, aliasMap[key] ?: name ?: address, onBack = onBack) {
                BarBtn(fg, R.drawable.ic_search) { searchOpen = !searchOpen; if (!searchOpen) searchQ = "" }
                MenuBtn(
                    fg,
                    listOf(
                        T("اعلان‌های این مکالمه", "Notifications") to { askNotif = true },
                        T("تغییر نام", "Rename") to { renameText = aliasMap[key] ?: name ?: ""; askRename = true },
                        (if (key in vipSet) T("حذف از مخاطبین ویژه", "Remove VIP") else T("مخاطب ویژه (VIP)", "Mark as VIP")) to {
                            Flags.setVip(listOf(key), key !in vipSet)
                        },
                        T("یادآوری برای پاسخ", "Remind me to reply") to { askRemind = true },
                        T("علامت نخوانده", "Mark as unread") to {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { Sms.markUnread(app, address) }
                                if (ok) {
                                    onBack()
                                    Store.refresh(app)
                                } else {
                                    toast(app, T("برای این کار باید برنامه پیش‌فرض باشد", "Set PSMS as default SMS app first"))
                                }
                            }
                        },
                        T("افزودن به صفحه اصلی گوشی", "Add to home screen") to { pinShortcut() },
                        T("ذخیره در مخاطبین", "Save to contacts") to {
                            try {
                                val i = Intent(Intent.ACTION_INSERT_OR_EDIT)
                                i.type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                i.putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                ctx.startActivity(i)
                            } catch (e: Exception) { }
                        },
                        T("تماس", "Call") to {
                            try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + address))) } catch (e: Exception) { }
                        },
                        T("پس‌زمینه مکالمه", "Chat background") to { askBg = true },
                        (if (key in pinnedSet) T("حذف پین", "Unpin") else T("پین کردن", "Pin")) to {
                            Flags.setPinned(listOf(key), key !in pinnedSet)
                        },
                        (if (key in archivedSet) T("خروج از بایگانی", "Unarchive") else T("بایگانی", "Archive")) to {
                            Flags.setArchived(listOf(key), key !in archivedSet)
                        },
                        (if (key in blockedSet) T("خروج از اسپم", "Not spam") else T("اسپم / مسدود", "Block as spam")) to {
                            Flags.setBlocked(listOf(key), key !in blockedSet)
                        },
                        (if (key in privSet) T("خروج از پوشه خصوصی", "Remove from private box") else T("انتقال به پوشه خصوصی", "Move to private box")) to {
                            if (Pro.gate(ctx, "private")) Flags.setPrivate(listOf(key), key !in privSet)
                        },
                        T("خروجی PDF", "Export PDF") to { if (Pro.gate(ctx, "pdf")) pdfLauncher.launch("chat.pdf") },
                        T("خروجی CSV (اکسل)", "Export CSV (Excel)") to { if (Pro.gate(ctx, "csv")) csvLauncher.launch("chat.csv") },
                        T("حذف مکالمه", "Delete conversation") to { askDelConv = true }
                    )
                )
            }
        } else {
            AppBar(t, Fmt.digits(T("${multi.size} پیام", "${multi.size} selected"), t.jalali), onBack = { multi = emptySet() }) {
                BarBtn(fg, R.drawable.ic_trash) { askDelMsgs = multi.toList() }
            }
        }

        if (searchOpen) {
            GlassField(t, searchQ, { searchQ = it }, T("جستجو در این مکالمه…", "Search in this chat…"),
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth())
        }

        if (pinnedMsg != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glass(t, 14.dp)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_pin, t.accent, 16.dp)
                Spacer(Modifier.size(8.dp))
                Text(pinnedMsg.body, color = t.text, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { Flags.setPinnedMsg(key, null) }, contentAlignment = Alignment.Center) {
                    Ic(R.drawable.ic_close, t.text, 16.dp)
                }
            }
        }

        if (unsubCode != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glass(t, 14.dp)
                    .clickable { askUnsub = unsubCode }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_info, Color(0xFFEF6C00), 18.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    T("اشتراک ناخواسته؟ لغو با یک لمس (ارسال $unsubCode)", "Unwanted subscription? Cancel with one tap (send $unsubCode)"),
                    color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f)
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().then(bgModifier)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.id }) { r ->
                    val m = r.msg
                    if (m == null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                r.header ?: "", color = tx.copy(alpha = 0.7f), fontSize = 11.sp,
                                modifier = Modifier.clip(CircleShape).background(tx.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        val sent = m.type != 1
                        val isSel = m.id in multi
                        val shape = RoundedCornerShape(t.bubble.dp)
                        val sentBg = if (t.flat) t.accent else t.accent.copy(alpha = 0.5f)
                        val sentFg = if (t.flat) t.onAccent else tx
                        val recvBg = if (chatBg != null) tx.copy(alpha = 0.12f)
                        else if (t.flat) t.surface else Color.White.copy(alpha = t.glass + 0.06f)
                        val fgc = if (sent) sentFg else tx
                        val subc = fgc.copy(alpha = 0.65f)
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(
                                Modifier.widthIn(max = 300.dp).clip(shape)
                                    .background(if (sent) sentBg else recvBg)
                                    .border(
                                        if (isSel) 2.dp else 0.5.dp,
                                        if (isSel) t.accent else tx.copy(alpha = 0.15f), shape
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (multi.isNotEmpty()) {
                                                multi = if (isSel) multi - m.id else multi + m.id
                                            }
                                        },
                                        onLongClick = {
                                            if (multi.isNotEmpty()) {
                                                multi = if (isSel) multi - m.id else multi + m.id
                                            } else {
                                                menuMsg = m
                                            }
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(m.body, color = fgc, fontSize = (15 * t.msgScale).sp)
                                if (!sent && Classifier.isOtp(m.body)) {
                                    val code = Classifier.extractCode(m.body)
                                    if (code != null) {
                                        Spacer(Modifier.size(6.dp))
                                        Row(
                                            Modifier.clip(CircleShape).background(t.accent.copy(alpha = 0.25f))
                                                .clickable {
                                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                                    cm.setPrimaryClip(ClipData.newPlainText("code", code))
                                                    toast(ctx, T("کد کپی شد", "Code copied"))
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Ic(R.drawable.ic_copy, fgc, 14.dp)
                                            Spacer(Modifier.size(6.dp))
                                            Text(T("کپی کد ", "Copy code ") + code, color = fgc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (!sent && LinkCheck.suspicious(m.body)) {
                                    Spacer(Modifier.size(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Ic(R.drawable.ic_info, Color(0xFFE53935), 14.dp)
                                        Spacer(Modifier.size(4.dp))
                                        Text(T("لینک مشکوک؛ باز نکنید", "Suspicious link — be careful"), color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                val slot = Sim.slotLabel(m.subId)
                                val status = when {
                                    !sent -> ""
                                    m.type == 4 -> " …"
                                    m.type == 5 -> " ✗ " + T("ناموفق", "Failed")
                                    m.status == 0 -> " ✓✓"
                                    else -> " ✓"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (m.id.toString() in starred) {
                                        Ic(R.drawable.ic_star, subc, 11.dp)
                                        Spacer(Modifier.size(3.dp))
                                    }
                                    Text(
                                        Fmt.time(m.date, t, withDate = true) + (if (slot != null) "  $slot" else "") + status,
                                        color = if (m.type == 5) Color(0xFFE53935) else subc, fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pendingJob != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glass(t, 14.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(T("در حال ارسال…", "Sending…"), color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    pendingJob?.cancel()
                    pendingJob = null
                    text = pendingText
                }) { Text(T("لغو ارسال", "Undo")) }
            }
        }

        if (lastIn != null && text.isEmpty() && pendingJob == null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Replies.suggest(lastIn.body)) { s -> Chip(t, s, false) { text = s } }
            }
        }

        if (trayOpen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glass(t, 18.dp).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrayBtn(t, R.drawable.ic_bolt, T("آماده", "Templates")) { trayOpen = false; askTemplates = true }
                TrayBtn(t, R.drawable.ic_clock, T("زمان‌بندی", "Schedule")) {
                    trayOpen = false
                    if (text.isBlank()) {
                        toast(ctx, T("اول متن پیام را بنویسید", "Type the message first"))
                    } else {
                        pickMode = "send"
                        pickBody = text.trim()
                        pickInitial = System.currentTimeMillis() + 3600000L
                    }
                }
                TrayBtn(t, R.drawable.ic_mic, T("صدا", "Voice")) {
                    trayOpen = false
                    try {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.isFa) "fa-IR" else "en-US")
                        voiceLauncher.launch(i)
                    } catch (e: Exception) {
                        toast(ctx, T("تایپ صوتی در این گوشی در دسترس نیست", "Voice typing is not available on this phone"))
                    }
                }
                TrayBtn(t, R.drawable.ic_repeat, T("یادآوری", "Remind")) { trayOpen = false; askRemind = true }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { trayOpen = !trayOpen },
                contentAlignment = Alignment.Center
            ) { Ic(if (trayOpen) R.drawable.ic_close else R.drawable.ic_plus, tx, 22.dp) }
            if (sims.size >= 2) {
                Box(
                    Modifier.clip(CircleShape).background(t.accent.copy(alpha = if (t.flat) 1f else 0.4f))
                        .clickable {
                            val next = sims.firstOrNull { it.subId != sendSub } ?: sims.first()
                            Flags.setSim(key, next.subId)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val slot = sims.firstOrNull { it.subId == sendSub }?.slot ?: 0
                    Text("SIM ${slot + 1}", color = if (t.flat) t.onAccent else t.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                GlassField(t, text, { text = it }, T("پیام…", "Message…"), Modifier.fillMaxWidth(), singleLine = false)
                if (text.length > 60) {
                    val cnt = try { SmsMessage.calculateLength(text, false) } catch (e: Exception) { null }
                    if (cnt != null && cnt.size >= 3) {
                        Text(
                            Fmt.digits("${cnt[2]} · ${cnt[0]} " + T("پیامک", "SMS"), t.jalali),
                            color = tx.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                        )
                    }
                }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(t.accent).clickable { submit() },
                contentAlignment = Alignment.Center
            ) { Ic(R.drawable.ic_send, t.onAccent, 22.dp) }
        }
    }

    // ----- dialogs -----

    val mm = menuMsg
    if (mm != null) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add(T("کپی", "Copy") to {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("sms", mm.body))
            toast(ctx, T("کپی شد", "Copied"))
        })
        for (f in Detect.find(mm.body)) {
            when (f.kind) {
                "card" -> items.add(T("کپی شماره کارت: ", "Copy card number: ") + f.value to {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("card", f.value))
                    toast(ctx, T("کپی شد", "Copied"))
                })
                "amount" -> items.add(T("کپی مبلغ: ", "Copy amount: ") + f.value to {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("amount", f.value.replace(",", "")))
                    toast(ctx, T("کپی شد", "Copied"))
                })
                "track" -> items.add(T("کپی کد رهگیری و باز کردن سایت پست", "Copy tracking code & open Post site") to {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("track", f.value))
                    toast(ctx, T("کد کپی شد: ", "Code copied: ") + f.value)
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tracking.post.ir/"))) } catch (e: Exception) { }
                })
                "address" -> items.add(T("نمایش آدرس روی نقشه", "Show address on map") to {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.value))))
                    } catch (e: Exception) {
                        toast(ctx, T("برنامه نقشه پیدا نشد", "No map app found"))
                    }
                })
            }
        }
        items.add(T("خواندن با صدا", "Read aloud") to { Speaker.speak(ctx, mm.body) })
        items.add(T("اشتراک‌گذاری متن", "Share text") to {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, mm.body)
            ctx.startActivity(Intent.createChooser(i, null))
        })
        items.add(T("اشتراک‌گذاری به‌صورت تصویر", "Share as image") to {
            if (Pro.gate(ctx, "cardimage")) {
                scope.launch {
                    val f = withContext(Dispatchers.IO) {
                        CardImage.create(app, aliasMap[key] ?: name ?: address, mm.body, Fmt.time(mm.date, t, withDate = true))
                    }
                    CardImage.share(ctx, f)
                }
            }
        })
        items.add(T("فوروارد به شماره", "Forward to number") to { forwardTo = ""; askForward = mm })
        val isStar = mm.id.toString() in starred
        items.add((if (isStar) T("حذف ستاره", "Remove star") else T("ستاره‌دار کردن", "Star")) to {
            Flags.setStarred(listOf(mm.id.toString()), !isStar)
        })
        val isPinnedMsg = pinnedMsgId == mm.id
        items.add((if (isPinnedMsg) T("حذف پین پیام", "Unpin message") else T("پین پیام بالای مکالمه", "Pin message to top")) to {
            Flags.setPinnedMsg(key, if (isPinnedMsg) null else mm.id)
        })
        items.add(T("یادآوری", "Remind me") to {
            pickMode = "remind"
            pickBody = mm.body
            pickInitial = Replies.dueDate(mm.body)
        })
        if (mm.type == 5) {
            items.add(T("ارسال مجدد", "Resend") to {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Sms.deleteMessages(app, listOf(mm.id), false)
                        Sms.send(app, address, mm.body, sendSub)
                    }
                    Store.refresh(app)
                }
            })
        }
        for (l in Replies.links(mm.body)) {
            val isUrl = l.startsWith("http") || l.startsWith("www")
            val warn = isUrl && LinkCheck.suspicious(l)
            items.add((if (isUrl) (if (warn) T("⚠ لینک مشکوک: ", "⚠ Suspicious link: ") else T("باز کردن لینک: ", "Open link: ")) else T("تماس با ", "Call ")) + l to {
                try {
                    if (isUrl) {
                        val u = if (l.startsWith("http")) l else "https://$l"
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                    } else {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$l")))
                    }
                } catch (e: Exception) { }
            })
        }
        items.add(T("انتخاب چند پیام", "Select") to { multi = multi + mm.id })
        items.add(T("حذف", "Delete") to { askDelMsgs = listOf(mm.id) })
        AlertDialog(
            onDismissRequest = { menuMsg = null },
            title = {
                val slot = Sim.slotLabel(mm.subId)
                Text(Fmt.time(mm.date, t, withDate = true) + (if (slot != null) "  ·  $slot" else ""), fontSize = 14.sp)
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((label, act) in items) {
                        Text(
                            label, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { menuMsg = null; act() }.padding(vertical = 11.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuMsg = null }) { Text(T("بستن", "Close")) } }
        )
    }

    val delIds = askDelMsgs
    if (delIds != null) {
        AlertDialog(
            onDismissRequest = { askDelMsgs = null },
            title = { Text(T("حذف پیام‌ها", "Delete messages")) },
            text = { Text(Fmt.digits(T("${delIds.size} پیام حذف می‌شود (۳۰ روز در سطل زباله می‌ماند).", "${delIds.size} message(s) will be deleted (kept in Trash for 30 days)."), t.jalali)) },
            confirmButton = {
                TextButton(onClick = {
                    askDelMsgs = null
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, T("برای حذف، PSMS باید برنامه پیش‌فرض پیامک باشد", "PSMS must be the default SMS app to delete"))
                    } else {
                        multi = emptySet()
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteMessages(app, delIds) }
                            Store.refresh(app)
                        }
                    }
                }) { Text(T("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { askDelMsgs = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askDelConv) {
        AlertDialog(
            onDismissRequest = { askDelConv = false },
            title = { Text(T("حذف کل مکالمه", "Delete conversation")) },
            text = { Text(T("همه‌ی پیام‌ها حذف می‌شوند (۳۰ روز در سطل زباله می‌ماند).", "All messages will be deleted (kept in Trash for 30 days).")) },
            confirmButton = {
                TextButton(onClick = {
                    askDelConv = false
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, T("برای حذف، PSMS باید برنامه پیش‌فرض پیامک باشد", "PSMS must be the default SMS app to delete"))
                    } else {
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteConvs(app, listOf(address)) }
                            Store.refresh(app)
                            onBack()
                        }
                    }
                }) { Text(T("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { askDelConv = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    val fw = askForward
    if (fw != null) {
        AlertDialog(
            onDismissRequest = { askForward = null },
            title = { Text(T("فوروارد پیام", "Forward message")) },
            text = { GlassField(t, forwardTo, { forwardTo = it }, T("شماره گیرنده", "Recipient number"), Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val to = forwardTo.trim()
                    askForward = null
                    if (to.isNotEmpty()) {
                        Sms.send(app, to, fw.body, sendSub)
                        toast(app, T("ارسال شد", "Sent"))
                        Store.refresh(app)
                    }
                }) { Text(T("ارسال", "Send")) }
            },
            dismissButton = { TextButton(onClick = { askForward = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askRename) {
        AlertDialog(
            onDismissRequest = { askRename = false },
            title = { Text(T("نام نمایشی (فقط در این برنامه)", "Display name (this app only)")) },
            text = { GlassField(t, renameText, { renameText = it }, T("نام", "Name"), Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    askRename = false
                    Flags.setAlias(key, renameText)
                    Store.refresh(app)
                }) { Text(T("ذخیره", "Save")) }
            },
            dismissButton = { TextButton(onClick = { askRename = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askBg) {
        AlertDialog(
            onDismissRequest = { askBg = false },
            title = { Text(T("پس‌زمینه مکالمه", "Chat background")) },
            text = {
                Column {
                    Text(
                        T("پیش‌فرض تم", "Theme default"), fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { Flags.setConvBg(key, null); askBg = false }.padding(vertical = 11.dp)
                    )
                    Palettes.chatBgs.forEachIndexed { i, b ->
                        Text(
                            T(b.fa, b.en), fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { Flags.setConvBg(key, i); askBg = false }.padding(vertical = 11.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askBg = false }) { Text(T("بستن", "Close")) } }
        )
    }

    if (askNotif) {
        NotifEditor(t, "k:$key", T("اعلان‌های این مکالمه", "Notifications for this chat")) { askNotif = false }
    }

    if (askTemplates) {
        AlertDialog(
            onDismissRequest = { askTemplates = false },
            title = { Text(T("پیام‌های آماده", "Templates")) },
            text = {
                Column {
                    if (Cfg.templates.value.isEmpty()) Text(T("قالبی ثبت نشده (از تنظیمات اضافه کنید)", "No templates yet (add them in Settings)"))
                    for (s in Cfg.templates.value) {
                        Text(
                            s, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { text = s; askTemplates = false }.padding(vertical = 11.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askTemplates = false }) { Text(T("بستن", "Close")) } }
        )
    }

    val us = askUnsub
    if (us != null) {
        AlertDialog(
            onDismissRequest = { askUnsub = null },
            title = { Text(T("لغو اشتراک", "Cancel subscription")) },
            text = { Text(T("عدد $us به این شماره ارسال می‌شود تا اشتراک لغو شود.", "\"$us\" will be sent to this number to cancel the subscription.")) },
            confirmButton = {
                TextButton(onClick = {
                    askUnsub = null
                    actualSend(us, sendSub)
                }) { Text(T("ارسال", "Send")) }
            },
            dismissButton = { TextButton(onClick = { askUnsub = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askRemind) {
        AlertDialog(
            onDismissRequest = { askRemind = false },
            title = { Text(T("یادآوری برای پاسخ", "Remind me to reply")) },
            text = {
                Column {
                    val opts = listOf(
                        T("۳۰ دقیقه دیگر", "In 30 minutes") to 30L * 60000L,
                        T("۱ ساعت دیگر", "In 1 hour") to 3600000L,
                        T("۳ ساعت دیگر", "In 3 hours") to 3L * 3600000L,
                        T("فردا همین موقع", "Tomorrow") to 86400000L
                    )
                    for ((label, ms) in opts) {
                        Text(
                            label, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { askRemind = false; remindIn(ms) }.padding(vertical = 11.dp)
                        )
                    }
                    Text(
                        T("تاریخ دلخواه…", "Pick a date…"), fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            askRemind = false
                            pickMode = "remind"
                            pickBody = T("پاسخ به ", "Reply to ") + (aliasMap[key] ?: name ?: address)
                            pickInitial = System.currentTimeMillis() + 86400000L
                        }.padding(vertical = 11.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askRemind = false }) { Text(T("بستن", "Close")) } }
        )
    }

    val mode = pickMode
    if (mode != null) {
        JalaliPicker(
            t = t, initial = pickInitial,
            onDismiss = { pickMode = null },
            onPick = { dayMs ->
                pickMode = null
                val defH = if (mode == "send") (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24 else 9
                android.app.TimePickerDialog(ctx, { _, h, mi ->
                    val at = dayMs + h * 3600000L + mi * 60000L
                    if (mode == "send") {
                        schedAt = at
                    } else {
                        Sched.schedule(app, "remind", at, address, pickBody, -1)
                        toast(app, T("یادآوری تنظیم شد", "Reminder set"))
                    }
                }, defH, 0, true).show()
            }
        )
    }

    val at = schedAt
    if (at != null) {
        AlertDialog(
            onDismissRequest = { schedAt = null },
            title = { Text(T("تکرار پیام", "Repeat")) },
            text = {
                Column {
                    val opts = listOf(
                        "" to T("فقط یک بار", "Once"),
                        "daily" to T("هر روز", "Every day"),
                        "weekly" to T("هر هفته", "Every week"),
                        "monthly" to T("هر ماه", "Every month"),
                        "yearly" to T("هر سال (مثلاً تبریک تولد)", "Every year (e.g. birthday)")
                    )
                    for ((rep, label) in opts) {
                        Text(
                            label, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (rep.isNotEmpty() && !Pro.gate(ctx, "repeat")) {
                                    schedAt = null
                                } else {
                                    Sched.schedule(app, "send", at, address, pickBody, sendSub, "", rep)
                                    text = ""
                                    Flags.setDraft(key, "")
                                    schedAt = null
                                    toast(app, T("پیام زمان‌بندی شد", "Message scheduled"))
                                }
                            }.padding(vertical = 11.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { schedAt = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}

@Composable
fun TrayBtn(t: GlassTheme, icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Ic(icon, t.text, 22.dp)
        Text(label, color = t.subText, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
