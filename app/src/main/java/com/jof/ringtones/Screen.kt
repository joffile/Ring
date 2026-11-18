package com.jof.ringtones

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val ph: Float)
private data class Cloud(val x: Float, val y: Float, val s: Float, val sp: Float)

private fun ridgePaths(w: Float, h: Float): List<Path> {
    val far = Path().apply {
        moveTo(0f, h * 0.80f); lineTo(w * 0.16f, h * 0.65f); lineTo(w * 0.30f, h * 0.78f)
        lineTo(w * 0.46f, h * 0.59f); lineTo(w * 0.62f, h * 0.79f); lineTo(w * 0.80f, h * 0.63f)
        lineTo(w, h * 0.80f); lineTo(w, h); lineTo(0f, h); close()
    }
    val near = Path().apply {
        moveTo(0f, h * 0.90f); lineTo(w * 0.22f, h * 0.75f); lineTo(w * 0.42f, h * 0.90f)
        lineTo(w * 0.60f, h * 0.73f); lineTo(w * 0.82f, h * 0.91f); lineTo(w, h * 0.81f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    return listOf(far, near)
}

private fun dunePaths(w: Float, h: Float): List<Path> {
    val far = Path().apply {
        moveTo(0f, h * 0.84f)
        cubicTo(w * 0.25f, h * 0.71f, w * 0.55f, h * 0.93f, w, h * 0.77f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    val near = Path().apply {
        moveTo(0f, h * 0.94f)
        cubicTo(w * 0.30f, h * 0.83f, w * 0.62f, h * 1.02f, w, h * 0.87f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    return listOf(far, near)
}

@Composable
fun SkyBackground(p: Float, time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val stars = remember {
        val r = Random(7)
        List(72) { Star(r.nextFloat(), r.nextFloat() * 0.62f, r.nextFloat(), r.nextFloat()) }
    }
    val clouds = remember {
        val r = Random(21)
        List(6) { Cloud(r.nextFloat(), 0.10f + r.nextFloat() * 0.34f, 0.20f + r.nextFloat() * 0.22f, 0.02f + r.nextFloat() * 0.05f) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val ridges = remember(wPx, hPx) { ridgePaths(wPx, hPx) }
        val dunes = remember(wPx, hPx) { dunePaths(wPx, hPx) }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow), startY = 0f, endY = h))
            val na = 1f - p
            if (na > 0.01f) {
                stars.forEach { s ->
                    val tw = 0.30f + 0.70f * (0.5f + 0.5f * sin((time + s.ph) * 2.0 * PI)).toFloat()
                    drawCircle(pal.orb, s.r * w * 0.0035f + 0.7f, Offset(s.x * w, s.y * h), na * tw * 0.95f)
                }
                val mc = Offset(w * 0.79f, h * 0.12f)
                val mr = w * 0.072f
                drawCircle(
                    Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = 0.34f * na), Color.Transparent), mc, mr * 4.2f),
                    mr * 4.2f, mc
                )
                drawCircle(pal.orb, mr, mc, na)
                drawCircle(pal.skyMid, mr * 0.94f, Offset(mc.x + mr * 0.44f, mc.y - mr * 0.24f), na)
                drawPath(ridges[0], pal.ridgeFar, na)
                drawPath(ridges[1], pal.ridgeNear, na)
            }
            if (p > 0.01f) {
                val sc = Offset(w * 0.23f, h * 0.13f)
                val sr = w * 0.095f
                drawCircle(
                    Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = 0.60f * p), Color.Transparent), sc, sr * 3.6f),
                    sr * 3.6f, sc
                )
                drawCircle(pal.orb, sr, sc, p * 0.95f)
                clouds.forEach { c ->
                    val cx = ((c.x + time * c.sp) % 1.25f - 0.12f) * w
                    val cy = c.y * h
                    val s = c.s * w
                    val white = Color(0xFFFFF9F2)
                    drawRoundRect(white.copy(alpha = 0.70f * p), Offset(cx, cy), Size(s, s * 0.32f), CornerRadius(s * 0.16f))
                    drawCircle(white.copy(alpha = 0.70f * p), s * 0.19f, Offset(cx + s * 0.30f, cy + s * 0.01f))
                    drawCircle(white.copy(alpha = 0.58f * p), s * 0.14f, Offset(cx + s * 0.63f, cy + s * 0.05f))
                }
                drawPath(dunes[0], pal.ridgeFar, p)
                drawPath(dunes[1], pal.ridgeNear, p)
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, pal.orbGlow.copy(alpha = 0.14f)), startY = h * 0.60f, endY = h))
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, pal.scrim.copy(alpha = 0.42f)),
                    Offset(w / 2f, h * 0.40f), max(w, h) * 0.80f
                )
            )
        }
    }
}

