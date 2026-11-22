package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.ConnectionRouteType
import com.example.mesh.DeviceStatus
import com.example.mesh.MAX_GROUP_DEVICES
import com.example.mesh.RadarDeviceNode
import com.example.mesh.SignalQualityTier
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RadarTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Animated circular Bluetooth mesh radar scope displaying up to 20 group nodes.
 *
 * Radial placement represents logical Signal Quality / Link Tier (NEVER physical distance),
 * and angular placement distributes unique device slots (#01..#20) cleanly around 360 degrees
 * (NEVER fabricated GPS or compass bearings).
 */
@Composable
fun RadarScopeCanvas(
    nodes: List<RadarDeviceNode>,
    selectedNodeAddress: String?,
    isScanningActive: Boolean,
    isPttTransmitting: Boolean,
    localSlotNumber: Int,
    onSelectNode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_fraction"
    )

    val unitCodeStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
    val ringLabelStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp
        )
    }

    val nodeBySlot = remember(nodes) { nodes.associateBy { it.slotNumber } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Live Bluetooth mesh radar scope showing ${nodes.size} of $MAX_GROUP_DEVICES group devices"
            }
            .testTag("radar_scope_canvas"),
        shape = RoundedCornerShape(24.dp),
        color = radarColors.chassisBg,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, radarColors.cardBorder, RoundedCornerShape(24.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Radar,
                        contentDescription = null,
                        tint = if (isScanningActive) radarColors.inRange else radarColors.mutedText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isScanningActive) "SCANNING MESH" else "RADAR STANDBY",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isScanningActive) radarColors.inRange else radarColors.mutedText
                    )
                }
                Text(
                    text = "SLOTS ${nodes.size}/$MAX_GROUP_DEVICES • LOCAL #${localSlotNumber.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nodes) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxRadius = (minOf(size.width, size.height) / 2f) * 0.88f
                                val hit = nodes.minByOrNull { node ->
                                    val pos = computeNodeOffset(node, center, maxRadius)
                                    hypot(pos.x - tapOffset.x, pos.y - tapOffset.y)
                                }
                                if (hit != null) {
                                    val pos = computeNodeOffset(hit, center, maxRadius)
                                    val dist = hypot(pos.x - tapOffset.x, pos.y - tapOffset.y)
                                    if (dist <= 56f) {
                                        onSelectNode(hit.address)
                                    }
                                }
                            }
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = (minOf(size.width, size.height) / 2f) * 0.88f

                    // Background radial depth
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                radarColors.inRange.copy(alpha = 0.08f),
                                radarColors.chassisBg,
                                Color(0xFF04080D)
                            ),
                            center = center,
                            radius = outerRadius * 1.1f
                        ),
                        radius = outerRadius * 1.08f,
                        center = center
                    )

                    // Signal Quality Tier rings (Strong, Moderate/Relay, Weak, Lost perimeter)
                    val ringFractions = listOf(0.28f, 0.54f, 0.78f, 1.0f)
                    val ringNames = listOf("STRONG", "GOOD/RELAY", "WEAK", "LOST")
                    val gridStrokeColor = radarColors.cardBorder.copy(alpha = 0.75f)

                    ringFractions.forEachIndexed { idx, frac ->
                        val r = outerRadius * frac
                        val isLostRing = idx == ringFractions.lastIndex
                        drawCircle(
                            color = if (isLostRing) radarColors.outOfRange.copy(alpha = 0.35f) else gridStrokeColor,
                            radius = r,
                            center = center,
                            style = Stroke(
                                width = if (isLostRing) 1.5.dp.toPx() else 1.dp.toPx(),
                                pathEffect = if (idx == 2) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                            )
                        )
                        drawRingLabel(
                            textMeasurer = textMeasurer,
                            text = ringNames[idx],
                            style = ringLabelStyle,
                            color = if (isLostRing) radarColors.outOfRange.copy(alpha = 0.75f) else radarColors.mutedText,
                            topCenter = Offset(center.x, center.y - r + 4.dp.toPx())
                        )
                    }

                    // Crosshairs and 30-degree sector ticks
                    for (deg in 0 until 360 step 30) {
                        val rad = deg * (PI / 180.0)
                        val isMajorAxis = deg % 90 == 0
                        val innerStart = if (isMajorAxis) outerRadius * 0.08f else outerRadius * 0.94f
                        val start = Offset(
                            center.x + (innerStart * cos(rad)).toFloat(),
                            center.y + (innerStart * sin(rad)).toFloat()
                        )
                        val end = Offset(
                            center.x + (outerRadius * cos(rad)).toFloat(),
                            center.y + (outerRadius * sin(rad)).toFloat()
                        )
                        drawLine(
                            color = gridStrokeColor.copy(alpha = if (isMajorAxis) 0.55f else 0.38f),
                            start = start,
                            end = end,
                            strokeWidth = if (isMajorAxis) 1.dp.toPx() else 0.8.dp.toPx()
                        )
                    }

                    // Animated phosphor radar sweep cone
                    if (isScanningActive) {
                        val sweepBrush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            0.94f to radarColors.inRange.copy(alpha = 0.14f),
                            1.0f to radarColors.inRange.copy(alpha = 0.48f),
                            center = center
                        )
                        drawArc(
                            brush = sweepBrush,
                            startAngle = sweepAngle - 360f,
                            sweepAngle = 90f,
                            useCenter = true,
                            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                            size = Size(outerRadius * 2f, outerRadius * 2f)
                        )
                        val beamRad = (sweepAngle) * (PI / 180.0)
                        drawLine(
                            color = radarColors.inRange.copy(alpha = 0.85f),
                            start = center,
                            end = Offset(
                                center.x + (outerRadius * cos(beamRad)).toFloat(),
                                center.y + (outerRadius * sin(beamRad)).toFloat()
                            ),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Precompute positions for all nodes
                    val positions = nodes.associate { it.slotNumber to computeNodeOffset(it, center, outerRadius) }

                    // Draw multi-hop relay links (Center -> Relay Host -> Relayed Node)
                    nodes.forEach { node ->
                        val relay = node.relayPath
                        if (node.routeType == ConnectionRouteType.RELAY && relay != null) {
                            val targetPos = positions[node.slotNumber] ?: return@forEach
                            val viaPos = positions[relay.viaSlotNumber]
                            val routeColor = if (node.status == DeviceStatus.IN_RANGE) {
                                radarColors.relayAccent
                            } else {
                                radarColors.weakUnstable
                            }
                            if (viaPos != null) {
                                drawLine(
                                    color = routeColor.copy(alpha = 0.5f),
                                    start = center,
                                    end = viaPos,
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )
                                drawLine(
                                    color = routeColor.copy(alpha = 0.85f),
                                    start = viaPos,
                                    end = targetPos,
                                    strokeWidth = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                                )
                            } else {
                                drawLine(
                                    color = routeColor.copy(alpha = 0.65f),
                                    start = center,
                                    end = targetPos,
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                                )
                            }
                        }
                    }

                    // Draw center local transceiver node ("YOU")
                    if (isPttTransmitting) {
                        drawCircle(
                            color = radarColors.pttAccent.copy(alpha = 0.32f * pulseFraction),
                            radius = 22.dp.toPx(),
                            center = center
                        )
                    }
                    drawCircle(
                        color = if (isPttTransmitting) radarColors.pttAccent else radarColors.relayAccent,
                        radius = 8.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = center
                    )

                    // Draw up to 20 device indicators with distinct accessible shapes + colors + slot tags
                    nodes.forEach { node ->
                        val pos = positions[node.slotNumber] ?: return@forEach
                        val isSelected = node.address == selectedNodeAddress
                        val nodeColor = when (node.status) {
                            DeviceStatus.IN_RANGE -> radarColors.inRange
                            DeviceStatus.WEAK_UNSTABLE -> radarColors.weakUnstable
                            DeviceStatus.OUT_OF_RANGE -> radarColors.outOfRange
                        }

                        // Selection reticle
                        if (isSelected) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = 17.dp.toPx(),
                                center = pos,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            )
                        }

                        // PTT Active glow from remote mesh peer
                        if (node.isTransmittingPtt && node.status != DeviceStatus.OUT_OF_RANGE) {
                            drawCircle(
                                color = radarColors.pttAccent.copy(alpha = 0.45f * pulseFraction),
                                radius = 18.dp.toPx(),
                                center = pos
                            )
                        }

                        when (node.status) {
                            DeviceStatus.IN_RANGE -> {
                                if (node.routeType == ConnectionRouteType.RELAY) {
                                    // Relayed GREEN node: Cyan relay halo + emerald core
                                    drawCircle(
                                        color = radarColors.relayAccent.copy(alpha = 0.28f),
                                        radius = 14.dp.toPx(),
                                        center = pos
                                    )
                                    drawCircle(
                                        color = radarColors.relayAccent,
                                        radius = 11.dp.toPx(),
                                        center = pos,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawCircle(
                                        color = nodeColor,
                                        radius = 7.dp.toPx(),
                                        center = pos
                                    )
                                } else {
                                    // Direct GREEN node: Solid emerald beacon + subtle pulse halo
                                    drawCircle(
                                        color = nodeColor.copy(alpha = 0.22f * pulseFraction),
                                        radius = 13.dp.toPx(),
                                        center = pos
                                    )
                                    drawCircle(
                                        color = nodeColor,
                                        radius = 7.5.dp.toPx(),
                                        center = pos
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.85f),
                                        radius = 2.5.dp.toPx(),
                                        center = pos
                                    )
                                }
                            }

                            DeviceStatus.WEAK_UNSTABLE -> {
                                // YELLOW: Accessible diamond shape + amber border
                                val d = 9.dp.toPx()
                                val diamond = Path().apply {
                                    moveTo(pos.x, pos.y - d)
                                    lineTo(pos.x + d, pos.y)
                                    lineTo(pos.x, pos.y + d)
                                    lineTo(pos.x - d, pos.y)
                                    close()
                                }
                                drawPath(path = diamond, color = nodeColor.copy(alpha = 0.25f))
                                drawPath(
                                    path = diamond,
                                    color = nodeColor,
                                    style = Stroke(width = 2.2.dp.toPx())
                                )
                                drawCircle(color = nodeColor, radius = 3.dp.toPx(), center = pos)
                            }

                            DeviceStatus.OUT_OF_RANGE -> {
                                // RED: Hollow circle with X cross (accessible shape + color)
                                val r = 8.dp.toPx()
                                drawCircle(
                                    color = radarColors.chassisBg,
                                    radius = r,
                                    center = pos
                                )
                                drawCircle(
                                    color = nodeColor,
                                    radius = r,
                                    center = pos,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                val c = r * 0.55f
                                drawLine(
                                    color = nodeColor,
                                    start = Offset(pos.x - c, pos.y - c),
                                    end = Offset(pos.x + c, pos.y + c),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = nodeColor,
                                    start = Offset(pos.x + c, pos.y - c),
                                    end = Offset(pos.x - c, pos.y + c),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Unique unit number badge (#01..#20)
                        drawNodeTag(
                            textMeasurer = textMeasurer,
                            tag = node.unitCode,
                            style = unitCodeStyle,
                            accentColor = nodeColor,
                            bgColor = radarColors.chassisBg.copy(alpha = 0.88f),
                            anchor = Offset(pos.x, pos.y + 13.dp.toPx()),
                            canvasSize = size
                        )
                    }

                    // Touch hint if empty
                    if (nodeBySlot.isEmpty()) {
                        drawCircle(
                            color = radarColors.cardBorder.copy(alpha = 0.35f),
                            radius = outerRadius * 0.54f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Rings indicate signal quality & link tier — not physical distance or GPS position",
                style = MaterialTheme.typography.labelSmall,
                color = radarColors.mutedText
            )
        }
    }
}

private fun computeNodeOffset(
    node: RadarDeviceNode,
    center: Offset,
    outerRadius: Float
): Offset {
    // 20 deterministic angular sectors around 360° starting from top (-90°), avoiding overlap
    val angleDeg = -90.0 + ((node.slotNumber - 1) * (360.0 / MAX_GROUP_DEVICES))
    val angleRad = angleDeg * (PI / 180.0)

    // Stagger odd/even slots slightly within each tier ring so dense 20-phone groups stay legible
    val stagger = if (node.slotNumber % 2 == 0) 0.04f else -0.04f

    val radiusFraction = when (node.status) {
        DeviceStatus.OUT_OF_RANGE -> 0.96f
        DeviceStatus.WEAK_UNSTABLE -> 0.74f + stagger
        DeviceStatus.IN_RANGE -> {
            if (node.routeType == ConnectionRouteType.RELAY) {
                0.55f + stagger
            } else {
                when (node.signalQualityTier) {
                    SignalQualityTier.EXCELLENT -> 0.27f + (stagger * 0.5f)
                    SignalQualityTier.GOOD -> 0.43f + stagger
                    SignalQualityTier.FAIR -> 0.56f + stagger
                    SignalQualityTier.POOR -> 0.70f + stagger
                    SignalQualityTier.UNAVAILABLE -> 0.52f + stagger
                }
            }
        }
    }.coerceIn(0.20f, 0.98f)

    val r = outerRadius * radiusFraction
    return Offset(
        x = center.x + (r * cos(angleRad)).toFloat(),
        y = center.y + (r * sin(angleRad)).toFloat()
    )
}

private fun DrawScope.drawRingLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    topCenter: Offset
) {
    val measured = textMeasurer.measure(text, style)
    val x = (topCenter.x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
    val y = topCenter.y.coerceIn(0f, size.height - measured.size.height)
    drawText(
        textLayoutResult = measured,
        color = color,
        topLeft = Offset(x, y)
    )
}

private fun DrawScope.drawNodeTag(
    textMeasurer: TextMeasurer,
    tag: String,
    style: TextStyle,
    accentColor: Color,
    bgColor: Color,
    anchor: Offset,
    canvasSize: Size
) {
    val measured = textMeasurer.measure(tag, style)
    val padX = 4.dp.toPx()
    val padY = 1.dp.toPx()
    val boxW = measured.size.width + padX * 2f
    val boxH = measured.size.height + padY * 2f

    val left = (anchor.x - boxW / 2f).coerceIn(2f, (canvasSize.width - boxW - 2f).coerceAtLeast(2f))
    val top = anchor.y.coerceIn(2f, (canvasSize.height - boxH - 2f).coerceAtLeast(2f))

    drawRoundRect(
        color = bgColor,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
    drawText(
        textLayoutResult = measured,
        color = accentColor,
        topLeft = Offset(left + padX, top + padY)
    )
}
