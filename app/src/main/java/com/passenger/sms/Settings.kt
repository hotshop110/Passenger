package com.passenger.sms

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun LockedBtn(t: GlassTheme, label: String, onPro: () -> Unit) {
    ActionBtn(t, label + "  —  PSMS Pro", R.drawable.ic_lock) { onPro() }
}

@Composable
private fun AboutPoint(t: GlassTheme, icon: Int, title: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(t.accent.copy(alpha = if (t.flat) 0.16f else 0.28f)),
            contentAlignment = Alignment.Center
        ) { Ic(icon, t.accent, 20.dp) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text, color = t.subText, fontSize = 12.sp)
        }
    }
}

@Composable
fun AboutCard(t: GlassTheme, onPrivacy: () -> Unit) {
    val ctx = LocalContext.current
    val ver = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    Column(
        Modifier.fillMaxWidth().glass(t, 22.dp).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.logo_psms), contentDescription = null,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(26.dp))
        )
        Text("PSMS", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier.clip(CircleShape).background(t.accent.copy(alpha = if (t.flat) 0.16f else 0.3f))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(T("نسخه ", "Version ") + Fmt.digits(ver, t.jalali), color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            T("امنیت و سرعت را احساس کنید", "Feel the security and speed"),
            color = t.accent, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            T("پیام‌رسانی شیک و مدرن برای کسانی که پیامک را جدی می‌گیرند؛ سریع، امن و بدون دردسر.",
                "Sleek, modern messaging for people who take SMS seriously — fast, secure and effortless."),
            color = t.subText, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Divider(t)
        AboutPoint(
            t, R.drawable.ic_shield, T("امنیت", "Security"),
            T("قفل با اثر انگشت و رمز عددی، پوشه خصوصی، هشدار لینک مشکوک و پیام‌هایی که فقط روی گوشی خودتان می‌مانند.",
                "Fingerprint & PIN lock, a private box, suspicious-link warnings, and messages that stay on your phone.")
        )
        AboutPoint(
            t, R.drawable.ic_bolt, T("سرعت", "Speed"),
            T("جستجوی فوری، کپی یک‌لمسی کد تأیید، پاسخ سریع از اعلان، دو سیم‌کارت و میانبرهای هوشمند.",
                "Instant search, one-tap code copy, quick reply from notifications, dual SIM and smart shortcuts.")
        )
        AboutPoint(
            t, R.drawable.ic_star, T("زیبایی", "Design"),
            T("۱۰ تم مدرن، رنگ و فونت دلخواه و رابطی ساده که حس سبکی می‌دهد.",
                "10 modern themes, your own colors and fonts, and a clean interface that feels light.")
        )
        AboutPoint(
            t, R.drawable.ic_chart, T("هوشمندی", "Smart"),
            T("دسته‌بندی خودکار پیام‌ها، خلاصه بانکی، یادآوری و پیام‌های زمان‌بندی‌شده.",
                "Automatic message categories, bank summaries, reminders and scheduled messages.")
        )
        Divider(t)
        Text(
            T("PSMS با این هدف ساخته شده که پیامک‌ها دوباره دلنشین و مرتب شوند. نظر و پیشنهاد شما مستقیم روی نسخه‌های بعدی اثر می‌گذارد.",
                "PSMS is built to make SMS pleasant and organised again. Your feedback goes straight into the next versions."),
            color = t.text, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Text(T("ارتباط با ما", "Contact us"), color = t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(t, CONTACT_EMAIL, false, R.drawable.ic_mail) {
                try {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                    i.putExtra(Intent.EXTRA_SUBJECT, "PSMS " + ver)
                    ctx.startActivity(i)
                } catch (e: Exception) { }
            }
            Box(Modifier.size(40.dp).glass(t, 20.dp).clickable {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("email", CONTACT_EMAIL))
                toast(ctx, T("ایمیل کپی شد", "Email copied"))
            }, contentAlignment = Alignment.Center) { Ic(R.drawable.ic_copy, t.text, 18.dp) }
        }
        Chip(t, T("سیاست حریم خصوصی", "Privacy policy"), false, R.drawable.ic_shield) { onPrivacy() }
        Text(T("ساخته‌شده با عشق در ایران", "Made with love in Iran"), color = t.subText, fontSize = 11.sp)
        Text("© PSMS", color = t.subText, fontSize = 10.sp)
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
    val timeout by Security.timeoutSec
    val hasPin by Security.hasPin
    val undo by Cfg.undoSecs
    val otpDays by Cfg.otpDays
    val quietOn by Cfg.quietOn
    val qs by Cfg.quietStart
    val qe by Cfg.quietEnd
    val arOn by Cfg.autoReplyOn
    val arText by Cfg.autoReplyText
    val hideLock by Cfg.hideLock
    val groupN by Cfg.groupNotifs
    val quietPromo by Cfg.quietPromo
    val spamRules by Cfg.spamRules
    val swStart by Cfg.swipeStart
    val swEnd by Cfg.swipeEnd
    val autoArch by Cfg.autoArchiveMonths
    val secure by Cfg.secureScreen
    val autoSim by Cfg.autoSimByOp
    val sig1 by Cfg.sig1
    val sig2 by Cfg.sig2
    val showBal by Cfg.showBalances
    val budget by Cfg.budget
    val abOn by Cfg.autoBackupOn
    val abUri by Cfg.autoBackupUri
    val abLast by Cfg.lastBackup
    val folders by Flags.folders
    val proActive by Pro.active
    val nBg = Palettes.backgrounds.size
    val nAc = Palettes.accents.size
    var notifFor by remember { mutableStateOf<Cat?>(null) }
    var pinMode by remember { mutableStateOf<String?>(null) }
    var pinCur by remember { mutableStateOf("") }
    var pinNew by remember { mutableStateOf("") }
    var pinNew2 by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf("") }
    var budgetText by remember(budget) { mutableStateOf(if (budget > 0) budget.toString() else "") }

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
                    toast(ctx, T("برای بازیابی، PSMS باید برنامه پیش‌فرض پیامک باشد", "PSMS must be the default SMS app to restore"))
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
    val csvAllL = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = withContext(Dispatchers.IO) {
                    try { Csv.exportMsgs(ctx, uri, Sms.loadAll(ctx)) } catch (e: Exception) { -1 }
                }
                toast(ctx, if (n >= 0) T("$n پیام ذخیره شد", "$n messages exported") else T("خروجی ناموفق بود", "Export failed"))
            }
        }
    }
    val treeL = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
            Cfg.setAutoBackupUri(uri.toString())
            Cfg.setAutoBackupOn(true)
        }
    }

    fun canLock(): Boolean =
        BiometricManager.from(ctx).canAuthenticate(lockAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS || Security.hasPin.value

    Column(Modifier.fillMaxSize()) {
        AppBar(t, T("تنظیمات", "Settings"), onBack = onBack)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!BuildConfig.STORE_BUILD) {
            Row(
                Modifier.fillMaxWidth().glass(t, 18.dp).clickable { onPage(Page.PRO) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_star, t.accent, 24.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PSMS Pro", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (proActive) T("فعال است", "Active") else T("امکانات حرفه‌ای را باز کنید", "Unlock professional features"),
                        color = if (proActive) t.accent else t.subText, fontSize = 12.sp
                    )
                }
                Ic(R.drawable.ic_chevron_down, t.subText, 20.dp, Modifier.padding(0.dp))
            }
            }

            Section(t, T("زبان", "Language"), R.drawable.ic_chat, true) {
                val cur = Langs.all.firstOrNull { it.code == Lang.code.value }
                Text(
                    T("زبان فعلی: ", "Current: ") + (cur?.native ?: "English"),
                    color = t.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                Text(
                    T("زبان پیش‌فرض برنامه همان زبان گوشی شماست. فارسی و انگلیسی کامل ترجمه شده‌اند؛ بقیه‌ی زبان‌ها فعلاً با متن انگلیسی نمایش داده می‌شوند و ترجمه‌شان در نسخه‌های بعدی اضافه می‌شود.",
                        "The default is your phone's language. Persian and English are fully translated; other languages show English text for now and will be translated in future versions."),
                    color = t.subText, fontSize = 12.sp
                )
                for (l in Langs.all) {
                    val sel = l.code == Lang.code.value
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            Cfg.setLang(l.code)
                            if (!l.full) toast(ctx, T("این زبان هنوز ترجمه نشده؛ فعلاً متن انگلیسی نمایش داده می‌شود", "This language is not translated yet; English text is shown for now"))
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            l.native, color = t.text, fontSize = 15.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
                        )
                        if (!l.full) Text(T("به‌زودی", "Soon"), color = t.subText, fontSize = 11.sp)
                        if (sel) {
                            Spacer(Modifier.size(8.dp))
                            Ic(R.drawable.ic_check_all, t.accent, 18.dp)
                        }
                    }
                }
                ActionBtn(t, T("استفاده از زبان گوشی", "Use phone language"), R.drawable.ic_chat) { Cfg.setLang(Langs.phoneDefault()) }
                ActionBtn(t, T("کمک به ترجمه (ایمیل)", "Help translate (email)"), R.drawable.ic_mail) {
                    try {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                        i.putExtra(Intent.EXTRA_SUBJECT, "PSMS translation")
                        ctx.startActivity(i)
                    } catch (e: Exception) { }
                }
            }

            Section(t, T("تم", "Theme"), R.drawable.ic_tune, true) {
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

            Section(t, T("رنگ اصلی", "Accent color"), R.drawable.ic_star, true) {
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
                Section(t, T("پس‌زمینه شیشه‌ای", "Glass background"), R.drawable.ic_tune, true) {
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
                        ) { Ic(R.drawable.ic_edit, Color.White, 20.dp) }
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

            Section(t, T("ظاهر لیست و پیام‌ها", "List & bubbles"), R.drawable.ic_chat, true) {
                SwitchRow(t, T("لیست فشرده", "Compact list"), raw.dense) { onChange(raw.copy(dense = it)) }
                LabeledSlider(t, T("گردی حباب پیام", "Bubble roundness"), raw.bubble, 4f..28f) { onChange(raw.copy(bubble = it)) }
                LabeledSlider(t, T("اندازه متن پیام‌ها", "Message text size"), raw.msgScale, 0.8f..1.6f) { onChange(raw.copy(msgScale = it)) }
            }

            Section(t, T("عمل‌های کشیدن انگشت روی لیست", "List swipe actions"), R.drawable.ic_archive, true) {
                Text(T("کشیدن به سمت شروع", "Swipe toward start"), color = t.subText, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in SWIPE_OPTIONS) Chip(t, swipeLabel(a), swStart == a, swipeIcon(a)) { Cfg.setSwipeStart(a) }
                }
                Text(T("کشیدن به سمت پایان", "Swipe toward end"), color = t.subText, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in SWIPE_OPTIONS) Chip(t, swipeLabel(a), swEnd == a, swipeIcon(a)) { Cfg.setSwipeEnd(a) }
                }
            }

            Section(t, T("متن و تاریخ", "Text & date"), R.drawable.ic_edit, true) {
                LabeledSlider(t, T("اندازه متن برنامه", "App text size"), raw.textScale, 0.8f..1.6f) { onChange(raw.copy(textScale = it)) }
                Text(T("فونت", "Font"), color = t.subText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palettes.fonts.forEachIndexed { i, n -> Chip(t, n, raw.fontIndex == i) { onChange(raw.copy(fontIndex = i)) } }
                }
                SwitchRow(t, T("تاریخ شمسی و ارقام فارسی", "Jalali dates & Persian digits"), raw.jalali) { onChange(raw.copy(jalali = it)) }
            }

            Section(t, T("صفحه اصلی", "Home screen"), R.drawable.ic_chat, true) {
                Text(T("بایگانی خودکار مکالمه‌های قدیمی (ماه)", "Auto-archive inactive conversations (months)"), color = t.subText, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(0, 3, 6, 12)) {
                        Chip(t, if (m == 0) T("خاموش", "Off") else Fmt.digits("$m", raw.jalali), autoArch == m) { Cfg.setAutoArchive(m) }
                    }
                }
                if (Pro.allowed("balances")) {
                    SwitchRow(t, T("نمایش موجودی حساب‌ها بالای لیست", "Show account balances on top"), showBal) { Cfg.setShowBalances(it) }
                } else {
                    LockedBtn(t, T("نمایش موجودی حساب‌ها", "Account balance cards")) { onPage(Page.PRO) }
                }
            }

            Section(t, T("اعلان‌ها", "Notifications"), R.drawable.ic_bolt, true) {
                SwitchRow(t, T("گروه‌بندی اعلان‌ها بر اساس دسته", "Group notifications by category"), groupN) { Cfg.setGroupNotifs(it) }
                SwitchRow(t, T("اعلان بی‌صدا برای تبلیغاتی، ناشناس و خدمات", "Silent notifications for promo, unknown & services"), quietPromo) { Cfg.setQuietPromo(it) }
                SwitchRow(t, T("پنهان کردن متن پیام در قفل صفحه", "Hide message text on lock screen"), hideLock) { Cfg.setHideLock(it) }
                Text(T("صدا، لرزش و رنگ هر دسته", "Sound, vibration and color per category"), color = t.subText, fontSize = 13.sp)
                for (c in listOf(Cat.CONTACTS, Cat.BANK, Cat.OTP, Cat.GOV, Cat.PROMO, Cat.UNKNOWN)) {
                    ActionBtn(t, c.label, c.icon) { if (Pro.gate(ctx, "notif_cat")) notifFor = c }
                }
                SwitchRow(t, T("ساعت سکوت", "Quiet hours"), quietOn) { Cfg.setQuietOn(it) }
                if (quietOn) {
                    LabeledSlider(t, T("شروع: ", "From: ") + Fmt.digits("$qs:00", raw.jalali), qs.toFloat(), 0f..23f) { Cfg.setQuietStart(it.roundToInt()) }
                    LabeledSlider(t, T("پایان: ", "To: ") + Fmt.digits("$qe:00", raw.jalali), qe.toFloat(), 0f..23f) { Cfg.setQuietEnd(it.roundToInt()) }
                }
            }

            Section(t, T("قوانین اسپم", "Spam rules"), R.drawable.ic_block, true) {
                Text(
                    T("پیام‌هایی که شماره یا متنشان شامل این کلمه‌ها باشد (با ویرگول جدا کنید) خودکار به اسپم می‌روند و اعلان نمی‌دهند.",
                        "Messages whose sender or text contains these words (comma-separated) go to Spam silently."),
                    color = t.subText, fontSize = 13.sp
                )
                GlassField(t, spamRules, { Cfg.setSpamRules(it) }, T("کلمه یا شماره…", "Words or numbers…"), Modifier.fillMaxWidth(), singleLine = false)
            }

            Section(t, T("ارسال", "Sending"), R.drawable.ic_send, true) {
                Text(T("تأخیر قبل از ارسال (امکان لغو)", "Delay before sending (undo)"), color = t.subText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in listOf(0, 5, 10)) {
                        Chip(t, if (s == 0) T("خاموش", "Off") else Fmt.digits("$s", raw.jalali) + T(" ثانیه", " s"), undo == s) { Cfg.setUndo(s) }
                    }
                }
                SwitchRow(t, T("انتخاب خودکار سیم‌کارت بر اساس اپراتور گیرنده", "Auto-pick SIM by recipient's operator"), autoSim) { Cfg.setAutoSim(it) }
                Text(T("امضای پایان پیام", "Message signature"), color = t.subText, fontSize = 13.sp)
                GlassField(t, sig1, { Cfg.setSig1(it) }, T("امضای سیم ۱ (اختیاری)", "SIM 1 signature (optional)"), Modifier.fillMaxWidth())
                GlassField(t, sig2, { Cfg.setSig2(it) }, T("امضای سیم ۲ (اختیاری)", "SIM 2 signature (optional)"), Modifier.fillMaxWidth())
                SwitchRow(t, T("پاسخ خودکار به مخاطبین", "Auto-reply to contacts"), arOn) { Cfg.setAutoReplyOn(it) }
                if (arOn) {
                    GlassField(t, arText, { Cfg.setAutoReplyText(it) }, T("متن پاسخ خودکار", "Auto-reply text"), Modifier.fillMaxWidth(), singleLine = false)
                }
                ActionBtn(t, T("پیام‌های آماده", "Templates"), R.drawable.ic_bolt) { onPage(Page.TEMPLATES) }
            }

            Section(t, T("پوشه‌های دلخواه", "Custom folders"), R.drawable.ic_folder, true) {
                if (!Pro.allowed("folders")) {
                    LockedBtn(t, T("پوشه‌های دلخواه", "Custom folders")) { onPage(Page.PRO) }
                } else {
                    for (f in folders) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(R.drawable.ic_folder, t.accent, 18.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(f, color = t.text, modifier = Modifier.weight(1f))
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable { Flags.removeFolder(f) }, contentAlignment = Alignment.Center) {
                                Ic(R.drawable.ic_close, t.subText, 16.dp)
                            }
                        }
                    }
                    GlassField(t, newFolder, { newFolder = it }, T("نام پوشه جدید", "New folder name"), Modifier.fillMaxWidth())
                    ActionBtn(t, T("افزودن پوشه", "Add folder"), R.drawable.ic_plus) {
                        if (newFolder.isNotBlank()) { Flags.addFolder(newFolder); newFolder = "" }
                    }
                }
            }

            Section(t, T("پاک‌سازی خودکار", "Auto cleanup"), R.drawable.ic_trash, true) {
                Text(T("حذف کدهای تأیید قدیمی‌تر از…", "Delete verification codes older than…"), color = t.subText, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in listOf(0, 1, 3, 7, 30)) {
                        Chip(t, if (d == 0) T("خاموش", "Off") else Fmt.digits("$d", raw.jalali) + T(" روز", " d"), otpDays == d) { Cfg.setOtpDays(d) }
                    }
                }
            }

            Section(t, T("امنیت", "Security"), R.drawable.ic_lock, true) {
                SwitchRow(t, T("قفل برنامه", "App lock"), lockOn) { on ->
                    if (on) {
                        if (canLock()) Security.setEnabled(true)
                        else toast(ctx, T("اول رمز عددی بسازید یا اثر انگشت / قفل صفحه گوشی را فعال کنید", "Set a PIN first, or set up a fingerprint / screen lock on the phone"))
                    } else {
                        Security.setEnabled(false)
                    }
                }
                Text(T("قفل‌شدن بعد از خروج از برنامه", "Lock after leaving the app"), color = t.subText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t, T("فوری", "Immediately"), timeout == 0) { Security.setTimeout(0) }
                    Chip(t, T("۱ دقیقه", "1 min"), timeout == 60) { Security.setTimeout(60) }
                    Chip(t, T("۵ دقیقه", "5 min"), timeout == 300) { Security.setTimeout(300) }
                }
                if (!hasPin) {
                    ActionBtn(t, T("ساخت رمز عددی (PIN)", "Set a PIN"), R.drawable.ic_key) { pinMode = "set"; pinCur = ""; pinNew = ""; pinNew2 = "" }
                } else {
                    ActionBtn(t, T("تغییر رمز عددی", "Change PIN"), R.drawable.ic_key) { pinMode = "change"; pinCur = ""; pinNew = ""; pinNew2 = "" }
                    ActionBtn(t, T("حذف رمز عددی", "Remove PIN"), R.drawable.ic_trash) { pinMode = "remove"; pinCur = ""; pinNew = ""; pinNew2 = "" }
                }
                SwitchRow(t, T("پنهان کردن برنامه در لیست برنامه‌های اخیر و ممانعت از اسکرین‌شات", "Hide app in recents & block screenshots"), secure) { Cfg.setSecureScreen(it) }
            }

            Section(t, T("دسته‌بندی", "Categories"), R.drawable.ic_label, true) {
                ActionBtn(t, T("ویرایش کلمات دسته‌بندی", "Edit category keywords"), R.drawable.ic_edit) { onPage(Page.RULES) }
            }

            Section(t, T("بودجه و بانک", "Bank & budget"), R.drawable.ic_bank, true) {
                if (Pro.allowed("budget")) {
                    Text(T("اگر هزینه ماه از این مبلغ بیشتر شود هشدار می‌گیرید (۰ = خاموش)", "Get an alert when this month's spending passes this amount (0 = off)"), color = t.subText, fontSize = 13.sp)
                    GlassField(
                        t, budgetText,
                        { v ->
                            val d = Fmt.norm(v).filter { it.isDigit() }.take(13)
                            budgetText = d
                            Cfg.setBudget(d.toLongOrNull() ?: 0L)
                        },
                        T("بودجه ماهانه (ریال)", "Monthly budget"), Modifier.fillMaxWidth()
                    )
                } else {
                    LockedBtn(t, T("بودجه ماهانه و هشدار", "Monthly budget & alert")) { onPage(Page.PRO) }
                }
                ActionBtn(t, T("خلاصه پیام‌های بانکی", "Bank summary"), R.drawable.ic_chart) { onPage(Page.BANK) }
            }

            Section(t, T("پشتیبان‌گیری", "Backup"), R.drawable.ic_archive, true) {
                ActionBtn(t, T("ذخیره‌ی همه‌ی پیام‌ها در فایل", "Save all messages to a file"), R.drawable.ic_download) { exportL.launch("psms-backup.json") }
                ActionBtn(t, T("بازیابی پیام‌ها از فایل", "Restore messages from a file"), R.drawable.ic_archive) { importL.launch(arrayOf("application/json", "*/*")) }
                ActionBtn(t, T("خروجی CSV (اکسل) از همه پیام‌ها", "Export all messages as CSV (Excel)"), R.drawable.ic_chart) {
                    if (Pro.gate(ctx, "csv")) csvAllL.launch("psms-messages.csv")
                }
                if (Pro.allowed("autobackup")) {
                    SwitchRow(t, T("پشتیبان‌گیری خودکار روزانه", "Daily automatic backup"), abOn) { on ->
                        if (on && abUri.isEmpty()) treeL.launch(null) else Cfg.setAutoBackupOn(on)
                    }
                    if (abOn || abUri.isNotEmpty()) {
                        ActionBtn(t, T("انتخاب پوشه‌ی پشتیبان", "Choose backup folder"), R.drawable.ic_folder) { treeL.launch(null) }
                        Text(
                            T("هر روز یک بار هنگام باز شدن برنامه ذخیره می‌شود. آخرین: ", "Saved once a day when the app opens. Last: ") +
                                (if (abLast > 0) Fmt.time(abLast, raw, withDate = true) else "—"),
                            color = t.subText, fontSize = 11.sp
                        )
                    }
                } else {
                    LockedBtn(t, T("پشتیبان‌گیری خودکار", "Automatic backup")) { onPage(Page.PRO) }
                }
            }

            if (!isDefault) {
                ActionBtn(t, T("تنظیم به‌عنوان برنامه پیش‌فرض پیامک", "Set as default SMS app"), R.drawable.ic_chat) { onDefault() }
            }

            AboutCard(t) { onPage(Page.PRIVACY) }
            Spacer(Modifier.size(16.dp))
        }
    }

    val nf = notifFor
    if (nf != null) {
        NotifEditor(t, "c:${nf.name}", nf.label) { notifFor = null }
    }

    val pm = pinMode
    if (pm != null) {
        AlertDialog(
            onDismissRequest = { pinMode = null },
            title = {
                Text(
                    when (pm) {
                        "set" -> T("ساخت رمز عددی", "Set a PIN")
                        "change" -> T("تغییر رمز عددی", "Change PIN")
                        else -> T("حذف رمز عددی", "Remove PIN")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pm != "set") GlassField(t, pinCur, { pinCur = it.filter { c -> c.isDigit() }.take(8) }, T("رمز فعلی", "Current PIN"), Modifier.fillMaxWidth())
                    if (pm != "remove") {
                        GlassField(t, pinNew, { pinNew = it.filter { c -> c.isDigit() }.take(8) }, T("رمز جدید (۴ تا ۸ رقم)", "New PIN (4–8 digits)"), Modifier.fillMaxWidth())
                        GlassField(t, pinNew2, { pinNew2 = it.filter { c -> c.isDigit() }.take(8) }, T("تکرار رمز جدید", "Repeat new PIN"), Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var ok = true
                    if (pm != "set" && !Security.checkPin(pinCur)) {
                        toast(ctx, T("رمز فعلی اشتباه است", "Current PIN is wrong")); ok = false
                    }
                    if (ok && pm != "remove") {
                        if (pinNew.length < 4) { toast(ctx, T("رمز باید حداقل ۴ رقم باشد", "PIN must be at least 4 digits")); ok = false }
                        else if (pinNew != pinNew2) { toast(ctx, T("تکرار رمز یکسان نیست", "PINs do not match")); ok = false }
                    }
                    if (ok) {
                        if (pm == "remove") {
                            Security.clearPin()
                            if (Security.enabled.value && !canLock()) Security.setEnabled(false)
                        } else {
                            Security.setPin(pinNew)
                        }
                        pinMode = null
                    }
                }) { Text(T("تأیید", "OK")) }
            },
            dismissButton = { TextButton(onClick = { pinMode = null }) { Text(T("انصراف", "Cancel")) } }
        )
    }
}
