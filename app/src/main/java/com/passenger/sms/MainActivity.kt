package com.passenger.sms

import android.Manifest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var prompting = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { Store.refresh(applicationContext) }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Security.init(this)
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

    fun authenticate() {
        if (prompting) return
        val auth = lockAuthenticators()
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            Security.locked.value = false
            return
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Passenger")
            .setSubtitle("برای ورود تأیید هویت کنید")
            .setAllowedAuthenticators(auth)
        if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText("لغو")
        prompting = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    Security.locked.value = false
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            }
        )
        prompt.authenticate(builder.build())
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

enum class Page { HOME, SETTINGS, RULES }

@Composable
fun App() {
    val ctx = LocalContext.current
    remember { Flags.init(ctx); Rules.init(ctx); Security.init(ctx); 0 }
    val themeStore = remember { ThemeStore(ctx) }
    val raw = themeStore.state
    val t = raw.resolve(isSystemInDarkTheme())
    val font = fontFor(raw.fontIndex)
    val baseDensity = LocalDensity.current
    val scaled = Density(baseDensity.density, baseDensity.fontScale * raw.textScale)

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

    LaunchedEffect(Unit) {
        if (PERMS.all { Sms.granted(ctx, it) }) Store.refresh(ctx) else permLauncher.launch(PERMS)
    }
    val pending by Store.pendingOpen
    LaunchedEffect(pending) {
        if (pending != null) { open = pending; page = Page.HOME; Store.pendingOpen.value = null }
    }
    LaunchedEffect(locked) {
        if (locked) (ctx as? MainActivity)?.authenticate()
    }

    BackHandler(enabled = page != Page.HOME || open != null) {
        if (page == Page.RULES) page = Page.SETTINGS
        else if (page == Page.SETTINGS) page = Page.HOME
        else open = null
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides scaled,
        LocalAppFont provides font
    ) {
        MaterialTheme {
            ProvideTextStyle(TextStyle(fontFamily = font)) {
                GlassBackground(t) {
                    if (locked) {
                        LockScreen(t) { (ctx as? MainActivity)?.authenticate() }
                    } else when {
                        page == Page.SETTINGS -> SettingsScreen(
                            raw = raw, t = t,
                            onChange = { themeStore.set(it) },
                            onBack = { page = Page.HOME },
                            isDefault = isDefault,
                            onDefault = { requestDefaultSms(ctx, roleLauncher) },
                            onRules = { page = Page.RULES }
                        )
                        page == Page.RULES -> RulesScreen(t) { page = Page.SETTINGS }
                        open != null -> ThreadScreen(t, open!!) { open = null }
                        else -> HomeScreen(
                            t = t,
                            isDefault = isDefault,
                            onDefault = { requestDefaultSms(ctx, roleLauncher) },
                            onOpen = { open = it },
                            onSettings = { page = Page.SETTINGS }
                        )
                    }
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
