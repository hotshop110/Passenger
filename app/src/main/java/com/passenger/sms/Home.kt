package com.passenger.sms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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

class TabEnv(
    val archived: Set<String>, val blocked: Set<String>,
    val priv: Set<String>, val folderOf: Map<String, String>
)

fun inTab(c: Conv, tb: Cat, e: TabEnv): Boolean {
    if (c.key in e.priv) return false
    val isBlocked = c.key in e.blocked || c.spam
    return when (tb) {
        Cat.ALL -> c.key !in e.archived && !isBlocked
        Cat.ARCHIVE -> c.key in e.archived
        Cat.SPAM -> isBlocked
        else -> c.cat == tb && c.key !in e.archived && !isBlocked
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
    val vip by Flags.vip
    val folders by Flags.folders
    val folderOf by Flags.folderOf
    val shared by Store.sharedText
    val swipeStart by Cfg.swipeStart
    val swipeEnd by Cfg.swipeEnd
    val showBal by Cfg.showBalances
    val proActive by Pro.active

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Cat.ALL) }
    var folderTab by remember { mutableStateOf<String?>(null) }
    var simFilter by remember { mutableStateOf(-1) }
    var unreadOnly by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    var askMove by remember { mutableStateOf(false) }
    var askFolder by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    var balances by remember { mutableStateOf(listOf<BalanceInfo>()) }

    val env = TabEnv(archived, blocked, priv, folderOf)

    val shown = remember(convs, query, tab, folderTab, simFilter, unreadOnly, pinned, archived, blocked, priv, folderOf) {
        convs.filter {
            val okTab = if (folderTab != null) {
                folderOf[it.key] == folderTab && it.key !in archived && it.key !in blocked && it.key !in priv && !it.spam
            } else {
                inTab(it, tab, env)
            }
            okTab &&
                (simFilter < 0 || it.subId == simFilter) &&
                (!unreadOnly || it.unread > 0) &&
                (query.isBlank() ||
                    (it.name ?: "").contains(query, true) ||
                    it.address.contains(query) ||
                    it.last.body.contains(query, true))
        }.sortedByDescending { it.key in pinned }
    }
    val looksLikeNumber = query.length >= 3 && query.all { it.isDigit() || it == '+' }

    LaunchedEffect(convs.size, showBal, proActive) {
        balances = if (showBal && Pro.allowed("balances")) {
            withContext(Dispatchers.IO) { Bank.latestBalances(ctx) }
        } else emptyList()
    }

    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    fun markReadFor(addrs: List<String>) {
        if (!Sms.isDefault(ctx)) {
            toast(ctx, T("برای این کار باید برنامه پیش‌فرض باشد", "Set PSMS as default SMS app first"))
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) { for (a in addrs) Sms.markRead(ctx, a) }
            Store.refresh(ctx)
        }
    }

    fun doSwipe(a: String, c: Conv) {
        when (a) {
            "pin" -> Flags.setPinned(listOf(c.key), c.key !in pinned)
            "archive" -> Flags.setArchived(listOf(c.key), c.key !in archived)
            "delete" -> { selected = setOf(c.key); askDelete = true }
            "read" -> markReadFor(listOf(c.address))
        }
    }

    val filterActive = tab != Cat.ALL || folderTab != null || simFilter >= 0 || unreadOnly

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isEmpty()) {
                AppBar(t, "PSMS") {
                    val fg = if (t.flat) t.barText else t.text
                    BarBtn(fg, R.drawable.ic_tune) { onPage(Page.SETTINGS) }
                    MenuBtn(
                        fg,
                        listOf(
                            T("جستجوی پیشرفته", "Advanced search") to { onPage(Page.SEARCH) },
                            T("ستاره‌دارها", "Starred") to { onPage(Page.STARRED) },
                            T("زمان‌بندی‌شده‌ها", "Scheduled") to { onPage(Page.SCHEDULED) },
                            T("خلاصه بانکی", "Bank summary") to { onPage(Page.BANK) },
                            T("آمار پیام‌ها", "Statistics") to { if (Pro.gate(ctx, "stats")) onPage(Page.STATS) },
                            T("ارسال گروهی", "Bulk send") to { onPage(Page.BULK) },
                            T("پوشه خصوصی", "Private box") to { if (Pro.gate(ctx, "private")) onPrivate() },
                            T("پاک‌سازی", "Cleanup") to { if (Pro.gate(ctx, "cleanup")) onPage(Page.CLEANUP) },
                            T("علامت همه به‌عنوان خوانده‌شده", "Mark all as read") to {
                                markReadFor(convs.filter { it.unread > 0 }.map { it.address })
                            },
                            T("سطل زباله", "Trash") to { onPage(Page.TRASH) },
                            T("نسخه PSMS Pro", "PSMS Pro") to { onPage(Page.PRO) }
                        ).filter { !(BuildConfig.STORE_BUILD && it.first == T("نسخه PSMS Pro", "PSMS Pro")) }
                    )
                }
            } else {
                val fg = if (t.flat) t.barText else t.text
                AppBar(t, Fmt.digits(T("${selected.size} انتخاب", "${selected.size} selected"), t.jalali), onBack = { selected = emptySet() }) {
                    BarBtn(fg, R.drawable.ic_pin) {
                        val allPinned = selected.all { it in pinned }
                        Flags.setPinned(selected, !allPinned)
                        selected = emptySet()
                    }
                    BarBtn(fg, R.drawable.ic_trash) { askDelete = true }
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
                            T("انتقال به پوشه", "Move to folder") to { if (Pro.gate(ctx, "folders")) askFolder = true },
                            T("مخاطب ویژه (VIP)", "Toggle VIP") to {
                                val all = selected.all { it in vip }
                                Flags.setVip(selected, !all); selected = emptySet()
                            },
                            T("انتقال به پوشه خصوصی", "Move to private box") to {
                                if (Pro.gate(ctx, "private")) { Flags.setPrivate(selected, true); selected = emptySet() }
                            },
                            T("علامت خوانده‌شده", "Mark as read") to {
                                val addrs = convs.filter { it.key in selected }.map { it.address }
                                selected = emptySet()
                                markReadFor(addrs)
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
                        T("برای دریافت، ذخیره و حذف پیامک‌ها، PSMS را برنامه پیش‌فرض کنید",
                            "Make PSMS your default SMS app to receive, store and delete messages"),
                        color = t.text, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Text(T("تنظیم", "Set"), color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            val sh = shared
            if (sh != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                        .glass(t, 14.dp).clickable { onPage(Page.NEWMSG) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        T("گیرنده را انتخاب کنید: ", "Choose a recipient: ") + sh,
                        color = t.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { Store.sharedText.value = null }, contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_close, t.text, 18.dp)
                    }
                }
            }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassField(t, query, { query = it }, T("جستجو در پیام‌ها و مخاطبین…", "Search messages and contacts…"), Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier.size(48.dp).glass(t, 24.dp).clickable { filtersOpen = !filtersOpen },
                    contentAlignment = Alignment.Center
                ) {
                    Ic(
                        R.drawable.ic_chevron_down, if (filterActive) t.accent else t.text, 22.dp,
                        Modifier.rotate(if (filtersOpen) 180f else 0f)
                    )
                    if (filterActive) {
                        Box(Modifier.align(Alignment.TopEnd).padding(11.dp).size(8.dp).clip(CircleShape).background(t.accent))
                    }
                }
            }

            if (filterActive && !filtersOpen) {
                val parts = ArrayList<String>()
                if (folderTab != null) parts.add(folderTab!!) else if (tab != Cat.ALL) parts.add(tab.label)
                if (unreadOnly) parts.add(T("نخوانده", "Unread"))
                if (simFilter >= 0) parts.add("SIM " + ((sims.firstOrNull { it.subId == simFilter }?.slot ?: 0) + 1))
                Text(
                    parts.joinToString("  ·  "),
                    color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
                )
            }

            if (filtersOpen) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Chip(t, T("نخوانده", "Unread"), unreadOnly, R.drawable.ic_unread) { unreadOnly = !unreadOnly } }
                    items(Cat.values().toList()) { c ->
                        val n = convs.count { inTab(it, c, env) }
                        Chip(t, Fmt.digits("${c.label} $n", t.jalali), folderTab == null && c == tab, c.icon) {
                            tab = c; folderTab = null
                        }
                    }
                }
                if (folders.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(folders) { f ->
                            val n = convs.count { folderOf[it.key] == f && it.key !in priv }
                            Chip(t, Fmt.digits("$f $n", t.jalali), folderTab == f, R.drawable.ic_folder) {
                                folderTab = if (folderTab == f) null else f
                                tab = Cat.ALL
                            }
                        }
                    }
                }
                if (sims.size >= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Chip(t, T("همه سیم‌ها", "All SIMs"), simFilter < 0, R.drawable.ic_sim) { simFilter = -1 } }
                        items(sims) { s ->
                            Chip(t, "SIM ${s.slot + 1}", simFilter == s.subId, R.drawable.ic_sim) { simFilter = s.subId }
                        }
                    }
                }
            }

            if (balances.isNotEmpty() && !filterActive && query.isBlank()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (b in balances) {
                        Column(
                            Modifier.glass(t, 14.dp).clickable { onOpen(b.address) }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(b.name ?: b.address, color = t.subText, fontSize = 11.sp, maxLines = 1)
                            Text(money(b.amount, t), color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(Fmt.date(b.date, t), color = t.subText, fontSize = 10.sp)
                        }
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
                            Text(T("ارسال پیام به $query", "Message $query"), color = t.text, fontSize = 15.sp)
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
                        t = t, enabled = selected.isEmpty(),
                        startAction = swipeStart, endAction = swipeEnd,
                        onAction = { a -> doSwipe(a, c) }
                    ) {
                        ConvRow(
                            t, c, pinned = c.key in pinned, selected = c.key in selected, vip = c.key in vip,
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
                .size(56.dp).clip(CircleShape).background(t.accent).clickable { onPage(Page.NEWMSG) },
            contentAlignment = Alignment.Center
        ) { Ic(R.drawable.ic_edit, t.onAccent, 26.dp) }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false; selected = emptySet() },
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
                        toast(ctx, T("برای حذف، PSMS باید برنامه پیش‌فرض پیامک باشد",
                            "PSMS must be the default SMS app to delete"))
                        selected = emptySet()
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
            dismissButton = { TextButton(onClick = { askDelete = false; selected = emptySet() }) { Text(T("انصراف", "Cancel")) } }
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
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Flags.setOverride(selected, c)
                                selected = emptySet()
                                askMove = false
                                Store.refresh(ctx)
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (c != null) {
                                Ic(c.icon, LocalContentColor.current, 20.dp)
                                Spacer(Modifier.size(10.dp))
                            }
                            Text(if (c == null) T("تشخیص خودکار", "Automatic") else c.label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askMove = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }

    if (askFolder) {
        AlertDialog(
            onDismissRequest = { askFolder = false },
            title = { Text(T("انتقال به پوشه", "Move to folder")) },
            text = {
                Column {
                    for (f in folders) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Flags.setFolder(selected, f)
                                selected = emptySet()
                                askFolder = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ic(R.drawable.ic_folder, LocalContentColor.current, 20.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(f, fontSize = 16.sp)
                        }
                    }
                    Text(
                        T("بدون پوشه", "No folder"), fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            Flags.setFolder(selected, null)
                            selected = emptySet()
                            askFolder = false
                        }.padding(vertical = 12.dp)
                    )
                    GlassField(t, newFolder, { newFolder = it }, T("پوشه جدید…", "New folder…"), Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newFolder.trim()
                    if (n.isNotEmpty()) {
                        Flags.addFolder(n)
                        Flags.setFolder(selected, n)
                        newFolder = ""
                        selected = emptySet()
                        askFolder = false
                    }
                }) { Text(T("ساخت و انتقال", "Create & move")) }
            },
            dismissButton = { TextButton(onClick = { askFolder = false }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(
    t: GlassTheme, enabled: Boolean, startAction: String, endAction: String,
    onAction: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.StartToEnd) onAction(startAction)
            else if (v == SwipeToDismissBoxValue.EndToStart) onAction(endAction)
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled && startAction != "none",
        enableDismissFromEndToStart = enabled && endAction != "none",
        backgroundContent = {
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val a = if (toStart) startAction else endAction
            Box(
                Modifier.fillMaxSize().background(t.accent.copy(alpha = 0.35f)).padding(horizontal = 24.dp),
                contentAlignment = if (toStart) Alignment.CenterStart else Alignment.CenterEnd
            ) { Ic(swipeIcon(a), t.text, 24.dp) }
        }
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConvRow(
    t: GlassTheme, c: Conv, pinned: Boolean, selected: Boolean, vip: Boolean, draft: String?, sim: String?,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name ?: c.address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (vip) {
                        Spacer(Modifier.size(4.dp))
                        Ic(R.drawable.ic_vip, t.accent, 14.dp)
                    }
                }
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
                    if (pinned) {
                        Ic(R.drawable.ic_pin, t.subText, 12.dp)
                        Spacer(Modifier.size(3.dp))
                    }
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
