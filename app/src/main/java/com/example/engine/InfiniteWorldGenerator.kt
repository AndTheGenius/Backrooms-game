package com.example.engine

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

data class ChunkCoord(val cx: Int, val cy: Int)

class WorldChunk(
    val coord: ChunkCoord,
    val biome: BiomeType,
    val cells: Array<CellType> // 16 * 16 row-major
) {
    fun get(lx: Int, ly: Int): CellType = cells[ly * InfiniteWorldGenerator.CHUNK_SIZE + lx]
    fun set(lx: Int, ly: Int, type: CellType) {
        cells[ly * InfiniteWorldGenerator.CHUNK_SIZE + lx] = type
    }
}

class InfiniteWorldGenerator(private val worldSeed: Long) {

    companion object {
        const val CHUNK_SIZE = 16
        const val METERS_PER_CELL = 3.0f
    }

    private val chunks = HashMap<ChunkCoord, WorldChunk>()

    fun clear() {
        chunks.clear()
    }

    private fun chunkKey(wx: Int, wy: Int): ChunkCoord {
        val cx = floor(wx.toDouble() / CHUNK_SIZE).toInt()
        val cy = floor(wy.toDouble() / CHUNK_SIZE).toInt()
        return ChunkCoord(cx, cy)
    }

    fun getOrGenerateChunk(cx: Int, cy: Int): WorldChunk {
        val coord = ChunkCoord(cx, cy)
        return chunks.getOrPut(coord) { generateChunk(coord) }
    }

