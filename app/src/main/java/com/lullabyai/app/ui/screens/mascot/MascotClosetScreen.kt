package com.lullabyai.app.ui.screens.mascot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.repository.MascotBond
import com.lullabyai.app.data.repository.MascotBondRepository
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.mascot.Mascot
import com.lullabyai.app.ui.mascot.MascotArt
import com.lullabyai.app.ui.mascot.MascotMood
import com.lullabyai.app.ui.mascot.MascotWardrobe
import com.lullabyai.app.ui.mascot.WardrobeSlot
import com.lullabyai.app.ui.theme.Primary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ตู้เสื้อผ้า — the companion, how far it has been raised, and what it can wear.
 *
 * Levels are per companion, so this screen reads every bond, not just the active one: the grid has
 * to show what each companion is worth, and which companions are unlocked depends on the best bond
 * the carer has raised anywhere.
 */
@Composable
fun MascotClosetScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsManager(context) }
    val repo = remember { MascotBondRepository() }

    var bonds by remember { mutableStateOf<Map<MascotArt, MascotBond>>(emptyMap()) }
    var active by remember { mutableStateOf(MascotArt.NONGRAK) }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val chosen = settings.selectedMascot.first()
        active = MascotArt.entries.firstOrNull { it.key == chosen } ?: MascotArt.NONGRAK
        // Every companion, because "which are unlocked" depends on the best bond of any of them.
        bonds = MascotArt.entries.associateWith { art ->
            repo.get(art.key).getOrDefault(MascotBond(art.key))
        }
    }

    LaunchedEffect(Unit) {
        reload()
        loading = false
    }

    val bestLevel = bonds.values.maxOfOrNull { it.level } ?: 1
    val activeBond = bonds[active] ?: MascotBond(active.key)

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตู้เสื้อผ้าน้องรัก" else "Companion Closet",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CompanionStage(active, activeBond, isThai)

            note?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section(if (isThai) "เพื่อนของหนู" else "Companions") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MascotArt.entries.forEach { art ->
                        CompanionCard(
                            art = art,
                            bond = bonds[art] ?: MascotBond(art.key),
                            unlocked = MascotWardrobe.skinUnlocked(art, bestLevel),
                            selected = art == active,
                            isThai = isThai,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                settings.setSelectedMascot(art.key)
                                active = art
                                note = null
                            }
                        }
                    }
                }
            }

            Section(if (isThai) "เสื้อผ้า" else "Outfits") {
                val shirts = MascotWardrobe.slot(WardrobeSlot.SHIRT)
                if (shirts.isEmpty()) {
                    // Says what is true rather than looking broken: the rig and the unlocks are
                    // live, only the drawings are missing.
                    EmptyWardrobe(isThai)
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        shirts.forEach { item ->
                            val wearable = item.artFor(active) != null
                            OutfitCard(
                                label = item.nameFor(isThai),
                                requiredLevel = item.requiredLevel,
                                unlocked = item.unlockedAt(activeBond.level) && wearable,
                                selected = activeBond.equipped["shirt"] == item.id,
                                isThai = isThai,
                                modifier = Modifier.weight(1f)
                            ) {
                                scope.launch {
                                    val worn = activeBond.equipped["shirt"] == item.id
                                    repo.equip(
                                        active.key,
                                        WardrobeSlot.SHIRT,
                                        if (worn) null else item.id
                                    ).onSuccess {
                                        reload()
                                        note = null
                                    }.onFailure { e ->
                                        // Name what was received: a silent no-op here reads as a
                                        // broken button.
                                        note = e.message ?: "equip failed"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Section(if (isThai) "ได้ค่าความผูกพันจาก" else "Earns bond from") {
                EarnList(isThai)
            }
        }
    }
}

/** The companion itself, wearing whatever it has on, above its level and progress. */
@Composable
private fun CompanionStage(art: MascotArt, bond: MascotBond, isThai: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Mascot(
                art = art,
                mood = MascotMood.HAPPY,
                resting = MascotMood.IDLE,
                // Anything worn but not earned on THIS companion is dropped by the catalogue, so
                // switching to a less-raised companion cannot show borrowed clothes.
                garments = MascotWardrobe.garmentsFor(art, bond.equipped, bond.level),
                modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isThai) "ระดับความผูกพัน ${bond.level}" else "Bond level ${bond.level}",
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { MascotWardrobe.progressWithinLevel(bond.xp, bond.level) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(4.dp))
            val next = MascotWardrobe.xpForLevel(bond.level + 1)
            Text(
                if (isThai) "${bond.xp} / $next แต้ม ถึงระดับ ${bond.level + 1}"
                else "${bond.xp} / $next xp to level ${bond.level + 1}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun CompanionCard(
    art: MascotArt,
    bond: MascotBond,
    unlocked: Boolean,
    selected: Boolean,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Card(
        modifier
            .aspectRatio(0.78f)
            .then(if (unlocked) Modifier.clickable(onClick = onSelect) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Primary.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(
                    art = art,
                    mood = if (selected) MascotMood.HAPPY else MascotMood.IDLE,
                    animateFace = false,
                    modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                )
                Text(
                    if (unlocked) (if (isThai) "ระดับ ${bond.level}" else "Lv ${bond.level}")
                    else (if (isThai) "ระดับ ${MascotWardrobe.skinRequiredLevel(art)}"
                    else "Lv ${MascotWardrobe.skinRequiredLevel(art)}"),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!unlocked) {
                Box(
                    Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.72f)),
                    Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = if (isThai) "ยังไม่ปลดล็อก" else "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitCard(
    label: String,
    requiredLevel: Int,
    unlocked: Boolean,
    selected: Boolean,
    isThai: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Card(
        modifier
            .aspectRatio(0.85f)
            .then(if (unlocked) Modifier.clickable(onClick = onToggle) else Modifier)
            .then(
                if (selected) Modifier.border(2.dp, Primary, RoundedCornerShape(14.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(6.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 11.sp, textAlign = TextAlign.Center)
                if (!unlocked) {
                    Text(
                        if (isThai) "ระดับ $requiredLevel" else "Lv $requiredLevel",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWardrobe(isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (isThai) "ยังไม่มีเสื้อผ้าในตู้" else "The wardrobe is empty",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "เสื้อผ้าชุดแรกกำลังออกแบบอยู่ ระดับความผูกพันที่สะสมไว้ตอนนี้จะยังอยู่ครบเมื่อเสื้อผ้ามาถึง"
                else "The first outfits are still being drawn. Bond levels earned now are kept, and unlock them when they arrive.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What actually moves the level, so the number is never mysterious. */
@Composable
private fun EarnList(isThai: Boolean) {
    val rows = if (isThai) listOf(
        "เพิ่มข้อมูลลูก" to 50,
        "ฉีดวัคซีนครบ 1 เข็ม" to 40,
        "บันทึกการพบแพทย์" to 30,
        "อาการป่วยหายแล้ว" to 30,
        "พัฒนาการใหม่" to 25,
        "ชั่งน้ำหนัก/วัดส่วนสูง" to 20,
        "ดูแลลูกในแต่ละวัน" to 5
    ) else listOf(
        "Add a child" to 50,
        "A vaccine dose given" to 40,
        "Log a doctor visit" to 30,
        "An illness recovered" to 30,
        "A new milestone" to 25,
        "Weight / height recorded" to 20,
        "Any care logged today" to 5
    )
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { (label, points) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 12.sp)
                    Text("+$points", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (isThai) "ไม่มีการนับวันติดต่อกัน และแต้มไม่มีวันลดลง"
                else "No streaks, and points never go down.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
