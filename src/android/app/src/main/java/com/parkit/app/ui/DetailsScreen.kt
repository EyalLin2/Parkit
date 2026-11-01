package com.parkit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.DetailsUpdate
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/** Basic details: asked once after the first phone sign-in ([firstTime]),
 * editable later from the profile. Only "first name + last initial" is
 * ever shown to other drivers. */
@Composable
fun DetailsScreen(api: ApiService, sessionStore: SessionStore, firstTime: Boolean, onDone: () -> Unit, onSessionExpired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(avatarId((0..15).random())) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var emailLocked by remember { mutableStateOf(false) }
    val emailOk = EMAIL.matches(email.trim())
    val valid = first.isNotBlank() && last.isNotBlank() && emailOk

    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    // Prefill whatever we already know (e.g. the address an email sign-in used).
    // Saving waits for it: until then we don't know whether the email is locked.
    LaunchedEffect(reload) {
        loadError = null
        try {
            api.myProfile().account?.let {
                if (first.isBlank()) first = it.firstName.orEmpty()
                if (last.isBlank()) last = it.lastName.orEmpty()
                if (email.isBlank() || it.emailVerified) email = it.email.orEmpty()
                emailLocked = it.emailVerified
            }
            loaded = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else loadError = e.friendlyMessage(context, aboutSpot = false)
        }
    }

    fun save() {
        saving = true
        error = null
        scope.launch {
            try {
                api.updateDetails(DetailsUpdate(first.trim(), last.trim(), email.trim()))
                // Without the grid nobody chose one, so no picture is set for them.
                if (firstTime && com.parkit.app.Features.FIRST_RUN_AVATAR) avatar?.let { runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(it)) } }
                val token = sessionStore.token.value
                val userId = sessionStore.userId.value
                if (token != null && userId != null) sessionStore.save(token, userId, "${first.trim()} ${last.trim().first()}.")
                onDone()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context, aboutSpot = false, conflictRes = R.string.details_email_taken)
            } finally {
                saving = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        if (!firstTime) ScreenTopBar(title = stringResource(R.string.details_title), onBack = onDone)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (firstTime) {
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.details_welcome_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.details_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                if (com.parkit.app.Features.FIRST_RUN_AVATAR) {
                    Text(stringResource(R.string.details_pick_avatar), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 10.dp))
                    AvatarGrid(selected = avatar, onPick = { avatar = it }, modifier = Modifier.padding(bottom = 20.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = first,
                onValueChange = { first = it.take(60) },
                label = { Text(stringResource(R.string.details_first_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = last,
                onValueChange = { last = it.take(60) },
                label = { Text(stringResource(R.string.details_last_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text(stringResource(R.string.details_email)) },
                singleLine = true,
                enabled = loaded && !emailLocked,
                isError = email.isNotBlank() && !emailOk,
                supportingText = { if (email.isNotBlank() && !emailOk) Text(stringResource(R.string.details_email_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.details_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                androidx.compose.material3.TextButton(onClick = { reload++ }) { Text(stringResource(R.string.map_offline_retry)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
        Button(
            onClick = ::save,
            enabled = valid && loaded && !saving,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().height(52.dp),
        ) {
            if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(if (firstTime) R.string.driver_save_and_continue else R.string.driver_save))
        }
    }
}
