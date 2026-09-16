@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import com.lullabyai.app.viewmodel.ActivityCategories
import com.lullabyai.app.viewmodel.ActivityScheduleViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Design: `Add Activity (เพิ่มกิจกรรม)`.
 *
 * Everything on the calendar is created here, whatever its category — an appointment, a vaccine, a
 * milestone and an outing differ only by which chip is chosen and what that colours.
 */
@Composable
fun AddActivityScreen(
    viewModel: ActivityScheduleViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("family") }
    // Opens on the day you were looking at, which is almost always the one you mean.
    var date by remember(state.selectedDate) { mutableStateOf(state.selectedDate) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    /** Follows the start time until it is set by hand, so the common case needs no second pick. */
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var endTouched by remember { mutableStateOf(false) }
    var allDay by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf("") }
    /** The venue actually chosen from Google Maps, carrying its id and coordinates. */
    var pickedPlace by remember { mutableStateOf<com.lullabyai.app.data.remote.PlaceSuggestion?>(null) }
    /** Off by default — cost is something you may simply not know yet. */
    var knowCost by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && !state.saving

    Scaffold(
        containerColor = SchBackground,
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เพิ่มกิจกรรม" else "Add activity",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (state.childName.isNotBlank()) {
                Text(
                    (if (isThai) "สำหรับ " else "For ") + state.childName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SchOnSurfaceVariant
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (isThai) "ชื่อกิจกรรม" else "Title") },
                placeholder = {
                    Text(
                        if (isThai) "เช่น รับวัคซีน 6 เดือน" else "e.g. 6-month vaccine",
                        color = SchOutline
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(if (isThai) "ประเภท" else "Category")
                // Chips rather than a dropdown: the colour is the point, and a closed menu hides it.
                ActivityCategories.ALL.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            val selected = category == c.key
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) c.color.copy(0.14f) else SchSurface)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) c.color else SchOutlineVariant.copy(0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { category = c.key }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(c.icon, null, tint = c.color, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isThai) c.th else c.en,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) c.color else SchOnSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SchSurface)
                    .border(1.dp, SchOutlineVariant.copy(0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Label(if (isThai) "วันที่" else "Date")
                PickerBox(
                    text = dayTitle(date, isThai) + " " +
                        (if (isThai) (date.year + 543).toString() else date.year.toString()),
                    icon = Icons.Filled.CalendarToday,
                    onClick = { pickingDate = true }
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isThai) "ตลอดวัน" else "All day",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SchOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allDay,
                        onCheckedChange = { allDay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SchPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SchSurfaceHigh,
                            uncheckedBorderColor = SchOutlineVariant
                        )
                    )
                }

                // A time on an all-day entry would be a value the calendar then ignores.
                if (!allDay) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Label(if (isThai) "เวลาเริ่ม" else "Start time")
                            PickerBox(
                                text = "%02d:%02d".format(time.hour, time.minute) +
                                    if (isThai) " น." else "",
                                icon = Icons.Filled.Schedule,
                                onClick = { pickingTime = true }
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Label(if (isThai) "เวลาสิ้นสุด" else "End time")
                            PickerBox(
                                text = "%02d:%02d".format(endTime.hour, endTime.minute) +
                                    if (isThai) " น." else "",
                                icon = Icons.Filled.Schedule,
                                onClick = { pickingEndTime = true }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(if (isThai) "สถานที่" else "Place")
                OutlinedTextField(
                    value = place,
                    onValueChange = {
                        place = it
                        // Typing again means the previously chosen venue no longer applies.
                        pickedPlace = null
                    },
                    placeholder = {
                        Text(
                            if (isThai) "พิมพ์เพื่อค้นหาใน Google Maps" else "Type to search Google Maps",
                            color = SchOutline
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.searchPlaces(place) }) {
                            if (state.searchingPlaces) {
                                CircularProgressIndicator(
                                    color = SchPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Filled.Search, null, tint = SchPrimary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                pickedPlace?.let { p ->
                    // Confirms a real venue was chosen, not just text that resembles one.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SchPrimary.copy(0.08f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Place, null,
                            tint = SchPrimary, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SchOnSurface,
                                maxLines = 1
                            )
                            p.address?.let {
                                Text(it, fontSize = 11.sp, color = SchOnSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }

                state.placeResults.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, SchOutlineVariant.copy(0.5f), RoundedCornerShape(10.dp))
                            .clickable {
                                pickedPlace = p
                                place = p.name
                                // Google's price bracket pre-fills the estimate, and turns the
                                // cost switch on so the number is visible rather than hidden.
                                p.roughCostBaht?.let { c ->
                                    knowCost = true
                                    cost = c.toString()
                                }
                                viewModel.clearPlaceResults()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Place, null,
                            tint = SchOutline, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, color = SchOnSurface, maxLines = 1)
                            p.address?.let {
                                Text(it, fontSize = 11.sp, color = SchOnSurfaceVariant, maxLines = 1)
                            }
                        }
                        p.rating?.let {
                            Text("★ $it", fontSize = 11.sp, color = SchOnSurfaceVariant)
                        }
                    }
                }
            }

            // Cost is optional on purpose: a parent adding a hospital appointment usually has no
            // idea what it will cost, and an empty required-looking field is just pressure.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SchSurface)
                    .border(1.dp, SchOutlineVariant.copy(0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "ทราบค่าใช้จ่ายหรือไม่?" else "Know the cost?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SchOnSurface
                        )
                        Text(
                            if (isThai) "ไม่ระบุก็ได้" else "Optional",
                            fontSize = 12.sp,
                            color = SchOutline
                        )
                    }
                    Switch(
                        checked = knowCost,
                        onCheckedChange = {
                            knowCost = it
                            if (!it) cost = ""
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SchPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SchSurfaceHigh,
                            uncheckedBorderColor = SchOutlineVariant
                        )
                    )
                }
                if (knowCost) {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = {
                            Text(if (isThai) "ค่าใช้จ่ายโดยประมาณ (฿)" else "Estimated cost (฿)")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isThai) "บันทึกเพิ่มเติม" else "Notes") },
                minLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Text(
                    when (it) {
                        "empty_title" ->
                            if (isThai) "กรุณาระบุชื่อกิจกรรม" else "Enter a title"
                        // A schedule points forward; anything already done belongs in the log.
                        "past_start" -> if (isThai)
                            "ไม่สามารถเพิ่มกิจกรรมย้อนหลังได้ กรุณาเลือกวันและเวลาในอนาคต"
                        else "You cannot add an activity in the past — pick a future date and time"
                        "end_before_start" -> if (isThai)
                            "เวลาสิ้นสุดต้องอยู่หลังเวลาเริ่ม"
                        else "The end time must be after the start time"
                        else -> it
                    },
                    color = SchError,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = {
                    val zone = ZoneId.systemDefault()
                    // An all-day entry is stored at local midnight, so it lands on the intended
                    // date in this timezone rather than drifting to the day before in UTC.
                    val startsAt = if (allDay) date.atStartOfDay(zone).toOffsetDateTime()
                    else date.atTime(time).atZone(zone).toOffsetDateTime()
                    viewModel.addActivity(
                        title = title,
                        category = category,
                        startsAt = startsAt,
                        // An all-day entry has no meaningful end time to store.
                        endsAt = if (allDay) null
                        else date.atTime(endTime).atZone(zone).toOffsetDateTime(),
                        allDay = allDay,
                        locationName = place,
                        // Only present when a real venue was chosen, so a typed name never
                        // masquerades as a resolved place.
                        placeId = pickedPlace?.id,
                        lat = pickedPlace?.lat,
                        lng = pickedPlace?.lng,
                        notes = notes,
                        estimatedCostText = if (knowCost) cost else "",
                        onDone = onBack
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SchPrimary,
                    disabledContainerColor = SchSurfaceHigh
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        color = SchOnPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        if (isThai) "บันทึกกิจกรรม" else "Save activity",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text("OK", color = SchPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text("Cancel", color = SchOutline)
                }
            }
        ) { DatePicker(state = picker) }
    }

    if (pickingEndTime) {
        SchTimePickerDialog(
            title = if (isThai) "เวลาสิ้นสุด" else "End time",
            initial = endTime,
            onDismiss = { pickingEndTime = false },
            onPick = { endTime = it; endTouched = true; pickingEndTime = false }
        )
    }

    if (pickingTime) {
        SchTimePickerDialog(
            title = if (isThai) "เวลาเริ่ม" else "Start time",
            initial = time,
            onDismiss = { pickingTime = false },
            onPick = { picked ->
                val length = java.time.Duration.between(time, endTime).toMinutes()
                time = picked
                // The end keeps its distance unless it was set by hand, so it cannot silently
                // fall before the start.
                if (!endTouched) endTime = picked.plusMinutes(length.coerceAtLeast(60))
                pickingTime = false
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SchOnSurfaceVariant)
}

/** Read-only field that opens a picker — a keyboard has no business editing a date or a time. */
@Composable
private fun PickerBox(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, SchOutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SchOutline, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, color = SchOnSurface)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SchPrimary,
    unfocusedBorderColor = SchOutlineVariant,
    focusedContainerColor = SchSurface,
    unfocusedContainerColor = SchSurface,
    cursorColor = SchPrimary
)
