package ch.hubisan.sharetoemail.logic

import android.text.Html

data class EmailDraft(
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

object EmailComposer {

    fun compose(parsed: ParsedShare, fetchedTitles: Map<String, String?>): EmailDraft {
        val links = parsed.urls.map { url ->
            val title = fetchedTitles[url]?.takeIf { it.isNotBlank() }
            LinkItem(url = url, title = title)
        }

        val subject = buildSubject(parsed, links)
        val (htmlBody, textBody) = buildBodies(parsed, links)

        return EmailDraft(subject = subject, htmlBody = htmlBody, textBody = textBody)
    }

    private data class LinkItem(val url: String, val title: String?)

    private fun buildSubject(parsed: ParsedShare, links: List<LinkItem>): String {
        val maxLen = 120
        val minLen = 40

        val firstLine = parsed.rawText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(80)

        val linkParts = links.take(2).map { li ->
            li.title?.take(60) ?: domainOf(li.url)
        }.filter { it.isNotBlank() }

        val extraCount = (links.size - linkParts.size).takeIf { it > 0 }?.let { "+$it" }

        val parts = mutableListOf<String>()
        firstLine?.let { parts += it }
        parts += linkParts
        extraCount?.let { parts += it }

        if (parts.isEmpty()) return "Shared item"

        val joined = joinWithFairTrim(parts, " · ", maxLen)

        if (joined.length < minLen) {
            val extra = parsed.rawText.replace("\n", " ").trim()
            val need = minLen - joined.length
            val add = extra.drop(joined.length).trim().take(need + 10)
            val boosted = (joined + " " + add).trim()
            return boosted.take(maxLen).trim().trimEnd('·', '-', '—')
        }

        return joined
    }

    private fun joinWithFairTrim(parts: List<String>, sep: String, maxLen: Int): String {
        val raw = parts.joinToString(sep)
        if (raw.length <= maxLen) return raw

        val sepLen = sep.length * (parts.size - 1)
        val budget = (maxLen - sepLen).coerceAtLeast(1)

        val base = budget / parts.size
        var rem = budget % parts.size

        val trimmed = parts.map { p ->
            val allowance = base + if (rem > 0) { rem--; 1 } else 0
            val s = p.trim()
            if (s.length <= allowance) s
            else s.take((allowance - 1).coerceAtLeast(1)) + "…"
        }

        return trimmed.joinToString(sep).take(maxLen)
    }

    private fun buildBodies(parsed: ParsedShare, links: List<LinkItem>): Pair<String, String> {
        val sbText = StringBuilder()
        val sbHtml = StringBuilder()

        if (parsed.rawText.isNotBlank()) {
            sbText.append(parsed.rawText.trim()).append("\n\n")
        }

        if (links.isNotEmpty()) {
            sbText.append("Links:\n")
            links.forEachIndexed { idx, li ->
                val label = li.title?.let { "$it — " } ?: ""
                sbText.append("${idx + 1}. ").append(label).append(li.url).append("\n")
            }
            sbText.append("\n")
        }

        if (parsed.attachments.isNotEmpty()) {
            sbText.append("Attachments:\n")
            parsed.attachments.forEachIndexed { idx, a ->
                val name = a.displayName ?: a.uri.lastPathSegment ?: a.uri.toString()
                val size = a.sizeBytes?.let { " (${formatBytes(it)})" } ?: ""
                sbText.append("${idx + 1}. ").append(name).append(size).append("\n")
            }
            sbText.append("\n")
        }

        sbHtml.append("<html><body style=\"font-family:sans-serif;\">")
        if (parsed.rawText.isNotBlank()) {
            sbHtml.append("<p>")
            sbHtml.append(Html.escapeHtml(parsed.rawText.trim()).replace("\n", "<br/>"))
            sbHtml.append("</p>")
        }

        if (links.isNotEmpty()) {
            sbHtml.append("<h3>Links</h3><ol>")
            links.forEach { li ->
                val title = li.title?.let { Html.escapeHtml(it) }
                val urlEsc = Html.escapeHtml(li.url)
                sbHtml.append("<li>")
                if (!title.isNullOrBlank()) sbHtml.append(title).append(" — ")
                sbHtml.append("<a href=\"").append(urlEsc).append("\">").append(urlEsc).append("</a>")
                sbHtml.append("</li>")
            }
            sbHtml.append("</ol>")
        }

        if (parsed.attachments.isNotEmpty()) {
            sbHtml.append("<h3>Attachments</h3><ol>")
            parsed.attachments.forEach { a ->
                val name = Html.escapeHtml(a.displayName ?: a.uri.lastPathSegment ?: a.uri.toString())
                val size = a.sizeBytes?.let { " (${formatBytes(it)})" } ?: ""
                sbHtml.append("<li>").append(name).append(Html.escapeHtml(size)).append("</li>")
            }
            sbHtml.append("</ol>")
        }

        sbHtml.append("</body></html>")

        return sbHtml.toString() to sbText.toString().trimEnd()
    }

    private fun domainOf(url: String): String {
        return try {
            url.removePrefix("https://")
                .removePrefix("http://")
                .takeWhile { it != '/' }
                .lowercase()
        } catch (_: Throwable) { "" }
    }

    private fun formatBytes(b: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            b >= gb -> String.format("%.1f GB", b / gb)
            b >= mb -> String.format("%.1f MB", b / mb)
            b >= kb -> String.format("%.1f KB", b / kb)
            else -> "$b B"
        }
    }
}
