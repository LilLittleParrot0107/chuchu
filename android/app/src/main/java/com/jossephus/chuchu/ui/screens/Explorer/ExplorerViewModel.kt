package com.jossephus.chuchu.ui.screens.Explorer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jossephus.chuchu.data.model.explorer.ExplorerState
import com.jossephus.chuchu.data.network.JsonFileClient
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

// `label` = ten day du cho nut chuyen pane; pane X BUZZ dai nen giu nhu prototype.
enum class ExplorerPane(val label: String) {
    PROJECTS("PROJECTS"),
    YIELDS("YIELD"),
    BUZZ("X BUZZ"),
}

data class ExplorerUiState(
    val state: ExplorerState = ExplorerState(),
    val selectedPane: ExplorerPane = ExplorerPane.PROJECTS,
    val isRefreshing: Boolean = false,
    val everLoaded: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel cho tab EXPLORER: tải out/explorer.json qua dufs (ETag/304) và
 * poll mỗi 60s khi màn hiện — dữ liệu gộp đổi nhịp giờ nên không cần nhanh hơn.
 */
class ExplorerViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExplorerUiState())
    val ui: StateFlow<ExplorerUiState> = _ui.asStateFlow()

    private var client: JsonFileClient<ExplorerState>? = null
    private var clientUrl: String? = null
    private var pollJob: Job? = null

    init {
        refreshNow()
    }

    fun selectPane(pane: ExplorerPane) {
        _ui.update { it.copy(selectedPane = pane) }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshNow() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        _ui.update { it.copy(isRefreshing = true) }
        when (val result = withContext(Dispatchers.IO) { getOrCreateClient().fetch() }) {
            is JsonFileClient.FetchResult.Fresh -> _ui.update {
                it.copy(state = result.state, isRefreshing = false, everLoaded = true, error = null)
            }
            is JsonFileClient.FetchResult.Unchanged -> _ui.update {
                it.copy(isRefreshing = false, everLoaded = true, error = null)
            }
            is JsonFileClient.FetchResult.Failed -> _ui.update {
                it.copy(isRefreshing = false, error = "OFFLINE")
            }
        }
    }

    private fun getOrCreateClient(): JsonFileClient<ExplorerState> {
        val url = settings.resolvedExplorerUrl
        val existing = client
        if (existing != null && url == clientUrl) return existing
        return JsonFileClient(url, ExplorerState.serializer()).also {
            client = it
            clientUrl = url
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ExplorerViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return ExplorerViewModel(
                            settings = SettingsRepository.getInstance(application),
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
