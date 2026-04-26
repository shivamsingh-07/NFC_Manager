package com.nfcmanager.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NfcActionEntity::class, SavedMessageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class NfcDatabase : RoomDatabase() {
    abstract fun nfcActionDao(): NfcActionDao
    abstract fun savedMessageDao(): SavedMessageDao

    companion object {
        const val NAME = "nfc_manager.db"

        /**
         * v1 -> v2: introduce `nfc_actions`. The unique index on `uidHash`
         * enforces the "one action per tag" rule at the database level.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nfc_actions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uidHash` TEXT NOT NULL,
                        `techSignature` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `typeName` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `requireConfirmation` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_nfc_actions_uidHash` " +
                        "ON `nfc_actions` (`uidHash`)",
                )
            }
        }

        /** v2 -> v3: scan history feature removed; drop the `scan_records` table. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `scan_records`")
            }
        }

        /** v3 -> v4: Add saved_messages table */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
