package com.passenger.sms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun inTab(c: Conv, tb: Cat, archived: Set<String>, blocked: Set<String>, priv: Set<String>): Boolean {
    if (c.key in priv) return false
    return when (tb) {
        Cat.ALL -> c.key !in archived && c.key !in blocked
        Cat.ARCHIVE -> c.key in archived
        Cat.SPAM -> c.key in blocked
        else -> c.cat == tb && c.key !in archived && c.key !in blocked
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    t: GlassTheme, isDefault: Boolean, onDefault: () -> Unit,
    onOpen: (String) -> Unit, onPage: (Page) -> Unit, onPrivate: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val convs by Store.convs
    val sims by Sim.sims
    val pinned by Flags.pinned
    val archived by Flags.archived
    val blocked by Flags.blocked
    val priv by Flags.privateSet
    val drafts by Flags.drafts
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Cat.ALL) }
    var simFilter by remember { mutableStateOf(-1) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    var askMove by remember { mutableStateOf(false) }
    var askNew by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }

    val shown = remember(convs, query, tab, simFilter, pinned, archived, blocked, priv) {
        convs.filter {
            inTab(it, tab, archived, blocked, priv) &&
                (simFilter < 0 || it.subId == simFilter) &&
                (query.isBlank() ||
                    (it.name ?: "").contains(query, true) ||
                    it.address.contains(query) ||
                    it.last.body.contains(query, true))
        }.sortedByDescending { it.key in pinned }
    }
    val looksLikeNumber = query.length >= 3 && query.all { it.isDigit() || it == '+' }

    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isEmpty()) {
                AppBar(t, "Passenger") {
                    val fg = if (t.flat) t.barText else t.text
                    BarBtn(fg, "⚙") { onPage(Page.SETTINGS) }
                    MenuBtn(
                        fg,
                        listOf(
                            T("جستجوی پیشرفته", "Advanced search") to { onPage(Page.SEARCH) },
                            T("ستاره‌دارها", "Starred") to { onPage(Page.STARRED) },
                            T("زمان‌بندی‌شده‌ها", "Scheduled") to { onPage(Page.SCHEDULED) },
                            T("خلاصه بانکی", "Bank summary") to { onPage(Page.BANK) },
                            T("ارسال گروهی", "Bulk send") to { onPage(Page.BULK) },
                            T("پوشه خصوصی", "Private box") to { onPrivate() },
                            T("سطل زباله", "Trash") to { onPage(Page.TRASH) }
                        )
                    )
                }
            } else {
                val fg = if (t.flat) t.barText else t.text
                AppBar(t, Fmt.digits(T("${selected.size} انتخاب", "${selected.size} selected"), t.jalali), onBack = { selected = emptySet() }) {
                    BarBtn(fg, "📌") {
                        val allPinned = selected.all { it in pinned }
                        Flags.setPinned(selected, !allPinned)
                        selected = emptySet()
                    }
                    BarBtn(fg, "🗑") { askDelete = true }
                    MenuBtn(
                        fg,
                        listOf(
                            (if (tab == Cat.ARCHIVE) T("خروج از بایگانی", "Unarchive") else T("بایگانی", "Archive")) to {
                                Flags.setArchived(selected, tab != Cat.ARCHIVE); selected = emptySet()
                            },
                            (if (tab == Cat.SPAM) T("خروج از اسپم", "Not spam") else T("اسپم", "Spam")) to {
                                Flags.setBlocked(selected, tab != Cat.SPAM); selected = emptySet()
                            },
                            T("انتقال به دسته", "Move to category") to { askMove = true },
                            T("انتقال به پوشه خصوصی", "Move to private box") to {
                                Flags.setPrivate(selected, true); selected = emptySet()
                            },
                            T("علامت خوانده‌شده", "Mark as read") to {
                                val addrs = convs.filter { it.key in selected }.map { it.address }
                                selected = emptySet()
                                if (!Sms.isDefault(ctx)) {
                                    toast(ctx, T("برای این کار باید برنامه پیش‌فرض باشد", "Set Passenger as default SMS app first"))
                                } else {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { for (a in addrs) Sms.markRead(ctx, a) }
                                        Store.refresh(ctx)
                                    }
                                }
                            }
                        )
                    )
                }
            }

            if (!isDefault) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                        .glass(t, 14.dp).clickable { onDefault() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        T("برای دریافت، ذخیره و حذف پیامک‌ها، Passenger را برنامه پیش‌فرض کنید",
                            "Make Passenger your default SMS app to receive, store and delete messages"),
                        color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Text(T("تنظیم", "Set"), color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            GlassField(t, query, { query = it }, T("جستجو در پیام‌ها و مخاطبین…", "Search messages and contacts…"),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Cat.values().toList()) { c ->
                    val n = convs.count { inTab(it, c, archived, blocked, priv) }
                    Chip(t, c.emoji + " " + Fmt.digits("${c.label} $n", t.jalali), c == tab) { tab = c }
                }
            }

            if (sims.size >= 2) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Chip(t, T("همه سیم‌ها", "All SIMs"), simFilter < 0) { simFilter = -1 } }
                    items(sims) { s ->
                        Chip(t, "SIM ${s.slot + 1}", simFilter == s.subId) { simFilter = s.subId }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = if (t.flat) 0.dp else 16.dp, end = if (t.flat) 0.dp else 16.dp,
                    top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (t.flat) 0.dp else 10.dp)
            ) {
                if (looksLikeNumber && shown.isEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp).glass(t).clickable { onOpen(query) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(T("✎  ارسال پیام به $query", "✎  Message $query"), color = t.text, fontSize = 15.sp)
                        }
                    }
                }
                if (shown.isEmpty() && !looksLikeNumber) {
                    item {
                        Text(
                            T("پیامی در این بخش نیست", "No messages here"), color = t.subText, fontSize = 14.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                }
                items(shown, key = { it.key }) { c ->
                    SwipeRow(
                        t = t, c = c, enabled = selected.isEmpty(),
                        onPin = { Flags.setPinned(listOf(c.key), c.key !in pinned) },
                        onArchive = { Flags.setArchived(listOf(c.key), c.key !in archived) }
                    ) {
                        ConvRow(
                            t, c, pinned = c.key in pinned, selected = c.key in selected,
                            draft = drafts[c.key], sim = if (sims.size >= 2) Sim.slotLabel(c.subId) else null,
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
        }

        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .size(56.dp).clip(CircleShape).background(t.accent).clickable { askNew = true },
            contentAlignment = Alignment.Center
        ) { Text("✎", color = t.onAccent, fontSize = 22.sp) }
    }

    if (askNew) {
        AlertDialog(
            onDismissRequest = { askNew = false },
            title = { Text(T("پیام جدید", "New message")) },
            text = {
                GlassField(t, newNumber, { newNumber = it }, T("شماره یا نام", "Number"), Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newNumber.trim()
                    askNew = false
                    newNumber = ""
                    if (n.isNotEmpty()) onOpen(n)
                }) { Text(T("ادامه", "Next")) }
            },
            dismissButton = { TextButton(onClick = { askNew = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(T("حذف مکالمه‌ها", "Delete conversations")) },
            text = {
                Text(
                    Fmt.digits(
                        T("${selected.size} مکالمه حذف و ۳۰ روز در سطل زباله نگه داشته می‌شود.",
                            "${selected.size} conversation(s) will be deleted and kept in Trash for 30 days."),
                        t.jalali
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    if (!Sms.isDefault(ctx)) {
                        toast(ctx, T("برای حذف، Passenger باید برنامه پیش‌فرض پیامک باشد",
                            "Passenger must be the default SMS app to delete"))
                    } else {
                        val addrs = convs.filter { it.key in selected }.map { it.address }
                        selected = emptySet()
                        scope.launch {
                            withContext(Dispatchers.IO) { Sms.deleteConvs(ctx, addrs) }
                            Store.refresh(ctx)
                        }
                    }
                }) { Text(T("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askMove) {
        AlertDialog(
            onDismissRequest = { askMove = false },
            title = { Text(T("انتقال به دسته", "Move to category")) },
            text = {
                Column {
                    val options = listOf<Cat?>(Cat.CONTACTS, Cat.BANK, Cat.OTP, Cat.GOV, Cat.PROMO, Cat.UNKNOWN, null)
                    for (c in options) {
                        Text(
                            if (c == null) T("↺ تشخیص خودکار", "↺ Automatic") else c.emoji + " " + c.label,
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
            dismissButton = { TextButton(onClick = { askMove = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(
    t: GlassTheme, c: Conv, enabled: Boolean,
    onPin: () -> Unit, onArchive: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.StartToEnd) onPin()
            else if (v == SwipeToDismissBoxValue.EndToStart) onArchive()
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(t.accent.copy(alpha = 0.35f)).padding(horizontal = 24.dp),
                contentAlignment = if (toStart) Alignment.CenterStart else Alignment.CenterEnd
            ) { Text(if (toStart) "📌" else "🗄", fontSize = 22.sp) }
        }
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConvRow(
    t: GlassTheme, c: Conv, pinned: Boolean, selected: Boolean, draft: String?, sim: String?,
    onClick: () -> Unit, onLong: () -> Unit
) {
    val pad = if (t.dense) 7.dp else 12.dp
    val av = if (t.dense) 38.dp else 48.dp
    val base = if (t.flat) Modifier.fillMaxWidth().background(t.bg.first()) else Modifier.fillMaxWidth().glass(t)
    Column(base) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (selected) t.accent.copy(alpha = 0.22f) else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(horizontal = 14.dp, vertical = pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(t, c, av, selected)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name ?: c.address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (draft != null) {
                    Text(
                        T("پیش‌نویس: ", "Draft: ") + draft, color = t.accent, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        c.last.body, color = t.subText, fontSize = 13.sp,
                        maxLines = if (t.dense) 1 else 2, overflow = TextOverflow.Ellipsis
                    )
                }
                val code = if (c.cat == Cat.OTP) Classifier.extractCode(c.last.body) else null
                if (code != null) {
                    Text(T("کد: ", "Code: ") + code, color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) Text("📌", fontSize = 11.sp)
                    Text(Fmt.time(c.last.date, t), color = t.subText, fontSize = 11.sp)
                }
                if (sim != null) Text(sim, color = t.subText, fontSize = 10.sp)
                if (c.unread > 0) {
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier.clip(CircleShape).background(t.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Fmt.digits("${c.unread}", t.jalali), color = t.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (t.flat) Divider(t)
    }
}
