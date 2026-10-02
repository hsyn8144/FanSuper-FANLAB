package fan.lightningroulette.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [DatasetE::class, DatasetVersionE::class, SpinE::class, PredictionE::class, EvaluationE::class, ExperimentE::class,
        StateE::class, LogE::class, ModelVersionE::class, ImportBatchE::class],
    version = 1, exportSchema = false
)
abstract class LrDb : RoomDatabase() {
    abstract fun dao(): LrDao

    companion object {
        const val VERSION = 1
        const val FILE = "lightning_roulette.db"
        @Volatile private var inst: LrDb? = null

        fun get(ctx: Context): LrDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, LrDb::class.java, FILE)
                .addMigrations(*Migrations.ALL)      // yıkıcı geri dönüş (fallbackToDestructiveMigration) YOK: veri asla sessizce silinmez
                .build().also { inst = it }
        }

        /** Test/yedek geri yükleme için bağlantıyı kapatıp bırakır. */
        fun closeAndForget() { synchronized(this) { inst?.close(); inst = null } }
    }
}

/** Şema geçişleri. Şu an v1 ilk sürümdür; sonraki şema değişiklikleri buraya Migration olarak eklenir. */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
