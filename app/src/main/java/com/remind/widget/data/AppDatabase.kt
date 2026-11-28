package com.remind.widget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** EventType 用英文名存字符串，改枚举顺序不会破坏旧数据 */
class Converters {
    @TypeConverter
    fun toType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromType(type: EventType): String = type.name
}

@Database(
    entities = [Group::class, Event::class, DoneRecord::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun eventDao(): EventDao
    abstract fun doneDao(): DoneDao

    companion object {
        /**
         * v2：done_records 增加 ignored 列（区分「完成」和「忽略」）。
         * 必须写迁移 —— 用 fallbackToDestructiveMigration 会把你已有的栏目和事件全清掉。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE done_records ADD COLUMN ignored INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "remind.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
