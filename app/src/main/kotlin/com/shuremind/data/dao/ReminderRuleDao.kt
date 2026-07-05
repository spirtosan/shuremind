package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuremind.data.entity.ReminderRuleEntity

@Dao
interface ReminderRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReminderRuleEntity)

    @Query("SELECT * FROM reminder_rules WHERE task_id = :taskId")
    suspend fun getForTask(taskId: String): List<ReminderRuleEntity>

    @Query("DELETE FROM reminder_rules WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Delete
    suspend fun delete(rule: ReminderRuleEntity)
}
