package com.jossephus.chuchu.data.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dịch buzz bằng Gemini, dùng khoá API user dán trong Settings (27/9).
 *
 * Port từ vbook GeminiTranslator nhưng đi HttpURLConnection — kohi không có OkHttp.
 * Máy dịch thường dịch từng câu rời, chết với tiếng lóng mạng; LLM hiểu ngữ cảnh.
 * Khoá lấy miễn phí ở aistudio.google.com (KHÔNG phải gói Gemini Advanced).
 */
object GeminiTranslator {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    /** Thử tối đa ngần này model rồi thôi, khỏi dịch một bài mà gọi cả chục lượt. */
    private const val MAX_TRIES = 2

    /** Lúc đường cùng phải dò danh sách thật thì cũng chỉ thử ngần này. */
    private const val DISCOVERY_TRIES = 2

    /**
     * Hàng đợi model, xếp theo ĐO ĐẠC chứ không theo tên (vbook đo 3/9/2026:
     * gemini-flash-lite-latest 0,97s ngon; -flash-latest 503; -exp 503 sau 5,8s).
     * Bản thử nghiệm không bao giờ đứng đầu hàng.
     */
    private val PREFERRED = listOf(
        "gemini-flash-lite-latest",
        "gemini-flash-latest",
        "gemini-pro-latest",
    )

    @Volatile
    private var cachedModel: String? = null

    @Volatile
    private var discovered = false

    fun buildPrompt(text: String, targetName: String): String =
        "Dịch đoạn dưới sang $targetName, giọng tự nhiên, dễ đọc.\n" +
            "Quy tắc:\n" +
            "- Giữ nguyên @tên_tài_khoản, #thẻ, đường dẫn, emoji và cách xuống dòng.\n" +
            "- Tiếng lóng mạng thì dịch theo Ý, đừng dịch từng chữ.\n" +
            "- Giữ đúng mức trang trọng của bản gốc: đừng chêm tiếng lóng vùng miền\n" +
            "  hay xưng hô suồng sã nếu bản gốc không có.\n" +
            "- CHỈ trả về bản dịch, không thêm lời dẫn, không giải thích, không đóng ngoặc kép.\n" +
            "- Đoạn vốn đã là $targetName thì trả lại y nguyên.\n\n" +
            text

    /** Bóc chữ từ phản hồi `generateContent`. */
    fun parseContent(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val parts = JSONObject(body)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            sb.toString().trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Danh sách model để thử lần lượt. KHÔNG tin `ListModels` (vbook đo 2/9/2026:
     * khai gemini-2.5-flash nhưng gọi tới thì 404), nên thử thật, hỏng thì tụt xuống.
     * Ưu tiên bí danh `-latest` vì sống qua các lần đổi tên.
     */
    fun rankModels(listBody: String): List<String> {
        val usable = mutableListOf<String>()
        try {
            val models = JSONObject(listBody).optJSONArray("models") ?: JSONArray()
            for (i in 0 until models.length()) {
                val m = models.optJSONObject(i) ?: continue
                val name = m.optString("name").removePrefix("models/")
                if (!name.startsWith("gemini")) continue
                if (name.contains("tts") || name.contains("image") || name.contains("embedding")) continue
                if (name.contains("exp") || name.contains("high-res")) continue
                val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                var ok = false
                for (k in 0 until methods.length()) if (methods.optString(k) == "generateContent") ok = true
                if (ok) usable.add(name)
            }
        } catch (e: Exception) {
            // danh sách hỏng thì vẫn còn mấy cái mặc định bên dưới
        }
        val ranked = usable.sortedWith(
            compareByDescending<String> { it.contains("latest") }
                .thenByDescending { !it.contains("preview") }
                .thenByDescending { it.contains("flash") }
                .thenByDescending { versionKey(it) },
        )
        return (ranked + PREFERRED).distinct()
    }

    /** "gemini-2.5-flash" -> 25; để so bản nào mới hơn. */
    private fun versionKey(name: String): Int {
        val m = Regex("gemini-(\\d+)\\.?(\\d*)").find(name) ?: return 0
        val major = m.groupValues[1].toIntOrNull() ?: 0
        val minor = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 10 + minor
    }

    /** Dịch một đoạn; khoá sai hay hết hạn mức thì trả null để UI giữ nguyên bản gốc. */
    fun translate(apiKey: String, text: String): String? {
        if (apiKey.isBlank() || text.isBlank()) return null
        val payload = buildPayload(text)

        val queue = buildList {
            cachedModel?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(PREFERRED)
        }.distinct().take(MAX_TRIES)

        for (model in queue) {
            call(apiKey, model, payload)?.let { return remember(model, it) }
            // Model đang nhớ mà hỏng thì quên đi để lần sau dò lại từ đầu.
            if (cachedModel == model) cachedModel = null
        }

        // Cả hàng đợi cứng đều hỏng — có thể Google vừa đổi tên model. Lúc đó mới
        // dò danh sách thật, và chỉ ĐÚNG MỘT LẦN mỗi lượt chạy app.
        if (discovered) return null
        discovered = true
        for (model in discoverModels(apiKey).take(DISCOVERY_TRIES)) {
            if (model in queue) continue
            call(apiKey, model, payload)?.let { return remember(model, it) }
        }
        return null
    }

    private fun buildPayload(text: String): String =
        JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", buildPrompt(text, "tiếng Việt"))),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().put("temperature", 0.3).put("maxOutputTokens", 2048),
            )
        }.toString()

    private fun remember(model: String, out: String): String {
        cachedModel = model
        return out
    }

    /** Một lượt gọi; 404/503 đều trả null để nhường cho model kế. */
    private fun call(apiKey: String, model: String, payload: String): String? = runCatching {
        val conn = (URL("$BASE/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            parseContent(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            // Khong disconnect(): giu keep-alive (cung ly do RemoteLogo/DbtopClient).
            runCatching { conn.errorStream?.close() }
        }
    }.getOrNull()

    private fun discoverModels(apiKey: String): List<String> = runCatching {
        val conn = (URL("$BASE/models").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) "" else
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.errorStream?.close() }
        }
    }.getOrDefault("").let(::rankModels)
}
