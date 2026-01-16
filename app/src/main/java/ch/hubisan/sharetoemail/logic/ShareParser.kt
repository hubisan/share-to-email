package ch.hubisan.sharetoemail.logic

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat

data class Attachment(
    val uri: Uri,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?
)

enum class ShareKind {
    TEXT_PLAIN,
    TEXT_URL,
    TEXT_UNDEFINED,
    ATTACHMENT
}

data class ParsedShare(
    val kind: ShareKind,
    val rawText: String,
    val urls: List<String>,
    // NEU: Hier speichern wir die vorab extrahierten Titel (URL -> Titel)
    val prefilledTitles: Map<String, String> = emptyMap(),
    val attachments: List<Attachment>
)

object ShareParser {

    private const val JOIN_SEPARATOR = "\n\n"
    private const val URL_LIST_PREFIX_MAX_LEN = 5

    fun parse(context: Context, intent: Intent): ParsedShare {
        val cr = context.contentResolver
        val type = intent.type?.lowercase()

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()

        val combinedText = joinNonEmpty(extraText, subject)

        // ---- TEXT/PLAIN BRANCH ----
        if (type == "text/plain") {

            // both empty: treat as attachment if any exist (fallback)
            if (extraText.isBlank() && subject.isBlank()) {
                val attachments = collectAttachmentsWithClipData(intent, cr)
                return if (attachments.isNotEmpty()) {
                    ParsedShare(
                        kind = ShareKind.ATTACHMENT,
                        rawText = "",
                        urls = emptyList(),
                        attachments = attachments
                    )
                } else {
                    ParsedShare(
                        kind = ShareKind.TEXT_UNDEFINED,
                        rawText = "",
                        urls = emptyList(),
                        attachments = emptyList()
                    )
                }
            }

            // only EXTRA_TEXT, no SUBJECT -> TEXT_PLAIN
            if (extraText.isNotEmpty() && subject.isBlank()) {
                return ParsedShare(
                    kind = ShareKind.TEXT_PLAIN,
                    rawText = extraText,
                    urls = emptyList(),
                    attachments = emptyList()
                )
            }

            val extraTrim = extraText.trim()

            // single URL only -> TEXT_URL
            // Falls es nur 1 URL ist, ist das Subject oft der Titel. Das nehmen wir direkt mit.
            if (isSingleUrlOnly(extraTrim)) {
                val titleMap = if (subject.isNotBlank()) mapOf(extraTrim to subject) else emptyMap()
                return ParsedShare(
                    kind = ShareKind.TEXT_URL,
                    rawText = combinedText,
                    urls = listOf(extraTrim),
                    prefilledTitles = titleMap,
                    attachments = emptyList()
                )
            }

            // URL list -> TEXT_URL
            val urlList = extractUrlListFromLines(extraText)
            if (urlList.isNotEmpty()) {
                // Hier kommt deine Optimierung:
                val titles = tryExtractTitlesFromSubject(urlList, subject)

                return ParsedShare(
                    kind = ShareKind.TEXT_URL,
                    rawText = combinedText,
                    urls = urlList,
                    prefilledTitles = titles,
                    attachments = emptyList()
                )
            }

            // else -> TEXT_UNDEFINED
            return ParsedShare(
                kind = ShareKind.TEXT_UNDEFINED,
                rawText = combinedText,
                urls = emptyList(),
                attachments = emptyList()
            )
        }

        // ---- NON-TEXT/PLAIN BRANCH ----
        val attachments = collectAttachmentsWithClipData(intent, cr)

        return ParsedShare(
            kind = if (attachments.isNotEmpty()) ShareKind.ATTACHMENT else ShareKind.TEXT_UNDEFINED,
            rawText = combinedText,
            urls = emptyList(),
            attachments = attachments
        )
    }

