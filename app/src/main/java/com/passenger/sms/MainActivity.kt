package com.passenger.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var prompting = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { Store.refresh(applicationContext) }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Boot.init(this)
        handleIntent(intent)
        try {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) { }
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        Security.onStart()
    }

    override fun onStop() {
        super.onStop()
        Security.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            "com.passenger.sms.NEW" -> Store.pendingPage.value = "NEW"
            "com.passenger.sms.SEARCH" -> Store.pendingPage.value = "SEARCH"
            "com.passenger.sms.PRIVATE" -> Store.pendingPage.value = "PRIVATE"
            Intent.ACTION_SEND -> {
                val txt = i.getStringExtra(Intent.EXTRA_TEXT)
                if (!txt.isNullOrBlank()) {
                    Store.sharedText.value = txt
                    Store.pendingPage.value = "HOME"
                }
            }
            else -> {
                val addr = i.data?.schemeSpecificPart?.substringBefore("?")?.takeIf { it.isNotBlank() }
                if (addr != null) Store.pendingOpen.value = addr
                val body = i.getStringExtra("sms_body")
                if (!body.isNullOrBlank()) Store.sharedText.value = body
            }
        }
    }

    private fun biometricOk(): Boolean =
        BiometricManager.from(this).canAuthenticate(lockAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    private fun prompt(onOk: () -> Unit) {
        if (prompting) return
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("PSMS")
            .setSubtitle(T("برای ادامه تأیید هویت کنید", "Authenticate to continue"))
            .setAllowedAuthenticators(lockAuthenticators())
        if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText(T("لغو", "Cancel"))
        prompting = true
        val p = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    onOk()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            }
        )
        p.authenticate(builder.build())
    }

    /** App lock: biometric / phone lock if available; a PIN (if set) is always accepted on the lock screen. */
    fun authenticate() {
        if (!biometricOk()) {
            if (!Security.hasPin.value) Security.locked.value = false
            return
        }
        prompt { Security.locked.value = false }
    }

    /** Authentication before opening the private box. */
    fun authenticateThen(onOk: () -> Unit) {
        if (biometricOk()) {
            prompt(onOk)
        } else if (Security.hasPin.value) {
            Store.pinCallback.value = onOk
        } else {
            toast(this, T("ابتدا رمز عددی بسازید یا اثر انگشت / قفل صفحه گوشی را فعال کنید", "Set a PIN, or set up a fingerprint / screen lock on the phone first"))
        }
    }
}

fun lockAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30)
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    else BiometricManager.Authenticators.BIOMETRIC_WEAK

private val PERMS = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun requestDefaultSms(ctx: Context, launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
            launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
    } else {
        launcher.launch(
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
        )
    }
}

enum class Page { HOME, SETTINGS, RULES, TEMPLATES, SEARCH, STARRED, SCHEDULED, TRASH, BANK, BULK, PRIVATE, STATS, CLEANUP, PRO }

