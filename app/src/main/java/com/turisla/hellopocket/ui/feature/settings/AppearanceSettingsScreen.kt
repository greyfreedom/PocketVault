package com.turisla.hellopocket.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.UserPreferencesRepository
import org.koin.compose.viewmodel.koinViewModel

/**
 * 外观设置页面
 * 包含语言、主题等设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    viewModel: SettingsPageViewModel = koinViewModel(),
) {
    val showLanguageDialog by viewModel.showLanguageDialog.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    
    val showThemeDialog by viewModel.showThemeDialog.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = currentLanguage, onDismiss = viewModel::onLanguageDialogDismissed, onLanguageSelected = viewModel::onLanguageSelected
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = currentTheme, onDismiss = viewModel::onThemeDialogDismissed, onThemeSelected = viewModel::onThemeSelected
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_appearance)) }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            })
        }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { SettingsDivider() }
            item {
                val languageDesc = when (currentLanguage) {
                    UserPreferencesRepository.LANGUAGE_ENGLISH -> stringResource(R.string.language_english)
                    UserPreferencesRepository.LANGUAGE_CHINESE -> stringResource(R.string.language_chinese)
                    UserPreferencesRepository.LANGUAGE_KOREAN -> stringResource(R.string.language_korean)
                    UserPreferencesRepository.LANGUAGE_VIETNAMESE -> stringResource(R.string.language_vietnamese)
                    UserPreferencesRepository.LANGUAGE_HINDI -> stringResource(R.string.language_hindi)
                    UserPreferencesRepository.LANGUAGE_SPANISH -> stringResource(R.string.language_spanish)
                    UserPreferencesRepository.LANGUAGE_PORTUGUESE -> stringResource(R.string.language_portuguese)
                    else -> stringResource(R.string.language_follow_system)
                }
                SettingsListItem(
                    title = stringResource(R.string.language), description = languageDesc, icon = Icons.Outlined.Language, onClick = { viewModel.onLanguageClicked() })
            }
            item { SettingsDivider() }
            item {
                val themeDesc = when (currentTheme) {
                    UserPreferencesRepository.THEME_LIGHT -> stringResource(R.string.theme_light)
                    UserPreferencesRepository.THEME_DARK -> stringResource(R.string.theme_dark)
                    else -> stringResource(R.string.theme_follow_system)
                }
                SettingsListItem(
                    title = stringResource(R.string.theme), description = themeDesc, icon = Icons.Outlined.Palette, onClick = { viewModel.onThemeClicked() })
            }
            item { SettingsDivider() }
        }
    }
}

@Composable
private fun LanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
) {
    val languages = listOf(
        stringResource(R.string.language_follow_system) to UserPreferencesRepository.LANGUAGE_SYSTEM,
        stringResource(R.string.language_english) to UserPreferencesRepository.LANGUAGE_ENGLISH,
        stringResource(R.string.language_chinese) to UserPreferencesRepository.LANGUAGE_CHINESE,
        stringResource(R.string.language_korean) to UserPreferencesRepository.LANGUAGE_KOREAN,
        stringResource(R.string.language_vietnamese) to UserPreferencesRepository.LANGUAGE_VIETNAMESE,
        stringResource(R.string.language_hindi) to UserPreferencesRepository.LANGUAGE_HINDI,
        stringResource(R.string.language_spanish) to UserPreferencesRepository.LANGUAGE_SPANISH,
        stringResource(R.string.language_portuguese) to UserPreferencesRepository.LANGUAGE_PORTUGUESE,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(id = R.string.language)) },
        text = {
            Column {
                languages.forEach { (displayName, languageCode) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = (currentLanguage == languageCode), onClick = { onLanguageSelected(languageCode) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (currentLanguage == languageCode), onClick = { onLanguageSelected(languageCode) })
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        },
    )
}

@Composable
private fun ThemeDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit,
) {
    val themes = listOf(
        stringResource(R.string.theme_follow_system) to UserPreferencesRepository.THEME_SYSTEM,
        stringResource(R.string.theme_light) to UserPreferencesRepository.THEME_LIGHT,
        stringResource(R.string.theme_dark) to UserPreferencesRepository.THEME_DARK,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(id = R.string.theme)) },
        text = {
            Column {
                themes.forEach { (displayName, themeCode) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = (currentTheme == themeCode), onClick = { onThemeSelected(themeCode) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (currentTheme == themeCode), onClick = { onThemeSelected(themeCode) })
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        },
    )
}
