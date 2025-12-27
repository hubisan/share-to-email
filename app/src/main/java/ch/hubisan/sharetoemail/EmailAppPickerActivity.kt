package ch.hubisan.sharetoemail

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import ch.hubisan.sharetoemail.data.AppDataStore
import ch.hubisan.sharetoemail.ui.theme.ShareToEmailTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picker activity that lets the user choose a default email app.
 * Compose-based so it follows light/dark (and dynamic color).
 */
class EmailAppPickerActivity : ComponentActivity() {

    data class EmailTarget(
        val pkg: String,
        val cls: String,
        val label: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShareToEmailTheme {
                Surface {
                    val scope = rememberCoroutineScope()
                    val targets = remember { queryEmailTargets() }

                    LaunchedEffect(Unit) {
                        if (targets.isEmpty()) {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    }

                    if (targets.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = {
                                setResult(RESULT_CANCELED)
                                finish()
                            },
                            title = { Text("Choose default email app") },
                            text = {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(targets, key = { it.pkg + it.cls }) { t ->
                                        EmailTargetRow(
                                            target = t,
                                            onClick = {
                                                scope.launch {
                                                    // Save in IO
                                                    withContext(Dispatchers.IO) {
                                                        AppDataStore(this@EmailAppPickerActivity)
                                                            .setDefaultEmailApp(
                                                                AppDataStore.DefaultEmailApp(
                                                                    pkg = t.pkg,
                                                                    cls = t.cls
                                                                )
                                                            )
                                                    }

                                                    setResult(
                                                        RESULT_OK,
                                                        Intent()
                                                            .putExtra(EXTRA_PKG, t.pkg)
                                                            .putExtra(EXTRA_CLS, t.cls)
                                                    )
                                                    finish()
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                // no confirm button; selection is immediate
                            }
                        )
                    }
                }
            }
        }
    }

    private fun queryEmailTargets(): List<EmailTarget> {
        val probe = Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri() }
        val resolved = packageManager.queryIntentActivities(probe, 0)

        return resolved.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            EmailTarget(
                pkg = ai.packageName,
                cls = ai.name,
                label = ri.loadLabel(packageManager).toString(),
                icon = ri.loadIcon(packageManager)
            )
        }.sortedBy { it.label.lowercase() }
    }

    companion object {
        const val EXTRA_PKG = "extra_pkg"
        const val EXTRA_CLS = "extra_cls"
    }
}

@Composable
private fun EmailTargetRow(
    target: EmailAppPickerActivity.EmailTarget,
    onClick: () -> Unit
) {
    val bmp = remember(target.pkg, target.cls, target.icon) {
        target.icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )

        Text(
            text = target.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
