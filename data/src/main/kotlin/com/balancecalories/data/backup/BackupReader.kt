package com.balancecalories.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a restore attempt produced. Silence is not one of the options: each case is reported to the person. */
sealed interface ImportOutcome {

    data class Restored(
        val rows: Int,
        val fromSchemaVersion: Int,
        val fromFormatVersion: Int,
        val exportedAt: Long,
        /** Columns in the file that this build of the app no longer has. Reported, not fatal. */
        val droppedColumns: Set<String>,

        /**
         * Whether the file carried the bundled starter ingredients.
         *
         * It matters because the importer must not add a second copy of them afterwards. A backup made before the
         * product table travelled whole does not have them, so the caller has to look rather than assume.
         */
        val containsStarterProducts: Boolean = false,
    ) : ImportOutcome

    /** A real backup, but one this build must not apply. */
    /**
     * A real backup, but one this build must not apply.
     *
     * [detail] carries the underlying failure when there was one. Without it, "damaged" was a dead end: the first
     * real restore refused its own file and the reason (a foreign key pointing at a product the export had
     * skipped) was invisible.
     */
    data class Refused(val reason: Refusal, val detail: String? = null) : ImportOutcome

    enum class Refusal {
        /** Not a BalanceCalories backup at all. */
        NOT_A_BACKUP,

        /** Written by a newer *file format*; its shape may contain things this reader would drop. */
        NEWER_FORMAT,

        /** Written by a newer database schema. Guessing at unknown columns is how restores lose data. */
        NEWER_SCHEMA,

        /** Valid header, unreadable or contradictory body. */
        DAMAGED,
    }
}

/**
 * Restores a backup over the current data.
 *
 * A restore **replaces**, it does not merge. Merging personal records by guessed keys is how a restore quietly
 * duplicates a year of meals or attributes one person's weight to another; replacing is what "restore from backup"
 * means, and the screen asks before doing it. It keeps the rows' original ids, so every foreign key inside the file
 * still points at the same row afterwards.
 *
 * Foreign keys stay **on**. The tables are inserted parents-first and deleted children-first, which satisfies the
 * constraints honestly; turning them off would also hide a file that genuinely disagrees with itself.
 */
object BackupReader {

    fun restore(db: SupportSQLiteDatabase, json: String, currentSchemaVersion: Int): ImportOutcome {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return ImportOutcome.Refused(ImportOutcome.Refusal.NOT_A_BACKUP)
        }

        if (root.optString("format") != BackupTables.FORMAT_ID) {
            return ImportOutcome.Refused(ImportOutcome.Refusal.NOT_A_BACKUP)
        }
        val formatVersion = root.optInt("formatVersion", -1)
        if (formatVersion !in 1..BackupTables.FORMAT_VERSION) {
            return ImportOutcome.Refused(ImportOutcome.Refusal.NEWER_FORMAT)
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion !in 1..currentSchemaVersion) {
            return ImportOutcome.Refused(ImportOutcome.Refusal.NEWER_SCHEMA)
        }
        val tables = root.optJSONObject("tables")
            ?: return ImportOutcome.Refused(ImportOutcome.Refusal.DAMAGED)
        if (BackupTables.TABLES.any { tables.optJSONArray(it.name) == null }) {
            // Every table is written by this format, so a missing one means a truncated or edited file. Restoring
            // the part that happens to parse would leave a database that is neither the backup nor the old data.
            return ImportOutcome.Refused(ImportOutcome.Refusal.DAMAGED)
        }

        val droppedColumns = mutableSetOf<String>()
        var rows = 0
        db.beginTransaction()
        try {
            // Children first, so no delete hits a foreign key that the file still needs.
            BackupTables.TABLES.asReversed().forEach { table ->
                db.execSQL("DELETE FROM \"${table.name}\"")
            }
            BackupTables.TABLES.forEach { table ->
                rows += insertTable(db, table.name, tables.getJSONArray(table.name), droppedColumns)
            }
            // The product index is external-content FTS4: rows written straight into `product` are invisible to
            // search until it is rebuilt, and nothing would fail — the user would just stop finding products.
            db.execSQL("INSERT INTO product_fts(product_fts) VALUES('rebuild')")
            db.setTransactionSuccessful()
        } catch (e: JSONException) {
            // A truncated or hand-edited body: the transaction is rolled back by endTransaction below.
            return ImportOutcome.Refused(ImportOutcome.Refusal.DAMAGED, e.message)
        } catch (e: Exception) {
            // A constraint failure means the file disagrees with itself (a food entry whose product is absent,
            // for example). That is a damaged backup, and it must not leave a half-restored database behind.
            return ImportOutcome.Refused(ImportOutcome.Refusal.DAMAGED, e.message ?: e::class.java.simpleName)
        } finally {
            db.endTransaction()
        }

        val starterProducts = (0 until tables.getJSONArray("product").length()).any { index ->
            tables.getJSONArray("product").getJSONObject(index).optString("source") == "starter"
        }

        return ImportOutcome.Restored(
            rows = rows,
            fromSchemaVersion = schemaVersion,
            fromFormatVersion = formatVersion,
            exportedAt = root.optLong("exportedAt", 0L),
            droppedColumns = droppedColumns,
            containsStarterProducts = starterProducts,
        )
    }

    private fun insertTable(
        db: SupportSQLiteDatabase,
        table: String,
        rows: JSONArray,
        droppedColumns: MutableSet<String>,
    ): Int {
        if (rows.length() == 0) return 0
        val present = currentColumns(db, table)

        var inserted = 0
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val keys = row.keys().asSequence().toList()
            keys.filterNot { it in present }.forEach { droppedColumns.add("$table.$it") }
            val columns = keys.filter { it in present }
            require(columns.isNotEmpty()) { "backup row for '$table' has no column this build knows" }

            val sql = "INSERT INTO \"$table\" (${columns.joinToString(", ") { "\"$it\"" }}) " +
                "VALUES (${columns.joinToString(", ") { "?" }})"
            val statement = db.compileStatement(sql)
            try {
                columns.forEachIndexed { position, column ->
                    val value = row.get(column)
                    when (value) {
                        JSONObject.NULL -> statement.bindNull(position + 1)
                        is Int, is Long -> statement.bindLong(position + 1, (value as Number).toLong())
                        is Double -> statement.bindDouble(position + 1, value)
                        is Float -> statement.bindDouble(position + 1, value.toDouble())
                        is String -> statement.bindString(position + 1, value)
                        else -> error("unsupported JSON value in '$table.$column': ${value::class}")
                    }
                }
                statement.executeInsert()
            } finally {
                statement.close()
            }
            inserted++
        }
        return inserted
    }

    private fun currentColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(\"$table\")").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        return columns
    }
}
