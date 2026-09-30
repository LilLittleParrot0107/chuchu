package com.jossephus.chuchu.ui.screens.Web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.jossephus.chuchu.ui.components.KohiBackHandler
import com.jossephus.chuchu.ui.components.noRippleClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.screens.Files.formatFileSize
import com.jossephus.chuchu.ui.screens.Queue.FileHit
import com.jossephus.chuchu.ui.screens.Queue.FileSearchResult
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    // dufs ?json trả mtime MILI giây (khác qsrv trả giây rồi app ×1000 ở FileHit).
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
    /** Nhúng vào trang FILES của Queue (23/9): màn ngoài đã đệm system bar, không đệm thêm. */
    embedded: Boolean = false,
    /**
     * qsrv /files/search (25/9, prototype duyệt). null = không có ô search.
     * UI tự debounce 300ms rồi gọi trên Dispatchers.IO.
     */
    onSearch: (suspend (String) -> FileSearchResult)? = null,
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

    // Ô search (25/9): searchHits = null nghĩa là đang xem portal thường,
    // != null thì thân màn là danh sách kết quả (thay listing).
    var query by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<FileHit>?>(null) }
    var searchStat by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }
    val searchFn = rememberUpdatedState(onSearch)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            searchHits = null
            searchStat = ""
            searchError = null
            return@LaunchedEffect
        }
        delay(300) // gõ nhanh chỉ tìm một lần cho cụm chữ cuối
        val fn = searchFn.value ?: return@LaunchedEffect
        searchStat = "searching…"
        val r = fn(q)
        if (query.trim() != q) return@LaunchedEffect // người dùng đã gõ tiếp — vứt kết quả cũ
        searchError = r.error
        // 30/9 (user chốt B): kết quả search xếp mới đổi nhất trước (mtimeMs có sẵn).
        searchHits = r.hits?.sortedByDescending { it.mtimeMs }
        searchStat = if (r.error != null) "" else "${r.total} kết quả · ${"%.0f".format(Locale.US, r.tookMs)}ms"
    }

    fun encodedPath(p: String): String =
        p.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

    // 30/9 review: effect key thiếu baseUrl — đổi URL portal không fetch lại.
    LaunchedEffect(baseUrl, path, reloadTick) {
        val cached = WebPortalCache.get(baseUrl, path)
        // 30/9 review: thư mục rỗng đã cache cũng dùng (bản cũ isNotEmpty nên vào
        // lại dir rỗng là fetch thừa + chớp loading).
        if (cached != null) {
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

    KohiBackHandler {
        if (path.isEmpty()) onClose() else goUp()
    }

    /** Mở FILE theo đường dẫn tương đối trong vùng portal — dùng chung listing và kết quả search. */
    fun openFileAt(rel: String, name: String) {
        val fileUrl = baseUrl + "/" + encodedPath(rel)
        val ext = name.substringAfterLast('.', "").lowercase()
        when {
            ext == "apk" -> {
                runCatching {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val req = DownloadManager.Request(Uri.parse(fileUrl))
                        .setTitle(name)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            name,
                        )
                    dm.enqueue(req)
                    Toast.makeText(context, "downloading $name…", Toast.LENGTH_SHORT).show()
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

    fun openEntry(entry: PortalEntry) {
        val rel = if (path.isEmpty()) entry.name else "$path/${entry.name}"
        if (entry.isDir) {
            path = rel
            return
        }
        openFileAt(rel, entry.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .then(if (embedded) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()),
    ) {
        // Ô search (25/9, prototype v2 duyệt): field cùng loại với ô filter của
        // FileBrowserScreen. Gõ → qsrv /files/search, thân màn thay bằng kết quả.
        // 30/9 (user chốt B): search lên CÙNG HÀNG refresh, path gọn thành dòng nhỏ.
        // Không search (WebPortal dùng ngoài Queue) thì giữ hàng path + refresh cũ.
        if (onSearch != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("⌕", style = typography.label, color = colors.accent)
                ChuTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    placeholder = "search files…",
                    singleLine = true,
                    showLabel = false,
                    autoFocus = false,
                    verticalPadding = 8.dp,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    ChuText(
                        "[×]",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.noRippleClickable { query = "" },
                    )
                }
                ReloadButton(onReload = { reloadTick += 1 })
            }
            ChuText(
                if (path.isEmpty()) "/" else "/$path",
                style = typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nút ← đã bỏ (user chốt 16/9): back hệ thống lên thư mục cha rồi đóng (BackHandler ở trên).
                ChuText(
                    "/" + path,
                    style = typography.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ReloadButton(onReload = { reloadTick += 1 })
            }
        }

        val hits = searchHits
        when {
            // Kết quả search: cùng khuôn hàng portal + thêm cột thư mục (25/9).
            hits != null -> {
                if (searchStat.isNotBlank()) {
                    ChuText(
                        searchStat,
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
                searchError?.let { msg ->
                    ChuText(
                        msg,
                        style = typography.label,
                        color = colors.error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hits, key = { it.path }) { hit ->
                        // 30/9 (user chốt B): hàng phẳng, BỎ vạch hairline (27/9 đã thêm).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 27/9 (user: "bấm vào file đó thì t ko bấm được, chỉ được
                                    // đưa đến folder chứa nó"): file = MỞ THẲNG file như listing
                                    // (apk tải, media mở app xem, còn lại ACTION_VIEW); thư mục
                                    // = vào chính nó. Bỏ hành vi cũ "nhảy vào thư mục chứa".
                                    if (hit.isDir) {
                                        path = hit.path
                                        searchHits = null
                                        searchStat = ""
                                    } else {
                                        openFileAt(hit.path, hit.name)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChuText(
                                    glyphOf(hit.name, hit.isDir),
                                    style = typography.label,
                                    color = if (hit.isDir) colors.accent else colors.textMuted,
                                )
                                BasicText(
                                    text = highlightName(hit.name, query.trim(), colors.accent),
                                    style = typography.body.copy(color = colors.textPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                ChuText(
                                    if (hit.parentDir.isEmpty()) "/" else "${hit.parentDir}/",
                                    style = typography.labelSmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 110.dp),
                                )
                                ChuText(
                                    if (hit.isDir) "" else formatFileSize(hit.size),
                                    style = typography.labelSmall,
                                    color = colors.textMuted,
                                )
                                ChuText(
                                    if (hit.mtimeMs > 0) {
                                        SimpleDateFormat("dd/MM", Locale.US).format(Date(hit.mtimeMs))
                                    } else "",
                                    style = typography.labelSmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChuText("loading…", style = typography.label, color = colors.textMuted)
            }
            error != null && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChuText(error ?: "", style = typography.label, color = colors.textMuted)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.name }) { entry ->
                    val glyph = glyphOf(entry.name, entry.isDir)
                    // 30/9 (user chốt B): hàng phẳng, BỎ vạch hairline.
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

/** Nút tải lại listing — dùng chung hàng search và hàng path. */
@Composable
private fun ReloadButton(onReload: () -> Unit) {
    ChuButton(
        onClick = onReload,
        variant = ChuButtonVariant.Outlined,
        bracketed = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        ChuText("↻", style = ChuTypography.current.label)
    }
}

/** Glyph của hàng file — dùng chung cho listing portal và hàng kết quả search. */
private fun glyphOf(name: String, isDir: Boolean): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        isDir -> "/"
        ext in VIDEO_EXT -> "▶"
        ext in AUDIO_EXT -> "♪"
        ext in IMAGE_EXT -> "◻"
        ext == "apk" -> "⇩"
        else -> "·"
    }
}

/**
 * Tô accent các từ của [query] xuất hiện trong [name] (25/9) — giúp liếc mắt;
 * khớp hay không vẫn do chỉ mục qsrv quyết định, không phải luật ở đây.
 */
internal fun highlightName(name: String, query: String, accent: Color): AnnotatedString {
    val terms = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val marks = BooleanArray(name.length)
    for (t in terms) {
        var i = name.indexOf(t, ignoreCase = true)
        while (i >= 0) {
            for (j in i until (i + t.length).coerceAtMost(name.length)) marks[j] = true
            i = name.indexOf(t, startIndex = i + 1, ignoreCase = true)
        }
    }
    return buildAnnotatedString {
        var start = 0
        while (start < name.length) {
            val on = marks[start]
            var end = start
            while (end < name.length && marks[end] == on) end++
            val seg = name.substring(start, end)
            if (on) withStyle(SpanStyle(color = accent)) { append(seg) } else append(seg)
            start = end
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
