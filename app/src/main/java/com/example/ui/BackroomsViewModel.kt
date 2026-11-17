package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackroomsDatabase
import com.example.data.BackroomsRepository
import com.example.data.CodexCatalog
import com.example.data.DiscoveredEntryEntity
import com.example.data.RunRecordEntity
import com.example.data.SettingsDataStore
import com.example.engine.AnomalyType
import com.example.engine.AudioSynthEngine
import com.example.engine.BiomeType
import com.example.engine.CamcorderSettings
import com.example.engine.GameHudState
import com.example.engine.PickupType
import com.example.engine.ProceduralTextures
import com.example.engine.RaycasterRenderer
import com.example.engine.SimulationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

data class ArchiveUiState(
    val bestDistanceMeters: Float = 0f,
    val totalRuns: Int = 0,
    val totalGlitches: Int = 0,
    val runsByDistance: List<RunRecordEntity> = emptyList(),
    val runsByDate: List<RunRecordEntity> = emptyList(),
    val discoveredEntries: Map<String, DiscoveredEntryEntity> = emptyMap()
)

class BackroomsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BackroomsDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = BackroomsRepository(
        runRecordDao = database.runRecordDao(),
        discoveredEntryDao = database.discoveredEntryDao(),
        settingsDataStore = settingsStore
    )

    private val textures = ProceduralTextures()
    private val renderer = RaycasterRenderer(textures)
    private val audioEngine = AudioSynthEngine()
    private val androidBitmap = Bitmap.createBitmap(
        RaycasterRenderer.WIDTH,
        RaycasterRenderer.HEIGHT,
        Bitmap.Config.ARGB_8888
    )

    val frameBitmap: ImageBitmap = androidBitmap.asImageBitmap()

    val settings: StateFlow<CamcorderSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CamcorderSettings())

    val archiveState: StateFlow<ArchiveUiState> = combine(
        repository.bestDistance,
        repository.totalRuns,
        repository.totalGlitches,
        repository.runsByDistance,
        repository.runsByDate,
        repository.discoveredEntries
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val best = values[0] as Float? ?: 0f
        val count = values[1] as Int
        val glitches = values[2] as Int? ?: 0
        @Suppress("UNCHECKED_CAST")
        val byDist = values[3] as List<RunRecordEntity>
        @Suppress("UNCHECKED_CAST")
        val byDate = values[4] as List<RunRecordEntity>
        @Suppress("UNCHECKED_CAST")
        val entries = (values[5] as List<DiscoveredEntryEntity>).associateBy { it.entryId }
        ArchiveUiState(
            bestDistanceMeters = best,
            totalRuns = count,
            totalGlitches = glitches,
            runsByDistance = byDist,
            runsByDate = byDate,
            discoveredEntries = entries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArchiveUiState())

    private val _hudState = MutableStateFlow(GameHudState())
    val hudState: StateFlow<GameHudState> = _hudState.asStateFlow()

    private val _debriefRun = MutableStateFlow<RunRecordEntity?>(null)
    val debriefRun: StateFlow<RunRecordEntity?> = _debriefRun.asStateFlow()

    private var simulation: SimulationEngine? = null
    private var gameLoopJob: Job? = null

    // Volatile/Atomic input accumulators read by simulation loop
    @Volatile
    private var moveForwardInput = 0f

    @Volatile
    private var moveRightInput = 0f

    @Volatile
    private var sprintInput = false

    private val pendingLookDeltaPx = AtomicReference(0f)

    private val simListener = object : SimulationEngine.EventListener {
        override fun onBiomeDiscovered(biome: BiomeType, depthMeters: Float) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.markDiscovered(biome.id, CodexCatalog.CATEGORY_BIOME, depthMeters)
            }
        }

        override fun onAnomalyEncountered(anomaly: AnomalyType, depthMeters: Float) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.markDiscovered(anomaly.id, CodexCatalog.CATEGORY_ANOMALY, depthMeters)
            }
        }

        override fun onGlitchSpike(intensity: Float, isSpatialShift: Boolean) {
            audioEngine.triggerGlitchBurst(if (isSpatialShift) 0.95f else intensity)
            if (isSpatialShift && !settings.value.glitchSafetyMode) {
                triggerHaptic(durationMs = 45L, amplitude = 90)
            }
        }

        override fun onPickup(type: PickupType) {
            triggerHaptic(durationMs = 24L, amplitude = 70)
        }

        override fun onDamageTaken(amount: Float) {
            audioEngine.triggerGlitchBurst(1.0f)
            triggerHaptic(durationMs = 110L, amplitude = 210)
        }
    }

    fun startNewTape() {
        stopLoop()
        val seed = System.currentTimeMillis()
        val engine = SimulationEngine(seed)
        simulation = engine
        moveForwardInput = 0f
        moveRightInput = 0f
        sprintInput = false
        pendingLookDeltaPx.set(0f)

        viewModelScope.launch(Dispatchers.IO) {
            repository.markDiscovered(BiomeType.MONO_YELLOW.id, CodexCatalog.CATEGORY_BIOME, 0f)
        }

        val initial = engine.step(0.016f, 0f, 0f, 0f, false, simListener)
        renderer.renderFrame(
            world = engine.world,
            playerX = engine.playerX,
            playerY = engine.playerY,
            headingRad = engine.headingRad,
            headBobOffsetPx = engine.headBobOffsetPx,
            flashlightOn = engine.flashlightOn,
            batteryPct = engine.battery,
            healthPct = engine.health,
            sanityPct = engine.sanity,
            timeSec = engine.elapsedSeconds,
            glitch = engine.glitchTelemetry,
            safetyMode = settings.value.glitchSafetyMode,
            items = engine.items,
            anomalies = engine.anomalies
        )
        androidBitmap.setPixels(
            renderer.pixels,
            0,
            RaycasterRenderer.WIDTH,
            0,
            0,
            RaycasterRenderer.WIDTH,
            RaycasterRenderer.HEIGHT
        )
        _hudState.value = initial.copy(frameVersion = 1L)
        _debriefRun.value = null

        audioEngine.start()
        startLoop()
    }

    fun pauseGame() {
        if (!_hudState.value.isRunning || _hudState.value.isGameOver) return
        stopLoop()
        _hudState.value = _hudState.value.copy(isPaused = true)
    }

    fun resumeGame() {
        val current = _hudState.value
        if (!current.isRunning || current.isGameOver) return
        _hudState.value = current.copy(isPaused = false)
        audioEngine.start()
        startLoop()
    }

    fun ejectTapeEarly() {
        val current = _hudState.value
        if (current.isGameOver) return
        stopLoop()
        finalizeRun(current.copy(causeOfDeath = "Tape Ejected Manually"))
    }

    fun setMoveInput(forward: Float, right: Float) {
        moveForwardInput = forward.coerceIn(-1f, 1f)
        moveRightInput = right.coerceIn(-1f, 1f)
    }

    fun addLookDeltaPx(deltaPx: Float) {
        pendingLookDeltaPx.getAndUpdate { it + deltaPx }
    }

    fun setSprinting(sprinting: Boolean) {
        sprintInput = sprinting
    }

    fun toggleFlashlight() {
        simulation?.toggleFlashlight()
    }

    fun useAlmondWater() {
        if (simulation?.useAlmondWater() == true) {
            triggerHaptic(durationMs = 30L, amplitude = 85)
        }
    }

    fun updateSettings(transform: (CamcorderSettings) -> CamcorderSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun clearArchive() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearArchive()
        }
    }

    fun loadRunForDebrief(runId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _debriefRun.value = repository.getRunById(runId)
        }
    }

    private fun startLoop() {
        if (gameLoopJob?.isActive == true) return
        val engine = simulation ?: return

        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var lastNanos = System.nanoTime()
            var frameCounter = _hudState.value.frameVersion

            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0.004f, 0.05f)
                lastNanos = now

                val cfg = settings.value
                val rawLookPx = pendingLookDeltaPx.getAndSet(0f)
                val invertSign = if (cfg.invertLook) -1f else 1f
                val lookRad = rawLookPx * 0.0048f * cfg.lookSensitivity * invertSign

                val state = engine.step(
                    dtRaw = dt,
                    moveForwardInput = moveForwardInput,
                    moveRightInput = moveRightInput,
                    lookDeltaRad = lookRad,
                    sprintInput = sprintInput,
                    listener = simListener
                )

                renderer.renderFrame(
                    world = engine.world,
                    playerX = engine.playerX,
                    playerY = engine.playerY,
                    headingRad = engine.headingRad,
                    headBobOffsetPx = engine.headBobOffsetPx,
                    flashlightOn = engine.flashlightOn,
                    batteryPct = engine.battery,
                    healthPct = engine.health,
                    sanityPct = engine.sanity,
                    timeSec = engine.elapsedSeconds,
                    glitch = engine.glitchTelemetry,
                    safetyMode = cfg.glitchSafetyMode,
                    items = engine.items,
                    anomalies = engine.anomalies
                )
                androidBitmap.setPixels(
                    renderer.pixels,
                    0,
                    RaycasterRenderer.WIDTH,
                    0,
                    0,
                    RaycasterRenderer.WIDTH,
                    RaycasterRenderer.HEIGHT
                )

                audioEngine.updateParameters(
                    audioEnabled = cfg.audioEnabled,
                    volume = cfg.audioVolume,
                    glitch = state.glitch.overallIntensity,
                    nearestAnomalyMeters = state.nearestAnomalyDistanceMeters
                )

                frameCounter++

                if (state.isGameOver) {
                    withContext(Dispatchers.Main) {
                        stopLoop()
                        finalizeRun(state.copy(frameVersion = frameCounter))
                    }
                    break
                } else {
                    _hudState.value = state.copy(frameVersion = frameCounter)
                }

                // Target ~45-60fps smoothly without starving UI thread
                val frameMs = (System.nanoTime() - now) / 1_000_000L
                val sleepMs = (16L - frameMs).coerceAtLeast(2L)
                delay(sleepMs)
            }
        }
    }

    private fun finalizeRun(finalState: GameHudState) {
        viewModelScope.launch {
            val (runId, isNewRecord) = withContext(Dispatchers.IO) {
                repository.recordCompletedRun(
                    seed = finalState.seed,
                    peakDistanceMeters = finalState.peakDistanceMeters,
                    durationSeconds = finalState.elapsedSeconds.toInt(),
                    glitchesSurvived = finalState.glitch.glitchesSurvivedCount,
                    spatialShiftsEndured = finalState.glitch.nonEuclideanShiftsCount,
                    biomesVisitedCount = finalState.biomesVisitedCount,
                    anomaliesEncountered = finalState.anomaliesEncounteredCount,
                    causeOfDeath = finalState.causeOfDeath.ifEmpty { "Signal Lost" }
                )
            }
            _debriefRun.value = withContext(Dispatchers.IO) { repository.getRunById(runId) }
            _hudState.value = finalState.copy(
                isRunning = false,
                isGameOver = true,
                isPaused = false,
                completedRunId = runId,
                isNewRecord = isNewRecord
            )
        }
    }

    private fun stopLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        audioEngine.stop()
    }

    private fun triggerHaptic(durationMs: Long, amplitude: Int) {
        try {
            val app = getApplication<Application>()
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLoop()
    }
}
