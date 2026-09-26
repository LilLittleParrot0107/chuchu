package com.jossephus.chuchu.data.model.explorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload out/explorer.json do mkt/explorer.py gộp (26/9): newprojects + scout +
 * discover + WATCH (trend/gain/imgs, 27/9). Dashboard chỉ đọc đúng file này —
 * logic gộp nằm bên pipeline, app không port lại.
 */
@Serializable
data class ExplorerState(
    val ts: String = "",
    val tsnp: String = "",
    val tssc: String = "",
    val tsdc: String = "",
    /** Giờ cập nhật bảng TRENDING (mkt_snap/trending) — band WATCH · TRENDING. */
    val tstr: String = "",
    /** Sàn volume của danh sách gainer (gainers.json min_vol) — band GAINERS in "VOL ≥ …". */
    val gmin: Double? = null,
    /** {SYM: ảnh} top 500 CoinGecko — logo cho hàng token trong WATCH (mock chốt 27/9). */
    val imgs: Map<String, String> = emptyMap(),
    val trend: List<ExplorerTrend> = emptyList(),
    val gain: List<ExplorerGain> = emptyList(),
    val projects: List<ExplorerProject> = emptyList(),
    val yields: List<ExplorerYield> = emptyList(),
    val x: List<ExplorerBuzz> = emptyList(),
)

/** Một dòng TRENDING: thứ tự = hạng trending CoinGecko, mc_rank = hạng vốn hoá. */
@Serializable
data class ExplorerTrend(
    val sym: String,
    val name: String = "",
    val px: Double? = null,
    val chg: Double? = null,
    val img: String? = null,
    @SerialName("mc_rank") val mcRank: Int? = null,
    val vol: Double? = null,
)

/** Một dòng GAINERS: 24h trước, phần chỉ lọt top 7 ngày xếp sau (pipeline sắp). */
@Serializable
data class ExplorerGain(
    val sym: String,
    /** id CoinGecko (out/gainers.json "asset") — nút COINGECKO ↗ trong sheet. */
    val asset: String? = null,
    val name: String = "",
    val rank: Int? = null,
    val mcap: Double? = null,
    val vol24: Double? = null,
    /** vol 24h / vol trung bình 30 ngày. */
    val volx: Double? = null,
    val chg24: Double? = null,
    val chg7d: Double? = null,
    val chg30d: Double? = null,
    val img: String? = null,
    val driver: String? = null,
    val why: String = "",
    val family: String = "",
)

@Serializable
data class ExplorerProject(
    val name: String,
    val img: String? = null,
    val slug: String = "",
    val cat: String? = null,
    val chains: List<String> = emptyList(),
    val age: Int? = null,
    val tvl: Double? = null,
    val g7: Double? = null,
    val g30: Double? = null,
    val up14: Double? = null,
    val flags: List<String> = emptyList(),
    val action: String? = null,
    val why: String? = null,
    val audits: Int? = null,
    val tw: String? = null,
    val url: String? = null,
    val desc: String = "",
    @SerialName("n_pools") val nPools: Int? = null,
    val points: String = "",
)

@Serializable
data class ExplorerYield(
    val name: String,
    val img: String? = null,
    val chain: String? = null,
    val project: String? = null,
    val kind: String? = null,
    val lane: String? = null,
    val net: Double? = null,
    val risk: Double? = null,
    val why: String = "",
    val tvl: Double? = null,
    val flags: List<String> = emptyList(),
    val url: String? = null,
    val lltv: Double? = null,
    val lev: Double? = null,
    val bnet: Double? = null,
    val cy: Double? = null,
)

@Serializable
data class ExplorerBuzz(
    val name: String,
    val img: String? = null,
    val kind: String? = null,
    val score: Double? = null,
    val n: Int? = null,
    val likes: Int? = null,
    val head: String = "",
    val url: String? = null,
    @SerialName("by") val by: List<String> = emptyList(),
    val launch: Boolean = false,
    val yields: List<String> = emptyList(),
    // 26/9 user: BUZZ phải hiện NGÀY ĐĂNG bài dẫn — epoch giây do explorer.py tính
    // từ headline_ts; x cũ sắp mới → cũ theo trường này.
    @SerialName("when") val postTs: Long? = null,
)
