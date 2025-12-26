package ch.hubisan.sharetoemail

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ch.hubisan.sharetoemail.data.AppDataStore
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri

class EmailAppPickerActivity : Activity() {

    data class EmailTarget(
        val pkg: String,
        val cls: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only apps that handle ACTION_SENDTO mailto:
        val probe = Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri() }
        val resolved = packageManager.queryIntentActivities(probe, 0)

        val targets = resolved.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            val label = ri.loadLabel(packageManager).toString()
            val icon = ri.loadIcon(packageManager)
            EmailTarget(
                pkg = ai.packageName,
                cls = ai.name, // fully qualified activity class name
                label = label,
                icon = icon
            )
        }.sortedBy { it.label.lowercase() }

        if (targets.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val adapter = EmailTargetAdapter(this, targets)

        AlertDialog.Builder(this)
            .setTitle("Default E-Mail App wählen")
            .setAdapter(adapter) { _, which ->
                val t = targets[which]
                runBlocking {
                    AppDataStore(this@EmailAppPickerActivity).setDefaultEmailApp(
                        AppDataStore.DefaultEmailApp(pkg = t.pkg, cls = t.cls)
                    )
                }
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_PKG, t.pkg).putExtra(EXTRA_CLS, t.cls)
                )
                finish()
            }
            .setOnCancelListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private class EmailTargetAdapter(
        ctx: Activity,
        private val items: List<EmailTarget>
    ) : ArrayAdapter<EmailTarget>(ctx, 0, items) {

        private val iconSizePx = dp(ctx, 32)
        private val rowPadH = dp(ctx, 16)
        private val rowPadV = dp(ctx, 12)
        private val gap = dp(ctx, 16)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]

            val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)

                val iv = ImageView(context).apply {
                    id = android.R.id.icon
                    layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                addView(iv)

                val tv = TextView(context).apply {
                    id = android.R.id.text1
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { marginStart = gap }

                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(tv)
            }

            val iv = row.findViewById<ImageView>(android.R.id.icon)
            val tv = row.findViewById<TextView>(android.R.id.text1)

            iv.setImageDrawable(item.icon)
            tv.text = item.label

            return row
        }

        companion object {
            private fun dp(ctx: Activity, dp: Int): Int =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp.toFloat(),
                    ctx.resources.displayMetrics
                ).toInt()
        }
    }

    companion object {
        const val EXTRA_PKG = "extra_pkg"
        const val EXTRA_CLS = "extra_cls"
    }
}
