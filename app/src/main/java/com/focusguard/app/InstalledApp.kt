package com.focusguard.app

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val name: String,
    val icon: Drawable
)
