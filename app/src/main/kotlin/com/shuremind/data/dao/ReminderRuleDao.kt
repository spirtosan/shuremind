package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuremind.data.entity.ReminderRuleEntity

@Dao
internal interface ReminderRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReminderRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ReminderRuleEntity>)

    @Query("SELECT * FROM reminder_rules WHERE task_id = :taskId")
    suspend fun getForTask(taskId: String): List<ReminderRuleEntity>

    @Query("DELETE FROM reminder_rules WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Delete
    suspend fun delete(rule: ReminderRuleEntity)

    /** M5 export: every row (DATA_MODEL.md full-fidelity). */
    @Query("SELECT * FROM reminder_rules")
    suspend fun getAllForExport(): List<ReminderRuleEntity>

    /** M5 import (D-31 replace-all). */
    @Query("DELETE FROM reminder_rules")
    suspend fun deleteAll()
}
