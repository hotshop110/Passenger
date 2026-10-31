package com.passenger.sms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ThemeTile(t: GlassTheme, idx: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ft = if (idx > 0) Palettes.flats[idx - 1] else null
    val bar = if (ft == null) Color.White.copy(alpha = 0.25f) else if (ft.barIsPrimary) ft.accent else ft.bar
    val bg: Brush = if (ft == null) Brush.linearGradient(Palettes.backgrounds[0].colors) else Brush.linearGradient(listOf(ft.bg, ft.bg))
    val dot = if (ft == null) Color.White.copy(alpha = 0.5f) else ft.accent
    val line = if (ft == null) Color.White.copy(alpha = 0.35f) else ft.sub.copy(alpha = 0.5f)
    Column(modifier.clickable { onClick() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                .border(if (selected) 3.dp else 0.5.dp, if (selected) t.accent else t.divider, RoundedCornerShape(12.dp))
        ) {
            Box(Modifier.fillMaxWidth().height(20.dp).background(bar))
            for (i in 0 until 3) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.size(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(line))
                }
            }
        }
        Text(
            if (idx == 0) T("شیشه‌ای", "Glass") else T(ft!!.fa, ft.en),
            color = t.text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsScreen(
    raw: GlassTheme, t: GlassTheme, onChange: (GlassTheme) -> Unit, onBack: () -> Unit,
    isDefault: Boolean, onDefault: () -> Unit, onPage: (Page) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lockOn by Security.enabled
    val undo by Cfg.undoSecs
    val otpDays by Cfg.otpDays
    val quietOn by Cfg.quietOn
    val qs by Cfg.quietStart
    val qe by Cfg.quietEnd
    val arOn by Cfg.autoReplyOn
    val arText by Cfg.autoReplyText
    val nBg = Palettes.backgrounds.size
    val nAc = Palettes.accents.size
    var notifFor by remember { mutableStateOf<Cat?>(null) }

    val exportL = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    try { Backup.export(ctx, uri) } catch (e: Exception) { -1 }
                }
                toast(ctx, if (n >= 0) T("$n پیام پشتیبان‌گیری شد", "$n messages backed up") else T("پشتیبان‌گیری ناموفق بود", "Backup failed"))
            }
        }
    }
    val importL = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (!Sms.isDefault(ctx)) {
                    toast(ctx, T("برای بازیابی، Passenger باید برنامه پیش‌فرض پیامک باشد", "Passenger must be the default SMS app to restore"))
                } else {
                    val r = withContext(Dispatchers.IO) {
                        try { Backup.import(ctx, uri) } catch (e: Exception) { null }
                    }
                    toast(ctx, if (r != null) T("${r.first} پیام بازیابی شد، ${r.second} تکراری", "${r.first} restored, ${r.second} skipped") else T("بازیابی ناموفق بود", "Restore failed"))
                    Store.refresh(ctx)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("تنظیمات", "Settings"), onBack = onBack)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section(t, T("زبان", "Language")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t, "فارسی", Lang.code.value == "fa") { Cfg.setLang("fa") }
                    Chip(t, "English", Lang.code.value == "en") { Cfg.setLang("en") }
                }
            }

            Section(t, T("تم", "Theme")) {
                val idxs = (0..Palettes.flats.size).toList()
                for (pair in idxs.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in pair) {
                            ThemeTile(t, i, raw.style == i, Modifier.weight(1f)) { onChange(raw.copy(style = i)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                SwitchRow(t, T("تیره و روشن خودکار (طبق گوشی)", "Auto dark / light (follow phone)"), raw.auto) { onChange(raw.copy(auto = it)) }
            }

            Section(t, T("رنگ اصلی", "Accent color")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f))
                            .border(if (raw.accentIndex < 0) 3.dp else 1.dp, if (raw.accentIndex < 0) t.text else t.divider, CircleShape)
                            .clickable { onChange(raw.copy(accentIndex = -1)) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", color = t.text, fontWeight = FontWeight.Bold) }
                    Palettes.accents.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .border(if (i == raw.accentIndex) 3.dp else 1.dp, if (i == raw.accentIndex) t.text else t.divider, CircleShape)
                                .clickable { onChange(raw.copy(accentIndex = i)) }
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            .border(if (raw.customAccent) 3.dp else 1.dp, if (raw.customAccent) t.text else t.divider, CircleShape)
                            .clickable { onChange(raw.copy(accentIndex = nAc)) }
                    )
                }
                if (raw.customAccent) {
                    LabeledSlider(t, T("طیف رنگ", "Hue"), raw.accentHue, 0f..360f) { onChange(raw.copy(accentHue = it)) }
                }
            }

            if (raw.style == 0) {
                Section(t, T("پس‌زمینه شیشه‌ای", "Glass background")) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Palettes.backgrounds.forEachIndexed { i, p ->
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(p.colors))
                                    .border(if (i == raw.bgIndex) 3.dp else 1.dp, if (i == raw.bgIndex) t.accent else Color.White.copy(alpha = 0.4f), CircleShape)
                                    .clickable { onChange(raw.copy(bgIndex = i)) }
                            )
                        }
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(raw.copy(bgIndex = nBg).bg))
                                .border(if (raw.customBg) 3.dp else 1.dp, if (raw.customBg) t.accent else Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { onChange(raw.copy(bgIndex = nBg)) },
                            contentAlignment = Alignment.Center
                        ) { Text("✎", color = Color.White, fontSize = 18.sp) }
                    }
                    if (raw.customBg) {
                        LabeledSlider(t, T("رنگ اول", "Color 1"), raw.hue1, 0f..360f) { onChange(raw.copy(hue1 = it)) }
                        LabeledSlider(t, T("رنگ دوم", "Color 2"), raw.hue2, 0f..360f) { onChange(raw.copy(hue2 = it)) }
                        LabeledSlider(t, T("روشنایی", "Brightness"), raw.lightness, 0f..1f) { onChange(raw.copy(lightness = it)) }
                    }
                    LabeledSlider(t, T("شفافیت شیشه", "Glass opacity"), raw.glass, 0.05f..0.4f) { onChange(raw.copy(glass = it)) }
                    LabeledSlider(t, T("گردی گوشه‌ها", "Corner radius"), raw.radius, 8f..34f) { onChange(raw.copy(radius = it)) }
                    LabeledSlider(t, T("درخشش پس‌زمینه", "Background glow"), raw.glow, 0f..0.9f) { onChange(raw.copy(glow = it)) }
                }
            }

            Section(t, T("ظاهر لیست و پیام‌ها", "List & bubbles")) {
                SwitchRow(t, T("لیست فشرده", "Compact list"), raw.dense) { onChange(raw.copy(dense = it)) }
                LabeledSlider(t, T("گردی حباب پیام", "Bubble roundness"), raw.bubble, 4f..28f) { onChange(raw.copy(bubble = it)) }
                LabeledSlider(t, T("اندازه متن پیام‌ها", "Message text size"), raw.msgScale, 0.8f..1.6f) { onChange(raw.copy(msgScale = it)) }
            }

            Section(t, T("متن و تاریخ", "Text & date")) {
                LabeledSlider(t, T("اندازه متن برنامه", "App text size"), raw.textScale, 0.8f..1.6f) { onChange(raw.copy(textScale = it)) }
                Text(T("فونت", "Font"), color = t.subText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palettes.fonts.forEachIndexed { i, n -> Chip(t, n, raw.fontIndex == i) { onChange(raw.copy(fontIndex = i)) } }
                }
                SwitchRow(t, T("تاریخ شمسی و ارقام فارسی", "Jalali dates & Persian digits"), raw.jalali) { onChange(raw.copy(jalali = it)) }
            }

            Section(t, T("اعلان‌ها", "Notifications")) {
                Text(T("صدا، لرزش و رنگ هر دسته", "Sound, vibration and color per category"), color = t.subText, fontSize = 13.sp)
                for (c in listOf(Cat.CONTACTS, Cat.BANK, Cat.OTP, Cat.GOV, Cat.PROMO, Cat.UNKNOWN)) {
                    ActionBtn(t, c.emoji + " " + c.label) { notifFor = c }
                }
                SwitchRow(t, T("ساعت سکوت", "Quiet hours"), quietOn) { Cfg.setQuietOn(it) }
                if (quietOn) {
                    LabeledSlider(t, T("شروع: ", "From: ") + Fmt.digits("$qs:00", raw.jalali), qs.toFloat(), 0f..23f) { Cfg.setQuietStart(it.roundToInt()) }
                    LabeledSlider(t, T("پایان: ", "To: ") + Fmt.digits("$qe:00", raw.jalali), qe.toFloat(), 0f..23f) { Cfg.setQuietEnd(it.roundToInt()) }
                }
            }

            Section(t, T("ارسال", "Sending")) {
                Text(T("تأخیر قبل از ارسال (امکان لغو)", "Delay before sending (undo)"), color = t.subText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in listOf(0, 5, 10)) {
                        Chip(t, if (s == 0) T("خاموش", "Off") else Fmt.digits("$s", raw.jalali) + T(" ثانیه", " s"), undo == s) { Cfg.setUndo(s) }
                    }
                }
                SwitchRow(t, T("پاسخ خودکار به مخاطبین", "Auto-reply to contacts"), arOn) { Cfg.setAutoReplyOn(it) }
                if (arOn) {
                    GlassField(t, arText, { Cfg.setAutoReplyText(it) }, T("متن پاسخ خودکار", "Auto-reply text"), Modifier.fillMaxWidth(), singleLine = false)
                }
                ActionBtn(t, T("پیام‌های آماده", "Templates")) { onPage(Page.TEMPLATES) }
            }

            Section(t, T("پاک‌سازی خودکار", "Auto cleanup")) {
                Text(T("حذف کدهای تأیید قدیمی‌تر از…", "Delete verification codes older than…"), color = t.subText, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in listOf(0, 1, 3, 7, 30)) {
                        Chip(t, if (d == 0) T("خاموش", "Off") else Fmt.digits("$d", raw.jalali) + T(" روز", " d"), otpDays == d) { Cfg.setOtpDays(d) }
                    }
                }
            }

            Section(t, T("امنیت", "Security")) {
                SwitchRow(t, T("قفل برنامه (اثر انگشت یا قفل گوشی)", "App lock (fingerprint or phone lock)"), lockOn) { on ->
                    if (on) {
                        val ok = BiometricManager.from(ctx).canAuthenticate(lockAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
                        if (ok) Security.setEnabled(true)
                        else toast(ctx, T("ابتدا در تنظیمات گوشی اثر انگشت یا قفل صفحه تعریف کنید", "Set up a fingerprint or screen lock in phone settings first"))
                    } else {
                        Security.setEnabled(false)
                    }
                }
            }

            Section(t, T("دسته‌بندی", "Categories")) {
                ActionBtn(t, T("ویرایش کلمات دسته‌بندی", "Edit category keywords")) { onPage(Page.RULES) }
            }

            Section(t, T("پشتیبان‌گیری", "Backup")) {
                ActionBtn(t, T("ذخیره‌ی همه‌ی پیام‌ها در فایل", "Save all messages to a file")) { exportL.launch("passenger-backup.json") }
                ActionBtn(t, T("بازیابی پیام‌ها از فایل", "Restore messages from a file")) { importL.launch(arrayOf("application/json", "*/*")) }
            }

            if (!isDefault) {
                ActionBtn(t, T("تنظیم به‌عنوان برنامه پیش‌فرض پیامک", "Set as default SMS app")) { onDefault() }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    val nf = notifFor
    if (nf != null) {
        NotifEditor(t, "c:${nf.name}", nf.emoji + " " + nf.label) { notifFor = null }
    }
}
