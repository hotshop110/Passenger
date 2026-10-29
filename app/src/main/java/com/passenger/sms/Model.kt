package com.passenger.sms

enum class Cat(val label: String, val emoji: String) {
    ALL("همه", "💬"),
    CONTACTS("مخاطبین", "👤"),
    BANK("بانکی", "🏦"),
    OTP("کد تأیید", "🔑"),
    PROMO("تبلیغاتی", "📣"),
    UNKNOWN("ناشناس", "❓")
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
    private val otp = Regex(
        "(کد\\s*(تایید|تأیید|فعال|ورود|یکبار|یک\\s*بار|امنیتی)|رمز\\s*(یکبار|یک\\s*بار|پویا|دوم|ورود)|verification|OTP|one.?time|\\bcode\\b)",
        RegexOption.IGNORE_CASE
    )
    private val bank = Regex("(مانده|موجودی|واریز|برداشت|تراکنش|بانک|کارت|حساب|انتقال|پرداخت|مبلغ|ریال|پایا|ساتنا)")
    private val promo = Regex("(لغو\\s*[۱1]\\s*[۱1]|لغو\\s*عضویت|تخفیف|جشنواره|فروش\\s*ویژه|کد\\s*تخفیف|عضویت|اپلیکیشن|دانلود)")
    private val serviceBases = listOf("1000", "2000", "3000", "5000", "9000", "9820", "9830")

    private fun isShortOrServiceNumber(address: String): Boolean {
        if (address.any { it.isLetter() }) return false
        val d = address.filter { it.isDigit() }
        if (d.isEmpty()) return false
        if (!address.startsWith("+") && !address.startsWith("0") && d.length <= 8) return true
        val n = d.removePrefix("98").removePrefix("0")
        return serviceBases.any { n.startsWith(it) } && !address.startsWith("09")
    }

    fun classify(address: String, body: String, isContact: Boolean): Cat {
        if (isContact) return Cat.CONTACTS
        if (otp.containsMatchIn(body)) return Cat.OTP
        if (bank.containsMatchIn(body)) return Cat.BANK
        if (promo.containsMatchIn(body) || isShortOrServiceNumber(address)) return Cat.PROMO
        return Cat.UNKNOWN
    }
}
