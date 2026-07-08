package com.shuremind.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * D-42: first real (non-destructive) Room migration — both phones carry live data, so a
 * destructive fallback is no longer acceptable, now or ever again. Purely additive: one nullable-
 * free column with a SQL DEFAULT matching TaskEntity.alarmMode's @ColumnInfo(defaultValue = "0"),
 * so Room's schema validation agrees with what this migration actually creates.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN alarm_mode INTEGER NOT NULL DEFAULT 0")
    }
}
