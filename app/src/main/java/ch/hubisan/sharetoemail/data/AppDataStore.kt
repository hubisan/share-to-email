package ch.hubisan.sharetoemail.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "share_to_email")

class AppDataStore(private val context: Context) {

    private object Keys {
        val RECIPIENT_A_EMAIL = stringPreferencesKey("recipient_a_email")
        val RECIPIENT_B_EMAIL = stringPreferencesKey("recipient_b_email")
        val FETCH_TITLES_ENABLED = booleanPreferencesKey("fetch_titles_enabled")
        val RECIPIENTS_JSON = stringPreferencesKey("recipients_json")
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

    suspend fun isFetchTitlesEnabled(): Boolean =
        context.dataStore.data.first()[Keys.FETCH_TITLES_ENABLED] ?: false

    suspend fun setFetchTitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FETCH_TITLES_ENABLED] = enabled }
    }

    suspend fun getOtherRecipients(): List<Recipient> {
        val raw = context.dataStore.data.first()[Keys.RECIPIENTS_JSON].orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Recipient(
                            id = o.optString("id"),
                            label = o.optString("label"),
                            email = o.optString("email")
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.email.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveOtherRecipients(list: List<Recipient>) {
        val arr = JSONArray()
        list.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("label", r.label)
            o.put("email", r.email)
            arr.put(o)
        }
        context.dataStore.edit { it[Keys.RECIPIENTS_JSON] = arr.toString() }
    }
}
