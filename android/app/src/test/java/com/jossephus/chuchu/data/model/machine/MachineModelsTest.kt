package com.jossephus.chuchu.data.model.machine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hop dong voi `/q/machine`: server tra SO DEM THO, app tu lay hieu hai lan doc.
 * Mau duoi cat tu output that cua machine_state.py (3/9/2026).
 */
class MachineModelsTest {

    private val sample = """
    {"ts": 1788372000, "host": "the-real-witch", "ncpu": 16, "uptime_s": 90000,
     "load": [0.42, 1.10, 1.55],
     "cpu": {"total_s": 1000000.0, "idle_s": 900000.0},
     "mem": {"total_kb": 15000000, "avail_kb": 6000000,
             "swap_total_kb": 8000000, "swap_free_kb": 8000000},
     "disk": {"total_kb": 900000000, "used_kb": 180000000},
     "net": {"rx": 1000000, "tx": 500000},
     "temp": {"cpu": 63, "igpu": 45, "nvme": 41},
     "gpu": {"util": 8, "temp": 42, "mem_used_mb": 386, "mem_total_mb": 4096},
     "battery": {"pct": 80, "status": "Not charging"},
     "procs": [{"comm": "java", "cpu_s": 100.0, "rss_kb": 5767168, "n": 5},
               {"comm": "claude", "cpu_s": 50.0, "rss_kb": 1572864, "n": 7}],
     "quota": {
       "agy": {"cur": "acc2", "cache_ts": 1788371900,
               "accounts": [{"id": "acc1", "email": "a@b.c", "configured": true, "p5": 100.0, "pwk": 15.3,
                             "p5_reset": "2026-09-04T12:31:47Z", "pwk_reset": "khong-phai-ngay"},
                            {"id": "acc4", "configured": false}]},
       "claude": {"ok": true, "ts": 1788371000,
                  "session": {"used_pct": 4, "resets_at": "Sep 3, 4:20am", "resets_epoch": 1788384000},
                  "week": {"used_pct": 20, "resets_at": "Sep 6, 4pm", "resets_epoch": 1788685200}},
       "bai": {"ok": true, "ts": 1788371900, "balance": 14275165, "kind": "personal",
               "user": "user_PetRVOXfiJbc", "active": "active", "error": null}}}
    """.trimIndent()

    @Test
    fun parse_ReadsEveryBlock() {
        val s = parseMachineSnapshot(sample)
        assertEquals("the-real-witch", s.host)
        assertEquals(16, s.ncpu)
        assertEquals(0.42, s.load[0], 0.001)
        assertEquals(60.0, s.memPct, 0.1)          // (15000000-6000000)/15000000
        assertEquals(20.0, s.diskPct, 0.1)
        assertEquals(63, s.tempCpu)
        assertEquals(41, s.tempNvme)
        assertEquals(8, s.gpu!!.util)
        assertEquals(9.4, s.gpu!!.memPct, 0.2)
        assertEquals(80, s.battery!!.pct)
        assertEquals(2, s.procs.size)

        assertEquals("acc2", s.agy!!.current)
        assertEquals(15.3, s.agy!!.accounts[0].pctWeek!!, 0.01)
        assertNull("tai khoan chua cau hinh thi khong co so", s.agy!!.accounts[1].pctWeek)
        assertEquals(1788525107L, s.agy!!.accounts[0].reset5h)          // ISO UTC -> epoch
        assertNull("chuoi khong phai ngay thi null, khong nem", s.agy!!.accounts[0].resetWeek)
        assertNull(s.agy!!.accounts[1].reset5h)

        assertEquals(4, s.claude!!.session!!.usedPct)
        assertEquals(20, s.claude!!.week!!.usedPct)
        assertEquals("Sep 6, 4pm", s.claude!!.week!!.resetsAt)

        assertTrue(s.bai!!.ok)
        assertEquals(14275165L, s.bai!!.balance)
        assertEquals(1788371900L, s.bai!!.dataTs)
        assertNull(s.bai!!.error)
    }

