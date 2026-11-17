package com.example.engine

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class ProceduralTextures {

    companion object {
        const val TEX_SIZE = 64
    }

    val wallTextures: Map<BiomeType, IntArray>
    val glitchWallTexture: IntArray
    val itemTextures: Map<PickupType, IntArray>
    val anomalyTextures: Map<AnomalyType, IntArray>

    init {
        wallTextures = BiomeType.entries.associateWith { generateWallTexture(it) }
        glitchWallTexture = generateGlitchWallTexture()
        itemTextures = PickupType.entries.associateWith { generateItemTexture(it) }
        anomalyTextures = AnomalyType.entries.associateWith { generateAnomalyTexture(it) }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 0xFF) shl 24) or
            ((r.coerceIn(0, 255) and 0xFF) shl 16) or
            ((g.coerceIn(0, 255) and 0xFF) shl 8) or
            (b.coerceIn(0, 255) and 0xFF)
    }

    private fun generateWallTexture(biome: BiomeType): IntArray {
        val pixels = IntArray(TEX_SIZE * TEX_SIZE)
        val rng = Random(biome.ordinal * 9973L + 42L)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val n = rng.nextInt(-8, 9)
                val color = when (biome) {
                    BiomeType.MONO_YELLOW -> {
                        // Classic Level 0 damp yellow wallpaper with subtle vertical stripes & lower damp stain
                        val stripe = if (x % 8 < 2) -12 else 6
                        val arrowPattern = if ((x + y) % 16 == 0 || (x - y + 64) % 16 == 0) -8 else 0
                        val baseboard = y >= 57
                        val damp = if (y > 45) -((y - 45) * 2) else 0
                        if (baseboard) {
                            argb(255, 78 + n, 64 + n, 38 + n)
                        } else {
                            argb(
                                255,
                                182 + stripe + arrowPattern + damp + n,
                                162 + stripe + arrowPattern + damp + n,
                                84 + stripe / 2 + damp + n
                            )
                        }
                    }

                    BiomeType.POOLROOMS -> {
                        // Aquamarine ceramic grid tiles with crisp grout lines
                        val grout = (x % 16 == 0) || (y % 16 == 0)
                        val waterline = y > 50
                        if (grout) {
                            argb(255, 42 + n, 72 + n, 74 + n)
                        } else if (waterline) {
                            argb(255, 58 + n, 128 + n, 126 + n)
                        } else {
                            val sheen = if ((x + y) % 32 < 4) 14 else 0
                            argb(255, 92 + sheen + n, 168 + sheen + n, 160 + sheen + n)
                        }
                    }

                    BiomeType.PIPEWORKS -> {
                        // Industrial gray concrete with horizontal rust pipe band & rivets
                        val pipeBand = y in 20..29
                        val seam = x == 0 || x == 32 || y == 0
                        if (pipeBand) {
                            val cyl = 18 - abs(y - 24) * 3
                            argb(255, 96 + cyl + n, 74 + cyl / 2 + n, 58 + n)
                        } else if (seam) {
                            argb(255, 48 + n, 52 + n, 54 + n)
                        } else {
                            val grime = if (y > 48) -18 else 0
                            argb(255, 86 + grime + n, 90 + grime + n, 92 + grime + n)
                        }
                    }

                    BiomeType.RED_CORRIDOR -> {
                        // Crimson emergency ribs and dark steel trim
                        val rib = if (x % 16 < 3) -35 else 8
                        val hazardBand = y in 52..58 && ((x / 8 + y / 4) % 2 == 0)
                        if (hazardBand) {
                            argb(255, 195 + n, 52 + n, 32 + n)
                        } else {
                            argb(255, 138 + rib + n, 26 + rib / 3 + n, 24 + n)
                        }
                    }

                    BiomeType.VOID_STATIC -> {
                        // Fractured wireframe & corrupted static wall
                        val border = x < 2 || x >= 62 || y < 2 || y >= 62
                        val diag = abs(x - y) < 2 || abs(x + y - 63) < 2
                        if (border || diag) {
                            argb(255, 190, 210, 175)
                        } else {
                            val s = rng.nextInt(10, 65)
                            argb(255, s, s + 8, s)
                        }
                    }
                }
                pixels[y * TEX_SIZE + x] = color
            }
        }
        return pixels
    }

    private fun generateGlitchWallTexture(): IntArray {
        val pixels = IntArray(TEX_SIZE * TEX_SIZE)
        val rng = Random(1337L)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val checker = ((x / 8) + (y / 8)) % 2 == 0
                val noise = rng.nextInt(0, 45)
                pixels[y * TEX_SIZE + x] = if (y % 7 == 0) {
                    argb(255, 220, 45, 90)
                } else if (checker) {
                    argb(255, 28 + noise, 16, 36 + noise)
                } else {
                    argb(255, 150 + noise, 190, 95)
                }
            }
        }
        return pixels
    }

    private fun generateItemTexture(type: PickupType): IntArray {
        val pixels = IntArray(TEX_SIZE * TEX_SIZE)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val cx = x - 32
                val cy = y - 34
                val color = when (type) {
                    PickupType.BATTERY -> {
                        // AA Battery upright
                        when {
                            x in 27..36 && y in 16..19 -> argb(255, 215, 215, 220) // positive nub
                            x in 23..40 && y in 20..28 -> argb(255, 218, 165, 50) // copper top
                            x in 23..40 && y in 29..52 -> argb(255, 36, 40, 44) // dark body
                            x in 26..37 && y in 36..44 -> argb(255, 232, 200, 70) // charge stripe
                            else -> 0
                        }
                    }

                    PickupType.ALMOND_WATER -> {
                        // Glowing sage-green bottle
                        when {
                            x in 28..35 && y in 15..20 -> argb(255, 200, 195, 175) // cap
                            x in 29..34 && y in 21..25 -> argb(255, 130, 195, 165) // neck
                            x in 22..41 && y in 26..53 -> {
                                if (y in 34..44) argb(255, 232, 226, 204) // label
                                else argb(255, 102, 184, 148)
                            }
                            else -> 0
                        }
                    }

                    PickupType.TAPE_STABILIZER -> {
                        // Retro VHS Cassette
                        when {
                            x in 15..48 && y in 22..46 -> {
                                val reelLeft = (x - 24) * (x - 24) + (y - 34) * (y - 34) <= 20
                                val reelRight = (x - 39) * (x - 39) + (y - 34) * (y - 34) <= 20
                                when {
                                    reelLeft || reelRight -> argb(255, 210, 215, 200)
                                    y in 24..28 -> argb(255, 210, 74, 58) // red label strip
                                    else -> argb(255, 34, 36, 40)
                                }
                            }
                            else -> 0
                        }
                    }
                }
                if (color != 0) {
                    pixels[y * TEX_SIZE + x] = color
                } else if (cx * cx + cy * cy in 190..260) {
                    // subtle pickup aura ring
                    pixels[y * TEX_SIZE + x] = argb(90, 220, 210, 140)
                }
            }
        }
        return pixels
    }

    private fun generateAnomalyTexture(type: AnomalyType): IntArray {
        val pixels = IntArray(TEX_SIZE * TEX_SIZE)
        val rng = Random(type.ordinal * 4099L + 77L)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val nx = (x - 32) / 32f
                val ny = (y - 32) / 32f
                val color = when (type) {
                    AnomalyType.STALKER -> {
                        // Tall spindly black bacteria tendril silhouette with glowing pinpoint eyes
                        val torso = abs(nx) < (0.13f + 0.04f * sin(y * 0.4f)) && ny in -0.68f..0.35f
                        val head = nx * nx + (ny + 0.72f) * (ny + 0.72f) < 0.025f
                        val leftArm = abs(nx + 0.25f + ny * 0.12f) < 0.055f && ny in -0.5f..0.6f
                        val rightArm = abs(nx - 0.25f - ny * 0.12f) < 0.055f && ny in -0.5f..0.6f
                        val leftLeg = abs(nx + 0.10f) < 0.05f && ny in 0.30f..0.95f
                        val rightLeg = abs(nx - 0.10f) < 0.05f && ny in 0.30f..0.95f
                        val eyeL = (x == 30 && y == 9)
                        val eyeR = (x == 34 && y == 9)
                        when {
                            eyeL || eyeR -> argb(255, 255, 95, 72)
                            head || torso || leftArm || rightArm || leftLeg || rightLeg -> {
                                val v = rng.nextInt(6, 22)
                                argb(255, v, v, v)
                            }
                            else -> 0
                        }
                    }

                    AnomalyType.WATCHER -> {
                        // Pale eerie statue-like entity with clustered staring eyes
                        val body = abs(nx) < 0.20f * (1f - ny * 0.15f) && ny in -0.55f..0.92f
                        val head = nx * nx * 1.4f + (ny + 0.68f) * (ny + 0.68f) < 0.042f
                        val eyes = (x in 28..29 && y in 8..9) ||
                            (x in 34..35 && y in 8..9) ||
                            (x in 31..32 && y in 12..13)
                        when {
                            eyes -> argb(255, 240, 255, 225)
                            head || body -> {
                                val shade = 145 - (abs(nx) * 180).toInt() + rng.nextInt(-10, 10)
                                argb(255, shade, shade + 6, shade - 4)
                            }
                            else -> 0
                        }
                    }

                    AnomalyType.PHANTOM -> {
                        // Glitch-sliced spectral silhouette
                        val rowOffset = ((sin(y * 0.65f) * 6f).toInt())
                        val gx = (x + rowOffset - 32) / 32f
                        val inSilhouette = abs(gx) < 0.24f && ny in -0.75f..0.85f && (y % 3 != 0)
                        when {
                            inSilhouette && (y in 10..12 && abs(gx) < 0.14f) -> argb(255, 210, 255, 245)
                            inSilhouette -> {
                                val c = rng.nextInt(110, 215)
                                argb(215, c - 30, c, c + 15)
                            }
                            else -> 0
                        }
                    }

                    AnomalyType.MIMIC -> {
                        // Warped jagged dark-crimson horror with wide glowing maw
                        val hull = nx * nx + ny * ny < 0.42f && ny > -0.6f
                        val teeth = ny in -0.05f..0.18f && abs(nx) < 0.36f && ((x + y) % 4 == 0)
                        val eyes = (x in 25..27 && y in 18..20) || (x in 37..39 && y in 18..20)
                        when {
                            eyes -> argb(255, 255, 215, 80)
                            teeth -> argb(255, 235, 225, 200)
                            hull -> argb(255, 42 + rng.nextInt(0, 25), 14, 16)
                            else -> 0
                        }
                    }
                }
                pixels[y * TEX_SIZE + x] = color
            }
        }
        return pixels
    }
}
