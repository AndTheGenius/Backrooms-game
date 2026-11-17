import CoreGraphics
import Foundation
import SwiftUI
import UIKit

enum BackroomsScreen: Hashable {
    case title
    case play
    case debrief
    case archive
    case codex
    case settings
}

@MainActor
final class BackroomsStore: ObservableObject, SimulationEventListener {
    @Published var path: [BackroomsScreen] = []
    @Published var hudState = GameHudState()
    @Published var frameImage: CGImage?
    @Published var runs: [RunRecord] = []
    @Published var discoveredEntries: [String: DiscoveredEntry] = [:]
    @Published var settings: CamcorderSettings = CamcorderSettings()
    @Published var lastSavedRun: RunRecord?

    private let textures = ProceduralTextures()
    private lazy var renderer = RaycasterRenderer(textures: textures)
    private let audio = AudioSynthEngine()
    private var sim: SimulationEngine?
    private var loopTask: Task<Void, Never>?

    private var moveForward: Float = 0
    private var moveRight: Float = 0
    private var sprinting: Bool = false
    private var pendingLookDeltaPx: Float = 0

    private let runsKey = "backrooms_runs_v1"
    private let codexKey = "backrooms_codex_v1"
    private let settingsKey = "backrooms_settings_v1"

    var bestDistanceMeters: Float {
        runs.map(\.peakDistanceMeters).max() ?? 0
    }

    var totalGlitches: Int {
        runs.reduce(0) { $0 + $1.glitchesSurvived }
    }

    init() {
        loadPersistence()
    }

    func startNewTape() {
        stopLoop()
        let seed = UInt64(Date().timeIntervalSince1970 * 1000)
        let engine = SimulationEngine(seed: seed)
        self.sim = engine
        moveForward = 0; moveRight = 0; sprinting = false; pendingLookDeltaPx = 0
        markDiscovered(id: BiomeType.monoYellow.rawValue, category: "BIOME", depth: 0)
        let initHud = engine.step(dtRaw: 0.016, moveForwardInput: 0, moveRightInput: 0, lookDeltaRad: 0, sprintInput: false, listener: self)
        frameImage = renderer.renderFrame(
            world: engine.world, playerX: engine.playerX, playerY: engine.playerY,
            headingRad: engine.headingRad, headBobOffsetPx: engine.headBobOffsetPx,
            flashlightOn: engine.flashlightOn, batteryPct: engine.battery,
            healthPct: engine.health, sanityPct: engine.sanity,
            timeSec: engine.elapsedSeconds, glitch: engine.glitchTelemetry,
            safetyMode: settings.glitchSafetyMode, items: engine.items, anomalies: engine.anomalies
        )
        hudState = initHud
        lastSavedRun = nil
        audio.start()
        startLoop()
        path = [.play]
    }

    func pauseGame() {
        guard hudState.isRunning && !hudState.isGameOver else { return }
        stopLoop()
        hudState.isPaused = true
    }

    func resumeGame() {
        guard hudState.isRunning && !hudState.isGameOver else { return }
        hudState.isPaused = false
        audio.start()
        startLoop()
    }

    func ejectTapeEarly() {
        stopLoop()
        var finalState = hudState
        finalState.causeOfDeath = "Tape Ejected Manually"
        finalizeRun(finalState)
    }

    func setMoveInput(forward: Float, right: Float) {
        moveForward = max(-1, min(1, forward))
        moveRight = max(-1, min(1, right))
    }

    func addLookDeltaPx(_ dx: Float) {
        pendingLookDeltaPx += dx
    }