@Composable
fun ThemeSwitch(
    p: Float,
    width: Dp,
    onDrag: (Float) -> Unit,
    onCommit: (Boolean) -> Unit
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val height = width * 0.44f
    val wPx = with(density) { width.toPx() }
    val hPx = with(density) { height.toPx() }
    val kr = hPx * 0.36f
    val pad = hPx * 0.11f
    val travel = (wPx - 2f * (kr + pad)).coerceAtLeast(1f)
    val pill = RoundedCornerShape(50)
    val time by rememberInfiniteTransition(label = "knob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "kt"
    )
    val ridges = remember(wPx, hPx) { ridgePaths(wPx, hPx) }
    val dunes = remember(wPx, hPx) { dunePaths(wPx, hPx) }
    val miniStars = remember {
        val r = Random(4)
        List(9) { Star(0.30f + r.nextFloat() * 0.66f, 0.12f + r.nextFloat() * 0.42f, r.nextFloat(), r.nextFloat()) }
    }
    Box(
        Modifier
            .width(width)
            .height(height)
            .drawBehind {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(pal.primary.copy(alpha = 0.34f), pal.tertiary.copy(alpha = 0.30f))),
                    Offset(-7f, -7f), Size(size.width + 14f, size.height + 14f), CornerRadius(size.height)
                )
            }
            .clip(pill)
            .border(2.dp, pal.border, pill)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCommit(p < 0.5f)
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d -> onDrag((p + d / travel).coerceIn(0f, 1f)) },
                onDragStopped = { onCommit(p > 0.5f) }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                Brush.horizontalGradient(listOf(pal.skyTop, pal.skyMid, pal.skyLow)),
                cornerRadius = CornerRadius(h / 2f)
            )
            val na = 1f - p
            if (na > 0.01f) {
                miniStars.forEach { s ->
                    val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin((time * 3f + s.ph) * 2.0 * PI)).toFloat()
                    drawCircle(pal.orb, s.r * h * 0.02f + 0.8f, Offset(s.x * w, s.y * h), na * tw)
                }
                val mc = Offset(w * 0.78f, h * 0.30f)
                val mr = h * 0.13f
                drawCircle(pal.orb, mr, mc, na)
                drawCircle(pal.skyMid, mr * 0.92f, Offset(mc.x + mr * 0.45f, mc.y - mr * 0.25f), na)
                drawPath(ridges[0], pal.ridgeFar, na * 0.95f)
                drawPath(ridges[1], pal.ridgeNear, na * 0.95f)
            }
            if (p > 0.01f) {
                val white = Color(0xFFFFF9F2)
                drawRoundRect(white.copy(alpha = 0.72f * p), Offset(w * 0.16f, h * 0.22f), Size(w * 0.24f, h * 0.13f), CornerRadius(h * 0.07f))
                drawCircle(white.copy(alpha = 0.72f * p), h * 0.09f, Offset(w * 0.24f, h * 0.22f))
                drawRoundRect(white.copy(alpha = 0.55f * p), Offset(w * 0.50f, h * 0.34f), Size(w * 0.18f, h * 0.10f), CornerRadius(h * 0.05f))
                drawPath(dunes[0], pal.ridgeFar, p * 0.95f)
                drawPath(dunes[1], pal.ridgeNear, p * 0.95f)
            }
            val cx = kr + pad + p * travel
            val cy = h / 2f
            drawCircle(
                Brush.radialGradient(listOf(pal.orbGlow.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), kr * 2.3f),
                kr * 2.3f, Offset(cx, cy)
            )
            drawCircle(lerp(Color(0xFF141230), Color(0xFFFFF3C4), p), kr, Offset(cx, cy))
            drawCircle(pal.orbGlow.copy(alpha = 0.75f), kr, Offset(cx, cy), style = Stroke(1.8f))
            if (p < 0.98f) {
                val crater = Color(0xFF0A0920).copy(alpha = 0.55f * (1f - p))
                drawCircle(crater, kr * 0.22f, Offset(cx - kr * 0.28f, cy - kr * 0.24f))
                drawCircle(crater, kr * 0.15f, Offset(cx + kr * 0.26f, cy + kr * 0.10f))
                drawCircle(crater, kr * 0.11f, Offset(cx - kr * 0.05f, cy + kr * 0.36f))
            }
            if (p > 0.02f) {
                rotate(time * 360f, Offset(cx, cy)) {
                    for (i in 0 until 8) {
                        val a = i * 45.0 * PI / 180.0
                        val x1 = cx + (kr * 1.18f) * kotlin.math.cos(a).toFloat()
                        val y1 = cy + (kr * 1.18f) * kotlin.math.sin(a).toFloat()
                        val x2 = cx + (kr * 1.42f) * kotlin.math.cos(a).toFloat()
                        val y2 = cy + (kr * 1.42f) * kotlin.math.sin(a).toFloat()
                        drawLine(pal.orb.copy(alpha = 0.85f * p), Offset(x1, y1), Offset(x2, y2), 2.2f)
                    }
                }
            }
        }
    }
}

