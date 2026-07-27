package com.nla.AIscanerPDF.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.BuildConfig
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.domain.model.ThemeMode
import com.nla.AIscanerPDF.presentation.common.ConfirmDialog
import com.nla.AIscanerPDF.presentation.navigation.Routes
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showDeleteAll by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                FilterChip(settings.themeMode == ThemeMode.SYSTEM,
                    { viewModel.onThemeSelected(ThemeMode.SYSTEM) },
                    { Text(stringResource(R.string.settings_theme_system)) })
                FilterChip(settings.themeMode == ThemeMode.LIGHT,
                    { viewModel.onThemeSelected(ThemeMode.LIGHT) },
                    { Text(stringResource(R.string.settings_theme_light)) })
                FilterChip(settings.themeMode == ThemeMode.DARK,
                    { viewModel.onThemeSelected(ThemeMode.DARK) },
                    { Text(stringResource(R.string.settings_theme_dark)) })
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_auto_detect),
                    Modifier.weight(1f),
                )
                Switch(
                    checked = settings.autoDetectCorners,
                    onCheckedChange = viewModel::onAutoDetectChanged,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TextButton(onClick = { showPrivacy = true }) {
                Text(stringResource(R.string.settings_privacy))
            }
            TextButton(onClick = { navController.navigate(Routes.PREMIUM) }) {
                Text(stringResource(R.string.settings_subscription))
            }
            TextButton(onClick = { showDeleteAll = true }) {
                Text(
                    stringResource(R.string.settings_delete_all),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all),
            message = stringResource(R.string.settings_delete_all_message),
            onConfirm = {
                viewModel.onDeleteAll()
                showDeleteAll = false
            },
            onDismiss = { showDeleteAll = false },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.settings_privacy)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.privacy_policy_text))
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
}
