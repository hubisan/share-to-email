package ch.hubisan.sharetoemail.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "share_to_email")

class AppDataStore(private val context: Context) {

    private object Keys {
        val RECIPIENT_A_EMAIL = stringPreferencesKey("recipient_a_email")
        val RECIPIENT_B_EMAIL = stringPreferencesKey("recipient_b_email")
        val RECIPIENT_C_EMAIL = stringPreferencesKey("recipient_c_email")
        val FETCH_TITLES_ENABLED = booleanPreferencesKey("fetch_titles_enabled")
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
}
