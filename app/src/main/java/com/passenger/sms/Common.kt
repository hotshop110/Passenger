package com.passenger.sms

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassField(
    t: GlassTheme, value: String, onValue: (String) -> Unit,
    hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true
) {
    Box(modifier.glass(t, 20.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = t.subText, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = singleLine,
            maxLines = if (singleLine) 1 else 6,
            textStyle = TextStyle(color = t.text, fontSize = 15.sp, fontFamily = LocalAppFont.current),
            cursorBrush = SolidColor(t.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BarBtn(fg: Color, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 20.sp) }
}

@Composable
fun MenuBtn(fg: Color, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarBtn(fg, "⋮") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, act) in items) {
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() })
            }
        }
    }
}

@Composable
fun AppBar(
    t: GlassTheme, title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val bg = if (t.flat) t.barColor else Color.Transparent
    val fg = if (t.flat) t.barText else t.text
    Row(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) BarBtn(fg, if (Lang.isFa) "→" else "←", onBack)
        Text(
            title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        actions()
    }
}

@Composable
fun IconBtn(t: GlassTheme, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glass(t, 20.dp).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 17.sp, color = t.text) }
}

@Composable
fun Section(t: GlassTheme, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(t).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = t.text, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun LabeledSlider(
    t: GlassTheme, label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, color = t.subText, fontSize = 13.sp)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = t.accent, activeTrackColor = t.accent,
                inactiveTrackColor = t.subText.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SwitchRow(t: GlassTheme, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = t.accent, checkedThumbColor = t.onAccent)
        )
    }
}

@Composable
fun ActionBtn(t: GlassTheme, label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().glass(t, 18.dp).clickable { onClick() }.padding(14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun Chip(t: GlassTheme, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.glass(t, 18.dp)
            .background(if (selected) t.accent.copy(alpha = if (t.flat) 1f else 0.55f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected && t.flat) t.onAccent else t.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

fun initialsOf(s: String): String {
    val parts = s.trim().split(' ', '‌').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    val a = parts[0].first().toString()
    val b = if (parts.size > 1) parts[1].first().toString() else ""
    return (a + b).uppercase()
}

@Composable
fun Avatar(t: GlassTheme, c: Conv, size: Dp, selected: Boolean) {
    val fg = if (t.flat) t.onAccent else t.text
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(t.accent.copy(alpha = if (t.flat) 1f else 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        val title = c.name ?: c.address
        val photo = c.photo
        if (selected) {
            Text("✓", color = fg, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
        } else if (photo != null) {
            Image(
                bitmap = photo, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        } else if (title.any { it.isLetter() }) {
            Text(initialsOf(title), color = fg, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold)
        } else {
            Text(c.cat.emoji, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun Divider(t: GlassTheme) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.divider))
}

@Composable
fun LockScreen(t: GlassTheme, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.size(12.dp))
        Text("Passenger", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(T("برنامه قفل است", "App is locked"), color = t.subText, fontSize = 14.sp)
        Spacer(Modifier.size(24.dp))
        Box(
            Modifier.glass(t, 22.dp).clickable { onUnlock() }.padding(horizontal = 32.dp, vertical = 14.dp)
        ) { Text(T("باز کردن", "Unlock"), color = t.accent, fontWeight = FontWeight.Bold) }
    }
}
