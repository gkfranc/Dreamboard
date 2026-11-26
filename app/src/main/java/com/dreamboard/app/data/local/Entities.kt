package com.dreamboard.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

// All amounts are INTEGER minor units (cents). Rates and shares are INTEGER basis points.
// Enums are stored by name. Dates are stored as epoch days via Converters.

@Entity(tableName = "account", indices = [Index("name")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val institution: String,
    val openingBalanceMinor: Long,
    val openingDate: LocalDate,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "category",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("parentId"), Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val monthlyBudgetMinor: Long,
    val sortOrder: Int,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val format: String,
    val importedAt: Long,
    val rowCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val errorReport: String,
)

@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["importBatchId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["accountId", "date"]), Index("categoryId"), Index("importBatchId"), Index("dedupeHash"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val date: LocalDate,
    /** Always positive; direction comes from [type]. */
    val amountMinor: Long,
    val type: String,
    val categoryId: String?,
    val merchant: String,
    val note: String,
    val paidByCreditCard: Boolean,
    val source: String,
    val importBatchId: String?,
    /** Fingerprint of account, date, signed amount and description, for duplicate detection. */
    val dedupeHash: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "txn_split",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("transactionId"), Index("categoryId")],
)
data class TransactionSplitEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val categoryId: String,
    val amountMinor: Long,
)

@Entity(tableName = "asset", indices = [Index("category")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val institution: String,
    val valueMinor: Long,
    val valueType: String,
    val isGoalFund: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "asset_valuation",
    foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["assetId", "date"])],
)
data class AssetValuationEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val date: LocalDate,
    val valueMinor: Long,
    val valueType: String,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "liability", indices = [Index("category")])
data class LiabilityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lender: String,
    val category: String,
    val kind: String,
    val balanceMinor: Long?,
    val originalPrincipalMinor: Long?,
    val annualRateBps: Int,
    val monthlyPaymentMinor: Long,
    val dueDayOfMonth: Int?,
    val interestPaidToDateMinor: Long?,
    val customPriority: Int,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "liability_payment",
    foreignKeys = [ForeignKey(entity = LiabilityEntity::class, parentColumns = ["id"], childColumns = ["liabilityId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["liabilityId", "date"])],
)
data class LiabilityPaymentEntity(
    @PrimaryKey val id: String,
    val liabilityId: String,
    val date: LocalDate,
    val amountMinor: Long,
    val principalMinor: Long,
    val interestMinor: Long,
    val note: String,
    val createdAt: Long,
)

/** Audit trail for every manual change to a balance or value. */
@Entity(tableName = "balance_adjustment", indices = [Index(value = ["refType", "refId"])])
data class BalanceAdjustmentEntity(
    @PrimaryKey val id: String,
    val refType: String,
    val refId: String,
    val date: LocalDate,
    val previousMinor: Long?,
    val newMinor: Long?,
    val reason: String,
    val createdAt: Long,
)

@Entity(tableName = "net_worth_snapshot", indices = [Index(value = ["date"], unique = true)])
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val assetsMinor: Long,
    val liabilitiesMinor: Long,
    val method: String,
    val createdAt: Long,
)

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: String,
    val targetYear: Int,
    val targetMonth: Int,
    val todayPriceMinor: Long,
    val monthlyContributionMinor: Long,
    val annualReturnBps: Int,
    val annualInflationBps: Int,
    val insuranceRateBps: Int,
    val annualMaintenanceMinor: Long,
    val annualFuelMinor: Long,
    val updatedAt: Long,
)

@Entity(tableName = "milestone", indices = [Index("year")])
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val title: String,
    val description: String,
    val status: String,
    val updatedAt: Long,
)

// ---- Ignite Motors (schema version 2) ----

/** Profit-sharing rule. Shares are basis points of net deal profit and sum to 10,000. */
@Entity(tableName = "funding_arrangement")
data class ArrangementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val investorShareBps: Long,
    val partnerShareBps: Long,
    val personalShareBps: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "vehicle",
    foreignKeys = [ForeignKey(entity = ArrangementEntity::class, parentColumns = ["id"], childColumns = ["arrangementId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("arrangementId"), Index("importKey"), Index("status")],
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val registration: String,
    val make: String,
    val model: String,
    val year: Int,
    val purchaseDate: LocalDate?,
    val purchasePriceMinor: Long,
    val arrangementId: String,
    val condition: String,
    val listingPriceMinor: Long?,
    val expectedPriceMinor: Long?,
    val actualPriceMinor: Long?,
    val saleDate: LocalDate?,
    val status: String,
    val notes: String,
    /** Normalised "car name|date bought" so a re-imported tracker updates the same car. */
    val importKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "vehicle_expense",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class VehicleExpenseEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val kind: String,
    val amountMinor: Long,
    val note: String,
)

/** Single row (id = 1): monthly Ignite targets. Zero means no target set. */
@Entity(tableName = "ignite_settings")
data class IgniteSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val targetNetProfitMinor: Long,
    val targetUnits: Int,
)
