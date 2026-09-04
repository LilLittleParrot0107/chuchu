package com.jossephus.chuchu.data.model.machine

import org.json.JSONObject

/**
 * Anh chup THO tu `GET /q/machine`.
 *
 * `cpuTotalS`, `cpuIdleS`, `netRx/Tx`, `MachineProc.cpuS` deu la so **cong don tu
 * luc khoi dong** — mot minh chung khong noi len dieu gi. Muon ra %CPU hay MB/s
 * thi lay hieu cua hai anh chup: xem [derive]. Server co the khong nho gi giua
 * hai request chinh la nho vay.
 */
data class MachineSnapshot(
    val ts: Long,
    val host: String,
    val ncpu: Int,
    val uptimeS: Long,
    val load: List<Double>,
    val cpuTotalS: Double,
    val cpuIdleS: Double,
    val memTotalKb: Long,
    val memAvailKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val diskTotalKb: Long,
    val diskUsedKb: Long,
    val netRx: Long,
    val netTx: Long,
    val tempCpu: Int?,
    val tempNvme: Int?,
    val gpu: MachineGpu?,
    val battery: MachineBattery?,
    val procs: List<MachineProc>,
    val agy: AgyQuota?,
    val claude: ClaudeQuota?,
) {
    val memUsedKb: Long get() = (memTotalKb - memAvailKb).coerceAtLeast(0)
    val memPct: Double get() = if (memTotalKb > 0) 100.0 * memUsedKb / memTotalKb else 0.0
    val diskPct: Double get() = if (diskTotalKb > 0) 100.0 * diskUsedKb / diskTotalKb else 0.0
}

data class MachineGpu(val util: Int, val temp: Int, val memUsedMb: Long, val memTotalMb: Long) {
    val memPct: Double get() = if (memTotalMb > 0) 100.0 * memUsedMb / memTotalMb else 0.0
}

data class MachineBattery(val pct: Int, val status: String) {
    /** May chay bang pin = co the dang mat dien — thu dang bao nhat khi o xa. */
    val discharging: Boolean get() = status.equals("Discharging", ignoreCase = true)
}

data class MachineProc(val comm: String, val cpuS: Double, val rssKb: Long, val n: Int)

data class AgyAccount(
    val id: String,
    val email: String?,
    val configured: Boolean,
    val error: String?,
    val pct5h: Double?,
    val pctWeek: Double?,
    /** Epoch giây lúc cửa sổ reset — từ `resetTime` ISO-8601 của agy; null nếu thiếu/hỏng. */
    val reset5h: Long? = null,
    val resetWeek: Long? = null,
)

/** Quota Antigravity — [pct5h]/[pctWeek] la phan tram CON LAI, khong phai da dung. */
data class AgyQuota(val current: String, val accounts: List<AgyAccount>, val cacheTs: Long)

data class ClaudeWindow(val usedPct: Int, val resetsAt: String?, val resetsEpoch: Long?)

/** Quota Claude — [sessionPct]/[weekPct] la phan tram DA DUNG (nguoc voi agy). */
data class ClaudeQuota(
    val ok: Boolean,
    val session: ClaudeWindow?,
    val week: ClaudeWindow?,
    val dataTs: Long,
    val error: String?,
)

/** Ket qua da tinh xong, san sang ve. */
data class MachineReadout(
    val snapshot: MachineSnapshot,
    /** null o nhip DAU (chua co anh chup truoc de tru). */
    val cpuPct: Double?,
    val netRxPerSec: Double?,
    val netTxPerSec: Double?,
    /** Rong o nhip dau — %CPU tung tien trinh cung can hai mau. */
    val topCpu: List<MachineProcRate>,
    val topRam: List<MachineProc>,
)

data class MachineProcRate(val comm: String, val pct: Double, val n: Int)

/**
 * Hieu cua hai anh chup -> so hien duoc.
 *
 * [prev] la null (nhip dau) hoac dong ho nhay lui thi tra null cho nhung o can
 * hai mau, CHU KHONG doan. UI hien "—" mot nhip roi co so that.
 *
 * %CPU tinh theo **ca may** (chia cho so nhan) de tong cua cac tien trinh khop
 * voi thanh CPU — giong wtop.
 */
fun derive(prev: MachineSnapshot?, cur: MachineSnapshot, topN: Int = 5): MachineReadout {
    val dt = if (prev == null) 0L else cur.ts - prev.ts
    val usable = prev != null && dt > 0

    val cpuPct = if (!usable) null else {
        val dTotal = cur.cpuTotalS - prev!!.cpuTotalS
        val dIdle = cur.cpuIdleS - prev.cpuIdleS
        // Boot lai may hay dem tran -> so lui: bo qua, dung bao 0% (nhin nhu
        // may dang ranh) cung dung bao am.
        if (dTotal <= 0.0 || dIdle < 0.0) null
        else (100.0 * (dTotal - dIdle) / dTotal).coerceIn(0.0, 100.0)
    }

    fun rate(before: Long, after: Long): Double? =
        if (!usable || after < before) null else (after - before).toDouble() / dt

    val topCpu = if (!usable) emptyList() else {
        val before = prev!!.procs.associateBy { it.comm }
        cur.procs.mapNotNull { p ->
            val old = before[p.comm] ?: return@mapNotNull null      // moi xuat hien, chua tinh duoc
            val d = p.cpuS - old.cpuS
            if (d < 0.0) return@mapNotNull null                     // nhom da chet roi sinh lai
            val pct = 100.0 * d / dt / cur.ncpu.coerceAtLeast(1)
            if (pct < 0.05) null else MachineProcRate(p.comm, pct, p.n)
        }.sortedByDescending { it.pct }.take(topN)
    }

    return MachineReadout(
        snapshot = cur,
        cpuPct = cpuPct,
        netRxPerSec = rate(prev?.netRx ?: 0L, cur.netRx),
        netTxPerSec = rate(prev?.netTx ?: 0L, cur.netTx),
        topCpu = topCpu,
        topRam = cur.procs.sortedByDescending { it.rssKb }.take(topN),
    )
}

