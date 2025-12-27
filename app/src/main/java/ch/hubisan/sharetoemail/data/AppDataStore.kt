package ch.hubisan.sharetoemail.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed settings storage for the app.
 *
 * Stored values:
 * - Recipient email addresses for share slots A/B/C
 * - Optional feature flags (e.g., fetch titles)
 * - Default email app target (package + activity class)
 *
 * Notes:
 * - This class provides simple suspend getter/setter methods for convenience.
 * - Values are stored in a single Preferences DataStore named "share_to_email".
 */
class AppDataStore(private val context: Context) {

    /**
     * Represents a concrete activity target inside an email app.
     *
     * @property pkg Package name of the app.
     * @property cls Fully qualified activity class name.
     */
    data class DefaultEmailApp(val pkg: String, val cls: String)

    /**
     * Keys used in the Preferences DataStore.
     */
    private object Keys {
        val RECIPIENT_A_EMAIL = stringPreferencesKey("recipient_a_email")
        val RECIPIENT_B_EMAIL = stringPreferencesKey("recipient_b_email")
        val RECIPIENT_C_EMAIL = stringPreferencesKey("recipient_c_email")

        val FETCH_TITLES_ENABLED = booleanPreferencesKey("fetch_titles_enabled")

        val DEFAULT_EMAIL_PKG = stringPreferencesKey("default_email_pkg")
        val DEFAULT_EMAIL_CLS = stringPreferencesKey("default_email_cls")
    }

    /**
     * Returns the configured recipient email for slot A (or empty string if not set).
     */
    suspend fun getRecipientAEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_A_EMAIL].orEmpty()

    /**
     * Stores the recipient email for slot A.
     *
     * @param email Recipient email address (trimmed before storing).
     */
    suspend fun setRecipientAEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_A_EMAIL] = email.trim() }
    }

    /**
     * Returns the configured recipient email for slot B (or empty string if not set).
     */
    suspend fun getRecipientBEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_B_EMAIL].orEmpty()

    /**
     * Stores the recipient email for slot B.
     *
     * @param email Recipient email address (trimmed before storing).
     */
    suspend fun setRecipientBEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_B_EMAIL] = email.trim() }
    }

    /**
     * Returns the configured recipient email for slot C (or empty string if not set).
     */
    suspend fun getRecipientCEmail(): String =
        context.dataStore.data.first()[Keys.RECIPIENT_C_EMAIL].orEmpty()

    /**
     * Stores the recipient email for slot C.
     *
     * @param email Recipient email address (trimmed before storing).
     */
    suspend fun setRecipientCEmail(email: String) {
        context.dataStore.edit { it[Keys.RECIPIENT_C_EMAIL] = email.trim() }
    }

    /**
     * Returns whether the optional "fetch titles" feature is enabled.
     *
     * Default: false
     */
    suspend fun isFetchTitlesEnabled(): Boolean =
        context.dataStore.data.first()[Keys.FETCH_TITLES_ENABLED] ?: false

    /**
     * Enables or disables the optional "fetch titles" feature.
     */
    suspend fun setFetchTitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FETCH_TITLES_ENABLED] = enabled }
    }

    /**
     * Returns the configured default email app target, or null if none is set.
     *
     * Both package and activity class must be present to be usable with Intent#setClassName().
     */
    suspend fun getDefaultEmailApp(): DefaultEmailApp? {
        val prefs = context.dataStore.data.first()
        val pkg = prefs[Keys.DEFAULT_EMAIL_PKG]
        val cls = prefs[Keys.DEFAULT_EMAIL_CLS]
        return if (!pkg.isNullOrBlank() && !cls.isNullOrBlank()) DefaultEmailApp(pkg, cls) else null
    }

    /**
     * Stores or clears the default email app target.
     *
     * @param app The selected app (package + class), or null to clear.
     */
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

/**
 * App-wide Preferences DataStore instance.
 *
 * Defined at top-level so it is created once per context and can be reused.
 */
private val Context.dataStore by preferencesDataStore(name = "share_to_email")
