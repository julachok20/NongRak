package com.lullabyai.app.ui.screens.community

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import com.lullabyai.app.ui.theme.SecondaryContainer
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import io.github.jan.supabase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityUserProfileScreen(
    authorName: String,
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    childViewModel: ChildViewModel = viewModel(),
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val authState by authViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()
    val allPosts by communityRepo.posts.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        communityRepo.fetchPosts()
    }

    val myDisplayName = remember(authState.profile, childState.child, s.isThai) {
        authState.profile?.username?.trim()?.takeIf { it.isNotEmpty() }
            ?: authState.profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: childState.child?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { if (s.isThai) "คุณแม่น้อง$it" else "Parent of $it" }
            ?: (if (s.isThai) "คุณแม่น้องรัก" else "NongRak Parent")
    }

    val isCurrentUser = authorName.isBlank() ||
        authorName.equals("me", ignoreCase = true) ||
        authorName.equals(myDisplayName, ignoreCase = true) ||
        authorName.equals(authState.profile?.username, ignoreCase = true) ||
        authorName.equals(authState.profile?.fullName, ignoreCase = true)

    val displayedName = if (isCurrentUser) myDisplayName else authorName

    // Build children names label, e.g. "Mother of Giga, Nina, Fifa"
    val childrenNames = remember(childState.children, childState.child) {
        if (childState.children.isNotEmpty()) {
            childState.children.mapNotNull { it.name.trim().takeIf { n -> n.isNotEmpty() } }
        } else {
            listOfNotNull(childState.child?.name?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    val relationshipBadge = remember(authState.profile, childrenNames, isCurrentUser, s.isThai) {
        val rel = authState.profile?.relationship?.trim()?.takeIf { it.isNotEmpty() }
        if (isCurrentUser && childrenNames.isNotEmpty()) {
            val namesStr = childrenNames.joinToString(", ")
            val relTitle = when (rel?.lowercase()) {
                "พ่อ", "father", "dad" -> if (s.isThai) "คุณพ่อของน้อง" else "Father of"
                "แม่", "mother", "mom" -> if (s.isThai) "คุณแม่ของน้อง" else "Mother of"
                null -> if (s.isThai) "คุณแม่ของน้อง" else "Mother of"
                else -> if (s.isThai) "$rel ของน้อง" else "$rel of"
            }
            "($relTitle $namesStr)"
        } else if (isCurrentUser) {
            val relTitle = rel ?: (if (s.isThai) "ผู้ปกครอง" else "Parent")
            "($relTitle)"
        } else {
            if (s.isThai) "(สมาชิกชุมชนน้องรัก)" else "(NongRak Community Member)"
        }
    }

    val avatarUrl = if (isCurrentUser) {
        authState.avatarSignedUrl ?: authState.profile?.avatarUrl
    } else {
        allPosts.find { it.authorName.equals(authorName, ignoreCase = true) }?.authorAvatarUrl
    }

    val currentUserId = authState.profile?.id
    val userPosts = remember(allPosts, displayedName, isCurrentUser, currentUserId) {
        if (isCurrentUser && !currentUserId.isNullOrBlank()) {
            allPosts.filter { it.userId == currentUserId || it.authorName.equals(displayedName, ignoreCase = true) }
        } else {
            allPosts.filter { it.authorName.equals(displayedName, ignoreCase = true) }
        }
    }

    val totalLikesReceived = remember(userPosts) {
        userPosts.sumOf { it.likesCount }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "โปรไฟล์สมาชิก" else "Member Profile",
                onBack = { navController.popBackStack() },
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Profile Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(PrimaryContainer.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)
                            )
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayedName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verified Member",
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Child relation pill badge
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = relationshipBadge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        if (isCurrentUser) {
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.EditProfile.route) },
                                shape = RoundedCornerShape(100.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (s.isThai) "แก้ไขข้อมูลโปรไฟล์" else "Edit Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Activity Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${userPosts.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                text = if (s.isThai) "กระทู้ที่สร้าง" else "Posts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalLikesReceived",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )
                            Text(
                                text = if (s.isThai) "ถูกใจที่ได้รับ" else "Likes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isCurrentUser) "${childState.children.size.coerceAtLeast(if (childState.child != null) 1 else 0)}" else "1+",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (s.isThai) "เด็กในความดูแล" else "Children",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Detailed Contact Card (For current user or contact info)
            if (isCurrentUser) {
                val email = com.lullabyai.app.data.remote.supabase.auth.currentUserOrNull()?.email ?: ""
                val phone = authState.profile?.phone?.takeIf { it.isNotBlank() } ?: (if (s.isThai) "ยังไม่ได้ระบุ" else "Not provided")
                val role = authState.profile?.relationship?.takeIf { it.isNotBlank() } ?: (if (s.isThai) "คุณแม่" else "Mother")

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ContactPhone, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (s.isThai) "ข้อมูลการติดต่อโดยละเอียด" else "Detailed Contact Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Phone, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(if (s.isThai) "เบอร์โทรศัพท์" else "Phone Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(phone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (email.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.Email, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(if (s.isThai) "อีเมล" else "Email", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.FamilyRestroom, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(if (s.isThai) "บทบาทในครอบครัว" else "Family Role", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(role, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Children List Section (If Current User or has children data)
            if (isCurrentUser && (childState.children.isNotEmpty() || childState.child != null)) {
                val displayChildren = if (childState.children.isNotEmpty()) childState.children else listOfNotNull(childState.child)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (s.isThai) "ข้อมูลเด็กในความดูแล" else "Children Under Care",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    displayChildren.forEach { child ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!child.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = child.avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = child.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = childViewModel.calculateAge(s.isThai),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = if (child.gender == "female" || child.gender == "girl") (if (s.isThai) "หญิง" else "Girl")
                                        else if (child.gender == "male" || child.gender == "boy") (if (s.isThai) "ชาย" else "Boy")
                                        else (if (s.isThai) "เด็ก" else "Child"),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // User's Community Posts Feed
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.DynamicFeed, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (s.isThai) "กระทู้ที่เผยแพร่ (${userPosts.size})" else "Published Posts (${userPosts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (userPosts.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (s.isThai) "ยังไม่มีกระทู้ที่เผยแพร่ในชุมชน" else "No posts published yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    userPosts.forEach { post ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = post.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (!post.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = post.imageUrl,
                                        contentDescription = "Post image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = post.timeAgo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                                            Text("${post.likesCount}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.Comment, null, tint = Primary, modifier = Modifier.size(14.dp))
                                            Text("${post.comments.size}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
