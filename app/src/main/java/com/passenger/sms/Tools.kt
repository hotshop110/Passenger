package com.passenger.sms

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------- date picker (Jalali or Gregorian) ----------

@Composable
private fun Stepper(t: GlassTheme, label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(60.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onMinus() }, contentAlignment = Alignment.Center) { Text("−", fontSize = 22.sp) }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onPlus() }, contentAlignment = Alignment.Center) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
fun JalaliPicker(t: GlassTheme, initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val ymd0 = remember { Fmt.ymd(initial, t.jalali) }
    var y by remember { mutableStateOf(ymd0[0]) }
    var m by remember { mutableStateOf(ymd0[1]) }
    var d by remember { mutableStateOf(ymd0[2]) }
    val maxD = Fmt.daysIn(y, m, t.jalali)
    val dd = if (d > maxD) maxD else d
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("انتخاب تاریخ", "Pick a date")) },
        text = {
            Column {
                Stepper(t, T("سال", "Year"), Fmt.digits("$y", t.jalali), { y-- }, { y++ })
                Stepper(t, T("ماه", "Month"), Fmt.monthName(m, t.jalali), { m = if (m <= 1) 12 else m - 1 }, { m = if (m >= 12) 1 else m + 1 })
                Stepper(t, T("روز", "Day"), Fmt.digits("$dd", t.jalali), { d = if (dd <= 1) maxD else dd - 1 }, { d = if (dd >= maxD) 1 else dd + 1 })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Fmt.midnight(y, m, dd, t.jalali)) }) { Text(T("تأیید", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("انصراف", "Cancel")) } }
    )
}

// ---------- notification editor ----------

@Composable
fun NotifEditor(t: GlassTheme, id: String, title: String, onDismiss: () -> Unit) {
    val p = NotifPrefs.map.value[id] ?: NotifPref()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION")
        val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (u != null) NotifPrefs.set(id, NotifPrefs.get(id).copy(sound = u.toString()))
    }
    val colors = listOf(0, Color(0xFF1976D2).toArgb(), Color(0xFF2E7D32).toArgb(), Color(0xFFD81B60).toArgb(),
        Color(0xFFEF6C00).toArgb(), Color(0xFF7B1FA2).toArgb())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow(t, T("بی‌صدا (بدون اعلان)", "Mute (no notification)"), p.mute) { NotifPrefs.set(id, p.copy(mute = it)) }
                SwitchRow(t, T("لرزش", "Vibrate"), p.vibrate) { NotifPrefs.set(id, p.copy(vibrate = it)) }
                Text(T("صدا", "Sound"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Chip(t, T("پیش‌فرض", "Default"), p.sound == null) { NotifPrefs.set(id, p.copy(sound = null)) }
                    Chip(t, T("بی‌صدا", "Silent"), p.sound == "silent") { NotifPrefs.set(id, p.copy(sound = "silent")) }
                    Chip(t, T("انتخاب…", "Choose…"), p.sound != null && p.sound != "silent") {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        picker.launch(i)
                    }
                }
                Text(T("رنگ اعلان", "Notification color"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (c in colors) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (c == 0) Color.LightGray else Color(c))
                                .border(if (p.color == c) 3.dp else 1.dp, if (p.color == c) Color.Black else Color.Gray, CircleShape)
                                .clickable { NotifPrefs.set(id, p.copy(color = c)) },
                            contentAlignment = Alignment.Center
                        ) { if (c == 0) Text("∅", fontSize = 14.sp) }
                    }
                }
                TextButton(onClick = { NotifPrefs.clear(id) }) { Text(T("بازنشانی", "Reset")) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T("بستن", "Close")) } }
    )
}

// ---------- advanced search ----------

data class SearchRow(val m: Msg, val name: String?, val cat: Cat)

@Composable
fun MsgRow(t: GlassTheme, name: String, snippet: String, date: String, onClick: () -> Unit, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().glass(t, 14.dp).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(name, color = t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(date, color = t.subText, fontSize = 11.sp)
            }
            Text(snippet, color = t.subText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null && onTrailing != null) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onTrailing() }, contentAlignment = Alignment.Center) {
                Text(trailing, fontSize = 16.sp, color = t.text)
            }
        }
    }
}

