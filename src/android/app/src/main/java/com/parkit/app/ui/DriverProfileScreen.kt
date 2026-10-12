package com.parkit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.CityOut
import com.parkit.app.api.PermitZoneOut
import com.parkit.app.api.Preferences
import com.parkit.app.api.friendlyMessage
import com.parkit.app.api.isUnauthorized
import com.parkit.app.auth.DriverPrefs
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** "My car & preferences". Shown once right after login ([onboarding] —
 * skippable, and skipped automatically if the account already has
 * preferences) and from the personal area. Everything here is optional:
 * each answer only changes what the map highlights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfileScreen(
    api: ApiService,
    sessionStore: SessionStore,
    onboarding: Boolean,
    onDone: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val hebrewUi = context.resources.configuration.locales[0].language in setOf("he", "iw")
    val cached = sessionStore.driver.value

    var loading by remember { mutableStateOf(true) }
    var vehicleSize by remember { mutableStateOf(cached.preferences.vehicleSize) }
    var carLabel by remember { mutableStateOf(cached.preferences.carLabel) }
    var disabledPermit by remember { mutableStateOf(cached.preferences.hasDisabledPermit) }
    var city by remember { mutableStateOf(cached.preferences.homeCity) }
    var cityCentre by remember { mutableStateOf(cached.homeLat?.let { lat -> cached.homeLng?.let { lng -> lat to lng } }) }
    var zone by remember { mutableStateOf(cached.preferences.residentZone) }
    var residentPermit by remember { mutableStateOf(zone != null) }

    var cities by remember { mutableStateOf<List<CityOut>>(emptyList()) }
    var zones by remember { mutableStateOf<List<PermitZoneOut>>(emptyList()) }

    var plate by remember { mutableStateOf("") }
    var lookingUp by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCity by remember { mutableStateOf(false) }
    var pickZone by remember { mutableStateOf(false) }

    fun toggleResident(on: Boolean) {
        residentPermit = on
        if (on && zone == null) pickZone = true // turning it on means "now pick my zone"
    }

    fun finish() {
        sessionStore.onboardingSeen = true
        onDone()
    }

    LaunchedEffect(Unit) {
        try {
            val server = api.myProfile().preferences
            // Onboarding is for new drivers; an account that already answered
            // (on another device, or before reinstalling) goes straight on.
            if (onboarding && !server.isEmpty) {
                sessionStore.syncDriver(server)
                finish()
                return@LaunchedEffect
            }
            vehicleSize = server.vehicleSize
            carLabel = server.carLabel
            disabledPermit = server.hasDisabledPermit
            if (server.homeCity != city) cityCentre = null
            city = server.homeCity
            zone = server.residentZone
            residentPermit = zone != null
            loading = false
            zones = api.permitZones()
            cities = api.cities()
            city?.let { name -> cities.firstOrNull { it.name == name }?.let { cityCentre = it.lat to it.lng } }
        } catch (e: Exception) {
            loading = false
            if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
        }
    }

    fun lookup() {
        val digits = plate.filter { it.isDigit() }
        if (digits.length !in 7..8) {
            lookupError = context.getString(R.string.driver_plate_invalid)
            return
        }
        lookingUp = true
        lookupError = null
        scope.launch {
            try {
                val car = api.lookupVehicle(digits)
                carLabel = car.label
                vehicleSize = car.size
                plate = ""
                focusManager.clearFocus()
            } catch (e: Exception) {
                lookupError = when {
                    e.isUnauthorized() -> return@launch onSessionExpired()
                    e is HttpException && e.code() == 404 -> context.getString(R.string.driver_plate_not_found)
                    else -> context.getString(R.string.driver_plate_lookup_failed)
                }
            } finally {
                lookingUp = false
            }
        }
    }

    fun save() {
        saving = true
        error = null
        scope.launch {
            try {
                val saved = api.updatePreferences(
                    Preferences(
                        vehicleSize = vehicleSize,
                        carLabel = carLabel,
                        hasDisabledPermit = disabledPermit,
                        homeCity = city,
                        residentZone = zone.takeIf { residentPermit },
                    ),
                )
                sessionStore.saveDriver(DriverPrefs(saved, cityCentre?.first, cityCentre?.second))
                finish()
            } catch (e: Exception) {
                if (e.isUnauthorized()) onSessionExpired() else error = e.friendlyMessage(context)
            } finally {
                saving = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        if (onboarding) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = ::finish) { Text(stringResource(R.string.driver_skip)) }
            }
        } else {
            ScreenTopBar(title = stringResource(R.string.driver_title), onBack = onDone)
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (onboarding) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.driver_onboarding_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.driver_onboarding_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // --- The car --------------------------------------------------
            SectionTitle(stringResource(R.string.driver_section_car))
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    carLabel?.let { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                            IconBadge(Icons.Filled.DirectionsCar)
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.driver_car_from_registry),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = plate,
                            onValueChange = { v -> plate = v.filter { it.isDigit() || it == '-' }.take(10) },
                            label = { Text(stringResource(if (carLabel == null) R.string.driver_plate_label else R.string.driver_plate_change)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { lookup() }),
                            isError = lookupError != null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(10.dp))
                        Button(onClick = ::lookup, enabled = !lookingUp && plate.isNotBlank(), shape = RoundedCornerShape(50), modifier = Modifier.height(52.dp)) {
                            if (lookingUp) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.driver_plate_lookup))
                            }
                        }
                    }
                    Text(
                        lookupError ?: stringResource(R.string.driver_plate_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lookupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Text(stringResource(R.string.driver_size_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    val sizes = listOf("compact" to R.string.report_size_compact, "regular" to R.string.report_size_regular, "large" to R.string.report_size_large)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        sizes.forEachIndexed { i, (value, labelRes) ->
                            SegmentedButton(
                                selected = vehicleSize == value,
                                onClick = {
                                    // A manual change means the looked-up model no longer describes the car.
                                    if (vehicleSize != value) carLabel = null
                                    vehicleSize = value
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, sizes.size),
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                    Text(
                        stringResource(R.string.driver_size_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // --- Disabled permit -------------------------------------------
            SectionTitle(stringResource(R.string.driver_section_permit))
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { disabledPermit = !disabledPermit }) {
                        IconBadge(Icons.AutoMirrored.Filled.Accessible)
                        Text(stringResource(R.string.driver_disabled_permit), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Switch(checked = disabledPermit, onCheckedChange = { disabledPermit = it })
                    }
                    Text(
                        stringResource(R.string.driver_disabled_permit_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // --- Where you park --------------------------------------------
            SectionTitle(stringResource(R.string.driver_section_area))
            SectionCard {
                Column {
                    PickerRow(
                        icon = Icons.Filled.LocationCity,
                        title = stringResource(R.string.driver_home_city),
                        value = city?.let { name -> cities.firstOrNull { it.name == name }?.displayName(hebrewUi) ?: name }
                            ?: stringResource(R.string.driver_not_set),
                        hint = stringResource(R.string.driver_home_city_hint),
                        onClick = { pickCity = true },
                    )
                }
            }

            // --- Resident permit: opt-in, profile only ---------------------
            // Most drivers in a city don't live there, so this isn't asked
            // after login and stays off unless the driver turns it on.
            if (!onboarding) {
                SectionTitle(stringResource(R.string.driver_section_resident))
                SectionCard {
                    Column {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toggleResident(!residentPermit) }) {
                                IconBadge(Icons.Filled.Badge)
                                Text(stringResource(R.string.driver_has_resident_permit), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                Switch(checked = residentPermit, onCheckedChange = ::toggleResident)
                            }
                            Text(
                                stringResource(R.string.driver_resident_permit_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (residentPermit) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            PickerRow(
                                icon = Icons.Filled.LocationCity,
                                title = stringResource(R.string.driver_resident_zone),
                                value = zone?.let { key -> zoneDisplay(context, key, zones.firstOrNull { it.key == key }?.city) } ?: stringResource(R.string.driver_not_set),
                                hint = stringResource(R.string.driver_resident_zone_hint),
                                onClick = { pickZone = true },
                            )
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = ::save,
            enabled = !saving,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().height(52.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(if (onboarding) R.string.driver_save_and_continue else R.string.driver_save))
            }
        }
    }

    if (pickCity) {
        ChoiceDialog(
            title = stringResource(R.string.driver_home_city),
            searchable = true,
            options = listOf(Choice(null, stringResource(R.string.driver_not_set))) +
                cities.map { Choice(it.name, it.displayName(hebrewUi), listOfNotNull(it.name, it.nameEn).joinToString(" ")) }
                    .sortedBy { it.label },
            selected = city,
            onPick = { name ->
                city = name
                cityCentre = cities.firstOrNull { it.name == name }?.let { it.lat to it.lng }
                pickCity = false
            },
            onDismiss = { pickCity = false },
        )
    }
    if (pickZone) {
        ChoiceDialog(
            title = stringResource(R.string.driver_resident_zone),
            searchable = false,
            options = zones.map { Choice(it.key, zoneDisplay(context, it.key, it.city)) },
            selected = zone,
            footer = stringResource(R.string.driver_resident_zone_coverage),
            onPick = { key ->
                zone = key
                pickZone = false
            },
            onDismiss = { pickZone = false },
        )
    }
}

private data class Choice(val value: String?, val label: String, val search: String = label)

/** OSM has Hebrew names for every city and English for nearly all. */
private fun CityOut.displayName(hebrewUi: Boolean): String = if (hebrewUi) name else nameEn ?: name

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PickerRow(icon: ImageVector, title: String, value: String, hint: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    ) {
        IconBadge(icon)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    searchable: Boolean,
    options: List<Choice>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) options else options.filter { it.value != null && it.search.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.driver_search_city)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.value ?: "" }) { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (value == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                footer?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driver_close)) } },
    )
}
