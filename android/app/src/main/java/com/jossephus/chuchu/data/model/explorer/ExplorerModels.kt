package com.jossephus.chuchu.data.model.explorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload out/explorer.json do mkt/explorer.py gộp (26/9): newprojects + scout +
 * discover + giá 4 coin đầu. Tab EXPLORER chỉ đọc đúng file này — logic gộp nằm
 * bên pipeline, app không port lại.
 */
@Serializable
data class ExplorerState(
    val ts: String = "",
    val tsnp: String = "",
    val tssc: String = "",
    val tsdc: String = "",
    val market: List<ExplorerCoin> = emptyList(),
    val projects: List<ExplorerProject> = emptyList(),
    val yields: List<ExplorerYield> = emptyList(),
    val x: List<ExplorerBuzz> = emptyList(),
)

@Serializable
data class ExplorerCoin(
    val sym: String,
    val px: Double? = null,
    val c24: Double? = null,
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
    val vi: String = "",
    val url: String? = null,
    @SerialName("by") val by: List<String> = emptyList(),
    val launch: Boolean = false,
    val yields: List<String> = emptyList(),
    // 26/9 user: BUZZ phải hiện NGÀY ĐĂNG bài dẫn — epoch giây do explorer.py tính
    // từ headline_ts; x cũ sắp mới → cũ theo trường này.
    @SerialName("when") val postTs: Long? = null,
)
