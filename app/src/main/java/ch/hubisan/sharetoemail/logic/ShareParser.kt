package ch.hubisan.sharetoemail.logic

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat

/**
 * Represents a shared attachment (file/image/etc.).
 *
 * @property uri Content URI for the attachment.
 * @property mimeType MIME type as reported by the content resolver (may be null).
 * @property displayName Best-effort filename (may be null).
 * @property sizeBytes Best-effort size in bytes (may be null).
 */
data class Attachment(
    val uri: Uri,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?
)

/**
 * Normalized result of parsing an Android share [Intent].
 *
 * @property rawText Combined plain text extracted from the intent (subject/text/clip text).
 * @property urls URLs extracted from [rawText].
 * @property attachments Attachments extracted from EXTRA_STREAM and ClipData URIs.
 */
data class ParsedShare(
    val rawText: String,
    val urls: List<String>,
    val attachments: List<Attachment>
)

/**
 * Parses common Android share intents into [ParsedShare].
 *
 * Sources considered:
 * - Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT
 * - Intent.EXTRA_STREAM (single and multiple)
 * - ClipData (text and URIs)
 *
 * Behavior:
 * - Deduplicates URIs and URLs.
 * - Filters out video attachments (MIME type "video/" with any subtype).
 */
object ShareParser {

    /**
     * Parses a share [intent] into normalized [ParsedShare].
     *
     * @param context Used to access the [ContentResolver].
     * @param intent Incoming share intent.
     * @return Parsed share content.
     */
    fun parse(context: Context, intent: Intent): ParsedShare {
        val cr = context.contentResolver

        val textParts = mutableListOf<String>()
        val streamUris = mutableListOf<Uri>()

        // 1) Text extras
        intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(textParts::add)

        intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(textParts::add)

        // 2) Attachments via EXTRA_STREAM (single/multiple) using non-deprecated helpers
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let(streamUris::add)

        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let(streamUris::addAll)

        // 3) ClipData (may contain both text and URIs)
        intent.clipData?.let { cd ->
            for (i in 0 until cd.itemCount) {
                val item = cd.getItemAt(i)

                item.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(textParts::add)

                item.uri?.let(streamUris::add)
            }
        }

        val rawText = textParts.joinToString("\n\n").trim()
        val urls = extractUrls(rawText)

        val attachments = streamUris
            .distinct()
            .mapNotNull { uri -> uri.toAttachmentOrNull(cr) }

        return ParsedShare(
            rawText = rawText,
            urls = urls,
            attachments = attachments
        )
    }

    /**
     * Extracts URLs from text using a conservative regex.
     *
     * @param text Input text.
     * @return Distinct list of URLs in first-seen order.
     */
    private fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val regex = Regex("""https?://[^\s<>()"]+""", RegexOption.IGNORE_CASE)
        return regex.findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ')', ']', '}', ';') }
            .distinct()
            .toList()
    }

    /**
     * Converts a URI into an [Attachment] or returns null if it should be skipped.
     *
     * Currently skipped:
     * - Video attachments (MIME type starts with "video/").
     *
     * @receiver The attachment URI.
     * @param cr Content resolver used to query metadata.
     * @return Attachment or null if filtered out.
     */
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

    /**
     * Queries a single string column from the content resolver.
     *
     * @param uri Content URI to query.
     * @param column Column name to read.
     * @return The column value or null.
     */
    private fun ContentResolver.queryString(uri: Uri, column: String): String? {
        return runCatching {
            query(uri, arrayOf(column), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(column)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
    }

    /**
     * Queries a single long column from the content resolver.
     *
     * @param uri Content URI to query.
     * @param column Column name to read.
     * @return The column value or null.
     */
    private fun ContentResolver.queryLong(uri: Uri, column: String): Long? {
        return runCatching {
            query(uri, arrayOf(column), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(column)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else null
            }
        }.getOrNull()
    }
}
