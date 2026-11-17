package com.example.engine

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class RaycasterRenderer(
    private val textures: ProceduralTextures
) {
    companion object {
        const val WIDTH = 240
        const val HEIGHT = 135
        private const val HALF_H = HEIGHT / 2
        private const val FOV_SCALE = 0.66f
        private const val MAX_RAY_DIST = 16.5f
    }

    private val rawPixels = IntArray(WIDTH * HEIGHT)
    private val postPixels = IntArray(WIDTH * HEIGHT)
    private val zBuffer = FloatArray(WIDTH)
    private val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val imageBitmap: ImageBitmap = bitmap.asImageBitmap()

    private val spriteDistances = ArrayList<Pair<Int, Float>>(32)

    fun renderFrame(
        world: InfiniteWorldGenerator,
        playerX: Float,
        playerY: Float,
        headingRad: Float,
        headBobOffsetPx: Float,
        flashlightOn: Boolean,
        batteryPct: Float,
        healthPct: Float,
        sanityPct: Float,
        timeSec: Float,
        glitch: GlitchTelemetry,
        safetyMode: Boolean,
        items: List<WorldItem>,
        anomalies: List<AnomalyEntity>
    ) {
        val dirX = cos(headingRad)
        val dirY = sin(headingRad)
        val planeX = -dirY * FOV_SCALE
        val planeY = dirX * FOV_SCALE

        val rayDirX0 = dirX - planeX
        val rayDirY0 = dirY - planeY
        val rayDirX1 = dirX + planeX
        val rayDirY1 = dirY + planeY

        val bobShift = headBobOffsetPx.toInt().coerceIn(-5, 5)
        val horizonY = (HALF_H + bobShift).coerceIn(20, HEIGHT - 20)

        // Flashlight flicker when battery is low or during heavy glitch
        val flickerSeed = sin(timeSec * 37f) * sin(timeSec * 19f)
        val flashlightPower = if (!flashlightOn || batteryPct <= 0.5f) {
            0.18f
        } else if (batteryPct < 18f && flickerSeed > 0.55f) {
            0.35f
        } else if (glitch.overallIntensity > 0.65f && flickerSeed > 0.72f) {
            0.40f
        } else {
            1.0f
        }

        val currentBiome = world.getBiomeAt(floor(playerX).toInt(), floor(playerY).toInt())
        val rng = Random((timeSec * 1000f).toLong())

        // 1. Floor and Ceiling Scanline Casting
        for (y in 0 until HEIGHT) {
            val isFloor = y >= horizonY
            val p = if (isFloor) (y - horizonY).coerceAtLeast(1) else (horizonY - y).coerceAtLeast(1)
            val rowDistance = (HALF_H.toFloat() / p.toFloat()).coerceAtMost(MAX_RAY_DIST)

            val stepX = rowDistance * (rayDirX1 - rayDirX0) / WIDTH
            val stepY = rowDistance * (rayDirY1 - rayDirY0) / WIDTH
            var fx = playerX + rowDistance * rayDirX0
            var fy = playerY + rowDistance * rayDirY0

            val distFog = (1f - (rowDistance / MAX_RAY_DIST)).coerceIn(0.03f, 1f)
            val rowOffset = y * WIDTH

            for (x in 0 until WIDTH) {
                val cellX = floor(fx).toInt()
                val cellY = floor(fy).toInt()
                val tx = ((fx - cellX) * 64f).toInt() and 63
                val ty = ((fy - cellY) * 64f).toInt() and 63

                val beamCenterDist = abs(x - WIDTH / 2) / (WIDTH * 0.5f)
                val spotlight = (1f - beamCenterDist * 1.15f).coerceIn(0f, 1f) * flashlightPower
                val light = (distFog * (0.28f + 0.72f * spotlight)).coerceIn(0.03f, 1.0f)

                val baseColor = if (isFloor) {
                    sampleFloorPixel(currentBiome, cellX, cellY, tx, ty, timeSec)
                } else {
                    sampleCeilingPixel(currentBiome, cellX, cellY, tx, ty, timeSec)
                }

                rawPixels[rowOffset + x] = shadeRgb(baseColor, light, currentBiome == BiomeType.RED_CORRIDOR)
                fx += stepX
                fy += stepY
            }
        }

        // 2. Wall DDA Raycasting
        for (x in 0 until WIDTH) {
            val cameraX = 2f * x / WIDTH - 1f
            val rayDirX = dirX + planeX * cameraX
            val rayDirY = dirY + planeY * cameraX

            var mapX = floor(playerX).toInt()
            var mapY = floor(playerY).toInt()

            val deltaDistX = if (rayDirX == 0f) 1e30f else abs(1f / rayDirX)
            val deltaDistY = if (rayDirY == 0f) 1e30f else abs(1f / rayDirY)

            val stepX: Int
            var sideDistX: Float
            if (rayDirX < 0) {
                stepX = -1
                sideDistX = (playerX - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1.0f - playerX) * deltaDistX
            }

            val stepY: Int
            var sideDistY: Float
            if (rayDirY < 0) {
                stepY = -1
                sideDistY = (playerY - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1.0f - playerY) * deltaDistY
            }

            var hitCell = CellType.EMPTY
            var side = 0
            var steps = 0

            while (hitCell == CellType.EMPTY && steps < 28) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }
                if (sideDistX > MAX_RAY_DIST && sideDistY > MAX_RAY_DIST) break
                hitCell = world.getCell(mapX, mapY)
                steps++
            }

            val perpWallDist = if (hitCell == CellType.EMPTY) {
                MAX_RAY_DIST
            } else if (side == 0) {
                (sideDistX - deltaDistX).coerceAtLeast(0.08f)
            } else {
                (sideDistY - deltaDistY).coerceAtLeast(0.08f)
            }
            zBuffer[x] = perpWallDist

            if (hitCell != CellType.EMPTY && perpWallDist < MAX_RAY_DIST) {
                val lineHeight = (HEIGHT / perpWallDist).toInt()
                val drawStart = (-lineHeight / 2 + horizonY).coerceAtLeast(0)
                val drawEnd = (lineHeight / 2 + horizonY).coerceAtMost(HEIGHT - 1)

                var wallX = if (side == 0) {
                    playerY + perpWallDist * rayDirY
                } else {
                    playerX + perpWallDist * rayDirX
                }
                wallX -= floor(wallX)

                var texX = (wallX * ProceduralTextures.TEX_SIZE).toInt() and 63
                if (side == 0 && rayDirX > 0) texX = 63 - texX
                if (side == 1 && rayDirY < 0) texX = 63 - texX

                val wallBiome = world.getBiomeAt(mapX, mapY)
                val tex = if (hitCell == CellType.GLITCH_WALL) {
                    textures.glitchWallTexture
                } else {
                    textures.wallTextures[wallBiome] ?: textures.wallTextures.getValue(BiomeType.MONO_YELLOW)
                }

                val beamCenterDist = abs(x - WIDTH / 2) / (WIDTH * 0.5f)
                val spotlight = (1f - beamCenterDist * 1.1f).coerceIn(0f, 1f) * flashlightPower
                val distFog = (1f - perpWallDist / MAX_RAY_DIST).coerceIn(0.04f, 1f)
                val sideShade = if (side == 1) 0.78f else 1.0f
                val light = (distFog * sideShade * (0.28f + 0.72f * spotlight)).coerceIn(0.04f, 1.0f)

                val stepTex = ProceduralTextures.TEX_SIZE.toFloat() / lineHeight.coerceAtLeast(1)
                var texPos = (drawStart - horizonY + lineHeight / 2f) * stepTex

                for (y in drawStart..drawEnd) {
                    val texY = texPos.toInt() and 63
                    texPos += stepTex
                    val texColor = tex[texY * ProceduralTextures.TEX_SIZE + texX]
                    rawPixels[y * WIDTH + x] = shadeRgb(texColor, light, wallBiome == BiomeType.RED_CORRIDOR)
                }
            }
        }

        // 3. Z-Sorted Billboard Sprites (Items + Anomalies)
        val totalSprites = items.size + anomalies.size
        spriteDistances.clear()
        for (i in items.indices) {
            val item = items[i]
            if (!item.collected) {
                val dx = item.x - playerX
                val dy = item.y - playerY
                val d2 = dx * dx + dy * dy
                if (d2 < MAX_RAY_DIST * MAX_RAY_DIST) {
                    spriteDistances.add(i to d2)
                }
            }
        }
        val itemCount = items.size
        for (i in anomalies.indices) {
            val a = anomalies[i]
            val dx = a.x - playerX
            val dy = a.y - playerY
            val d2 = dx * dx + dy * dy
            if (d2 < MAX_RAY_DIST * MAX_RAY_DIST) {
                spriteDistances.add((itemCount + i) to d2)
            }
        }
        spriteDistances.sortByDescending { it.second }

        val invDet = 1.0f / (planeX * dirY - dirX * planeY)
        for ((encodedIdx, _) in spriteDistances) {
            val isAnomaly = encodedIdx >= itemCount
            val sx: Float
            val sy: Float
            val tex: IntArray
            val scaleW: Float
            val scaleH: Float
            val vMovePx: Float
            val emissive: Boolean

            if (!isAnomaly) {
                val item = items[encodedIdx]
                sx = item.x - playerX
                sy = item.y - playerY
                tex = textures.itemTextures.getValue(item.type)
                scaleW = 0.42f
                scaleH = 0.42f
                vMovePx = 42f
                emissive = item.type == PickupType.ALMOND_WATER
            } else {
                val anomaly = anomalies[encodedIdx - itemCount]
                sx = anomaly.x - playerX
                sy = anomaly.y - playerY
                tex = if (anomaly.type == AnomalyType.MIMIC && !anomaly.mimicRevealed) {
                    textures.itemTextures.getValue(PickupType.TAPE_STABILIZER)
                } else {
                    textures.anomalyTextures.getValue(anomaly.type)
                }
                scaleW = if (anomaly.type == AnomalyType.STALKER) 0.95f else 0.78f
                scaleH = if (anomaly.type == AnomalyType.STALKER) 1.05f else 0.90f
                vMovePx = if (anomaly.type == AnomalyType.PHANTOM) (sin(timeSec * 6f) * 8f) else 8f
                emissive = anomaly.type == AnomalyType.WATCHER || anomaly.type == AnomalyType.PHANTOM
            }

            val transformX = invDet * (dirY * sx - dirX * sy)
            val transformY = invDet * (-planeY * sx + planeX * sy)
            if (transformY <= 0.18f || transformY >= MAX_RAY_DIST) continue

            val spriteScreenX = ((WIDTH / 2) * (1 + transformX / transformY)).toInt()
            val vOffset = (vMovePx / transformY).toInt()
            val spriteHeight = abs((HEIGHT / transformY) * scaleH).toInt().coerceAtMost(HEIGHT * 2)
            val spriteWidth = abs((HEIGHT / transformY) * scaleW).toInt().coerceAtMost(WIDTH * 2)

            val drawStartY = (-spriteHeight / 2 + horizonY + vOffset).coerceAtLeast(0)
            val drawEndY = (spriteHeight / 2 + horizonY + vOffset).coerceAtMost(HEIGHT - 1)
            val drawStartX = (-spriteWidth / 2 + spriteScreenX).coerceAtLeast(0)
            val drawEndX = (spriteWidth / 2 + spriteScreenX).coerceAtMost(WIDTH - 1)

            val distFog = (1f - transformY / MAX_RAY_DIST).coerceIn(0.08f, 1f)
            val centerLight = (0.35f + 0.65f * flashlightPower).coerceIn(0.15f, 1f)
            val spriteLight = if (emissive) maxOf(distFog, 0.55f) else (distFog * centerLight)

            for (stripe in drawStartX..drawEndX) {
                if (transformY < zBuffer[stripe]) {
                    val texX = ((stripe - (-spriteWidth / 2 + spriteScreenX)) *
                        ProceduralTextures.TEX_SIZE / spriteWidth.coerceAtLeast(1)).coerceIn(0, 63)
                    for (y in drawStartY..drawEndY) {
                        val d = (y - vOffset - horizonY + spriteHeight / 2)
                        val texY = (d * ProceduralTextures.TEX_SIZE / spriteHeight.coerceAtLeast(1)).coerceIn(0, 63)
                        val c = tex[texY * ProceduralTextures.TEX_SIZE + texX]
                        val alpha = (c ushr 24) and 0xFF
                        if (alpha > 40) {
                            rawPixels[y * WIDTH + stripe] = shadeRgb(c, spriteLight, false)
                        }
                    }
                }
            }
        }

        // 4. Autogenerated VHS Post-Processing Pass (Tearing, Chromatic Split, Scanlines, Snow, Vignette)
        applyVhsPostProcess(
            glitch = glitch,
            healthPct = healthPct,
            sanityPct = sanityPct,
            timeSec = timeSec,
            safetyMode = safetyMode,
            rng = rng
        )

        bitmap.setPixels(postPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
    }

    private fun sampleFloorPixel(
        biome: BiomeType,
        cellX: Int,
        cellY: Int,
        tx: Int,
        ty: Int,
        timeSec: Float
    ): Int {
        val grain = ((tx * 17 + ty * 31) and 7) - 3
        return when (biome) {
            BiomeType.MONO_YELLOW -> {
                // Damp office carpet with faint stain variation
                val stain = if (((cellX * 73 + cellY * 37) and 7) == 0 && (tx - 32) * (tx - 32) + (ty - 32) * (ty - 32) < 400) -14 else 0
                packRgb(138 + grain + stain, 126 + grain + stain, 76 + grain + stain)
            }
            BiomeType.POOLROOMS -> {
                // Shallow shimmering aquamarine pool water over tiled bottom
                val ripple = (sin((tx + timeSec * 18f) * 0.22f) * 8f).toInt()
                val grout = tx % 16 == 0 || ty % 16 == 0
                if (grout) packRgb(36, 86, 90) else packRgb(56 + ripple, 124 + ripple, 126 + ripple)
            }
            BiomeType.PIPEWORKS -> {
                val grate = (tx % 8 < 2) || (ty % 8 < 2)
                if (grate) packRgb(42, 44, 46) else packRgb(64 + grain, 66 + grain, 68 + grain)
            }
            BiomeType.RED_CORRIDOR -> {
                val centerLine = tx in 28..35
                if (centerLine) packRgb(96, 22, 20) else packRgb(34 + grain, 18, 18)
            }
            BiomeType.VOID_STATIC -> {
                val wire = tx < 3 || ty < 3
                if (wire) packRgb(120, 145, 110) else packRgb(18 + grain, 20 + grain, 18 + grain)
            }
        }
    }

    private fun sampleCeilingPixel(
        biome: BiomeType,
        cellX: Int,
        cellY: Int,
        tx: Int,
        ty: Int,
        timeSec: Float
    ): Int {
        val isLightPanel = ((cellX + cellY) and 1) == 0 && tx in 18..46 && ty in 14..50
        return when (biome) {
            BiomeType.MONO_YELLOW -> {
                val grid = tx == 0 || tx == 32 || ty == 0 || ty == 32
                when {
                    isLightPanel -> {
                        val buzzFlicker = if (((cellX * 13 + cellY) and 7) == 0 && sin(timeSec * 42f) > 0.4f) 0.55f else 1f
                        packRgb((245 * buzzFlicker).toInt(), (240 * buzzFlicker).toInt(), (195 * buzzFlicker).toInt())
                    }
                    grid -> packRgb(74, 72, 54)
                    else -> packRgb(118, 114, 86)
                }
            }
            BiomeType.POOLROOMS -> {
                val skylight = isLightPanel
                if (skylight) packRgb(180, 224, 218) else packRgb(72, 112, 110)
            }
            BiomeType.PIPEWORKS -> {
                val conduit = ty in 26..36
                when {
                    isLightPanel -> packRgb(220, 168, 92)
                    conduit -> packRgb(72, 56, 44)
                    else -> packRgb(34, 36, 38)
                }
            }
            BiomeType.RED_CORRIDOR -> {
                val alarmOn = sin(timeSec * 6f) > 0f
                if (isLightPanel && alarmOn) packRgb(245, 65, 48) else packRgb(28, 12, 12)
            }
            BiomeType.VOID_STATIC -> {
                if ((tx xor ty) and 15 == 0) packRgb(95, 115, 90) else packRgb(10, 12, 10)
            }
        }
    }

    private fun applyVhsPostProcess(
        glitch: GlitchTelemetry,
        healthPct: Float,
        sanityPct: Float,
        timeSec: Float,
        safetyMode: Boolean,
        rng: Random
    ) {
        val tearAmp = if (safetyMode) (glitch.trackingTearIntensity * 4f) else (glitch.trackingTearIntensity * 11f)
        val chroma = if (safetyMode) glitch.chromaticShiftPx.coerceAtMost(2) else glitch.chromaticShiftPx.coerceIn(0, 6)
        val snowChance = if (safetyMode) (glitch.staticSnowAmount * 0.45f) else glitch.staticSnowAmount
        val damagePulse = ((100f - healthPct) / 100f).coerceIn(0f, 1f)
        val sanityLow = ((55f - sanityPct) / 55f).coerceIn(0f, 1f)
        val tearBandY = ((sin(timeSec * 3.1f) * 0.5f + 0.5f) * HEIGHT).toInt()

        for (y in 0 until HEIGHT) {
            val rowOff = y * WIDTH
            val scanlineMul = if (y and 1 == 1) 0.87f else 1.0f

            // Localized horizontal VHS tape tear
            val bandDist = abs(y - tearBandY)
            val rowShift = if (tearAmp > 0.4f && bandDist < 14) {
                (sin(y * 0.45f + timeSec * 45f) * tearAmp * (1f - bandDist / 14f)).toInt()
            } else if (glitch.overallIntensity > 0.55f && y % 29 == ((timeSec * 60).toInt() % 29)) {
                rng.nextInt(-5, 6)
            } else {
                0
            }

            val vy = abs(y - HALF_H).toFloat() / HALF_H

            for (x in 0 until WIDTH) {
                val srcX = (x + rowShift).coerceIn(0, WIDTH - 1)
                val centerPx = rawPixels[rowOff + srcX]

                var r: Int
                var g: Int
                var b: Int
                if (chroma > 0) {
                    val leftX = (srcX - chroma).coerceAtLeast(0)
                    val rightX = (srcX + chroma).coerceAtMost(WIDTH - 1)
                    r = (rawPixels[rowOff + leftX] shr 16) and 0xFF
                    g = (centerPx shr 8) and 0xFF
                    b = rawPixels[rowOff + rightX] and 0xFF
                } else {
                    r = (centerPx shr 16) and 0xFF
                    g = (centerPx shr 8) and 0xFF
                    b = centerPx and 0xFF
                }

                // CRT vignette + damage crimson tint
                val vx = abs(x - WIDTH / 2).toFloat() / (WIDTH / 2)
                val vigDist = hypot(vx * 0.85f, vy * 0.85f)
                val vignette = (1.06f - vigDist * (0.42f + sanityLow * 0.22f)).coerceIn(0.28f, 1f) * scanlineMul

                r = (r * vignette).toInt()
                g = (g * vignette).toInt()
                b = (b * vignette).toInt()

                if (damagePulse > 0.08f && vigDist > 0.55f) {
                    val redBoost = (damagePulse * (vigDist - 0.55f) * 260f).toInt()
                    r = (r + redBoost).coerceAtMost(255)
                }

                // Random VHS snow grain
                if (rng.nextFloat() < snowChance) {
                    val grain = rng.nextInt(140, 240)
                    r = grain
                    g = grain
                    b = grain - 10
                }

                postPixels[rowOff + x] = (0xFF shl 24) or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
            }
        }
    }

    private fun shadeRgb(argb: Int, light: Float, crimsonTint: Boolean): Int {
        var r = (((argb ushr 16) and 0xFF) * light).toInt()
        val g = (((argb ushr 8) and 0xFF) * light).toInt()
        val b = ((argb and 0xFF) * light).toInt()
        if (crimsonTint) {
            r = (r * 1.15f).toInt().coerceAtMost(255)
        }
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun packRgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)
    }
}
