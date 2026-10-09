package com.comp90018.flashcards.ui.play

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the screen in landscape while this is on screen, then restores the previous orientation.
 *
 * Used by the 2-player and 4-player modes, where the phone is held up to the forehead. Either
 * landscape direction is allowed, so the phone can be held either way round.
 */
@Composable
fun LockLandscape() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (activity != null && previous != null) activity.requestedOrientation = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
