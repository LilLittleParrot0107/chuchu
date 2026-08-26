package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import com.jossephus.chuchu.data.network.DbtopClient
import com.jossephus.chuchu.data.network.SpendingClient
import com.jossephus.chuchu.data.repository.DbtopCacheManager
import com.jossephus.chuchu.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// `label` = ten day du (section band); `tab` = dang ngan cho nut chuyen view
// — 4 tab chia deu man hep thi "Positions"/"Watchlist" dai hon nut, bi "…".
enum class DbtopView(val label: String, val tab: String) {
    POSITIONS("Positions", "POS"),
    WATCHLIST("Watchlist", "WATCH"),
    CHARTS("Chart", "CHART"),
    SPENDING("Spending", "SPEND"),
}

fun normalizeBaseToken(sym: String): String {
    val s = sym.trim().uppercase()
    return when {
        s in listOf("SMON", "SHMON", "GMON", "WMON", "MON") -> "MON"
        s in listOf("WHYPE", "SHYPE", "HYPE") -> "HYPE"
        s in listOf("UBTC", "WBTC", "CBBTC", "TBTC", "BTC") -> "BTC"
        s in listOf("WETH", "STETH", "WSTETH", "RETH", "UETH", "ETH") -> "ETH"
        s in listOf("WS", "S") -> "S"
        s in listOf("WMATIC", "MATIC", "POL") -> "POL"
        s in listOf("WBNB", "BNB") -> "BNB"
        s in listOf("WAVAX", "AVAX") -> "AVAX"
        s in listOf("WFTM", "FTM") -> "FTM"
        else -> s
    }
}

fun isUsdStablecoin(sym: String): Boolean {
    val s = sym.trim().uppercase()
    if (s == "EURM") return false
    if (s in listOf("USDC", "USDT", "USDT0", "AUSD", "USDM", "DAI", "USD+", "USDE", "FDUSD", "PYUSD", "CRVUSD", "FRAX", "LUSD", "TUSD", "BUSD", "GUSD", "USDD", "USDY")) {
        return true
    }
    if (s.startsWith("USD") || s.endsWith("USD") || s.contains("USDT") || s.contains("USDC")) {
        return true
    }
    return false
}

data class WatchlistTokenItem(
    val symbol: String,
    val price: Double,
    val totalUsd: Double,
    val changePct24h: Double? = null,
)

private data class TokenHoldingAccum(
    val amount: Double,
    val usd: Double,
    val price: Double,
    val proto: String,
)

fun DbtopState.buildWatchlist(px24: Map<String, Double> = emptyMap()): List<WatchlistTokenItem> {
    val map = mutableMapOf<String, MutableList<TokenHoldingAccum>>()

    fun add(sym: String, amt: Double, usd: Double, px: Double, proto: String) {
        val raw = sym.trim()
        if (raw.isBlank()) return
        val base = normalizeBaseToken(raw)
        if (isUsdStablecoin(base)) return
        val list = map.getOrPut(base) { mutableListOf() }
        list.add(TokenHoldingAccum(amt, usd, px, proto))
    }

    for (row in rows) {
        val d = row.detail
        d?.collateral?.forEach { add(it.sym, it.amt, it.usd, it.px, row.proto) }
        d?.supply?.forEach { add(it.sym, it.amt, it.usd, it.px, row.proto) }
        d?.borrow?.forEach { add(it.sym, it.amt, it.usd, it.px, row.proto) }
        d?.reward?.forEach { add(it.sym, it.amt, it.usd, it.px, row.proto) }
        d?.option?.underlying?.let { add(it.sym, it.amt, it.amt * it.px, it.px, row.proto) }
    }

    for (wt in walletTokens) {
        add(wt.sym, wt.amt, wt.usd, wt.px, "Wallet")
    }

    for ((sym, p) in px) {
        val base = normalizeBaseToken(sym)
        if (isUsdStablecoin(base)) continue
        if (!map.containsKey(base)) {
            map[base] = mutableListOf()
        }
    }

    return map.mapNotNull { (baseSym, holdings) ->
        val totalUsd = holdings.sumOf { it.usd }
        // Luật dbtop: chỉ show những token có vị thế nhiều hơn 100 USD
        if (totalUsd < 100.0) return@mapNotNull null

        // Giá của token gốc (LST -> giá token gốc: MON, HYPE, BTC, ETH...)
        val currentPx = px[baseSym]
            ?: px[baseSym.lowercase()]
            ?: px[baseSym.uppercase()]
            ?: holdings.firstOrNull { it.price > 0.0 }?.price
            ?: 0.0

        if (currentPx <= 0.0) return@mapNotNull null

        // px24 co the mang ky hieu wrap (UBTC, WMON) thay vi symbol chuan —
        // thu exact roi W-/U- prefix (cung gia voi goc); KHONG lay s*/sh*
        // (LST gia khac han).
        val prev = px24[baseSym] ?: px24["W" + baseSym] ?: px24["U" + baseSym]
        WatchlistTokenItem(
            symbol = baseSym,
            price = currentPx,
            totalUsd = totalUsd,
            changePct24h = prev?.takeIf { it > 0 }?.let { (currentPx - it) / it * 100.0 },
        )
    }.sortedWith(
        compareByDescending<WatchlistTokenItem> { it.totalUsd }
            .thenByDescending { it.price }
            .thenBy { it.symbol }
    )
}

