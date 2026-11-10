package de.pauseqr.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ServiceUtil {
    fun isBlockerServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
