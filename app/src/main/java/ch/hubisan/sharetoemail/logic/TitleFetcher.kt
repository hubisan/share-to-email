package ch.hubisan.sharetoemail.logic

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object TitleFetcher {

    private const val MAX_READ_BYTES = 64 * 1024 // 64 KB

    fun fetchTitle(
        url: String,
        connectTimeoutMs: Int = 2500,
        readTimeoutMs: Int = 2500
    ): String? {
        return try {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ShareToEmail/1.0")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }

            try {
                conn.inputStream.use { input ->
                    val bytes = readUpToLimit(input)
                    val html = bytes.toString(Charsets.UTF_8)

                    Regex("(?is)<title[^>]*>(.*?)</title>")
                        .find(html)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readUpToLimit(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream(MAX_READ_BYTES)
        var total = 0

        while (total < MAX_READ_BYTES) {
            val toRead = minOf(buffer.size, MAX_READ_BYTES - total)
            val n = input.read(buffer, 0, toRead)
            if (n <= 0) break
            out.write(buffer, 0, n)
            total += n
        }

        return out.toByteArray()
    }
}