@Composable
fun MenuBar(index: Int, badges: List<Int>, onSelect: (Int) -> Unit) {
    val pal = LocalPal.current
    val icons = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.TravelExplore, Icons.Rounded.Favorite, Icons.Rounded.Tune)
    val labels = listOf("Треки", "Поиск", "Избранное", "Настройки")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(pal.surfaceHigh.copy(alpha = 0.94f), pal.surface.copy(alpha = 0.90f))))
            .border(1.dp, pal.border, RoundedCornerShape(28.dp))
    ) {
        val itemW = maxWidth / 4
        val x by animateDpAsState(itemW * index, spring(dampingRatio = 0.72f, stiffness = 430f), label = "nav")
        Box(
            Modifier
                .offset(x = x + 6.dp, y = 8.dp)
                .width(itemW - 12.dp)
                .height(58.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(pal.primary.copy(alpha = 0.30f), pal.tertiary.copy(alpha = 0.24f))))
                .border(1.dp, pal.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val sel = i == index
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box {
                        Icon(
                            icons[i], label, Modifier.size(23.dp),
                            tint = if (sel) pal.primary else pal.textDim
                        )
                        val badge = badges.getOrElse(i) { 0 }
                        if (badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 12.dp, y = (-5).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(pal.tertiary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(if (badge > 999) "999+" else badge.toString(), fontSize = 9.sp, color = pal.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label, fontSize = 11.sp,
                        color = if (sel) pal.text else pal.textDim,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(pal.primary, pal.tertiary))
                else Brush.horizontalGradient(listOf(pal.surfaceHigh.copy(alpha = 0.75f), pal.surface.copy(alpha = 0.65f)))
            )
            .border(1.dp, if (selected) Color.Transparent else pal.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (selected) pal.onPrimary else pal.textDim)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) pal.onPrimary else pal.text)
    }
}

@Composable
fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val f by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 520f), label = "sw")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(54.dp)
            .height(30.dp)
            .clip(shape)
            .background(
                if (checked) Brush.horizontalGradient(listOf(pal.primary, pal.tertiary))
                else Brush.horizontalGradient(listOf(pal.surface, pal.surfaceHigh))
            )
            .border(1.dp, pal.border, shape)
            .clickable { onChange(!checked) }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.34f
            val pad = size.height * 0.16f
            val cx = r + pad + f * (size.width - 2f * (r + pad))
            drawCircle(pal.orbGlow.copy(alpha = 0.5f), r * 1.5f, Offset(cx, size.height / 2f))
            drawCircle(pal.orb, r, Offset(cx, size.height / 2f))
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(pal.surfaceHigh.copy(alpha = 0.93f), pal.surface.copy(alpha = 0.88f))))
            .border(1.dp, pal.border, shape)
            .padding(14.dp),
        content = content
    )
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
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), pal.surface.copy(alpha = 0.55f))))
            .border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(label, fontSize = 10.sp, color = pal.textDim)
    }
}

