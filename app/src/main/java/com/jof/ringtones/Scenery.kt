package com.jof.ringtones

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private fun shapes(w: Float, h: Float, night: Boolean): List<Path> {
    val a = Path()
    val b = Path()
    if (night) {
        a.moveTo(0f, h * .80f); a.lineTo(w * .16f, h * .65f); a.lineTo(w * .30f, h * .78f)
        a.lineTo(w * .46f, h * .59f); a.lineTo(w * .62f, h * .79f); a.lineTo(w * .80f, h * .63f)
        a.lineTo(w, h * .80f)
        b.moveTo(0f, h * .90f); b.lineTo(w * .22f, h * .75f); b.lineTo(w * .42f, h * .90f)
        b.lineTo(w * .60f, h * .73f); b.lineTo(w * .82f, h * .91f); b.lineTo(w, h * .81f)
    } else {
        a.moveTo(0f, h * .84f); a.cubicTo(w * .25f, h * .71f, w * .55f, h * .93f, w, h * .77f)
        b.moveTo(0f, h * .94f); b.cubicTo(w * .30f, h * .83f, w * .62f, h * 1.02f, w, h * .87f)
    }
    a.lineTo(w, h); a.lineTo(0f, h); a.close()
    b.lineTo(w, h); b.lineTo(0f, h); b.close()
    return listOf(a, b)
}

private fun pulse(t: Float, ph: Float, speed: Float = 1f): Float =
    .3f + .7f * (.5f + .5f * sin(((t * speed + ph) * 2.0 * PI))).toFloat()

@Composable
fun SkyBackground(p: Float, time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val stars = remember { val r = Random(7); List(70) { floatArrayOf(r.nextFloat(), r.nextFloat() * .62f, r.nextFloat(), r.nextFloat()) } }
    val clouds = remember { val r = Random(21); List(6) { floatArrayOf(r.nextFloat(), .10f + r.nextFloat() * .34f, .20f + r.nextFloat() * .22f, .02f + r.nextFloat() * .05f) } }
    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val w = with(d) { maxWidth.toPx() }
        val h = with(d) { maxHeight.toPx() }
        val night = remember(w, h) { shapes(w, h, true) }
        val day = remember(w, h) { shapes(w, h, false) }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow), endY = size.height))
            val na = 1f - p
            if (na > .01f) {
                stars.forEach { s ->
                    drawCircle(pal.orb, s[2] * size.width * .0035f + .7f, Offset(s[0] * size.width, s[1] * size.height), na * pulse(time, s[3]))
                }
                val mc = Offset(size.width * .79f, size.height * .12f)
                val mr = size.width * .072f
                drawCircle(Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = .34f * na), Color.Transparent), mc, mr * 4.2f), mr * 4.2f, mc)
                drawCircle(pal.orb, mr, mc, na)
                drawCircle(pal.skyMid, mr * .94f, Offset(mc.x + mr * .44f, mc.y - mr * .24f), na)
                drawPath(night[0], pal.ridgeFar, na); drawPath(night[1], pal.ridgeNear, na)
            }
            if (p > .01f) {
                val sc = Offset(size.width * .23f, size.height * .13f)
                val sr = size.width * .095f
                drawCircle(Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = .6f * p), Color.Transparent), sc, sr * 3.6f), sr * 3.6f, sc)
                drawCircle(pal.orb, sr, sc, p * .95f)
                val wh = Color(0xFFFFF9F2)
                clouds.forEach { c ->
                    val cx = ((c[0] + time * c[3]) % 1.25f - .12f) * size.width
                    val cy = c[1] * size.height
                    val s = c[2] * size.width
                    drawRoundRect(wh.copy(alpha = .7f * p), Offset(cx, cy), Size(s, s * .32f), CornerRadius(s * .16f))
                    drawCircle(wh.copy(alpha = .7f * p), s * .19f, Offset(cx + s * .3f, cy))
                    drawCircle(wh.copy(alpha = .58f * p), s * .14f, Offset(cx + s * .63f, cy + s * .05f))
                }
                drawPath(day[0], pal.ridgeFar, p); drawPath(day[1], pal.ridgeNear, p)
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, pal.orbGlow.copy(alpha = .14f)), startY = size.height * .6f, endY = size.height))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, pal.scrim.copy(alpha = .42f)), Offset(size.width / 2f, size.height * .4f), max(size.width, size.height) * .8f))
        }
    }
}

