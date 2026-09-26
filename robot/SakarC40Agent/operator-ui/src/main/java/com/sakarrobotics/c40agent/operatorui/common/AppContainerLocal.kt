package com.sakarrobotics.c40agent.operatorui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.sakarrobotics.c40agent.domain.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided - wrap the app in CompositionLocalProvider from SakarOperatorApp")
}
