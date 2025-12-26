package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.Intent
import android.net.Uri
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

        // Minimal parse: text + attachments (EXTRA_STREAM)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        val attachments: ArrayList<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: arrayListOf()
            Intent.ACTION_SEND -> {
                val u = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                arrayListOf<Uri>().apply { if (u != null) add(u) }
            }
            else -> arrayListOf()
        }

        // TODO: next step: your full Subject/Body logic + URL extraction + ClipData + video-block
        val subject = if (sharedText.isNotBlank()) sharedText.take(120) else "Shared content"
        val body = sharedText.ifBlank { "See attachments." }

        val emailIntent = Intent().apply {
            action = if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
            type = if (attachments.isNotEmpty()) "*/*" else "text/plain"
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
            }
        }

        startActivity(Intent.createChooser(emailIntent, "Send email"))
        finish()
    }
}
