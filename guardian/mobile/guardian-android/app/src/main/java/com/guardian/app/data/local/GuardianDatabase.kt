package com.guardian.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.Severity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "crash_events")
data class CrashEventEntity(
    @PrimaryKey val eventId: String,
    val timestamp: String,
    val severity: String,
    val confidence: Double,
    val latitude: Double?,
    val longitude: Double?,
    val phoneConnected: Boolean,
    val delivered: Boolean,
    val status: String,
    /** DEVICE (BLE), BACKEND (REST) or MOCK. */
    val source: String,
) {
    fun toRecord() = CrashEventRecord(
        eventId, timestamp, Severity.parse(severity), confidence, latitude, longitude, phoneConnected, delivered, status, source,
    )

    companion object {
        fun from(r: CrashEventRecord) = CrashEventEntity(
            r.eventId, r.timestamp, r.severity.name, r.confidence, r.latitude, r.longitude, r.phoneConnected, r.delivered,
            r.status, r.source,
        )
    }
}

@Dao
interface CrashEventDao {
    @Query("SELECT * FROM crash_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CrashEventEntity>>

    @Query("SELECT * FROM crash_events WHERE eventId = :eventId")
    suspend fun get(eventId: String): CrashEventEntity?

    @Upsert
    suspend fun upsert(entity: CrashEventEntity)

    @Query("UPDATE crash_events SET status = :status WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: String)

    @Query("DELETE FROM crash_events")
    suspend fun clear()
}

@Database(entities = [CrashEventEntity::class], version = 1, exportSchema = false)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun crashEvents(): CrashEventDao

    companion object {
        fun create(context: Context): GuardianDatabase =
            Room.databaseBuilder(context, GuardianDatabase::class.java, "guardian.db").build()
    }
}
