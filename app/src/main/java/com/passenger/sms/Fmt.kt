package com.passenger.sms

import java.util.Calendar
import java.util.Locale

object Fmt {
    fun digits(s: String, persian: Boolean): String {
        if (!persian) return s
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '0'..'9') sb.append('۰' + (ch - '0')) else sb.append(ch)
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

    fun time(ts: Long, t: GlassTheme, withDate: Boolean = false): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val hm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val recent = System.currentTimeMillis() - ts < 24L * 3600 * 1000
        val out: String
        if (recent && !withDate) {
            out = hm
        } else {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val date = if (t.jalali) {
                val j = gregorianToJalali(y, m, d)
                String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
            } else {
                String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
            }
            out = if (withDate) "$date  $hm" else date
        }
        return digits(out, t.jalali)
    }
}
