package com.dreamboard.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE isArchived = 0 ORDER BY name") fun observe(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account WHERE id = :id") suspend fun get(id: String): AccountEntity?
    @Upsert suspend fun upsert(a: AccountEntity)
    @Query("DELETE FROM account WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM account") suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE isArchived = 0 ORDER BY sortOrder, name") fun observe(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category") suspend fun all(): List<CategoryEntity>
    @Upsert suspend fun upsert(c: CategoryEntity)
    @Upsert suspend fun upsertAll(c: List<CategoryEntity>)
    @Query("SELECT COUNT(*) FROM category") suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM txn ORDER BY date DESC, createdAt DESC") fun observe(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM txn_split") fun observeSplits(): Flow<List<TransactionSplitEntity>>
    @Upsert suspend fun upsert(t: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(t: List<TransactionEntity>)
    @Query("DELETE FROM txn WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM txn_split WHERE transactionId = :txId") suspend fun deleteSplits(txId: String)
    @Insert suspend fun insertSplits(s: List<TransactionSplitEntity>)
    @Query("SELECT COUNT(*) FROM txn WHERE accountId = :accountId") suspend fun countForAccount(accountId: String): Int
    @Query("SELECT dedupeHash FROM txn WHERE dedupeHash IN (:hashes)") suspend fun hashesIn(hashes: List<String>): List<String>
    @Insert suspend fun insertBatch(b: ImportBatchEntity)
    @Query("SELECT * FROM import_batch ORDER BY importedAt DESC") fun observeBatches(): Flow<List<ImportBatchEntity>>
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM asset ORDER BY category, name") fun observe(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM asset") suspend fun all(): List<AssetEntity>
    @Query("SELECT * FROM asset WHERE id = :id") suspend fun get(id: String): AssetEntity?
    @Upsert suspend fun upsert(a: AssetEntity)
    @Query("DELETE FROM asset WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun insertValuation(v: AssetValuationEntity)
    @Query("SELECT * FROM asset_valuation WHERE assetId = :id ORDER BY date DESC, createdAt DESC") suspend fun valuations(id: String): List<AssetValuationEntity>
}

@Dao
interface LiabilityDao {
    @Query("SELECT * FROM liability ORDER BY customPriority, name") fun observe(): Flow<List<LiabilityEntity>>
    @Query("SELECT * FROM liability") suspend fun all(): List<LiabilityEntity>
    @Query("SELECT * FROM liability WHERE id = :id") suspend fun get(id: String): LiabilityEntity?
    @Upsert suspend fun upsert(l: LiabilityEntity)
    @Query("DELETE FROM liability WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM liability_payment ORDER BY date DESC, createdAt DESC") fun observePayments(): Flow<List<LiabilityPaymentEntity>>
    @Insert suspend fun insertPayment(p: LiabilityPaymentEntity)
    @Insert suspend fun insertAdjustment(a: BalanceAdjustmentEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM net_worth_snapshot ORDER BY date") fun observe(): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM net_worth_snapshot ORDER BY date DESC LIMIT 1") suspend fun latest(): SnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: SnapshotEntity)
    @Query("DELETE FROM net_worth_snapshot WHERE date = :date") suspend fun deleteOn(date: LocalDate)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM goal WHERE id = :id") fun observeGoal(id: String): Flow<GoalEntity?>
    @Upsert suspend fun upsertGoal(g: GoalEntity)
    @Query("SELECT COUNT(*) FROM goal") suspend fun goalCount(): Int
    @Query("SELECT * FROM milestone ORDER BY year") fun observeMilestones(): Flow<List<MilestoneEntity>>
    @Upsert suspend fun upsertMilestones(m: List<MilestoneEntity>)
    @Query("SELECT COUNT(*) FROM milestone") suspend fun milestoneCount(): Int
}

@Dao
interface IgniteDao {
    @Query("SELECT * FROM funding_arrangement ORDER BY name") fun observeArrangements(): Flow<List<ArrangementEntity>>
    @Query("SELECT * FROM funding_arrangement") suspend fun arrangements(): List<ArrangementEntity>
    @Upsert suspend fun upsertArrangement(a: ArrangementEntity)
    @Upsert suspend fun upsertArrangements(a: List<ArrangementEntity>)
    @Query("SELECT COUNT(*) FROM vehicle WHERE arrangementId = :id") suspend fun vehiclesUsing(id: String): Int
    @Query("DELETE FROM funding_arrangement WHERE id = :id") suspend fun deleteArrangement(id: String)

    @Query("SELECT * FROM vehicle ORDER BY purchaseDate DESC, createdAt DESC") fun observeVehicles(): Flow<List<VehicleEntity>>
    @Query("SELECT * FROM vehicle_expense") fun observeExpenses(): Flow<List<VehicleExpenseEntity>>
    @Query("SELECT * FROM vehicle WHERE id = :id") suspend fun vehicle(id: String): VehicleEntity?
    @Query("SELECT * FROM vehicle WHERE importKey IN (:keys)") suspend fun vehiclesByKey(keys: List<String>): List<VehicleEntity>
    @Upsert suspend fun upsertVehicle(v: VehicleEntity)
    @Query("DELETE FROM vehicle WHERE id = :id") suspend fun deleteVehicle(id: String)
    @Query("DELETE FROM vehicle_expense WHERE vehicleId = :vehicleId") suspend fun deleteExpenses(vehicleId: String)
    @Insert suspend fun insertExpenses(e: List<VehicleExpenseEntity>)

    @Query("SELECT * FROM ignite_settings WHERE id = 1") fun observeSettings(): Flow<IgniteSettingsEntity?>
    @Upsert suspend fun upsertSettings(s: IgniteSettingsEntity)
}
