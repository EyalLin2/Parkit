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
import androidx.compose.material.icons.filled.Star
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
    initialTab: Int = 1,
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

    var backdrop by remember { mutableStateOf(sessionStore.profileBackground) }
    // Opens on the characters: partner benefits are still "coming soon".
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialTab) }

    fun wear(reward: RewardOut) {
        val key = reward.key ?: return
        when {
            key.startsWith("avatar_legend_") -> scope.launch {
                runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(key)) }.onSuccess { avatar = key }
            }
            key.startsWith("bg_") -> {
                backdrop = if (backdrop == key) null else key
                sessionStore.profileBackground = backdrop
            }
            else -> {
                special = if (special == key) null else key
                sessionStore.carSpecial = special
            }
        }
    }

    fun buy(reward: RewardOut) {
        scope.launch {
            try {
                val updated = api.redeemReward(reward.id)
                shop = updated
                if (reward.kind == "cosmetic") {
                    // Put it on right away — that's the fun part.
                    if (reward.key?.startsWith("avatar_legend_") == true) {
                        runCatching { api.setAvatar(com.parkit.app.api.AvatarUpdate(reward.key)) }
                        avatar = reward.key
                    } else if (reward.key?.startsWith("bg_") == true) {
                        backdrop = reward.key
                        sessionStore.profileBackground = reward.key
                    } else {
                        special = reward.key
                        sessionStore.carSpecial = reward.key
                    }
                } else {
                    newCode = updated.redemptions.firstOrNull { it.rewardId == reward.id }?.code
                }
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            }
        }
    }

    fun tap(reward: RewardOut, balance: Int) {
        when {
            reward.owned -> wear(reward)
            balance >= reward.cost -> confirm = reward
            else -> android.widget.Toast.makeText(context, context.getString(R.string.rewards_missing, reward.cost - balance), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        ScreenTopBar(title = stringResource(R.string.rewards_title), onBack = onBack)
        val s = shop
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
            }
            return@Column
        }
        // Balance: one line, the number big.
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.balance.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.rewards_balance_line),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
            }
            Text(stringResource(R.string.rewards_level_safe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ShopTabs(tab) { tab = it }
        val items = when (tab) {
            1 -> s.rewards.filter { it.key?.startsWith("avatar_legend_") == true }
            2 -> s.rewards.filter { it.kind == "cosmetic" && it.key?.startsWith("paint_") == true }
            3 -> s.rewards.filter { it.key?.startsWith("bg_") == true }
            else -> emptyList()
        }
        if (tab == 0) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                val partners = s.rewards.filter { it.kind == "partner" }
                if (partners.isEmpty()) {
                    item { PartnersSoon() }
                } else {
                    partners.forEach { reward -> item { PartnerCard(reward, hebrew, s.balance) { tap(reward, s.balance) } } }
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
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(if (tab == 1) 3 else 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items.size) { i ->
                    val reward = items[i]
                    val inUse = when {
                        reward.key?.startsWith("avatar_legend_") == true -> avatar == reward.key
                        reward.key?.startsWith("bg_") == true -> backdrop == reward.key
                        else -> special == reward.key
                    }
                    ShopItem(reward, hebrew, s.balance, inUse, onClick = { tap(reward, s.balance) }, twoLines = tab == 1) {
                        when (tab) {
                            1 -> avatarResource(reward.key)?.let { res ->
                                androidx.compose.foundation.Image(
                                    androidx.compose.ui.res.painterResource(res),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                )
                            }
                            2 -> CarArt(size = car.vehicleSize ?: "regular", paint = paint, outline = MaterialTheme.colorScheme.outline, special = reward.key, modifier = Modifier.fillMaxWidth())
                            else -> ProfileBackdrop(reward.key, Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)))
                        }
                    }
                }
            }
        }
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

/** Pill tabs, like a segmented control. */
@Composable
private fun ShopTabs(selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf(R.string.rewards_tab_partners, R.string.rewards_tab_legends, R.string.rewards_tab_car, R.string.rewards_tab_backdrops)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth()
            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
    ) {
        titles.forEachIndexed { i, res ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One thing to buy: its picture, its name and a small tag (price, "yours"
 * or "in use"). The whole card is the button. */
@Composable
private fun ShopItem(reward: RewardOut, hebrew: Boolean, balance: Int, inUse: Boolean, onClick: () -> Unit, twoLines: Boolean = false, picture: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (inUse) 2.dp else 1.dp, if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            picture()
            Text(
                if (hebrew) reward.titleHe else reward.titleEn,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = if (twoLines) 2 else 1,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(Modifier.padding(top = 6.dp)) { PriceTag(reward, balance, inUse) }
        }
    }
}

@Composable
private fun PriceTag(reward: RewardOut, balance: Int, inUse: Boolean) {
    val (bg, fg) = when {
        reward.owned -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.primary
        balance >= reward.cost -> Color(0xFFFEF7E0) to Color(0xFF8A5300)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Icon(
            when {
                reward.owned -> Icons.Filled.CheckCircle
                balance >= reward.cost -> Icons.Filled.Star
                else -> Icons.Filled.Lock
            },
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(13.dp),
        )
        Text(
            when {
                inUse -> stringResource(R.string.rewards_in_use)
                reward.owned -> stringResource(R.string.rewards_owned)
                else -> reward.cost.toString()
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PartnersSoon() {
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFEF7E0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = Color(0xFFB06000), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(stringResource(R.string.rewards_partners_soon_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.rewards_partners_soon_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PartnerCard(reward: RewardOut, hebrew: Boolean, balance: Int, onBuy: () -> Unit) {
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onBuy)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(if (hebrew) reward.titleHe else reward.titleEn, style = MaterialTheme.typography.titleSmall)
                reward.partner?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                (if (hebrew) reward.descriptionHe else reward.descriptionEn)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PriceTag(reward, balance, inUse = false)
        }
    }
}
