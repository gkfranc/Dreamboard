package com.dreamboard.app.feature.ignite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamboard.app.core.finance.IgniteMath
import com.dreamboard.app.core.finance.IgniteSummary
import com.dreamboard.app.core.finance.ProfitSplit
import com.dreamboard.app.domain.model.FundingArrangement
import com.dreamboard.app.domain.model.IgniteMonth
import com.dreamboard.app.domain.model.Vehicle
import com.dreamboard.app.domain.model.VehicleStatus
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

enum class InventoryFilter(val label: String) { IN_STOCK("In stock"), SOLD("Sold"), PIPELINE("Pipeline"), ALL("All") }

data class PoolPerformance(
    val arrangement: FundingArrangement,
    val deals: Int,
    val split: ProfitSplit,
    val capitalInStockMinor: Long,
)

data class IgniteUiState(
    val isDemo: Boolean,
    val asOf: LocalDate,
    val summary: IgniteSummary,
    val availableCashMinor: Long,
    val projectedStockProfitMinor: Long,
    val inventoryTurnoverX10: Long,
    val months: List<IgniteMonth>,
    val filter: InventoryFilter,
    val vehicles: List<Vehicle>,
    val ranking: List<Pair<Vehicle, Long>>,
    val pools: List<PoolPerformance>,
    val byModel: List<Pair<String, Long>>,
    val arrangements: Map<String, FundingArrangement>,
)

@HiltViewModel
class IgniteViewModel @Inject constructor(private val repo: FinanceRepository) : ViewModel() {
    private val filter = MutableStateFlow(InventoryFilter.IN_STOCK)

    val uiState: StateFlow<IgniteUiState> = combine(
        repo.vehicles, repo.arrangements, repo.igniteMonths, repo.igniteAvailableCashMinor, filter,
    ) { v, a, m, cash, f -> build(v, a, m, cash, f) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            build(repo.vehicles.value, repo.arrangements.value, repo.igniteMonths.value, repo.igniteAvailableCashMinor.value, filter.value),
        )

    fun setFilter(f: InventoryFilter) = filter.update { f }

    private fun build(
        vehicles: List<Vehicle>, arr: List<FundingArrangement>, months: List<IgniteMonth>, cash: Long, f: InventoryFilter,
    ): IgniteUiState {
        val asOf = repo.asOf
        val byId = arr.associateBy { it.id }
        val summary = IgniteMath.summarize(vehicles, byId, asOf)
        val sold = vehicles.filter { it.status == VehicleStatus.SOLD }
        val stock = vehicles.filter { it.status.inStock }
        val soldLast6 = sold.count { it.saleDate != null && !it.saleDate.isBefore(asOf.minusMonths(6)) }
        return IgniteUiState(
            isDemo = repo.isDemoData,
            asOf = asOf,
            summary = summary,
            availableCashMinor = cash,
            projectedStockProfitMinor = stock.sumOf { IgniteMath.projectedDealProfit(it) ?: 0L },
            inventoryTurnoverX10 = if (stock.isEmpty()) 0 else soldLast6 * 10L / stock.size,
            months = months,
            filter = f,
            vehicles = vehicles.filter {
                when (f) {
                    InventoryFilter.IN_STOCK -> it.status.inStock
                    InventoryFilter.SOLD -> it.status == VehicleStatus.SOLD
                    InventoryFilter.PIPELINE -> it.status == VehicleStatus.UNDER_CONSIDERATION
                    InventoryFilter.ALL -> true
                }
            },
            ranking = sold.mapNotNull { v -> IgniteMath.netDealProfit(v)?.let { v to it } }.sortedByDescending { it.second },
            pools = arr.map { a ->
                val deals = sold.filter { it.arrangementId == a.id }
                val profit = deals.sumOf { IgniteMath.netDealProfit(it) ?: 0L }
                PoolPerformance(a, deals.size, IgniteMath.split(profit, a), stock.filter { it.arrangementId == a.id }.sumOf { it.totalCostMinor })
            },
            byModel = sold.groupBy { "${it.make} ${it.model}" }
                .map { (k, l) -> k to l.sumOf { IgniteMath.netDealProfit(it) ?: 0L } }
                .sortedByDescending { it.second },
            arrangements = byId,
        )
    }
}

data class VehicleDetailUiState(
    val vehicle: Vehicle?,
    val arrangement: FundingArrangement?,
    val asOf: LocalDate,
) {
    val realizedProfit: Long? get() = vehicle?.let { IgniteMath.netDealProfit(it) }
    val projectedProfit: Long? get() = vehicle?.let { IgniteMath.projectedDealProfit(it) }
    val split: ProfitSplit? get() {
        val a = arrangement ?: return null
        val p = realizedProfit ?: projectedProfit ?: return null
        return IgniteMath.split(p, a)
    }
    val daysInStock: Int? get() = vehicle?.let { IgniteMath.daysInStock(it, asOf) }
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FinanceRepository,
) : ViewModel() {
    private val vehicleId: String = checkNotNull(savedStateHandle["vehicleId"])

    val uiState: StateFlow<VehicleDetailUiState> = combine(repo.vehicles, repo.arrangements) { v, a -> build(v, a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.vehicles.value, repo.arrangements.value))

    private fun build(vehicles: List<Vehicle>, arr: List<FundingArrangement>): VehicleDetailUiState {
        val v = vehicles.firstOrNull { it.id == vehicleId }
        return VehicleDetailUiState(v, v?.let { x -> arr.firstOrNull { it.id == x.arrangementId } }, repo.asOf)
    }
}
