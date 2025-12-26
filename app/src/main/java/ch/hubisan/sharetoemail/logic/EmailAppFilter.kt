package ch.hubisan.sharetoemail.logic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object EmailAppFilter {

    fun filterToEmailApps(context: Context, baseSendIntent: Intent): List<ComponentName> {
        val pm = context.packageManager

        val sendHandlers = pm.queryIntentActivities(baseSendIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toSet()

        val mailtoIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"))
        val mailtoPkgs = pm.queryIntentActivities(mailtoIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()

        return sendHandlers
            .filter { mailtoPkgs.contains(it.packageName) }
            .sortedBy { it.packageName }
    }
}

