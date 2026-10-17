package com.lullabyai.app.ui.screens.community

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalBlogDetailScreen(
    blogId: String,
    navController: NavController,
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val blogs by communityRepo.blogs.collectAsState(initial = communityRepo.getProfessionalBlogs())
    val blog = blogs.find { it.id == blogId } ?: communityRepo.getBlogById(blogId)

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "บทความจาก AI น้องรัก" else "AI NongRak Insights",
                onBack = { navController.popBackStack() },
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (blog != null) {
                                communityRepo.toggleBlogLike(blog.id)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (blog?.isLiked == true) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            if (blog?.isLiked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (blog?.isLiked == true) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${blog?.likesCount ?: 0} " + (if (s.isThai) "ถูกใจ" else "Likes"),
                            color = if (blog?.isLiked == true) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                if (blog != null) {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TITLE, blog.title)
                                        putExtra(Intent.EXTRA_TEXT, "${blog.title}\n\n${blog.summary}\n\n— คำแนะนำโดย ${blog.doctorName} (NongRak App)")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, if (s.isThai) "แชร์บทความ" else "Share Article")
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = { navController.popBackStack() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(
                                if (s.isThai) "อ่านเสร็จสิ้น" else "Done",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (blog == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (s.isThai) "ไม่พบบทความที่ต้องการ" else "Article not found",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Category & Read Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Primary
                    ) {
                        Text(
                            text = blog.category,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${blog.readTimeMinutes} " + (if (s.isThai) "นาทีการอ่าน" else "min read"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Title & Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = blog.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = blog.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // AI NongRak Author Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = blog.doctorName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = "Verified AI Advisor",
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = blog.doctorTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = blog.doctorHospital,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Key Medical Takeaways Card
                if (blog.keyTakeaways.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryContainer.copy(alpha = 0.35f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (s.isThai) "ข้อคิดสำคัญทางการแพทย์" else "Key Medical Takeaways",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }

                            blog.keyTakeaways.forEach { takeaway ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        "•",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = takeaway,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Article Main Content
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (s.isThai) "เนื้อหาและคำแนะนำฉบับสมบูรณ์" else "Full Medical Guidelines",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = blog.fullContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                    }
                }

                // Pediatric FAQs (Doctor Q&A)
                if (blog.faqs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Quiz,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (s.isThai) "คำถามพบบ่อยและคำตอบจาก AI น้องรัก (AI Q&A)" else "Frequently Asked Questions & AI Answers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        blog.faqs.forEach { (q, a) ->
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PrimaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                "Q",
                                                fontWeight = FontWeight.Bold,
                                                color = Primary,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = q,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFE8F5E9)
                                        ) {
                                            Text(
                                                "A",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = a,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Disclaimer note
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (s.isThai)
                                "บทความและคำแนะนำนี้ประมวลผลโดย AI น้องรัก ตามหลักมาตรฐานกุมารเวชศาสตร์สากล สำหรับการดูแลสุขภาพเบื้องต้น ไม่สามารถทดแทนการวินิจฉัยโดยแพทย์ผู้เชี่ยวชาญ"
                            else
                                "This article is curated by AI NongRak following international pediatric guidelines for general care guidance.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
