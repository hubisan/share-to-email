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

data class ParsedShare(
    val rawText: String,
    val urls: List<String>,
    val attachments: List<Attachment>
)

object ShareParser {

    fun parse(context: Context, intent: Intent): ParsedShare {
        val cr = context.contentResolver

        val textParts = mutableListOf<String>()
        val urls = linkedSetOf<String>()
        val attachments = mutableListOf<Attachment>()

        // 1) Text extras
        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { if (it.isNotBlank()) textParts += it.trim() }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.isNotBlank()) textParts += it.trim() }

        // 2) Attachments: EXTRA_STREAM (single/multiple) – non-deprecated
        val streams = mutableListOf<Uri>()

        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let { streams += it }

        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let { streams += it }

        // 3) ClipData: text + uris
        val cd = intent.clipData
        if (cd != null) {
            for (i in 0 until cd.itemCount) {
                val item = cd.getItemAt(i)
                item.text?.toString()?.takeIf { it.isNotBlank() }?.let { textParts += it.trim() }
                item.uri?.let { streams += it }
            }
        }

        // Dedup URIs
        val uniqUris = streams.distinct()

        // 4) Build attachments (block video/*)
        for (u in uniqUris) {
            val mime = cr.getType(u)
            if (mime != null && mime.startsWith("video/")) {
                // blocked
                continue
            }
            attachments += Attachment(
                uri = u,
                mimeType = mime,
                displayName = queryDisplayName(cr, u),
                sizeBytes = querySize(cr, u)
            )
        }

        // 5) Extract URLs from all text
        val joinedText = textParts.joinToString("\n\n").trim()
        extractUrls(joinedText).forEach { urls += it }

        return ParsedShare(
            rawText = joinedText,
            urls = urls.toList(),
            attachments = attachments
        )
    }

    private fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val regex = Regex("""https?://[^\s<>()"]+""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.value.trim().trimEnd('.', ',', ')', ']', '}', ';') }.distinct().toList()
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        return try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Throwable) { null }
    }

    private fun querySize(cr: ContentResolver, uri: Uri): Long? {
        return try {
            cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else null
            }
        } catch (_: Throwable) { null }
    }
}