    func setSprinting(_ active: Bool) { sprinting = active }
    func toggleFlashlight() { sim?.toggleFlashlight() }
    func useAlmondWater() {
        if sim?.useAlmondWater() == true {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    func updateSettings(_ transform: (inout CamcorderSettings) -> Void) {
        transform(&settings)
        if let data = try? JSONEncoder().encode(settings) {
            UserDefaults.standard.set(data, forKey: settingsKey)
        }
    }

    func clearArchive() {
        runs.removeAll()
        UserDefaults.standard.removeObject(forKey: runsKey)
    }

    private func startLoop() {
        loopTask?.cancel()
        loopTask = Task { [weak self] in
            var last = CACurrentMediaTime()
            while !Task.isCancelled {
                guard let self, let engine = self.sim else { break }
                let now = CACurrentMediaTime()
                let dt = Float(max(0.004, min(0.05, now - last)))
                last = now

                let lookPx = self.pendingLookDeltaPx
                self.pendingLookDeltaPx = 0
                let invert: Float = self.settings.invertLook ? -1 : 1
                let lookRad = lookPx * 0.0048 * self.settings.lookSensitivity * invert

                let state = engine.step(
                    dtRaw: dt,
                    moveForwardInput: self.moveForward,
                    moveRightInput: self.moveRight,
                    lookDeltaRad: lookRad,
                    sprintInput: self.sprinting,
                    listener: self
                )

                self.frameImage = self.renderer.renderFrame(
                    world: engine.world, playerX: engine.playerX, playerY: engine.playerY,
                    headingRad: engine.headingRad, headBobOffsetPx: engine.headBobOffsetPx,
                    flashlightOn: engine.flashlightOn, batteryPct: engine.battery,
                    healthPct: engine.health, sanityPct: engine.sanity,
                    timeSec: engine.elapsedSeconds, glitch: engine.glitchTelemetry,
                    safetyMode: self.settings.glitchSafetyMode, items: engine.items, anomalies: engine.anomalies
                )

                self.audio.updateParameters(
                    audioEnabled: self.settings.audioEnabled,
                    volume: self.settings.audioVolume,
                    glitch: state.glitch.overallIntensity,
                    nearestAnomalyMeters: state.nearestAnomalyDistanceMeters
                )

                if state.isGameOver {
                    self.stopLoop()
                    self.finalizeRun(state)
                    break
                } else {
                    self.hudState = state
                }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }

    private func stopLoop() {
        loopTask?.cancel()
        loopTask = nil
        audio.stop()
    }

    private func finalizeRun(_ state: GameHudState) {
        let isNewRecord = state.peakDistanceMeters > bestDistanceMeters && state.peakDistanceMeters >= 5
        let record = RunRecord(
            seed: state.seed,
            peakDistanceMeters: state.peakDistanceMeters,
            durationSeconds: Int(state.elapsedSeconds),
            glitchesSurvived: state.glitch.glitchesSurvivedCount,
            spatialShiftsEndured: state.glitch.nonEuclideanShiftsCount,
            biomesVisitedCount: state.biomesVisitedCount,
            anomaliesEncountered: state.anomaliesEncounteredCount,
            causeOfDeath: state.causeOfDeath.isEmpty ? "Signal Lost" : state.causeOfDeath,
            createdAt: Date()
        )
        runs.insert(record, at: 0)
        if let data = try? JSONEncoder().encode(runs) {
            UserDefaults.standard.set(data, forKey: runsKey)
        }
        lastSavedRun = record
        var next = state
        next.isRunning = false
        next.isGameOver = true
        next.isPaused = false
        next.isNewRecord = isNewRecord
        hudState = next
        path = [.debrief]
    }

    private func markDiscovered(id: String, category: String, depth: Float) {
        if var existing = discoveredEntries[id] {
            existing.encounterCount += 1
            existing.deepestMeters = max(existing.deepestMeters, depth)
            discoveredEntries[id] = existing
        } else {
            discoveredEntries[id] = DiscoveredEntry(entryId: id, category: category, encounterCount: 1, deepestMeters: depth, firstSeenAt: Date())
        }
        if let data = try? JSONEncoder().encode(discoveredEntries) {
            UserDefaults.standard.set(data, forKey: codexKey)
        }
    }

    private func loadPersistence() {
        if let d = UserDefaults.standard.data(forKey: runsKey), let r = try? JSONDecoder().decode([RunRecord].self, from: d) { runs = r }
        if let d = UserDefaults.standard.data(forKey: codexKey), let c = try? JSONDecoder().decode([String: DiscoveredEntry].self, from: d) { discoveredEntries = c }
        if let d = UserDefaults.standard.data(forKey: settingsKey), let s = try? JSONDecoder().decode(CamcorderSettings.self, from: d) { settings = s }
    }

    // MARK: - SimulationEventListener
    nonisolated func onBiomeDiscovered(_ biome: BiomeType, depthMeters: Float) {
        Task { @MainActor in self.markDiscovered(id: biome.rawValue, category: "BIOME", depth: depthMeters) }
    }
    nonisolated func onAnomalyEncountered(_ anomaly: AnomalyType, depthMeters: Float) {
        Task { @MainActor in self.markDiscovered(id: anomaly.rawValue, category: "ANOMALY", depth: depthMeters) }
    }
    nonisolated func onGlitchSpike(intensity: Float, isSpatialShift: Bool) {
        Task { @MainActor in
            self.audio.triggerGlitchBurst(strength: isSpatialShift ? 0.95 : intensity)
            if isSpatialShift && !self.settings.glitchSafetyMode {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
        }
    }
    nonisolated func onPickup(_ type: PickupType) {
        Task { @MainActor in UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }
    nonisolated func onDamageTaken(_ amount: Float) {
        Task { @MainActor in
            self.audio.triggerGlitchBurst(strength: 1.0)
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        }
    }
}
