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
    var avatar by remember { mutableStateOf<String?>(null) }
    val car = sessionStore.driver.value.preferences
    val paint = CarPaints.getOrNull(sessionStore.carPaint)
        ?: car.carPaint?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: CarPaints[0]

    LaunchedEffect(Unit) {
        try {
            shop = api.rewards()
            // Only wear what this account owns.
            val owned = shop?.rewards?.filter { it.owned }?.mapNotNull { it.key }.orEmpty()
            if (special != null && special !in owned) {
                special = null
                sessionStore.carSpecial = null
            }
            avatar = runCatching { api.myProfile().avatar }.getOrNull()
        } catch (e: Exception) {
            if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
        }
    }

    fun buy(reward: RewardOut) {
        scope.launch {
            try {
                val updated = api.redeemReward(reward.id)
                shop = updated
                if (reward.key?.startsWith("avatar_legend_") == true) {
                    // Wear it right away — that's the fun part.
                    runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(reward.key)) }
                    avatar = reward.key
                } else if (reward.kind == "cosmetic") {
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

        // Legends: famous Jewish & Israeli figures as your avatar.
        item { SectionTitle(stringResource(R.string.rewards_legends_section)) }
        s.rewards.filter { it.key?.startsWith("avatar_legend_") == true }.chunked(3).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    row.forEach { reward ->
                        LegendCard(
                            reward = reward,
                            hebrew = hebrew,
                            balance = s.balance,
                            wearing = avatar == reward.key,
                            modifier = Modifier.weight(1f),
                            onBuy = { confirm = reward },
                            onWear = {
                                scope.launch {
                                    runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(reward.key)) }
                                        .onSuccess { avatar = reward.key }
                                }
                            },
                        )
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }

        // ParkIt's own finishes for your car, each previewed on your car.
        item { SectionTitle(stringResource(R.string.rewards_car_section)) }
        s.rewards.filter { it.kind == "cosmetic" && it.key?.startsWith("paint_") == true }.chunked(2).forEach { pair ->
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
private fun LegendCard(
    reward: RewardOut,
    hebrew: Boolean,
    balance: Int,
    wearing: Boolean,
    modifier: Modifier,
    onBuy: () -> Unit,
    onWear: () -> Unit,
) {
    SectionCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp).fillMaxWidth()) {
            avatarResource(reward.key)?.let { res ->
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(res),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
            }
            Text(
                if (hebrew) reward.titleHe else reward.titleEn,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(Modifier.padding(top = 6.dp)) {
                if (reward.owned) {
                    TextButton(onClick = onWear, enabled = !wearing) {
                        Text(stringResource(if (wearing) R.string.rewards_wearing_avatar else R.string.rewards_wear))
                    }
                } else {
                    PriceButton(reward.cost, balance, owned = false, onBuy, compact = true)
                }
            }
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
private fun PriceButton(cost: Int, balance: Int, owned: Boolean, onBuy: () -> Unit, compact: Boolean = false) {
    val canBuy = !owned && balance >= cost
    if (canBuy) {
        Button(
            onClick = onBuy,
            shape = RoundedCornerShape(50),
            contentPadding = if (compact) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp) else androidx.compose.material3.ButtonDefaults.ContentPadding,
        ) {
            Text(stringResource(R.string.rewards_cost, cost), maxLines = 1, softWrap = false)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = false) {}.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 8.dp),
        ) {
            // No room for the lock in narrow cards — the price alone would get clipped.
            if (!compact) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                // Narrow cards show just the price; wide ones say what's missing.
                if (compact) stringResource(R.string.rewards_cost, cost) else stringResource(R.string.rewards_missing, cost - balance),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (compact) 0.dp else 6.dp),
            )
        }
    }
}