    fun getCell(wx: Int, wy: Int): CellType {
        val cx = floor(wx.toDouble() / CHUNK_SIZE).toInt()
        val cy = floor(wy.toDouble() / CHUNK_SIZE).toInt()
        val lx = ((wx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE
        val ly = ((wy % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE
        return getOrGenerateChunk(cx, cy).get(lx, ly)
    }

    fun getBiomeAt(wx: Int, wy: Int): BiomeType {
        val cx = floor(wx.toDouble() / CHUNK_SIZE).toInt()
        val cy = floor(wy.toDouble() / CHUNK_SIZE).toInt()
        return getOrGenerateChunk(cx, cy).biome
    }

    fun isSolid(wx: Int, wy: Int): Boolean {
        return getCell(wx, wy) != CellType.EMPTY
    }

    private fun setCellIfLoaded(wx: Int, wy: Int, cellType: CellType) {
        val cx = floor(wx.toDouble() / CHUNK_SIZE).toInt()
        val cy = floor(wy.toDouble() / CHUNK_SIZE).toInt()
        val chunk = chunks[ChunkCoord(cx, cy)] ?: return
        val lx = ((wx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE
        val ly = ((wy % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE
        chunk.set(lx, ly, cellType)
    }

    private fun hashChunk(cx: Int, cy: Int): Long {
        var h = worldSeed xor
            (cx.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor
            (cy.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong())
        h = (h xor (h ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        h = (h xor (h ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return h xor (h ushr 31)
    }

    private fun selectBiomeForChunk(cx: Int, cy: Int, rng: Random): BiomeType {
        if (cx == 0 && cy == 0) return BiomeType.MONO_YELLOW
        val centerDistMeters = hypot((cx * CHUNK_SIZE + 8).toFloat(), (cy * CHUNK_SIZE + 8).toFloat()) * METERS_PER_CELL
        val roll = rng.nextFloat()
        return when {
            centerDistMeters < 55f -> {
                if (roll < 0.82f) BiomeType.MONO_YELLOW else BiomeType.POOLROOMS
            }
            centerDistMeters < 115f -> {
                when {
                    roll < 0.35f -> BiomeType.MONO_YELLOW
                    roll < 0.72f -> BiomeType.POOLROOMS
                    else -> BiomeType.PIPEWORKS
                }
            }
            centerDistMeters < 200f -> {
                when {
                    roll < 0.22f -> BiomeType.POOLROOMS
                    roll < 0.62f -> BiomeType.PIPEWORKS
                    roll < 0.90f -> BiomeType.RED_CORRIDOR
                    else -> BiomeType.MONO_YELLOW
                }
            }
            centerDistMeters < 320f -> {
                when {
                    roll < 0.25f -> BiomeType.PIPEWORKS
                    roll < 0.68f -> BiomeType.RED_CORRIDOR
                    else -> BiomeType.VOID_STATIC
                }
            }
            else -> {
                when {
                    roll < 0.35f -> BiomeType.RED_CORRIDOR
                    roll < 0.82f -> BiomeType.VOID_STATIC
                    else -> BiomeType.PIPEWORKS
                }
            }
        }
    }

    private fun generateChunk(coord: ChunkCoord): WorldChunk {
        val rng = Random(hashChunk(coord.cx, coord.cy))
        val biome = selectBiomeForChunk(coord.cx, coord.cy, rng)
        val cells = Array(CHUNK_SIZE * CHUNK_SIZE) { CellType.WALL }

        fun setLocal(x: Int, y: Int, type: CellType) {
            if (x in 0 until CHUNK_SIZE && y in 0 until CHUNK_SIZE) {
                cells[y * CHUNK_SIZE + x] = type
            }
        }

        // Architectural forms vary cleanly by biome
        when (biome) {
            BiomeType.MONO_YELLOW -> {
                // Carve interconnected office halls + partition walls
                for (y in 1 until CHUNK_SIZE - 1) {
                    for (x in 1 until CHUNK_SIZE - 1) {
                        setLocal(x, y, CellType.EMPTY)
                    }
                }
                // Add office partition walls and columns
                val partitions = rng.nextInt(5, 10)
                repeat(partitions) {
                    val horizontal = rng.nextBoolean()
                    val sx = rng.nextInt(2, CHUNK_SIZE - 3)
                    val sy = rng.nextInt(2, CHUNK_SIZE - 3)
                    val len = rng.nextInt(3, 7)
                    for (i in 0 until len) {
                        val px = if (horizontal) (sx + i).coerceAtMost(CHUNK_SIZE - 2) else sx
                        val py = if (horizontal) sy else (sy + i).coerceAtMost(CHUNK_SIZE - 2)
                        if (i != len / 2) setLocal(px, py, CellType.WALL)
                    }
                }
                // Occasional yellow pillar grid
                if (rng.nextFloat() < 0.5f) {
                    for (py in 3 until CHUNK_SIZE - 2 step 4) {
                        for (px in 3 until CHUNK_SIZE - 2 step 4) {
                            setLocal(px, py, CellType.PILLAR)
                        }
                    }
                }
            }

            BiomeType.POOLROOMS -> {
                // Wide echoing tiled chambers with symmetric colonnade pillars
                for (y in 1 until CHUNK_SIZE - 1) {
                    for (x in 1 until CHUNK_SIZE - 1) {
                        setLocal(x, y, CellType.EMPTY)
                    }
                }
                for (py in 3 until CHUNK_SIZE - 2 step 3) {
                    for (px in 3 until CHUNK_SIZE - 2 step 3) {
                        if (rng.nextFloat() < 0.72f) setLocal(px, py, CellType.PILLAR)
                    }
                }
                // Partial divider walls
                repeat(2) {
                    val wy = rng.nextInt(4, CHUNK_SIZE - 4)
                    for (x in 2 until CHUNK_SIZE - 2) {
                        if (x !in 6..9) setLocal(x, wy, CellType.WALL)
                    }
                }
            }

            BiomeType.PIPEWORKS -> {
                // Tight industrial grid maze with 2-cell and 1-cell corridors
                for (y in 1 until CHUNK_SIZE - 1 step 3) {
                    for (x in 1 until CHUNK_SIZE - 1) {
                        setLocal(x, y, CellType.EMPTY)
                    }
                }
                for (x in 1 until CHUNK_SIZE - 1 step 3) {
                    for (y in 1 until CHUNK_SIZE - 1) {
                        setLocal(x, y, CellType.EMPTY)
                    }
                }
                // Carve 2 maintenance rooms
                repeat(2) {
                    val rx = rng.nextInt(2, CHUNK_SIZE - 6)
                    val ry = rng.nextInt(2, CHUNK_SIZE - 6)
                    for (dy in 0..3) for (dx in 0..3) setLocal(rx + dx, ry + dy, CellType.EMPTY)
                }
            }

            BiomeType.RED_CORRIDOR -> {
                // Long high-tension run straightaways with side alcoves
                val mainY = 7
                val mainX = 7
                for (x in 1 until CHUNK_SIZE - 1) {
                    setLocal(x, mainY, CellType.EMPTY)
                    setLocal(x, mainY + 1, CellType.EMPTY)
                }
                for (y in 1 until CHUNK_SIZE - 1) {
                    setLocal(mainX, y, CellType.EMPTY)
                    setLocal(mainX + 1, y, CellType.EMPTY)
                }
                // Flanking parallel alleys & dodging pockets
                for (i in 2 until CHUNK_SIZE - 2 step 3) {
                    for (y in 2..5) setLocal(i, y, CellType.EMPTY)
                    for (y in 10..13) setLocal(i, y, CellType.EMPTY)
                }
            }

            BiomeType.VOID_STATIC -> {
                // Chaotic fractured space with glitch walls
                for (y in 1 until CHUNK_SIZE - 1) {
                    for (x in 1 until CHUNK_SIZE - 1) {
                        val r = rng.nextFloat()
                        setLocal(
                            x,
                            y,
                            when {
                                r < 0.72f -> CellType.EMPTY
                                r < 0.88f -> CellType.GLITCH_WALL
                                else -> CellType.PILLAR
                            }
                        )
                    }
                }
            }
        }

        // Deterministic border gates matching neighboring chunks so world is seamlessly traversable
        val northGate = deterministicGate(coord.cx, coord.cy, horizontal = true)
        val southGate = deterministicGate(coord.cx, coord.cy + 1, horizontal = true)
        val westGate = deterministicGate(coord.cx, coord.cy, horizontal = false)
        val eastGate = deterministicGate(coord.cx + 1, coord.cy, horizontal = false)

        // Carve border gates (2-cells wide for comfortable mobile navigation)
        setLocal(northGate, 0, CellType.EMPTY)
        setLocal((northGate + 1).coerceAtMost(CHUNK_SIZE - 2), 0, CellType.EMPTY)
        setLocal(southGate, CHUNK_SIZE - 1, CellType.EMPTY)
        setLocal((southGate + 1).coerceAtMost(CHUNK_SIZE - 2), CHUNK_SIZE - 1, CellType.EMPTY)
        setLocal(0, westGate, CellType.EMPTY)
        setLocal(0, (westGate + 1).coerceAtMost(CHUNK_SIZE - 2), CellType.EMPTY)
        setLocal(CHUNK_SIZE - 1, eastGate, CellType.EMPTY)
        setLocal(CHUNK_SIZE - 1, (eastGate + 1).coerceAtMost(CHUNK_SIZE - 2), CellType.EMPTY)

        // Connect all 4 gates to chunk center (8, 8) so no chunk is ever isolated
        carvePath(cells, northGate, 1, 8, 8)
        carvePath(cells, southGate, CHUNK_SIZE - 2, 8, 8)
        carvePath(cells, 1, westGate, 8, 8)
        carvePath(cells, CHUNK_SIZE - 2, eastGate, 8, 8)

        // Safe starter office room around world origin (0, 0)
        if (coord.cx == 0 && coord.cy == 0) {
            for (y in 0..4) {
                for (x in 0..4) {
                    setLocal(x, y, CellType.EMPTY)
                }
            }
            // Carve clear exit corridor from starter room to chunk center
            carvePath(cells, 2, 2, 8, 8)
        }

        return WorldChunk(coord, biome, cells)
    }

    private fun deterministicGate(cx: Int, cy: Int, horizontal: Boolean): Int {
        val tag = if (horizontal) 0x517CC1B7L else 0xA24BAED4L
        val h = hashChunk(cx, cy) xor tag
        val idx = ((h ushr 8) and 0x7FFF).toInt()
        return 2 + (idx % (CHUNK_SIZE - 5))
    }

    private fun carvePath(cells: Array<CellType>, sx: Int, sy: Int, tx: Int, ty: Int) {
        var x = sx.coerceIn(1, CHUNK_SIZE - 2)
        var y = sy.coerceIn(1, CHUNK_SIZE - 2)
        while (x != tx) {
            cells[y * CHUNK_SIZE + x] = CellType.EMPTY
            x += if (tx > x) 1 else -1
        }
        while (y != ty) {
            cells[y * CHUNK_SIZE + x] = CellType.EMPTY
            y += if (ty > y) 1 else -1
        }
        cells[ty * CHUNK_SIZE + tx] = CellType.EMPTY
    }

    /**
     * Non-Euclidean Spatial Shift: mutates walls behind the player's camera frustum
     * without trapping the player or overlapping anomalies.
     */
    fun shiftArchitectureBehindPlayer(
        playerX: Float,
        playerY: Float,
        dirX: Float,
        dirY: Float,
        anomalies: List<AnomalyEntity>,
        rng: Random
    ): Boolean {
        val pCellX = floor(playerX).toInt()
        val pCellY = floor(playerY).toInt()
        var shifted = false

        repeat(10) {
            val dx = rng.nextInt(-9, 10)
            val dy = rng.nextInt(-9, 10)
            val dist = hypot(dx.toFloat(), dy.toFloat())
            if (dist !in 3.5f..9.5f) return@repeat

            // Check that candidate is strictly behind the player's view cone
            val dot = (dx * dirX + dy * dirY) / dist
            if (dot > -0.35f) return@repeat

            val wx = pCellX + dx
            val wy = pCellY + dy
            if (wx in -2..2 && wy in -2..2) return@repeat // preserve spawn origin

            // Never shift onto an anomaly
            val overlapsAnomaly = anomalies.any {
                floor(it.x).toInt() == wx && floor(it.y).toInt() == wy
            }
            if (overlapsAnomaly) return@repeat

            val current = getCell(wx, wy)
            val next = when (current) {
                CellType.EMPTY -> if (rng.nextFloat() < 0.65f) CellType.GLITCH_WALL else CellType.WALL
                CellType.WALL, CellType.PILLAR -> CellType.EMPTY
                CellType.GLITCH_WALL -> CellType.EMPTY
            }
            setCellIfLoaded(wx, wy, next)
            shifted = true
            if (rng.nextFloat() < 0.6f) return true
        }
        return shifted
    }

    fun pruneFarChunks(playerX: Float, playerY: Float, keepRadiusChunks: Int = 4) {
        val pcx = floor(playerX / CHUNK_SIZE).toInt()
        val pcy = floor(playerY / CHUNK_SIZE).toInt()
        val iter = chunks.keys.iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            if (kotlin.math.abs(key.cx - pcx) > keepRadiusChunks ||
                kotlin.math.abs(key.cy - pcy) > keepRadiusChunks
            ) {
                iter.remove()
            }
        }
    }
}
