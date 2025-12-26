package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import ch.hubisan.sharetoemail.data.AppDataStore
import ch.hubisan.sharetoemail.logic.EmailComposer
import ch.hubisan.sharetoemail.logic.ShareParser
import ch.hubisan.sharetoemail.logic.TitleFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ShareActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShareLock.tryAcquire()) {
            Toast.makeText(this, "Teilen läuft bereits – zuerst abschliessen.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val alias = intent.component?.className.orEmpty()
            val slot = when {
                alias.endsWith("ShareAliasA") -> "A"
                alias.endsWith("ShareAliasB") -> "B"
                alias.endsWith("ShareAliasC") -> "C"
                else -> "A"
            }

            val store = AppDataStore(this)

            val recipient = runBlocking {
                when (slot) {
                    "A" -> store.getRecipientAEmail()
                    "B" -> store.getRecipientBEmail()
                    "C" -> store.getRecipientCEmail()
                    else -> store.getRecipientAEmail()
                }
            }.trim()

            if (recipient.isBlank()) {
                Toast.makeText(this, "No recipient set for @$slot", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }

            val defaultEmailApp = runBlocking { store.getDefaultEmailApp() }
            if (defaultEmailApp == null) {
                Toast.makeText(this, "Please choose a Default E-Mail App in Settings", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }

            val appInstalled = try {
                packageManager.getPackageInfo(defaultEmailApp.pkg, 0)
                true
            } catch (_: Exception) {
                false
            }

            if (!appInstalled) {
                runBlocking { store.setDefaultEmailApp(null) }
                Toast.makeText(this, "Selected E-Mail App not installed. Please choose again.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }

            val parsed = ShareParser.parse(this, intent)

            // Titles only if toggle on (optional; you can remove later)
            val fetchTitlesEnabled = runBlocking { store.isFetchTitlesEnabled() }
            val fetchedTitles: Map<String, String?> = runBlocking {
                if (!fetchTitlesEnabled || parsed.urls.isEmpty()) {
                    emptyMap()
                } else {
                    parsed.urls.map { url ->
                        async {
                            val title = withContext(Dispatchers.IO) { TitleFetcher.fetchTitle(url) }
                            url to title
                        }
                    }.awaitAll().toMap()
                }
            }

            val draft = EmailComposer.compose(parsed, fetchedTitles)
            val attachmentUris = ArrayList(parsed.attachments.map { it.uri })

            // Build HTML Spanned for EXTRA_TEXT, because many mail apps ignore EXTRA_HTML_TEXT
            val spannedBody = Html.fromHtml(draft.htmlBody, Html.FROM_HTML_MODE_LEGACY)

            val emailIntent = Intent().apply {
                action = if (attachmentUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND

                // If attachments: many clients behave better with rfc822.
                // Without attachments: prefer text/html.
                type = if (attachmentUris.isEmpty()) "text/html" else "message/rfc822"

                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)

                // IMPORTANT: set EXTRA_TEXT as Spanned so links render as clickable in many clients
                putExtra(Intent.EXTRA_TEXT, spannedBody)

                // Also include HTML part (some clients use this instead)
                putExtra(Intent.EXTRA_HTML_TEXT, draft.htmlBody)

                if (attachmentUris.isNotEmpty()) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (attachmentUris.size > 1) {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris)
                    } else {
                        putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
                    }
                    clipData = buildClipData(attachmentUris)
                }

                setClassName(defaultEmailApp.pkg, defaultEmailApp.cls)
            }

            if (emailIntent.resolveActivity(packageManager) == null) {
                Toast.makeText(this, "Selected email app cannot handle this share", Toast.LENGTH_SHORT).show()
                return
            }

            startActivity(emailIntent)

        } finally {
            ShareLock.release()
            finish()
        }
    }

    private fun buildClipData(uris: List<Uri>): ClipData? {
        if (uris.isEmpty()) return null
        val first = uris.first()
        val clip = ClipData.newUri(contentResolver, "attachments", first)
        for (i in 1 until uris.size) {
            clip.addItem(ClipData.Item(uris[i]))
        }
        return clip
    }
}
