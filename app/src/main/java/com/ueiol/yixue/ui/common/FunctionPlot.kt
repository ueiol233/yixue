package com.ueiol.yixue.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.domain.plot.ExpressionParser
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import kotlin.math.abs

data class PlotSpec(
    val expressions: List<String>,
    val xMin: Double = -10.0,
    val xMax: Double = 10.0,
    val yMin: Double = -8.0,
    val yMax: Double = 8.0
)

object PlotParser {
    /** 解析绘图指令行："y=x^2" 或 "y=x^2, x=-5..5" 或 "..., x=-5..5, y=-2..8"。 */
    fun parseLines(lines: List<String>): PlotSpec? {
        val exprs = mutableListOf<String>()
        var xMin = -10.0; var xMax = 10.0
        var yMin: Double? = null; var yMax: Double? = null
        for (raw in lines) {
            val line = raw.trim().trimStart('-', '>', ' ')
            if (line.isEmpty()) continue
            val parts = splitTopLevel(line)
            // "y=x^2" / "f(x)=x^2" / "y = x^2" 只保留右侧表达式，否则解析器会因 'y' 报错
            val expr = parts[0].trim()
                .replace(Regex("(?i)^[yf]\\s*(\\(\\s*x\\s*\\))?\\s*="), "")
                .trim()
            if (expr.isEmpty()) continue
            exprs.add(expr)
            for (k in 1 until parts.size) {
                val seg = parts[k].trim().replace(" ", "")
                val m = Regex("([xy])=(-?[0-9.]+)\\.\\.(-?[0-9.]+)").find(seg) ?: continue
                val lo = m.groupValues[2].toDoubleOrNull() ?: continue
                val hi = m.groupValues[3].toDoubleOrNull() ?: continue
                when (m.groupValues[1]) {
                    "x" -> { xMin = lo; xMax = hi }
                    "y" -> { yMin = lo; yMax = hi }
                }
            }
        }
        if (exprs.isEmpty()) return null
        return PlotSpec(exprs, xMin, xMax, yMin ?: -8.0, yMax ?: 8.0)
    }

    /** 只按括号外的逗号切分，避免 max(x,0) 这类函数被拆坏。 */
    private fun splitTopLevel(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        s.forEach { c ->
            when (c) {
                '(' -> { depth++; sb.append(c) }
                ')' -> { depth--; sb.append(c) }
                ',' -> if (depth == 0) { out.add(sb.toString()); sb.clear() } else sb.append(c)
                else -> sb.append(c)
            }
        }
        out.add(sb.toString())
        return out
    }
}

/**
 * 函数图像组件（Compose Canvas）：网格 + 坐标轴 + 多条曲线。
 *
 * [allowGesture] 为 false 时不拦截任何拖动事件，列表/聊天里可以正常上下滑动，
 * 点击交给外层的 clickable 处理（打开大图）。[singleFingerPan] 仅在大图查看器里开启。
 */
