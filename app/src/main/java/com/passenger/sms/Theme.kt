package com.passenger.sms

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class BgPalette(val name: String, val colors: List<Color>)

class FlatTheme(
    val fa: String, val en: String,
    val bg: Color, val bar: Color, val barText: Color,
    val text: Color, val sub: Color, val accent: Color,
    val surface: Color, val divider: Color, val barIsPrimary: Boolean
)

class ChatBg(val fa: String, val en: String, val colors: List<Color>, val text: Color)

object Palettes {
    val backgrounds = listOf(
        BgPalette("اقیانوس", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
        BgPalette("غروب", listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))),
        BgPalette("جنگل", listOf(Color(0xFF0B3D2E), Color(0xFF134E4A), Color(0xFF1F7A5C))),
        BgPalette("نیمه‌شب", listOf(Color(0xFF000000), Color(0xFF1B1B2F), Color(0xFF2D2D44))),
        BgPalette("بنفش", listOf(Color(0xFF2E1065), Color(0xFF5B21B6), Color(0xFF9D174D))),
        BgPalette("آسمان روشن", listOf(Color(0xFFE0EAFC), Color(0xFFCFDEF3), Color(0xFFB6CCFE))),
        BgPalette("هلویی روشن", listOf(Color(0xFFFFECD2), Color(0xFFFCB69F), Color(0xFFFFD1FF)))
    )
    val accents = listOf(
        Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF34D399),
        Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFFF97316), Color(0xFFFFFFFF)
    )
    val flats = listOf(
        FlatTheme("آبی کلاسیک", "Classic blue", Color(0xFFFFFFFF), Color(0xFF1976D2), Color(0xFFFFFFFF),
            Color(0xFF212121), Color(0xFF757575), Color(0xFF1976D2), Color(0xFFF5F5F5), Color(0xFFEEEEEE), true),
        FlatTheme("سبز دریایی", "Teal", Color(0xFFFFFFFF), Color(0xFF00897B), Color(0xFFFFFFFF),
            Color(0xFF212121), Color(0xFF757575), Color(0xFF00897B), Color(0xFFF5F5F5), Color(0xFFEEEEEE), true),
        FlatTheme("بنفش", "Purple", Color(0xFFFFFFFF), Color(0xFF7B1FA2), Color(0xFFFFFFFF),
            Color(0xFF212121), Color(0xFF757575), Color(0xFF7B1FA2), Color(0xFFF5F5F5), Color(0xFFEEEEEE), true),
        FlatTheme("صورتی", "Pink", Color(0xFFFFFFFF), Color(0xFFD81B60), Color(0xFFFFFFFF),
            Color(0xFF212121), Color(0xFF757575), Color(0xFFD81B60), Color(0xFFF5F5F5), Color(0xFFEEEEEE), true),
        FlatTheme("نارنجی", "Orange", Color(0xFFFFFFFF), Color(0xFFEF6C00), Color(0xFFFFFFFF),
            Color(0xFF212121), Color(0xFF757575), Color(0xFFEF6C00), Color(0xFFF5F5F5), Color(0xFFEEEEEE), true),
        FlatTheme("تیره آمولد", "AMOLED dark", Color(0xFF000000), Color(0xFF000000), Color(0xFFFFFFFF),
            Color(0xFFFFFFFF), Color(0xFF9E9E9E), Color(0xFF4FC3F7), Color(0xFF121212), Color(0xFF222222), false),
        FlatTheme("شب آبی", "Night blue", Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFF1F5F9),
            Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF38BDF8), Color(0xFF1E293B), Color(0xFF273449), false),
        FlatTheme("مینیمال خاکستری", "Minimal gray", Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF263238),
            Color(0xFF263238), Color(0xFF78909C), Color(0xFF455A64), Color(0xFFF5F7F8), Color(0xFFECEFF1), false),
        FlatTheme("سبک آیفونی", "iOS style", Color(0xFFF2F2F7), Color(0xFFF2F2F7), Color(0xFF000000),
            Color(0xFF000000), Color(0xFF8E8E93), Color(0xFF0A84FF), Color(0xFFFFFFFF), Color(0xFFD1D1D6), false),
        FlatTheme("کرمی گرم", "Warm cream", Color(0xFFFAF3E8), Color(0xFF8D6E63), Color(0xFFFFFFFF),
            Color(0xFF3E2723), Color(0xFF8D6E63), Color(0xFF8D6E63), Color(0xFFF3E9D8), Color(0xFFEADFCB), true)
    )
    val chatBgs = listOf(
        ChatBg("آسمان", "Sky", listOf(Color(0xFFDCEBFA), Color(0xFFBBD6F3)), Color(0xFF1B1B2F)),
        ChatBg("نعناعی", "Mint", listOf(Color(0xFFDFF5EA), Color(0xFFB7E4CE)), Color(0xFF1B1B2F)),
        ChatBg("شنی", "Sand", listOf(Color(0xFFF7EBD7), Color(0xFFEBD5B0)), Color(0xFF3E2723)),
        ChatBg("گل‌بهی", "Rose", listOf(Color(0xFFFBE4EC), Color(0xFFF4BFD1)), Color(0xFF3B1226)),
        ChatBg("شب", "Night", listOf(Color(0xFF111827), Color(0xFF1F2937)), Color.White),
        ChatBg("مشکی", "Black", listOf(Color(0xFF000000), Color(0xFF000000)), Color.White)
    )
    val fonts = listOf("سیستم", "وزیرمتن", "Serif")
}

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

fun fontFor(i: Int): FontFamily = when (i) {
    1 -> Vazir
    2 -> FontFamily.Serif
    else -> FontFamily.Default
}

val LocalAppFont = compositionLocalOf<FontFamily> { FontFamily.Default }

