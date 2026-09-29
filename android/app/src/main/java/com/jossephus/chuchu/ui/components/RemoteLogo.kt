package com.jossephus.chuchu.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Logo nhỏ cho hàng explorer (icon DeFiLlama, avatar X qua unavatar.io) — app
 * không có Coil/Glide nên tự tải bằng HttpURLConnection với cache RAM theo URL
 * (user hỏi 26/9: "explore không có logo như preview à?"). URL thiếu hay tải
 * hỏng thì rơi về glyph như bản trước, hàng không bị nhảy layout.
 */
private val logoCache = LruCache<String, ImageBitmap>(48)

@Composable
fun RemoteLogo(
    url: String?,
    fallback: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = url?.let { logoCache.get(it) },
        key1 = url,
    ) {
        if (url == null) {
            value = null
            return@produceState
        }
        logoCache.get(url)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            // RAM → disk (sống qua restart) → mạng. Disk quan trọng vì unavatar
            // giới hạn quota theo IP (29/9: IP datacenter hết quota 19h): tải lại
            // mỗi lần mở app là tự bắn vào chân.
            loadDiskLogo(context, url)?.also { logoCache.put(url, it) }
                ?: fetchLogoBytes(url)?.let { bytes ->
                    saveDiskLogo(context, url, bytes)
                    decodeLogo(bytes)?.also { logoCache.put(url, it) }
                }
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(3.dp)),
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            ChuText(
                fallback,
                style = ChuTypography.current.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/** Key file an toàn từ URL (SHA-256 hex). */
private fun logoKey(url: String): String = try {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
} catch (e: Exception) {
    url.hashCode().toString(16)
}

private fun logoFile(context: android.content.Context, url: String): java.io.File =
    java.io.File(java.io.File(context.filesDir, "logos"), logoKey(url))

private fun loadDiskLogo(context: android.content.Context, url: String): ImageBitmap? = runCatching {
    val f = logoFile(context, url)
    if (!f.isFile) return@runCatching null
    decodeLogo(f.readBytes())
}.getOrNull()

private fun saveDiskLogo(context: android.content.Context, url: String, bytes: ByteArray) = runCatching {
    val f = logoFile(context, url)
    f.parentFile?.mkdirs()
    // Ảnh logo vài KB, vài trăm handle — không cap số lượng (đủ nhẹ để kệ).
    f.writeBytes(bytes)
}

private fun fetchLogoBytes(url: String): ByteArray? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "kohi-android")
    }
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
        conn.inputStream.use { it.readBytes() }
    } finally {
        // Khong disconnect(): giu keep-alive (cung ly do DbtopClient/JsonFileClient).
        runCatching { conn.errorStream?.close() }
    }
}.getOrNull()

private fun decodeLogo(bytes: ByteArray): ImageBitmap? {
    // Ảnh avatar có thể 400x400 — decode 2 lượt để hạ mẫu, hàng list chỉ cần ~96px.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

