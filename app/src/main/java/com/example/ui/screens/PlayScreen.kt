package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.engine.CamcorderSettings
import com.example.engine.GameHudState
import com.example.engine.ThreatTier
import com.example.ui.theme.SanityEmerald
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun PlayScreen(
    hudState: GameHudState,
    settings: CamcorderSettings,
    frameBitmap: ImageBitmap,
    onMoveInput: (forward: Float, right: Float) -> Unit,
    onLookDeltaPx: (Float) -> Unit,
    onSprintChanged: (Boolean) -> Unit,
    onToggleFlashlight: () -> Unit,
    onUseAlmondWater: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEjectTape: () -> Unit,
    onUpdateSettings: ((CamcorderSettings) -> CamcorderSettings) -> Unit,
    onGameOver: () -> Unit
) {
    LaunchedEffect(hudState.isGameOver) {
        if (hudState.isGameOver) {
            onGameOver()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = !hudState.isGameOver) {
        if (hudState.isPaused) onResume() else onPause()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isLandscapeOrWide = maxWidth > maxHeight

        if (isLandscapeOrWide) {
            // Full-bleed camcorder viewfinder with translucent floating edge controls
            Box(modifier = Modifier.fillMaxSize()) {
                ViewfinderCanvas(
                    hudState = hudState,
                    settings = settings,
                    frameBitmap = frameBitmap,
                    onLookDeltaPx = onLookDeltaPx,
                    onPause = onPause,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    AnalogMoveStick(
                        onMoveInput = onMoveInput,
                        modifier = Modifier.size(132.dp)
                    )
                    ActionButtonsCluster(
                        hudState = hudState,
                        onSprintChanged = onSprintChanged,
                        onToggleFlashlight = onToggleFlashlight,
                        onUseAlmondWater = onUseAlmondWater
                    )
                }
            }
        } else {
            // Portrait Handheld Layout: Upper Viewfinder + Lower Camcorder Control Deck
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                ViewfinderCanvas(
                    hudState = hudState,
                    settings = settings,
                    frameBitmap = frameBitmap,
                    onLookDeltaPx = onLookDeltaPx,
                    onPause = onPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                )

                // Tactile Camcorder Control Deck
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            AnalogMoveStick(
                                onMoveInput = onMoveInput,
                                modifier = Modifier.size(142.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "MOVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {
                            // Dedicated Look Drag Pad so right thumb never blocks viewfinder
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var lastX = down.position.x
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                val dx = change.position.x - lastX
                                                lastX = change.position.x
                                                if (dx != 0f) onLookDeltaPx(dx)
                                                change.consume()
                                            }
                                        }
                                    }
                                    .testTag("look_drag_pad")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "◀  DRAG TO TURN  ▶",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            ActionButtonsCluster(
                                hudState = hudState,
                                onSprintChanged = onSprintChanged,
                                onToggleFlashlight = onToggleFlashlight,
                                onUseAlmondWater = onUseAlmondWater
                            )
                        }
                    }
                }
            }
        }

        // Pause Modal Overlay
        if (hudState.isPaused) {
            PauseOverlayDialog(
                hudState = hudState,
                settings = settings,
                onResume = onResume,
                onEjectTape = onEjectTape,
                onUpdateSettings = onUpdateSettings
            )
        }
    }
}

@Composable
private fun ViewfinderCanvas(
    hudState: GameHudState,
    settings: CamcorderSettings,
    frameBitmap: ImageBitmap,
    onLookDeltaPx: (Float) -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewfinderCd = stringResource(R.string.cd_viewfinder)
    Box(
        modifier = modifier
            .background(Color.Black)
            .semantics { contentDescription = viewfinderCd }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        if (dx != 0f) onLookDeltaPx(dx)
                        change.consume()
                    }
                }
            }
    ) {
        val frameTick = hudState.frameVersion
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick so Compose invalidates whenever a new raycaster frame arrives
            @Suppress("UNUSED_VARIABLE")
            val tick = frameTick
            drawImage(
                image = frameBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(frameBitmap.width, frameBitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.None
            )

            // Viewfinder corner framing brackets
            val pad = 14.dp.toPx()
            val len = 18.dp.toPx()
            val bracketColor = Color(0xFFEAE6D2).copy(alpha = 0.5f)
            val stroke = 2.dp.toPx()
            // Top-left
            drawLine(bracketColor, Offset(pad, pad), Offset(pad + len, pad), stroke)
            drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + len), stroke)
            // Top-right
            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), stroke)
            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), stroke)
            // Bottom-left
            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), stroke)
            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), stroke)
            // Bottom-right
            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), stroke)
            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), stroke)
        }

        // Top VHS Telemetry HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val blinkOn = ((hudState.elapsedSeconds * 2f).toInt() % 2) == 0
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (blinkOn) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hudState.glitch.corruptTimestamp) {
                            "REC // ERR-??:??"
                        } else {
                            "REC // ${formatDuration(hudState.elapsedSeconds.toInt())}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "DIST %.1fm", hudState.distanceFromSpawnMeters),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                            .testTag("pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.cd_pause_button),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Biome & Threat Tier row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${hudState.currentBiome.sectorCode} // ${hudState.currentBiome.displayName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )

                val threatColor = when (hudState.threatTier) {
                    ThreatTier.DORMANT -> SanityEmerald
                    ThreatTier.UNSTABLE -> MaterialTheme.colorScheme.primary
                    ThreatTier.HOSTILE -> MaterialTheme.colorScheme.tertiary
                    ThreatTier.FRACTURED -> MaterialTheme.colorScheme.error
                }
                Surface(
                    color = threatColor.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, threatColor),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = hudState.threatTier.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = threatColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Center Glitch Warning Banner & Item Toasts
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = hudState.glitch.warningBanner != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.82f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = hudState.glitch.warningBanner ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
        }

        // Bottom Viewfinder Bars: HP / Sanity / Battery / Stamina + Spawn Compass
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .width(176.dp)
                    .background(Color.Black.copy(alpha = 0.52f), MaterialTheme.shapes.small)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HudBar("VIT", hudState.health / 100f, MaterialTheme.colorScheme.tertiary)
                HudBar("SIG", hudState.sanity / 100f, MaterialTheme.colorScheme.secondary)
                HudBar("BAT", hudState.battery / 100f, MaterialTheme.colorScheme.primary)
                HudBar("STM", hudState.stamina / 100f, SanityEmerald)
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hudState.statusToast?.let { toast ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = toast,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (settings.showSignalCompass) {
                    SpawnBearingDial(
                        headingDeg = hudState.headingDegrees,
                        spawnBearingDeg = hudState.spawnBearingDegrees,
                        glitchIntensity = hudState.glitch.overallIntensity
                    )
                }
            }
        }
    }
}

