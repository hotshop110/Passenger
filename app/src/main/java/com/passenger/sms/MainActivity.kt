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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { Store.refresh(applicationContext) }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.pendingOpen.value = addressFrom(intent)
        try {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) { }
        setContent { App() }
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
}

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

@Composable
fun App() {
    val ctx = LocalContext.current
    val themeStore = remember { ThemeStore(ctx) }
    val t = themeStore.state
    var isDefault by remember { mutableStateOf(Sms.isDefault(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }

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
        if (pending != null) { open = pending; settings = false; Store.pendingOpen.value = null }
    }

    BackHandler(enabled = settings || open != null) {
        if (settings) settings = false else open = null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            GlassBackground(t) {
                when {
                    settings -> SettingsScreen(
                        t = t,
                        onChange = { themeStore.set(it) },
                        onBack = { settings = false },
                        isDefault = isDefault,
                        onDefault = { requestDefaultSms(ctx, roleLauncher) }
                    )
                    open != null -> ThreadScreen(t, open!!) { open = null }
                    else -> HomeScreen(
                        t = t,
                        isDefault = isDefault,
                        onDefault = { requestDefaultSms(ctx, roleLauncher) },
                        onOpen = { open = it },
                        onSettings = { settings = true }
                    )
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
