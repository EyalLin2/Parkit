package com.parkit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.EmailOtpRequest
import com.parkit.app.api.EmailOtpVerify
import com.parkit.app.api.OtpRequest
import com.parkit.app.api.OtpVerify
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Sign in with a 6-digit code sent by email or SMS (email first: it's
 * free; SMS needs a paid provider). The server
 * decides everything security-related (throttling, attempts, expiry); this
 * screen only explains it. [onLoggedIn] gets whether the account still
 * needs its basic details (first sign-in). */
@Composable
fun LoginScreen(api: ApiService, sessionStore: SessionStore, onLoggedIn: (needsProfile: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var byEmail by remember { mutableStateOf(true) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) } // normalised number once a code was sent
    var code by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun explainText(e: Exception): String = when {
        e is HttpException && e.code() == 422 -> context.getString(if (byEmail) R.string.details_email_invalid else R.string.login_phone_invalid)
        e is HttpException && e.code() == 503 -> context.getString(R.string.login_send_failed)
        e is HttpException && e.code() == 429 -> context.getString(R.string.login_too_many)
        e is HttpException && e.code() == 400 && e.response()?.errorBody()?.string()?.contains("expired") == true ->
            context.getString(R.string.login_code_expired)
        e is HttpException && e.code() == 400 -> context.getString(R.string.login_code_wrong)
        else -> context.getString(com.parkit.app.api.ParkItError.of(e).messageRes)
    }

    fun explain(e: Exception): String {
        val code = com.parkit.app.api.ParkItError.of(e).also { com.parkit.app.api.RecentErrors.record(it) }.code
        return context.getString(R.string.error_with_code, explainText(e), code)
    }

    fun sendCode() {
        loading = true
        error = null
        scope.launch {
            try {
                if (byEmail) {
                    val sent = api.requestEmailOtp(EmailOtpRequest(email.trim()))
                    sentTo = sent.email
                    devCode = sent.devCode
                    resendIn = sent.resendAfterSeconds
                } else {
                    val sent = api.requestOtp(OtpRequest(phone))
                    sentTo = sent.phone
                    devCode = sent.devCode
                    resendIn = sent.resendAfterSeconds
                }
                code = ""
            } catch (e: Exception) {
                error = explain(e)
            } finally {
                loading = false
            }
        }
    }

    fun verify() {
        val target = sentTo ?: return
        loading = true
        error = null
        scope.launch {
            try {
                val token = if (byEmail) api.verifyEmailOtp(EmailOtpVerify(target, code)) else api.verifyOtp(OtpVerify(target, code))
                sessionStore.save(token.accessToken, token.userId, "")
                onLoggedIn(token.needsProfile)
            } catch (e: Exception) {
                error = explain(e)
                code = ""
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(sentTo, resendIn) {
        if (resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(112.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = stringResource(R.string.logo_cd), modifier = Modifier.size(76.dp))
        }
        Text(
            "ParkIt",
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = com.parkit.app.ui.theme.SuezOne),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                val target = sentTo
                if (target == null) {
                    LoginMethodToggle(byEmail) { byEmail = it; error = null }
                    Text(
                        stringResource(if (byEmail) R.string.login_email_body else R.string.login_phone_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                    )
                    if (byEmail) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(254) },
                            label = { Text(stringResource(R.string.details_email)) },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (!loading) sendCode() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else OutlinedTextField(
                        value = phone,
                        onValueChange = { v -> phone = v.filter { it.isDigit() || it == '-' || it == '+' }.take(14) },
                        label = { Text(stringResource(R.string.login_phone_label)) },
                        placeholder = { Text("050-1234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (!loading) sendCode() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = ::sendCode,
                        enabled = !loading && (if (byEmail) "@" in email && "." in email else phone.count { it.isDigit() } >= 9),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.login_send_code), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Text(stringResource(R.string.login_code_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.login_code_body, "\u2066${displayTarget(target)}\u2069"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v ->
                            code = v.filter { it.isDigit() }.take(6)
                            if (code.length == 6 && !loading) verify() // no extra tap needed
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, letterSpacing = 10.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Closed beta (no real SMS/email yet): the server hands the
                    // code back, shown like the message that would have arrived.
                    devCode?.let {
                        androidx.compose.material3.Surface(
                            onClick = { code = it },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(stringResource(R.string.login_dev_code_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.login_dev_code, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = ::verify,
                        enabled = !loading && code.length == 6,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.login_verify), style = MaterialTheme.typography.titleMedium)
                    }
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        TextButton(onClick = { sentTo = null; error = null }) { Text(stringResource(if (byEmail) R.string.login_change_email else R.string.login_change_number)) }
                        TextButton(onClick = ::sendCode, enabled = resendIn == 0 && !loading) {
                            Text(if (resendIn > 0) stringResource(R.string.login_resend_in, resendIn) else stringResource(R.string.login_resend))
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        Text(
            stringResource(R.string.login_terms_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        Text(
            stringResource(R.string.login_agree),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { LegalLinks.open(context, LegalLinks.TERMS) }) { Text(stringResource(R.string.legal_terms), style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = { LegalLinks.open(context, LegalLinks.PRIVACY) }) { Text(stringResource(R.string.legal_privacy), style = MaterialTheme.typography.labelMedium) }
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LoginMethodToggle(byEmail: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = byEmail,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.login_by_email)) }
        SegmentedButton(
            selected = !byEmail,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.login_by_phone)) }
    }
}

/** "+972501234567" → "050-123-4567", the way Israelis write their number;
 * email addresses pass through unchanged. */
internal fun displayTarget(target: String): String {
    val m = Regex("""^\+972(5\d)(\d{3})(\d{4})$""").matchEntire(target) ?: return target
    val (prefix, mid, end) = m.destructured
    return "0$prefix-$mid-$end"
}
