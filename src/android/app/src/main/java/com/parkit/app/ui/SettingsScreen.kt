package com.parkit.app.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Face
import com.parkit.app.api.friendlyMessage
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.locale.LocaleManager
import com.parkit.app.ui.theme.ThemeManager

/** A real settings screen (grouped rows, current value on the trailing
 * edge, a picker on tap) — the iOS/Android/Pango convention everyone
 * already knows how to use — instead of a stack of cards with chips
 * squeezed into each one. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: suspend () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenDriver: () -> Unit,
    carLabel: String?,
    onSendFeedback: suspend (String) -> Unit,
    onOpenTips: () -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) } // "language" | "theme" | "logout" | "delete"
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val languageCode = LocaleManager.getLanguage(context) ?: java.util.Locale.getDefault().language
    val languageLabel = if (languageCode == "he" || languageCode == "iw") stringResource(R.string.profile_language_hebrew) else stringResource(R.string.profile_language_english)
    val themeMode by ThemeManager.getOverride()
    val themeLabel = stringResource(
        when (themeMode) {
            "light" -> R.string.profile_theme_light
            "dark" -> R.string.profile_theme_dark
            else -> R.string.profile_theme_system
        },
    )
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        // Account things live here, not in the profile (which is about progress).
        SectionTitle(stringResource(R.string.settings_section_account))
        SectionCard {
            Column {
                SettingsRow(Icons.Filled.Person, stringResource(R.string.details_title), subtitle = stringResource(R.string.settings_details_sub), onClick = onOpenDetails)
                RowDivider()
                SettingsRow(Icons.Filled.DirectionsCar, stringResource(R.string.driver_title), subtitle = carLabel ?: stringResource(R.string.driver_summary_empty_body), onClick = onOpenDriver)
            }
        }

        SectionTitle(stringResource(R.string.settings_section_preferences))
        SectionCard {
            Column {
                SettingsRow(Icons.Filled.Language, stringResource(R.string.profile_language_label), value = languageLabel) { dialog = "language" }
                RowDivider()
                SettingsRow(Icons.Filled.DarkMode, stringResource(R.string.settings_appearance), value = themeLabel) { dialog = "theme" }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_community))
        SectionCard {
            Column {
            val shareTitle = stringResource(R.string.profile_share_title)
            val shareMessage = stringResource(R.string.profile_share_message)
            SettingsRow(Icons.Filled.Lightbulb, stringResource(R.string.settings_tips), onClick = onOpenTips)
            RowDivider()
            SettingsRow(Icons.Filled.Feedback, stringResource(R.string.feedback_title), subtitle = stringResource(R.string.feedback_subtitle)) { dialog = "feedback" }
            RowDivider()
            SettingsRow(Icons.Filled.PersonAdd, stringResource(R.string.profile_share_button), subtitle = stringResource(R.string.settings_invite_subtitle)) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_about))
        SectionCard {
            Column {
                SettingsRow(Icons.Filled.Info, stringResource(R.string.settings_version), value = version, showChevron = false)
                RowDivider()
                SettingsRow(Icons.Filled.Map, stringResource(R.string.settings_map_data), value = stringResource(R.string.settings_map_data_value), showChevron = false)
                RowDivider()
                SettingsRow(Icons.Filled.Face, stringResource(R.string.settings_avatars), subtitle = stringResource(R.string.settings_avatars_value), showChevron = false)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionCard {
            Column {
            SettingsRow(
                Icons.AutoMirrored.Filled.Logout,
                stringResource(R.string.map_logout_cd),
                tint = MaterialTheme.colorScheme.error,
                showChevron = false,
            ) { dialog = "logout" }
            RowDivider()
            // Required by Google Play and the App Store for any app with accounts.
            SettingsRow(
                Icons.Filled.DeleteForever,
                stringResource(R.string.settings_delete_account),
                tint = MaterialTheme.colorScheme.error,
                showChevron = false,
            ) { dialog = "delete" }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }

    when (dialog) {
        "language" -> ChoiceDialog(
            title = stringResource(R.string.profile_language_label),
            options = listOf("he" to stringResource(R.string.profile_language_hebrew), "en" to stringResource(R.string.profile_language_english)),
            selected = if (languageCode == "iw") "he" else languageCode,
            onDismiss = { dialog = null },
            onSelect = { code ->
                dialog = null
                if (code != languageCode) {
                    LocaleManager.setLanguage(context, code)
                    (context as? Activity)?.recreate()
                }
            },
        )
        "theme" -> ChoiceDialog(
            title = stringResource(R.string.settings_appearance),
            options = listOf(
                null to stringResource(R.string.profile_theme_system),
                "light" to stringResource(R.string.profile_theme_light),
                "dark" to stringResource(R.string.profile_theme_dark),
            ),
            selected = themeMode,
            onDismiss = { dialog = null },
            onSelect = { mode ->
                dialog = null
                ThemeManager.setOverride(context, mode)
            },
        )
        "logout" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; onLogout() }) {
                    Text(stringResource(R.string.map_logout_cd), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
        "feedback" -> FeedbackDialog(onSend = onSendFeedback, onDismiss = { dialog = null })
        "delete" -> AlertDialog(
            onDismissRequest = { if (!deleting) dialog = null },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_delete_body))
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        deleteError = null
                        scope.launch {
                            try {
                                onDeleteAccount()
                            } catch (e: Exception) {
                                deleteError = e.friendlyMessage(context)
                                deleting = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.settings_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (showChevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}


/** Free-text feedback for testers. The app version, device and the last
 * error codes are attached automatically. */
@Composable
private fun FeedbackDialog(onSend: suspend (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(if (sent) R.string.feedback_thanks_title else R.string.feedback_title)) },
        text = {
            if (sent) {
                Text(stringResource(R.string.feedback_thanks_body))
            } else {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(4000) },
                        placeholder = { Text(stringResource(R.string.feedback_hint)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.feedback_attached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.driver_close)) }
            } else {
                TextButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                onSend(text.trim())
                                sent = true
                            } catch (e: Exception) {
                                error = e.friendlyMessage(context)
                            } finally {
                                sending = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.feedback_send)) }
            }
        },
        dismissButton = { if (!sent) TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