@Composable
fun SearchPage(t: GlassTheme, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val sims by Sim.sims
    val priv by Flags.privateSet
    var q by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(0L) }
    var to by remember { mutableStateOf(0L) }
    var simF by remember { mutableStateOf(-1) }
    var catF by remember { mutableStateOf(Cat.ALL) }
    var picking by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(listOf<SearchRow>()) }

    LaunchedEffect(q, from, to, simF, catF) {
        delay(300)
        results = withContext(Dispatchers.IO) {
            Sms.search(ctx, q, from, to, simF).filter { Sms.keyOf(it.address) !in priv }.map {
                val info = Sms.contactInfo(ctx, it.address)
                val cat = Classifier.classify(it.address, it.body, info.first != null, null)
                SearchRow(it, info.first ?: Flags.aliases.value[Sms.keyOf(it.address)], cat)
            }.filter { catF == Cat.ALL || it.cat == catF }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("جستجوی پیشرفته", "Advanced search"), onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassField(t, q, { q = it }, T("متن پیام…", "Message text…"), Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Chip(t, T("از: ", "From: ") + (if (from > 0) Fmt.date(from, t) else "—"), from > 0) { picking = "from" }
                Chip(t, T("تا: ", "To: ") + (if (to > 0) Fmt.date(to - 86400000L, t) else "—"), to > 0) { picking = "to" }
                if (from > 0 || to > 0) Chip(t, T("پاک کردن تاریخ", "Clear dates"), false) { from = 0L; to = 0L }
            }
            if (sims.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t, T("همه سیم‌ها", "All SIMs"), simF < 0) { simF = -1 }
                    for (s in sims) Chip(t, "SIM ${s.slot + 1}", simF == s.subId) { simF = s.subId }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (c in listOf(Cat.ALL, Cat.CONTACTS, Cat.BANK, Cat.OTP, Cat.GOV, Cat.PROMO, Cat.UNKNOWN)) {
                    Chip(t, c.label, catF == c) { catF = c }
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (results.isEmpty()) {
                item { Text(T("نتیجه‌ای نیست", "No results"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            items(results, key = { it.m.id }) { r ->
                MsgRow(t, r.name ?: r.m.address, r.m.body, Fmt.time(r.m.date, t, withDate = true), { onOpen(r.m.address) })
            }
        }
    }

    val pk = picking
    if (pk != null) {
        JalaliPicker(
            t = t, initial = if (pk == "from") (if (from > 0) from else System.currentTimeMillis() - 30L * 86400000L)
            else (if (to > 0) to - 86400000L else System.currentTimeMillis()),
            onDismiss = { picking = null },
            onPick = { ms ->
                if (pk == "from") from = ms else to = ms + 86400000L
                picking = null
            }
        )
    }
}

// ---------- starred ----------

@Composable
fun StarredPage(t: GlassTheme, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val starred by Flags.starred
    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    LaunchedEffect(starred) {
        msgs = withContext(Dispatchers.IO) { Sms.loadByIds(ctx, starred.mapNotNull { it.toLongOrNull() }) }
    }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("پیام‌های ستاره‌دار", "Starred messages"), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (msgs.isEmpty()) {
                item { Text(T("پیامی ستاره‌دار نشده", "No starred messages"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            items(msgs, key = { it.id }) { m ->
                val nm = Sms.contactName(ctx, m.address) ?: Flags.aliases.value[Sms.keyOf(m.address)] ?: m.address
                MsgRow(t, nm, m.body, Fmt.time(m.date, t, withDate = true), { onOpen(m.address) }, "★") {
                    Flags.setStarred(listOf(m.id.toString()), false)
                }
            }
        }
    }
}

// ---------- scheduled ----------

@Composable
fun ScheduledPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sched by remember { mutableStateOf(Sched.all(ctx).filter { it.type != "retry" }) }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("زمان‌بندی‌شده‌ها و یادآوری‌ها", "Scheduled & reminders"), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sched.isEmpty()) {
                item { Text(T("موردی نیست", "Nothing scheduled"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            items(sched, key = { it.id }) { it ->
                val kind = if (it.type == "send") T("ارسال", "Send") else T("یادآوری", "Reminder")
                MsgRow(
                    t, kind + " · " + it.address, it.body, Fmt.time(it.at, t, withDate = true), {},
                    "✕"
                ) {
                    Sched.remove(ctx, it.id)
                    sched = Sched.all(ctx).filter { x -> x.type != "retry" }
                }
            }
        }
    }
}

// ---------- trash ----------

@Composable
fun TrashPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var trash by remember { mutableStateOf(listOf<TrashMsg>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Trash.purgeOld(ctx) }
        trash = withContext(Dispatchers.IO) { Trash.load(ctx) }
    }
    val groups = trash.groupBy { Sms.keyOf(it.address) }.values.sortedByDescending { g -> g.maxOf { it.deletedAt } }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("سطل زباله (۳۰ روز)", "Trash (30 days)"), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (groups.isEmpty()) {
                item { Text(T("سطل زباله خالی است", "Trash is empty"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            items(groups, key = { it.first().address + it.first().deletedAt }) { g ->
                val last = g.maxByOrNull { it.date }!!
                Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(last.address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(last.body, color = t.subText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.digits(T("${g.size} پیام · حذف: ", "${g.size} message(s) · deleted: "), t.jalali) + Fmt.date(g.maxOf { it.deletedAt }, t),
                        color = t.subText, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t, T("بازیابی", "Restore"), false) {
                            if (!Sms.isDefault(ctx)) {
                                toast(ctx, T("برای بازیابی باید برنامه پیش‌فرض باشد", "Set Passenger as default SMS app first"))
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Trash.restore(ctx, g) }
                                    trash = withContext(Dispatchers.IO) { Trash.load(ctx) }
                                    Store.refresh(ctx)
                                }
                            }
                        }
                        Chip(t, T("حذف همیشگی", "Delete forever"), false) {
                            val set = g.toSet()
                            scope.launch {
                                withContext(Dispatchers.IO) { Trash.remove(ctx) { it in set } }
                                trash = withContext(Dispatchers.IO) { Trash.load(ctx) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- bank summary ----------

fun money(v: Long, t: GlassTheme): String = Fmt.digits(String.format(Locale.US, "%,d", v), t.jalali)

@Composable
fun BankPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<MonthSum>?>(null) }
    LaunchedEffect(Unit) { rows = withContext(Dispatchers.IO) { Bank.load(ctx, t.jalali) } }
    val list = rows
    val maxVal = list?.maxOfOrNull { maxOf(it.credit, it.debit) } ?: 1L
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("خلاصه پیام‌های بانکی", "Bank summary"), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    T("مبلغ‌ها از متن پیامک‌های بانکی ۱۲ ماه اخیر تشخیص داده می‌شود و ممکن است دقیق نباشد.",
                        "Amounts are detected from bank SMS text over the last 12 months and may not be exact."),
                    color = t.subText, fontSize = 12.sp
                )
            }
            if (list == null) {
                item { Text(T("در حال محاسبه…", "Calculating…"), color = t.subText) }
            } else if (list.isEmpty()) {
                item { Text(T("پیام بانکی‌ای پیدا نشد", "No bank messages found"), color = t.subText) }
            }
            items(list ?: emptyList()) { r ->
                Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Fmt.monthName(r.m, t.jalali) + " " + Fmt.digits("${r.y}", t.jalali), color = t.text, fontWeight = FontWeight.Bold)
                    Text(T("برداشت: ", "Spent: ") + money(r.debit, t), color = Color(0xFFE53935), fontSize = 13.sp)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(t.divider)) {
                        Box(Modifier.fillMaxWidth((r.debit.toFloat() / maxVal).coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(Color(0xFFE53935)))
                    }
                    Text(T("واریز: ", "Received: ") + money(r.credit, t), color = Color(0xFF2E7D32), fontSize = 13.sp)
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(t.divider)) {
                        Box(Modifier.fillMaxWidth((r.credit.toFloat() / maxVal).coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                    }
                    Text(Fmt.digits(T("${r.count} تراکنش", "${r.count} transactions"), t.jalali), color = t.subText, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- bulk send ----------

@Composable
fun BulkPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val sims by Sim.sims
    var numbers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf(-1) }
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    val list = numbers.split(',', '،', '\n', ' ').map { it.trim() }.filter { n -> n.count { it.isDigit() } >= 5 }.distinct()

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, T("ارسال گروهی", "Bulk send"), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(T("شماره‌ها را با ویرگول یا خط جدید جدا کنید.", "Separate numbers with commas or new lines."), color = t.subText, fontSize = 13.sp)
            GlassField(t, numbers, { numbers = it }, T("شماره‌ها", "Numbers"), Modifier.fillMaxWidth(), singleLine = false)
            GlassField(t, body, { body = it }, T("متن پیام", "Message"), Modifier.fillMaxWidth(), singleLine = false)
            if (sims.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t, T("سیم پیش‌فرض", "Default SIM"), sub < 0) { sub = -1 }
                    for (s in sims) Chip(t, "SIM ${s.slot + 1}", sub == s.subId) { sub = s.subId }
                }
            }
            Text(Fmt.digits(T("${list.size} شماره معتبر", "${list.size} valid number(s)"), t.jalali), color = t.text, fontSize = 13.sp)
            if (progress.isNotEmpty()) Text(progress, color = t.accent, fontWeight = FontWeight.Bold)
            ActionBtn(t, T("ارسال", "Send")) {
                if (list.isEmpty() || body.isBlank()) toast(ctx, T("شماره و متن را وارد کنید", "Enter numbers and a message"))
                else confirm = true
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(T("ارسال گروهی", "Bulk send")) },
            text = {
                Text(
                    Fmt.digits(
                        T("پیام برای ${list.size} شماره ارسال می‌شود." + (if (list.size > 30) " تعداد زیاد ممکن است از طرف اپراتور محدود شود." else ""),
                            "The message will be sent to ${list.size} number(s)." + (if (list.size > 30) " Large batches may be limited by your carrier." else "")),
                        t.jalali
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val nums = list
                    val msg = body.trim()
                    val s = sub
                    Store.scope.launch {
                        var done = 0
                        for (n in nums) {
                            Sms.send(app, n, msg, s)
                            done++
                            progress = Fmt.digits(T("ارسال $done از ${nums.size}", "Sent $done of ${nums.size}"), t.jalali)
                            delay(1500)
                        }
                        progress = Fmt.digits(T("پایان: ${nums.size} پیام", "Done: ${nums.size} messages"), t.jalali)
                        Store.refresh(app)
                    }
                }) { Text(T("ارسال", "Send")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}

// ---------- private box ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrivatePage(t: GlassTheme, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val convs by Store.convs
    val priv by Flags.privateSet
    val sims by Sim.sims
    val shown = convs.filter { it.key in priv }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("پوشه خصوصی", "Private box"), onBack = onBack)
        Text(
            T("برای خارج کردن از پوشه، روی مکالمه نگه دارید.", "Long-press a conversation to remove it from the private box."),
            color = t.subText, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 8.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(start = if (t.flat) 0.dp else 16.dp, end = if (t.flat) 0.dp else 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (t.flat) 0.dp else 10.dp)
        ) {
            if (shown.isEmpty()) {
                item { Text(T("پوشه خالی است", "Empty"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            items(shown, key = { it.key }) { c ->
                ConvRow(
                    t, c, pinned = false, selected = false, draft = null,
                    sim = if (sims.size >= 2) Sim.slotLabel(c.subId) else null,
                    onClick = { onOpen(c.address) },
                    onLong = {
                        Flags.setPrivate(listOf(c.key), false)
                        toast(ctx, T("از پوشه خصوصی خارج شد", "Removed from private box"))
                    }
                )
            }
        }
    }
}

// ---------- templates ----------

@Composable
fun TemplatesPage(t: GlassTheme, onBack: () -> Unit) {
    val list by Cfg.templates
    var newText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, T("پیام‌های آماده", "Templates"), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { s ->
                Row(Modifier.fillMaxWidth().glass(t, 14.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s, color = t.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { Cfg.setTemplates(list - s) }, contentAlignment = Alignment.Center) {
                        Text("✕", color = t.text)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(t, newText, { newText = it }, T("قالب جدید…", "New template…"), Modifier.fillMaxWidth())
                    ActionBtn(t, T("افزودن", "Add")) {
                        if (newText.isNotBlank()) {
                            Cfg.setTemplates(list + newText.trim())
                            newText = ""
                        }
                    }
                }
            }
        }
    }
}

// ---------- category rules ----------

@Composable
fun RulesPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var gen by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { Store.refresh(ctx) } }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, T("کلمات دسته‌بندی", "Category keywords"), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                T("کلمه‌ها را با ویرگول جدا کنید. اگر پیامی یکی از آنها را داشته باشد در همان دسته قرار می‌گیرد. ترتیب بررسی: مخاطبین، کد تأیید، بانکی، خدمات دولتی، تبلیغاتی، ناشناس.",
                    "Separate keywords with commas. A message with any keyword goes to that category. Order: contacts, codes, bank, services, promo, unknown."),
                color = t.subText, fontSize = 13.sp
            )
            for (c in Rules.editable) {
                key(gen, c) {
                    var txt by remember { mutableStateOf(Rules.text(c)) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.emoji + " " + c.label, color = t.text, fontWeight = FontWeight.Bold)
                        GlassField(t, txt, { txt = it; Rules.set(c, it) }, T("کلمه‌ها…", "Keywords…"),
                            Modifier.fillMaxWidth(), singleLine = false)
                    }
                }
            }
            ActionBtn(t, T("بازگشت به کلمات پیش‌فرض", "Reset to defaults")) { Rules.reset(); gen++ }
        }
    }
}
