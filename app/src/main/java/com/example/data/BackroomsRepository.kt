package com.example.data

import com.example.engine.CamcorderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BackroomsRepository(
    private val runRecordDao: RunRecordDao,
    private val discoveredEntryDao: DiscoveredEntryDao,
    private val settingsDataStore: SettingsDataStore
) {
    val runsByDistance: Flow<List<RunRecordEntity>> = runRecordDao.observeRunsByDistance()
    val runsByDate: Flow<List<RunRecordEntity>> = runRecordDao.observeRunsByDate()
    val bestDistance: Flow<Float?> = runRecordDao.observeBestDistance()
    val totalRuns: Flow<Int> = runRecordDao.observeTotalRuns()
    val totalGlitches: Flow<Int?> = runRecordDao.observeTotalGlitches()
    val discoveredEntries: Flow<List<DiscoveredEntryEntity>> = discoveredEntryDao.observeAllEntries()
    val settings: Flow<CamcorderSettings> = settingsDataStore.settingsFlow

    suspend fun recordCompletedRun(
        seed: Long,
        peakDistanceMeters: Float,
        durationSeconds: Int,
        glitchesSurvived: Int,
        spatialShiftsEndured: Int,
        biomesVisitedCount: Int,
        anomaliesEncountered: Int,
        causeOfDeath: String
    ): Pair<Long, Boolean> {
        val previousBest = runRecordDao.observeBestDistance().first() ?: 0f
        val isNewRecord = peakDistanceMeters > previousBest && peakDistanceMeters >= 5f
        val runId = runRecordDao.insertRun(
            RunRecordEntity(
                seed = seed,
                peakDistanceMeters = peakDistanceMeters,
                durationSeconds = durationSeconds,
                glitchesSurvived = glitchesSurvived,
                spatialShiftsEndured = spatialShiftsEndured,
                biomesVisitedCount = biomesVisitedCount,
                anomaliesEncountered = anomaliesEncountered,
                causeOfDeath = causeOfDeath
            )
        )
        return runId to isNewRecord
    }

    suspend fun getRunById(runId: Long): RunRecordEntity? = runRecordDao.getRunById(runId)

    suspend fun markDiscovered(entryId: String, category: String, depthMeters: Float) {
        val existing = discoveredEntryDao.getEntryById(entryId)
        if (existing == null) {
            discoveredEntryDao.upsertEntry(
                DiscoveredEntryEntity(
                    entryId = entryId,
                    category = category,
                    encounterCount = 1,
                    deepestMeters = depthMeters
                )
            )
        } else {
            discoveredEntryDao.upsertEntry(
                existing.copy(
                    encounterCount = existing.encounterCount + 1,
                    deepestMeters = maxOf(existing.deepestMeters, depthMeters)
                )
            )
        }
    }

    suspend fun updateSettings(transform: (CamcorderSettings) -> CamcorderSettings) {
        settingsDataStore.updateSettings(transform)
    }

    suspend fun clearArchive() {
        runRecordDao.clearAllRuns()
    }
}
