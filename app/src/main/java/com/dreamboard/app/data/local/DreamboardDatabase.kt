package com.dreamboard.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, ImportBatchEntity::class, TransactionEntity::class,
        TransactionSplitEntity::class, AssetEntity::class, AssetValuationEntity::class, LiabilityEntity::class,
        LiabilityPaymentEntity::class, BalanceAdjustmentEntity::class, SnapshotEntity::class, GoalEntity::class,
        MilestoneEntity::class,
        ArrangementEntity::class, VehicleEntity::class, VehicleExpenseEntity::class, IgniteSettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class DreamboardDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun assets(): AssetDao
    abstract fun liabilities(): LiabilityDao
    abstract fun snapshots(): SnapshotDao
    abstract fun plan(): PlanDao
    abstract fun ignite(): IgniteDao

    companion object {
        const val NAME = "dreamboard.db"

        /**
         * Every future schema change adds a Migration here (or an AutoMigration on @Database).
         * Destructive fallback is never enabled: financial records must survive upgrades.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
