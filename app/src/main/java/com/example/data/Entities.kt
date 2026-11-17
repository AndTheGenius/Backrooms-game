package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_records")
data class RunRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seed: Long,
    val peakDistanceMeters: Float,
    val durationSeconds: Int,
    val glitchesSurvived: Int,
    val spatialShiftsEndured: Int,
    val biomesVisitedCount: Int,
    val anomaliesEncountered: Int,
    val causeOfDeath: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "discovered_entries")
data class DiscoveredEntryEntity(
    @PrimaryKey val entryId: String,
    val category: String,
    val encounterCount: Int,
    val deepestMeters: Float,
    val firstSeenAtMillis: Long = System.currentTimeMillis()
)
