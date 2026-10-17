@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.child

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.handoff.ParentHandoffBrief
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.ChildAvatarPreset
import com.lullabyai.app.data.model.ChildFavorite
import com.lullabyai.app.data.model.FavoriteCategory
import com.lullabyai.app.data.model.FavoriteSource
import com.lullabyai.app.ui.components.SectionCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * แก้ไขข้อมูลลูก — the child's whole profile on one page.
 *
 * Built to the Stitch frame, which is deliberately long: a parent edits this rarely, and usually
 * because a nurse, a nanny or a form asked for something specific. One scroll they can search
 * beats five screens they have to remember the way to.
 *
 * Two things are NOT edited in place. Allergies and chronic conditions are structured rows with
 * their own severity, symptom and prevention fields, and they already have screens that edit them
 * properly — this page shows them and hands over. Duplicating those editors inline would mean two
 * ways to write the same medical rows, which is how they drift apart.
 */
@Composable
fun EditChildScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val thai = s.isThai
    val state by childViewModel.uiState.collectAsState()
    val child = state.child
    val canDelete = state.isChildOwner
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { st -> st.readBytes() } }.getOrNull()
            if (bytes != null) childViewModel.uploadChildAvatar(bytes)
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                val cr = context.contentResolver
                var fname = "document"
                cr.query(it, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) fname = c.getString(idx) ?: fname
                }
                val mime = cr.getType(it)
                val bytes = cr.openInputStream(it)?.use { st -> st.readBytes() }
                if (bytes != null && bytes.size <= 10 * 1024 * 1024) childViewModel.uploadMedicalDocument(fname, mime, bytes)
            }
        }
    }
    LaunchedEffect(child?.id) { childViewModel.loadMedicalDocuments() }

    // Every field is re-seeded when the selected child changes, so switching child mid-edit cannot
    // carry one child's half-typed name onto another's row.
    var fullName by remember(child) { mutableStateOf(child?.fullName ?: "") }
    var name by remember(child) { mutableStateOf(child?.name ?: "") }
    var gender by remember(child) { mutableStateOf(child?.gender ?: "unspecified") }
    var birthdate by remember(child) { mutableStateOf(child?.birthdate ?: "") }
    var birthTime by remember(child) { mutableStateOf(child?.birthTime?.take(5) ?: "") }
    var abo by remember(child) { mutableStateOf(bloodAbo(child?.bloodGroup)) }
    var rhPositive by remember(child) { mutableStateOf(bloodRhPositive(child?.bloodGroup)) }
    var nationalId by remember(child) { mutableStateOf(child?.nationalId ?: "") }
    var placeOfBirth by remember(child) { mutableStateOf(child?.placeOfBirth ?: "") }
    var weight by remember(child) { mutableStateOf(child?.weightKg?.toString() ?: "") }
    var height by remember(child) { mutableStateOf(child?.heightCm?.toString() ?: "") }
    var soothingNote by remember(child) { mutableStateOf(child?.soothingNote ?: "") }
    var notes by remember(child) { mutableStateOf(child?.notes ?: "") }

    fun edited(): Child? = child?.copy(
        fullName = fullName.trim().ifBlank { null },
        name = name.trim(),
        gender = gender,
        birthdate = birthdate.trim(),
        birthTime = birthTime.trim().ifBlank { null },
        bloodGroup = composeBloodGroup(abo, rhPositive),
        nationalId = nationalId.trim().ifBlank { null },
        placeOfBirth = placeOfBirth.trim().ifBlank { null },
        weightKg = weight.toDoubleOrNull(),
        heightCm = height.toDoubleOrNull(),
        soothingNote = soothingNote.trim().ifBlank { null },
        notes = notes.trim().ifBlank { null }
    )

    fun save() { edited()?.let { childViewModel.saveChildProfile(it, onBack) } }

    var recordFeedOpen by remember { mutableStateOf(false) }
    var presetPickerOpen by remember { mutableStateOf(false) }
    if (presetPickerOpen) {
        PresetPickerDialog(
            thai = thai,
            current = ChildAvatarPreset.idOf(child?.avatarUrl),
            onPick = { childViewModel.pickAvatarPreset(it); presetPickerOpen = false },
            onDismiss = { presetPickerOpen = false }
        )
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (thai) "แก้ไขข้อมูลน้อง ${child?.name.orEmpty()}" else "Edit ${child?.name.orEmpty()}",
                onBack = onBack,
                navController = navController,
                actions = {
                    Button(
                        onClick = { save() },
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (thai) "บันทึก" else "Save", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            AvatarCard(
                thai = thai,
                displayName = name.ifBlank { child?.name.orEmpty() },
                ageLine = child?.birthdate?.let { ParentHandoffBrief.compactAge(it, thai) }.orEmpty(),
                avatarUrl = state.childAvatarSignedUrl,
                uploading = state.avatarUploading,
                onUpload = { pickImage() },
                onPreset = { childViewModel.pickAvatarPreset(it) },
                showAll = { presetPickerOpen = true }
            )

            SectionCard(
                title = if (thai) "ข้อมูลทั่วไป" else "General",
                subtitle = if (thai) "ข้อมูลประจำตัวและวันสำคัญ" else "Identity and key dates",
                icon = Icons.Filled.Badge
            ) {
                FieldLabel(if (thai) "ชื่อจริง - นามสกุล (พร้อมคำนำหน้า)" else "Legal name (with title)")
                EditField(fullName, { fullName = it }, placeholder = if (thai) "ด.ช. / ด.ญ. ชื่อ นามสกุล" else "Full legal name")

                FieldLabel(if (thai) "ชื่อเล่น" else "Nickname")
                EditField(name, { name = it })

                FieldLabel(if (thai) "เพศกำเนิด" else "Sex at birth")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderChip(if (thai) "ชาย" else "Male", Icons.Filled.Male, gender == "male", Modifier.weight(1f)) { gender = "male" }
                    GenderChip(if (thai) "หญิง" else "Female", Icons.Filled.Female, gender == "female", Modifier.weight(1f)) { gender = "female" }
                    GenderChip(if (thai) "ไม่ระบุ" else "Unspecified", Icons.Filled.Person, gender != "male" && gender != "female", Modifier.weight(1f)) { gender = "unspecified" }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(if (thai) "วันเกิด" else "Birthdate")
                        EditField(birthdate, { birthdate = it }, placeholder = "YYYY-MM-DD", trailing = Icons.Filled.CalendarToday)
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel(if (thai) "เวลาเกิด" else "Time of birth")
                        EditField(birthTime, { birthTime = it }, placeholder = "HH:MM", trailing = Icons.Filled.Schedule)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(if (thai) "น้ำหนักล่าสุด (กก.)" else "Latest weight (kg)")
                        EditField(weight, { weight = it }, keyboard = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel(if (thai) "ส่วนสูงล่าสุด (ซม.)" else "Latest height (cm)")
                        EditField(height, { height = it }, keyboard = KeyboardType.Decimal)
                    }
                }
            }

            SectionCard(
                title = if (thai) "ข้อมูลการแพทย์และภูมิแพ้" else "Medical and allergies",
                subtitle = if (thai) "ข้อมูลจำเป็นสำหรับกุมารแพทย์และห้องฉุกเฉิน" else "What a paediatrician or an emergency room needs",
                icon = Icons.Filled.HealthAndSafety
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel(if (thai) "กรุ๊ปเลือด (ABO & Rh)" else "Blood group (ABO & Rh)", Modifier.weight(1f))
                    RhToggle(rhPositive) { rhPositive = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "AB", "O").forEach { g ->
                        BloodChip(g, abo == g, Modifier.weight(1f)) { abo = if (abo == g) null else g }
                    }
                }

                LinkedChipSection(
                    thai = thai,
                    title = if (thai) "ประวัติแพ้อาหาร / ยา" else "Food & medication allergies",
                    icon = Icons.Filled.Warning,
                    tint = Error,
                    labels = state.allergies.map { a ->
                        a.name + (if (a.severity.isNotBlank()) " (${severityLabel(a.severity, thai)})" else "")
                    },
                    emptyText = if (child?.noKnownAllergies == true) {
                        if (thai) "ไม่มีประวัติแพ้" else "No known allergies"
                    } else {
                        if (thai) "ยังไม่ได้บันทึก" else "Not recorded yet"
                    },
                    onManage = { navController.navigate(Screen.AllergyHistory.route) }
                )

                LinkedChipSection(
                    thai = thai,
                    title = if (thai) "โรคประจำตัว / ภาวะเฝ้าระวัง" else "Chronic conditions",
                    icon = Icons.Filled.MonitorHeart,
                    tint = Primary,
                    labels = state.chronicConditions.map { it.name },
                    emptyText = if (thai) "ยังไม่ได้บันทึก" else "Not recorded yet",
                    onManage = { navController.navigate(Screen.ChronicRegistry.route) }
                )
            }

            SectionCard(
                title = if (thai) "เอกสารและสูติบัตร" else "Documents",
                subtitle = if (thai) "ข้อมูลทางราชการและบัตรสุขภาพเด็ก" else "Official records",
                icon = Icons.Filled.Folder
            ) {
                FieldLabel(if (thai) "เลขประจำตัวประชาชน / รหัสเด็ก" else "National ID / child code")
                EditField(nationalId, { nationalId = it }, placeholder = "1-2345-67890-12-3", trailing = Icons.Filled.Pin)

                FieldLabel(if (thai) "โรงพยาบาลที่เกิด" else "Hospital of birth")
                EditField(placeOfBirth, { placeOfBirth = it }, trailing = Icons.Filled.LocalHospital)

                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Outline.copy(0.5f)), RoundedCornerShape(16.dp))
                        .clickable { docPicker.launch("*/*") }
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.docUploading) CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.CloudUpload, null, tint = Primary, modifier = Modifier.size(30.dp))
                        Text(if (thai) "แตะเพื่ออัปโหลดสูติบัตร / เอกสาร" else "Tap to upload a document", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("PDF, JPG, PNG (Max 10MB)", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                state.medicalDocuments.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Description, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(d.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        if (canDelete) {
                            IconButton(onClick = { childViewModel.deleteMedicalDocument(d.id, d.filePath) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, if (thai) "ลบไฟล์" else "Remove", tint = Error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            FavoritesSection(
                thai = thai,
                childName = name.ifBlank { child?.name.orEmpty() },
                favorites = state.favorites,
                saving = state.favoritesSaving,
                soothingNote = soothingNote,
                onSoothingNote = { soothingNote = it },
                shared = child?.favoritesShared ?: true,
                onShared = { childViewModel.setFavoritesShared(it) },
                onAdd = { category, label -> childViewModel.addFavorite(category, label, FavoriteSource.PARENT) },
                onRemove = { childViewModel.removeFavorite(it) }
            )

            // Everything the app has logged for this child, newest first — milk, food, sleep,
            // nappies, growth, vaccines, symptoms, medicine and caregivers' notes, each with who
            // wrote it. The server already merges these (list_child_activity_feed); an earlier
            // client-side merge here missed milk, food and care logs.
            LaunchedEffect(child?.id) { childViewModel.loadActivityFeed() }
            val feed = state.activityFeed
            SectionCard(
                title = if (thai) "บันทึกเพิ่มเติม" else "Recent records",
                subtitle = if (thai) "รายการล่าสุดที่บันทึกไว้ในแอป" else "The latest entries logged in the app",
                icon = Icons.Filled.EditNote
            ) {
                if (feed.isEmpty()) {
                    Text(
                        if (thai) "ยังไม่มีการบันทึกในแอป" else "Nothing logged yet",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                } else {
                    feed.take(5).forEach { e -> RecordRow(e, thai) }
                    if (feed.size > 5) {
                        TextButton(onClick = { recordFeedOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (thai) "ดูทั้งหมด (${feed.size})" else "See all (${feed.size})",
                                fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (recordFeedOpen) {
                RecordFeedDialog(thai = thai, entries = feed) { recordFeedOpen = false }
            }

            NavRow(
                title = if (thai) "บัตรประจำตัวสุขภาพดิจิทัล" else "Digital health ID card",
                subtitle = if (thai) "พร้อมใช้งานแบบ QR Code สากล" else "With a scannable QR code",
                icon = Icons.Filled.Badge,
                actionText = if (thai) "ดูบัตร" else "View"
            ) { navController.navigate(Screen.HealthBook.route) }

            NavRow(
                title = if (thai) "ข้อมูลที่ระบบบันทึกจากการถาม-ตอบ" else "What the daily questions recorded",
                subtitle = if (thai) "ข้อมูลและพฤติกรรมที่รวบรวมจากการประเมินและคำถามประจำวัน" else "Behaviour gathered from the daily check-ins",
                icon = Icons.Filled.Insights,
                actionText = if (thai) "ดูสรุป" else "Open"
            ) { navController.navigate(Screen.ChildAiSummary.route) }

            state.error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            if (canDelete) {
                TextButton(onClick = { navController.navigate(Screen.ProfileHistory.route) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.History, null, tint = Outline, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (thai) "ประวัติการแก้ไขโปรไฟล์" else "Profile edit history", color = Outline, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (thai) "บันทึกการเปลี่ยนแปลงทั้งหมด" else "Save all changes",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------
// Blood group is one stored string ("O+") shown as two controls.
// -------------------------------------------------------------------------------------------

internal fun bloodAbo(stored: String?): String? {
    val v = stored?.trim()?.uppercase()?.trimEnd('+', '-')?.trim()
    return v?.takeIf { it == "A" || it == "B" || it == "AB" || it == "O" }
}

/** Unknown Rh reads as positive — the common case, and what the ID card already prints. */
internal fun bloodRhPositive(stored: String?): Boolean = stored?.trim()?.endsWith("-") != true

internal fun composeBloodGroup(abo: String?, rhPositive: Boolean): String? =
    abo?.let { it + if (rhPositive) "+" else "-" }

private fun severityLabel(severity: String, thai: Boolean): String = when (severity) {
    "severe" -> if (thai) "รุนแรง" else "severe"
    "moderate" -> if (thai) "ปานกลาง" else "moderate"
    else -> if (thai) "เล็กน้อย" else "mild"
}

// -------------------------------------------------------------------------------------------
// Pieces
// -------------------------------------------------------------------------------------------

@Composable
private fun AvatarCard(
    thai: Boolean,
    displayName: String,
    ageLine: String,
    avatarUrl: String?,
    uploading: Boolean,
    onUpload: () -> Unit,
    onPreset: (String) -> Unit,
    showAll: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SecondaryContainer.copy(alpha = 0.4f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (thai) "ข้อมูลอัปเดตเพื่อการวิเคราะห์ AI ที่แม่นยำ" else "Kept up to date for sharper AI analysis",
            style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold
        )
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(SecondaryContainer).clickable { onUpload() },
                contentAlignment = Alignment.Center
            ) {
                when {
                    uploading -> CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    !avatarUrl.isNullOrBlank() -> AsyncImage(
                        model = avatarUrl,
                        contentDescription = if (thai) "รูปน้อง" else "Child photo",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                    else -> Text("👶", fontSize = 46.sp)
                }
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Primary).clickable { onUpload() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
        }
        if (displayName.isNotBlank()) {
            Text(
                (if (thai) "น้อง " else "") + displayName,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }
        if (ageLine.isNotBlank()) {
            Text(ageLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(2.dp))
        Text(
            if (thai) "หรือเลือกรูปสำเร็จรูป" else "Or pick a ready-made photo",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChildAvatarPreset.ids.take(6).forEach { id ->
                PresetFace(id = id, selected = false, size = 46.dp, thai = thai) { onPreset(id) }
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f))
                    .clickable { showAll() },
                contentAlignment = Alignment.Center
            ) {
                Text("+${ChildAvatarPreset.ids.size - 6}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
        }
        TextButton(onClick = { showAll() }) {
            Text(if (thai) "ดูรูปทั้งหมด" else "See all photos", fontSize = 12.sp, color = Primary)
        }
    }
}

@Composable
private fun FavoritesSection(
    thai: Boolean,
    childName: String,
    favorites: List<ChildFavorite>,
    saving: Boolean,
    soothingNote: String,
    onSoothingNote: (String) -> Unit,
    shared: Boolean,
    onShared: (Boolean) -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    SectionCard(
        title = if (thai) "ของโปรดและข้อควรจำเพาะ" else "Favourites and what to remember",
        subtitle = if (thai) "สิ่งที่น้องชอบเป็นพิเศษ สำหรับผู้ดูแล"
        else "What this child likes, gathered for whoever is looking after them",
        icon = Icons.Filled.Favorite
    ) {
        FavoriteCategory.ordered.forEach { category ->
            FavoriteCategoryBlock(
                thai = thai,
                category = category,
                items = favorites.filter { it.category == category },
                saving = saving,
                onAdd = { onAdd(category, it) },
                onRemove = onRemove
            )
        }

        Spacer(Modifier.height(6.dp))
        FieldLabel(
            if (thai) "ข้อสังเกตและวิธีปลอบใจเมื่อน้องงอแง" else "How to settle ${childName.ifBlank { "them" }}"
        )
        OutlinedTextField(
            value = soothingNote, onValueChange = onSoothingNote,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = {
                Text(
                    if (thai) "เช่น ชอบจับผ้าห่มนุ่มๆ และเปิดเพลงกล่อมเบาๆ ก่อนนอน"
                    else "e.g. holds a soft blanket, likes a quiet lullaby before sleep",
                    color = Outline, fontSize = 12.sp
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = editFieldColors()
        )

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SecondaryContainer.copy(alpha = 0.35f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Group, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (thai) "แชร์ให้ผู้ดูแลเห็น" else "Share with caregivers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (thai) "คุณยาย และ พี่เลี้ยง สามารถดูข้อความและของโปรดได้" else "Grandparents and the nanny can read these",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = shared, onCheckedChange = onShared)
        }
    }
}

@Composable
private fun FavoriteCategoryBlock(
    thai: Boolean,
    category: String,
    items: List<ChildFavorite>,
    saving: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var draft by remember(category) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            FavoriteCategory.label(category, thai),
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (items.isEmpty()) {
            Text(
                if (thai) "ยังไม่ได้บันทึก" else "Nothing recorded yet",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
        }
        // FlowRow so a long Thai label wraps instead of being clipped on a narrow phone.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { fav -> FavoriteChip(thai, fav) { onRemove(fav.id) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it.take(120) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (thai) "เพิ่มรายการ..." else "Add an item...", color = Outline, fontSize = 12.sp) },
                shape = RoundedCornerShape(12.dp),
                colors = editFieldColors()
            )
            FilledTonalButton(
                onClick = { if (draft.isNotBlank()) { onAdd(draft.trim()); draft = "" } },
                enabled = draft.isNotBlank() && !saving,
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (thai) "เพิ่ม" else "Add", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun FavoriteChip(thai: Boolean, fav: ChildFavorite, onRemove: () -> Unit) {
    val fromAi = fav.source != FavoriteSource.PARENT
    Row(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(if (fromAi) SecondaryContainer.copy(alpha = 0.5f) else Primary.copy(alpha = 0.10f))
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fromAi) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(fav.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Filled.Close, if (thai) "ลบ" else "Remove", tint = Outline, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun LinkedChipSection(
    thai: Boolean,
    title: String,
    icon: ImageVector,
    tint: Color,
    labels: List<String>,
    emptyText: String,
    onManage: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
            TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (thai) "จัดการ" else "Manage", fontSize = 12.sp, color = Primary)
            }
        }
        if (labels.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.labelSmall, color = Outline)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { l ->
                    Text(
                        l, fontSize = 12.sp, color = tint,
                        modifier = Modifier.clip(RoundedCornerShape(100.dp))
                            .background(tint.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(actionText, fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun GenderChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Color.White else Outline, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BloodChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RhToggle(positive: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        if (positive) "Rh-Positive (+)" else "Rh-Negative (−)",
        fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = if (positive) Color(0xFF16A34A) else Error,
        modifier = Modifier.clickable { onChange(!positive) }
    )
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    trailing: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = placeholder?.let { { Text(it, color = Outline, fontSize = 13.sp) } },
        trailingIcon = trailing?.let { { Icon(it, null, tint = Outline, modifier = Modifier.size(18.dp)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = editFieldColors()
    )
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

/** One ready-made face. The art is already circular with a transparent corner, so no clip fights it. */
@Composable
private fun PresetFace(
    id: String,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp,
    thai: Boolean,
    onClick: () -> Unit
) {
    val res = ChildAvatarPreset.drawableRes(id) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = if (thai) "รูปสำเร็จรูป" else "Ready-made photo",
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Primary else Primary.copy(alpha = 0.18f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}

/**
 * All the ready-made faces.
 *
 * A dialog rather than another inline row: there are over a hundred of them, and a grid cannot
 * live inside the edit page's own vertical scroll — two nested scrolls in the same direction is
 * a fight the user loses. In here the grid owns the scrolling.
 */
@Composable
private fun PresetPickerDialog(
    thai: Boolean,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (thai) "เลือกรูปสำเร็จรูป" else "Pick a ready-made photo",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (thai) "รูปนี้จะแสดงทุกที่ รวมถึงใน LINE และบัตรประจำตัว"
                            else "Shows everywhere, including LINE and the ID card",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, if (thai) "ปิด" else "Close")
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ChildAvatarPreset.ids, key = { it }) { id ->
                        PresetFace(id = id, selected = id == current, size = 60.dp, thai = thai) { onPick(id) }
                    }
                }
            }
        }
    }
}

/** One line of the child's history: what it was, the detail worth seeing, who, and when. */
@Composable
private fun RecordRow(e: ChildActivityEntry, thai: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(recordTint(e.kind).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(recordIcon(e.kind), null, tint = recordTint(e.kind), modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(recordLabel(e.kind, thai), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val detail = listOfNotNull(e.detail, e.note).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" — ")
            if (detail.isNotBlank()) {
                Text(
                    detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(e.occurredAt.take(10), fontSize = 10.sp, color = Outline)
            e.authorName?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 9.sp, color = Outline, maxLines = 1)
            }
        }
    }
}

/** Kinds as list_child_activity_feed names them; care_* covers every caregiver log type. */
private fun recordLabel(kind: String, thai: Boolean): String = when {
    kind == "growth" -> if (thai) "การเติบโต" else "Growth"
    kind == "vaccine" -> if (thai) "วัคซีน" else "Vaccine"
    kind == "milk" -> if (thai) "นม" else "Milk"
    kind == "food" -> if (thai) "อาหาร" else "Food"
    kind == "waste" -> if (thai) "ขับถ่าย" else "Nappy"
    kind == "sleep" -> if (thai) "การนอน" else "Sleep"
    kind == "symptom" -> if (thai) "อาการป่วย" else "Symptom"
    kind == "medicine" -> if (thai) "ยา" else "Medicine"
    kind.startsWith("care_") -> if (thai) "บันทึกผู้ดูแล" else "Caregiver note"
    else -> if (thai) "บันทึก" else "Record"
}

private fun recordIcon(kind: String): ImageVector = when {
    kind == "growth" -> Icons.Filled.TrendingUp
    kind == "vaccine" -> Icons.Filled.Vaccines
    kind == "milk" || kind == "food" -> Icons.Filled.Restaurant
    kind == "waste" -> Icons.Filled.BabyChangingStation
    kind == "sleep" -> Icons.Filled.Bedtime
    kind == "symptom" -> Icons.Filled.MonitorHeart
    kind == "medicine" -> Icons.Filled.Medication
    else -> Icons.Filled.EditNote
}

@Composable
private fun recordTint(kind: String): Color = when (kind) {
    "symptom", "medicine" -> Error
    "sleep" -> Color(0xFF6366F1)
    "waste" -> Color(0xFFD97706)
    "milk", "food" -> Color(0xFF16A34A)
    else -> Primary
}

/** The whole history. A dialog keeps the parent on the page they were editing. */
@Composable
private fun RecordFeedDialog(
    thai: Boolean,
    entries: List<ChildActivityEntry>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (thai) "บันทึกทั้งหมด (${entries.size})" else "All records (${entries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, if (thai) "ปิด" else "Close")
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e -> RecordRow(e, thai) }
                }
            }
        }
    }
}