@Composable
fun ThemeSwitch(p: Float, width: Dp, onDrag: (Float) -> Unit, onCommit: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val d = LocalDensity.current
    val height = width * .44f
    val w = with(d) { width.toPx() }
    val h = with(d) { height.toPx() }
    val kr = h * .36f
    val pad = h * .11f
    val travel = (w - 2f * (kr + pad)).coerceAtLeast(1f)
    val pill = RoundedCornerShape(50)
    val t by rememberInfiniteTransition(label = "k").animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "kt")
    val night = remember(w, h) { shapes(w, h, true) }
    val day = remember(w, h) { shapes(w, h, false) }
    val st = remember { val r = Random(4); List(9) { floatArrayOf(.3f + r.nextFloat() * .66f, .12f + r.nextFloat() * .42f, r.nextFloat(), r.nextFloat()) } }
    Box(Modifier.width(width).height(height)
        .drawBehind { drawRoundRect(Brush.horizontalGradient(listOf(pal.primary.copy(alpha = .34f), pal.tertiary.copy(alpha = .3f))), Offset(-7f, -7f), Size(size.width + 14f, size.height + 14f), CornerRadius(size.height)) }
        .clip(pill).border(2.dp, pal.border, pill)
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCommit(p < .5f) }
        .draggable(Orientation.Horizontal, rememberDraggableState { onDrag((p + it / travel).coerceIn(0f, 1f)) }, onDragStopped = { onCommit(p > .5f) })
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val na = 1f - p
            drawRoundRect(Brush.horizontalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow)), cornerRadius = CornerRadius(size.height / 2f))
            if (na > .01f) {
                st.forEach { s -> drawCircle(pal.orb, s[2] * size.height * .02f + .8f, Offset(s[0] * size.width, s[1] * size.height), na * pulse(t, s[3], 3f)) }
                val mc = Offset(size.width * .78f, size.height * .3f)
                val mr = size.height * .13f
                drawCircle(pal.orb, mr, mc, na)
                drawCircle(pal.skyMid, mr * .92f, Offset(mc.x + mr * .45f, mc.y - mr * .25f), na)
                drawPath(night[0], pal.ridgeFar, na); drawPath(night[1], pal.ridgeNear, na)
            }
            if (p > .01f) {
                val wh = Color(0xFFFFF9F2)
                drawRoundRect(wh.copy(alpha = .72f * p), Offset(size.width * .16f, size.height * .22f), Size(size.width * .24f, size.height * .13f), CornerRadius(size.height * .07f))
                drawCircle(wh.copy(alpha = .72f * p), size.height * .09f, Offset(size.width * .24f, size.height * .22f))
                drawPath(day[0], pal.ridgeFar, p); drawPath(day[1], pal.ridgeNear, p)
            }
            val cx = kr + pad + p * travel
            val cy = size.height / 2f
            val c = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = .55f), Color.Transparent), c, kr * 2.3f), kr * 2.3f, c)
            drawCircle(lerp(Color(0xFF141230), Color(0xFFFFF3C4), p), kr, c)
            drawCircle(pal.orbGlow.copy(alpha = .75f), kr, c, style = Stroke(1.8f))
            if (p < .98f) {
                val cr = Color(0xFF0A0920).copy(alpha = .55f * (1f - p))
                drawCircle(cr, kr * .22f, Offset(cx - kr * .28f, cy - kr * .24f))
                drawCircle(cr, kr * .15f, Offset(cx + kr * .26f, cy + kr * .1f))
                drawCircle(cr, kr * .11f, Offset(cx - kr * .05f, cy + kr * .36f))
            }
            if (p > .02f) rotate(t * 360f, c) {
                for (i in 0 until 8) {
                    val a = i * 45.0 * PI / 180.0
                    drawLine(pal.orb.copy(alpha = .85f * p),
                        Offset(cx + kr * 1.18f * cos(a).toFloat(), cy + kr * 1.18f * sin(a).toFloat()),
                        Offset(cx + kr * 1.42f * cos(a).toFloat(), cy + kr * 1.42f * sin(a).toFloat()), 2.2f)
                }
            }
        }
    }
}

