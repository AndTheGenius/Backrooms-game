package com.example.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class SimulationEngine(
    private val seed: Long = System.currentTimeMillis()
) {
    companion object {
        private const val START_X = 2.5f
        private const val START_Y = 2.5f
        private const val PLAYER_RADIUS = 0.25f
        private const val WALK_SPEED = 2.85f
        private const val SPRINT_SPEED = 4.65f
    }

    val world = InfiniteWorldGenerator(seed)
    private val rng = Random(seed)

    var playerX: Float = START_X
        private set
    var playerY: Float = START_Y
        private set
    var headingRad: Float = 0.35f
        private set

    var health: Float = 100f
        private set
    var sanity: Float = 100f
        private set
    var battery: Float = 100f
        private set
    var stamina: Float = 100f
        private set
    var flashlightOn: Boolean = true
        private set
    var almondWaterCount: Int = 1
        private set

    var elapsedSeconds: Float = 0f
        private set
    var peakDistanceMeters: Float = 0f
        private set
    var headBobPhase: Float = 0f
        private set
    var headBobOffsetPx: Float = 0f
        private set

    val items = mutableListOf<WorldItem>()
    val anomalies = mutableListOf<AnomalyEntity>()

    private val visitedBiomes = mutableSetOf(BiomeType.MONO_YELLOW)
    private val encounteredAnomalies = mutableSetOf<AnomalyType>()
    private val spawnedItemChunks = mutableSetOf<ChunkCoord>()

    private var nextId = 1L
    private var glitchBurstTimer = 0f
    private var nextGlitchCooldown = 7f
    private var nextShiftCooldown = 11f
    private var stabilizerCalmTimer = 0f
    private var toastTimer = 0f
    private var warningBannerTimer = 0f
    private var statusToast: String? = null
    private var warningBanner: String? = null

    private var glitchesSurvived = 0
    private var nonEuclideanShifts = 0
    private var damageCooldown = 0f
    private var causeOfDeath = ""

    var glitchTelemetry = GlitchTelemetry()
        private set

    interface EventListener {
        fun onBiomeDiscovered(biome: BiomeType, depthMeters: Float)
        fun onAnomalyEncountered(anomaly: AnomalyType, depthMeters: Float)
        fun onGlitchSpike(intensity: Float, isSpatialShift: Boolean)
        fun onPickup(type: PickupType)
        fun onDamageTaken(amount: Float)
    }

    init {
        // Pre-warm starting chunks and place safe initial supplies
        world.getOrGenerateChunk(0, 0)
        spawnItemsInChunk(ChunkCoord(0, 0), isOrigin = true)
        spawnedItemChunks.add(ChunkCoord(0, 0))
    }

    fun toggleFlashlight() {
        if (battery > 1f) {
            flashlightOn = !flashlightOn
        } else {
            flashlightOn = false
            setToast("BATTERY DEPLETED")
        }
    }

    fun useAlmondWater(): Boolean {
        if (almondWaterCount <= 0) {
            setToast("NO ALMOND WATER")
            return false
        }
        almondWaterCount--
        sanity = (sanity + 38f).coerceAtMost(100f)
        health = (health + 22f).coerceAtMost(100f)
        stamina = (stamina + 30f).coerceAtMost(100f)
        stabilizerCalmTimer = 4.5f
        setToast("ALMOND WATER // SIGNAL CALMED")
        return true
    }

    fun step(
        dtRaw: Float,
        moveForwardInput: Float,
        moveRightInput: Float,
        lookDeltaRad: Float,
        sprintInput: Boolean,
        listener: EventListener?
    ): GameHudState {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        elapsedSeconds += dt
        if (damageCooldown > 0f) damageCooldown -= dt
        if (stabilizerCalmTimer > 0f) stabilizerCalmTimer -= dt
        if (toastTimer > 0f) {
            toastTimer -= dt
            if (toastTimer <= 0f) statusToast = null
        }
        if (warningBannerTimer > 0f) {
            warningBannerTimer -= dt
            if (warningBannerTimer <= 0f) warningBanner = null
        }

        // 1. Apply camera yaw turn
        headingRad = ((headingRad + lookDeltaRad) % (2f * PI.toFloat()))
        if (headingRad < 0f) headingRad += 2f * PI.toFloat()

        val dirX = cos(headingRad)
        val dirY = sin(headingRad)
        val rightX = -dirY
        val rightY = dirX

        // 2. Movement & Sprint Stamina
        val moveMag = hypot(moveForwardInput, moveRightInput).coerceAtMost(1f)
        val isMoving = moveMag > 0.08f
        val isSprinting = sprintInput && isMoving && stamina > 3f
        if (isSprinting) {
            stamina = (stamina - 22f * dt).coerceAtLeast(0f)
        } else {
            val regen = if (isMoving) 7.5f else 15.5f
            stamina = (stamina + regen * dt).coerceAtMost(100f)
        }

        val speed = if (isSprinting) SPRINT_SPEED else WALK_SPEED
        val vx = (dirX * moveForwardInput + rightX * moveRightInput) * speed * dt
        val vy = (dirY * moveForwardInput + rightY * moveRightInput) * speed * dt

        movePlayerWithSliding(vx, vy)

        if (isMoving) {
            headBobPhase += dt * (if (isSprinting) 13.5f else 8.5f)
            headBobOffsetPx = sin(headBobPhase) * (if (isSprinting) 3.4f else 2.0f)
        } else {
            headBobOffsetPx *= 0.85f
        }

        // 3. Distance, Biome, & Chunk Streaming
        val distanceMeters = hypot(playerX - START_X, playerY - START_Y) * InfiniteWorldGenerator.METERS_PER_CELL
        if (distanceMeters > peakDistanceMeters) peakDistanceMeters = distanceMeters

        val pCellX = floor(playerX).toInt()
        val pCellY = floor(playerY).toInt()
        val currentBiome = world.getBiomeAt(pCellX, pCellY)
        if (visitedBiomes.add(currentBiome)) {
            listener?.onBiomeDiscovered(currentBiome, distanceMeters)
            setToast("ENTERING ${currentBiome.displayName.uppercase()}")
        }

        val pcx = floor(playerX / InfiniteWorldGenerator.CHUNK_SIZE).toInt()
        val pcy = floor(playerY / InfiniteWorldGenerator.CHUNK_SIZE).toInt()
        for (dy in -1..1) {
            for (dx in -1..1) {
                val coord = ChunkCoord(pcx + dx, pcy + dy)
                world.getOrGenerateChunk(coord.cx, coord.cy)
                if (spawnedItemChunks.add(coord)) {
                    spawnItemsInChunk(coord, isOrigin = false)
                }
            }
        }
        if (items.size > 48) {
            items.removeAll {
                it.collected || hypot(it.x - playerX, it.y - playerY) > 36f
            }
        }
        world.pruneFarChunks(playerX, playerY)

        // 4. Flashlight Battery
        if (flashlightOn) {
            val drainScale = 1.0f + (distanceMeters / 500f) * 0.45f
            battery = (battery - 1.45f * drainScale * dt).coerceAtLeast(0f)
            if (battery <= 0f) {
                flashlightOn = false
                setToast("FLASHLIGHT BATTERY EXHAUSTED")
            }
        } else {
            // Emergency trickle trickle recharge up to 8% so player is never permanently pitch-black
            if (battery < 8f) {
                battery = (battery + 0.6f * dt).coerceAtMost(8f)
            }
        }

        // 5. Pickups
        val itemIter = items.iterator()
        while (itemIter.hasNext()) {
            val item = itemIter.next()
            if (!item.collected && hypot(item.x - playerX, item.y - playerY) < 0.72f) {
                itemIter.remove()
                listener?.onPickup(item.type)
                when (item.type) {
                    PickupType.BATTERY -> {
                        battery = (battery + 42f).coerceAtMost(100f)
                        if (!flashlightOn && battery > 12f) flashlightOn = true
                        setToast("+AA BATTERY CELL (+42%)")
                    }
                    PickupType.ALMOND_WATER -> {
                        if (almondWaterCount < 3) {
                            almondWaterCount++
                            setToast("+ALMOND WATER (HELD: $almondWaterCount)")
                        } else {
                            sanity = (sanity + 28f).coerceAtMost(100f)
                            health = (health + 15f).coerceAtMost(100f)
                            setToast("ALMOND WATER CONSUMED (+SANITY)")
                        }
                    }
                    PickupType.TAPE_STABILIZER -> {
                        stabilizerCalmTimer = 7.0f
                        sanity = (sanity + 24f).coerceAtMost(100f)
                        health = (health + 12f).coerceAtMost(100f)
                        glitchBurstTimer = 0f
                        setToast("TAPE STABILIZER // REALITY ANCHORED")
                    }
                }
            }
        }

        // 6. Escalating Threat Tier & Anomaly Spawning/AI
        val threatTier = when {
            distanceMeters < 65f -> ThreatTier.DORMANT
            distanceMeters < 160f -> ThreatTier.UNSTABLE
            distanceMeters < 300f -> ThreatTier.HOSTILE
            else -> ThreatTier.FRACTURED
        }

        maintainAnomalyPopulation(distanceMeters, dirX, dirY)

        var nearestDistCells = 999f
        var huntingCount = 0
        var nearestDamager: AnomalyType? = null

        val anomIter = anomalies.iterator()
        while (anomIter.hasNext()) {
            val a = anomIter.next()
            val dx = playerX - a.x
            val dy = playerY - a.y
            val distCells = hypot(dx, dy)
            if (distCells > 30f) {
                anomIter.remove()
                continue
            }
            if (distCells < nearestDistCells) {
                nearestDistCells = distCells
                nearestDamager = a.type
            }

            val los = distCells < 13f && hasLineOfSight(a.x, a.y, playerX, playerY)
            if (los && distCells < 10.5f && encounteredAnomalies.add(a.type)) {
                listener?.onAnomalyEncountered(a.type, distanceMeters)
            }

            val aggroScale = 1f + (distanceMeters / 260f).coerceAtMost(0.95f)
            stepAnomalyAi(a, dx, dy, distCells, los, isSprinting, dirX, dirY, aggroScale, dt)

            if (a.alertState == AnomalyAlertState.HUNTING) huntingCount++

            // Contact Damage
            if (distCells < 0.62f && damageCooldown <= 0f) {
                val dmg = when (a.type) {
                    AnomalyType.STALKER -> 34f
                    AnomalyType.WATCHER -> 40f
                    AnomalyType.PHANTOM -> 26f
                    AnomalyType.MIMIC -> 38f
                } * (1f + (distanceMeters / 500f).coerceAtMost(0.45f))
                health = (health - dmg).coerceAtLeast(0f)
                sanity = (sanity - 16f).coerceAtLeast(0f)
                damageCooldown = 1.25f
                glitchBurstTimer = 1.1f
                warningBanner = "CONTACT // ${a.type.displayName.uppercase()}"
                warningBannerTimer = 1.8f
                listener?.onDamageTaken(dmg)
                if (health <= 0f) {
                    causeOfDeath = "Terminated by ${a.type.displayName}"
                }
            }
        }

        // 7. Sanity / Signal Integrity & Autogenerated Glitches
        val nearestMeters = nearestDistCells * InfiniteWorldGenerator.METERS_PER_CELL
        val darknessDrain = if (!flashlightOn) 0.95f else 0f
        val proximityDrain = if (nearestDistCells < 6.5f) (6.5f - nearestDistCells) * 1.15f else 0f
        val depthDrain = (distanceMeters / 350f) * 0.75f
        val netSanityDelta = if (stabilizerCalmTimer > 0f) {
            3.2f
        } else if (nearestDistCells > 9f && flashlightOn && currentBiome == BiomeType.MONO_YELLOW) {
            0.6f - depthDrain
        } else {
            -(darknessDrain + proximityDrain + depthDrain)
        }
        sanity = (sanity + netSanityDelta * dt).coerceIn(0f, 100f)

        if (sanity <= 0f && damageCooldown <= 0f) {
            health = (health - 7.5f * dt).coerceAtLeast(0f)
            if (health <= 0f && causeOfDeath.isEmpty()) {
                causeOfDeath = "Total Signal & Reality Collapse"
            }
        }

        // Glitch Director
        val baseGlitch = (
            (distanceMeters / 420f).coerceAtMost(0.55f) +
                ((100f - sanity) / 140f).coerceAtMost(0.35f) +
                (if (nearestDistCells < 8f) (8f - nearestDistCells) / 14f else 0f)
            ).let { if (stabilizerCalmTimer > 0f) it * 0.25f else it }.coerceIn(0.03f, 1f)

        nextGlitchCooldown -= dt
        if (nextGlitchCooldown <= 0f) {
            glitchBurstTimer = rng.nextFloat() * 0.9f + 0.55f
            glitchesSurvived++
            val minCd = (6.8f - (distanceMeters / 90f)).coerceAtLeast(2.2f)
            nextGlitchCooldown = minCd + rng.nextFloat() * 3.5f
            if (warningBanner == null) {
                warningBanner = listOf(
                    "VHS TRACKING LOSS",
                    "SIGNAL DRIFT DETECTED",
                    "TIMEFRAME CORRUPTION",
                    "MAGNETIC INTERFERENCE"
                ).random(rng)
                warningBannerTimer = 1.4f
            }
            listener?.onGlitchSpike(baseGlitch, isSpatialShift = false)
        }

        nextShiftCooldown -= dt
        if (nextShiftCooldown <= 0f && stabilizerCalmTimer <= 0f) {
            val didShift = world.shiftArchitectureBehindPlayer(playerX, playerY, dirX, dirY, anomalies, rng)
            val minShiftCd = (12.5f - (distanceMeters / 60f)).coerceAtLeast(3.8f)
            nextShiftCooldown = minShiftCd + rng.nextFloat() * 4.5f
            if (didShift) {
                nonEuclideanShifts++
                glitchBurstTimer = maxOf(glitchBurstTimer, 0.85f)
                warningBanner = "NON-EUCLIDEAN SHIFT"
                warningBannerTimer = 1.6f
                listener?.onGlitchSpike(0.85f, isSpatialShift = true)
            }
        }

        if (glitchBurstTimer > 0f) {
            glitchBurstTimer = (glitchBurstTimer - dt).coerceAtLeast(0f)
        }

        val burstBoost = if (glitchBurstTimer > 0f) 0.55f else 0f
        val totalGlitch = (baseGlitch + burstBoost).coerceIn(0f, 1f)
        glitchTelemetry = GlitchTelemetry(
            overallIntensity = totalGlitch,
            chromaticShiftPx = (totalGlitch * 5.2f).toInt(),
            trackingTearIntensity = if (glitchBurstTimer > 0f) (0.45f + totalGlitch * 0.55f) else (totalGlitch * 0.25f),
            staticSnowAmount = (0.015f + totalGlitch * 0.085f),
            corruptTimestamp = glitchBurstTimer > 0.25f || sanity < 30f,
            warningBanner = warningBanner,
            nonEuclideanShiftsCount = nonEuclideanShifts,
            glitchesSurvivedCount = glitchesSurvived
        )

        val headingDeg = ((headingRad * 180f / PI.toFloat()) % 360f + 360f) % 360f
        val toSpawnRad = atan2(START_Y - playerY, START_X - playerX)
        val spawnBearingDeg = ((toSpawnRad * 180f / PI.toFloat()) % 360f + 360f) % 360f

        return GameHudState(
            isRunning = health > 0f,
            isPaused = false,
            isGameOver = health <= 0f,
            seed = seed,
            distanceFromSpawnMeters = distanceMeters,
            peakDistanceMeters = peakDistanceMeters,
            elapsedSeconds = elapsedSeconds,
            health = health,
            sanity = sanity,
            battery = battery,
            stamina = stamina,
            flashlightOn = flashlightOn,
            almondWaterCount = almondWaterCount,
            currentBiome = currentBiome,
            threatTier = threatTier,
            headingDegrees = headingDeg,
            spawnBearingDegrees = spawnBearingDeg,
            nearestAnomalyDistanceMeters = nearestMeters,
            glitch = glitchTelemetry,
            statusToast = statusToast,
            causeOfDeath = if (health <= 0f) causeOfDeath.ifEmpty { "Lost in ${currentBiome.displayName}" } else "",
            biomesVisitedCount = visitedBiomes.size,
            anomaliesEncounteredCount = encounteredAnomalies.size
        )
    }

    private fun stepAnomalyAi(
        a: AnomalyEntity,
        dx: Float,
        dy: Float,
        distCells: Float,
        los: Boolean,
        playerSprinting: Boolean,
        playerDirX: Float,
        playerDirY: Float,
        aggroScale: Float,
        dt: Float
    ) {
        val invDist = if (distCells > 0.001f) 1f / distCells else 0f
        val toPlayerX = dx * invDist
        val toPlayerY = dy * invDist

        when (a.type) {
            AnomalyType.STALKER -> {
                val hearRadius = if (playerSprinting) 9.5f else 4.0f
                if (los || distCells < hearRadius) {
                    a.alertState = AnomalyAlertState.HUNTING
                } else if (a.alertState == AnomalyAlertState.HUNTING && distCells > 12f) {
                    a.alertState = AnomalyAlertState.PATROL
                }
                if (a.alertState == AnomalyAlertState.HUNTING) {
                    val chaseSpeed = 2.05f * aggroScale
                    moveEntityWithCollision(a, toPlayerX * chaseSpeed * dt, toPlayerY * chaseSpeed * dt)
                } else {
                    patrolEntity(a, 1.15f * aggroScale, dt)
                }
            }

            AnomalyType.WATCHER -> {
                // Check if player is looking directly at Watcher with active flashlight
                val fromPlayerX = -toPlayerX
                val fromPlayerY = -toPlayerY
                val lookDot = fromPlayerX * playerDirX + fromPlayerY * playerDirY
                val observed = los && flashlightOn && lookDot > 0.65f && distCells < 11f
                if (observed) {
                    a.alertState = AnomalyAlertState.FROZEN
                } else if (distCells < 14f) {
                    a.alertState = AnomalyAlertState.HUNTING
                    val surgeSpeed = 2.45f * aggroScale
                    moveEntityWithCollision(a, toPlayerX * surgeSpeed * dt, toPlayerY * surgeSpeed * dt)
                } else {
                    a.alertState = AnomalyAlertState.PATROL
                    patrolEntity(a, 0.9f, dt)
                }
            }

            AnomalyType.PHANTOM -> {
                a.stateTimer -= dt
                if (distCells < 12f && a.stateTimer <= 0f) {
                    a.stateTimer = (5.2f / aggroScale).coerceAtLeast(2.0f)
                    // Phase-shift closer to an open cell around the player
                    val angle = rng.nextFloat() * 2f * PI.toFloat()
                    val r = rng.nextFloat() * 4.5f + 2.8f
                    val tx = playerX + cos(angle) * r
                    val ty = playerY + sin(angle) * r
                    if (!world.isSolid(floor(tx).toInt(), floor(ty).toInt())) {
                        a.x = tx
                        a.y = ty
                        glitchBurstTimer = maxOf(glitchBurstTimer, 0.45f)
                    }
                }
                if (distCells < 7f) {
                    a.alertState = AnomalyAlertState.HUNTING
                    val drift = 1.55f * aggroScale
                    moveEntityWithCollision(a, toPlayerX * drift * dt, toPlayerY * drift * dt)
                } else {
                    a.alertState = AnomalyAlertState.PATROL
                }
            }

            AnomalyType.MIMIC -> {
                if (!a.mimicRevealed) {
                    a.alertState = AnomalyAlertState.PATROL
                    if (distCells < 3.6f && los) {
                        a.mimicRevealed = true
                        a.alertState = AnomalyAlertState.HUNTING
                        warningBanner = "LURE MIMIC TRIGGERED"
                        warningBannerTimer = 1.4f
                    }
                } else {
                    a.alertState = AnomalyAlertState.HUNTING
                    val lungeSpeed = 2.65f * aggroScale
                    moveEntityWithCollision(a, toPlayerX * lungeSpeed * dt, toPlayerY * lungeSpeed * dt)
                }
            }
        }
    }

    private fun patrolEntity(a: AnomalyEntity, speed: Float, dt: Float) {
        a.stateTimer -= dt
        if (a.stateTimer <= 0f) {
            a.patrolAngle = rng.nextFloat() * 2f * PI.toFloat()
            a.stateTimer = 1.5f + rng.nextFloat() * 2.5f
        }
        val moved = moveEntityWithCollision(
            a,
            cos(a.patrolAngle) * speed * dt,
            sin(a.patrolAngle) * speed * dt
        )
        if (!moved) {
            a.patrolAngle = (a.patrolAngle + PI.toFloat() * 0.65f) % (2f * PI.toFloat())
        }
    }

    private fun moveEntityWithCollision(a: AnomalyEntity, dx: Float, dy: Float): Boolean {
        var moved = false
        val r = 0.24f
        if (!isCircleBlocked(a.x + dx, a.y, r)) {
            a.x += dx
            moved = true
        }
        if (!isCircleBlocked(a.x, a.y + dy, r)) {
            a.y += dy
            moved = true
        }
        return moved
    }

    private fun maintainAnomalyPopulation(distanceMeters: Float, dirX: Float, dirY: Float) {
        val targetCount = when {
            distanceMeters < 35f -> 1
            distanceMeters < 95f -> 3
            distanceMeters < 200f -> 5
            distanceMeters < 350f -> 7
            else -> 9
        }
        if (anomalies.size >= targetCount) return

        repeat(4) {
            if (anomalies.size >= targetCount) return
            val baseAngle = atan2(dirY, dirX) + (rng.nextFloat() - 0.5f) * 2.8f
            val spawnDist = 9f + rng.nextFloat() * 9f
            val sx = playerX + cos(baseAngle) * spawnDist
            val sy = playerY + sin(baseAngle) * spawnDist
            val cx = floor(sx).toInt()
            val cy = floor(sy).toInt()
            if (cx in -2..4 && cy in -2..4) return@repeat
            if (!world.isSolid(cx, cy)) {
                val type = pickAnomalyTypeForDepth(distanceMeters)
                anomalies.add(
                    AnomalyEntity(
                        id = nextId++,
                        type = type,
                        x = cx + 0.5f,
                        y = cy + 0.5f,
                        patrolAngle = rng.nextFloat() * 6.28f,
                        phaseSeed = rng.nextFloat()
                    )
                )
            }
        }
    }

    private fun pickAnomalyTypeForDepth(distanceMeters: Float): AnomalyType {
        val roll = rng.nextFloat()
        return when {
            distanceMeters < 65f -> if (roll < 0.78f) AnomalyType.STALKER else AnomalyType.WATCHER
            distanceMeters < 150f -> when {
                roll < 0.45f -> AnomalyType.STALKER
                roll < 0.80f -> AnomalyType.WATCHER
                else -> AnomalyType.PHANTOM
            }
            else -> when {
                roll < 0.34f -> AnomalyType.STALKER
                roll < 0.62f -> AnomalyType.WATCHER
                roll < 0.84f -> AnomalyType.PHANTOM
                else -> AnomalyType.MIMIC
            }
        }
    }

    private fun spawnItemsInChunk(coord: ChunkCoord, isOrigin: Boolean) {
        val chunk = world.getOrGenerateChunk(coord.cx, coord.cy)
        if (isOrigin) {
            items.add(WorldItem(nextId++, 3.5f, 1.5f, PickupType.BATTERY))
            items.add(WorldItem(nextId++, 1.5f, 3.5f, PickupType.ALMOND_WATER))
            return
        }
        val count = rng.nextInt(1, 4)
        var placed = 0
        repeat(16) {
            if (placed >= count) return
            val lx = rng.nextInt(1, InfiniteWorldGenerator.CHUNK_SIZE - 1)
            val ly = rng.nextInt(1, InfiniteWorldGenerator.CHUNK_SIZE - 1)
            if (chunk.get(lx, ly) == CellType.EMPTY) {
                val wx = coord.cx * InfiniteWorldGenerator.CHUNK_SIZE + lx + 0.5f
                val wy = coord.cy * InfiniteWorldGenerator.CHUNK_SIZE + ly + 0.5f
                val r = rng.nextFloat()
                val type = when {
                    r < 0.50f -> PickupType.BATTERY
                    r < 0.84f -> PickupType.ALMOND_WATER
                    else -> PickupType.TAPE_STABILIZER
                }
                items.add(WorldItem(nextId++, wx, wy, type))
                placed++
            }
        }
    }

    private fun movePlayerWithSliding(dx: Float, dy: Float) {
        if (!isCircleBlocked(playerX + dx, playerY, PLAYER_RADIUS)) {
            playerX += dx
        }
        if (!isCircleBlocked(playerX, playerY + dy, PLAYER_RADIUS)) {
            playerY += dy
        }
    }

    private fun isCircleBlocked(cx: Float, cy: Float, r: Float): Boolean {
        val minX = floor(cx - r).toInt()
        val maxX = floor(cx + r).toInt()
        val minY = floor(cy - r).toInt()
        val maxY = floor(cy + r).toInt()
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (world.isSolid(x, y)) return true
            }
        }
        return false
    }

    private fun hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy)
        val steps = (dist * 4f).toInt().coerceIn(1, 60)
        val sx = dx / steps
        val sy = dy / steps
        var cx = x1
        var cy = y1
        for (i in 1 until steps) {
            cx += sx
            cy += sy
            if (world.isSolid(floor(cx).toInt(), floor(cy).toInt())) return false
        }
        return true
    }

    private fun setToast(msg: String) {
        statusToast = msg
        toastTimer = 2.4f
    }
}
