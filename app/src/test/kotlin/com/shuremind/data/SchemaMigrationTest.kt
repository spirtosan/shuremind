package com.shuremind.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * D-42: no instrumentation (Room's MigrationTestHelper) is available in this environment, so this
 * asserts the migration's additive character against Room's own exported schema JSON instead (per
 * CLAUDE.md "Room migration required" — both phones carry live data, destructive is unacceptable).
 * Real on-device verification of MIGRATION_2_3 against each phone's live v2 database is a manual
 * checklist item (Session 10 proposal), not something a JVM test can exercise.
 */
class SchemaMigrationTest {

    private fun schema(version: Int): kotlinx.serialization.json.JsonObject {
        val file = File("schemas/com.shuremind.data.AppDatabase/$version.json")
        check(file.exists()) { "Expected Room-exported schema at ${file.absolutePath} — run a build first (room { schemaDirectory(...) })." }
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun entityFields(schema: kotlinx.serialization.json.JsonObject, tableName: String) =
        schema["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == tableName }["fields"]!!.jsonArray
            .map { it.jsonObject }

    @Test
    fun `v3 bumps the version and adds exactly one additive, defaulted column to tasks`() {
        val v2 = schema(2)
        val v3 = schema(3)

        assertEquals(2, v2["database"]!!.jsonObject["version"]!!.jsonPrimitive.int)
        assertEquals(3, v3["database"]!!.jsonObject["version"]!!.jsonPrimitive.int)

        val v2TaskColumns = entityFields(v2, "tasks").map { it["columnName"]!!.jsonPrimitive.content }.toSet()
        val v3TaskFields = entityFields(v3, "tasks")
        val v3TaskColumns = v3TaskFields.map { it["columnName"]!!.jsonPrimitive.content }.toSet()

        // Additive: every v2 column is still present, plus exactly one new one.
        assertTrue("v2 tasks columns must all survive into v3", v2TaskColumns.all { it in v3TaskColumns })
        assertEquals(setOf("alarm_mode"), v3TaskColumns - v2TaskColumns)

        val alarmMode = v3TaskFields.first { it["columnName"]!!.jsonPrimitive.content == "alarm_mode" }
        assertEquals("INTEGER", alarmMode["affinity"]!!.jsonPrimitive.content)
        assertEquals(true, alarmMode["notNull"]!!.jsonPrimitive.boolean)
        assertEquals("0", alarmMode["defaultValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun `v3 leaves every other table's columns untouched`() {
        val v2 = schema(2)
        val v3 = schema(3)
        val otherTables = listOf("reminder_rules", "tags", "task_tags", "completion_log", "meter_readings")

        for (table in otherTables) {
            val v2Columns = entityFields(v2, table).map { it["columnName"]!!.jsonPrimitive.content }.toSet()
            val v3Columns = entityFields(v3, table).map { it["columnName"]!!.jsonPrimitive.content }.toSet()
            assertEquals("table '$table' must be unchanged between v2 and v3", v2Columns, v3Columns)
        }
    }
}
