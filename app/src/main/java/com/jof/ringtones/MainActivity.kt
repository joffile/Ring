package com.jof.ringtones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Pal(
    val dark: Boolean, val skyTop: Color, val skyMid: Color, val skyLow: Color,
    val ridgeFar: Color, val ridgeNear: Color, val orb: Color, val orbGlow: Color,
    val primary: Color, val secondary: Color, val tertiary: Color, val success: Color,
    val warning: Color, val danger: Color, val surface: Color, val surfaceHigh: Color,
    val border: Color, val text: Color, val textDim: Color, val onPrimary: Color, val scrim: Color
)

val Night = Pal(true, Color(0xFF07061A), Color(0xFF1E1B4B), Color(0xFF4C3F91), Color(0xFF3B3480), Color(0xFF171335), Color(0xFFF5F1E3), Color(0xFFBFA8FF), Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFF14122E), Color(0xFF231F4A), Color(0xFF413A80), Color(0xFFECEAFF), Color(0xFFA9A3D9), Color(0xFF120F28), Color(0xFF05040F))
val Day = Pal(false, Color(0xFFFFEBD6), Color(0xFFFFC3A8), Color(0xFFF2777A), Color(0xFFE86A8A), Color(0xFF9C1458), Color(0xFFFFF6D0), Color(0xFFFFC94D), Color(0xFFE11D48), Color(0xFF0284C7), Color(0xFF7C3AED), Color(0xFF047857), Color(0xFFB45309), Color(0xFFB91C1C), Color(0xFFFFF4EA), Color(0xFFFFFFFF), Color(0xFFEFC0AC), Color(0xFF3B1024), Color(0xFF8A5A6B), Color(0xFFFFF7F0), Color(0xFF3A1220))

fun palAt(p: Float) = Pal(p < .5f,
    lerp(Night.skyTop, Day.skyTop, p), lerp(Night.skyMid, Day.skyMid, p), lerp(Night.skyLow, Day.skyLow, p),
    lerp(Night.ridgeFar, Day.ridgeFar, p), lerp(Night.ridgeNear, Day.ridgeNear, p), lerp(Night.orb, Day.orb, p), lerp(Night.orbGlow, Day.orbGlow, p),
    lerp(Night.primary, Day.primary, p), lerp(Night.secondary, Day.secondary, p), lerp(Night.tertiary, Day.tertiary, p), lerp(Night.success, Day.success, p),
    lerp(Night.warning, Day.warning, p), lerp(Night.danger, Day.danger, p), lerp(Night.surface, Day.surface, p), lerp(Night.surfaceHigh, Day.surfaceHigh, p),
    lerp(Night.border, Day.border, p), lerp(Night.text, Day.text, p), lerp(Night.textDim, Day.textDim, p), lerp(Night.onPrimary, Day.onPrimary, p), lerp(Night.scrim, Day.scrim, p))

val LocalPal = compositionLocalOf { Night }

fun schemeFor(p: Pal): ColorScheme = if (p.dark) darkColorScheme(primary = p.primary, onPrimary = p.onPrimary, secondary = p.secondary, tertiary = p.tertiary, background = p.skyTop, onBackground = p.text, surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim, outline = p.border, error = p.danger)
else lightColorScheme(primary = p.primary, onPrimary = p.onPrimary, secondary = p.secondary, tertiary = p.tertiary, background = p.skyTop, onBackground = p.text, surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim, outline = p.border, error = p.danger)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        enableEdgeToEdge(); super.onCreate(s)
        setContent {
            val vm: RingtoneViewModel = viewModel()
            val p by vm.themeProgress.collectAsState()
            val pal = remember(p) { palAt(p) }
            CompositionLocalProvider(LocalPal provides pal) { MaterialTheme(colorScheme = schemeFor(pal)) { Screen(vm) } }
        }
    }
}

private fun shapes(w: Float, h: Float, night: Boolean): List<Path> {
    val a = Path(); val b = Path()
    if (night) { a.moveTo(0f, h * .8f); a.lineTo(w * .16f, h * .65f); a.lineTo(w * .3f, h * .78f); a.lineTo(w * .46f, h * .59f); a.lineTo(w * .62f, h * .79f); a.lineTo(w * .8f, h * .63f); a.lineTo(w, h * .8f); b.moveTo(0f, h * .9f); b.lineTo(w * .22f, h * .75f); b.lineTo(w * .42f, h * .9f); b.lineTo(w * .6f, h * .73f); b.lineTo(w * .82f, h * .91f); b.lineTo(w, h * .81f) }
    else { a.moveTo(0f, h * .84f); a.cubicTo(w * .25f, h * .71f, w * .55f, h * .93f, w, h * .77f); b.moveTo(0f, h * .94f); b.cubicTo(w * .3f, h * .83f, w * .62f, h * 1.02f, w, h * .87f) }
    a.lineTo(w, h); a.lineTo(0f, h); a.close(); b.lineTo(w, h); b.lineTo(0f, h); b.close()
    return listOf(a, b)
}