@Composable
fun MenuBar(index: Int, badges: List<Int>, onSelect: (Int) -> Unit) {
    val pal = LocalPal.current
    val ic = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.TravelExplore, Icons.Rounded.Favorite, Icons.Rounded.Tune)
    val lb = listOf("Треки", "Поиск", "Избранное", "Настройки")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).height(74.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(listOf(pal.surfaceHigh.copy(alpha = .94f), pal.surface.copy(alpha = .9f))))
        .border(1.dp, pal.border, RoundedCornerShape(28.dp))) {
        val iw = maxWidth / 4
        val x by animateDpAsState(iw * index, spring(dampingRatio = .72f, stiffness = 430f), label = "nav")
        Box(Modifier.offset(x = x + 6.dp, y = 8.dp).width(iw - 12.dp).height(58.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(pal.primary.copy(alpha = .3f), pal.tertiary.copy(alpha = .24f))))
            .border(1.dp, pal.primary.copy(alpha = .45f), RoundedCornerShape(22.dp)))
        Row(Modifier.fillMaxSize()) {
            lb.forEachIndexed { i, l ->
                val sel = i == index
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box {
                        Icon(ic[i], l, Modifier.size(23.dp), tint = if (sel) pal.primary else pal.textDim)
                        val b = badges.getOrElse(i) { 0 }
                        if (b > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-5).dp)
                            .clip(RoundedCornerShape(50)).background(pal.tertiary).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text(if (b > 999) "999+" else b.toString(), fontSize = 9.sp, color = pal.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(l, fontSize = 11.sp, color = if (sel) pal.text else pal.textDim, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val pal = LocalPal.current
    val s = RoundedCornerShape(50)
    Row(Modifier.clip(s)
        .background(if (selected) Brush.horizontalGradient(listOf(pal.primary, pal.tertiary)) else Brush.horizontalGradient(listOf(pal.surfaceHigh.copy(alpha = .75f), pal.surface.copy(alpha = .65f))))
        .border(1.dp, if (selected) Color.Transparent else pal.border, s)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(15.dp), tint = if (selected) pal.onPrimary else pal.textDim); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) pal.onPrimary else pal.text)
    }
}

@Composable
fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val f by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = .6f, stiffness = 520f), label = "sw")
    val s = RoundedCornerShape(50)
    Box(Modifier.width(54.dp).height(30.dp).clip(s)
        .background(if (checked) Brush.horizontalGradient(listOf(pal.primary, pal.tertiary)) else Brush.horizontalGradient(listOf(pal.surface, pal.surfaceHigh)))
        .border(1.dp, pal.border, s).clickable { onChange(!checked) }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * .34f
            val pd = size.height * .16f
            val cx = r + pd + f * (size.width - 2f * (r + pd))
            drawCircle(pal.orbGlow.copy(alpha = .5f), r * 1.5f, Offset(cx, size.height / 2f))
            drawCircle(pal.orb, r, Offset(cx, size.height / 2f))
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val s = RoundedCornerShape(22.dp)
    Column(modifier.clip(s)
        .background(Brush.verticalGradient(listOf(pal.surfaceHigh.copy(alpha = .93f), pal.surface.copy(alpha = .88f))))
        .border(1.dp, pal.border, s).padding(14.dp), content = content)
}

