package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import ch.hubisan.sharetoemail.data.AppDataStore
import kotlinx.coroutines.runBlocking

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            finish()
            return
        }

        val defaultEmailApp = runBlocking { store.getDefaultEmailApp() }
        if (defaultEmailApp == null) {
            Toast.makeText(this, "Please choose a Default E-Mail App in Settings", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        // Validate selected app still exists
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
            finish()
            return
        }

        // Minimal parse: text + attachments (EXTRA_STREAM)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        val attachments: ArrayList<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: arrayListOf()

            Intent.ACTION_SEND -> {
                val u: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (u != null) arrayListOf(u) else arrayListOf()
            }

            else -> arrayListOf()
        }

        val subject = if (sharedText.isNotBlank()) sharedText.take(120) else "Shared content"
        val body = sharedText.ifBlank { "See attachments." }

        val emailIntent = Intent().apply {
            action = if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND

            // For attachments, don't rely on message/rfc822 as a "filter"
            type = if (attachments.isEmpty()) "text/plain" else "*/*"

            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)

            if (attachments.isNotEmpty()) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (attachments.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments)
                } else {
                    putExtra(Intent.EXTRA_STREAM, attachments.first())
                }

                // Improves URI permission propagation on some clients
                clipData = buildClipData(attachments)
            }

            // Force the chosen email app (no chooser)
            setClassName(defaultEmailApp.pkg, defaultEmailApp.cls)
        }

        if (emailIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "Selected email app cannot handle this share", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        startActivity(emailIntent)
        finish()
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
