package com.passenger.sms

import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
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
fun MsgRow(t: GlassTheme, name: String, snippet: String, date: String, onClick: () -> Unit, trailing: Int? = null, onTrailing: (() -> Unit)? = null) {
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
                Ic(trailing, t.text, 20.dp)
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
                MsgRow(t, nm, m.body, Fmt.time(m.date, t, withDate = true), { onOpen(m.address) }, R.drawable.ic_star) {
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
                val rp = when (it.repeat) {
                    "daily" -> T(" · هر روز", " · daily")
                    "weekly" -> T(" · هر هفته", " · weekly")
                    "monthly" -> T(" · هر ماه", " · monthly")
                    "yearly" -> T(" · هر سال", " · yearly")
                    else -> ""
                }
                val kind = (if (it.type == "send") T("ارسال", "Send") else T("یادآوری", "Reminder")) + rp
                MsgRow(
                    t, kind + " · " + it.address, it.body, Fmt.time(it.at, t, withDate = true), {},
                    R.drawable.ic_close
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
                                toast(ctx, T("برای بازیابی باید برنامه پیش‌فرض باشد", "Set PSMS as default SMS app first"))
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
    val scope = rememberCoroutineScope()
    val budget by Cfg.budget
    val proActive by Pro.active
    var rows by remember { mutableStateOf<List<MonthSum>?>(null) }
    var accounts by remember { mutableStateOf(listOf<AccountSum>()) }
    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { Bank.load(ctx, t.jalali) }
        accounts = withContext(Dispatchers.IO) { Bank.byAccount(ctx) }
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    try { Csv.exportBank(ctx, uri, Bank.txList(ctx)) } catch (e: Exception) { -1 }
                }
                toast(ctx, if (n >= 0) T("$n تراکنش ذخیره شد", "$n transactions exported") else T("خروجی ناموفق بود", "Export failed"))
            }
        }
    }
    val list = rows
    val maxVal = list?.maxOfOrNull { maxOf(it.credit, it.debit) } ?: 1L
    val ymdNow = Fmt.ymd(System.currentTimeMillis(), true)
    val thisMonth = list?.firstOrNull { it.y == ymdNow[0] && it.m == ymdNow[1] }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("خلاصه پیام‌های بانکی", "Bank summary"), onBack = onBack) {
            BarBtn(if (t.flat) t.barText else t.text, R.drawable.ic_download) {
                if (Pro.gate(ctx, "csv")) csvLauncher.launch("bank.csv")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    T("مبلغ‌ها از متن پیامک‌های بانکی ۱۲ ماه اخیر تشخیص داده می‌شود و ممکن است دقیق نباشد.",
                        "Amounts are detected from bank SMS text over the last 12 months and may not be exact."),
                    color = t.subText, fontSize = 12.sp
                )
            }
            if (budget > 0 && Pro.allowed("budget")) {
                item {
                    val spent = thisMonth?.debit ?: 0L
                    val frac = (spent.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                    Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(T("بودجه این ماه", "This month's budget"), color = t.text, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(t.divider)) {
                            Box(
                                Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f)).height(10.dp).clip(CircleShape)
                                    .background(if (spent >= budget) Color(0xFFE53935) else t.accent)
                            )
                        }
                        Text(money(spent, t) + " / " + money(budget, t), color = t.subText, fontSize = 12.sp)
                    }
                }
            } else if (budget > 0 && !proActive) {
                item { Text(T("بودجه ماهانه در نسخه Pro فعال می‌شود.", "Monthly budget is part of PSMS Pro."), color = t.subText, fontSize = 12.sp) }
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
            if (accounts.isNotEmpty()) {
                item { Text(T("بر اساس حساب / کارت (۴ رقم آخر)", "By account / card (last 4 digits)"), color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(accounts) { a ->
                    Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("••••  " + Fmt.digits(a.tail, t.jalali), color = t.text, fontWeight = FontWeight.Bold)
                        Text(T("برداشت: ", "Spent: ") + money(a.debit, t) + "   " + T("واریز: ", "Received: ") + money(a.credit, t), color = t.subText, fontSize = 12.sp)
                        Text(Fmt.digits(T("${a.count} تراکنش", "${a.count} transactions"), t.jalali), color = t.subText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ---------- bulk send ----------

fun parseRecipients(src: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val seen = HashSet<String>()
    for (line in src.split('\n')) {
        val tokens = line.split(',', '،', ';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) continue
        val nums = tokens.filter { tk ->
            val n = Fmt.norm(tk)
            n.count { it.isDigit() } >= 5 && n.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        }
        val names = tokens.filter { it !in nums }
        if (nums.isEmpty()) continue
        for (n in nums) {
            val clean = Fmt.norm(n).replace(" ", "").replace("-", "")
            val dk = clean.filter { it.isDigit() }.takeLast(9)
            if (seen.add(dk)) out.add(Pair(clean, if (nums.size == 1) names.joinToString(" ") else ""))
        }
    }
    return out
}

@Composable
fun BulkPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val sims by Sim.sims
    val groups by Flags.groups
    var numbers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf(-1) }
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var askGroup by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                numbers = txt.replace("\r", "").removePrefix("\uFEFF")
            } catch (e: Exception) {
                toast(ctx, T("خواندن فایل ناموفق بود", "Could not read the file"))
            }
        }
    }

    val list = parseRecipients(numbers)

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, T("ارسال گروهی", "Bulk send"), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                T("هر خط یک گیرنده: «شماره» یا «شماره، نام». در متن پیام می‌توانید {نام} بنویسید.",
                    "One recipient per line: \"number\" or \"number, name\". Use {name} in the message."),
                color = t.subText, fontSize = 13.sp
            )
            GlassField(t, numbers, { numbers = it }, T("گیرنده‌ها", "Recipients"), Modifier.fillMaxWidth(), singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Chip(t, T("وارد کردن از فایل CSV", "Import CSV"), false, R.drawable.ic_download) {
                    if (Pro.gate(ctx, "bulk_csv")) csvPicker.launch(arrayOf("text/*", "application/vnd.ms-excel", "*/*"))
                }
                Chip(t, T("ذخیره به‌عنوان گروه", "Save as group"), false, R.drawable.ic_folder) {
                    if (Pro.gate(ctx, "bulk_groups")) {
                        if (list.isEmpty()) toast(ctx, T("اول گیرنده وارد کنید", "Add recipients first")) else askGroup = true
                    }
                }
            }
            if (groups.isNotEmpty()) {
                Text(T("گروه‌های ذخیره‌شده", "Saved groups"), color = t.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for ((gn, gv) in groups) {
                        Chip(t, gn, false, R.drawable.ic_person) {
                            if (Pro.gate(ctx, "bulk_groups")) numbers = (if (numbers.isBlank()) "" else numbers.trimEnd() + "\n") + gv
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { Flags.setGroup(gn, null) }, contentAlignment = Alignment.Center) {
                            Ic(R.drawable.ic_close, t.subText, 14.dp)
                        }
                    }
                }
            }
            GlassField(t, body, { body = it }, T("متن پیام", "Message"), Modifier.fillMaxWidth(), singleLine = false)
            if (sims.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t, T("سیم پیش‌فرض", "Default SIM"), sub < 0) { sub = -1 }
                    for (s in sims) Chip(t, "SIM ${s.slot + 1}", sub == s.subId) { sub = s.subId }
                }
            }
            Text(Fmt.digits(T("${list.size} گیرنده معتبر", "${list.size} valid recipient(s)"), t.jalali), color = t.text, fontSize = 13.sp)
            if (progress.isNotEmpty()) Text(progress, color = t.accent, fontWeight = FontWeight.Bold)
            ActionBtn(t, T("ارسال", "Send"), R.drawable.ic_send) {
                if (list.isEmpty() || body.isBlank()) toast(ctx, T("گیرنده و متن را وارد کنید", "Enter recipients and a message"))
                else confirm = true
            }
        }
    }

    if (askGroup) {
        AlertDialog(
            onDismissRequest = { askGroup = false },
            title = { Text(T("نام گروه", "Group name")) },
            text = { GlassField(t, groupName, { groupName = it }, T("مثلاً مشتری‌ها", "e.g. Customers"), Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val n = groupName.trim()
                    if (n.isNotEmpty()) {
                        Flags.setGroup(n, list.joinToString("\n") { if (it.second.isNotEmpty()) it.first + ", " + it.second else it.first })
                        groupName = ""
                        askGroup = false
                    }
                }) { Text(T("ذخیره", "Save")) }
            },
            dismissButton = { TextButton(onClick = { askGroup = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(T("ارسال گروهی", "Bulk send")) },
            text = {
                Text(
                    Fmt.digits(
                        T("پیام برای ${list.size} نفر ارسال می‌شود." + (if (list.size > 30) " تعداد زیاد ممکن است از طرف اپراتور محدود شود." else ""),
                            "The message will be sent to ${list.size} recipient(s)." + (if (list.size > 30) " Large batches may be limited by your carrier." else "")),
                        t.jalali
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val recips = list
                    val tpl = body.trim()
                    val s = sub
                    Store.scope.launch {
                        var done = 0
                        for ((num, nm) in recips) {
                            val msg = tpl.replace("{name}", nm).replace("{نام}", nm)
                            Sms.send(app, num, msg, s)
                            done++
                            progress = Fmt.digits(T("ارسال $done از ${recips.size}", "Sent $done of ${recips.size}"), t.jalali)
                            delay(1500)
                        }
                        progress = Fmt.digits(T("پایان: ${recips.size} پیام", "Done: ${recips.size} messages"), t.jalali)
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
                    t, c, pinned = false, selected = false, vip = false, draft = null,
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
                        Ic(R.drawable.ic_close, t.text, 18.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(c.icon, t.accent, 18.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(c.label, color = t.text, fontWeight = FontWeight.Bold)
                        }
                        GlassField(t, txt, { txt = it; Rules.set(c, it) }, T("کلمه‌ها…", "Keywords…"),
                            Modifier.fillMaxWidth(), singleLine = false)
                    }
                }
            }
            ActionBtn(t, T("بازگشت به کلمات پیش‌فرض", "Reset to defaults")) { Rules.reset(); gen++ }
        }
    }
}


// ---------- statistics ----------

@Composable
fun StatsPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sims by Sim.sims
    var data by remember { mutableStateOf<StatsData?>(null) }
    LaunchedEffect(Unit) { data = withContext(Dispatchers.IO) { Stats.compute(ctx) } }
    val d = data
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("آمار پیام‌ها", "Statistics"), onBack = onBack)
        if (d == null) {
            Text(T("در حال محاسبه…", "Calculating…"), color = t.subText, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Fmt.digits("${d.total}", t.jalali), color = t.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(T("کل پیام‌ها", "Total messages"), color = t.subText, fontSize = 12.sp)
                        Text(Fmt.digits(T("دریافتی ${d.received}  ·  ارسالی ${d.sent}", "Received ${d.received}  ·  Sent ${d.sent}"), t.jalali), color = t.text, fontSize = 13.sp)
                    }
                }
                if (d.months.isNotEmpty()) {
                    item {
                        val mx = (d.months.maxOfOrNull { it.third } ?: 1).coerceAtLeast(1)
                        Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(T("پیام‌ها در ماه‌های اخیر", "Messages per month"), color = t.text, fontWeight = FontWeight.Bold)
                            for ((y, m, n) in d.months) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Fmt.monthName(m, true) + " " + Fmt.digits("$y", true), color = t.subText, fontSize = 11.sp, modifier = Modifier.width(96.dp))
                                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(t.divider)) {
                                        Box(Modifier.fillMaxWidth((n.toFloat() / mx).coerceIn(0.03f, 1f)).height(8.dp).clip(CircleShape).background(t.accent))
                                    }
                                    Text(Fmt.digits("$n", t.jalali), color = t.text, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                if (d.top.isNotEmpty()) {
                    item {
                        val mx = (d.top.maxOfOrNull { it.third } ?: 1).coerceAtLeast(1)
                        Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(T("پرتکرارترین فرستنده‌ها", "Top senders"), color = t.text, fontWeight = FontWeight.Bold)
                            for ((addr, nm, n) in d.top) {
                                Column {
                                    Row {
                                        Text(nm ?: addr, color = t.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(Fmt.digits("$n", t.jalali), color = t.subText, fontSize = 12.sp)
                                    }
                                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(t.divider)) {
                                        Box(Modifier.fillMaxWidth((n.toFloat() / mx).coerceIn(0.03f, 1f)).height(6.dp).clip(CircleShape).background(t.accent))
                                    }
                                }
                            }
                        }
                    }
                }
                if (d.bySim.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(T("سهم هر سیم‌کارت", "Per SIM"), color = t.text, fontWeight = FontWeight.Bold)
                            for ((sid, n) in d.bySim) {
                                val slot = sims.firstOrNull { it.subId == sid }?.slot
                                Text((if (slot != null) "SIM ${slot + 1}" else "SIM ?") + "  —  " + Fmt.digits("$n", t.jalali), color = t.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (d.byCat.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(T("پیام‌های دریافتی بر اساس دسته", "Received by category"), color = t.text, fontWeight = FontWeight.Bold)
                            for ((c, n) in d.byCat.entries.sortedByDescending { it.value }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Ic(c.icon, t.accent, 18.dp)
                                    Spacer(Modifier.size(8.dp))
                                    Text(c.label, color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(Fmt.digits("$n", t.jalali), color = t.subText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- cleanup ----------

@Composable
fun CleanupPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<CleanupGroup>?>(null) }
    var askFor by remember { mutableStateOf<CleanupGroup?>(null) }
    LaunchedEffect(Unit) { groups = withContext(Dispatchers.IO) { Cleanup.scan(ctx) } }
    fun title(id: String) = when (id) {
        "promo" -> T("تبلیغات قدیمی‌تر از ۳۰ روز", "Promos older than 30 days")
        "otp" -> T("کدهای تأیید قدیمی‌تر از ۷ روز", "Codes older than 7 days")
        else -> T("پیام‌های ناشناس قدیمی‌تر از یک سال", "Unknown senders older than a year")
    }
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("پاک‌سازی", "Cleanup"), onBack = onBack)
        val g = groups
        if (g == null) {
            Text(T("در حال بررسی…", "Scanning…"), color = t.subText, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(g) { grp ->
                    Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title(grp.id), color = t.text, fontWeight = FontWeight.Bold)
                        Text(
                            Fmt.digits(T("${grp.ids.size} پیام · حدود ${grp.bytes / 1024 + 1} کیلوبایت", "${grp.ids.size} messages · about ${grp.bytes / 1024 + 1} KB"), t.jalali),
                            color = t.subText, fontSize = 12.sp
                        )
                        if (grp.ids.isNotEmpty()) {
                            Chip(t, T("حذف", "Delete"), false, R.drawable.ic_trash) { askFor = grp }
                        }
                    }
                }
            }
        }
    }
    val ask = askFor
    if (ask != null) {
        AlertDialog(
            onDismissRequest = { askFor = null },
            title = { Text(T("حذف پیام‌ها", "Delete messages")) },
            text = { Text(Fmt.digits(T("${ask.ids.size} پیام برای همیشه حذف می‌شود.", "${ask.ids.size} messages will be deleted permanently."), t.jalali)) },
            confirmButton = {
                TextButton(onClick = {
                    askFor = null
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, T("برای حذف، PSMS باید برنامه پیش‌فرض پیامک باشد", "PSMS must be the default SMS app to delete"))
                    } else {
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteMessages(ctx, ask.ids, false) }
                            groups = withContext(Dispatchers.IO) { Cleanup.scan(ctx) }
                            Store.refresh(ctx)
                        }
                    }
                }) { Text(T("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { askFor = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}

// ---------- PSMS Pro ----------

fun proFeatures(): List<Pair<String, String>> = listOf(
    "stats" to T("آمار پیام‌ها و نمودارها", "Message statistics & charts"),
    "balances" to T("کارت موجودی حساب‌ها روی صفحه اصلی", "Account balance cards on the home screen"),
    "budget" to T("بودجه ماهانه و هشدار هزینه", "Monthly budget & spending alert"),
    "private" to T("پوشه خصوصی با قفل", "Locked private box"),
    "folders" to T("پوشه‌های دلخواه", "Custom folders"),
    "repeat" to T("پیام تکرارشونده و تبریک تولد", "Recurring messages & birthdays"),
    "bulk_groups" to T("گروه‌های ارسال گروهی", "Bulk-send groups"),
    "bulk_csv" to T("ارسال گروهی از فایل CSV با نام شخصی‌شده", "Bulk send from CSV with personalised names"),
    "autobackup" to T("پشتیبان‌گیری خودکار روزانه", "Daily automatic backup"),
    "pdf" to T("خروجی PDF مکالمه", "Conversation PDF export"),
    "csv" to T("خروجی CSV / اکسل", "CSV / Excel export"),
    "cardimage" to T("اشتراک‌گذاری پیام به‌صورت تصویر", "Share messages as images"),
    "cleanup" to T("ابزار پاک‌سازی هوشمند", "Smart cleanup tool"),
    "notif_cat" to T("صدا و رنگ اعلان برای هر دسته", "Notification sound & color per category")
)

@Composable
fun ProPage(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val active by Pro.active
    var code by remember { mutableStateOf("") }
    val devId = remember { Pro.deviceId(ctx) }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, "PSMS Pro", onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().glass(t, 18.dp).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_psms), contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                )
                Text("PSMS Pro", color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (active) T("فعال است. از امکانات حرفه‌ای لذت ببرید.", "Active. Enjoy the pro features.")
                    else T("امکانات حرفه‌ای را باز کنید", "Unlock the professional features"),
                    color = if (active) t.accent else t.subText, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }

            Section(t, T("امکانات Pro", "Pro features"), R.drawable.ic_star) {
                for ((id, label) in proFeatures()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(if (active || id !in Pro.LOCKED) R.drawable.ic_check_all else R.drawable.ic_lock, if (active) t.accent else t.subText, 18.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(label, color = t.text, fontSize = 14.sp)
                    }
                }
            }

            if (!active) {
                Section(t, T("خرید و فعال‌سازی", "Get Pro"), R.drawable.ic_key) {
                    Text(
                        T("۱) کد دستگاه را برای من بفرستید.  ۲) بعد از پرداخت، کد فعال‌سازی مخصوص همین دستگاه را دریافت می‌کنید.  ۳) کد را اینجا وارد کنید.",
                            "1) Send me your device ID.  2) After payment you receive an activation code for this device only.  3) Paste it below."),
                        color = t.subText, fontSize = 13.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().glass(t, 14.dp).clickable {
                            val cm = ctx.getSystemService(ClipboardManager::class.java)
                            cm.setPrimaryClip(ClipData.newPlainText("device", devId))
                            toast(ctx, T("کد دستگاه کپی شد", "Device ID copied"))
                        }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(T("کد دستگاه", "Device ID"), color = t.subText, fontSize = 11.sp)
                            Text(devId, color = t.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Ic(R.drawable.ic_copy, t.accent, 20.dp)
                    }
                    ActionBtn(t, T("ارسال کد دستگاه با ایمیل", "Email my device ID"), R.drawable.ic_mail) {
                        try {
                            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                            i.putExtra(Intent.EXTRA_SUBJECT, "PSMS Pro - $devId")
                            i.putExtra(Intent.EXTRA_TEXT, "Device ID: $devId")
                            ctx.startActivity(i)
                        } catch (e: Exception) { }
                    }
                    GlassField(t, code, { code = it }, T("کد فعال‌سازی", "Activation code"), Modifier.fillMaxWidth(), singleLine = false)
                    ActionBtn(t, T("فعال‌سازی", "Activate"), R.drawable.ic_check_all) {
                        if (Pro.activate(ctx, code)) {
                            toast(ctx, T("PSMS Pro فعال شد 🎉", "PSMS Pro activated"))
                            code = ""
                        } else {
                            toast(ctx, T("کد معتبر نیست", "Invalid code"))
                        }
                    }
                }
            } else {
                Section(t, T("وضعیت", "Status"), R.drawable.ic_shield) {
                    Text(T("کد دستگاه: ", "Device ID: ") + devId, color = t.subText, fontSize = 12.sp)
                }
            }
        }
    }
}


// ---------- new message: contact list ----------

@Composable
fun ContactsPage(t: GlassTheme, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { Sms.allContacts(ctx) } }
    val list = all
    val qd = Fmt.norm(q).filter { it.isDigit() }
    val shown = remember(list, q) {
        (list ?: emptyList()).filter {
            q.isBlank() || it.first.contains(q, true) ||
                (qd.length >= 2 && Fmt.norm(it.second).filter { c -> c.isDigit() }.contains(qd))
        }
    }
    val groups = remember(shown) { shown.groupBy { it.first.trim().take(1).uppercase() } }
    val looksLikeNumber = Fmt.norm(q).let { n -> n.length >= 3 && n.all { it.isDigit() || it == '+' || it == ' ' } }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        AppBar(t, T("پیام جدید", "New message"), onBack = onBack)
        GlassField(
            t, q, { q = it }, T("جستجوی نام یا شماره…", "Search name or number…"),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (looksLikeNumber) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).glass(t, 14.dp)
                            .clickable { onOpen(Fmt.norm(q).replace(" ", "")) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ic(R.drawable.ic_send, t.accent, 20.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(T("ارسال پیام به ", "Message ") + Fmt.norm(q), color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            if (list == null) {
                item { Text(T("در حال بارگذاری مخاطبین…", "Loading contacts…"), color = t.subText, modifier = Modifier.padding(24.dp)) }
            } else if (list.isEmpty()) {
                item {
                    Text(
                        if (Sms.granted(ctx, android.Manifest.permission.READ_CONTACTS))
                            T("مخاطبی پیدا نشد", "No contacts found")
                        else T("برای دیدن مخاطبین، مجوز «مخاطبین» را در تنظیمات گوشی بدهید.", "Allow the Contacts permission in phone settings to see your contacts."),
                        color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center
                    )
                }
            } else if (shown.isEmpty() && !looksLikeNumber) {
                item { Text(T("نتیجه‌ای نیست", "No results"), color = t.subText, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
            }
            for ((letter, people) in groups) {
                item(key = "h_$letter") {
                    Text(
                        letter, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)
                    )
                }
                items(people, key = { "c_" + it.second + it.first }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p.second) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(t.accent.copy(alpha = if (t.flat) 1f else 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initialsOf(p.first), color = if (t.flat) t.onAccent else t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(p.first, color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.second, color = t.subText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


// ---------- privacy policy ----------

fun privacySections(): List<Pair<String, String>> = listOf(
    T("خلاصه", "Summary") to T(
        "PSMS هیچ اطلاعاتی را جمع‌آوری یا به اینترنت ارسال نمی‌کند. برنامه اصلاً مجوز اینترنت ندارد، تبلیغات ندارد و ابزار تحلیل رفتار کاربران در آن نیست. پیام‌ها و تنظیمات شما فقط روی گوشی خودتان می‌مانند.",
        "PSMS does not collect or send any information over the internet. The app has no internet permission, no ads and no analytics. Your messages and settings stay on your phone."
    ),
    T("مجوزهایی که استفاده می‌شود و دلیلش", "Permissions and why they are used") to T(
        "پیامک (خواندن، دریافت، ارسال): کار اصلی برنامه.\nمخاطبین: نمایش نام و عکس مخاطب و انتخاب گیرنده.\nوضعیت تلفن: تشخیص سیم‌کارت‌ها برای دو سیم‌کارت.\nاعلان‌ها: اعلان پیام‌های جدید، پاسخ سریع و هشدارها.\nراه‌اندازی گوشی: ثبت دوباره‌ی پیام‌های زمان‌بندی‌شده بعد از ری‌استارت.\nلرزش: لرزش اعلان‌ها.",
        "SMS (read, receive, send): the core of the app.\nContacts: show contact names and photos and pick recipients.\nPhone state: detect SIM cards for dual-SIM support.\nNotifications: new-message alerts, quick reply and warnings.\nBoot completed: re-register scheduled messages after a restart.\nVibration: notification vibration."
    ),
    T("اطلاعاتی که روی گوشی ذخیره می‌شود", "What is stored on your phone") to T(
        "تنظیمات ظاهر، پوشه‌ها، پین‌ها، پیش‌نویس‌ها، یادآوری‌ها و رمز عددی (به‌صورت رمزنگاری‌شده‌ی یک‌طرفه) فقط در حافظه‌ی خود برنامه نگه‌داری می‌شود. پیام‌های حذف‌شده ۳۰ روز در سطل زباله‌ی برنامه می‌مانند و بعد پاک می‌شوند.",
        "Appearance settings, folders, pins, drafts, reminders and your PIN (stored only as a one-way hash) are kept in the app's private storage. Deleted messages stay in the app's trash for 30 days and are then erased."
    ),
    T("پشتیبان‌گیری و خروجی", "Backups and exports") to T(
        "پشتیبان‌گیری، خروجی PDF و CSV فقط وقتی انجام می‌شود که خودتان درخواست کنید و فایل در جایی ذخیره می‌شود که خودتان انتخاب می‌کنید. برنامه چیزی را به سرور نمی‌فرستد.",
        "Backups and PDF/CSV exports happen only when you ask for them and are saved where you choose. Nothing is uploaded to any server."
    ),
    T("امنیت", "Security") to T(
        "قفل برنامه با اثر انگشت، قفل گوشی یا رمز عددی، پوشه‌ی خصوصی، پنهان کردن متن پیام در قفل صفحه و ممانعت از اسکرین‌شات از امکانات اختیاری است.",
        "Optional protections include app lock (fingerprint, phone lock or PIN), a private box, hiding message text on the lock screen and blocking screenshots."
    ),
    T("اشتراک با دیگران", "Sharing with others") to T(
        "برنامه اطلاعات شما را با هیچ شخص ثالثی به اشتراک نمی‌گذارد. فقط وقتی خودتان گزینه‌ی «اشتراک‌گذاری» را بزنید، متن یا تصویر انتخابی به برنامه‌ی مورد نظر شما داده می‌شود. تایپ صوتی و خواندن با صدا توسط سرویس‌های سیستمی گوشی انجام می‌شود و تابع سیاست همان سرویس‌هاست.",
        "The app shares nothing with third parties. Only when you tap Share, the chosen text or image is handed to the app you pick. Voice typing and read-aloud are handled by your phone's system services and follow their own policies."
    ),
    T("حذف اطلاعات", "Deleting your data") to T(
        "با حذف برنامه، همه‌ی اطلاعات داخلی آن پاک می‌شود. پیامک‌ها در خود سیستم اندروید می‌مانند و به برنامه‌ی پیامک دیگری منتقل می‌شوند.",
        "Uninstalling the app removes all of its internal data. Your SMS messages remain in Android itself and are available to any other SMS app."
    ),
    T("ارتباط", "Contact") to T(
        "برای هر سؤال درباره‌ی حریم خصوصی: ", "For any privacy question: "
    ) + CONTACT_EMAIL
)

@Composable
fun PrivacyPage(t: GlassTheme, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("سیاست حریم خصوصی", "Privacy policy"), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for ((title, body) in privacySections()) {
                Column(Modifier.fillMaxWidth().glass(t, 14.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(body, color = t.subText, fontSize = 13.sp)
                }
            }
            Text(T("آخرین به‌روزرسانی: آبان ۱۴۰۵", "Last updated: November 2026"), color = t.subText, fontSize = 11.sp)
        }
    }
}
