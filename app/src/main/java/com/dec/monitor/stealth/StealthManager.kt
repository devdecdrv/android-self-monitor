package com.dec.monitor.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object StealthManager {

    // hide app icon from launcher
    fun hideIcon(context: Context) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            ComponentName(context, "com.dec.monitor.MainActivity"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    // restore icon
    fun showIcon(context: Context) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            ComponentName(context, "com.dec.monitor.MainActivity"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