    @Test
    fun parse_MissingBlocksAreNullNotCrash() {
        val s = parseMachineSnapshot("""{"ts": 1, "host": "vps", "cpu": {"total_s": 5.0}}""")
        assertEquals("vps", s.host)
        assertNull(s.gpu)
        assertNull(s.battery)
        assertNull(s.agy)
        assertNull(s.claude)
        assertNull(s.bai)
        assertNull(s.tempCpu)
        assertTrue(s.procs.isEmpty())
        assertEquals(0.0, s.memPct, 0.001)          // khong co mem -> 0, khong chia cho 0
    }

    @Test
    fun derive_FirstTickHasNoRates() {
        // Nhip dau: khong co gi de tru, phai tra null chu khong duoc doan 0%.
        val r = derive(null, parseMachineSnapshot(sample))
        assertNull(r.cpuPct)
        assertNull(r.netRxPerSec)
        assertTrue(r.topCpu.isEmpty())
        assertEquals(2, r.topRam.size)              // top RAM van co ngay tu nhip dau
        assertEquals("java", r.topRam[0].comm)
    }

    @Test
    fun derive_SecondTickComputesRates() {
        val a = parseMachineSnapshot(sample)
        // 2 giay sau: CPU chay them 32s (tren 16 nhan), idle them 16s -> ban 50%.
        val b = a.copy(
            ts = a.ts + 2,
            cpuTotalS = a.cpuTotalS + 32.0,
            cpuIdleS = a.cpuIdleS + 16.0,
            netRx = a.netRx + 2048,
            netTx = a.netTx + 1024,
            procs = listOf(
                a.procs[0].copy(cpuS = a.procs[0].cpuS + 0.32),   // 0.32s/2s/16 nhan = 1%
                a.procs[1].copy(cpuS = a.procs[1].cpuS + 3.2),    // = 10%
            ),
        )
        val r = derive(a, b)
        assertEquals(50.0, r.cpuPct!!, 0.01)
        assertEquals(1024.0, r.netRxPerSec!!, 0.01)
        assertEquals(512.0, r.netTxPerSec!!, 0.01)
        assertEquals("claude", r.topCpu[0].comm)
        assertEquals(10.0, r.topCpu[0].pct, 0.01)
        assertEquals(1.0, r.topCpu[1].pct, 0.01)
    }

    @Test
    fun derive_CounterGoingBackwardsGivesNullNotNegative() {
        // May khoi dong lai giua hai nhip: so dem ve 0. Tha khong hien con hon
        // hien -300% hay 0% (nhin nhu may dang ranh).
        val a = parseMachineSnapshot(sample)
        val b = a.copy(ts = a.ts + 2, cpuTotalS = 10.0, cpuIdleS = 9.0, netRx = 0)
        val r = derive(a, b)
        assertNull(r.cpuPct)
        assertNull(r.netRxPerSec)
    }

    @Test
    fun derive_NewProcessGroupIsSkippedUntilItHasTwoSamples() {
        val a = parseMachineSnapshot(sample)
        val b = a.copy(
            ts = a.ts + 2,
            cpuTotalS = a.cpuTotalS + 32.0,
            cpuIdleS = a.cpuIdleS + 16.0,
            procs = a.procs + MachineProc("zig", cpuS = 99.0, rssKb = 1024, n = 1),
        )
        val r = derive(a, b)
        assertTrue("nhom moi chua co mau truoc, khong duoc suy ra 99%",
            r.topCpu.none { it.comm == "zig" })
    }

    @Test
    fun derive_ZeroElapsedIsNotDividedBy() {
        val a = parseMachineSnapshot(sample)
        val r = derive(a, a.copy(cpuTotalS = a.cpuTotalS + 5.0))   // cung ts
        assertNull(r.cpuPct)
        assertNull(r.netRxPerSec)
    }
}
