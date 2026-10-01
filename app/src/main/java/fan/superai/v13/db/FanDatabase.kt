package fan.superai.v13.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FanRecordEntity::class, PredictionRecordEntity::class, PredictionExplanationEntity::class,
        ModelStateEntity::class, ModelPerformanceEntity::class, RegimeStateEntity::class,
        CalibrationStateEntity::class, EnsembleStateEntity::class, ReplayStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FanDatabase : RoomDatabase() {
    abstract fun dao(): V13Dao

    companion object {
        @Volatile private var inst: FanDatabase? = null

        fun get(ctx: Context): FanDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, FanDatabase::class.java, "fan_super_v13.db")
                .build().also { inst = it }
        }

        /** Testler için bellek içi veritabanı. */
        fun inMemory(ctx: Context): FanDatabase =
            Room.inMemoryDatabaseBuilder(ctx.applicationContext, FanDatabase::class.java).allowMainThreadQueries().build()
    }
}
