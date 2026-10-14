package com.parkit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.RewardOut
import com.parkit.app.api.ShopOut
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch

/**
 * The rewards shop. Points earned by reporting and handing spots on buy car
 * finishes (ParkIt's own) and benefits from partner businesses (a one-time
 * code). Buying never lowers the level — that follows lifetime points.
 */
@Composable
fun RewardsScreen(
    api: ApiService,
    sessionStore: SessionStore,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hebrew = context.resources.configuration.locales[0].language in setOf("he", "iw")
    var shop by remember { mutableStateOf<ShopOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<RewardOut?>(null) }
    var newCode by remember { mutableStateOf<String?>(null) }
    var special by remember { mutableStateOf(sessionStore.carSpecial) }
    val car = sessionStore.driver.value.preferences
    val paint = CarPaints.getOrNull(sessionStore.carPaint)
        ?: car.carPaint?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: CarPaints[0]

    LaunchedEffect(Unit) {
        try {
            shop = api.rewards()
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
        }
    }

    fun buy(reward: RewardOut) {
        scope.launch {
            try {
                val updated = api.redeemReward(reward.id)
                shop = updated
                if (reward.kind == "cosmetic") {
                    // Wear it right away — that's the fun part.
                    special = reward.key
                    sessionStore.carSpecial = reward.key
                } else {
                    newCode = updated.redemptions.firstOrNull { it.rewardId == reward.id }?.code
                }
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        item { ScreenTopBar(title = stringResource(R.string.rewards_title), onBack = onBack) }
        val s = shop
        if (s == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        // Balance: the number that matters here, big.
        item {
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B4F91), Color(0xFF2E7BD6)))),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.rewards_balance_label), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.rewards_balance_value, s.balance),
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.rewards_level_safe), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Partner benefits first: they're the real reason to collect.
        item { SectionTitle(stringResource(R.string.rewards_partners)) }
        val partners = s.rewards.filter { it.kind == "partner" }
        if (partners.isEmpty()) {
            item {
                SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(stringResource(R.string.rewards_partners_soon_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.rewards_partners_soon_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            partners.forEach { reward ->
                item {
                    PartnerCard(reward, hebrew, s.balance) { confirm = reward }
                }
            }
        }

        // ParkIt's own finishes for your car, each previewed on your car.
        item { SectionTitle(stringResource(R.string.rewards_car_section)) }
        s.rewards.filter { it.kind == "cosmetic" }.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    pair.forEach { reward ->
                        FinishCard(
                            reward = reward,
                            hebrew = hebrew,
                            balance = s.balance,
                            size = car.vehicleSize ?: "regular",
                            paint = paint,
                            wearing = special == reward.key,
                            modifier = Modifier.weight(1f),
                            onBuy = { confirm = reward },
                            onWear = {
                                special = if (special == reward.key) null else reward.key
                                sessionStore.carSpecial = special
                            },
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }

        if (s.redemptions.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.rewards_my_codes)) }
            s.redemptions.forEach { r ->
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(if (hebrew) r.titleHe else r.titleEn, style = MaterialTheme.typography.titleSmall)
                                r.partner?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(r.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item { Box(Modifier.height(32.dp)) }
    }

    confirm?.let { reward ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (hebrew) reward.titleHe else reward.titleEn) },
            text = { Text(stringResource(R.string.rewards_confirm_body, reward.cost)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    buy(reward)
                }) { Text(stringResource(R.string.rewards_buy)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    newCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newCode = null },
            title = { Text(stringResource(R.string.rewards_code_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.rewards_code_body),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { newCode = null }) { Text(stringResource(R.string.level_up_ok)) } },
        )
    }
}

@Composable
private fun PartnerCard(reward: RewardOut, hebrew: Boolean, balance: Int, onBuy: () -> Unit) {
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(if (hebrew) reward.titleHe else reward.titleEn, style = MaterialTheme.typography.titleSmall)
                reward.partner?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                (if (hebrew) reward.descriptionHe else reward.descriptionEn)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PriceButton(reward.cost, balance, owned = false, onBuy)
        }
    }
}

@Composable
private fun FinishCard(
    reward: RewardOut,
    hebrew: Boolean,
    balance: Int,
    size: String,
    paint: Color,
    wearing: Boolean,
    modifier: Modifier,
    onBuy: () -> Unit,
    onWear: () -> Unit,
) {
    SectionCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            CarArt(size = size, paint = paint, outline = MaterialTheme.colorScheme.outline, special = reward.key, modifier = Modifier.width(120.dp))
            Text(if (hebrew) reward.titleHe else reward.titleEn, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(top = 8.dp)) {
                if (reward.owned) {
                    TextButton(onClick = onWear) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(if (wearing) R.string.rewards_wearing else R.string.rewards_wear),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else {
                    PriceButton(reward.cost, balance, owned = false, onBuy)
                }
            }
        }
    }
}

/** "300 נק׳" — filled when affordable, a lock and what's missing when not. */
@Composable
private fun PriceButton(cost: Int, balance: Int, owned: Boolean, onBuy: () -> Unit) {
    val canBuy = !owned && balance >= cost
    if (canBuy) {
        Button(onClick = onBuy, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.rewards_cost, cost))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = false) {}.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.rewards_missing, cost - balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
