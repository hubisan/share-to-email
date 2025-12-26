package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ch.hubisan.sharetoemail.data.AppDataStore
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

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

private data class DefaultEmailUi(
    val isSet: Boolean,
    val label: String,
    val pkg: String,
    val icon: Drawable?
)

@Composable
private fun SettingsScreen(store: AppDataStore) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var emailA by remember { mutableStateOf("") }
    var emailB by remember { mutableStateOf("") }
    var emailC by remember { mutableStateOf("") }
    var fetchTitles by remember { mutableStateOf(false) }

    var defaultUi by remember { mutableStateOf(DefaultEmailUi(false, "Not set", "", null)) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            scope.launch { defaultUi = loadDefaultEmailUi(store, ctx) }
        }
    }

    LaunchedEffect(Unit) {
        emailA = store.getRecipientAEmail()
        emailB = store.getRecipientBEmail()
        emailC = store.getRecipientCEmail()
        fetchTitles = store.isFetchTitlesEnabled()
        defaultUi = loadDefaultEmailUi(store, ctx)
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

            // ===== Default Email App (styled) =====
            Text("Default E-Mail App", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon
                    val iconBmp = remember(defaultUi.pkg, defaultUi.icon) {
                        defaultUi.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                    }

                    Surface(
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp)
                                )
                            } else {
                                Text("✉️")
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = defaultUi.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (defaultUi.isSet) {
                            Text(
                                text = defaultUi.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Required: choose one email app for sending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickerLauncher.launch(Intent(ctx, EmailAppPickerActivity::class.java)) }
                        ) {
                            Text("Choose…")
                        }
                        OutlinedButton(
                            enabled = defaultUi.isSet,
                            onClick = {
                                scope.launch {
                                    store.setDefaultEmailApp(null)
                                    defaultUi = loadDefaultEmailUi(store, ctx)
                                }
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }

            Text(
                "This app will be used directly for sending (no chooser).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Fetch page titles (slow)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "If enabled, tries to fetch <title> for nicer link subjects.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = fetchTitles, onCheckedChange = { saveFetch(it) })
            }
        }
    }
}

private suspend fun loadDefaultEmailUi(store: AppDataStore, ctx: android.content.Context): DefaultEmailUi {
    val app = store.getDefaultEmailApp() ?: return DefaultEmailUi(false, "Not set", "", null)

    return try {
        val pm = ctx.packageManager
        val ai = pm.getApplicationInfo(app.pkg, 0)
        val label = pm.getApplicationLabel(ai).toString()
        val icon = pm.getApplicationIcon(app.pkg)
        DefaultEmailUi(true, label, app.pkg, icon)
    } catch (_: Exception) {
        // App missing
        DefaultEmailUi(true, "Not installed", app.pkg, null)
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
