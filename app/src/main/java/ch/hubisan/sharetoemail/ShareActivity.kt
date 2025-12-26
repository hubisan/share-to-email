package ch.hubisan.sharetoemail

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cls = intent.component?.className ?: ""
        val which = when {
            cls.endsWith("ShareAliasA") -> "A"
            cls.endsWith("ShareAliasB") -> "B"
            cls.endsWith("ShareAliasOthers") -> "others"
            else -> "unknown"
        }

        Toast.makeText(this, "Share target: $which", Toast.LENGTH_SHORT).show()

        // Fürs Debuggen erstmal sofort schließen, damit es sich "instant" anfühlt.
        finish()
    }
}