@Composable
fun App() {
    val ctx = LocalContext.current
    val themeStore = remember { ThemeStore(ctx) }
    val raw = themeStore.state
    val t = raw.resolve(isSystemInDarkTheme())
    val font = fontFor(raw.fontIndex)
    val baseDensity = LocalDensity.current
    val scaled = Density(baseDensity.density, baseDensity.fontScale * raw.textScale)
    val view = LocalView.current
    val secure by Cfg.secureScreen

    var isDefault by remember { mutableStateOf(Sms.isDefault(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(Page.HOME) }
    val locked by Security.locked
    val pinCb by Store.pinCallback
    var pinText by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { Store.refresh(ctx) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = Sms.isDefault(ctx)
        Store.refresh(ctx)
    }

    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = if (t.flat) t.barColor.luminance() > 0.5f else t.light
            c.isAppearanceLightNavigationBars = t.light
            if (secure) w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(Unit) {
        if (PERMS.all { Sms.granted(ctx, it) }) Store.refresh(ctx) else permLauncher.launch(PERMS)
    }
    val pending by Store.pendingOpen
    LaunchedEffect(pending) {
        if (pending != null) { open = pending; Store.pendingOpen.value = null }
    }
    val pendingPage by Store.pendingPage
    LaunchedEffect(pendingPage) {
        when (pendingPage) {
            "SEARCH" -> { open = null; page = Page.SEARCH; Store.pendingPage.value = null }
            "PRO" -> { open = null; page = Page.PRO; Store.pendingPage.value = null }
            "HOME" -> { open = null; page = Page.HOME; Store.pendingPage.value = null }
            "PRIVATE" -> {
                open = null
                Store.pendingPage.value = null
                if (Pro.gate(ctx, "private")) {
                    (ctx as? MainActivity)?.authenticateThen { page = Page.PRIVATE }
                }
            }
            "NEW" -> { open = null; page = Page.HOME }
        }
    }
    LaunchedEffect(locked) {
        if (locked) (ctx as? MainActivity)?.authenticate()
    }

    BackHandler(enabled = page != Page.HOME || open != null) {
        if (open != null) {
            open = null
        } else if (page == Page.RULES || page == Page.TEMPLATES) {
            page = Page.SETTINGS
        } else {
            page = Page.HOME
        }
    }

    val openThread: (String) -> Unit = { open = it }
    val goHome: () -> Unit = { page = Page.HOME }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (Lang.isFa) LayoutDirection.Rtl else LayoutDirection.Ltr),
        LocalDensity provides scaled,
        LocalAppFont provides font
    ) {
        MaterialTheme {
            ProvideTextStyle(TextStyle(fontFamily = font)) {
                GlassBackground(t) {
                    if (locked) {
                        LockScreen(
                            t,
                            onBiometric = { (ctx as? MainActivity)?.authenticate() },
                            onPin = { pin ->
                                if (Security.checkPin(pin)) { Security.locked.value = false; true } else false
                            }
                        )
                    } else if (open != null) {
                        ThreadScreen(t, open!!) { open = null }
                    } else {
                        when (page) {
                            Page.SETTINGS -> SettingsScreen(
                                raw = raw, t = t,
                                onChange = { themeStore.set(it) },
                                onBack = goHome,
                                isDefault = isDefault,
                                onDefault = { requestDefaultSms(ctx, roleLauncher) },
                                onPage = { page = it }
                            )
                            Page.RULES -> RulesPage(t) { page = Page.SETTINGS }
                            Page.TEMPLATES -> TemplatesPage(t) { page = Page.SETTINGS }
                            Page.SEARCH -> SearchPage(t, goHome, openThread)
                            Page.STARRED -> StarredPage(t, goHome, openThread)
                            Page.SCHEDULED -> ScheduledPage(t, goHome)
                            Page.TRASH -> TrashPage(t, goHome)
                            Page.BANK -> BankPage(t, goHome)
                            Page.BULK -> BulkPage(t, goHome)
                            Page.PRIVATE -> PrivatePage(t, goHome, openThread)
                            Page.STATS -> StatsPage(t, goHome)
                            Page.CLEANUP -> CleanupPage(t, goHome)
                            Page.PRO -> ProPage(t, goHome)
                            Page.HOME -> HomeScreen(
                                t = t,
                                isDefault = isDefault,
                                onDefault = { requestDefaultSms(ctx, roleLauncher) },
                                onOpen = openThread,
                                onPage = { page = it },
                                onPrivate = { (ctx as? MainActivity)?.authenticateThen { page = Page.PRIVATE } }
                            )
                        }
                    }

                    val cb = pinCb
                    if (cb != null) {
                        AlertDialog(
                            onDismissRequest = { Store.pinCallback.value = null; pinText = "" },
                            title = { Text(T("رمز عددی را وارد کنید", "Enter your PIN")) },
                            text = {
                                GlassField(t, pinText, { pinText = it.filter { c -> c.isDigit() }.take(8) }, T("رمز عددی", "PIN"), Modifier)
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (Security.checkPin(pinText)) {
                                        Store.pinCallback.value = null
                                        pinText = ""
                                        cb()
                                    } else {
                                        toast(ctx, T("رمز اشتباه است", "Wrong PIN"))
                                        pinText = ""
                                    }
                                }) { Text(T("تأیید", "OK")) }
                            },
                            dismissButton = { TextButton(onClick = { Store.pinCallback.value = null; pinText = "" }) { Text(T("انصراف", "Cancel")) } }
                        )
                    }
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
