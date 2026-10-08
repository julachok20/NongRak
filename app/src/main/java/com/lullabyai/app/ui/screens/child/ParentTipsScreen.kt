package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.R
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * The picture for a tip, by category.
 *
 * Falls back to the family illustration rather than drawing nothing: a row with a hole where the
 * others have a picture reads as a failed image load, which is worse than a generic one. A new
 * category the app has not met yet therefore still looks deliberate.
 */
@androidx.annotation.DrawableRes
private fun tipImageFor(category: String): Int = when (category.trim()) {
    "โภชนาการ" -> R.drawable.tip_nutrition
    "การนอน" -> R.drawable.tip_sleep
    "พัฒนาการ" -> R.drawable.tip_praise
    "สุขภาพ" -> R.drawable.tip_guidance
    else -> R.drawable.tip_family
}

/**
 * เคล็ดลับ — short things worth knowing.
 *
 * Reads the source that already exists rather than inventing a second one:
 * [CommunityRepository.getAiArticleTips], the same written articles หมอเด็กตอบ shows.
 *
 * The notice at the bottom is the point of the screen as much as the tips are: these are general
 * guidance for an age band, NOT advice about this particular child. A screen that blurs the two is
 * quietly claiming more than the app can support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentTipsScreen(
    navController: NavController,
    childViewModel: ChildViewModel,
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val tips = remember { communityRepo.getAiArticleTips() }
    val childName = state.child?.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.isThai) "เคล็ดลับคุณแม่" else "Parent tips",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = if (s.isThai) "ย้อนกลับ" else "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Primary.copy(alpha = 0.10f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (s.isThai) "เคล็ดลับดูแลลูกน้อยในแต่ละช่วงวัย"
                            else "Caring for your child, age by age",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (s.isThai) "อ่านสั้น ๆ ใช้ได้จริง" else "Short, practical reads",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.tip_bonding),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp)
                    )
                }
            }

            if (tips.isEmpty()) {
                item {
                    Text(
                        if (s.isThai) "ยังไม่มีเคล็ดลับในตอนนี้ค่ะ" else "No tips yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(tips, key = { it.id }) { tip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            navController.navigate(Screen.ProfessionalBlogDetail.route(tip.id))
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(tipImageFor(tip.category)),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                tip.category,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            tip.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Text(
                            tip.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (s.isThai) "อ่าน ${tip.readTimeMinutes} นาที"
                                else "${tip.readTimeMinutes} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Said once, at the bottom, where it gets read rather than skipped past.
            item {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (s.isThai)
                            "เคล็ดลับเหล่านี้เป็นคำแนะนำทั่วไปตามช่วงวัย ไม่ใช่คำวินิจฉัยสำหรับ" +
                                (childName?.let { "น้อง$it" } ?: "ลูกของคุณ") +
                                " โดยเฉพาะ หากกังวลเรื่องอาการ ปรึกษาแพทย์นะคะ"
                        else
                            "These are general tips for an age range, not a diagnosis for your " +
                                "child. If you are worried about a symptom, please see a doctor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
