package com.remind.widget.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Group>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun all(): List<Group>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun byId(id: Long): Group?

    @Insert
    suspend fun insert(group: Group): Long

    @Update
    suspend fun update(group: Group)

    @Delete
    suspend fun delete(group: Group)

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE enabled = 1 ORDER BY sortOrder, id")
    suspend fun allEnabled(): List<Event>

    @Query("SELECT * FROM events WHERE groupId = :groupId ORDER BY sortOrder, id")
    fun observeByGroup(groupId: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE groupId = :groupId ORDER BY sortOrder, id")
    suspend fun byGroup(groupId: Long): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): Event?

    @Insert
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}

@Dao
interface DoneDao {
    @Query("SELECT * FROM done_records WHERE dayKey = :day ORDER BY doneAt ASC")
    suspend fun byDay(day: String): List<DoneRecord>

    @Query("SELECT * FROM done_records WHERE dayKey = :day ORDER BY doneAt ASC")
    fun observeByDay(day: String): Flow<List<DoneRecord>>

    @Query("SELECT * FROM done_records ORDER BY doneAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DoneRecord>

    /** 某个事件历史上所有"已完成的那一次"，用于判定今天这次是否做过 */
    @Query("SELECT dueKey FROM done_records WHERE eventId = :eventId")
    suspend fun dueKeysOf(eventId: Long): List<String>

    @Insert
    suspend fun insert(record: DoneRecord): Long

    @Query("DELETE FROM done_records WHERE eventId = :eventId AND dueKey = :dueKey")
    suspend fun deleteByDue(eventId: Long, dueKey: String)

    @Query("DELETE FROM done_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 历史保留策略：清掉早于某天的记录 */
    @Query("DELETE FROM done_records WHERE dayKey < :before")
    suspend fun pruneBefore(before: String)
}
