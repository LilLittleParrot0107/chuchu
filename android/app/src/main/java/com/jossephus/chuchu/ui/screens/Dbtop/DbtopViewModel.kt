package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import com.jossephus.chuchu.data.network.DbtopClient
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
import kotlinx.coroutines.withContext

/**
 * Bộ lọc danh mục trên Dashboard dbtop.
 */
enum class DbtopFilter(val label: String) {
    ALL("Tất cả"),
    OPTIONS("Options"),
    LENDING("Lending"),
    POOLS("Pools"),
    WALLET("Ví"),
    CHARTS("Biểu đồ")
}

/**
 * Trạng thái UI toàn diện của Dashboard dbtop.
 */
data class DbtopUiState(
    val state: DbtopState = DbtopState(),
    val loading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val everLoaded: Boolean = false,
    val lastUpdatedTime: Long = 0L,
    val freshness: DataFreshness = DataFreshness.Fresh(0L),
    val isStale: Boolean = false,
    val selectedFilter: DbtopFilter = DbtopFilter.ALL,
    val expandedRowName: String? = null,
    val activeTabMode: Int = 0, // 0 = Danh sách vị thế, 1 = Biểu đồ & Analytics
    val toast: String? = null,
) {
    /**
     * Danh sách DApp đã được phân loại theo bộ lọc hiện tại.
     */
    val filteredRows: List<DappRow>
        get() = when (selectedFilter) {
            DbtopFilter.ALL -> state.rows
            DbtopFilter.OPTIONS -> state.rows.filter {
                it.detail?.option != null || it.proto.equals("Rysk", ignoreCase = true) || it.name.startsWith("Call", true) || it.name.startsWith("Put", true)
            }
            DbtopFilter.LENDING -> state.rows.filter {
                it.health != null || it.debt != null || it.coll != null || it.name.contains("Lending", true)
            }
            DbtopFilter.POOLS -> state.rows.filter {
                it.detail?.asPoolBreakdown() != null || it.name.contains("Pool", true) || it.proto.contains("balancer", true) || it.proto.contains("mento", true)
            }
            DbtopFilter.WALLET -> emptyList() // Render Wallet Tokens riêng
            DbtopFilter.CHARTS -> emptyList() // Render Charts riêng
        }

    /**
     * Tính toán số đếm cho từng nhóm Filter.
     */
    val categoryCounts: Map<DbtopFilter, Int>
        get() = mapOf(
            DbtopFilter.ALL to state.rows.size,
            DbtopFilter.OPTIONS to state.rows.count {
                it.detail?.option != null || it.proto.equals("Rysk", ignoreCase = true) || it.name.startsWith("Call", true) || it.name.startsWith("Put", true)
            },
            DbtopFilter.LENDING to state.rows.count {
                it.health != null || it.debt != null || it.coll != null || it.name.contains("Lending", true)
            },
            DbtopFilter.POOLS to state.rows.count {
                it.detail?.asPoolBreakdown() != null || it.name.contains("Pool", true) || it.proto.contains("balancer", true) || it.proto.contains("mento", true)
            },
            DbtopFilter.WALLET to state.walletTokens.size,
            DbtopFilter.CHARTS to (if (state.curve.isNotEmpty() || state.daily.isNotEmpty()) 1 else 0),
        )

    /**
     * Vị thế có rủi ro cao nhất cần cảnh báo (Health Factor < 1.25x hoặc Option đáo hạn trong 4h).
     */
    val criticalLendingRow: DappRow?
        get() = state.rows.find { it.health != null && it.health < 1.25 }
}

/**
 * ViewModel quản lý dữ liệu và vòng đời polling của dbtop Dashboard.
 */
class DbtopViewModel(
    application: Application,
    private val settings: SettingsRepository,
    private val cacheManager: DbtopCacheManager = DbtopCacheManager(application),
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(DbtopUiState())
    val ui: StateFlow<DbtopUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private var client: DbtopClient? = null

    init {
        // 1. Khởi nạp Offline Cache tức thì (0ms Instant Load)
        loadCachedSnapshot()
        // 2. Chạy ngầm tải bản mới nhất từ server
        refreshNow()
    }

    private fun getOrCreateClient(): DbtopClient {
        val endpoint = settings.resolvedDbtopUrl
        val token = settings.queueToken.value
        val existing = client
        if (existing != null) return existing
        return DbtopClient(endpointUrl = endpoint, authToken = token).also { client = it }
    }

    /**
     * Nạp snapshot đã lưu từ disk để hiển thị ngay tức khắc.
     */
    private fun loadCachedSnapshot() {
        val (cachedState, cachedTime) = cacheManager.loadSnapshot()
        if (cachedState != null && cachedState.netWorth > 0) {
            val now = System.currentTimeMillis()
            val isStale = (now - cachedTime) > STALE_THRESHOLD_MS
            _ui.update {
                it.copy(
                    state = cachedState,
                    everLoaded = true,
                    lastUpdatedTime = cachedTime,
                    freshness = cachedState.freshness(),
                    isStale = isStale,
                    loading = false,
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
    private suspend fun refreshOnce(isBackgroundPoll: Boolean = false): Boolean {
        val httpClient = getOrCreateClient()

        if (!isBackgroundPoll) {
            _ui.update { it.copy(loading = !it.everLoaded, isRefreshing = it.everLoaded) }
        }

        return when (val result = withContext(Dispatchers.IO) { httpClient.fetch(forceRefresh = !isBackgroundPoll) }) {
            is DbtopClient.FetchResult.Fresh -> {
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    cacheManager.saveSnapshot(result.rawJson, now)
                }
                _ui.update {
                    it.copy(
                        state = result.state,
                        loading = false,
                        isRefreshing = false,
                        everLoaded = true,
                        lastUpdatedTime = now,
                        freshness = result.freshness,
                        isStale = false,
                        error = null,
                    )
                }
                false
            }
            is DbtopClient.FetchResult.Unchanged -> {
                val now = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        lastUpdatedTime = now,
                        isStale = false,
                        error = null,
                    )
                }
                false
            }
            is DbtopClient.FetchResult.Failed -> {
                _ui.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = result.message,
                        isStale = it.everLoaded,
                    )
                }
                true
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshOnce(isBackgroundPoll = false)
        }
    }

    fun pullToRefresh() {
        refreshNow()
    }

    // =========================================================================
    // UI ACTIONS
    // =========================================================================

    fun setFilter(filter: DbtopFilter) {
        _ui.update { it.copy(selectedFilter = filter) }
    }

    fun toggleRowExpanded(name: String) {
        _ui.update {
            val newName = if (it.expandedRowName == name) null else name
            it.copy(expandedRowName = newName)
        }
    }

    fun setTabMode(mode: Int) {
        _ui.update { it.copy(activeTabMode = mode) }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        private const val ACTIVE_POLL_INTERVAL_MS = 20_000L
        private const val MAX_BACKOFF_INTERVAL_MS = 60_000L
        private const val STALE_THRESHOLD_MS = 30_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DbtopViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return DbtopViewModel(
                            application = application,
                            settings = SettingsRepository.getInstance(application),
                            cacheManager = DbtopCacheManager(application),
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