/** Doc JSON cua `/machine`. Thieu phan nao thi phan do null, khong nem. */
fun parseMachineSnapshot(json: String): MachineSnapshot {
    val o = JSONObject(json)
    val cpu = o.optJSONObject("cpu")
    val mem = o.optJSONObject("mem")
    val disk = o.optJSONObject("disk")
    val net = o.optJSONObject("net")
    val temp = o.optJSONObject("temp")
    val quota = o.optJSONObject("quota")

    val load = o.optJSONArray("load")?.let { arr ->
        (0 until arr.length()).map { arr.optDouble(it, 0.0) }
    } ?: emptyList()

    val procs = o.optJSONArray("procs")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            MachineProc(
                comm = p.optString("comm"),
                cpuS = p.optDouble("cpu_s", 0.0),
                rssKb = p.optLong("rss_kb", 0L),
                n = p.optInt("n", 1),
            )
        }
    } ?: emptyList()

    return MachineSnapshot(
        ts = o.optLong("ts", 0L),
        host = o.optString("host", ""),
        ncpu = o.optInt("ncpu", 1),
        uptimeS = o.optLong("uptime_s", 0L),
        load = load,
        cpuTotalS = cpu?.optDouble("total_s", 0.0) ?: 0.0,
        cpuIdleS = cpu?.optDouble("idle_s", 0.0) ?: 0.0,
        memTotalKb = mem?.optLong("total_kb", 0L) ?: 0L,
        memAvailKb = mem?.optLong("avail_kb", 0L) ?: 0L,
        swapTotalKb = mem?.optLong("swap_total_kb", 0L) ?: 0L,
        swapFreeKb = mem?.optLong("swap_free_kb", 0L) ?: 0L,
        diskTotalKb = disk?.optLong("total_kb", 0L) ?: 0L,
        diskUsedKb = disk?.optLong("used_kb", 0L) ?: 0L,
        netRx = net?.optLong("rx", 0L) ?: 0L,
        netTx = net?.optLong("tx", 0L) ?: 0L,
        tempCpu = temp?.optIntOrNull("cpu"),
        tempNvme = temp?.optIntOrNull("nvme"),
        gpu = o.optJSONObject("gpu")?.let {
            MachineGpu(
                util = it.optInt("util", 0),
                temp = it.optInt("temp", 0),
                memUsedMb = it.optLong("mem_used_mb", 0L),
                memTotalMb = it.optLong("mem_total_mb", 0L),
            )
        },
        battery = o.optJSONObject("battery")?.let {
            MachineBattery(pct = it.optInt("pct", 0), status = it.optString("status", ""))
        },
        procs = procs,
        agy = quota?.optJSONObject("agy")?.let { parseAgy(it) },
        claude = quota?.optJSONObject("claude")?.let { parseClaude(it) },
    )
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key).takeIf { has(key) }

/**
 * "2026-09-11T07:31:47Z" -> epoch giây. Chuỗi lạ/rỗng -> null, không ném.
 * Dùng SimpleDateFormat vì minSdk 24 chưa có java.time (cần API 26).
 */
private fun isoEpoch(s: String?): Long? {
    if (s.isNullOrBlank() || s == "null") return null
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return try { fmt.parse(s)?.time?.div(1000) } catch (_: Exception) { null }
}

private fun parseAgy(o: JSONObject): AgyQuota {
    val accounts = o.optJSONArray("accounts")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            AgyAccount(
                id = a.optString("id"),
                email = a.optString("email").takeIf { it.isNotBlank() && it != "null" },
                configured = a.optBoolean("configured", false),
                error = a.optString("error").takeIf { it.isNotBlank() && it != "null" },
                pct5h = if (a.isNull("p5")) null else a.optDouble("p5"),
                pctWeek = if (a.isNull("pwk")) null else a.optDouble("pwk"),
                reset5h = isoEpoch(a.optString("p5_reset")),
                resetWeek = isoEpoch(a.optString("pwk_reset")),
            )
        }
    } ?: emptyList()
    return AgyQuota(
        current = o.optString("cur", ""),
        accounts = accounts,
        cacheTs = o.optLong("cache_ts", 0L),
    )
}

private fun parseClaude(o: JSONObject): ClaudeQuota {
    fun window(key: String): ClaudeWindow? = o.optJSONObject(key)?.let {
        ClaudeWindow(
            usedPct = it.optInt("used_pct", 0),
            resetsAt = it.optString("resets_at").takeIf { s -> s.isNotBlank() && s != "null" },
            resetsEpoch = if (it.isNull("resets_epoch")) null else it.optLong("resets_epoch"),
        )
    }
    return ClaudeQuota(
        ok = o.optBoolean("ok", false),
        session = window("session"),
        week = window("week"),
        // `ts` la moc lan LAY THANH CONG cuoi cung — hong thi no dung yen, nho
        // vay tinh duoc tuoi that thay vi tuong so vua moi.
        dataTs = o.optLong("ts", 0L),
        error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
    )
}
