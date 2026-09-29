package com.jossephus.chuchu.ui.screens.Queue

/**
 * Registry ô trên dải machine (28/9, user: tự chọn ô hiện + thêm usage opencode-go).
 * Mỗi id là một ô Cell(label + giá trị + màu). Thứ tự trong danh sách = thứ tự vẽ.
 * Mặc định đúng 4 ô cũ (RAM·CPU·CL5H·CLWK) nên ai không đụng setting thì không đổi gì.
 */
internal object StripCells {
    const val RAM = "ram"
    const val CPU = "cpu"
    const val CL5H = "cl5h"
    const val CLWK = "clwk"
    const val AGY5H = "agy5h"
    const val AGYWK = "agywk"
    const val BAI = "bai"
    const val OC5H = "oc5h"
    const val OCWK = "ocwk"

    val ALL = listOf(RAM, CPU, CL5H, CLWK, AGY5H, AGYWK, BAI, OC5H, OCWK)
    val DEFAULT = listOf(RAM, CPU, CL5H, CLWK)

    /** Tên hiện trong setting (giữ ngắn như nhãn dải). */
    fun label(id: String): String = when (id) {
        RAM -> "RAM"
        CPU -> "CPU"
        CL5H -> "CL·5H"
        CLWK -> "CL·WK"
        AGY5H -> "AGY·5H"
        AGYWK -> "AGY·WK"
        BAI -> "BAI"
        OC5H -> "OC·5H"
        OCWK -> "OC·WK"
        else -> id
    }

    /** Chú thích nguồn trong setting. */
    fun hint(id: String): String = when (id) {
        RAM -> "% đã dùng"
        CPU -> "% toàn máy"
        CL5H, CLWK -> "claude còn lại"
        AGY5H, AGYWK -> "acc đang dùng còn lại"
        BAI -> "số credit còn lại"
        OC5H, OCWK -> "opencode-go còn lại"
        else -> ""
    }

    /** Lọc id lạ + trùng; rỗng (toàn lạ) thì về mặc định — dải không bao giờ vẽ ô ma. */
    fun normalize(ids: List<String>): List<String> =
        ids.filter { it in ALL }.distinct().ifEmpty { DEFAULT }
}
