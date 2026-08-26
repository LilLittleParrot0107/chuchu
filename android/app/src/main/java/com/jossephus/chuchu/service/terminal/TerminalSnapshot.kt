package com.jossephus.chuchu.service.terminal

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ImagePlacement(
    val cellCol: Int,
    val cellRow: Int,
    val cellXOffset: Int,
    val cellYOffset: Int,
    val destW: Int,
    val destH: Int,
    val srcX: Int,
    val srcY: Int,
    val srcW: Int,
    val srcH: Int,
    val imgW: Int,
    val imgH: Int,
    val bitmap: Bitmap,
)

data class TerminalSnapshot(
    val cols: Int,
    val rows: Int,
    val cursorX: Int,
    val cursorY: Int,
    val cursorVisible: Boolean,
    val defaultBgArgb: Int,
    val defaultFgArgb: Int,
    val codepoints: IntArray,
    val fgArgb: IntArray,
    val bgArgb: IntArray,
    val flags: ByteArray,
    /**
     * Sparse map: cell index -> extra grapheme codepoints (appended after the
     * base codepoint stored in [codepoints]). Present when the corresponding
     * cell has [CELL_FLAG_HAS_GRAPHEME] set.
     */
    val graphemeExtras: Map<Int, IntArray> = emptyMap(),
    val images: List<ImagePlacement> = emptyList(),
    /**
     * Stable screen.y of the content currently at viewport row 0. Changes
     * monotonically as the viewport scrolls, so the host can subtract it
     * across snapshots to remap a content-tracking selection anchor.
     */
    val viewportScrollY: Int = 0,
    /**
     * True when the running app has enabled a drag-reporting mouse mode
     * (DECSET 1002/1003). When set, the host forwards long-press drag
     * gestures to the app so a multiplexer (tmux/zellij/...) can perform
     * its own pane-scoped selection in copy mode instead of the host
     * building a grid-wide client-side selection that crosses pane borders.
     */
    val appHandlesSelectionDrag: Boolean = false,
    /**
     * FNV-1a 64-bit over the full cell grid + grapheme extras, computed in a
     * single pass during parse. equals/hashCode compare THIS instead of
     * content-scanning four whole-grid arrays on every StateFlow emit (up to
     * 60/s) — that scan was doubling the per-frame snapshot cost.
     */
    val contentHash: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalSnapshot) return false
        return cols == other.cols && rows == other.rows &&
            cursorX == other.cursorX && cursorY == other.cursorY &&
            cursorVisible == other.cursorVisible &&
            defaultBgArgb == other.defaultBgArgb &&
            defaultFgArgb == other.defaultFgArgb &&
            contentHash == other.contentHash &&
            images == other.images &&
            viewportScrollY == other.viewportScrollY &&
            appHandlesSelectionDrag == other.appHandlesSelectionDrag
    }

    override fun hashCode(): Int {
        var result = cols
        result = 31 * result + rows
        result = 31 * result + cursorX
        result = 31 * result + cursorY
        result = 31 * result + cursorVisible.hashCode()
        result = 31 * result + defaultBgArgb
        result = 31 * result + defaultFgArgb
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + images.hashCode()
        result = 31 * result + viewportScrollY
        result = 31 * result + appHandlesSelectionDrag.hashCode()
        return result
    }

    /**
     * Scratch parse tai su dung giua cac frame. CHI chua buffer trung gian
     * khong bao gio thoat khoi fromByteBuffer — cac mang publish
     * (codepoints/fg/bg/flags) van cap phat moi vi UI thread co the con giu
     * snapshot cu (xem comment trong ham).
     */
    class ParseScratch {
        internal var cellBytes: ByteArray = ByteArray(0)

        internal fun obtain(size: Int): ByteArray {
            if (cellBytes.size < size) cellBytes = ByteArray(size)
            return cellBytes
        }
    }

    /**
     * Cache Bitmap theo noi dung (dims + len + FNV mau ~768 byte cua pixel)
     * — truoc day moi image snapshot (toi ~60/s) goi Bitmap.createBitmap +
     * copyPixelsFromBuffer cho CA anh kitty tinh, tuc la cap phat va upload
     * lai nguyen bitmap moi frame.
     */
    class BitmapCache {
        private class Entry(val bitmap: Bitmap, var gen: Long)

        private val entries = HashMap<Long, Entry>()
        private var gen = 0L

        internal fun beginFrame() {
            gen++
        }

        internal fun obtain(key: Long, imgW: Int, imgH: Int, pixels: ByteBuffer): Bitmap {
            entries[key]?.let { entry ->
                entry.gen = gen
                return entry.bitmap
            }
            val bitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(pixels)
            entries[key] = Entry(bitmap, gen)
            return bitmap
        }

        internal fun endFrame() {
            // Giu lai 1 the he: list cua frame truoc co the van dang duoc UI
            // ve. Khong recycle() — tha cho GC de khoi dung bitmap dang hien thi.
            entries.entries.removeAll { (_, entry) -> gen - entry.gen > 1 }
        }
    }

    companion object {
        const val CELL_FLAG_HAS_GRAPHEME: Int = 0x40
        const val CELL_FLAG_SPACER: Int = 0x80
        const val CELL_FLAG_BOLD: Int = 0x01
        const val CELL_FLAG_ITALIC: Int = 0x02
        const val CELL_FLAG_UNDERLINE: Int = 0x04
        const val CELL_FLAG_INVERSE: Int = 0x08
        const val CELL_FLAG_BLINK: Int = 0x10
        const val CELL_FLAG_FAINT: Int = 0x20
        private const val HEADER_I32_COUNT = 14
        private const val CELL_SIZE_BYTES = 11
        private const val IMAGE_HEADER_BYTES = 52
        private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L

        private fun packArgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        fun fromByteBuffer(
            buffer: ByteBuffer,
            images: List<ImagePlacement> = emptyList(),
            scratch: ParseScratch? = null,
        ): TerminalSnapshot {
            val wrapped = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            wrapped.position(0)

            val cols = wrapped.int
            val rows = wrapped.int
            val cursorX = wrapped.int
            val cursorY = wrapped.int
            val cursorVisible = wrapped.int == 1
            val defaultBgR = wrapped.int
            val defaultBgG = wrapped.int
            val defaultBgB = wrapped.int
            val defaultFgR = wrapped.int
            val defaultFgG = wrapped.int
            val defaultFgB = wrapped.int
            val extrasOffset = wrapped.int
            val viewportScrollY = wrapped.int
            val appHandlesSelectionDrag = wrapped.int == 1

            val cellCount = cols * rows
            val expectedSize = (HEADER_I32_COUNT * 4) + (cellCount * CELL_SIZE_BYTES)
            require(buffer.capacity() >= expectedSize) {
                "Snapshot buffer too small: cap=${buffer.capacity()} expected=$expectedSize"
            }

            // Bulk-read all cell bytes in one operation, then parse from the
            // byte array to avoid thousands of virtual-dispatch ByteBuffer
            // calls that dominate the parse cost on Android.
            // Allocate fresh arrays each frame — the old arrays are held by the
            // previous TerminalSnapshot visible to the UI thread, so reusing
            // them would cause a data race.
            val cellDataLen = cellCount * CELL_SIZE_BYTES
            val cellBytes = scratch?.obtain(cellDataLen) ?: ByteArray(cellDataLen)
            wrapped.get(cellBytes, 0, cellDataLen)

            val codepoints = IntArray(cellCount)
            val fgArgb = IntArray(cellCount)
            val bgArgb = IntArray(cellCount)
            val flags = ByteArray(cellCount)

            var off = 0
            var hash = FNV_OFFSET_BASIS
            for (i in 0 until cellCount) {
                // codepoint: little-endian i32 from 4 bytes
                codepoints[i] = (cellBytes[off].toInt() and 0xFF) or
                    ((cellBytes[off + 1].toInt() and 0xFF) shl 8) or
                    ((cellBytes[off + 2].toInt() and 0xFF) shl 16) or
                    ((cellBytes[off + 3].toInt() and 0xFF) shl 24)
                off += 4
                val fgR = cellBytes[off].toInt() and 0xFF; off++
                val fgG = cellBytes[off].toInt() and 0xFF; off++
                val fgB = cellBytes[off].toInt() and 0xFF; off++
                val bgR = cellBytes[off].toInt() and 0xFF; off++
                val bgG = cellBytes[off].toInt() and 0xFF; off++
                val bgB = cellBytes[off].toInt() and 0xFF; off++
                fgArgb[i] = packArgb(fgR, fgG, fgB)
                bgArgb[i] = packArgb(bgR, bgG, bgB)
                flags[i] = cellBytes[off]; off++
                hash = (hash xor codepoints[i].toLong()) * FNV_PRIME
                hash = (hash xor fgArgb[i].toLong()) * FNV_PRIME
                hash = (hash xor bgArgb[i].toLong()) * FNV_PRIME
                hash = (hash xor flags[i].toLong()) * FNV_PRIME
            }

            val graphemeExtras: Map<Int, IntArray> =
                if (extrasOffset > 0 && extrasOffset < wrapped.capacity()) {
                    val extras = wrapped.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    extras.position(extrasOffset)
                    if (extras.remaining() < 4) {
                        emptyMap()
                    } else {
                        val recordCount = extras.int
                        val parsed = HashMap<Int, IntArray>(recordCount.coerceAtLeast(0))
                        var valid = true
                        for (record in 0 until recordCount) {
                            if (extras.remaining() < 8) {
                                valid = false
                                break
                            }
                            val cellIndex = extras.int
                            val count = extras.int
                            if (cellIndex !in 0 until cellCount || count < 0 || extras.remaining() < count * 4) {
                                valid = false
                                break
                            }
                            val cps = IntArray(count)
                            for (j in 0 until count) cps[j] = extras.int
                            parsed[cellIndex] = cps
                            hash = (hash xor cellIndex.toLong()) * FNV_PRIME
                            for (cp in cps) hash = (hash xor cp.toLong()) * FNV_PRIME
                        }
                        if (valid) parsed else emptyMap()
                    }
                } else {
                    emptyMap()
                }

            val snapshot = TerminalSnapshot(
                cols = cols,
                rows = rows,
                cursorX = cursorX,
                cursorY = cursorY,
                cursorVisible = cursorVisible,
                defaultBgArgb = packArgb(defaultBgR, defaultBgG, defaultBgB),
                defaultFgArgb = packArgb(defaultFgR, defaultFgG, defaultFgB),
                codepoints = codepoints,
                fgArgb = fgArgb,
                bgArgb = bgArgb,
                flags = flags,
                graphemeExtras = graphemeExtras,
                images = images,
                viewportScrollY = viewportScrollY,
                appHandlesSelectionDrag = appHandlesSelectionDrag,
                contentHash = hash,
            )

            return snapshot
        }

        fun parseImages(buffer: ByteBuffer?, cache: BitmapCache? = null): List<ImagePlacement> {
            if (buffer == null || buffer.capacity() < 4) return emptyList()
            val wrapped = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            wrapped.position(0)
            val count = wrapped.int
            cache?.beginFrame()
            if (count <= 0) {
                cache?.endFrame()
                return emptyList()
            }

            val images = ArrayList<ImagePlacement>(count)
            for (i in 0 until count) {
                if (wrapped.remaining() < IMAGE_HEADER_BYTES) break
                val cellCol = wrapped.int
                val cellRow = wrapped.int
                val cellXOffset = wrapped.int
                val cellYOffset = wrapped.int
                val destW = wrapped.int
                val destH = wrapped.int
                val srcX = wrapped.int
                val srcY = wrapped.int
                val srcW = wrapped.int
                val srcH = wrapped.int
                val imgW = wrapped.int
                val imgH = wrapped.int
                val dataLen = wrapped.int

                val expectedLen = imgW.toLong() * imgH.toLong() * 4L
                if (imgW <= 0 || imgH <= 0 || dataLen <= 0 ||
                    expectedLen > Int.MAX_VALUE ||
                    dataLen != expectedLen.toInt() ||
                    wrapped.remaining() < dataLen
                ) {
                    Log.w(
                        "TerminalSnapshot",
                        "bad image record: img=${imgW}x$imgH dataLen=$dataLen expected=$expectedLen remaining=${wrapped.remaining()}",
                    )
                    break
                }

                val pixelBytes = wrapped.slice().order(ByteOrder.nativeOrder())
                pixelBytes.limit(dataLen)

                val bitmap = if (cache != null) {
                    // Key noi dung: dims + len + FNV cua 3 lat cat 256 byte
                    // (dau/giua/cuoi) — du de phan biet anh that ma khong
                    // phai hash ca MB moi frame.
                    var key = FNV_OFFSET_BASIS
                    key = (key xor imgW.toLong()) * FNV_PRIME
                    key = (key xor imgH.toLong()) * FNV_PRIME
                    key = (key xor dataLen.toLong()) * FNV_PRIME
                    val base = wrapped.position()
                    val sampleStarts = intArrayOf(0, (dataLen / 2 - 128).coerceAtLeast(0), (dataLen - 256).coerceAtLeast(0))
                    for (start in sampleStarts) {
                        val end = (start + 256).coerceAtMost(dataLen)
                        for (p in start until end) {
                            key = (key xor wrapped.get(base + p).toLong()) * FNV_PRIME
                        }
                    }
                    cache.obtain(key, imgW, imgH, pixelBytes)
                } else {
                    Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888).also {
                        it.copyPixelsFromBuffer(pixelBytes)
                    }
                }
                wrapped.position(wrapped.position() + dataLen)

                images += ImagePlacement(
                    cellCol = cellCol,
                    cellRow = cellRow,
                    cellXOffset = cellXOffset,
                    cellYOffset = cellYOffset,
                    destW = destW,
                    destH = destH,
                    srcX = srcX,
                    srcY = srcY,
                    srcW = srcW,
                    srcH = srcH,
                    imgW = imgW,
                    imgH = imgH,
                    bitmap = bitmap,
                )
            }
            cache?.endFrame()
            return images
        }
    }
}
