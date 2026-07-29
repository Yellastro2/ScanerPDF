package com.nla.AIscanerPDF

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.AnalyticsEvent
import com.nla.AIscanerPDF.data.billing.PayDeeplinkHandler
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository
import com.nla.AIscanerPDF.presentation.navigation.AppNavGraph
import com.nla.AIscanerPDF.presentation.settings.SettingsViewModel
import com.nla.AIscanerPDF.presentation.theme.ScannerTheme

class MainActivity : ComponentActivity() {

    private val analytics: Analytics by inject()
    private val subscriptions: SubscriptionRepository by inject()
    private var subscriptionRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            analytics.logEvent(AnalyticsEvent.APP_OPENED)
            (subscriptions as? PayDeeplinkHandler)?.onNewIntent(intent)
        }
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            ScannerTheme(themeMode = settings.themeMode) {
                val navController = rememberNavController()
                AppNavGraph(navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (subscriptionRefreshJob?.isActive == true) return
        subscriptionRefreshJob = lifecycleScope.launch {
            runCatching { subscriptions.refreshSubscriptionStatus() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (subscriptions as? PayDeeplinkHandler)?.onNewIntent(intent)
    }
}
