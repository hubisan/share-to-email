package ch.hubisan.sharetoemail.logic

import android.text.Html
import java.util.Locale
import androidx.core.net.toUri

data class EmailDraft(
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

object EmailComposer {

    private const val SUBJECT_MAX = 80
    private const val URL_SEP = " | "

    fun compose(parsed: ParsedShare, fetchedTitles: Map<String, String?>): EmailDraft {
        val links = parsed.urls.map { url ->
            val title = fetchedTitles[url]?.trim()?.takeIf { it.isNotBlank() }
            LinkItem(url = url, title = title)
        }

        val subject = buildSubject(parsed, links)
        val (htmlBody, textBody) = buildBodies(parsed, links)

        return EmailDraft(subject = subject, htmlBody = htmlBody, textBody = textBody)
    }

    private data class LinkItem(val url: String, val title: String?)

    // ---------- SUBJECT ----------

    private fun buildSubject(parsed: ParsedShare, links: List<LinkItem>): String {
        val prefix = subjectPrefix(parsed)
        val core = subjectCore(parsed, links)
        return ellipsize("$prefix $core", SUBJECT_MAX)
    }

    private fun subjectPrefix(parsed: ParsedShare): String {
        return when {
            parsed.urls.isNotEmpty() -> "[url]"
            parsed.attachments.isNotEmpty() && parsed.attachments.all { it.mimeType?.startsWith("image/") == true } -> "[img]"
            parsed.attachments.isNotEmpty() -> "[file]"
            parsed.rawText.isNotBlank() -> "[txt]"
            else -> "[file]"
        }
    }

    private fun subjectCore(parsed: ParsedShare, links: List<LinkItem>): String {
        return when {
            links.isNotEmpty() -> {
                // Wenn Title gefetcht wurde, verwende Title; sonst Domain. Mehrere mit " | " trennen.
                links.joinToString(URL_SEP) { li -> (li.title ?: domainOf(li.url)).trim() }
                    .ifBlank { "Link" }
            }

            parsed.rawText.isNotBlank() -> {
                parsed.rawText.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    ?: "Shared text"
            }

            parsed.attachments.isNotEmpty() -> {
                if (parsed.attachments.size == 1) {
                    parsed.attachments.first().displayName
                        ?: parsed.attachments.first().uri.lastPathSegment
                        ?: "Attachment"
                } else {
                    "${parsed.attachments.size} files"
                }
            }

            else -> "Shared content"
        }
    }

    // ---------- BODY (always list) ----------

    private fun buildBodies(parsed: ParsedShare, links: List<LinkItem>): Pair<String, String> {
        val itemsPlain = mutableListOf<String>()
        val itemsHtml = mutableListOf<String>()

        fun addItem(plain: String, html: String) {
            itemsPlain += plain
            itemsHtml += "<li>$html</li>"
        }

        val textLines = parsed.rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val attachmentLines = parsed.attachments.map { a ->
            val name = a.displayName ?: a.uri.lastPathSegment ?: a.uri.toString()
            val size = a.sizeBytes?.let { " (${formatBytes(it)})" }.orEmpty()
            name + size
        }

        if (links.isNotEmpty()) {
            // URLs first (always list)
            links.forEach { li ->
                val plain = if (!li.title.isNullOrBlank()) "${li.title} — ${li.url}" else li.url
                val html = if (!li.title.isNullOrBlank()) {
                    "${Html.escapeHtml(li.title)} — ${anchor(li.url)}"
                } else {
                    anchor(li.url)
                }
                addItem(plain, html)
            }

            // Then any extra text lines as bullets
            textLines.forEach { line ->
                addItem(line, Html.escapeHtml(line))
            }

            // Then attachments
            attachmentLines.forEach { line ->
                addItem(line, Html.escapeHtml(line))
            }
        } else {
            // No URLs: text bullets first
            textLines.forEach { line ->
                addItem(line, Html.escapeHtml(line))
            }

            // Then attachments
            attachmentLines.forEach { line ->
                addItem(line, Html.escapeHtml(line))
            }
        }

        if (itemsPlain.isEmpty()) {
            addItem("Shared content", "Shared content")
        }

        val textBody = itemsPlain.joinToString("\n") { "- $it" }
        val htmlBody = "<html><body><ul>${itemsHtml.joinToString("")}</ul></body></html>"

        return htmlBody to textBody
    }

    // ---------- helpers ----------

    private fun anchor(url: String): String {
        val u = Html.escapeHtml(url)
        return "<a href=\"$u\">$u</a>"
    }

    private fun domainOf(url: String): String {
        return try {
            val host = url.toUri().host ?: return url
            host.removePrefix("www.")
        } catch (_: Throwable) {
            url
        }
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