@Composable
private fun HudBar(label: String, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(30.dp)
        )
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
        )
    }
}

@Composable
private fun SpawnBearingDial(
    headingDeg: Float,
    spawnBearingDeg: Float,
    glitchIntensity: Float
) {
    val jitter = if (glitchIntensity > 0.55f) ((System.currentTimeMillis() % 29) - 14).toFloat() else 0f
    val relRad = Math.toRadians((spawnBearingDeg - headingDeg + jitter).toDouble()).toFloat()
    val ringColor = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.primary

    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = CircleShape,
        border = BorderStroke(1.dp, ringColor),
        modifier = Modifier.size(44.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.34f
            // Direction relative to player facing up (-90 deg on canvas)
            val canvasAngle = relRad - (Math.PI.toFloat() / 2f)
            val tip = Offset(c.x + cos(canvasAngle) * r, c.y + sin(canvasAngle) * r)
            drawLine(arrowColor, c, tip, strokeWidth = 2.5.dp.toPx())
            drawCircle(arrowColor, radius = 3.dp.toPx(), center = tip)
        }
    }
}

@Composable
private fun AnalogMoveStick(
    onMoveInput: (forward: Float, right: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val stickCd = stringResource(R.string.cd_move_stick)
    val ringColor = MaterialTheme.colorScheme.outline
    val knobColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .semantics { contentDescription = stickCd }
            .testTag("move_joystick")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val maxRadius = (size.width.coerceAtMost(size.height) * 0.38f).coerceAtLeast(1f)
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun updateFromPos(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadius) delta * (maxRadius / dist) else delta
                        knobOffset = clamped
                        // Up on screen (-Y) = forward (+1), Right on screen (+X) = strafe right (+1)
                        onMoveInput(-clamped.y / maxRadius, clamped.x / maxRadius)
                    }

                    updateFromPos(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        updateFromPos(change.position)
                        change.consume()
                    }
                    knobOffset = Offset.Zero
                    onMoveInput(0f, 0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension * 0.44f
            drawCircle(color = bgColor.copy(alpha = 0.85f), radius = outerR, center = center)
            drawCircle(color = ringColor, radius = outerR, center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = knobColor, radius = outerR * 0.42f, center = center + knobOffset)
        }
    }
}

@Composable
private fun ActionButtonsCluster(
    hudState: GameHudState,
    onSprintChanged: (Boolean) -> Unit,
    onToggleFlashlight: () -> Unit,
    onUseAlmondWater: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Almond Water Consumable
        OutlinedButton(
            onClick = onUseAlmondWater,
            enabled = hudState.almondWaterCount > 0,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("almond_water_button")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.LocalDrink,
                    contentDescription = stringResource(R.string.cd_almond_water),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "x${hudState.almondWaterCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Flashlight Toggle
        Button(
            onClick = onToggleFlashlight,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hudState.flashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hudState.flashlightOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("flashlight_button")
        ) {
            Icon(
                imageVector = if (hudState.flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                contentDescription = stringResource(R.string.cd_flashlight_toggle),
                modifier = Modifier.size(20.dp)
            )
        }

        // Hold-to-Sprint Pad
        var sprintingPressed by remember { mutableStateOf(false) }
        val sprintCd = stringResource(R.string.cd_sprint_button)
        Surface(
            color = if (sprintingPressed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                1.dp,
                if (sprintingPressed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(width = 76.dp, height = 54.dp)
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = sprintCd }
                .testTag("sprint_button")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        sprintingPressed = true
                        onSprintChanged(true)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                        }
                        sprintingPressed = false
                        onSprintChanged(false)
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.label_sprint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PauseOverlayDialog(
    hudState: GameHudState,
    settings: CamcorderSettings,
    onResume: () -> Unit,
    onEjectTape: () -> Unit,
    onUpdateSettings: ((CamcorderSettings) -> CamcorderSettings) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_paused),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Current Depth: %.1fm  |  Peak: %.1fm",
                        hudState.distanceFromSpawnMeters,
                        hudState.peakDistanceMeters
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Camcorder Audio", style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { onUpdateSettings { it.copy(audioEnabled = !it.audioEnabled) } },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (settings.audioEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Toggle audio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Turn Sensitivity", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(Locale.US, "%.2fx", settings.lookSensitivity),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.lookSensitivity,
                        onValueChange = { s -> onUpdateSettings { it.copy(lookSensitivity = s) } },
                        valueRange = 0.4f..2.2f
                    )
                }

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("resume_tape_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_resume), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onEjectTape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("eject_tape_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_eject_tape),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
