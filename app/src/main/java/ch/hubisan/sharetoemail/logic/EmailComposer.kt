package ch.hubisan.sharetoemail.logic

import android.net.Uri
import android.text.Html
import androidx.core.net.toUri

/**
 * Represents the final email draft data used by the share activity.
 *
 * Notes:
 * - Many mail clients (notably Gmail when opened via intent) do not reliably render HTML.
 *   Therefore, [textBody] is the primary body format we rely on.
 *
 * @property subject The email subject line.
 * @property htmlBody A minimal HTML representation (best-effort for clients that render it).
 * @property textBody The plain text body (primary).
 */
data class EmailDraft(
    val subject: String,
    val htmlBody: String,
    val textBody: String
)

/**
 * Builds an [EmailDraft] from parsed share input and optional fetched titles.
 *
 * Body rules (textBody is the primary output):
 * - Always use bullet points (even for a single item).
 * - For URLs:
 *     • Title URL
 *   or:
 *     • URL
 *   and add an empty line after each URL item (for readability).
 * - For attachments:
 *     • image1.png
 *     • file1.pdf
 *   i.e., file name + extension only (no size).
 *
 * HTML output is best-effort and mirrors the text content.
 */
object EmailComposer {

    /** Maximum length for the subject, applied after prefix + content are combined. */
    private const val SUBJECT_MAX = 160

    /** Maximum length of a single item label when building multi-item subjects. */
    private const val ITEM_MAX = 40

    /** Separator used when concatenating multiple short labels into a subject. */
    private const val SUBJECT_SEP = " | "

    /** Bullet prefix used for every item line. */
    private const val BULLET = "— "

    /**
     * Composes an [EmailDraft] from parsed share data.
     *
     * Title selection priority per URL:
     * 1) fetchedTitles[url] if present and non-blank
     * 2) inferred title from raw share text (some apps include titles in the share payload)
     * 3) no title (URL only)
     *
     * @param parsed The already parsed share content (urls, attachments, rawText).
     * @param fetchedTitles Optional mapping URL -> fetched title (e.g. via network).
     * @return A complete email draft.
     */
    fun compose(parsed: ParsedShare, fetchedTitles: Map<String, String?>): EmailDraft {
        val inferredTitlesByUrl: Map<String, String?> =
            inferTitlesFromRawText(parsed.rawText, parsed.urls)

        val links = parsed.urls.map { url ->
            val fetched = fetchedTitles[url]?.trim()?.takeIf { it.isNotBlank() }
            val inferred = inferredTitlesByUrl[url]?.trim()?.takeIf { it.isNotBlank() }
            LinkItem(url = url, title = fetched ?: inferred)
        }

        val subject = buildSubject(parsed, links)

        // Gmail via intents often ignores HTML; textBody is the source of truth.
        val (textBody, htmlBody) = buildBodies(parsed, links)

        return EmailDraft(subject = subject, htmlBody = htmlBody, textBody = textBody)
    }

    /**
     * Internal representation of a shared URL with an optional title.
     */
    private data class LinkItem(val url: String, val title: String?)

    // ---------- SUBJECT ----------

    /**
     * Builds the email subject with a small prefix to indicate the content type.
     *
     * Subject is limited to [SUBJECT_MAX] characters.
     *
     * @param parsed Parsed share data.
     * @param links List of extracted link items.
     * @return A subject line suitable for email clients.
     */
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

    /**
     * Chooses a prefix that hints at the type of shared content.
     *
     * @param parsed Parsed share data.
     * @return A short prefix like "[url]" or "[file]".
     */
    private fun subjectPrefix(parsed: ParsedShare): String = when {
        parsed.urls.isNotEmpty() -> "[url]"
        parsed.attachments.isNotEmpty() && parsed.attachments.all { it.mimeType?.startsWith("image/") == true } -> "[img]"
        parsed.attachments.isNotEmpty() -> "[file]"
        parsed.rawText.isNotBlank() -> "[txt]"
        else -> "[file]"
    }

    /**
     * Builds a subject for link shares.
     *
     * Behavior:
     * - Single link: use title if present; otherwise full URL (not just domain).
     * - Multiple links: use title if present; otherwise domain; keep each label short and
     *   pack as many as fit.
     *
     * @param links Link items.
     * @param remaining Available character budget (excluding prefix).
     * @return Subject core for links.
     */
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

    /**
     * Builds a subject for attachment shares.
     *
     * @param parsed Parsed share data.
     * @param remaining Available character budget.
     * @return Subject core based on attachment file names.
     */
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

    /**
     * Builds a subject for pure text shares.
     *
     * @param parsed Parsed share data.
     * @param remaining Available character budget.
     * @return Subject core based on the first non-empty line of raw text.
     */
    private fun subjectForText(parsed: ParsedShare, remaining: Int): String {
        val firstLine = parsed.rawText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "Shared text"
        return ellipsize(firstLine, remaining)
    }

    // ---------- BODY ----------

