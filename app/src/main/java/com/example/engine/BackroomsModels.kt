package com.example.engine

enum class BiomeType(
    val id: String,
    val displayName: String,
    val sectorCode: String,
    val minRecommendedMeters: Float
) {
    MONO_YELLOW("biome_mono_yellow", "Level 0: Damp Office", "SEC-00", 0f),
    POOLROOMS("biome_poolrooms", "Sub-Flooded Poolrooms", "SEC-37", 45f),
    PIPEWORKS("biome_pipeworks", "Concrete Pipeworks", "SEC-02", 90f),
    RED_CORRIDOR("biome_red_corridor", "Crimson Run Sector", "SEC-!", 180f),
    VOID_STATIC("biome_void_static", "Fractured Null Void", "ERR-99", 300f)
}

enum class CellType {
    EMPTY,
    WALL,
    PILLAR,
    GLITCH_WALL
}

enum class PickupType(val displayName: String) {
    BATTERY("AA Battery Cell"),
    ALMOND_WATER("Almond Water"),
    TAPE_STABILIZER("Tape Stabilizer")
}

enum class AnomalyType(
    val id: String,
    val displayName: String,
    val threatRating: String
) {
    STALKER("anomaly_stalker", "Bacteria Stalker", "CLASS III // PURSUER"),
    WATCHER("anomaly_watcher", "The Watcher", "CLASS IV // GAZE-LOCKED"),
    PHANTOM("anomaly_phantom", "Static Phantom", "CLASS IV // PHASE-SHIFT"),
    MIMIC("anomaly_mimic", "Lure Mimic", "CLASS V // AMBUSH")
}

enum class ThreatTier(val label: String) {
    DORMANT("DORMANT"),
    UNSTABLE("UNSTABLE"),
    HOSTILE("HOSTILE"),
    FRACTURED("FRACTURED")
}

enum class AnomalyAlertState {
    PATROL,
    INVESTIGATING,
    HUNTING,
    FROZEN
}

data class WorldItem(
    val id: Long,
    val x: Float,
    val y: Float,
    val type: PickupType,
    val collected: Boolean = false
)

data class AnomalyEntity(
    val id: Long,
    val type: AnomalyType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var alertState: AnomalyAlertState = AnomalyAlertState.PATROL,
    var patrolAngle: Float = 0f,
    var stateTimer: Float = 0f,
    var mimicRevealed: Boolean = false,
    var lastTeleportTime: Float = 0f,
    var phaseSeed: Float = 0f
)

data class GlitchTelemetry(
    val overallIntensity: Float = 0f,
    val chromaticShiftPx: Int = 0,
    val trackingTearIntensity: Float = 0f,
    val staticSnowAmount: Float = 0.04f,
    val corruptTimestamp: Boolean = false,
    val warningBanner: String? = null,
    val nonEuclideanShiftsCount: Int = 0,
    val glitchesSurvivedCount: Int = 0
)

data class CamcorderSettings(
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 0.75f,
    val lookSensitivity: Float = 1.0f,
    val glitchSafetyMode: Boolean = false,
    val invertLook: Boolean = false,
    val showSignalCompass: Boolean = true
)

data class GameHudState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val seed: Long = 0L,
    val distanceFromSpawnMeters: Float = 0f,
    val peakDistanceMeters: Float = 0f,
    val elapsedSeconds: Float = 0f,
    val health: Float = 100f,
    val sanity: Float = 100f,
    val battery: Float = 100f,
    val stamina: Float = 100f,
    val flashlightOn: Boolean = true,
    val almondWaterCount: Int = 1,
    val currentBiome: BiomeType = BiomeType.MONO_YELLOW,
    val threatTier: ThreatTier = ThreatTier.DORMANT,
    val headingDegrees: Float = 0f,
    val spawnBearingDegrees: Float = 0f,
    val nearestAnomalyDistanceMeters: Float = 999f,
    val glitch: GlitchTelemetry = GlitchTelemetry(),
    val statusToast: String? = null,
    val completedRunId: Long? = null,
    val isNewRecord: Boolean = false,
    val causeOfDeath: String = "",
    val biomesVisitedCount: Int = 1,
    val anomaliesEncounteredCount: Int = 0,
    val frameVersion: Long = 0L
)
