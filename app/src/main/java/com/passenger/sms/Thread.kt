package com.passenger.sms

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val simMap by Flags.simFor
    val bgMap by Flags.convBg
    val aliasMap by Flags.aliases
    val chatBg = bgMap[key]?.toIntOrNull()?.let { Palettes.chatBgs.getOrNull(it) }
    val tx = chatBg?.text ?: t.text
    val txSub = tx.copy(alpha = 0.65f)

    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var text by remember(address) { mutableStateOf(Flags.drafts.value[key] ?: "") }
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
    var pickMode by remember { mutableStateOf<String?>(null) }
    var pickBody by remember { mutableStateOf("") }
    var pickInitial by remember { mutableStateOf(0L) }
    var pendingJob by remember { mutableStateOf<Job?>(null) }
    var pendingText by remember { mutableStateOf("") }

    val sendSub: Int = run {
        if (sims.size < 2) return@run -1
        val saved = simMap[key]?.toIntOrNull()
        val cand = saved ?: msgs.firstOrNull { it.subId >= 0 }?.subId ?: Sim.defaultSub()
        if (sims.any { it.subId == cand }) cand else sims.first().subId
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

    androidx.activity.compose.BackHandler(enabled = multi.isNotEmpty()) { multi = emptySet() }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        Pdf.export(app, uri, name ?: address, Sms.loadThread(app, address), t)
                        true
                    } catch (e: Exception) { false }
                }
                toast(app, if (ok) T("فایل PDF ذخیره شد", "PDF saved") else T("ساخت PDF ناموفق بود", "PDF export failed"))
            }
        }
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
        val body = text.trim()
        if (body.isEmpty()) return
        text = ""
        Flags.setDraft(key, "")
        val d = Cfg.undoSecs.value
        val sub = sendSub
        if (d <= 0) {
            actualSend(body, sub)
        } else {
            pendingText = body
            pendingJob = Store.scope.launch {
                delay(d * 1000L)
                actualSend(body, sub)
                pendingJob = null
            }
        }
    }

    val bgModifier = if (chatBg != null) Modifier.background(Brush.verticalGradient(chatBg.colors)) else Modifier

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        val fg = if (t.flat) t.barText else t.text
        if (multi.isEmpty()) {
            AppBar(t, aliasMap[key] ?: name ?: address, onBack = onBack) {
                MenuBtn(
                    fg,
                    listOf(
                        T("اعلان‌های این مکالمه", "Notifications") to { askNotif = true },
                        T("تغییر نام", "Rename") to { renameText = aliasMap[key] ?: name ?: ""; askRename = true },
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
                            Flags.setPrivate(listOf(key), key !in privSet)
                        },
                        T("خروجی PDF", "Export PDF") to { pdfLauncher.launch("chat.pdf") },
                        T("حذف مکالمه", "Delete conversation") to { askDelConv = true }
                    )
                )
            }
        } else {
            AppBar(t, Fmt.digits(T("${multi.size} پیام", "${multi.size} selected"), t.jalali), onBack = { multi = emptySet() }) {
                BarBtn(fg, "🗑") { askDelMsgs = multi.toList() }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().then(bgModifier)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(msgs, key = { it.id }) { m ->
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
                            val slot = Sim.slotLabel(m.subId)
                            val status = when {
                                !sent -> ""
                                m.type == 4 -> " …"
                                m.type == 5 -> " ✗ " + T("ناموفق", "Failed")
                                m.status == 0 -> " ✓✓"
                                else -> " ✓"
                            }
                            val star = if (m.id.toString() in starred) "★ " else ""
                            Text(
                                star + Fmt.time(m.date, t, withDate = true) + (if (slot != null) "  $slot" else "") + status,
                                color = if (m.type == 5) Color(0xFFE53935) else subc, fontSize = 10.sp
                            )
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

        val lastIn = msgs.firstOrNull()?.takeIf { it.type == 1 }
        if (lastIn != null && text.isEmpty() && pendingJob == null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Replies.suggest(lastIn.body)) { s -> Chip(t, s, false) { text = s } }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { askTemplates = true }, contentAlignment = Alignment.Center) {
                Text("⚡", fontSize = 18.sp)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable {
                    if (text.isBlank()) {
                        toast(ctx, T("اول متن پیام را بنویسید", "Type the message first"))
                    } else {
                        pickMode = "send"
                        pickBody = text.trim()
                        pickInitial = System.currentTimeMillis() + 3600000L
                    }
                },
                contentAlignment = Alignment.Center
            ) { Text("⏰", fontSize = 18.sp) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(t.accent).clickable { submit() },
                contentAlignment = Alignment.Center
            ) { Text("➤", color = t.onAccent, fontSize = 20.sp) }
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
        items.add(T("اشتراک‌گذاری", "Share") to {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, mm.body)
            ctx.startActivity(Intent.createChooser(i, null))
        })
        items.add(T("فوروارد به شماره", "Forward to number") to { forwardTo = ""; askForward = mm })
        val isStar = mm.id.toString() in starred
        items.add((if (isStar) T("حذف ستاره", "Remove star") else T("ستاره‌دار کردن", "Star")) to {
            Flags.setStarred(listOf(mm.id.toString()), !isStar)
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
                        Sms.deleteMessages(app, listOf(mm.id))
                        Sms.send(app, address, mm.body, sendSub)
                    }
                    Store.refresh(app)
                }
            })
        }
        for (l in Replies.links(mm.body)) {
            val isUrl = l.startsWith("http") || l.startsWith("www")
            items.add((if (isUrl) "🔗 " else "📞 ") + l to {
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
                Column {
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
                        toast(ctx, T("برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد", "Passenger must be the default SMS app to delete"))
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
                        toast(ctx, T("برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد", "Passenger must be the default SMS app to delete"))
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
                        Sched.schedule(app, "send", at, address, pickBody, sendSub)
                        text = ""
                        Flags.setDraft(key, "")
                        toast(app, T("پیام زمان‌بندی شد", "Message scheduled"))
                    } else {
                        Sched.schedule(app, "remind", at, address, pickBody, -1)
                        toast(app, T("یادآوری تنظیم شد", "Reminder set"))
                    }
                }, defH, 0, true).show()
            }
        )
    }
}
