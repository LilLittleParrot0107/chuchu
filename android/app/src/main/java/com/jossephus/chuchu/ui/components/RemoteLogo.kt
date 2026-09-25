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
            fetchLogo(url)?.also { logoCache.put(url, it) }
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

private fun fetchLogo(url: String): ImageBitmap? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "kohi-android")
    }
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
        val bytes = conn.inputStream.use { it.readBytes() }
        // Ảnh avatar có thể 400x400 — decode 2 lượt để hạ mẫu, hàng list chỉ cần ~96px.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    } finally {
        // Khong disconnect(): giu keep-alive (cung ly do DbtopClient/JsonFileClient).
        runCatching { conn.errorStream?.close() }
    }
}.getOrNull()
