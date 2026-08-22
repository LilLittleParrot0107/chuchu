package com.jossephus.chuchu.data.network

/** Normalize browser and API URLs to the qsrv base route used by QueueClient. */
internal fun normalizeQueueBaseUrl(raw: String): String {
    var normalized = raw.trim().substringBefore('#').substringBefore('?').trimEnd('/')
    normalized = when {
        normalized.endsWith("/ui", ignoreCase = true) -> normalized.dropLast(3)
        normalized.endsWith("/state", ignoreCase = true) -> normalized.dropLast(6)
        else -> normalized
    }
    return normalized.trimEnd('/')
}
