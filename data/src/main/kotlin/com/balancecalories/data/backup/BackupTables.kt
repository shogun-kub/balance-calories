package com.balancecalories.data.backup

/**
 * One table as it appears in a backup: its name, and the query that reads it.
 *
 * The `ORDER BY` is part of the format, not an optimisation: two exports of unchanged data must produce the same
 * bytes, otherwise "is this backup newer than that one" is a question nobody can answer, and a test that compares
 * files fails for reasons that have nothing to do with the data.
 */
internal data class BackupTable(val name: String, val select: String)

/**
 * The backup format's own constants, and which tables travel in it.
 *
 * The format is deliberately **table-shaped rather than typed**: a backup of a personal database is a set of rows,
 * and describing those rows as JSON objects keyed by column name keeps one property that matters more than elegant
 * DTOs — a backup written by an older app version still restores into a newer schema, because a column the reader
 * does not know is simply not written, and a column the reader gains falls back to its default. Message classes
 * would have needed a migration for the *backup* on every schema change, which is a worse problem than an ugly file.
 *
 * What is not in the file, and why:
 *
 * - `product_fts` is derived from `product`; restoring it separately would let the index disagree with the rows.
 * - `pending_lookup` is a retry queue; restoring it would replay forgotten network work on a new phone.
 * - `sync_state` and `app_setting` are device-local: which profile is open, when the band last synced. A backup
 *   that silently changed them would be surprising, and useful for nothing.
 */
internal object BackupTables {

    /** Written into every file, so an unrelated JSON file is rejected by name and not by a parser exception. */
    const val FORMAT_ID = "balancecalories.backup"

    /** Bumped only when the *file shape* changes, independently of the database schema version. */
    const val FORMAT_VERSION = 1

    /**
     * Parents before children.
     *
     * Insert order on restore; delete order is this list reversed, which is what keeps foreign keys happy without
     * switching `PRAGMA foreign_keys` off — a restore that needed them disabled would also be a restore that hides
     * referential mistakes in the file.
     */
    val TABLES = listOf(
        BackupTable("profile", "SELECT * FROM profile ORDER BY id"),
        BackupTable("body_measurement", "SELECT * FROM body_measurement ORDER BY id"),
        BackupTable("target_history", "SELECT * FROM target_history ORDER BY profile_id, effective_from"),
        // Every product, starter pack included.
        //
        // The tempting optimisation — skip the 69 rows the app ships — is wrong, and a restore proved it: a food
        // entry from the starter pack points at a starter product, so the file arrived with a foreign key to a row
        // that was not in it, and the restore (correctly) refused itself. A backup has to be self-consistent, and
        // 69 rows of JSON is a trivial price. Product ids are preserved, so entry links survive too.
        BackupTable("product", "SELECT * FROM product ORDER BY id"),
        BackupTable("recipe", "SELECT * FROM recipe ORDER BY id"),
        BackupTable("recipe_item", "SELECT * FROM recipe_item ORDER BY recipe_id, position"),
        BackupTable("food_entry", "SELECT * FROM food_entry ORDER BY id"),
        BackupTable("daily_activity", "SELECT * FROM daily_activity ORDER BY profile_id, local_date"),
    )
}
