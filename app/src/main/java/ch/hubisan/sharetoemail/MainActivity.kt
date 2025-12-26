package ch.hubisan.sharetoemail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.hubisan.sharetoemail.data.AppDataStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppDataStore(this)

        setContent {
            MaterialTheme {
                SettingsScreen(store)
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: AppDataStore) {
    val scope = rememberCoroutineScope()

    var emailA by remember { mutableStateOf("") }
    var emailB by remember { mutableStateOf("") }
    var emailC by remember { mutableStateOf("") }
    var fetchTitles by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        emailA = store.getRecipientAEmail()
        emailB = store.getRecipientBEmail()
        emailC = store.getRecipientCEmail()
        fetchTitles = store.isFetchTitlesEnabled()
    }

    fun saveA(v: String) { emailA = v; scope.launch { store.setRecipientAEmail(v) } }
    fun saveB(v: String) { emailB = v; scope.launch { store.setRecipientBEmail(v) } }
    fun saveC(v: String) { emailC = v; scope.launch { store.setRecipientCEmail(v) } }
    fun saveFetch(v: Boolean) { fetchTitles = v; scope.launch { store.setFetchTitlesEnabled(v) } }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Share to Email – Settings", style = MaterialTheme.typography.titleLarge)

            EmailField(
                label = "Recipient for Share to @A",
                value = emailA,
                onChange = { saveA(it.trim()) }
            )
            EmailField(
                label = "Recipient for Share to @B",
                value = emailB,
                onChange = { saveB(it.trim()) }
            )
            EmailField(
                label = "Recipient for Share to @C",
                value = emailC,
                onChange = { saveC(it.trim()) }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Fetch page titles (slow)", style = MaterialTheme.typography.titleMedium)
                    Text("If enabled, tries to fetch <title> for nicer link subjects.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = fetchTitles, onCheckedChange = { saveFetch(it) })
            }
        }
    }
}

@Composable
private fun EmailField(label: String, value: String, onChange: (String) -> Unit) {
    val isError = value.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        isError = isError,
        modifier = Modifier.fillMaxWidth()
    )
    if (isError) {
        Text("Invalid email", color = MaterialTheme.colorScheme.error)
    }
}
