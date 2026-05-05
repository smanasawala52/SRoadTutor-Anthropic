package com.sroadtutor.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.R
import com.sroadtutor.i18n.AppLocales
import kotlinx.coroutines.launch

/**
 * Language picker. Saves the choice to DataStore via {@link AppLocales} and
 * recreates the hosting Activity so every resource string is re-resolved
 * against the new locale (and RTL languages flip layout direction).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val current by AppLocales.currentLanguage(context).collectAsStateWithLifecycle(
        initialValue = AppLocales.Language.ENGLISH
    )
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(AppLocales.Language.entries.toList(), key = { it.tag }) { lang ->
                val resId = remember(lang) {
                    context.resources.getIdentifier(lang.displayResName, "string", context.packageName)
                }
                ListItem(
                    headlineContent = {
                        Text(
                            if (resId != 0) context.getString(resId) else lang.tag,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = { Text(lang.tag) },
                    trailingContent = {
                        if (lang == current) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            AppLocales.setLanguage(context, lang)
                            // Recreate the Activity so every resource string,
                            // and RTL layout direction for ar/ur, is re-applied.
                            (context as? Activity)?.recreate()
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

