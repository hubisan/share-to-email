package ch.hubisan.sharetoemail.logic

import android.text.Html
import androidx.core.net.toUri
import java.util.Locale

data class EmailDraft(
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

object EmailComposer {

    private const val SUBJECT_MAX = 160
    private const val ITEM_MAX = 40
    private const val SEP = " | "

    fun compose(parsed: ParsedShare, fetchedTitles: Map<String, String?>): EmailDraft {
        // Der Share-Source (z.B. Firefox) liefert oft Titel/Brand im Text.
        // Wir versuchen, Titles ohne Netz-Fetch zuzuordnen:
        // - Single URL: erster Nicht-URL-Text
        // - Multi URL: letzte Nicht-URL-Zeile, kommagetrennt, Reihenfolge = URL-Reihenfolge
        val inferredTitlesByUrl: Map<String, String?> =
            inferTitlesFromRawText(parsed.rawText, parsed.urls)

        val links = parsed.urls.map { url ->
            val fetched = fetchedTitles[url]?.trim()?.takeIf { it.isNotBlank() }
            val inferred = inferredTitlesByUrl[url]?.trim()?.takeIf { it.isNotBlank() }
            LinkItem(url = url, title = fetched ?: inferred)
        }

        val subject = buildSubject(parsed, links)
        val (htmlBody, textBody) = buildBodies(parsed, links)

        return EmailDraft(subject = subject, htmlBody = htmlBody, textBody = textBody)
    }

    private data class LinkItem(val url: String, val title: String?)

    // ---------- SUBJECT ----------

    private fun buildSubject(parsed: ParsedShare, links: List<LinkItem>): String {
        val prefix = subjectPrefix(parsed)
        val remaining = (SUBJECT_MAX - (prefix.length + 1)).coerceAtLeast(0)

        val core = when {
            links.isNotEmpty() -> subjectForLinks(links, remaining)
            parsed.attachments.isNotEmpty() -> subjectForAttachments(parsed, remaining)
            parsed.rawText.isNotBlank() -> subjectForText(parsed, remaining)
            else -> "Shared content".take(remaining)
        }

        return ellipsize("$prefix $core", SUBJECT_MAX)
    }

    private fun subjectPrefix(parsed: ParsedShare): String = when {
        parsed.urls.isNotEmpty() -> "[url]"
        parsed.attachments.isNotEmpty() && parsed.attachments.all { it.mimeType?.startsWith("image/") == true } -> "[img]"
        parsed.attachments.isNotEmpty() -> "[file]"
        parsed.rawText.isNotBlank() -> "[txt]"
        else -> "[file]"
    }

    private fun subjectForLinks(links: List<LinkItem>, remaining: Int): String {
        fun labelSingle(li: LinkItem): String =
            (li.title ?: li.url).trim().ifBlank { li.url }

        return if (links.size == 1) {
            // 1 Link: Title oder URL (nicht nur Domain)
            ellipsize(labelSingle(links.first()), remaining)
        } else {
            // mehrere: Title falls vorhanden, sonst Domain; je max 40; bis Subject voll ist
            joinWithinLimit(
                parts = links.map { li ->
                    val base = (li.title ?: domainOf(li.url)).trim().ifBlank { domainOf(li.url) }
                    ellipsize(base, ITEM_MAX)
                },
                sep = SEP,
                maxLen = remaining
            )
        }
    }

    private fun subjectForAttachments(parsed: ParsedShare, remaining: Int): String {
        val names = parsed.attachments.map { a ->
            a.displayName ?: a.uri.lastPathSegment ?: a.uri.toString()
        }

        return if (names.size == 1) {
            ellipsize(names.first(), remaining)
        } else {
            joinWithinLimit(
                parts = names.map { ellipsize(it, ITEM_MAX) },
                sep = SEP,
                maxLen = remaining
            )
        }
    }

    private fun subjectForText(parsed: ParsedShare, remaining: Int): String {
        val firstLine = parsed.rawText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "Shared text"
        return ellipsize(firstLine, remaining)
    }

    // ---------- BODY (always list) ----------

    private fun buildBodies(parsed: ParsedShare, links: List<LinkItem>): Pair<String, String> {
        val itemsPlain = mutableListOf<String>()
        val itemsHtml = mutableListOf<String>()

        fun addItem(plain: String, html: String) {
            itemsPlain += plain
            itemsHtml += "<li>$html</li>"
        }

        val attachmentLines = parsed.attachments.map { a ->
            val name = a.displayName ?: a.uri.lastPathSegment ?: a.uri.toString()
            val size = a.sizeBytes?.let { " (${formatBytes(it)})" }.orEmpty()
            name + size
        }

        if (links.isNotEmpty()) {
            // WICHTIG: Wenn URLs vorhanden sind, NICHT zusätzlich rawText als Bullets ausgeben,
            // weil Firefox/Browser sonst Titel/Brand/URL mehrfach liefern (das war dein "Durcheinander").
            links.forEach { li ->
                val text = (li.title ?: li.url).trim().ifBlank { li.url }

                // Plain: Title [URL] oder URL
                val plain = if (!li.title.isNullOrBlank()) "${li.title} [${li.url}]" else li.url

                // HTML exakt wie gewünscht:
                // - Title vorhanden: <a href="URL">Title</a>
                // - sonst:          <a href="URL">URL</a>
                val html = anchor(li.url, text)

                addItem(plain, html)
            }
        } else {
            // Keine URLs: Text als Bullets
            val textLines = parsed.rawText
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

            textLines.forEach { line ->
                addItem(line, Html.escapeHtml(line))
            }
        }

        // Attachments immer hinten dran
        attachmentLines.forEach { line ->
            addItem(line, Html.escapeHtml(line))
        }

        if (itemsPlain.isEmpty()) {
            addItem("Shared content", "Shared content")
        }

        val textBody = itemsPlain.joinToString("\n") { "• $it" }
        val htmlBody = "<ul style=\"margin:0;padding-left:18px;\">${itemsHtml.joinToString("")}</ul>"

        return htmlBody to textBody
    }

    // ---------- Title inference (no fetch) ----------

    private fun inferTitlesFromRawText(rawText: String, urls: List<String>): Map<String, String?> {
        if (rawText.isBlank() || urls.isEmpty()) return emptyMap()

        val lines = rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        fun isUrlLine(s: String): Boolean =
            s.startsWith("http://", true) || s.startsWith("https://", true)

        val nonUrlLines = lines.filterNot { isUrlLine(it) }

        // Single URL: take first non-url line as title candidate
        if (urls.size == 1) {
            val t = nonUrlLines.firstOrNull()?.let { cleanupTitle(it) }
            return mapOf(urls.first() to t)
        }

        // Multi URL: Firefox often appends a single comma-separated title list line
        val last = nonUrlLines.lastOrNull().orEmpty()
        val parts = last.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (parts.size >= urls.size) {
            urls.mapIndexed { idx, url -> url to cleanupTitle(parts[idx]) }.toMap()
        } else {
            emptyMap()
        }
    }

    private fun cleanupTitle(s: String): String {
        // minimal cleanup: collapse whitespace, strip leading bullets/quotes
        return s
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('•', '-', '–', '—')
            .trim()
    }

    // ---------- helpers ----------

    private fun anchor(url: String, text: String): String {
        val u = Html.escapeHtml(url)
        val t = Html.escapeHtml(text)
        return "<a href=\"$u\">$t</a>"
    }

    private fun domainOf(url: String): String {
        return try {
            val host = url.toUri().host ?: return url
            host.removePrefix("www.")
        } catch (_: Throwable) {
            url
        }
    }

    private fun joinWithinLimit(parts: List<String>, sep: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        val out = StringBuilder()
        for (p in parts) {
            if (out.isEmpty()) {
                if (p.length > maxLen) return ellipsize(p, maxLen)
                out.append(p)
            } else {
                val candidateLen = out.length + sep.length + p.length
                if (candidateLen > maxLen) break
                out.append(sep).append(p)
            }
        }
        if (out.isEmpty() && parts.isNotEmpty()) return ellipsize(parts.first(), maxLen)
        return out.toString()
    }

    private fun ellipsize(s: String, max: Int): String {
        val t = s.trim()
        if (t.length <= max) return t
        if (max <= 1) return "…"
        return t.take(max - 1).trimEnd() + "…"
    }

    private fun formatBytes(b: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            b >= gb -> String.format(Locale.US, "%.1f GB", b / gb)
            b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
            b >= kb -> String.format(Locale.US, "%.1f KB", b / kb)
            else -> "$b B"
        }
    }
}