data class GlassTheme(
    val style: Int = 1,
    val bgIndex: Int = 0,
    val accentIndex: Int = -1,
    val glass: Float = 0.16f,
    val radius: Float = 22f,
    val glow: Float = 0.5f,
    val hue1: Float = 210f,
    val hue2: Float = 280f,
    val lightness: Float = 0.25f,
    val accentHue: Float = 200f,
    val fontIndex: Int = 1,
    val textScale: Float = 1f,
    val auto: Boolean = false,
    val jalali: Boolean = true,
    val dense: Boolean = false,
    val bubble: Float = 18f,
    val msgScale: Float = 1f
) {
    val flat: Boolean get() = style > 0
    val ft: FlatTheme get() = Palettes.flats[(style - 1).coerceIn(0, Palettes.flats.size - 1)]
    val customBg: Boolean get() = bgIndex >= Palettes.backgrounds.size
    val customAccent: Boolean get() = accentIndex >= Palettes.accents.size

    val bg: List<Color>
        get() {
            if (flat) return listOf(ft.bg, ft.bg, ft.bg)
            if (!customBg) return Palettes.backgrounds[bgIndex].colors
            val v = 0.12f + 0.85f * lightness
            val s = 0.55f - 0.35f * lightness
            return listOf(
                Color.hsv(hue1, s, v * 0.9f),
                Color.hsv((hue1 + hue2) / 2f, s, v),
                Color.hsv(hue2, s, minOf(1f, v * 1.1f))
            )
        }

    val accent: Color
        get() = when {
            accentIndex < 0 -> if (flat) ft.accent else Palettes.accents[0]
            customAccent -> Color.hsv(accentHue, 0.65f, 1f)
            else -> Palettes.accents[accentIndex]
        }

    val onAccent: Color get() = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val barColor: Color get() = if (!flat) Color.Transparent else if (ft.barIsPrimary) accent else ft.bar
    val barText: Color
        get() = if (!flat) text else if (ft.barIsPrimary) onAccent else ft.barText
    val light: Boolean get() = bg[0].luminance() > (if (flat) 0.5f else 0.6f)
    val text: Color get() = if (flat) ft.text else if (light) Color(0xFF1B1B2F) else Color.White
    val subText: Color get() = if (flat) ft.sub else text.copy(alpha = 0.65f)
    val surface: Color get() = if (flat) ft.surface else Color.White.copy(alpha = glass)
    val divider: Color get() = if (flat) ft.divider else Color.White.copy(alpha = 0.18f)

    fun resolve(systemDark: Boolean): GlassTheme {
        if (!auto) return this
        return when {
            systemDark && light -> if (flat) copy(style = 6) else copy(bgIndex = 3)
            !systemDark && !light -> if (flat) copy(style = 1) else copy(bgIndex = 5)
            else -> this
        }
    }
}

class ThemeStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val maxBg = Palettes.backgrounds.size
    private val maxAc = Palettes.accents.size

    var state by mutableStateOf(
        GlassTheme(
            style = sp.getInt("style", 1).coerceIn(0, Palettes.flats.size),
            bgIndex = sp.getInt("bg", 0).coerceIn(0, maxBg),
            accentIndex = sp.getInt("ac", -1).coerceIn(-1, maxAc),
            glass = sp.getFloat("glass", 0.16f),
            radius = sp.getFloat("radius", 22f),
            glow = sp.getFloat("glow", 0.5f),
            hue1 = sp.getFloat("hue1", 210f),
            hue2 = sp.getFloat("hue2", 280f),
            lightness = sp.getFloat("light", 0.25f),
            accentHue = sp.getFloat("ahue", 200f),
            fontIndex = sp.getInt("font", 1).coerceIn(0, 2),
            textScale = sp.getFloat("scale", 1f),
            auto = sp.getBoolean("auto", false),
            jalali = sp.getBoolean("jalali", true),
            dense = sp.getBoolean("dense", false),
            bubble = sp.getFloat("bubble", 18f),
            msgScale = sp.getFloat("msgscale", 1f)
        )
    )
        private set

    fun set(t: GlassTheme) {
        state = t
        sp.edit()
            .putInt("style", t.style)
            .putInt("bg", t.bgIndex).putInt("ac", t.accentIndex)
            .putFloat("glass", t.glass).putFloat("radius", t.radius).putFloat("glow", t.glow)
            .putFloat("hue1", t.hue1).putFloat("hue2", t.hue2).putFloat("light", t.lightness)
            .putFloat("ahue", t.accentHue).putInt("font", t.fontIndex)
            .putFloat("scale", t.textScale).putBoolean("auto", t.auto).putBoolean("jalali", t.jalali)
            .putBoolean("dense", t.dense).putFloat("bubble", t.bubble).putFloat("msgscale", t.msgScale)
            .apply()
    }
}

fun Modifier.glass(t: GlassTheme, radius: Dp = t.radius.dp): Modifier {
    if (t.flat) {
        val shape = RoundedCornerShape(minOf(radius, 14.dp))
        return this.clip(shape).background(t.surface).border(0.5.dp, t.divider, shape)
    }
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = t.glass))
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))),
            shape
        )
}

@Composable
fun GlassBackground(t: GlassTheme, content: @Composable BoxScope.() -> Unit) {
    if (t.flat) {
        Box(Modifier.fillMaxSize().background(t.bg.first())) { content() }
        return
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(t.bg))) {
        Box(
            Modifier.align(Alignment.TopStart).offset((-70).dp, 60.dp).size(260.dp)
                .blur(70.dp).background(t.accent.copy(alpha = t.glow), CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(70.dp, (-60).dp).size(300.dp)
                .blur(80.dp).background(t.bg.last().copy(alpha = 0.9f), CircleShape)
        )
        content()
    }
}
