package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.Period

/**
 * เลือกลูกน้อยเพื่อดำเนินการต่อ — which child a quick action is about.
 *
 * Shown ONLY when the family has more than one child. Most families have one, and asking a parent
 * to pick her only child before every log is a tax on the common case to serve the rarer one; the
 * caller checks the count and goes straight to the destination otherwise.
 *
 * Choosing here calls the same [ChildViewModel.selectChild] the header switcher calls, so the two
 * can never disagree. That matters more than it looks: if the picker held one child while the
 * switcher held another, a dose would be written against the wrong child - and nothing on screen
 * would tell her.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildPickerScreen(
    navController: NavController,
    /** Where to go once a child is chosen. */
    nextRoute: String,
    childViewModel: ChildViewModel
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val children = state.children

    // Defaults to the ACTIVE child, never to the first row. She has already told the app who she is
    // looking at, and quietly moving that selection is how the wrong record gets written.
    var selectedId by remember(state.child?.id, children) {
        mutableStateOf(state.child?.id ?: children.firstOrNull()?.id)
    }

    // A family that drops to one child while this is open has nothing left to choose.
    LaunchedEffect(children.size) {
        if (children.size <= 1) {
            children.firstOrNull()?.let { childViewModel.selectChild(it.id) }
            navController.navigate(nextRoute) {
                popUpTo(Screen.ChildPicker.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.isThai) "เลือกลูกน้อย" else "Choose a child",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (s.isThai) "ปิด" else "Close"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.isThai) "เลือกลูกน้อยเพื่อดำเนินการต่อ" else "Choose a child to continue",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (s.isThai) "แตะเลือกลูกน้อยที่ต้องการบันทึกหรือดูข้อมูล"
                else "Tap the child you want to record or view.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(children, key = { it.id }) { child ->
                    ChildPickRow(
                        child = child,
                        birthOrderLabel = birthOrderLabel(children, child, s.isThai),
                        selected = child.id == selectedId,
                        isThai = s.isThai,
                        onClick = { selectedId = child.id }
                    )
                }
            }

            Button(
                onClick = {
                    val id = selectedId ?: return@Button
                    childViewModel.selectChild(id)
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.ChildPicker.route) { inclusive = true }
                    }
                },
                enabled = selectedId != null,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(52.dp)
            ) {
                Text(
                    if (s.isThai) "ดำเนินการต่อ" else "Continue",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ArrowForward, null,
                    tint = Color.White, modifier = Modifier.size(18.dp)
                )
            }

            Text(
                if (s.isThai) "สลับโปรไฟล์ลูกน้อยได้ตลอดเวลาจากเมนูด้านบน"
                else "You can switch child profiles any time from the menu above.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

/**
 * One child, as a row. Shared by the picker and by ลูกของคุณ on the home screen so the two cannot
 * drift apart - a child who reads as "คนโต" in one place must read the same in the other.
 */
@Composable
internal fun ChildPickRow(
    child: Child,
    birthOrderLabel: String?,
    selected: Boolean,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (!child.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = child.avatarUrl,
                    contentDescription = child.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(26.dp))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isThai) "น้อง ${child.name}" else child.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                birthOrderLabel?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Primary.copy(alpha = 0.10f)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = listOfNotNull(
                    ageText(child.birthdate, isThai),
                    genderText(child.gender, isThai)
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * คนโต / คนกลาง / น้องเล็ก, from birth order.
 *
 * Null when a birthdate is missing, rather than guessing a position: calling a child the eldest
 * when the app does not actually know is a small lie a parent spots instantly.
 */
internal fun birthOrderLabel(children: List<Child>, child: Child, isThai: Boolean): String? {
    val dated = children.mapNotNull { c ->
        runCatching { LocalDate.parse(c.birthdate) }.getOrNull()?.let { c.id to it }
    }
    if (dated.size < 2 || dated.none { it.first == child.id }) return null
    val ordered = dated.sortedBy { it.second }.map { it.first }
    return when (ordered.indexOf(child.id)) {
        0 -> if (isThai) "คนโต" else "Eldest"
        ordered.lastIndex -> if (isThai) "น้องเล็ก" else "Youngest"
        else -> if (isThai) "คนกลาง" else "Middle"
    }
}

/** "วัย 2 ขวบ 3 เดือน", or months under a year. Null when the birthdate is missing or unparseable. */
internal fun ageText(birthdate: String, isThai: Boolean): String? {
    val born = runCatching { LocalDate.parse(birthdate) }.getOrNull() ?: return null
    val now = LocalDate.now()
    if (born.isAfter(now)) return null
    val p = Period.between(born, now)
    return when {
        p.years >= 1 && p.months > 0 ->
            if (isThai) "วัย ${p.years} ขวบ ${p.months} เดือน" else "${p.years}y ${p.months}m"
        p.years >= 1 -> if (isThai) "วัย ${p.years} ขวบ" else "${p.years}y"
        p.months >= 1 -> if (isThai) "วัย ${p.months} เดือน" else "${p.months}m"
        else -> if (isThai) "วัย ${p.days} วัน" else "${p.days}d"
    }
}

internal fun genderText(gender: String, isThai: Boolean): String? = when (gender.lowercase()) {
    "male", "boy" -> if (isThai) "เพศชาย" else "Boy"
    "female", "girl" -> if (isThai) "เพศหญิง" else "Girl"
    else -> null
}