    /**
     * Versucht, die Titel aus dem Subject zu parsen, basierend auf der Heuristik:
     * Wenn (Anzahl Kommas) == (Anzahl URLs - 1), dann entspricht die Reihenfolge der Titel der der URLs.
     */
    private fun tryExtractTitlesFromSubject(urls: List<String>, subject: String): Map<String, String> {
        if (urls.size < 2 || subject.isBlank()) return emptyMap()

        val commaCount = subject.count { it == ',' }
        // Exakter Match der Struktur erforderlich
        if (commaCount == urls.size - 1) {
            val rawTitles = subject.split(",")
            if (rawTitles.size == urls.size) {
                // Wir zippen die URLs mit den getrimmten Titeln zusammen
                return urls.zip(rawTitles.map { it.trim() }).toMap()
            }
        }
        return emptyMap()
    }

    private fun collectAttachmentsWithClipData(intent: Intent, cr: ContentResolver): List<Attachment> {
        val uris = mutableListOf<Uri>()

        // EXTRA_STREAM (single/multiple)
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let(uris::add)

        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let(uris::addAll)

        // ClipData URIs
        intent.clipData?.let { cd ->
            for (i in 0 until cd.itemCount) {
                cd.getItemAt(i).uri?.let(uris::add)
            }
        }

        return uris
            .distinctBy { it.toString() }
            .mapNotNull { it.toAttachmentOrNull(cr) }
    }

    private fun joinNonEmpty(a: String, b: String): String {
        val parts = ArrayList<String>(2)
        if (a.isNotEmpty()) parts.add(a)
        if (b.isNotEmpty()) parts.add(b)
        return parts.joinToString(JOIN_SEPARATOR)
    }

    private fun isSingleUrlOnly(textTrimmed: String): Boolean {
        if (textTrimmed.isEmpty()) return false
        return URL_REGEX.matchEntire(textTrimmed) != null
    }

    private fun extractUrlListFromLines(extraText: String): List<String> {
        if (extraText.isBlank()) return emptyList()

        val urls = mutableListOf<String>()
        var sawNonEmpty = false

        for (rawLine in extraText.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            sawNonEmpty = true

            val stripped = stripShortPrefix(line).trim()
            val url = extractExactlyOneUrl(stripped) ?: return emptyList()
            urls.add(url)
        }

        return if (!sawNonEmpty) emptyList() else urls.distinct()
    }

    private fun stripShortPrefix(s: String): String {
        val limit = minOf(URL_LIST_PREFIX_MAX_LEN, s.length)
        val head = s.substring(0, limit)
        val wsIdx = head.indexOfFirst { it == ' ' || it == '\t' }
        return if (wsIdx >= 0) s.substring(wsIdx + 1) else s
    }

    private fun extractExactlyOneUrl(s: String): String? {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return null

        val matches = URL_REGEX.findAll(trimmed).map { it.value }.toList()
        if (matches.size != 1) return null

        val token = matches[0]
        val cleaned = trimTrailingPunctuation(token)

        val remainder = trimmed.replace(token, "").trim()
        if (remainder.isNotEmpty()) return null

        return if (URL_REGEX.matches(cleaned)) cleaned else null
    }

    private fun trimTrailingPunctuation(url: String): String {
        var u = url
        while (u.isNotEmpty() && u.last() in TRAILING_PUNCTUATION) {
            u = u.dropLast(1)
        }
        return u
    }

    private val TRAILING_PUNCTUATION = setOf('.', ',', ')', ']', '}', ';', ':', '!', '?')

    private fun Uri.toAttachmentOrNull(cr: ContentResolver): Attachment? {
        val mime = runCatching { cr.getType(this) }.getOrNull()
        if (mime?.startsWith("video/") == true) return null

        return Attachment(
            uri = this,
            mimeType = mime,
            displayName = cr.queryString(this, OpenableColumns.DISPLAY_NAME),
            sizeBytes = cr.queryLong(this, OpenableColumns.SIZE)
        )
    }

    private fun ContentResolver.queryString(uri: Uri, column: String): String? {
        return runCatching {
            query(uri, arrayOf(column), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(column)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun ContentResolver.queryLong(uri: Uri, column: String): Long? {
        return runCatching {
            query(uri, arrayOf(column), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(column)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else null
            }
        }.getOrNull()
    }

    private val URL_REGEX = Regex("""https?://[^\s<>()"]+""", RegexOption.IGNORE_CASE)
}