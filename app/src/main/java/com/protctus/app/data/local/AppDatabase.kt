package com.protctus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.protctus.app.data.local.dao.NotificationDao
import com.protctus.app.data.local.dao.UserSafeListDao
import com.protctus.app.data.local.dao.VvipDomainDao
import com.protctus.app.data.local.entity.NotificationEntity
import com.protctus.app.data.local.entity.UserSafeList
import com.protctus.app.data.local.entity.VvipDomain
import com.protctus.app.data.local.entity.MicrolearningCardData
import com.protctus.app.data.local.entity.ThreatTelemetry
import com.protctus.app.data.local.entity.UserStatsEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.protctus.app.domain.model.NotificationType

class Converters {
    @TypeConverter
    fun fromNotificationType(value: NotificationType): String = value.name

    @TypeConverter
    fun toNotificationType(value: String): NotificationType = enumValueOf(value)
}

@Database(
    entities = [RecentSearch::class, ThreatAlert::class, UserSettings::class, UserProfile::class, OtpSecurityState::class, NotificationEntity::class, UserSafeList::class, VvipDomain::class, MicrolearningCardData::class, ThreatTelemetry::class, UserStatsEntity::class, com.protctus.app.data.local.entity.QuestEntity::class, com.protctus.app.data.local.entity.ScannedFileCache::class],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun threatDao(): ThreatDao
    abstract fun settingsDao(): SettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userSafeListDao(): UserSafeListDao
    abstract fun vvipDomainDao(): VvipDomainDao
    abstract fun microlearningCardDao(): com.protctus.app.data.local.dao.MicrolearningCardDao
    abstract fun threatTelemetryDao(): com.protctus.app.data.local.dao.ThreatTelemetryDao
    abstract fun questDao(): com.protctus.app.data.local.dao.QuestDao
    abstract fun scannedFileCacheDao(): com.protctus.app.data.local.dao.ScannedFileCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_safe_list` (`domain` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`domain`))"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vvip_domains` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `microlearning_cards` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '', `title` TEXT NOT NULL, `description` TEXT NOT NULL, `visualUrl` TEXT NOT NULL, `options` TEXT NOT NULL, `correctAnswer` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `threat_telemetry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `domainHashSha256` TEXT NOT NULL, `confidenceScore` REAL NOT NULL, `threatType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isFalsePositive` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `microlearning_cards` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `microlearning_cards` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `microlearning_cards` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `microlearning_cards` ADD COLUMN `isUnlocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_stats` (`uid` TEXT NOT NULL, `total_xp` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `quests` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `is_active` INTEGER NOT NULL DEFAULT 1, `is_completed` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `scanned_file_cache` (`filePath` TEXT NOT NULL, `lastModified` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `isMalicious` INTEGER NOT NULL, `scanTimestamp` INTEGER NOT NULL, PRIMARY KEY(`filePath`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scanned_file_cache` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `scanned_file_cache` ADD COLUMN `sha256Hash` TEXT")
                db.execSQL("ALTER TABLE `scanned_file_cache` ADD COLUMN `verdictDecision` TEXT NOT NULL DEFAULT 'CLEAN'")
                db.execSQL("ALTER TABLE `scanned_file_cache` ADD COLUMN `verdictScore` REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
