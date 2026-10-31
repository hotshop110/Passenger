package com.passenger.sms

import androidx.compose.ui.graphics.ImageBitmap

enum class Cat(val fa: String, val en: String, val emoji: String) {
    ALL("همه", "All", "💬"),
    CONTACTS("مخاطبین", "Contacts", "👤"),
    BANK("بانکی", "Bank", "🏦"),
    OTP("کد تأیید", "Codes", "🔑"),
    GOV("خدمات دولتی", "Services", "🏛"),
    PROMO("تبلیغاتی", "Promo", "📣"),
    UNKNOWN("ناشناس", "Unknown", "❓"),
    ARCHIVE("بایگانی", "Archive", "🗄"),
    SPAM("اسپم", "Spam", "🚫");

    val label: String get() = T(fa, en)
}

data class Msg(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val subId: Int = -1,
    val status: Int = -1
)

data class Conv(
    val key: String,
    val address: String,
    val name: String?,
    val last: Msg,
    val count: Int,
    val unread: Int,
    val cat: Cat,
    val subId: Int,
    val photo: ImageBitmap?
)

object Classifier {
    private val serviceBases = listOf("1000", "2000", "3000", "5000", "9000", "9820", "9830")
    private val codeRe = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    private fun hit(body: String, words: List<String>?): Boolean =
        words != null && words.any { it.isNotEmpty() && body.contains(it, ignoreCase = true) }

    fun isOtp(body: String) = hit(body, Rules.map.value[Cat.OTP])
    fun isBank(body: String) = hit(body, Rules.map.value[Cat.BANK])

    fun extractCode(body: String): String? = codeRe.find(Fmt.norm(body))?.value

    private fun isShortOrServiceNumber(address: String): Boolean {
        if (address.any { it.isLetter() }) return false
        val d = address.filter { it.isDigit() }
        if (d.isEmpty()) return false
        if (!address.startsWith("+") && !address.startsWith("0") && d.length <= 8) return true
        val n = d.removePrefix("98").removePrefix("0")
        return serviceBases.any { n.startsWith(it) } && !address.startsWith("09")
    }

    fun classify(address: String, body: String, isContact: Boolean, forced: Cat?): Cat {
        if (forced != null) return forced
        if (isContact) return Cat.CONTACTS
        val r = Rules.map.value
        if (hit(body, r[Cat.OTP])) return Cat.OTP
        if (hit(body, r[Cat.BANK])) return Cat.BANK
        if (hit(body, r[Cat.GOV])) return Cat.GOV
        if (hit(body, r[Cat.PROMO]) || isShortOrServiceNumber(address)) return Cat.PROMO
        return Cat.UNKNOWN
    }
}
