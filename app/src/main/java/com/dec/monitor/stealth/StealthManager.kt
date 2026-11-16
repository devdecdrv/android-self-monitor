package com.dec.monitor.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object StealthManager {

    fun hideIcon(context: Context) {
        try {
            val pm = context.packageManager
            // disable the launcher alias, not MainActivity itself
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.MainActivityAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d("StealthManager", "Icon hidden")
        } catch (e: Exception) {
            Log.e("StealthManager", "Hide failed: ${e.message}")
            // fallback: disable MainActivity directly
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, "${context.packageName}.MainActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e2: Exception) {
                Log.e("StealthManager", "Fallback hide failed: ${e2.message}")
            }
        }
    }

    fun showIcon(context: Context) {
        try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.MainActivityAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("StealthManager", "Show failed: ${e.message}")
        }
    }
}
