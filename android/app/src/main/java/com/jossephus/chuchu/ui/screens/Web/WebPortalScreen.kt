package com.jossephus.chuchu.ui.screens.Web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.screens.Files.formatFileSize
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PortalEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtimeMs: Long,
)

private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "ts")
private val AUDIO_EXT = setOf("mp3", "m4a", "flac", "ogg", "wav", "opus")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

private object WebPortalCache {
    private val cache = android.util.LruCache<String, List<PortalEntry>>(50)

    fun get(url: String, path: String): List<PortalEntry>? = cache.get("$url:$path")
    fun put(url: String, path: String, entries: List<PortalEntry>) {
        cache.put("$url:$path", entries)
    }
}

/**
 * Native browser for the dufs file portal — kohi-styled listing over dufs'
 * `?json` API instead of a WebView (which was just a worse Edge). Videos and
 * audio stream straight into the system player chooser, APKs go through the
 * system DownloadManager, everything else opens via ACTION_VIEW.
 */
@Composable
fun WebPortalScreen(
    url: String,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val context = LocalContext.current
    val baseUrl = remember(url) { url.trimEnd('/') }

    var path by remember { mutableStateOf("") } // "" = root, else "a/b"
    var entries by remember(baseUrl, path) {
        mutableStateOf(WebPortalCache.get(baseUrl, path) ?: emptyList())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember(baseUrl, path) {
        mutableStateOf(entries.isEmpty())
    }
    var reloadTick by remember { mutableIntStateOf(0) }

    fun encodedPath(p: String): String =
        p.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

    LaunchedEffect(path, reloadTick) {
        val cached = WebPortalCache.get(baseUrl, path)
        if (cached != null && cached.isNotEmpty()) {
            entries = cached
            loading = false
        } else {
            loading = true
        }
        error = null
        val listUrl = baseUrl + "/" + encodedPath(path) + "?json"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(listUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val stream = conn.inputStream
                try {
                    stream.bufferedReader().readText()
                } finally {
                    stream.close()
                }
            }
        }
        result.fold(
            onSuccess = { body ->
                runCatching {
                    val paths = JSONObject(body).getJSONArray("paths")
                    val list = ArrayList<PortalEntry>(paths.length())
                    for (i in 0 until paths.length()) {
                        val o = paths.getJSONObject(i)
                        list += PortalEntry(
                            name = o.getString("name"),
                            isDir = o.getString("path_type").contains("Dir"),
                            size = o.optLong("size", 0L),
                            mtimeMs = o.optLong("mtime", 0L),
                        )
                    }
                    val sorted = list.sortedWith(
                        compareByDescending<PortalEntry> { it.isDir }
                            .thenBy { it.name.lowercase() },
                    )
                    entries = sorted
                    WebPortalCache.put(baseUrl, path, sorted)
                    error = null
                }.onFailure {
                    if (entries.isEmpty()) {
                        error = "bad listing: ${it.message}"
                    }
                }
            },
            onFailure = {
                if (entries.isEmpty()) {
                    error = it.message ?: "network error"
                }
            },
        )
        loading = false
    }

    fun goUp() {
        path = path.substringBeforeLast('/', "")
    }

    BackHandler {
        if (path.isEmpty()) onClose() else goUp()
    }

    fun openEntry(entry: PortalEntry) {
        if (entry.isDir) {
            path = if (path.isEmpty()) entry.name else "$path/${entry.name}"
            return
        }
        val fileUrl = baseUrl + "/" + encodedPath(
            if (path.isEmpty()) entry.name else "$path/${entry.name}",
        )
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        when {
            ext == "apk" -> {
                runCatching {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val req = DownloadManager.Request(Uri.parse(fileUrl))
                        .setTitle(entry.name)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            entry.name,
                        )
                    dm.enqueue(req)
                    Toast.makeText(context, "downloading ${entry.name}…", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "download failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            ext in VIDEO_EXT -> viewUrl(context, fileUrl, "video/*")
            ext in AUDIO_EXT -> viewUrl(context, fileUrl, "audio/*")
            ext in IMAGE_EXT -> viewUrl(context, fileUrl, "image/*")
            else -> viewUrl(context, fileUrl, null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuButton(
                onClick = { if (path.isEmpty()) onClose() else goUp() },
                variant = ChuButtonVariant.Outlined,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                ChuText("←", style = typography.label)
            }
            ChuText(
                "/" + path,
                style = typography.label,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChuButton(
                onClick = { reloadTick += 1 },
                variant = ChuButtonVariant.Outlined,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                ChuText("↻", style = typography.label)
            }
        }

        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChuText("loading…", style = typography.label, color = colors.textMuted)
            }
            error != null && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChuText(error ?: "", style = typography.label, color = colors.textMuted)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.name }) { entry ->
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val glyph = when {
                        entry.isDir -> "/"
                        ext in VIDEO_EXT -> "▶"
                        ext in AUDIO_EXT -> "♪"
                        ext in IMAGE_EXT -> "◻"
                        ext == "apk" -> "⇩"
                        else -> "·"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openEntry(entry) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChuText(
                            glyph,
                            style = typography.label,
                            color = if (entry.isDir) colors.accent else colors.textMuted,
                        )
                        ChuText(
                            entry.name,
                            style = typography.body,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        ChuText(
                            if (entry.isDir) "" else formatFileSize(entry.size),
                            style = typography.labelSmall,
                            color = colors.textMuted,
                        )
                        ChuText(
                            if (entry.mtimeMs > 0) {
                                SimpleDateFormat("dd/MM", Locale.US).format(Date(entry.mtimeMs))
                            } else "",
                            style = typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

private fun viewUrl(context: Context, url: String, mime: String?) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (mime != null) setDataAndType(Uri.parse(url), mime) else data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Toast.makeText(context, "open failed: ${it.message}", Toast.LENGTH_LONG).show()
    }
}
