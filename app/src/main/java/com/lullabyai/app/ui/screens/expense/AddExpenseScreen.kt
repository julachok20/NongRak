@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.expense

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.screens.child.ChildBottomNav
import androidx.compose.material.icons.filled.Warning
import com.lullabyai.app.viewmodel.ExpenseCategories
import com.lullabyai.app.viewmodel.ExpenseErrors
import com.lullabyai.app.viewmodel.ExpenseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Design: `48.Cost/add_expense`.
 *
 * The three switches are independent rather than one status: a car seat can be under warranty and
 * already in use, and a tin of formula has an expiry date but no warranty. Each one only reveals
 * its own field while it is on, and the table refuses a date whose switch is off — so a row can
 * never claim two things at once.
 */
@Composable
fun AddExpenseScreen(
    viewModel: ExpenseViewModel,
    navController: NavController,
    /** Which child to book this cost against; blank means the one selected app-wide. */
    childId: String = "",
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Preselect before load so this screen opens on the child whose record you came from.
    LaunchedEffect(childId) {
        viewModel.preselectChild(childId)
        viewModel.load()
    }

    /** The child the cost lands on. Starts from what was carried in, then follows the picker. */
    var targetChild by remember(childId, state.childId) {
        mutableStateOf(childId.ifBlank { null } ?: state.scopeChildId ?: state.childId)
    }

    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var spentOn by remember { mutableStateOf(LocalDate.now()) }
    /** What was bought — becomes the row's title in the list. */
    var productName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var hasWarranty by remember { mutableStateOf(false) }
    var warrantyExpiresOn by remember { mutableStateOf<LocalDate?>(null) }
    var hasExpiration by remember { mutableStateOf(false) }
    var expiresOn by remember { mutableStateOf<LocalDate?>(null) }
    var alreadyUsed by remember { mutableStateOf(false) }
    var usageNote by remember { mutableStateOf("") }

    var receiptBytes by remember { mutableStateOf<ByteArray?>(null) }
    var receiptName by remember { mutableStateOf<String?>(null) }
    var receiptTooBig by remember { mutableStateOf(false) }

    /** Which date field a picker is open for; null means closed. */
    var pickingDate by remember { mutableStateOf<DateTarget?>(null) }

    val pickReceipt = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        // The bucket caps uploads at 5MB. Refusing here explains why, instead of failing halfway
        // through a save the user thinks already worked.
        if (bytes != null && bytes.size <= 5 * 1024 * 1024) {
            receiptBytes = bytes
            receiptName = uri.lastPathSegment
            receiptTooBig = false
        } else {
            receiptBytes = null
            receiptName = null
            receiptTooBig = bytes != null
        }
    }

    val canSave = ExpenseViewModel.parseSatang(amount) != null && !state.saving

    val save = {
        viewModel.addExpense(
            amountText = amount,
            category = category,
            spentOn = spentOn,
            name = productName,
            note = note,
            hasWarranty = hasWarranty,
            warrantyExpiresOn = warrantyExpiresOn,
            hasExpiration = hasExpiration,
            expiresOn = expiresOn,
            alreadyUsed = alreadyUsed,
            usageNote = usageNote,
            receipt = receiptBytes,
            targetChildId = targetChild,
            onDone = onBack
        )
    }

    Scaffold(
        containerColor = ScBackground,
        // Standard header, like every other screen. The design's top-bar Save lives on the
        // full-width button at the foot of the form instead — one Save, not two.
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เพิ่มรายการ" else "Add expense",
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
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Whose cost is this? Shown whenever there is more than one child, so a cost can be
            // booked to a sibling without leaving this screen or switching the whole app over.
            if (state.children.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(if (isThai) "บันทึกให้ (Child)" else "Record for")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.children.forEach { c ->
                            val selected = c.id == targetChild
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) ScPrimary else ScSurfaceContainer)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) ScPrimary else ScOutlineVariant.copy(0.4f),
                                        CircleShape
                                    )
                                    .clickable { targetChild = c.id }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    c.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else ScOnSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            AmountField(amount, isThai) { amount = it }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(if (isThai) "หมวดหมู่ (Category)" else "Category")
                // 3 columns x 2 rows, laid out by hand: a LazyVerticalGrid inside a scrolling
                // Column needs a fixed height, and six fixed items do not justify one.
                ExpenseCategories.ALL.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            CategoryTile(
                                key = c.key,
                                label = if (isThai) c.th else c.en,
                                selected = category == c.key,
                                onClick = { category = c.key },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // Name, date and note share one white card, as in the design.
            CardPanel {
                FieldLabel(if (isThai) "ชื่อสินค้า/รายการ (Item name)" else "Item name")
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    placeholder = {
                        Text(
                            if (isThai) "เช่น นมผง, วัคซีน MMR" else "e.g. Milk powder, MMR vaccine",
                            color = ScOutline
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                FieldLabel(if (isThai) "วันที่ (Date)" else "Date")
                DateField(
                    value = spentOn,
                    isThai = isThai,
                    onClick = { pickingDate = DateTarget.SPENT_ON }
                )
                Spacer(Modifier.height(16.dp))
                FieldLabel(if (isThai) "บันทึกเพิ่มเติม (Note)" else "Note")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = {
                        Text(
                            if (isThai) "ระบุรายละเอียดเพิ่มเติม..." else "Add more detail…",
                            color = ScOutline
                        )
                    },
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CardPanel {
                ToggleRow(
                    label = if (isThai) "มีประกัน?" else "Has warranty?",
                    checked = hasWarranty,
                    onCheckedChange = {
                        hasWarranty = it
                        if (!it) warrantyExpiresOn = null
                    }
                ) {
                    FieldLabel(
                        if (isThai) "วันหมดอายุประกัน" else "Warranty expiry date",
                        small = true
                    )
                    DateField(
                        value = warrantyExpiresOn,
                        isThai = isThai,
                        onClick = { pickingDate = DateTarget.WARRANTY }
                    )
                }

                HorizontalDivider(
                    Modifier.padding(vertical = 16.dp),
                    color = ScOutlineVariant.copy(0.3f)
                )

                ToggleRow(
                    label = if (isThai) "มีวันหมดอายุสินค้า?" else "Has expiration date?",
                    checked = hasExpiration,
                    onCheckedChange = {
                        hasExpiration = it
                        if (!it) expiresOn = null
                    }
                ) {
                    FieldLabel(
                        if (isThai) "วันหมดอายุสินค้า" else "Product expiry date",
                        small = true
                    )
                    DateField(
                        value = expiresOn,
                        isThai = isThai,
                        onClick = { pickingDate = DateTarget.EXPIRY }
                    )
                }

                HorizontalDivider(
                    Modifier.padding(vertical = 16.dp),
                    color = ScOutlineVariant.copy(0.3f)
                )

                ToggleRow(
                    label = if (isThai) "ใช้งานแล้ว?" else "Already used?",
                    checked = alreadyUsed,
                    onCheckedChange = {
                        alreadyUsed = it
                        if (!it) usageNote = ""
                    }
                ) {
                    FieldLabel(
                        if (isThai) "รายละเอียดการใช้งาน" else "Usage details",
                        small = true
                    )
                    OutlinedTextField(
                        value = usageNote,
                        onValueChange = { usageNote = it },
                        placeholder = {
                            Text(
                                if (isThai) "ระบุรายละเอียด..." else "Add detail…",
                                color = ScOutline
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (isThai) "แนบใบเสร็จ (Receipt)" else "Receipt")
                ReceiptZone(
                    attached = receiptBytes != null,
                    fileName = receiptName,
                    isThai = isThai,
                    onPick = { pickReceipt.launch("image/*") },
                    onClear = { receiptBytes = null; receiptName = null }
                )
                if (receiptTooBig) {
                    Text(
                        if (isThai) "ไฟล์ใหญ่เกิน 5MB" else "That file is larger than 5MB",
                        color = ScError,
                        fontSize = 12.sp
                    )
                }
            }

            ExpenseErrors.message(state.error, isThai)?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ScErrorContainer, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Warning, null, tint = ScError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = ScError, fontSize = 13.sp)
                }
            }

            Button(
                onClick = save,
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScPrimary,
                    disabledContainerColor = ScSurfaceHigh
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        if (isThai) "บันทึกรายการ" else "Save item",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    pickingDate?.let { target ->
        val initial = when (target) {
            DateTarget.SPENT_ON -> spentOn
            DateTarget.WARRANTY -> warrantyExpiresOn
            DateTarget.EXPIRY -> expiresOn
        } ?: LocalDate.now()
        ExpenseDatePicker(
            initial = initial,
            onDismiss = { pickingDate = null },
            onPick = { picked ->
                when (target) {
                    DateTarget.SPENT_ON -> spentOn = picked
                    DateTarget.WARRANTY -> warrantyExpiresOn = picked
                    DateTarget.EXPIRY -> expiresOn = picked
                }
                pickingDate = null
            }
        )
    }
}

private enum class DateTarget { SPENT_ON, WARRANTY, EXPIRY }

/** ฿ beside a big centred figure over a single underline — the design's amount section. */
@Composable
private fun AmountField(value: String, isThai: Boolean, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("฿", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ScOnSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            TextField(
                value = value,
                onValueChange = onChange,
                placeholder = {
                    Text(
                        "0.00",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScOutlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                textStyle = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScOnSurface,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ScSurfaceContainer.copy(0.2f),
                    unfocusedContainerColor = ScSurface,
                    focusedIndicatorColor = ScPrimary,
                    unfocusedIndicatorColor = ScOutlineVariant,
                    cursorColor = ScPrimary
                ),
                modifier = Modifier.width(200.dp)
            )
        }
        Text(
            if (isThai) "ยอดเงิน (THB)" else "Amount (THB)",
            fontSize = 12.sp,
            color = ScOutline
        )
    }
}

@Composable
private fun CategoryTile(
    key: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ScPrimary.copy(0.05f) else ScSurface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ScPrimary else ScOutline.copy(0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (selected) ScPrimary else ScSurfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                ExpenseCategories.icon(key), null,
                tint = if (selected) Color.White else ScPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ScPrimary else ScOnSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CardPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurface)
            .border(1.dp, ScOutline.copy(0.1f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        content = content
    )
}

/** A switch and the field it reveals. Off means the field is both hidden and cleared. */
@Composable
private fun ColumnScope.ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    revealed: @Composable ColumnScope.() -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScOnSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ScPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = ScSurfaceHigh,
                uncheckedBorderColor = ScOutlineVariant
            )
        )
    }
    if (checked) {
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = revealed)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = ScOnSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun ColumnScope.FieldLabel(text: String, small: Boolean = false) {
    Text(
        text,
        fontSize = if (small) 12.sp else 14.sp,
        fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold,
        color = if (small) ScOutline else ScOnSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}

/** Read-only box that opens the picker — a keyboard has no business editing a date. */
@Composable
private fun DateField(value: LocalDate?, isThai: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ScOutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CalendarToday, null, tint = ScOutline, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            value?.let { thaiDate(it.toString(), isThai) }
                ?: (if (isThai) "เลือกวันที่" else "Pick a date"),
            fontSize = 15.sp,
            color = if (value != null) ScOnSurface else ScOutline
        )
    }
}

@Composable
private fun ReceiptZone(
    attached: Boolean,
    fileName: String?,
    isThai: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScSurfaceContainer.copy(0.3f))
            .border(
                2.dp,
                if (attached) ScPrimary else ScOutlineVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = if (attached) onClear else onPick)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp).background(ScSurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (attached) Icons.Filled.CheckCircle else Icons.Filled.AddAPhoto,
                null,
                tint = ScPrimary
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                attached && isThai -> "แนบใบเสร็จแล้ว — แตะเพื่อลบ"
                attached -> "Receipt attached — tap to remove"
                isThai -> "อัปโหลดรูปภาพใบเสร็จ"
                else -> "Upload a receipt photo"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            fileName ?: if (isThai) "PNG, JPG สูงสุด 5MB" else "PNG, JPG up to 5MB",
            fontSize = 12.sp,
            color = ScOutline,
            maxLines = 1
        )
    }
}

@Composable
private fun ExpenseDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK", color = ScPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ScOutline) }
        }
    ) { DatePicker(state = state) }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ScPrimary,
    unfocusedBorderColor = ScOutlineVariant,
    focusedContainerColor = ScSurface,
    unfocusedContainerColor = ScSurface,
    cursorColor = ScPrimary
)
