package com.passenger.sms

enum class Cat(val label: String, val emoji: String) {
    ALL("همه", "💬"),
    CONTACTS("مخاطبین", "👤"),
    BANK("بانکی", "🏦"),
    OTP("کد تأیید", "🔑"),
    PROMO("تبلیغاتی", "📣"),
    UNKNOWN("ناشناس", "❓"),
    ARCHIVE("بایگانی", "🗄"),
    SPAM("اسپم", "🚫")
}

data class Msg(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean
)

data class Conv(
    val key: String,
    val address: String,
    val name: String?,
    val last: Msg,
    val count: Int,
    val unread: Int,
    val cat: Cat
)

object Classifier {
    private val serviceBases = listOf("1000", "2000", "3000", "5000", "9000", "9820", "9830")

    private fun hit(body: String, words: List<String>?): Boolean =
        words != null && words.any { it.isNotEmpty() && body.contains(it, ignoreCase = true) }

    private fun isShortOrServiceNumber(address: String): Boolean {
        if (address.any { it.isLetter() }) return false
        val d = address.filter { it.isDigit() }
        if (d.isEmpty()) return false
        if (!address.startsWith("+") && !address.startsWith("0") && d.length <= 8) return true
        val n = d.removePrefix("98").removePrefix("0")
        return serviceBases.any { n.startsWith(it) } && !address.startsWith("09")
    }

    fun classify(address: String, body: String, isContact: Boolean, override: Cat?): Cat {
        if (override != null) return override
        if (isContact) return Cat.CONTACTS
        val r = Rules.map.value
        if (hit(body, r[Cat.OTP])) return Cat.OTP
        if (hit(body, r[Cat.BANK])) return Cat.BANK
        if (hit(body, r[Cat.PROMO]) || isShortOrServiceNumber(address)) return Cat.PROMO
        return Cat.UNKNOWN
    }
}
