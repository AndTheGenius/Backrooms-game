package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackroomsDatabase
import com.example.data.RunRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Backrooms: Null Sector", appName)
    }

    @Test
    fun `room database persists and queries run records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BackroomsDatabase.getInstance(context)
        val dao = db.runRecordDao()
        dao.clearAllRuns()

        val id = dao.insertRun(
            RunRecordEntity(
                seed = 12345L,
                peakDistanceMeters = 184.5f,
                durationSeconds = 95,
                glitchesSurvived = 8,
                spatialShiftsEndured = 3,
                biomesVisitedCount = 3,
                anomaliesEncountered = 2,
                causeOfDeath = "Terminated by Bacteria Stalker"
            )
        )

        val fetched = dao.getRunById(id)
        assertNotNull(fetched)
        assertEquals(184.5f, fetched!!.peakDistanceMeters, 0.01f)
        assertEquals(184.5f, dao.observeBestDistance().first() ?: 0f, 0.01f)
        dao.clearAllRuns()
    }
}
