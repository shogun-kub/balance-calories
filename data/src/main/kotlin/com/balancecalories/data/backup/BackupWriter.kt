package com.balancecalories.data.backup

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises the database into the backup JSON.
 *
 * Reading happens through raw cursors rather than DAOs on purpose: the backup must capture every column, including
 * ones no repository currently reads, and a DAO built for the UI would silently drop them. A column added later is
 * carried automatically, which is the whole point of a table-shaped format.
 */
object BackupWriter {

    /**
     * @param appVersion the `versionName` of the app doing the writing, so a support question can start with it.
     */
    fun write(
        db: SupportSQLiteDatabase,
        appVersion: String,
        schemaVersion: Int,
        exportedAt: Long,
    ): String {
        val root = JSONObject()
        root.put("format", BackupTables.FORMAT_ID)
        root.put("formatVersion", BackupTables.FORMAT_VERSION)
        root.put("schemaVersion", schemaVersion)
        root.put("appVersion", appVersion)
        root.put("exportedAt", exportedAt)

        val tables = JSONObject()
        BackupTables.TABLES.forEach { table ->
            tables.put(table.name, readTable(db, table.select))
        }
        root.put("tables", tables)
        return root.toString(2)
    }

    private fun readTable(db: SupportSQLiteDatabase, sql: String): JSONArray {
        val rows = JSONArray()
        db.query(sql).use { cursor ->
            while (cursor.moveToNext()) {
                rows.put(readRow(cursor))
            }
        }
        return rows
    }

    private fun readRow(cursor: Cursor): JSONObject {
        val row = JSONObject()
        for (index in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(index)
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> row.put(name, cursor.getString(index))
                // No table in this schema stores a blob; if one ever does, an explicit failure is better than
                // silently writing a toString() of the array.
                else -> error("backup does not support blob column '$name'")
            }
        }
        return row
    }
}
