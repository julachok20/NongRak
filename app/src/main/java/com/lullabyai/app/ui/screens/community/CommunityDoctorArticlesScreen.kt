package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer

/**
 * หมอเด็กตอบ — the doctor and AI article list.
 *
 * These used to sit inline on the community feed. The Stitch redesign of that page has no article
 * section, so the list lives here and is reached from the story rail. Deleting the section should
 * not have deleted the feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDoctorArticlesScreen(
    navController: NavController,
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val blogs by communityRepo.blogs.collectAsState(initial = communityRepo.getProfessionalBlogs())

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "หมอเด็กตอบ" else "Ask a doctor",
                onBack = { navController.popBackStack() },
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        bottomBar = {
            CommunityBottomBar(
                selected = CommunityTab.FEED,
                isThai = s.isThai,
                onExitToApp = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onFeed = { navController.navigate(Screen.Community.route) { launchSingleTop = true } },
                onGroup = { },
                onProfile = { navController.navigate(Screen.CommunityUserProfile.route("me")) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (s.isThai) {
                        "บทความจากคุณหมอและ AI น้องรัก เป็นข้อมูลทั่วไป ไม่ใช่การวินิจฉัยลูกของคุณ"
                    } else {
                        "Articles from doctors and NongRak AI. General information, not a diagnosis " +
                            "of your child."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            items(blogs, key = { it.id }) { blog ->
                Card(
                    onClick = { navController.navigate(Screen.ProfessionalBlogDetail.route(blog.id)) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(50), color = PrimaryContainer) {
                                Text(
                                    blog.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Icon(
                                Icons.Filled.Schedule, null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (s.isThai) "${blog.readTimeMinutes} นาที" else "${blog.readTimeMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Text(
                            blog.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            blog.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 3
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalHospital, null,
                                tint = Primary, modifier = Modifier.size(14.dp)
                            )
                            Text(
                                blog.doctorName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}
