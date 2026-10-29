package com.passenger.sms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- small building blocks ----------

@Composable
fun GlassField(
    t: GlassTheme, value: String, onValue: (String) -> Unit,
    hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true
) {
    Box(modifier.glass(t, 20.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = t.subText, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = singleLine,
            maxLines = if (singleLine) 1 else 6,
            textStyle = TextStyle(color = t.text, fontSize = 15.sp, fontFamily = LocalAppFont.current),
            cursorBrush = SolidColor(t.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun IconBtn(t: GlassTheme, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glass(t, 20.dp).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 17.sp, color = t.text) }
}

@Composable
fun Section(t: GlassTheme, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(t).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = t.text, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun LabeledSlider(
    t: GlassTheme, label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, color = t.subText, fontSize = 13.sp)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = t.accent, activeTrackColor = t.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SwitchRow(t: GlassTheme, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = t.accent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun ActionBtn(t: GlassTheme, label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().glass(t, 18.dp).clickable { onClick() }.padding(14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

// ---------- lock ----------

@Composable
fun LockScreen(t: GlassTheme, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.size(12.dp))
        Text("Passenger", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("برنامه قفل است", color = t.subText, fontSize = 14.sp)
        Spacer(Modifier.size(24.dp))
        Box(
            Modifier.glass(t, 22.dp).clickable { onUnlock() }.padding(horizontal = 32.dp, vertical = 14.dp)
        ) { Text("باز کردن", color = t.accent, fontWeight = FontWeight.Bold) }
    }
}

// ---------- home ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    t: GlassTheme, isDefault: Boolean, onDefault: () -> Unit,
    onOpen: (String) -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val convs by Store.convs
    val pinned by Flags.pinned
    val archived by Flags.archived
    val blocked by Flags.blocked
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Cat.ALL) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    var askMove by remember { mutableStateOf(false) }

    fun inTab(c: Conv, tb: Cat): Boolean = when (tb) {
        Cat.ALL -> c.key !in archived && c.key !in blocked
        Cat.ARCHIVE -> c.key in archived
        Cat.SPAM -> c.key in blocked
        else -> c.cat == tb && c.key !in archived && c.key !in blocked
    }

    val shown = remember(convs, query, tab, pinned, archived, blocked) {
        convs.filter {
            inTab(it, tab) && (query.isBlank() ||
                (it.name ?: "").contains(query, true) ||
                it.address.contains(query) ||
                it.last.body.contains(query, true))
        }.sortedByDescending { it.key in pinned }
    }
    val looksLikeNumber = query.length >= 3 && query.all { it.isDigit() || it == '+' }

    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selected.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Passenger", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconBtn(t, "⚙") { onSettings() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp).glass(t, 24.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconBtn(t, "✕") { selected = emptySet() }
                Text(Fmt.digits("${selected.size} انتخاب", t.jalali), color = t.text,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconBtn(t, "📌") {
                    val allPinned = selected.all { it in pinned }
                    Flags.setPinned(selected, !allPinned)
                    selected = emptySet()
                }
                IconBtn(t, "🗄") {
                    Flags.setArchived(selected, tab != Cat.ARCHIVE)
                    selected = emptySet()
                }
                IconBtn(t, "🚫") {
                    Flags.setBlocked(selected, tab != Cat.SPAM)
                    selected = emptySet()
                }
                IconBtn(t, "🏷") { askMove = true }
                IconBtn(t, "🗑") { askDelete = true }
            }
        }

        if (!isDefault) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .glass(t, 18.dp).clickable { onDefault() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("برای دریافت، ذخیره و حذف پیامک‌ها، Passenger را برنامه پیش‌فرض کنید",
                    color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("تنظیم", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        GlassField(t, query, { query = it }, "جستجو در پیام‌ها و مخاطبین…",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Cat.values().toList()) { c ->
                val n = convs.count { inTab(it, c) }
                val sel = c == tab
                Row(
                    Modifier.glass(t, 18.dp)
                        .background(if (sel) t.accent.copy(alpha = 0.55f) else Color.Transparent)
                        .clickable { tab = c }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.emoji, fontSize = 14.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(Fmt.digits("${c.label} $n", t.jalali), color = t.text, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (looksLikeNumber && shown.isEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().glass(t).clickable { onOpen(query) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✎  ارسال پیام به $query", color = t.text, fontSize = 15.sp)
                    }
                }
            }
            if (shown.isEmpty() && !looksLikeNumber) {
                item {
                    Text("پیامی در این بخش نیست", color = t.subText, fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            }
            items(shown, key = { it.key }) { c ->
                ConvRow(
                    t, c, pinned = c.key in pinned, selected = c.key in selected,
                    onClick = {
                        if (selected.isNotEmpty()) {
                            selected = if (c.key in selected) selected - c.key else selected + c.key
                        } else {
                            onOpen(c.address)
                        }
                    },
                    onLong = {
                        selected = if (c.key in selected) selected - c.key else selected + c.key
                    }
                )
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("حذف مکالمه‌ها") },
            text = { Text(Fmt.digits("${selected.size} مکالمه با همه‌ی پیام‌هایش برای همیشه حذف می‌شود.", t.jalali)) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, "برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد")
                    } else {
                        val addrs = convs.filter { it.key in selected }.map { it.address }
                        selected = emptySet()
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteConvs(ctx, addrs) }
                            Store.refresh(ctx)
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("انصراف") } }
        )
    }

    if (askMove) {
        AlertDialog(
            onDismissRequest = { askMove = false },
            title = { Text("انتقال به دسته") },
            text = {
                Column {
                    val options = listOf<Cat?>(Cat.CONTACTS, Cat.BANK, Cat.OTP, Cat.PROMO, Cat.UNKNOWN, null)
                    for (c in options) {
                        Text(
                            if (c == null) "↺ تشخیص خودکار" else "${c.emoji} ${c.label}",
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                Flags.setOverride(selected, c)
                                selected = emptySet()
                                askMove = false
                                Store.refresh(ctx)
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askMove = false }) { Text("انصراف") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConvRow(
    t: GlassTheme, c: Conv, pinned: Boolean, selected: Boolean,
    onClick: () -> Unit, onLong: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().glass(t)
            .background(if (selected) t.accent.copy(alpha = 0.35f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(t.accent.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Text("✓", color = t.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            else if (c.cat == Cat.CONTACTS) Text((c.name ?: c.address).take(1), color = t.text,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            else Text(c.cat.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name ?: c.address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.last.body, color = t.subText, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) Text("📌", fontSize = 11.sp)
                Text(Fmt.time(c.last.date, t), color = t.subText, fontSize = 11.sp)
            }
            if (c.unread > 0) {
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier.clip(CircleShape).background(t.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Fmt.digits("${c.unread}", t.jalali), color = Color.Black, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------- thread ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(t: GlassTheme, address: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val convs by Store.convs
    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var text by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    var askDelMsgs by remember { mutableStateOf(false) }
    var askDelConv by remember { mutableStateOf(false) }
    val name = remember(address, convs) { convs.firstOrNull { it.address == address }?.name }

    LaunchedEffect(convs, address) {
        val list = withContext(Dispatchers.IO) { Sms.loadThread(ctx, address) }
        msgs = list
        if (list.any { !it.read && it.type == 1 }) {
            withContext(Dispatchers.IO) { Sms.markRead(ctx, address) }
        }
    }

    androidx.activity.compose.BackHandler(enabled = sel.isNotEmpty()) { sel = emptySet() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).glass(t, 24.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sel.isEmpty()) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Text("→", color = t.text, fontSize = 22.sp)
                }
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(name ?: address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (name != null) Text(address, color = t.subText, fontSize = 11.sp)
                }
                IconBtn(t, "🗑") { askDelConv = true }
            } else {
                IconBtn(t, "✕") { sel = emptySet() }
                Spacer(Modifier.size(8.dp))
                Text(Fmt.digits("${sel.size} پیام انتخاب شده", t.jalali), color = t.text,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconBtn(t, "🗑") { askDelMsgs = true }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(msgs, key = { it.id }) { m ->
                val sent = m.type != 1
                val isSel = m.id in sel
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                            .background(
                                if (sent) t.accent.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = t.glass + 0.06f)
                            )
                            .border(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) t.accent else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(18.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (sel.isNotEmpty()) {
                                        sel = if (isSel) sel - m.id else sel + m.id
                                    }
                                },
                                onLongClick = { sel = if (isSel) sel - m.id else sel + m.id }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(m.body, color = t.text, fontSize = 15.sp)
                        Text(Fmt.time(m.date, t, withDate = true), color = t.subText, fontSize = 10.sp)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassField(t, text, { text = it }, "پیام…", Modifier.weight(1f), singleLine = false)
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(t.accent)
                    .clickable {
                        val body = text.trim()
                        if (body.isEmpty()) return@clickable
                        if (!Sms.granted(ctx, Manifest.permission.SEND_SMS)) {
                            toast(ctx, "مجوز ارسال پیامک داده نشده"); return@clickable
                        }
                        try {
                            Sms.send(ctx, address, body)
                            text = ""
                            if (!Sms.isDefault(ctx)) toast(ctx, "ارسال شد (برای ذخیره، برنامه پیش‌فرض کنید)")
                            Store.refresh(ctx)
                        } catch (e: Exception) { toast(ctx, "ارسال ناموفق بود") }
                    },
                contentAlignment = Alignment.Center
            ) { Text("➤", color = Color.Black, fontSize = 20.sp) }
        }
    }

    if (askDelMsgs) {
        AlertDialog(
            onDismissRequest = { askDelMsgs = false },
            title = { Text("حذف پیام‌ها") },
            text = { Text(Fmt.digits("${sel.size} پیام برای همیشه حذف می‌شود.", t.jalali)) },
            confirmButton = {
                TextButton(onClick = {
                    askDelMsgs = false
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, "برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد")
                    } else {
                        val ids = sel.toList()
                        sel = emptySet()
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteMessages(ctx, ids) }
                            Store.refresh(ctx)
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { askDelMsgs = false }) { Text("انصراف") } }
        )
    }

    if (askDelConv) {
        AlertDialog(
            onDismissRequest = { askDelConv = false },
            title = { Text("حذف کل مکالمه") },
            text = { Text("همه‌ی پیام‌های این مکالمه برای همیشه حذف می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelConv = false
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, "برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد")
                    } else {
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteConvs(ctx, listOf(address)) }
                            Store.refresh(ctx)
                            onBack()
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { askDelConv = false }) { Text("انصراف") } }
        )
    }
}

// ---------- settings ----------

@Composable
fun SettingsScreen(
    raw: GlassTheme, t: GlassTheme, onChange: (GlassTheme) -> Unit, onBack: () -> Unit,
    isDefault: Boolean, onDefault: () -> Unit, onRules: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lockOn by Security.enabled
    val nBg = Palettes.backgrounds.size
    val nAc = Palettes.accents.size

    val exportL = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    try { Backup.export(ctx, uri) } catch (e: Exception) { -1 }
                }
                toast(ctx, if (n >= 0) "$n پیام پشتیبان‌گیری شد" else "پشتیبان‌گیری ناموفق بود")
            }
        }
    }
    val importL = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (!Sms.isDefault(ctx)) {
                    toast(ctx, "برای بازیابی، Passenger باید برنامه پیش‌فرض پیامک باشد")
                } else {
                    val r = withContext(Dispatchers.IO) {
                        try { Backup.import(ctx, uri) } catch (e: Exception) { null }
                    }
                    toast(ctx, if (r != null) "${r.first} پیام بازیابی شد، ${r.second} تکراری" else "بازیابی ناموفق بود")
                    Store.refresh(ctx)
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(t, "→") { onBack() }
            Spacer(Modifier.size(12.dp))
            Text("تنظیمات", color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Section(t, "پس‌زمینه") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.backgrounds.forEachIndexed { i, p ->
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(Brush.linearGradient(p.colors))
                            .border(if (i == raw.bgIndex) 3.dp else 1.dp,
                                if (i == raw.bgIndex) t.accent else Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { onChange(raw.copy(bgIndex = i)) }
                    )
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.linearGradient(raw.copy(bgIndex = nBg).bg))
                        .border(if (raw.customBg) 3.dp else 1.dp,
                            if (raw.customBg) t.accent else Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onChange(raw.copy(bgIndex = nBg)) },
                    contentAlignment = Alignment.Center
                ) { Text("✎", color = Color.White, fontSize = 18.sp) }
            }
            if (raw.customBg) {
                LabeledSlider(t, "رنگ اول", raw.hue1, 0f..360f) { onChange(raw.copy(hue1 = it)) }
                LabeledSlider(t, "رنگ دوم", raw.hue2, 0f..360f) { onChange(raw.copy(hue2 = it)) }
                LabeledSlider(t, "روشنایی", raw.lightness, 0f..1f) { onChange(raw.copy(lightness = it)) }
            }
            SwitchRow(t, "تیره و روشن خودکار (طبق تنظیم گوشی)", raw.auto) { onChange(raw.copy(auto = it)) }
        }

        Section(t, "رنگ تأکید") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c)
                            .border(if (i == raw.accentIndex) 3.dp else 1.dp,
                                if (i == raw.accentIndex) t.text else Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { onChange(raw.copy(accentIndex = i)) }
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(if (raw.customAccent) 3.dp else 1.dp,
                            if (raw.customAccent) t.text else Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onChange(raw.copy(accentIndex = nAc)) }
                )
            }
            if (raw.customAccent) {
                LabeledSlider(t, "طیف رنگ", raw.accentHue, 0f..360f) { onChange(raw.copy(accentHue = it)) }
            }
        }

        Section(t, "شیشه") {
            LabeledSlider(t, "شفافیت شیشه", raw.glass, 0.05f..0.4f) { onChange(raw.copy(glass = it)) }
            LabeledSlider(t, "گردی گوشه‌ها", raw.radius, 8f..34f) { onChange(raw.copy(radius = it)) }
            LabeledSlider(t, "درخشش پس‌زمینه", raw.glow, 0f..0.9f) { onChange(raw.copy(glow = it)) }
        }

        Section(t, "متن و تاریخ") {
            LabeledSlider(t, "اندازه متن", raw.textScale, 0.8f..1.6f) { onChange(raw.copy(textScale = it)) }
            Text("فونت", color = t.subText, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Palettes.fonts.forEachIndexed { i, n ->
                    val on = raw.fontIndex == i
                    Box(
                        Modifier.glass(t, 16.dp)
                            .background(if (on) t.accent.copy(alpha = 0.55f) else Color.Transparent)
                            .clickable { onChange(raw.copy(fontIndex = i)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(n, color = t.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            SwitchRow(t, "تاریخ شمسی و ارقام فارسی", raw.jalali) { onChange(raw.copy(jalali = it)) }
        }

        Section(t, "امنیت") {
            SwitchRow(t, "قفل برنامه (اثر انگشت یا قفل گوشی)", lockOn) { on ->
                if (on) {
                    val ok = BiometricManager.from(ctx).canAuthenticate(lockAuthenticators()) ==
                        BiometricManager.BIOMETRIC_SUCCESS
                    if (ok) Security.setEnabled(true)
                    else toast(ctx, "ابتدا در تنظیمات گوشی اثر انگشت یا قفل صفحه تعریف کنید")
                } else {
                    Security.setEnabled(false)
                }
            }
        }

        Section(t, "دسته‌بندی") {
            ActionBtn(t, "ویرایش کلمات دسته‌بندی") { onRules() }
        }

        Section(t, "پشتیبان‌گیری") {
            ActionBtn(t, "ذخیره‌ی همه‌ی پیام‌ها در فایل") { exportL.launch("passenger-backup.json") }
            ActionBtn(t, "بازیابی پیام‌ها از فایل") { importL.launch(arrayOf("application/json", "*/*")) }
        }

        if (!isDefault) {
            ActionBtn(t, "تنظیم به‌عنوان برنامه پیش‌فرض پیامک") { onDefault() }
        }
        Spacer(Modifier.size(16.dp))
    }
}

// ---------- category rules ----------

@Composable
fun RulesScreen(t: GlassTheme, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var gen by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { Store.refresh(ctx) }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(t, "→") { onBack() }
            Spacer(Modifier.size(12.dp))
            Text("کلمات دسته‌بندی", color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "کلمه‌ها را با ویرگول جدا کنید. اگر پیامی یکی از این کلمه‌ها را داشته باشد، در همان دسته قرار می‌گیرد. ترتیب بررسی: مخاطبین، کد تأیید، بانکی، تبلیغاتی، ناشناس.",
            color = t.subText, fontSize = 13.sp
        )
        for (c in Rules.editable) {
            key(gen, c) {
                var txt by remember { mutableStateOf(Rules.text(c)) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${c.emoji} ${c.label}", color = t.text, fontWeight = FontWeight.Bold)
                    GlassField(t, txt, { txt = it; Rules.set(c, it) }, "کلمه‌ها…",
                        Modifier.fillMaxWidth(), singleLine = false)
                }
            }
        }
        ActionBtn(t, "بازگشت به کلمات پیش‌فرض") { Rules.reset(); gen++ }
        Spacer(Modifier.size(16.dp))
    }
}
