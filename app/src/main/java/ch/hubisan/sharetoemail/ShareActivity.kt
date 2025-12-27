package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import ch.hubisan.sharetoemail.data.AppDataStore
import ch.hubisan.sharetoemail.logic.EmailComposer
import ch.hubisan.sharetoemail.logic.ShareParser
import kotlinx.coroutines.runBlocking

/**
 * Entry activity for Android share intents.
 *
 * Responsibilities:
 * - Determine which configured "slot" (A/B/C) was used (via activity-alias).
 * - Load the recipient email address for that slot from [AppDataStore].
 * - Ensure a default email app is configured and installed (e.g., Gmail).
 * - Parse the incoming share intent into normalized data ([ShareParser]).
 * - Compose an email draft ([EmailComposer]) and launch the chosen email app.
 *
 * Important:
 * - This activity intentionally does NOT use any locking (ShareLock removed).
 * - This activity intentionally does NOT fetch web page titles (TitleFetcher removed).
 * - We rely on plain text body because Gmail/intent HTML rendering is unreliable.
 */
class ShareActivity : Activity() {

    /**
     * Handles the incoming share intent and starts the configured email app with a draft.
     *
     * If a required setting is missing (recipient or default email app), the user is redirected
     * to [MainActivity] to configure it.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val slot = resolveSlot(intent)
            val store = AppDataStore(this)

            val recipient = runBlocking { store.getRecipientEmailForSlot(slot) }.trim()
            if (recipient.isBlank()) {
                Toast.makeText(this, "No recipient set for @$slot", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }

            val defaultEmailApp = runBlocking { store.getDefaultEmailApp() }
            if (defaultEmailApp == null) {
                Toast.makeText(this, "Please choose a Default E-Mail App in Settings", Toast.LENGTH_SHORT)
                    .show()
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }

            if (!isInstalled(defaultEmailApp.pkg)) {
                // If the selected package is no longer installed, clear the setting and ask the user again.
                runBlocking { store.setDefaultEmailApp(null) }
                Toast.makeText(
                    this,
                    "Selected E-Mail App not installed. Please choose again.",
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }

            val parsed = ShareParser.parse(this, intent)

            // No TitleFetcher: only inferred titles (if any) and otherwise raw URLs are used.
            val draft = EmailComposer.compose(parsed, emptyMap())

            val attachmentUris = ArrayList(parsed.attachments.map { it.uri })

            val emailIntent = Intent().apply {
                action = if (attachmentUris.size > 1) {
                    Intent.ACTION_SEND_MULTIPLE
                } else {
                    Intent.ACTION_SEND
                }

                // Plain text is the most reliable for Gmail intent flows.
                // With attachments, "*/*" keeps client compatibility high.
                type = if (attachmentUris.isEmpty()) "text/plain" else "*/*"

                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                putExtra(Intent.EXTRA_TEXT, draft.textBody)

                if (attachmentUris.isNotEmpty()) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    if (attachmentUris.size > 1) {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris)
                    } else {
                        putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
                    }

                    // Provide ClipData so the target app has read access to every attachment URI.
                    clipData = buildClipData(attachmentUris)
                }

                // Force the selected email app (e.g., Gmail).
                setClassName(defaultEmailApp.pkg, defaultEmailApp.cls)
            }

            if (emailIntent.resolveActivity(packageManager) == null) {
                Toast.makeText(this, "Selected email app cannot handle this share", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            startActivity(emailIntent)
        } finally {
            // Always close this trampoline activity.
            finish()
        }
    }

    /**
     * Determines which recipient slot to use based on the activity alias.
     *
     * The app uses separate activity-alias entries (e.g., ShareAliasA/B/C) so the user can pick
     * different share targets for different recipients.
     *
     * @param intent The incoming share intent.
     * @return "A", "B" or "C" (defaults to "A").
     */
    private fun resolveSlot(intent: Intent): String {
        val alias = intent.component?.className.orEmpty()
        return when {
            alias.endsWith("ShareAliasA") -> "A"
            alias.endsWith("ShareAliasB") -> "B"
            alias.endsWith("ShareAliasC") -> "C"
            else -> "A"
        }
    }

    /**
     * Checks whether a package is installed on the device.
     *
     * @param pkg Android package name.
     * @return True if installed, otherwise false.
     */
    private fun isInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds [ClipData] containing all attachment URIs.
     *
     * This improves compatibility for multiple attachments because some clients read from
     * ClipData and/or require it for URI permission propagation.
     *
     * @param uris Attachment URIs.
     * @return ClipData or null if there are no URIs.
     */
    private fun buildClipData(uris: List<Uri>): ClipData? {
        if (uris.isEmpty()) return null
        val clip = ClipData.newUri(contentResolver, "attachments", uris.first())
        for (i in 1 until uris.size) {
            clip.addItem(ClipData.Item(uris[i]))
        }
        return clip
    }
}

/**
 * Convenience extension to fetch the configured recipient for a given slot.
 *
 * Keeping this mapping in one place avoids duplicating the "when(slot)" logic.
 *
 * @receiver AppDataStore instance
 * @param slot "A", "B" or "C"
 * @return The configured recipient email address (may be blank if not configured)
 */
private suspend fun AppDataStore.getRecipientEmailForSlot(slot: String): String {
    return when (slot) {
        "A" -> getRecipientAEmail()
        "B" -> getRecipientBEmail()
        "C" -> getRecipientCEmail()
        else -> getRecipientAEmail()
    }
}
