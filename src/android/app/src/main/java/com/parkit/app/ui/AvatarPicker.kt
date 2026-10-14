package com.parkit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parkit.app.R

/** Sixteen Tel Aviv types — the hipster from Florentin, the surfer from
 * Gordon, falafel lovers at the Carmel market, goths, the DJ at Dizengoff…
 * Characters are DiceBear "Personas" by Draftbit (CC BY 4.0, credited in
 * Settings) set in hand-drawn Tel Aviv scenes with small props. Ids match
 * the server's avatar_01..16. */
private val AVATARS = listOf(
    R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03, R.drawable.avatar_04,
    R.drawable.avatar_05, R.drawable.avatar_06, R.drawable.avatar_07, R.drawable.avatar_08,
    R.drawable.avatar_09, R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
    R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16,
)

/** Legend avatars (illustrated figures), unlocked in the rewards shop. */
val LEGEND_AVATARS = mapOf(
    "avatar_legend_herzl" to R.drawable.avatar_legend_herzl,
    "avatar_legend_bengurion" to R.drawable.avatar_legend_bengurion,
    "avatar_legend_golda" to R.drawable.avatar_legend_golda,
    "avatar_legend_peres" to R.drawable.avatar_legend_peres,
    "avatar_legend_benyehuda" to R.drawable.avatar_legend_benyehuda,
    "avatar_legend_bialik" to R.drawable.avatar_legend_bialik,
    "avatar_legend_scientist" to R.drawable.avatar_legend_scientist,
    "avatar_legend_trumpeldor" to R.drawable.avatar_legend_trumpeldor,
    "avatar_legend_rambam" to R.drawable.avatar_legend_rambam,
    "avatar_legend_dayan" to R.drawable.avatar_legend_dayan,
)

fun avatarResource(id: String?): Int? =
    LEGEND_AVATARS[id] ?: id?.removePrefix("avatar_")?.toIntOrNull()?.let { AVATARS.getOrNull(it - 1) }

fun avatarId(index: Int): String = "avatar_%02d".format(index + 1)

/** A 4×4 grid of avatars; the chosen one is ringed in the brand colour. */
@Composable
fun AvatarGrid(selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val names = androidx.compose.ui.res.stringArrayResource(R.array.avatar_names)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        AVATARS.indices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val id = avatarId(i)
                    val chosen = id == selected
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onPick(id) },
                    ) {
                        Image(
                            painter = painterResource(AVATARS[i]),
                            contentDescription = names.getOrNull(i),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            names.getOrNull(i).orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            // Two-word names ("Falafel lover") wrap instead of
                            // being cut off in the narrow dialog; minLines keeps rows aligned.
                            maxLines = 2,
                            minLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarPickerDialog(selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.avatar_pick_title)) },
        text = { AvatarGrid(selected, onPick = { onPick(it); onDismiss() }, modifier = Modifier.padding(top = 4.dp)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driver_close)) } },
    )
}