private fun pulse(t: Float, ph: Float, sp: Float = 1f) = .3f + .7f * (.5f + .5f * sin((t * sp + ph) * 2.0 * PI)).toFloat()

@Composable
fun SkyBackground(p: Float, time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val stars = remember { val r = Random(7); List(60) { floatArrayOf(r.nextFloat(), r.nextFloat() * .6f, r.nextFloat(), r.nextFloat()) } }
    val clouds = remember { val r = Random(21); List(5) { floatArrayOf(r.nextFloat(), .1f + r.nextFloat() * .3f, .2f + r.nextFloat() * .2f, .02f + r.nextFloat() * .05f) } }
    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
        val night = remember(w, h) { shapes(w, h, true) }; val day = remember(w, h) { shapes(w, h, false) }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow), endY = size.height))
            val na = 1f - p
            if (na > .01f) {
                stars.forEach { s -> drawCircle(pal.orb, s[2] * size.width * .0035f + .7f, Offset(s[0] * size.width, s[1] * size.height), na * pulse(time, s[3])) }
                val mc = Offset(size.width * .79f, size.height * .12f); val mr = size.width * .07f
                drawCircle(pal.orb, mr, mc, na); drawCircle(pal.skyMid, mr * .94f, Offset(mc.x + mr * .44f, mc.y - mr * .24f), na)
                drawPath(night[0], pal.ridgeFar, na); drawPath(night[1], pal.ridgeNear, na)
            }
            if (p > .01f) {
                val sc = Offset(size.width * .23f, size.height * .13f); val sr = size.width * .09f
                drawCircle(pal.orb, sr, sc, p * .95f)
                val wh = Color(0xFFFFF9F2)
                clouds.forEach { c -> val cx = ((c[0] + time * c[3]) % 1.25f - .12f) * size.width; val cy = c[1] * size.height; val s = c[2] * size.width
                    drawRoundRect(wh.copy(alpha = .7f * p), Offset(cx, cy), Size(s, s * .32f), CornerRadius(s * .16f)); drawCircle(wh.copy(alpha = .7f * p), s * .19f, Offset(cx + s * .3f, cy)) }
                drawPath(day[0], pal.ridgeFar, p); drawPath(day[1], pal.ridgeNear, p)
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, pal.scrim.copy(alpha = .4f)), Offset(size.width / 2, size.height * .4f), max(size.width, size.height) * .8f))
        }
    }
}

@Composable
fun ThemeSwitch(p: Float, width: Dp, onDrag: (Float) -> Unit, onCommit: (Boolean) -> Unit) {
    val pal = LocalPal.current; val d = LocalDensity.current
    val height = width * .44f
    val w = with(d) { width.toPx() }; val h = with(d) { height.toPx() }
    val kr = h * .36f; val pad = h * .11f; val travel = (w - 2 * (kr + pad)).coerceAtLeast(1f)
    val pill = RoundedCornerShape(50)
    val t by rememberInfiniteTransition(label = "k").animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "kt")
    Box(Modifier.width(width).height(height).clip(pill).border(2.dp, pal.border, pill)
        .clickable { onCommit(p < .5f) }
        .draggable(Orientation.Horizontal, rememberDraggableState { onDrag((p + it / travel).coerceIn(0f, 1f)) }, onDragStopped = { onCommit(p > .5f) })) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Brush.horizontalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow)), cornerRadius = CornerRadius(size.height / 2))
            val cx = kr + pad + p * travel; val cy = size.height / 2; val c = Offset(cx, cy)
            drawCircle(lerp(Color(0xFF141230), Color(0xFFFFF3C4), p), kr, c)
            drawCircle(pal.orbGlow.copy(alpha = .75f), kr, c, style = Stroke(1.8f))
            if (p < .98f) { val cr = Color(0xFF0A0920).copy(alpha = .55f * (1 - p)); drawCircle(cr, kr * .22f, Offset(cx - kr * .28f, cy - kr * .24f)); drawCircle(cr, kr * .15f, Offset(cx + kr * .26f, cy + kr * .1f)) }
            if (p > .02f) rotate(t * 360f, c) { for (i in 0 until 8) { val a = i * 45.0 * PI / 180; drawLine(pal.orb.copy(alpha = .85f * p), Offset(cx + kr * 1.18f * cos(a).toFloat(), cy + kr * 1.18f * sin(a).toFloat()), Offset(cx + kr * 1.42f * cos(a).toFloat(), cy + kr * 1.42f * sin(a).toFloat()), 2.2f) } }
        }
    }
}

