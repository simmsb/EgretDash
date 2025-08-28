package com.simmsb.egretdash

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.simmsb.egretdash.ScooterStatus.DrivingMode
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


internal class InstantConverter {
    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun longToInstant(value: Long?): Instant? =
        value?.let(Instant::fromEpochMilliseconds)

    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun instantToLong(instant: Instant?): Long? =
        instant?.toEpochMilliseconds()
}

@Entity(tableName = "statuses")
data class ScooterStatusDB @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: Instant,
    val charging: Boolean,
    val drivingMode: DrivingMode,
    val ecoModeRange: Float,
    val errorCode: Int,
    val findMyStatus: Int,
    val lightsOn: Boolean,
    val locked: Boolean,
    val powerOutput: Int,
    val poweredOn: Boolean,
    val rangeFactor: Int,
    val speed: Float,
    val sportModeRange: Float,
    val temperatureHigh: Boolean,
    val temperatureLow: Boolean,
    val throttle: Int,
    val tourModeRange: Float,
)


@Dao
interface StatusDao {
    @Insert()
    suspend fun insert(scooterStatusDB: ScooterStatusDB)

    @Query("SELECT * from statuses ORDER BY date ASC")
    fun getAllItems(): Flow<List<ScooterStatusDB>>

    @Query("SELECT * from statuses ORDER BY date DESC LIMIT :n")
    fun getLatest(n: Int): Flow<List<ScooterStatusDB>>
}

@Entity(tableName = "odo")
data class OdometerDB(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: Instant,
    val hectoMeters: Int,
    val total: Int,
    val totalEco: Int,
    val totalTour: Int,
    val totalSport: Int,
)

@Dao
interface OdoDao {
    @Insert()
    suspend fun insert(odometerDB: OdometerDB)

    @Query("SELECT * from odo ORDER BY date ASC")
    fun getAllItems(): Flow<List<OdometerDB>>

    @Query("SELECT * from odo ORDER BY date DESC LIMIT :n")
    fun getLatest(n: Int): Flow<List<OdometerDB>>
}

@Dao
interface RawDao {
    @RawQuery
    suspend fun raw(sql: RoomRawQuery): Int
}

@Database(entities = [ScooterStatusDB::class, OdometerDB::class], version = 1, exportSchema = false)
@TypeConverters(
    InstantConverter::class,
)
abstract class DashboardDatabase : RoomDatabase() {

    abstract fun statusDao(): StatusDao
    abstract fun odoDao(): OdoDao

    abstract fun rawDao(): RawDao

    suspend fun checkpoint() {
        rawDao().raw(RoomRawQuery("pragma wal_checkpoint(full)"))
    }

    suspend fun dump(context: Context): ByteArray {
        val dbPath = openHelper.readableDatabase.path!!
        checkpoint()
        return File(dbPath).readBytes()
    }

    companion object {
        @Volatile
        private var Instance: DashboardDatabase? = null

        fun getDatabase(context: Context): DashboardDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, DashboardDatabase::class.java, "dashboard_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}