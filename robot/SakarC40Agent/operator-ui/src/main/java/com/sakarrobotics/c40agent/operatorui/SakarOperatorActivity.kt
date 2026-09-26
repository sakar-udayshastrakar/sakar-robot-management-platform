package com.sakarrobotics.c40agent.operatorui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sakarrobotics.c40agent.domain.di.AppContainerHolder
import com.sakarrobotics.c40agent.operatorui.nav.SakarOperatorApp

/**
 * The Sakar CleanBot on-robot operator app's single Activity/NavHost entry
 * point (replaces the previous diagnostic-only launcher). The real
 * [com.sakarrobotics.c40agent.domain.di.AppContainer] is obtained from
 * [android.app.Application] via [AppContainerHolder] - constructed once by
 * SakarC40Application (:app) - rather than this module constructing any
 * :robot/:data types itself.
 */
class SakarOperatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AppContainerHolder).appContainer
        setContent { SakarOperatorApp(container) }
    }
}