@Composable
fun Waveform(seed: Long, progress: Float, modifier: Modifier = Modifier, bars: Int = 34) {
    val pal = LocalPal.current
    val heights = remember(seed, bars) {
        val r = Random(seed)
        FloatArray(bars) { 0.22f + 0.78f * (0.35f * r.nextFloat() + 0.65f * r.nextFloat()) }
    }
    Canvas(modifier) {
        val gap = size.width / (bars * 2f)
        val bw = gap
        for (i in 0 until bars) {
            val bh = (heights[i] * size.height).coerceAtLeast(2f)
            val x = i * 2f * gap + gap * 0.5f
            val done = (i + 0.5f) / bars <= progress
            drawRoundRect(
                if (done) pal.primary else pal.border.copy(alpha = 0.85f),
                Offset(x, (size.height - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f)
            )
        }
    }
}

@Composable
fun Equalizer(time: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 4
        val gap = size.width / (n * 2f)
        for (i in 0 until n) {
            val f = 0.30f + 0.70f * (0.5f + 0.5f * sin((time * 6f + i * 0.27f) * 2.0 * PI)).toFloat()
            val bh = size.height * f
            drawRoundRect(color, Offset(i * 2f * gap + gap * 0.5f, size.height - bh), Size(gap, bh), CornerRadius(gap / 2f))
        }
    }
}

@Composable
fun Radar(time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val blips = remember {
        val r = Random(11)
        List(5) { Star(0.15f + r.nextFloat() * 0.7f, 0.15f + r.nextFloat() * 0.7f, r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val R = size.minDimension / 2f
        for (i in 1..3) drawCircle(pal.border.copy(alpha = 0.7f), R * i / 3f, c, style = Stroke(1.4f))
        drawLine(pal.border.copy(alpha = 0.6f), Offset(c.x - R, c.y), Offset(c.x + R, c.y), 1.2f)
        drawLine(pal.border.copy(alpha = 0.6f), Offset(c.x, c.y - R), Offset(c.x, c.y + R), 1.2f)
        rotate(time * 360f, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, pal.primary.copy(alpha = 0.45f), Color.Transparent), c),
                R, c
            )
            drawLine(pal.primary, c, Offset(c.x + R, c.y), 2f)
        }
        blips.forEach { b ->
            val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin((time * 2f + b.ph) * 2.0 * PI)).toFloat()
            drawCircle(pal.tertiary, 3.4f + pulse * 2.4f, Offset(b.x * size.width, b.y * size.height), pulse)
        }
    }
}

@Composable
fun IndeterminateBar(time: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    Canvas(modifier) {
        drawRoundRect(pal.border.copy(alpha = 0.5f), size = size, cornerRadius = CornerRadius(size.height / 2f))
        val w = size.width * 0.34f
        val x = ((time * 1.6f) % 1.4f - 0.2f) * size.width
        drawRoundRect(
            Brush.horizontalGradient(listOf(pal.primary, pal.tertiary, pal.secondary)),
            Offset(x.coerceIn(-w, size.width), 0f), Size(w, size.height), CornerRadius(size.height / 2f)
        )
    }
}

@Composable
fun ScrollThumb(state: LazyListState, total: Int, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val frac by remember(total) {
        derivedStateOf {
            if (total <= 0) 0f
            else {
                val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val first = state.firstVisibleItemIndex
                (first.toFloat() / total).coerceIn(0f, 1f) to (visible.toFloat() / total).coerceIn(0.08f, 1f)
            }
        }
    }
    Canvas(modifier) {
        drawRoundRect(pal.border.copy(alpha = 0.35f), size = size, cornerRadius = CornerRadius(size.width / 2f))
        val pair = frac as? Pair<*, *> ?: return@Canvas
        val pos = (pair.first as? Float) ?: 0f
        val len = (pair.second as? Float) ?: 0.2f
        val th = (size.height * len).coerceAtLeast(24f)
        val y = pos * (size.height - th)
        drawRoundRect(
            Brush.verticalGradient(listOf(pal.primary, pal.tertiary)),
            Offset(0f, y), Size(size.width, th), CornerRadius(size.width / 2f)
        )
    }
}
