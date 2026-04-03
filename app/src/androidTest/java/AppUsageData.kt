package com.example.spyscan

import android.graphics.drawable.Drawable

data class AppUsageData(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable?,
    val durationMillis: Long,
    val lastUsedMillis: Long
)