@Composable
fun FunctionPlot(
    spec: PlotSpec,
    modifier: Modifier = Modifier,
    fillParent: Boolean = false,
    allowGesture: Boolean = false,
    singleFingerPan: Boolean = false
) {
    // 保留表达式与编译结果的配对，图例需要展示原始表达式文本
    val compiled = remember(spec) {
        spec.expressions.mapNotNull { e ->
            runCatching { ExpressionParser.compile(e) }.getOrNull()?.let { fn -> e to fn }
        }
    }
    var view by remember(spec) {
        mutableStateOf(doubleArrayOf(spec.xMin, spec.xMax, spec.yMin, spec.yMax))
    }
    val curveColors = listOf(
        MaterialTheme.colorScheme.primary, YxExt.subjectGreen, YxExt.subjectOrange,
        YxExt.subjectPurple, YxExt.subjectCyan, YxExt.subjectRed
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline

    val sizeModifier = if (fillParent) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(220.dp)

    // 表达式全部解析失败时给出提示，而不是留一片空白
    if (compiled.isEmpty()) {
        Box(
            modifier = modifier.then(sizeModifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "函数表达式无法解析，试试 y=x^2",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .then(
                if (allowGesture) {
                    Modifier.pointerInput(spec, singleFingerPan) {
                        awaitEachGesture {
                            // 单指默认不消费事件：列表可以继续上下滚动；大图里才允许单指平移
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val handle = pressed >= 2 || (singleFingerPan && pressed == 1)
                                if (handle && (zoom != 1f || pan != Offset.Zero) && size.width > 0 && size.height > 0) {
                                    // 必须整体替换数组：原地改元素不触发状态变化，Canvas 永远不会重绘
                                    val cx = (view[0] + view[1]) / 2
                                    val cy = (view[2] + view[3]) / 2
                                    val halfW = (view[1] - view[0]) / 2 / zoom
                                    val halfH = (view[3] - view[2]) / 2 / zoom
                                    var left = cx - halfW
                                    var right = cx + halfW
                                    var top = cy + halfH
                                    var bottom = cy - halfH
                                    val w = right - left
                                    val h = top - bottom
                                    val dx = pan.x / size.width * w
                                    val dy = pan.y / size.height * h
                                    left -= dx; right -= dx
                                    top += dy; bottom += dy
                                    view = doubleArrayOf(left, right, bottom, top)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
        val xMin = view[0]; val xMax = view[1]; val yMin = view[2]; val yMax = view[3]
        val w = size.width; val h = size.height
        fun sx(x: Double) = ((x - xMin) / (xMax - xMin) * w).toFloat()
        fun sy(y: Double) = (h - ((y - yMin) / (yMax - yMin) * h)).toFloat()

        // 网格
        val xStep = niceStep(xMax - xMin)
        val yStep = niceStep(yMax - yMin)
        var gx = Math.floor(xMin / xStep) * xStep
        while (gx <= xMax) { drawLine(gridColor, Offset(sx(gx), 0f), Offset(sx(gx), h), 1f); gx += xStep }
        var gy = Math.floor(yMin / yStep) * yStep
        while (gy <= yMax) { drawLine(gridColor, Offset(0f, sy(gy)), Offset(w, sy(gy)), 1f); gy += yStep }
        // 坐标轴
        if (yMin <= 0 && yMax >= 0) drawLine(axisColor, Offset(sx(0.0), 0f), Offset(sx(0.0), h), 2f)
        if (xMin <= 0 && xMax >= 0) drawLine(axisColor, Offset(0f, sy(0.0)), Offset(w, sy(0.0)), 2f)

        // 曲线
        val n = 300
        compiled.forEachIndexed { idx, pair ->
            val fn = pair.second
            val color = curveColors[idx % curveColors.size]
            val path = Path()
            var drawing = false
            var prevY: Double? = null
            for (k in 0..n) {
                val x = xMin + (xMax - xMin) * k / n
                // try/catch 直取结果，避免每帧 300×曲线数 次 Result 对象分配（拖动/缩放时尤其明显）
                val y = try { fn(x) } catch (_: Throwable) { Double.NaN }
                if (y.isNaN() || y.isInfinite() || (prevY != null && abs(y - prevY) > (yMax - yMin) * 4)) {
                    drawing = false; prevY = if (y.isNaN()) null else y
                    continue
                }
                val px = sx(x); val py = sy(y)
                if (!drawing) { path.moveTo(px, py); drawing = true } else path.lineTo(px, py)
                prevY = y
            }
            drawPath(path, color, style = Stroke(width = 5f))
        }
        }

        // 多条曲线时叠加图例，标明每条曲线的颜色对应
        if (compiled.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        RoundedCornerShape(YxRadius.ExtraMini)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                compiled.forEachIndexed { idx, pair ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(curveColors[idx % curveColors.size], CircleShape)
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            pair.first,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun niceStep(range: Double): Double {
    val raw = range / 10
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val norm = raw / mag
    val mult = when {
        norm < 1.5 -> 1.0; norm < 3.0 -> 2.0; norm < 7.0 -> 5.0; else -> 10.0
    }
    return mult * mag
}