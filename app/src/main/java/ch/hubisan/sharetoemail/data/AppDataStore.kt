package ch.hubisan.sharetoemail.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "share_to_email")

class AppDataStore(private val context: Context) {

    data class DefaultEmailApp(val pkg: String, val cls: String)

    private object Keys {
        val RECIPIENT_A_EMAIL = stringPreferencesKey("recipient_a_email")
        val RECIPIENT_B_EMAIL = stringPreferencesKey("recipient_b_email")
        val RECIPIENT_C_EMAIL = stringPreferencesKey("recipient_c_email")
        val FETCH_TITLES_ENABLED = booleanPreferencesKey("fetch_titles_enabled")

        val DEFAULT_EMAIL_PKG = stringPreferencesKey("default_email_pkg")
        val DEFAULT_EMAIL_CLS = stringPreferencesKey("default_email_cls")
    }

    suspend fun getRecipientAEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_A_EMAIL].orEmpty()

    suspend fun setRecipientAEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_A_EMAIL] = email.trim() }
    }

    suspend fun getRecipientBEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_B_EMAIL].orEmpty()

    suspend fun setRecipientBEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_B_EMAIL] = email.trim() }
    }

    suspend fun getRecipientCEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_C_EMAIL].orEmpty()

    suspend fun setRecipientCEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_C_EMAIL] = email.trim() }
    }

    suspend fun isFetchTitlesEnabled(): Boolean =
        context.dataStore.data.first()[Keys.FETCH_TITLES_ENABLED] ?: false

    suspend fun setFetchTitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FETCH_TITLES_ENABLED] = enabled }
    }

    // Default email app (optional)
    suspend fun getDefaultEmailApp(): DefaultEmailApp? {
        val prefs = context.dataStore.data.first()
        val pkg = prefs[Keys.DEFAULT_EMAIL_PKG]
        val cls = prefs[Keys.DEFAULT_EMAIL_CLS]
        return if (!pkg.isNullOrBlank() && !cls.isNullOrBlank()) DefaultEmailApp(pkg, cls) else null
    }

    suspend fun setDefaultEmailApp(app: DefaultEmailApp?) {
        context.dataStore.edit { prefs ->
            if (app == null) {
                prefs.remove(Keys.DEFAULT_EMAIL_PKG)
                prefs.remove(Keys.DEFAULT_EMAIL_CLS)
            } else {
                prefs[Keys.DEFAULT_EMAIL_PKG] = app.pkg
                prefs[Keys.DEFAULT_EMAIL_CLS] = app.cls
            }
        }
    }
}
