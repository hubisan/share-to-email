package ch.hubisan.sharetoemail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.hubisan.sharetoemail.data.AppDataStore
import ch.hubisan.sharetoemail.data.Recipient
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = AppDataStore(this)

        setContent {
            MaterialTheme {
                SettingsScreen(store = store)
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: AppDataStore) {
    val scope = rememberCoroutineScope()

    var emailA by remember { mutableStateOf("") }
    var emailB by remember { mutableStateOf("") }
    var fetchTitles by remember { mutableStateOf(false) }
    var others by remember { mutableStateOf<List<Recipient>>(emptyList()) }

    var newLabel by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }

    // load once
    LaunchedEffect(Unit) {
        emailA = store.getRecipientAEmail()
        emailB = store.getRecipientBEmail()
        fetchTitles = store.isFetchTitlesEnabled()
        others = store.getOtherRecipients()
    }

    fun saveA(value: String) {
        emailA = value
        scope.launch { store.setRecipientAEmail(value) }
    }

    fun saveB(value: String) {
        emailB = value
        scope.launch { store.setRecipientBEmail(value) }
    }

    fun saveFetch(value: Boolean) {
        fetchTitles = value
        scope.launch { store.setFetchTitlesEnabled(value) }
    }

    fun saveOthers(list: List<Recipient>) {
        others = list
        scope.launch { store.saveOtherRecipients(list) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Share to Email – Settings", style = MaterialTheme.typography.titleLarge)
            }

            item {
                OutlinedTextField(
                    value = emailA,
                    onValueChange = { saveA(it.trim()) },
                    label = { Text("Recipient for Share to @A") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = emailA.isNotBlank() && !isValidEmail(emailA),
                    modifier = Modifier.fillMaxWidth()
                )
                if (emailA.isNotBlank() && !isValidEmail(emailA)) {
                    Text("Invalid email", color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                OutlinedTextField(
                    value = emailB,
                    onValueChange = { saveB(it.trim()) },
                    label = { Text("Recipient for Share to @B") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = emailB.isNotBlank() && !isValidEmail(emailB),
                    modifier = Modifier.fillMaxWidth()
                )
                if (emailB.isNotBlank() && !isValidEmail(emailB)) {
                    Text("Invalid email", color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Fetch page titles (slow)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "If enabled, the app tries to download the webpage title for nicer subjects.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = fetchTitles, onCheckedChange = { saveFetch(it) })
                }
            }

            item {
                HorizontalDivider()
                Text("Recipients for Share to @others", style = MaterialTheme.typography.titleMedium)
            }

            // Add new recipient
            item {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Label (e.g. Alice, Team, Notes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it.trim() },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = newEmail.isNotBlank() && !isValidEmail(newEmail),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val label = newLabel.trim()
                            val email = newEmail.trim()
                            if (label.isBlank() || !isValidEmail(email)) return@Button

                            val updated = others + Recipient(
                                id = UUID.randomUUID().toString(),
                                label = label,
                                email = email
                            )
                            saveOthers(updated)
                            newLabel = ""
                            newEmail = ""
                        },
                        enabled = newLabel.trim().isNotBlank() && isValidEmail(newEmail.trim())
                    ) {
                        Text("Add")
                    }

                    OutlinedButton(
                        onClick = {
                            newLabel = ""
                            newEmail = ""
                        }
                    ) { Text("Clear") }
                }
            }

            // List existing
            items(others, key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.titleMedium)
                            Text(r.email, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(
                            onClick = {
                                saveOthers(others.filterNot { it.id == r.id })
                            }
                        ) { Text("Delete") }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun isValidEmail(s: String): Boolean {
    // simple, good enough for settings input
    return android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
}
