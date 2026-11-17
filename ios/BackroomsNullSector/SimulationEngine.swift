import Foundation

protocol SimulationEventListener: AnyObject {
    func onBiomeDiscovered(_ biome: BiomeType, depthMeters: Float)
    func onAnomalyEncountered(_ anomaly: AnomalyType, depthMeters: Float)
    func onGlitchSpike(intensity: Float, isSpatialShift: Bool)
    func onPickup(_ type: PickupType)
    func onDamageTaken(_ amount: Float)
}

final class SimulationEngine {
    private static let startX: Float = 2.5
    private static let startY: Float = 2.5
    private static let playerRadius: Float = 0.25
    private static let walkSpeed: Float = 2.85
    private static let sprintSpeed: Float = 4.65

    let seed: UInt64
    let world: InfiniteWorldGenerator
    private var rng: SeededRNG

    private(set) var playerX: Float = startX
    private(set) var playerY: Float = startY
    private(set) var headingRad: Float = 0.35

    private(set) var health: Float = 100
    private(set) var sanity: Float = 100
    private(set) var battery: Float = 100
    private(set) var stamina: Float = 100
    private(set) var flashlightOn: Bool = true
    private(set) var almondWaterCount: Int = 1

    private(set) var elapsedSeconds: Float = 0
    private(set) var peakDistanceMeters: Float = 0
    private(set) var headBobPhase: Float = 0
    private(set) var headBobOffsetPx: Float = 0

    private(set) var items: [WorldItem] = []
    private(set) var anomalies: [AnomalyEntity] = []
    private(set) var glitchTelemetry = GlitchTelemetry()

    private var visitedBiomes: Set<BiomeType> = [.monoYellow]
    private var encounteredAnomalies: Set<AnomalyType> = []
    private var spawnedItemChunks: Set<ChunkCoord> = []

    private var nextId: Int64 = 1
    private var glitchBurstTimer: Float = 0
    private var nextGlitchCooldown: Float = 7
    private var nextShiftCooldown: Float = 11
    private var stabilizerCalmTimer: Float = 0
    private var toastTimer: Float = 0
    private var warningBannerTimer: Float = 0
    private var statusToast: String?
    private var warningBanner: String?

    private var glitchesSurvived = 0
    private var nonEuclideanShifts = 0
    private var damageCooldown: Float = 0
    private var causeOfDeath = ""

    init(seed: UInt64 = UInt64(Date().timeIntervalSince1970 * 1000)) {
        self.seed = seed
        self.world = InfiniteWorldGenerator(worldSeed: seed)
        self.rng = SeededRNG(seed: seed)
        _ = world.getOrGenerateChunk(cx: 0, cy: 0)
        spawnItems(in: ChunkCoord(cx: 0, cy: 0), isOrigin: true)
        spawnedItemChunks.insert(ChunkCoord(cx: 0, cy: 0))
    }

    func toggleFlashlight() {
        if battery > 1 {
            flashlightOn.toggle()
        } else {
            flashlightOn = false
            setToast("BATTERY DEPLETED")
        }
    }

    func useAlmondWater() -> Bool {
        guard almondWaterCount > 0 else {
            setToast("NO ALMOND WATER")
            return false
        }
        almondWaterCount -= 1
        sanity = min(100, sanity + 38)
        health = min(100, health + 22)
        stamina = min(100, stamina + 30)
        stabilizerCalmTimer = 4.5
        setToast("ALMOND WATER // SIGNAL CALMED")
        return true
    }

