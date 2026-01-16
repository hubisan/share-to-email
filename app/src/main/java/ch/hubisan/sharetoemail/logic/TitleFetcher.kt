package ch.hubisan.sharetoemail.logic

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object TitleFetcher {

    private const val MAX_CONCURRENCY = 4
    // WICHTIG: Limit erhöht auf 4 MB. 256 KB ist für moderne Seiten oft zu wenig,
    // da riesige Inline-CSS/JS Blöcke den Head aufblähen.
    private const val MAX_BYTES = 4 * 1024 * 1024L

    // Wir tarnen uns als "echter" Chrome Browser auf Android, um nicht von Firewalls geblockt zu werden.
    private const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        // Timeouts leicht erhöht, da wir jetzt mehr Daten laden könnten
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        // CookieJar könnte hier helfen, falls Seiten Cookies erzwingen,
        // aber für einfache Titel meist overkill.
        .build()

    suspend fun fetchTitles(urls: List<String>): Map<String, String?> = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope emptyMap()

        val sem = Semaphore(MAX_CONCURRENCY)

        urls.distinct().map { url ->
            async {
                sem.withPermit {
                    // runCatching ist super, fängt auch Timeouts ab
                    url to runCatching { fetchOne(url) }.getOrNull()
                }
            }
        }.awaitAll().toMap()
    }

    private fun fetchOne(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            // Akzeptiere explizit HTML
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "de-CH,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache") // Versuch, frische Daten zu bekommen
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null

            val ctype = resp.header("Content-Type")?.lowercase().orEmpty()
            val looksLikeHtml = ctype.contains("text/html") || ctype.contains("application/xhtml")

            // Manche Server senden keinen Content-Type Header oder falsche Header.
            // Wenn wir unsicher sind, schauen wir trotzdem rein, ausser es ist offensichtlich ein Bild/PDF.
            if (!looksLikeHtml && (ctype.contains("image/") || ctype.contains("application/pdf"))) {
                return null
            }

            val body = resp.body ?: return null

            // Kleiner Trick: peekBody lädt Daten in den Speicher, aber OkHttp macht das effizient.
            // Aber bei deinem Stream-Ansatz bleiben wir lieber beim Stream, um Speicher zu sparen.
            body.byteStream().use { raw ->
                val limited = LimitedInputStream(raw, MAX_BYTES)

                // Wir übergeben die URL, damit Jsoup relative Links auflösen kann (wichtig für Favicons etc., hier weniger)
                val doc: Document = Jsoup.parse(limited, null, url)
                return extractBestTitle(doc)
            }
        }
    }

    private fun extractBestTitle(doc: Document): String? {
        fun clean(s: String?): String? =
            s?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }

        // 1. OpenGraph (Facebook/Social Media) - oft am besten formatiert
        val ogTitle = clean(doc.selectFirst("meta[property=og:title]")?.attr("content"))
        if (ogTitle != null) return ogTitle

        // 2. Twitter Cards
        val twTitle = clean(doc.selectFirst("meta[name=twitter:title]")?.attr("content"))
        if (twTitle != null) return twTitle

        // 3. Der klassische HTML Titel
        val title = clean(doc.title())
        if (title != null) return title

        // 4. Schema.org JSON-LD (Advanced Fallback)
        // Viele moderne Seiten nutzen JSON-LD für Google. Das ist etwas komplexer zu parsen mit Regex,
        // aber wir können schauen, ob wir "headline" finden.
        // (Hier vereinfacht weggelassen, da fehleranfällig ohne JSON Parser)

        // 5. Fallback: H1
        val h1 = clean(doc.selectFirst("h1")?.text())
        if (h1 != null) return h1

        return null
    }

    /**
     * Stellt sicher, dass wir nicht 100MB grosse Dateien laden, falls der Server
     * uns versehentlich eine ISO-Datei als HTML verkauft.
     */
    private class LimitedInputStream(
        input: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(input) {

        private var count: Long = 0

        override fun read(): Int {
            if (count >= maxBytes) return -1
            val r = super.read()
            if (r != -1) count++
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (count >= maxBytes) return -1
            val remaining = (maxBytes - count).toInt()
            val toRead = minOf(len, remaining)
            val r = super.read(b, off, toRead)
            if (r > 0) count += r.toLong()
            return r
        }
    }
}