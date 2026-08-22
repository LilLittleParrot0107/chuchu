package com.jossephus.chuchu.ui.screens.Queue

/** Queue projection used outside the full screen by Terminal and ServerList. */
data class QueueAmbientSummary(
    val totalActive: Int = 0,
    val runningCount: Int = 0,
    val pendingCount: Int = 0,
    val blockedCount: Int = 0,
    val isAnyWorking: Boolean = false,
    val isAnyBlocked: Boolean = false,
    val isPaused: Boolean = false,
    val activeTaskId: Int? = null,
    val primaryAgentName: String? = null,
    val statusText: String = "",
    val topTasks: List<QueueTask> = emptyList(),
    val hasError: Boolean = false,
) {
    companion object {
        val Empty = QueueAmbientSummary()

        fun from(state: QueueState, error: String?): QueueAmbientSummary {
            val activeTasks = state.tasks.filterNot { it.isCompleted }
            val runningTasks = activeTasks.filter { it.isRunning }
            val blockedAgents = state.agents.filter { agent ->
                agent.tone == QueueTone.Warn ||
                    agent.word.contains("needs approval", ignoreCase = true) ||
                    agent.word.contains("cần anh", ignoreCase = true)
            }
            val workingAgents = state.agents.filter { it.tone == QueueTone.Accent }
            val primaryTask = runningTasks.firstOrNull()
            val primaryAgent = workingAgents.firstOrNull() ?: state.agents.firstOrNull()

            return QueueAmbientSummary(
                totalActive = activeTasks.size,
                runningCount = runningTasks.size,
                pendingCount = (activeTasks.size - runningTasks.size).coerceAtLeast(0),
                blockedCount = blockedAgents.size,
                isAnyWorking = runningTasks.isNotEmpty() || workingAgents.isNotEmpty(),
                isAnyBlocked = blockedAgents.isNotEmpty(),
                isPaused = state.paused,
                activeTaskId = primaryTask?.id,
                primaryAgentName = primaryTask?.target?.takeIf(String::isNotBlank) ?: primaryAgent?.name,
                statusText = when {
                    blockedAgents.isNotEmpty() -> "needs approval"
                    runningTasks.isNotEmpty() -> "running"
                    state.paused -> "paused"
                    activeTasks.isNotEmpty() -> "waiting"
                    else -> "ready"
                },
                topTasks = activeTasks.take(MAX_AMBIENT_TASKS),
                hasError = error != null || state.banner?.tone == QueueTone.Error,
            )
        }

        private const val MAX_AMBIENT_TASKS = 3
    }
}
