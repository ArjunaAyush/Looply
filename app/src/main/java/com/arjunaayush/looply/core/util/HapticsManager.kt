package com.arjunaayush.looply.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

enum class HapticEffectType {
    CONFIRM,
    TICK,
    HEAVY_DELETE,
    FAVORITE_POP,
    SEEK_TICK,
    LOOP_TOGGLE
}

/**
 * Pure tactile vibrational feedback manager (Zero audio sound effects).
 */
class HapticsManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun playHaptic(
        type: HapticEffectType,
        composeHaptic: HapticFeedback? = null
    ) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val composition = VibrationEffect.startComposition()
                    var handled = false
                    when (type) {
                        HapticEffectType.CONFIRM, HapticEffectType.LOOP_TOGGLE -> {
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                            handled = true
                        }
                        HapticEffectType.TICK, HapticEffectType.SEEK_TICK -> {
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                            handled = true
                        }
                        HapticEffectType.HEAVY_DELETE -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                            } else {
                                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 1.0f)
                            }
                            handled = true
                        }
                        HapticEffectType.FAVORITE_POP -> {
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 20)
                            handled = true
                        }
                    }

                    if (handled) {
                        vibrator.vibrate(composition.compose())
                        return
                    }
                } catch (_: Exception) {
                    // Fallback to predefined effects
                }
            }

            try {
                // Fallback for API 26-29
                when (type) {
                    HapticEffectType.CONFIRM, HapticEffectType.LOOP_TOGGLE -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(30)
                        }
                    }
                    HapticEffectType.TICK, HapticEffectType.SEEK_TICK -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(12)
                        }
                    }
                    HapticEffectType.HEAVY_DELETE -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(70)
                        }
                    }
                    HapticEffectType.FAVORITE_POP -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(25)
                    }
                }
            } catch (_: Exception) {
                // Ignore any security/device vibration failures gracefully
            }
        } else {
            composeHaptic?.performHapticFeedback(
                when (type) {
                    HapticEffectType.CONFIRM, HapticEffectType.LOOP_TOGGLE -> HapticFeedbackType.LongPress
                    HapticEffectType.HEAVY_DELETE -> HapticFeedbackType.LongPress
                    else -> HapticFeedbackType.TextHandleMove
                }
            )
        }
    }
}
