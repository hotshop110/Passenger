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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        Store.pendingOpen.value = addressFrom(intent)
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
        Store.pendingOpen.value = addressFrom(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
    }

    private fun addressFrom(i: Intent?): String? =
        i?.data?.schemeSpecificPart?.substringBefore("?")?.takeIf { it.isNotBlank() }

    private fun prompt(onOk: () -> Unit) {
        if (prompting) return
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Passenger")
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

    /** App lock prompt. If no biometrics / screen lock exist, the app is not locked out. */
    fun authenticate() {
        if (BiometricManager.from(this).canAuthenticate(lockAuthenticators()) != BiometricManager.BIOMETRIC_SUCCESS) {
            Security.locked.value = false
            return
        }
        prompt { Security.locked.value = false }
    }

    /** Authentication before opening the private box. */
    fun authenticateThen(onOk: () -> Unit) {
        if (BiometricManager.from(this).canAuthenticate(lockAuthenticators()) != BiometricManager.BIOMETRIC_SUCCESS) {
            toast(this, T("ابتدا در تنظیمات گوشی اثر انگشت یا قفل صفحه تعریف کنید", "Set up a fingerprint or screen lock in phone settings first"))
            return
        }
        prompt(onOk)
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

enum class Page { HOME, SETTINGS, RULES, TEMPLATES, SEARCH, STARRED, SCHEDULED, TRASH, BANK, BULK, PRIVATE }

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

    var isDefault by remember { mutableStateOf(Sms.isDefault(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(Page.HOME) }
    val locked by Security.locked

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
        }
    }

    LaunchedEffect(Unit) {
        if (PERMS.all { Sms.granted(ctx, it) }) Store.refresh(ctx) else permLauncher.launch(PERMS)
    }
    val pending by Store.pendingOpen
    LaunchedEffect(pending) {
        if (pending != null) { open = pending; Store.pendingOpen.value = null }
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
                        LockScreen(t) { (ctx as? MainActivity)?.authenticate() }
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
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
