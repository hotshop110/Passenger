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
    val fonts = listOf("سیستم", "وزیرمتن", "سریف")
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
    val bgIndex: Int = 0,
    val accentIndex: Int = 0,
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
    val jalali: Boolean = true
) {
    val customBg: Boolean get() = bgIndex >= Palettes.backgrounds.size
    val customAccent: Boolean get() = accentIndex >= Palettes.accents.size

    val bg: List<Color>
        get() {
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
        get() = if (customAccent) Color.hsv(accentHue, 0.65f, 1f) else Palettes.accents[accentIndex]
    val light: Boolean get() = bg[0].luminance() > 0.6f
    val text: Color get() = if (light) Color(0xFF1B1B2F) else Color.White
    val subText: Color get() = text.copy(alpha = 0.65f)

    fun resolve(systemDark: Boolean): GlassTheme {
        if (!auto) return this
        return when {
            systemDark && light -> copy(bgIndex = 3)
            !systemDark && !light -> copy(bgIndex = 5)
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
            bgIndex = sp.getInt("bg", 0).coerceIn(0, maxBg),
            accentIndex = sp.getInt("ac", 0).coerceIn(0, maxAc),
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
            jalali = sp.getBoolean("jalali", true)
        )
    )
        private set

    fun set(t: GlassTheme) {
        state = t
        sp.edit()
            .putInt("bg", t.bgIndex).putInt("ac", t.accentIndex)
            .putFloat("glass", t.glass).putFloat("radius", t.radius).putFloat("glow", t.glow)
            .putFloat("hue1", t.hue1).putFloat("hue2", t.hue2).putFloat("light", t.lightness)
            .putFloat("ahue", t.accentHue).putInt("font", t.fontIndex)
            .putFloat("scale", t.textScale).putBoolean("auto", t.auto).putBoolean("jalali", t.jalali)
            .apply()
    }
}

fun Modifier.glass(t: GlassTheme, radius: Dp = t.radius.dp): Modifier {
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