    func step(
        dtRaw: Float,
        moveForwardInput: Float,
        moveRightInput: Float,
        lookDeltaRad: Float,
        sprintInput: Bool,
        listener: SimulationEventListener?
    ) -> GameHudState {
        let dt = max(0.001, min(0.05, dtRaw))
        elapsedSeconds += dt
        if damageCooldown > 0 { damageCooldown -= dt }
        if stabilizerCalmTimer > 0 { stabilizerCalmTimer -= dt }
        if toastTimer > 0 { toastTimer -= dt; if toastTimer <= 0 { statusToast = nil } }
        if warningBannerTimer > 0 { warningBannerTimer -= dt; if warningBannerTimer <= 0 { warningBanner = nil } }

        headingRad = (headingRad + lookDeltaRad).truncatingRemainder(dividingBy: 2 * .pi)
        if headingRad < 0 { headingRad += 2 * .pi }

        let dirX = cos(headingRad)
        let dirY = sin(headingRad)
        let rightX = -dirY
        let rightY = dirX

        let moveMag = min(1.0, hypot(moveForwardInput, moveRightInput))
        let isMoving = moveMag > 0.08
        let isSprinting = sprintInput && isMoving && stamina > 3
        if isSprinting {
            stamina = max(0, stamina - 22 * dt)
        } else {
            stamina = min(100, stamina + (isMoving ? 7.5 : 15.5) * dt)
        }

        let speed = isSprinting ? Self.sprintSpeed : Self.walkSpeed
        let vx = (dirX * moveForwardInput + rightX * moveRightInput) * speed * dt
        let vy = (dirY * moveForwardInput + rightY * moveRightInput) * speed * dt
        movePlayerWithSliding(dx: vx, dy: vy)

        if isMoving {
            headBobPhase += dt * (isSprinting ? 13.5 : 8.5)
            headBobOffsetPx = sin(headBobPhase) * (isSprinting ? 3.4 : 2.0)
        } else {
            headBobOffsetPx *= 0.85
        }

        let distanceMeters = hypot(playerX - Self.startX, playerY - Self.startY) * InfiniteWorldGenerator.metersPerCell
        if distanceMeters > peakDistanceMeters { peakDistanceMeters = distanceMeters }

        let currentBiome = world.getBiomeAt(wx: Int(floor(playerX)), wy: Int(floor(playerY)))
        if visitedBiomes.insert(currentBiome).inserted {
            listener?.onBiomeDiscovered(currentBiome, depthMeters: distanceMeters)
            setToast("ENTERING \(currentBiome.displayName.uppercased())")
        }

        let pcx = Int(floor(playerX / Float(InfiniteWorldGenerator.chunkSize)))
        let pcy = Int(floor(playerY / Float(InfiniteWorldGenerator.chunkSize)))
        for dy in -1...1 {
            for dx in -1...1 {
                let coord = ChunkCoord(cx: pcx + dx, cy: pcy + dy)
                _ = world.getOrGenerateChunk(cx: coord.cx, cy: coord.cy)
                if spawnedItemChunks.insert(coord).inserted {
                    spawnItems(in: coord, isOrigin: false)
                }
            }
        }
        if items.count > 48 {
            items.removeAll { $0.collected || hypot($0.x - playerX, $0.y - playerY) > 36 }
        }
        world.pruneFarChunks(playerX: playerX, playerY: playerY)

        if flashlightOn {
            let drainScale = 1.0 + (distanceMeters / 500) * 0.45
            battery = max(0, battery - 1.45 * drainScale * dt)
            if battery <= 0 {
                flashlightOn = false
                setToast("FLASHLIGHT BATTERY EXHAUSTED")
            }
        } else if battery < 8 {
            battery = min(8, battery + 0.6 * dt)
        }

        items.removeAll { item in
            guard !item.collected && hypot(item.x - playerX, item.y - playerY) < 0.72 else { return false }
            listener?.onPickup(item.type)
            switch item.type {
            case .battery:
                battery = min(100, battery + 42)
                if !flashlightOn && battery > 12 { flashlightOn = true }
                setToast("+AA BATTERY CELL (+42%)")
            case .almondWater:
                if almondWaterCount < 3 {
                    almondWaterCount += 1
                    setToast("+ALMOND WATER (HELD: \(almondWaterCount))")
                } else {
                    sanity = min(100, sanity + 28)
                    health = min(100, health + 15)
                    setToast("ALMOND WATER CONSUMED (+SANITY)")
                }
            case .tapeStabilizer:
                stabilizerCalmTimer = 7.0
                sanity = min(100, sanity + 24)
                health = min(100, health + 12)
                glitchBurstTimer = 0
                setToast("TAPE STABILIZER // REALITY ANCHORED")
            }
            return true
        }

        let threatTier: ThreatTier
        if distanceMeters < 65 { threatTier = .dormant }
        else if distanceMeters < 160 { threatTier = .unstable }
        else if distanceMeters < 300 { threatTier = .hostile }
        else { threatTier = .fractured }

        maintainAnomalyPopulation(distanceMeters: distanceMeters, dirX: dirX, dirY: dirY)

        var nearestDistCells: Float = 999
        anomalies.removeAll { hypot(playerX - $0.x, playerY - $0.y) > 30 }
        for a in anomalies {
            let dx = playerX - a.x
            let dy = playerY - a.y
            let distCells = hypot(dx, dy)
            if distCells < nearestDistCells { nearestDistCells = distCells }

            let los = distCells < 13 && hasLineOfSight(x1: a.x, y1: a.y, x2: playerX, y2: playerY)
            if los && distCells < 10.5 && encounteredAnomalies.insert(a.type).inserted {
                listener?.onAnomalyEncountered(a.type, depthMeters: distanceMeters)
            }

            let aggroScale = 1.0 + min(0.95, distanceMeters / 260)
            stepAnomalyAI(a, dx: dx, dy: dy, distCells: distCells, los: los, playerSprinting: isSprinting, playerDirX: dirX, playerDirY: dirY, aggroScale: aggroScale, dt: dt)

            if distCells < 0.62 && damageCooldown <= 0 {
                let baseDmg: Float
                switch a.type {
                case .stalker: baseDmg = 34
                case .watcher: baseDmg = 40
                case .phantom: baseDmg = 26
                case .mimic: baseDmg = 38
                }
                let dmg = baseDmg * (1.0 + min(0.45, distanceMeters / 500))
                health = max(0, health - dmg)
                sanity = max(0, sanity - 16)
                damageCooldown = 1.25
                glitchBurstTimer = 1.1
                warningBanner = "CONTACT // \(a.type.displayName.uppercased())"
                warningBannerTimer = 1.8
                listener?.onDamageTaken(dmg)
                if health <= 0 { causeOfDeath = "Terminated by \(a.type.displayName)" }
            }
        }

        let nearestMeters = nearestDistCells * InfiniteWorldGenerator.metersPerCell
        let darknessDrain: Float = flashlightOn ? 0 : 0.95
        let proximityDrain: Float = nearestDistCells < 6.5 ? (6.5 - nearestDistCells) * 1.15 : 0
        let depthDrain: Float = (distanceMeters / 350) * 0.75
        let netSanity: Float = stabilizerCalmTimer > 0
            ? 3.2
            : (nearestDistCells > 9 && flashlightOn && currentBiome == .monoYellow ? 0.6 - depthDrain : -(darknessDrain + proximityDrain + depthDrain))
        sanity = max(0, min(100, sanity + netSanity * dt))
        if sanity <= 0 && damageCooldown <= 0 {
            health = max(0, health - 7.5 * dt)
            if health <= 0 && causeOfDeath.isEmpty { causeOfDeath = "Total Signal & Reality Collapse" }
        }

        var baseGlitch = min(0.55, distanceMeters / 420)
            + min(0.35, (100 - sanity) / 140)
            + (nearestDistCells < 8 ? (8 - nearestDistCells) / 14 : 0)
        if stabilizerCalmTimer > 0 { baseGlitch *= 0.25 }
        baseGlitch = max(0.03, min(1.0, baseGlitch))

        nextGlitchCooldown -= dt
        if nextGlitchCooldown <= 0 {
            glitchBurstTimer = rng.nextFloat() * 0.9 + 0.55
            glitchesSurvived += 1
            let minCd = max(2.2, 6.8 - distanceMeters / 90)
            nextGlitchCooldown = minCd + rng.nextFloat() * 3.5
            if warningBanner == nil {
                let banners = ["VHS TRACKING LOSS", "SIGNAL DRIFT DETECTED", "TIMEFRAME CORRUPTION", "MAGNETIC INTERFERENCE"]
                warningBanner = banners[rng.nextInt(0, banners.count)]
                warningBannerTimer = 1.4
            }
            listener?.onGlitchSpike(intensity: baseGlitch, isSpatialShift: false)
        }

        nextShiftCooldown -= dt
        if nextShiftCooldown <= 0 && stabilizerCalmTimer <= 0 {
            let shifted = world.shiftArchitectureBehindPlayer(playerX: playerX, playerY: playerY, dirX: dirX, dirY: dirY, anomalies: anomalies, rng: &rng)
            let minShift = max(3.8, 12.5 - distanceMeters / 60)
            nextShiftCooldown = minShift + rng.nextFloat() * 4.5
            if shifted {
                nonEuclideanShifts += 1
                glitchBurstTimer = max(glitchBurstTimer, 0.85)
                warningBanner = "NON-EUCLIDEAN SHIFT"
                warningBannerTimer = 1.6
                listener?.onGlitchSpike(intensity: 0.85, isSpatialShift: true)
            }
        }

        if glitchBurstTimer > 0 { glitchBurstTimer = max(0, glitchBurstTimer - dt) }
        let totalGlitch = max(0, min(1, baseGlitch + (glitchBurstTimer > 0 ? 0.55 : 0)))
        glitchTelemetry = GlitchTelemetry(
            overallIntensity: totalGlitch,
            chromaticShiftPx: Int(totalGlitch * 5.2),
            trackingTearIntensity: glitchBurstTimer > 0 ? (0.45 + totalGlitch * 0.55) : (totalGlitch * 0.25),
            staticSnowAmount: 0.015 + totalGlitch * 0.085,
            corruptTimestamp: glitchBurstTimer > 0.25 || sanity < 30,
            warningBanner: warningBanner,
            nonEuclideanShiftsCount: nonEuclideanShifts,
            glitchesSurvivedCount: glitchesSurvived
        )

        let headingDeg = ((headingRad * 180 / .pi).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        let toSpawn = atan2(Self.startY - playerY, Self.startX - playerX)
        let spawnDeg = ((toSpawn * 180 / .pi).truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)

        return GameHudState(
            isRunning: health > 0,
            isPaused: false,
            isGameOver: health <= 0,
            seed: seed,
            distanceFromSpawnMeters: distanceMeters,
            peakDistanceMeters: peakDistanceMeters,
            elapsedSeconds: elapsedSeconds,
            health: health,
            sanity: sanity,
            battery: battery,
            stamina: stamina,
            flashlightOn: flashlightOn,
            almondWaterCount: almondWaterCount,
            currentBiome: currentBiome,
            threatTier: threatTier,
            headingDegrees: headingDeg,
            spawnBearingDegrees: spawnDeg,
            nearestAnomalyDistanceMeters: nearestMeters,
            glitch: glitchTelemetry,
            statusToast: statusToast,
            causeOfDeath: health <= 0 ? (causeOfDeath.isEmpty ? "Lost in \(currentBiome.displayName)" : causeOfDeath) : "",
            biomesVisitedCount: visitedBiomes.count,
            anomaliesEncounteredCount: encounteredAnomalies.count
        )
    }

    private func stepAnomalyAI(_ a: AnomalyEntity, dx: Float, dy: Float, distCells: Float, los: Bool, playerSprinting: Bool, playerDirX: Float, playerDirY: Float, aggroScale: Float, dt: Float) {
        let inv = distCells > 0.001 ? (1.0 / distCells) : 0
        let toPX = dx * inv
        let toPY = dy * inv

        switch a.type {
        case .stalker:
            let hearRadius: Float = playerSprinting ? 9.5 : 4.0
            if los || distCells < hearRadius { a.alertState = .hunting }
            else if a.alertState == .hunting && distCells > 12 { a.alertState = .patrol }
            if a.alertState == .hunting {
                _ = moveEntity(a, dx: toPX * 2.05 * aggroScale * dt, dy: toPY * 2.05 * aggroScale * dt)
            } else {
                patrolEntity(a, speed: 1.15 * aggroScale, dt: dt)
            }
        case .watcher:
            let lookDot = (-toPX) * playerDirX + (-toPY) * playerDirY
            if los && flashlightOn && lookDot > 0.65 && distCells < 11 {
                a.alertState = .frozen
            } else if distCells < 14 {
                a.alertState = .hunting
                _ = moveEntity(a, dx: toPX * 2.45 * aggroScale * dt, dy: toPY * 2.45 * aggroScale * dt)
            } else {
                a.alertState = .patrol
                patrolEntity(a, speed: 0.9, dt: dt)
            }
        case .phantom:
            a.stateTimer -= dt
            if distCells < 12 && a.stateTimer <= 0 {
                a.stateTimer = max(2.0, 5.2 / aggroScale)
                let ang = rng.nextFloat() * 2 * .pi
                let r = rng.nextFloat() * 4.5 + 2.8
                let tx = playerX + cos(ang) * r
                let ty = playerY + sin(ang) * r
                if !world.isSolid(wx: Int(floor(tx)), wy: Int(floor(ty))) {
                    a.x = tx; a.y = ty
                    glitchBurstTimer = max(glitchBurstTimer, 0.45)
                }
            }
            if distCells < 7 {
                a.alertState = .hunting
                _ = moveEntity(a, dx: toPX * 1.55 * aggroScale * dt, dy: toPY * 1.55 * aggroScale * dt)
            } else {
                a.alertState = .patrol
            }
        case .mimic:
            if !a.mimicRevealed {
                a.alertState = .patrol
                if distCells < 3.6 && los {
                    a.mimicRevealed = true
                    a.alertState = .hunting
                    warningBanner = "LURE MIMIC TRIGGERED"
                    warningBannerTimer = 1.4
                }
            } else {
                a.alertState = .hunting
                _ = moveEntity(a, dx: toPX * 2.65 * aggroScale * dt, dy: toPY * 2.65 * aggroScale * dt)
            }
        }
    }

    private func patrolEntity(_ a: AnomalyEntity, speed: Float, dt: Float) {
        a.stateTimer -= dt
        if a.stateTimer <= 0 {
            a.patrolAngle = rng.nextFloat() * 2 * .pi
            a.stateTimer = 1.5 + rng.nextFloat() * 2.5
        }
        if !moveEntity(a, dx: cos(a.patrolAngle) * speed * dt, dy: sin(a.patrolAngle) * speed * dt) {
            a.patrolAngle = (a.patrolAngle + .pi * 0.65).truncatingRemainder(dividingBy: 2 * .pi)
        }
    }

    private func moveEntity(_ a: AnomalyEntity, dx: Float, dy: Float) -> Bool {
        var moved = false
        if !isCircleBlocked(cx: a.x + dx, cy: a.y, r: 0.24) { a.x += dx; moved = true }
        if !isCircleBlocked(cx: a.x, cy: a.y + dy, r: 0.24) { a.y += dy; moved = true }
        return moved
    }

    private func maintainAnomalyPopulation(distanceMeters: Float, dirX: Float, dirY: Float) {
        let cap = distanceMeters < 35 ? 1 : (distanceMeters < 95 ? 3 : (distanceMeters < 200 ? 5 : (distanceMeters < 350 ? 7 : 9)))
        guard anomalies.count < cap else { return }
        for _ in 0..<4 where anomalies.count < cap {
            let ang = atan2(dirY, dirX) + (rng.nextFloat() - 0.5) * 2.8
            let dist = 9 + rng.nextFloat() * 9
            let cx = Int(floor(playerX + cos(ang) * dist))
            let cy = Int(floor(playerY + sin(ang) * dist))
            if (-2...4).contains(cx) && (-2...4).contains(cy) { continue }
            if !world.isSolid(wx: cx, wy: cy) {
                let roll = rng.nextFloat()
                let type: AnomalyType
                if distanceMeters < 65 { type = roll < 0.78 ? .stalker : .watcher }
                else if distanceMeters < 150 { type = roll < 0.45 ? .stalker : (roll < 0.80 ? .watcher : .phantom) }
                else { type = roll < 0.34 ? .stalker : (roll < 0.62 ? .watcher : (roll < 0.84 ? .phantom : .mimic)) }
                anomalies.append(AnomalyEntity(id: nextId, type: type, x: Float(cx) + 0.5, y: Float(cy) + 0.5, patrolAngle: rng.nextFloat() * 6.28))
                nextId += 1
            }
        }
    }

    private func spawnItems(in coord: ChunkCoord, isOrigin: Bool) {
        let chunk = world.getOrGenerateChunk(cx: coord.cx, cy: coord.cy)
        if isOrigin {
            items.append(WorldItem(id: nextId, x: 3.5, y: 1.5, type: .battery)); nextId += 1
            items.append(WorldItem(id: nextId, x: 1.5, y: 3.5, type: .almondWater)); nextId += 1
            return
        }
        let count = rng.nextInt(1, 4)
        var placed = 0
        for _ in 0..<16 where placed < count {
            let lx = rng.nextInt(1, InfiniteWorldGenerator.chunkSize - 1)
            let ly = rng.nextInt(1, InfiniteWorldGenerator.chunkSize - 1)
            if chunk.get(lx, ly) == .empty {
                let wx = Float(coord.cx * InfiniteWorldGenerator.chunkSize + lx) + 0.5
                let wy = Float(coord.cy * InfiniteWorldGenerator.chunkSize + ly) + 0.5
                let r = rng.nextFloat()
                let t: PickupType = r < 0.50 ? .battery : (r < 0.84 ? .almondWater : .tapeStabilizer)
                items.append(WorldItem(id: nextId, x: wx, y: wy, type: t))
                nextId += 1
                placed += 1
            }
        }
    }

    private func movePlayerWithSliding(dx: Float, dy: Float) {
        if !isCircleBlocked(cx: playerX + dx, cy: playerY, r: Self.playerRadius) { playerX += dx }
        if !isCircleBlocked(cx: playerX, cy: playerY + dy, r: Self.playerRadius) { playerY += dy }
    }

    private func isCircleBlocked(cx: Float, cy: Float, r: Float) -> Bool {
        let minX = Int(floor(cx - r)), maxX = Int(floor(cx + r))
        let minY = Int(floor(cy - r)), maxY = Int(floor(cy + r))
        for y in minY...maxY {
            for x in minX...maxX {
                if world.isSolid(wx: x, wy: y) { return true }
            }
        }
        return false
    }

    private func hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float) -> Bool {
        let dx = x2 - x1, dy = y2 - y1
        let steps = max(1, min(60, Int(hypot(dx, dy) * 4)))
        let sx = dx / Float(steps), sy = dy / Float(steps)
        var cx = x1, cy = y1
        for _ in 1..<steps {
            cx += sx; cy += sy
            if world.isSolid(wx: Int(floor(cx)), wy: Int(floor(cy))) { return false }
        }
        return true
    }

    private func setToast(_ msg: String) {
        statusToast = msg
        toastTimer = 2.4
    }
}
