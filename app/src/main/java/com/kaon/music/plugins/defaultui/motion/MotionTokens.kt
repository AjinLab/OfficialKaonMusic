package com.kaon.music.plugins.defaultui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object MotionTokens {
    const val Fast = 120
    const val Normal = 220
    const val Slow = 400

    val SpringLow = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    val SpringMedium = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val SpringHigh = spring<Float>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh)
}
