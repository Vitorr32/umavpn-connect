package com.umavpn

import android.content.Context
import android.content.Intent
import android.util.Log
import com.umavpn.model.GameVersion

object GameLauncher {

    private const val TAG = "GameLauncher"

    /** Returns true if the game was launched, false if not installed or unavailable. */
    fun launch(context: Context, version: GameVersion): Boolean {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(version.launchPackageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: run {
                Log.w(TAG, "Game not installed: ${version.launchPackageName}")
                return false
            }

        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to launch ${version.launchPackageName}: ${error.message}")
            false
        }
    }
}
