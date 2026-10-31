package com.passenger.sms

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

object Fmt {
    val faMonths = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val enMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun digits(s: String, persian: Boolean): String {
        if (!persian) return s
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '0'..'9') sb.append('۰' + (ch - '0')) else sb.append(ch)
        }
        return sb.toString()
    }

    /** Persian/Arabic digits to ASCII, Arabic thousand separator to comma */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                '٬' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + (if (days < 186) days % 31 else (days - 186) % 30)
        return intArrayOf(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668 + (365 * jy2) + ((jy2 / 33) * 8) + (((jy2 % 33) + 3) / 4) + jd +
            (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    fun ymd(ts: Long, jalali: Boolean): IntArray {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return if (jalali) gregorianToJalali(y, m, d) else intArrayOf(y, m, d)
    }

    fun midnight(y: Int, m: Int, d: Int, jalali: Boolean): Long {
        val g = if (jalali) jalaliToGregorian(y, m, d) else intArrayOf(y, m, d)
        val cal = Calendar.getInstance()
        cal.set(g[0], g[1] - 1, g[2], 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun daysIn(y: Int, m: Int, jalali: Boolean): Int {
        if (jalali) return if (m <= 6) 31 else if (m <= 11) 30 else 29
        return GregorianCalendar(y, m - 1, 1).getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun monthName(m: Int, jalali: Boolean): String {
        val i = (m - 1).coerceIn(0, 11)
        return if (jalali) faMonths[i] else enMonths[i]
    }

    fun time(ts: Long, t: GlassTheme, withDate: Boolean = false): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val hm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val recent = System.currentTimeMillis() - ts < 24L * 3600 * 1000
        val out: String
        if (recent && !withDate) {
            out = hm
        } else {
            val j = ymd(ts, t.jalali)
            val date = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
            out = if (withDate) "$date  $hm" else date
        }
        return digits(out, t.jalali)
    }

    fun date(ts: Long, t: GlassTheme): String {
        val j = ymd(ts, t.jalali)
        return digits(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]), t.jalali)
    }
}
