package com.you.visionaid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Opens the system page where this application's permissions can be changed. */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}
