package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunRecordDao {
    @Query("SELECT * FROM run_records ORDER BY peakDistanceMeters DESC, createdAtMillis DESC")
    fun observeRunsByDistance(): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records ORDER BY createdAtMillis DESC")
    fun observeRunsByDate(): Flow<List<RunRecordEntity>>

    @Query("SELECT MAX(peakDistanceMeters) FROM run_records")
    fun observeBestDistance(): Flow<Float?>

    @Query("SELECT COUNT(*) FROM run_records")
    fun observeTotalRuns(): Flow<Int>

    @Query("SELECT SUM(glitchesSurvived) FROM run_records")
    fun observeTotalGlitches(): Flow<Int?>

    @Query("SELECT * FROM run_records WHERE id = :runId LIMIT 1")
    suspend fun getRunById(runId: Long): RunRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunRecordEntity): Long

    @Query("DELETE FROM run_records")
    suspend fun clearAllRuns()
}

@Dao
interface DiscoveredEntryDao {
    @Query("SELECT * FROM discovered_entries ORDER BY firstSeenAtMillis ASC")
    fun observeAllEntries(): Flow<List<DiscoveredEntryEntity>>

    @Query("SELECT * FROM discovered_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): DiscoveredEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DiscoveredEntryEntity)

    @Query("DELETE FROM discovered_entries")
    suspend fun clearEntries()
}
