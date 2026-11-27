package com.remind.widget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** EventType 用英文名存字符串，改枚举顺序不会破坏旧数据 */
class Converters {
    @TypeConverter
    fun toType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromType(type: EventType): String = type.name
}

@Database(
    entities = [Group::class, Event::class, DoneRecord::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun eventDao(): EventDao
    abstract fun doneDao(): DoneDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "remind.db"
            )
                // 单机 App 无迁移负担，版本变化直接重建（数据可接受丢失，避免升级即崩）
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
