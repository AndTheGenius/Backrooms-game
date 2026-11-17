import Foundation

enum BiomeType: String, CaseIterable, Codable {
    case monoYellow = "biome_mono_yellow"
    case poolrooms = "biome_poolrooms"
    case pipeworks = "biome_pipeworks"
    case redCorridor = "biome_red_corridor"
    case voidStatic = "biome_void_static"

    var displayName: String {
        switch self {
        case .monoYellow: return "Level 0: Damp Office"
        case .poolrooms: return "Sub-Flooded Poolrooms"
        case .pipeworks: return "Concrete Pipeworks"
        case .redCorridor: return "Crimson Run Sector"
        case .voidStatic: return "Fractured Null Void"
        }
    }

    var sectorCode: String {
        switch self {
        case .monoYellow: return "SEC-00"
        case .poolrooms: return "SEC-37"
        case .pipeworks: return "SEC-02"
        case .redCorridor: return "SEC-!"
        case .voidStatic: return "ERR-99"
        }
    }
}

enum CellType {
    case empty
    case wall
    case pillar
    case glitchWall
}

enum PickupType: String, CaseIterable {
    case battery = "AA Battery Cell"
    case almondWater = "Almond Water"
    case tapeStabilizer = "Tape Stabilizer"
}

enum AnomalyType: String, CaseIterable, Codable {
    case stalker = "anomaly_stalker"
    case watcher = "anomaly_watcher"
    case phantom = "anomaly_phantom"
    case mimic = "anomaly_mimic"

    var displayName: String {
        switch self {
        case .stalker: return "Bacteria Stalker"
        case .watcher: return "The Watcher"
        case .phantom: return "Static Phantom"
        case .mimic: return "Lure Mimic"
        }
    }
}

enum ThreatTier: String {
    case dormant = "DORMANT"
    case unstable = "UNSTABLE"
    case hostile = "HOSTILE"
    case fractured = "FRACTURED"
}

enum AnomalyAlertState {
    case patrol
    case investigating
    case hunting
    case frozen
}

struct WorldItem: Identifiable {
    let id: Int64
    let x: Float
    let y: Float
    let type: PickupType
    var collected: Bool = false
}

final class AnomalyEntity: Identifiable {
    let id: Int64
    let type: AnomalyType
    var x: Float
    var y: Float
    var alertState: AnomalyAlertState = .patrol
    var patrolAngle: Float
    var stateTimer: Float = 0
    var mimicRevealed: Bool = false

    init(id: Int64, type: AnomalyType, x: Float, y: Float, patrolAngle: Float) {
        self.id = id
        self.type = type
        self.x = x
        self.y = y
        self.patrolAngle = patrolAngle
    }
}

struct GlitchTelemetry {
    var overallIntensity: Float = 0
    var chromaticShiftPx: Int = 0
    var trackingTearIntensity: Float = 0
    var staticSnowAmount: Float = 0.04
    var corruptTimestamp: Bool = false
    var warningBanner: String? = nil
    var nonEuclideanShiftsCount: Int = 0
    var glitchesSurvivedCount: Int = 0
}

struct CamcorderSettings: Codable, Equatable {
    var audioEnabled: Bool = true
    var audioVolume: Float = 0.75
    var lookSensitivity: Float = 1.0
    var glitchSafetyMode: Bool = false
    var invertLook: Bool = false
    var showSignalCompass: Bool = true
}

struct GameHudState {
    var isRunning: Bool = false
    var isPaused: Bool = false
    var isGameOver: Bool = false
    var seed: UInt64 = 0
    var distanceFromSpawnMeters: Float = 0
    var peakDistanceMeters: Float = 0
    var elapsedSeconds: Float = 0
    var health: Float = 100
    var sanity: Float = 100
    var battery: Float = 100
    var stamina: Float = 100
    var flashlightOn: Bool = true
    var almondWaterCount: Int = 1
    var currentBiome: BiomeType = .monoYellow
    var threatTier: ThreatTier = .dormant
    var headingDegrees: Float = 0
    var spawnBearingDegrees: Float = 0
    var nearestAnomalyDistanceMeters: Float = 999
    var glitch: GlitchTelemetry = GlitchTelemetry()
    var statusToast: String? = nil
    var isNewRecord: Bool = false
    var causeOfDeath: String = ""
    var biomesVisitedCount: Int = 1
    var anomaliesEncounteredCount: Int = 0
}

struct RunRecord: Identifiable, Codable {
    var id: UUID = UUID()
    let seed: UInt64
    let peakDistanceMeters: Float
    let durationSeconds: Int
    let glitchesSurvived: Int
    let spatialShiftsEndured: Int
    let biomesVisitedCount: Int
    let anomaliesEncountered: Int
    let causeOfDeath: String
    let createdAt: Date
}

struct DiscoveredEntry: Identifiable, Codable {
    var id: String { entryId }
    let entryId: String
    let category: String
    var encounterCount: Int
    var deepestMeters: Float
    let firstSeenAt: Date
}
