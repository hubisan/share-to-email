package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import ch.hubisan.sharetoemail.data.AppDataStore
import ch.hubisan.sharetoemail.logic.EmailComposer
import ch.hubisan.sharetoemail.logic.ShareKind
import ch.hubisan.sharetoemail.logic.ShareParser
import ch.hubisan.sharetoemail.logic.TitleFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val slot = resolveSlot(intent)
            val store = AppDataStore(this)

            val recipient = runBlocking { store.getRecipientEmailForSlot(slot) }.trim()
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

            if (!isInstalled(defaultEmailApp.pkg)) {
                runBlocking { store.setDefaultEmailApp(null) }
                Toast.makeText(this, "Selected E-Mail App not installed. Please choose again.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }

            // 1. Parsen (Hier werden die Titel aus dem Subject bereits extrahiert)
            val parsed = ShareParser.parse(this, intent)

            // 2. Titel-Logik optimiert
            val finalTitles: Map<String, String?> = if (parsed.kind == ShareKind.TEXT_URL) {

                // Welche URLs haben wir noch NICHT im Subject gefunden?
                val missingUrls = parsed.urls.filter { !parsed.prefilledTitles.containsKey(it) }

                // Wir laden nur nach, wenn URLs fehlen.
                // Optimierung: Auch bei nur 1 URL versuchen wir den Titel zu holen, falls er fehlt.
                // (Deine alte Logik hatte hier `parsed.urls.size > 1`, ich habe das entfernt,
                // damit auch Einzel-URLs schöne Titel bekommen, wenn sie nicht im Subject stehen.)
                val fetchedMissing = if (missingUrls.isNotEmpty()) {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            TitleFetcher.fetchTitles(missingUrls)
                        }
                    }
                } else {
                    emptyMap()
                }

                // Kombinieren: Was wir schon hatten + was wir nachgeladen haben
                parsed.prefilledTitles + fetchedMissing
            } else {
                emptyMap()
            }

            // 3. Compose mit der kombinierten Map
            val draft = EmailComposer.compose(parsed, finalTitles)

            // Attachments (if any)
            val attachmentUris = ArrayList(parsed.attachments.map { it.uri })
            val hasAttachments = parsed.kind == ShareKind.ATTACHMENT && attachmentUris.isNotEmpty()

            val emailIntent = Intent().apply {
                action = if (hasAttachments && attachmentUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
                type = if (!hasAttachments) "text/plain" else "*/*"

                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                putExtra(Intent.EXTRA_TEXT, draft.textBody)

                if (hasAttachments) {
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
            finish()
        }
    }

    private fun resolveSlot(intent: Intent): String {
        val alias = intent.component?.className.orEmpty()
        return when {
            alias.endsWith("ShareAliasA") -> "A"
            alias.endsWith("ShareAliasB") -> "B"
            alias.endsWith("ShareAliasC") -> "C"
            else -> "A"
        }
    }

    private fun isInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildClipData(uris: List<Uri>): ClipData? {
        if (uris.isEmpty()) return null
        val clip = ClipData.newUri(contentResolver, "attachments", uris.first())
        for (i in 1 until uris.size) {
            clip.addItem(ClipData.Item(uris[i]))
        }
        return clip
    }
}

private suspend fun AppDataStore.getRecipientEmailForSlot(slot: String): String {
    return when (slot) {
        "A" -> getRecipientAEmail()
        "B" -> getRecipientBEmail()
        "C" -> getRecipientCEmail()
        else -> getRecipientAEmail()
    }
}