    /**
     * Builds both bodies:
     * - textBody: primary content for Gmail/intent flows
     * - htmlBody: best-effort minimal HTML for clients that render it
     *
     * Rules:
     * - Always bullet items (even if only one item).
     * - URLs:
     *     • Title URL
     *   or:
     *     • URL
     *   and add an empty line after each URL entry for readability.
     * - Attachments:
     *     • image1.png
     *     • file1.pdf
     *   (file name + extension only; no file size).
     * - If there are no URLs and no attachments: bullet each non-empty raw text line.
     *
     * Attachments are always appended after URLs/text when both exist.
     *
     * @param parsed Parsed share data.
     * @param links Link items extracted from URLs.
     * @return Pair(textBody, htmlBody)
     */
    private fun buildBodies(parsed: ParsedShare, links: List<LinkItem>): Pair<String, String> {
        val outLines = mutableListOf<String>()

        fun addBulletLine(line: String) {
            outLines += (BULLET + line)
        }

        fun addBlankLine() {
            outLines += ""
        }

        if (links.isNotEmpty()) {
            links.forEach { li ->
                val title = li.title?.trim().orEmpty()
                val url = li.url.trim()

                // No dash between title and URL (as requested).
                val line = if (title.isNotBlank() && !title.equals(url, ignoreCase = true)) {
                    "$title $url"
                } else {
                    url
                }

                addBulletLine(line)
                addBlankLine() // extra empty line after each URL
            }

            // Remove trailing blank lines
            while (outLines.isNotEmpty() && outLines.last().isBlank()) {
                outLines.removeAt(outLines.lastIndex)
            }
        } else if (parsed.attachments.isNotEmpty()) {
            // Attachments-only share: still bullets
            parsed.attachments.forEach { a ->
                addBulletLine(attachmentFileNameOnly(a.displayName, a.uri))
            }
        } else {
            // Text-only share: bullet each non-empty line
            parsed.rawText.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { addBulletLine(it) }
        }

        // Append attachments after URL list / text if we had URLs and attachments
        if (links.isNotEmpty() && parsed.attachments.isNotEmpty()) {
            parsed.attachments.forEach { a ->
                addBulletLine(attachmentFileNameOnly(a.displayName, a.uri))
            }
        }

        if (outLines.isEmpty()) {
            addBulletLine("Shared content")
        }

        val textBody = outLines.joinToString("\n")

        // Best-effort HTML, using the official Android framework escaper.
        val htmlBody = outLines.joinToString("<br/>") { Html.escapeHtml(it) }

        return textBody to htmlBody
    }

    /**
     * Extracts a short file name (including extension) for an attachment.
     *
     * Preference order:
     * 1) displayName (if present)
     * 2) lastPathSegment
     * 3) uri string
     *
     * The return value is reduced to the last path component to avoid long "content://..." lines.
     *
     * @param displayName Optional human-friendly name from the share source.
     * @param uri The content/file URI.
     * @return A string like "image1.png" or "file1.pdf".
     */
    private fun attachmentFileNameOnly(displayName: String?, uri: Uri): String {
        val raw = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: uri.toString()

        return raw.substringAfterLast('/').substringAfterLast('\\')
    }

    // ---------- Title inference (no network) ----------

    /**
     * Attempts to infer titles from the raw share text for known URL lists.
     *
     * Rationale:
     * Some share sources (e.g., browsers) include page titles in the share payload.
     * We try to map titles to URLs without network calls.
     *
     * Heuristics:
     * - Single URL: use the first non-URL line as title candidate.
     * - Multiple URLs: some browsers append one comma-separated line of titles; map in order.
     *
     * If heuristics do not match, returns an empty map.
     *
     * @param rawText Raw shared text as received.
     * @param urls The extracted URLs in the order they were found.
     * @return Map from URL to inferred title (nullable).
     */
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

    /**
     * Performs minimal cleanup on an inferred title.
     *
     * @param s Candidate title string.
     * @return Cleaned title.
     */
    private fun cleanupTitle(s: String): String {
        return s
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('•', '-', '–', '—')
            .trim()
    }

    // ---------- helpers ----------

    /**
     * Extracts the domain (host) from a URL. Falls back to returning the input on errors.
     *
     * @param url Any URL string.
     * @return Host without leading "www." if available; otherwise original URL.
     */
    private fun domainOf(url: String): String {
        return try {
            val host = url.toUri().host ?: return url
            host.removePrefix("www.")
        } catch (_: Throwable) {
            url
        }
    }

    /**
     * Concatenates parts into a single string separated by [SUBJECT_SEP] without exceeding [maxLen].
     *
     * @param parts The list of parts to join.
     * @param maxLen Max total length allowed.
     * @return Joined string within the limit.
     */
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

    /**
     * Truncates [s] to [max] characters, adding an ellipsis if needed.
     *
     * @param s Input string.
     * @param max Maximum length.
     * @return Possibly truncated string.
     */
    private fun ellipsize(s: String, max: Int): String {
        val t = s.trim()
        if (t.length <= max) return t
        if (max <= 1) return "…"
        return t.take(max - 1).trimEnd() + "…"
    }
}
