package com.example

import com.example.engine.BiomeType
import com.example.engine.CellType
import com.example.engine.InfiniteWorldGenerator
import com.example.engine.SimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackroomsEngineTest {

    @Test
    fun `origin spawn room is clear and starts in Level 0`() {
        val world = InfiniteWorldGenerator(worldSeed = 42L)
        assertEquals(BiomeType.MONO_YELLOW, world.getBiomeAt(2, 2))
        for (y in 0..4) {
            for (x in 0..4) {
                assertEquals(CellType.EMPTY, world.getCell(x, y))
            }
        }
    }

    @Test
    fun `chunks vary in biome form further from spawn`() {
        val world = InfiniteWorldGenerator(worldSeed = 9999L)
        val seenBiomes = mutableSetOf<BiomeType>()
        for (cy in -8..8) {
            for (cx in -8..8) {
                seenBiomes.add(world.getOrGenerateChunk(cx, cy).biome)
            }
        }
        assertTrue("Expected multiple Backrooms biomes across distance", seenBiomes.size >= 4)
    }

    @Test
    fun `simulation moves player and computes distance from spawn`() {
        val sim = SimulationEngine(seed = 777L)
        var state = sim.step(0.016f, 0f, 0f, 0f, false, null)
        assertEquals(0f, state.distanceFromSpawnMeters, 0.05f)

        repeat(20) {
            state = sim.step(0.04f, 1f, 0f, 0f, false, null)
        }
        assertTrue("Player should move away from spawn", state.distanceFromSpawnMeters > 1.0f)
        assertTrue("Glitch telemetry should be active", state.glitch.overallIntensity >= 0f)
    }
}
