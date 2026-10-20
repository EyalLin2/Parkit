package com.parkit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ParkingLotOut

/** A car park's card: what it is and how to get there. OpenStreetMap often
 * lacks names or details, so every line is optional — the card only says
 * what's actually known (and never guesses prices or opening hours). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotSheet(lot: ParkingLotOut, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hebrewUi = context.resources.configuration.locales[0].language in setOf("he", "iw")
    val slate = Color(android.graphics.Color.parseColor(MarkerBitmaps.LOT_COLOR))
    val name = (if (hebrewUi) lot.name else lot.nameEn ?: lot.name) ?: stringResource(R.string.lot_unnamed)
    val kind = when (lot.kind) {
        "underground" -> stringResource(R.string.lot_kind_underground)
        "multi-storey" -> stringResource(R.string.lot_kind_multistorey)
        "rooftop" -> stringResource(R.string.lot_kind_rooftop)
        "surface" -> stringResource(R.string.lot_kind_surface)
        else -> null
    }
    val details = listOfNotNull(
        kind,
        lot.capacity?.let { stringResource(R.string.lot_capacity, it) },
        stringResource(R.string.spot_distance_away, formatLotDistance(context, lot.distanceM)),
    )

    ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(slate, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("P", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    lot.operator?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            // Price and hours only when someone actually mapped them — never guessed.
            Text(
                lot.charge?.let { stringResource(R.string.lot_charge, it) } ?: stringResource(R.string.lot_charge_unknown),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            lot.openingHours?.let {
                Text(
                    stringResource(R.string.lot_hours, if (it == "24/7") stringResource(R.string.lot_hours_always) else it),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = {
                    DriveTo.open(context, lot.lat, lot.lng)
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.spot_navigate), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
            val updated = lot.dataUpdatedAt?.take(10)?.split("-")?.takeIf { it.size == 3 }?.let { (y, m, d) -> "$d.$m.$y" }
            Text(
                if (updated != null) stringResource(R.string.lot_data_note, updated) else stringResource(R.string.lot_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private fun formatLotDistance(context: android.content.Context, meters: Double): String =
    if (meters < 1000) context.getString(R.string.distance_m, (Math.round(meters / 10.0) * 10).toInt())
    else context.getString(R.string.distance_km, meters / 1000.0)