internal const val DBTOP_HIGH_RISK_HEALTH_FACTOR = 1.25

/** Names are not unique across protocols, so selection needs a composite key. */
internal fun DappRow.positionKey(): String =
    // Them hash gia tri de hai vi the GIONG HET nhau (cung proto/name/src —
    // deban tra ve the) khong sinh key trung trong LazyColumn: truoc day
    // crash "Key was already used".
    "$proto\u0000$name\u0000$src\u0000${cap.hashCode() * 31 + perday.hashCode()}"

/**
 * Trạng thái UI toàn diện của Dashboard dbtop.
 */
data class DbtopUiState(
    val state: DbtopState = DbtopState(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val everLoaded: Boolean = false,
    val freshness: DataFreshness = DataFreshness.Fresh(0L),
    val selectedView: DbtopView = DbtopView.POSITIONS,
    val selectedPositionKey: String? = null,
    val spending: SpendingState? = null,
    val moneyDisplay: MoneyDisplay = MoneyDisplay.USD,
) {
    /**
     * Vị thế có rủi ro cao nhất cần cảnh báo (Health Factor < 1.25x hoặc Option đáo hạn trong 4h).
     */
    val criticalLendingRow: DappRow?
        get() = state.rows
            .filter { it.health != null && it.health > 0.0 && it.health < DBTOP_HIGH_RISK_HEALTH_FACTOR }
            .minByOrNull { it.health ?: Double.MAX_VALUE }

    /**
     * dbtop tính tốc độ từ các row chưa đáo hạn và ẩn hẳn khi snapshot chết.
     * Không dùng thẳng state.perday: option có thể đáo hạn giữa hai lần scan.
     */
    fun currentPerDay(nowSec: Long = System.currentTimeMillis() / 1_000L): Double? {
        if (!everLoaded || freshness is DataFreshness.Dead) return null
        return state.rows.sumOf { row ->
            if (row.expiry != null && row.expiry <= nowSec) 0.0 else row.perday
        }
    }
}

/**
 * ViewModel quản lý dữ liệu và vòng đời polling của dbtop Dashboard.
 */
class DbtopViewModel(
    private val settings: SettingsRepository,
    private val cacheManager: DbtopCacheManager,
) : ViewModel() {

    private data class ClientConfig(
        val endpoint: String,
    )

    private val _ui = MutableStateFlow(DbtopUiState())
    val ui: StateFlow<DbtopUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private var client: DbtopClient? = null
    private var clientConfig: ClientConfig? = null
    private var spendingClient: SpendingClient? = null
    private var spendingClientUrl: String? = null
    private val refreshMutex = Mutex()

    init {
        _ui.update { it.copy(moneyDisplay = MoneyDisplay.fromId(settings.dbtopMoneyDisplay)) }
        // 1. Khởi nạp Offline Cache tức thì (0ms Instant Load)
        loadCachedSnapshot()
        // 2. Chạy ngầm tải bản mới nhất từ server
        refreshNow()
    }

    private fun getOrCreateClient(): DbtopClient {
        val config = ClientConfig(
            endpoint = settings.resolvedDbtopUrl,
        )
        val existing = client
        if (existing != null && config == clientConfig) return existing

        return DbtopClient(
            endpointUrl = config.endpoint,
        ).also {
            client = it
            clientConfig = config
        }
    }

    /**
     * Nạp snapshot đã lưu từ disk để hiển thị ngay tức khắc.
     */
    private fun loadCachedSnapshot() {
        val cachedState = cacheManager.loadSnapshot()
        if (cachedState != null && cachedState.netWorth > 0) {
            val freshness = cachedState.freshness()
            _ui.update {
                it.copy(
                    state = cachedState,
                    everLoaded = true,
                    freshness = freshness,
                )
            }
        }
    }

    // =========================================================================
    // LIFECYCLE-AWARE ADAPTIVE POLLING (20s ACTIVE, 0s BACKGROUND)
    // =========================================================================

    /**
     * Bắt đầu Polling khi màn hình Active (LifecycleResumeEffect).
     * Chu kỳ chuẩn: 20s, tự động backoff khi gặp lỗi mạng (20s -> 40s -> 60s).
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var currentInterval = ACTIVE_POLL_INTERVAL_MS
            while (isActive) {
                val hasError = refreshOnce(isBackgroundPoll = true)
                currentInterval = if (hasError) {
                    minOf(currentInterval * 2, MAX_BACKOFF_INTERVAL_MS)
                } else {
                    ACTIVE_POLL_INTERVAL_MS
                }
                delay(currentInterval)
            }
        }
    }

    /**
     * Dừng Polling ngay lập tức khi màn hình bị che khuất / app vào background.
     */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Thực hiện một lần fetch dữ liệu.
     */
    private suspend fun refreshOnce(isBackgroundPoll: Boolean = false): Boolean = refreshMutex.withLock {
        val httpClient = getOrCreateClient()

        if (!isBackgroundPoll) {
            _ui.update { it.copy(isRefreshing = true) }
        }

        // spending.json doc lap voi state.json — keo kem moi vong poll qua
        // SpendingClient (ETag/304); hong hay 304 thi giu ban cu, khong lam
        // do ca man dashboard.
        val spendingResult = withContext(Dispatchers.IO) { getOrCreateSpendingClient().fetch() }
        if (spendingResult is SpendingClient.FetchResult.Fresh) {
            _ui.update { it.copy(spending = spendingResult.state) }
        }

        when (val result = withContext(Dispatchers.IO) { httpClient.fetch(forceRefresh = !isBackgroundPoll) }) {
            is DbtopClient.FetchResult.Fresh -> {
                withContext(Dispatchers.IO) {
                    // Chi ghi khi payload doi — truoc day ghi de XML moi 20s poll.
                    if (cacheManager.lastRaw() != result.rawJson) {
                        cacheManager.saveSnapshot(result.rawJson)
                    }
                }
                _ui.update {
                    it.copy(
                        state = result.state,
                        isRefreshing = false,
                        everLoaded = true,
                        freshness = result.freshness,
                        error = null,
                    )
                }
                false
            }
            is DbtopClient.FetchResult.Unchanged -> {
                // 304 means the file is unchanged, not that its financial data
                // is fresh. dbtop treats state.ts as the only source of truth.
                val currentFreshness = _ui.value.state.freshness()
                _ui.update {
                    it.copy(
                        isRefreshing = false,
                        freshness = currentFreshness,
                        error = null,
                    )
                }
                false
            }
            is DbtopClient.FetchResult.Failed -> {
                _ui.update {
                    it.copy(
                        isRefreshing = false,
                        error = result.message,
                    )
                }
                true
            }
        }
    }

    private fun getOrCreateSpendingClient(): SpendingClient {
        val url = settings.resolvedSpendingUrl
        val existing = spendingClient
        if (existing != null && url == spendingClientUrl) return existing
        return SpendingClient(url).also {
            spendingClient = it
            spendingClientUrl = url
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshOnce(isBackgroundPoll = false)
        }
    }

    fun cycleMoneyDisplay() {
        _ui.update { it.copy(moneyDisplay = it.moneyDisplay.next()) }
        settings.dbtopMoneyDisplay = _ui.value.moneyDisplay.name
    }

    fun selectView(view: DbtopView) {
        _ui.update {
            it.copy(
                selectedView = view,
                selectedPositionKey = it.selectedPositionKey.takeIf { view == DbtopView.POSITIONS },
            )
        }
    }

    fun togglePosition(key: String) {
        _ui.update {
            it.copy(selectedPositionKey = if (it.selectedPositionKey == key) null else key)
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        private const val ACTIVE_POLL_INTERVAL_MS = 20_000L
        private const val MAX_BACKOFF_INTERVAL_MS = 60_000L
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DbtopViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return DbtopViewModel(
                            settings = SettingsRepository.getInstance(application),
                            cacheManager = DbtopCacheManager(application),
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
