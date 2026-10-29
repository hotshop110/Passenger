package com.passenger.sms

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val f = if (now - ts < 24L * 3600 * 1000) "HH:mm" else "yyyy/MM/dd"
    return SimpleDateFormat(f, Locale.getDefault()).format(Date(ts))
}

@Composable
fun GlassField(
    t: GlassTheme, value: String, onValue: (String) -> Unit,
    hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true
) {
    Box(modifier.glass(t, 20.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = t.subText, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
            textStyle = TextStyle(color = t.text, fontSize = 15.sp),
            cursorBrush = SolidColor(t.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HomeScreen(
    t: GlassTheme, isDefault: Boolean, onDefault: () -> Unit,
    onOpen: (String) -> Unit, onSettings: () -> Unit
) {
    val convs by Store.convs
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Cat.ALL) }
    val shown = remember(convs, query, cat) {
        convs.filter {
            (cat == Cat.ALL || it.cat == cat) &&
                (query.isBlank() ||
                    (it.name ?: "").contains(query, true) ||
                    it.address.contains(query) ||
                    it.last.body.contains(query, true))
        }
    }
    val looksLikeNumber = query.length >= 3 && query.all { it.isDigit() || it == '+' }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Passenger", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).glass(t, 22.dp).clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) { Text("🎨", fontSize = 18.sp) }
        }

        if (!isDefault) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .glass(t, 18.dp).clickable { onDefault() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("برای دریافت و ذخیره پیامک‌ها، Passenger را برنامه پیش‌فرض کنید",
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
                val n = if (c == Cat.ALL) convs.size else convs.count { it.cat == c }
                val sel = c == cat
                Row(
                    Modifier.glass(t, 18.dp)
                        .background(if (sel) t.accent.copy(alpha = 0.55f) else Color.Transparent)
                        .clickable { cat = c }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.emoji, fontSize = 14.sp)
                    Spacer(Modifier.size(6.dp))
                    Text("${c.label} $n", color = t.text, fontSize = 13.sp,
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
                    Text("پیامی در این دسته نیست", color = t.subText, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            }
            items(shown, key = { it.key }) { c -> ConvRow(t, c) { onOpen(c.address) } }
        }
    }
}

@Composable
fun ConvRow(t: GlassTheme, c: Conv, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(t).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(t.accent.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            val title = c.name ?: c.address
            if (c.cat == Cat.CONTACTS) Text(title.take(1), color = t.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            Text(fmtTime(c.last.date), color = t.subText, fontSize = 11.sp)
            if (c.unread > 0) {
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier.clip(CircleShape).background(t.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) { Text("${c.unread}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ThreadScreen(t: GlassTheme, address: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val convs by Store.convs
    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var text by remember { mutableStateOf("") }
    val name = remember(address, convs) { convs.firstOrNull { it.address == address }?.name }

    LaunchedEffect(convs, address) {
        val list = withContext(Dispatchers.IO) { Sms.loadThread(ctx, address) }
        msgs = list
        if (list.any { !it.read && it.type == 1 }) {
            withContext(Dispatchers.IO) { Sms.markRead(ctx, address) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).glass(t, 24.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("→", color = t.text, fontSize = 22.sp)
            }
            Spacer(Modifier.size(8.dp))
            Column {
                Text(name ?: address, color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (name != null) Text(address, color = t.subText, fontSize = 11.sp)
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
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(m.body, color = t.text, fontSize = 15.sp)
                        Text(fmtTime(m.date), color = t.subText, fontSize = 10.sp)
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
}

@Composable
fun SettingsScreen(
    t: GlassTheme, onChange: (GlassTheme) -> Unit, onBack: () -> Unit,
    isDefault: Boolean, onDefault: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).glass(t, 20.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("→", color = t.text, fontSize = 20.sp)
            }
            Spacer(Modifier.size(12.dp))
            Text("ظاهر برنامه", color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxWidth().glass(t).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("پس‌زمینه", color = t.text, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.backgrounds.forEachIndexed { i, p ->
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(Brush.linearGradient(p.colors))
                            .border(if (i == t.bgIndex) 3.dp else 1.dp,
                                if (i == t.bgIndex) t.accent else Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { onChange(t.copy(bgIndex = i)) }
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass(t).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("رنگ تأکید", color = t.text, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c)
                            .border(if (i == t.accentIndex) 3.dp else 1.dp,
                                if (i == t.accentIndex) t.text else Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { onChange(t.copy(accentIndex = i)) }
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass(t).padding(16.dp)) {
            Text("شفافیت شیشه", color = t.text, fontWeight = FontWeight.Bold)
            Slider(
                value = t.glass, onValueChange = { onChange(t.copy(glass = it)) },
                valueRange = 0.05f..0.4f,
                colors = SliderDefaults.colors(
                    thumbColor = t.accent, activeTrackColor = t.accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        if (!isDefault) {
            Box(
                Modifier.fillMaxWidth().glass(t).clickable { onDefault() }.padding(16.dp)
            ) { Text("تنظیم به‌عنوان برنامه پیش‌فرض پیامک", color = t.accent, fontWeight = FontWeight.Bold) }
        }
    }
}
