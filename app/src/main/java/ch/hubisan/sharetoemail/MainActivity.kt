package ch.hubisan.sharetoemail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import ch.hubisan.sharetoemail.ui.theme.ShareToEmailTheme
import ch.hubisan.sharetoemail.ui.theme.ShareToEmailThemeExtras
import kotlinx.coroutines.launch

/**
 * Main (settings) activity of the app.
 *
 * This screen is used to configure:
 * - Recipient email addresses for share slots A/B/C
 * - The default email app (used directly by ShareActivity; no chooser)
 */
class MainActivity : ComponentActivity() {

    /**
     * Sets up the Jetpack Compose UI and passes [AppDataStore] into the settings screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppDataStore(this)

        setContent {
            // ✅ use your app theme (light/dark + optional dynamic color)
            ShareToEmailTheme {
                SettingsScreen(store = store)
            }
        }
    }
}

/**
 * UI model representing the configured default email app.
 */
private data class DefaultEmailUi(
    val isSet: Boolean,
    val label: String,
    val pkg: String,
    val icon: Drawable?
)

/**
 * Settings UI containing recipient fields and the default email app picker.
 */
@Composable
private fun SettingsScreen(store: AppDataStore) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var emailA by remember { mutableStateOf("") }
    var emailB by remember { mutableStateOf("") }
    var emailC by remember { mutableStateOf("") }

    var defaultUi by remember {
        mutableStateOf(DefaultEmailUi(isSet = false, label = "Not set", pkg = "", icon = null))
    }

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
        defaultUi = loadDefaultEmailUi(store, ctx)
    }

    fun saveRecipientA(v: String) {
        emailA = v
        scope.launch { store.setRecipientAEmail(v) }
    }

    fun saveRecipientB(v: String) {
        emailB = v
        scope.launch { store.setRecipientBEmail(v) }
    }

    fun saveRecipientC(v: String) {
        emailC = v
        scope.launch { store.setRecipientCEmail(v) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Share to Email — Settings", style = MaterialTheme.typography.titleLarge)

            RecipientField(
                label = "Recipient for Share to @A",
                value = emailA,
                onChange = { saveRecipientA(it.trim()) }
            )

            RecipientField(
                label = "Recipient for Share to @B",
                value = emailB,
                onChange = { saveRecipientB(it.trim()) }
            )

            RecipientField(
                label = "Recipient for Share to @C",
                value = emailC,
                onChange = { saveRecipientC(it.trim()) }
            )

            HorizontalDivider()

            Text("Default email app", style = MaterialTheme.typography.titleMedium)

            DefaultEmailAppCard(
                ui = defaultUi,
                onChoose = { pickerLauncher.launch(Intent(ctx, EmailAppPickerActivity::class.java)) },
                onReset = {
                    scope.launch {
                        store.setDefaultEmailApp(null)
                        defaultUi = loadDefaultEmailUi(store, ctx)
                    }
                }
            )

            if (!defaultUi.isSet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Sending will not work until you select a default email app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = "This app will be used directly for sending (no chooser).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}

/**
 * A recipient input field with a status indicator icon.
 * The icon color reflects the state:
 * - Green Check: Valid email address
 * - Red Error: Invalid email format
 * - Yellow Warning: Empty field (target remains disabled)
 */
@Composable
private fun RecipientField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    val trimmed = value.trim()
    val isEmpty = trimmed.isBlank()
    val isValid = !isEmpty && android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    val isInvalid = !isEmpty && !isValid

    val statusIcon = when {
        isValid -> Icons.Filled.CheckCircle
        isInvalid -> Icons.Filled.Error
        else -> Icons.Filled.Warning
    }

    // ✅ theme-aware success/warning colors (light & dark)
    val iconTint = when {
        isValid -> ShareToEmailThemeExtras.statusColors.success
        isInvalid -> MaterialTheme.colorScheme.error
        else -> ShareToEmailThemeExtras.statusColors.warning
    }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        isError = isInvalid,
        trailingIcon = {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = iconTint
            )
        },
        supportingText = {
            when {
                isEmpty -> Text(
                    text = "Optional (target disabled without email)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                isInvalid -> Text(
                    text = "Invalid email address",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Card UI for displaying and changing the default email app.
 */
@Composable
private fun DefaultEmailAppCard(
    ui: DefaultEmailUi,
    onChoose: () -> Unit,
    onReset: () -> Unit
) {
    val iconBmp = remember(ui.pkg, ui.icon) {
        ui.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
    }

    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (ui.isSet)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (ui.isSet) ui.label else "Not selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (ui.isSet) {
                    Text(
                        text = ui.pkg,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Required for sending emails",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onChoose) { Text("Choose…") }
                OutlinedButton(enabled = ui.isSet, onClick = onReset) { Text("Reset") }
            }
        }
    }
}

/**
 * Loads the default email app from [AppDataStore] and maps it to [DefaultEmailUi].
 */
private suspend fun loadDefaultEmailUi(store: AppDataStore, ctx: Context): DefaultEmailUi {
    val app = store.getDefaultEmailApp() ?: return DefaultEmailUi(false, "Not set", "", null)

    return try {
        val pm = ctx.packageManager
        val ai = pm.getApplicationInfo(app.pkg, 0)
        val label = pm.getApplicationLabel(ai).toString()
        val icon = pm.getApplicationIcon(app.pkg)
        DefaultEmailUi(true, label, app.pkg, icon)
    } catch (_: Exception) {
        DefaultEmailUi(true, "Not installed", app.pkg, null)
    }
}
