package ch.hubisan.sharetoemail.logic

import android.net.Uri
import android.text.Html
import androidx.core.net.toUri

data class EmailDraft(
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

object EmailComposer {

    private const val SUBJECT_MAX = 160
    private const val ITEM_MAX = 40
    private const val SUBJECT_SEP = " | "

    /** Bullet prefix used for list items (URLs/attachments). */
    private const val BULLET = "– "

    fun compose(parsed: ParsedShare, fetchedTitles: Map<String, String?>): EmailDraft {
        // Wir nutzen die Heuristik aus dem Rohtext nur, wenn wir gar keine anderen Titel haben.
        // Das verhindert, dass wir falsche Titel raten, wenn wir schon echte haben.
        val inferredTitlesByUrl: Map<String, String?> =
            if (fetchedTitles.values.all { it.isNullOrBlank() }) {
                inferTitlesFromRawText(parsed.rawText, parsed.urls)
            } else {
                emptyMap()
            }

        val links = parsed.urls.map { url ->
            val fetched = fetchedTitles[url]?.trim()?.takeIf { it.isNotBlank() }
            val inferred = inferredTitlesByUrl[url]?.trim()?.takeIf { it.isNotBlank() }

            // WICHTIG: Falls der Titel exakt der URL entspricht, behandeln wir ihn als null,
            // damit später keine "URL URL" Dopplung entsteht.
            val finalTitle = (fetched ?: inferred)?.takeIf {
                !it.equals(url, ignoreCase = true) && !it.equals(url.removeSuffix("/"), ignoreCase = true)
            }

            LinkItem(url = url, title = finalTitle)
        }

        val subject = buildSubject(parsed, links)
        val (textBody, htmlBody) = buildBodies(parsed, links)

        return EmailDraft(subject = subject, htmlBody = htmlBody, textBody = textBody)
    }

    private data class LinkItem(val url: String, val title: String?)

    // ---------- SUBJECT ----------

    private fun buildSubject(parsed: ParsedShare, links: List<LinkItem>): String {
        val prefix = subjectPrefix(parsed)
        val remaining = (SUBJECT_MAX - (prefix.length + 1)).coerceAtLeast(0)

        val core = when {
            parsed.kind == ShareKind.TEXT_URL && links.isNotEmpty() ->
                subjectForLinks(links, remaining)

            parsed.kind == ShareKind.ATTACHMENT && parsed.attachments.isNotEmpty() ->
                subjectForAttachments(parsed, remaining)

            parsed.rawText.isNotBlank() ->
                subjectForText(parsed, remaining)

            else ->
                "Shared content".take(remaining)
        }

        return ellipsize("$prefix $core", SUBJECT_MAX)
    }

    private fun subjectPrefix(parsed: ParsedShare): String = when (parsed.kind) {
        ShareKind.TEXT_URL -> "[url]"
        ShareKind.TEXT_PLAIN -> "[txt]"
        ShareKind.TEXT_UNDEFINED -> "[txt]"
        ShareKind.ATTACHMENT -> {
            val allImages = parsed.attachments.isNotEmpty() &&
                    parsed.attachments.all { it.mimeType?.startsWith("image/") == true }
            if (allImages) "[img]" else "[file]"
        }
    }

    private fun subjectForLinks(links: List<LinkItem>, remaining: Int): String {
        fun labelSingle(li: LinkItem): String =
            (li.title ?: li.url).trim().ifBlank { li.url }

        return if (links.size == 1) {
            ellipsize(labelSingle(links.first()), remaining)
        } else {
            joinWithinLimit(
                parts = links.map { li ->
                    val base = (li.title ?: domainOf(li.url)).trim().ifBlank { domainOf(li.url) }
                    ellipsize(base, ITEM_MAX)
                },
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

    // ---------- BODY ----------

    private fun buildBodies(parsed: ParsedShare, links: List<LinkItem>): Pair<String, String> {
        val sb = StringBuilder()

        fun appendLine(s: String = "") {
            sb.append(s).append("\n")
        }

        fun ensureBlankLine() {
            if (sb.isEmpty()) return
            if (!sb.endsWith("\n")) appendLine()
            if (!sb.endsWith("\n\n")) appendLine()
        }

        // 1) URLs: bullet format
        if (parsed.kind == ShareKind.TEXT_URL && links.isNotEmpty()) {
            links.forEach { li ->
                val title = li.title?.trim().orEmpty()
                val url = li.url.trim()

                // "genau nicht Title Titel":
                // Da wir oben in compose() bereits sichergestellt haben, dass li.title
                // nicht identisch mit der URL ist, können wir hier sicher zusammenfügen.
                val line = if (title.isNotBlank()) {
                    "$title\n$url" // Titel in einer Zeile, URL direkt darunter
                } else {
                    url
                }

                appendLine(BULLET + line)
                appendLine()
            }

            while (sb.endsWith("\n\n")) sb.setLength(sb.length - 1)
            if (sb.endsWith("\n")) sb.setLength(sb.length - 1)
        } else {
            // 2) Not a URL-share
            if (parsed.rawText.isNotEmpty()) {
                sb.append(parsed.rawText)
                if (!parsed.rawText.endsWith("\n")) appendLine()
            }
        }

        // 3) Attachments
        if (parsed.attachments.isNotEmpty()) {
            ensureBlankLine()
            parsed.attachments.forEach { a ->
                appendLine(BULLET + attachmentFileNameOnly(a.displayName, a.uri))
            }
        }

        val rawBody = sb.toString()
        val textBody = normalizeBodyEdges(rawBody)
        val htmlBody = Html.escapeHtml(textBody).replace("\n", "<br/>")

        return textBody to htmlBody
    }

    private fun normalizeBodyEdges(s: String): String {
        if (s.isEmpty()) return s
        var start = 0
        var end = s.length
        while (start < end && s[start] == '\n') start++
        while (end > start && s[end - 1] == '\n') end--
        return s.substring(start, end)
    }

    private fun attachmentFileNameOnly(displayName: String?, uri: Uri): String {
        val raw = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: uri.toString()
        return raw.substringAfterLast('/').substringAfterLast('\\')
    }

    // ---------- Title inference ----------

    private fun inferTitlesFromRawText(rawText: String, urls: List<String>): Map<String, String?> {
        if (rawText.isBlank() || urls.isEmpty()) return emptyMap()

        val lines = rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        fun isUrlLine(s: String): Boolean =
            s.startsWith("http://", true) || s.startsWith("https://", true)

        val nonUrlLines = lines.filterNot { isUrlLine(it) }

        if (urls.size == 1) {
            val t = nonUrlLines.firstOrNull()?.let { cleanupTitle(it) }
            return mapOf(urls.first() to t)
        }
        return emptyMap()
    }

    private fun cleanupTitle(s: String): String {
        return s.replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('•', '-', '–', '—')
            .trim()
    }

    private fun domainOf(url: String): String {
        return try {
            val host = url.toUri().host ?: return url
            host.removePrefix("www.")
        } catch (_: Throwable) {
            url
        }
    }

    private fun joinWithinLimit(parts: List<String>, maxLen: Int): String {
        if (maxLen <= 0) return ""
        val out = StringBuilder()
        for (p in parts) {
            if (out.isEmpty()) {
                if (p.length > maxLen) return ellipsize(p, maxLen)
                out.append(p)
            } else {
                val candidateLen = out.length + SUBJECT_SEP.length + p.length
                if (candidateLen > maxLen) break
                out.append(SUBJECT_SEP).append(p)
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
}