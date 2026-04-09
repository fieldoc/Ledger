package com.example.todowallapp.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

enum class AppHapticPattern {
    NAVIGATE,
    CONFIRM,
    ERROR
}

fun performAppHaptic(
    view: View,
    context: Context,
    pattern: AppHapticPattern
) {
    val handled = when (pattern) {
        AppHapticPattern.NAVIGATE -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        AppHapticPattern.CONFIRM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            else false
        AppHapticPattern.ERROR ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            else false
    }

    if (!handled) {
        val durationMs = when (pattern) {
            AppHapticPattern.NAVIGATE -> 12L
            AppHapticPattern.CONFIRM -> 22L
            AppHapticPattern.ERROR -> 18L
        }
        vibrateFallback(context, durationMs)
    }
}

private fun vibrateFallback(context: Context, durationMs: Long) {
    val vibrator = getVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
