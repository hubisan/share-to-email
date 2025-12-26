package ch.hubisan.sharetoemail.logic

import java.net.HttpURLConnection
import java.net.URL

object TitleFetcher {
    fun fetchTitle(url: String, connectTimeoutMs: Int = 2500, readTimeoutMs: Int = 2500): String? {
        return try {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ShareToEmail/1.0")
            }
            conn.inputStream.use { input ->
                val bytes = input.readNBytes(64 * 1024)
                val html = bytes.toString(Charsets.UTF_8)
                val m = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
                m?.groupValues?.getOrNull(1)
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