@Composable
fun MenuBar(index: Int, badges: List<Int>, onSelect: (Int) -> Unit) {
    val pal = LocalPal.current
    val ic = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.TravelExplore, Icons.Rounded.Favorite, Icons.Rounded.Tune)
    val lb = listOf("Треки", "Поиск", "Избранное", "Настройки")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).height(72.dp).clip(RoundedCornerShape(28.dp)).background(pal.surfaceHigh.copy(alpha = .94f)).border(1.dp, pal.border, RoundedCornerShape(28.dp))) {
        val iw = maxWidth / 4
        val x by animateDpAsState(iw * index, spring(dampingRatio = .72f, stiffness = 430f), label = "nav")
        Box(Modifier.offset(x = x + 6.dp, y = 8.dp).width(iw - 12.dp).height(56.dp).clip(RoundedCornerShape(22.dp)).background(pal.primary.copy(alpha = .25f)))
        Row(Modifier.fillMaxSize()) { lb.forEachIndexed { i, l -> val sel = i == index
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(ic[i], l, Modifier.size(22.dp), tint = if (sel) pal.primary else pal.textDim)
                Spacer(Modifier.height(3.dp))
                Text(l, fontSize = 10.sp, color = if (sel) pal.text else pal.textDim, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            } } }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val pal = LocalPal.current; val s = RoundedCornerShape(50)
    Row(Modifier.clip(s).background(if (selected) pal.primary else pal.surfaceHigh.copy(alpha = .7f)).border(1.dp, if (selected) Color.Transparent else pal.border, s).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(15.dp), tint = if (selected) pal.onPrimary else pal.textDim); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) pal.onPrimary else pal.text)
    }
}

@Composable
fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val f by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = .6f, stiffness = 520f), label = "sw")
    val s = RoundedCornerShape(50)
    Box(Modifier.width(52.dp).height(30.dp).clip(s).background(if (checked) pal.primary else pal.surfaceHigh).border(1.dp, pal.border, s).clickable { onChange(!checked) }) {
        Canvas(Modifier.fillMaxSize()) { val r = size.height * .34f; val pd = size.height * .16f; val cx = r + pd + f * (size.width - 2 * (r + pd)); drawCircle(pal.orb, r, Offset(cx, size.height / 2)) }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current; val s = RoundedCornerShape(22.dp)
    Column(modifier.clip(s).background(pal.surfaceHigh.copy(alpha = .92f)).border(1.dp, pal.border, s).padding(14.dp), content = content)
}

@Composable
fun SectionLabel(t: String, hint: String? = null) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(pal.primary))
        Spacer(Modifier.width(8.dp)); Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal.text, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, fontSize = 11.sp, color = pal.textDim)
    }
}

@Composable
fun StatTile(v: String, l: String, tint: Color, modifier: Modifier = Modifier) {
    val pal = LocalPal.current; val s = RoundedCornerShape(16.dp)
    Column(modifier.clip(s).background(tint.copy(alpha = .18f)).border(1.dp, tint.copy(alpha = .4f), s).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint); Text(l, fontSize = 10.sp, color = pal.textDim)
    }
}

@Composable
fun Waveform(seed: Long, progress: Float, modifier: Modifier = Modifier, bars: Int = 30) {
    val pal = LocalPal.current
    val hs = remember(seed, bars) { val r = Random(seed); FloatArray(bars) { .22f + .78f * r.nextFloat() } }
    Canvas(modifier) { val gap = size.width / (bars * 2f)
        for (i in 0 until bars) { val bh = (hs[i] * size.height).coerceAtLeast(2f); drawRoundRect(if ((i + .5f) / bars <= progress) pal.primary else pal.border.copy(alpha = .85f), Offset(i * 2 * gap + gap * .5f, (size.height - bh) / 2), Size(gap, bh), CornerRadius(gap / 2)) } }
}

@Composable
fun Equalizer(time: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { val gap = size.width / 8f
        for (i in 0 until 4) { val bh = size.height * pulse(time, i * .27f, 6f); drawRoundRect(color, Offset(i * 2 * gap + gap * .5f, size.height - bh), Size(gap, bh), CornerRadius(gap / 2)) } }
}