@Composable
fun SectionLabel(text: String, hint: String? = null) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(pal.primary, pal.tertiary))))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal.text, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, fontSize = 11.sp, color = pal.textDim)
    }
}

@Composable
fun StatTile(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val s = RoundedCornerShape(16.dp)
    Column(modifier.clip(s).background(Brush.verticalGradient(listOf(tint.copy(alpha = .2f), pal.surface.copy(alpha = .55f))))
        .border(1.dp, tint.copy(alpha = .4f), s).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(label, fontSize = 10.sp, color = pal.textDim)
    }
}

@Composable
fun Waveform(seed: Long, progress: Float, modifier: Modifier = Modifier, bars: Int = 34) {
    val pal = LocalPal.current
    val hs = remember(seed, bars) { val r = Random(seed); FloatArray(bars) { .22f + .78f * r.nextFloat() } }
    Canvas(modifier) {
        val gap = size.width / (bars * 2f)
        for (i in 0 until bars) {
            val bh = (hs[i] * size.height).coerceAtLeast(2f)
            drawRoundRect(if ((i + .5f) / bars <= progress) pal.primary else pal.border.copy(alpha = .85f),
                Offset(i * 2f * gap + gap * .5f, (size.height - bh) / 2f), Size(gap, bh), CornerRadius(gap / 2f))
        }
    }
}

@Composable
fun Equalizer(time: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = size.width / 8f
        for (i in 0 until 4) {
            val bh = size.height * pulse(time, i * .27f, 6f)
            drawRoundRect(color, Offset(i * 2f * gap + gap * .5f, size.height - bh), Size(gap, bh), CornerRadius(gap / 2f))
        }
    }
}

@Composable
fun Radar(time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val bl = remember { val r = Random(11); List(5) { floatArrayOf(.15f + r.nextFloat() * .7f, .15f + r.nextFloat() * .7f, r.nextFloat()) } }
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (i in 1..3) drawCircle(pal.border.copy(alpha = .7f), r * i / 3f, c, style = Stroke(1.4f))
        drawLine(pal.border.copy(alpha = .6f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.2f)
        drawLine(pal.border.copy(alpha = .6f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.2f)
        rotate(time * 360f, c) {
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, pal.primary.copy(alpha = .45f), Color.Transparent), c), r, c)
            drawLine(pal.primary, c, Offset(c.x + r, c.y), 2f)
        }
        bl.forEach { b ->
            val pu = pulse(time, b[2], 2f)
            drawCircle(pal.tertiary, 3.4f + pu * 2.4f, Offset(b[0] * size.width, b[1] * size.height), pu)
        }
    }
}

@Composable
fun IndeterminateBar(time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    Canvas(modifier) {
        drawRoundRect(pal.border.copy(alpha = .5f), size = size, cornerRadius = CornerRadius(size.height / 2f))
        val w = size.width * .34f
        val x = ((time * 1.6f) % 1.4f - .2f) * size.width
        drawRoundRect(Brush.horizontalGradient(listOf(pal.primary, pal.tertiary, pal.secondary)),
            Offset(x.coerceIn(-w, size.width), 0f), Size(w, size.height), CornerRadius(size.height / 2f))
    }
}

@Composable
fun ScrollThumb(state: LazyListState, total: Int, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val pos by remember(total) { derivedStateOf { if (total <= 0) 0f else (state.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f) } }
    val len by remember(total) { derivedStateOf { if (total <= 0) .2f else (state.layoutInfo.visibleItemsInfo.size.toFloat() / total).coerceIn(.08f, 1f) } }
    Canvas(modifier) {
        drawRoundRect(pal.border.copy(alpha = .35f), size = size, cornerRadius = CornerRadius(size.width / 2f))
        val th = (size.height * len).coerceAtLeast(24f)
        drawRoundRect(Brush.verticalGradient(listOf(pal.primary, pal.tertiary)),
            Offset(0f, pos * (size.height - th)), Size(size.width, th), CornerRadius(size.width / 2f))
    }
}
