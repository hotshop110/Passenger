package com.passenger.sms

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** PSMS Pro: features listed in [LOCKED] need an activation code made by the developer for the device ID. */
object Pro {
    private const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEVk4aUDlH10rfX8GUxyWQl1HUyY6RrYICSBybwcX51G+ioeEa8sou4MFw4qD8ZEWVZkJk70/s/4uET6i6cbIhwA=="

    /** Features that require PSMS Pro. Remove an id from this set to make that feature free. */
    val LOCKED = setOf(
        "stats", "budget", "balances", "pdf", "csv", "autobackup", "repeat",
        "bulk_csv", "bulk_groups", "private", "folders", "notif_cat", "cardimage", "cleanup"
    )

    val active = mutableStateOf(false)
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("pro", Context.MODE_PRIVATE)
        sp = p
        active.value = verify(ctx, p.getString("key", "") ?: "")
    }

    fun deviceId(ctx: Context): String {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val d = MessageDigest.getInstance("SHA-256").digest(("psms|" + id).toByteArray(Charsets.UTF_8))
        val hex = StringBuilder()
        for (b in d) hex.append(String.format("%02X", b))
        val s = hex.substring(0, 10)
        return s.substring(0, 5) + "-" + s.substring(5)
    }

    fun verify(ctx: Context, key: String): Boolean {
        if (key.isBlank()) return false
        return try {
            val pk = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT)))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pk)
            sig.update(("PSMS|" + deviceId(ctx)).toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(key.trim(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        } catch (e: Exception) { false }
    }

    fun activate(ctx: Context, key: String): Boolean {
        if (!verify(ctx, key)) return false
        sp?.edit()?.putString("key", key.trim())?.apply()
        active.value = true
        return true
    }

    fun deactivate() {
        sp?.edit()?.remove("key")?.apply()
        active.value = false
    }

    fun allowed(feature: String): Boolean = active.value || feature !in LOCKED

    /** Returns true when allowed; otherwise opens the PSMS Pro page. */
    fun gate(ctx: Context, feature: String): Boolean {
        if (allowed(feature)) return true
        toast(ctx, T("این امکان در نسخه PSMS Pro فعال می‌شود", "This feature is part of PSMS Pro"))
        Store.pendingPage.value = "PRO"
        return false
    }
}
