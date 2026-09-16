package com.lullabyai.app.ui.screens.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityComment
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.ProfessionalAdviceBlog
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import com.lullabyai.app.ui.theme.SecondaryContainer
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentCommunityHubScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    childViewModel: ChildViewModel = viewModel(),
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    val authState by authViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        communityRepo.fetchPosts()
    }

    val childrenNames = remember(childState.children, childState.child) {
        if (childState.children.isNotEmpty()) {
            childState.children.mapNotNull { it.name.trim().takeIf { n -> n.isNotEmpty() } }
        } else {
            listOfNotNull(childState.child?.name?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    val currentUserName = remember(authState.profile, childState.child, s.isThai) {
        authState.profile?.username?.trim()?.takeIf { it.isNotEmpty() }
            ?: authState.profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: (if (s.isThai) "คุณแม่น้องรัก" else "NongRak Parent")
    }

    val currentUserRole = remember(authState.profile, childrenNames, s.isThai) {
        val rel = authState.profile?.relationship?.trim()?.takeIf { it.isNotEmpty() }
        if (childrenNames.isNotEmpty()) {
            val namesStr = childrenNames.joinToString(", ")
            val relTitle = when (rel?.lowercase()) {
                "พ่อ", "father", "dad" -> if (s.isThai) "คุณพ่อของน้อง" else "Father of"
                "แม่", "mother", "mom" -> if (s.isThai) "คุณแม่ของน้อง" else "Mother of"
                null -> if (s.isThai) "คุณแม่ของน้อง" else "Mother of"
                else -> if (s.isThai) "$rel ของน้อง" else "$rel of"
            }
            "($relTitle $namesStr)"
        } else {
            val relTitle = rel ?: (if (s.isThai) "ผู้ปกครอง" else "Parent")
            "($relTitle)"
        }
    }

    val posts by communityRepo.posts.collectAsState(initial = emptyList())
    val removedMine by communityRepo.removedMine.collectAsState(initial = emptyList())
    val blogs by communityRepo.blogs.collectAsState(initial = communityRepo.getProfessionalBlogs())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ทั้งหมด") }
    var selectedHashtagFilter by remember { mutableStateOf<String?>(null) }

    val popularHashtags = remember(posts) { communityRepo.getPopularHashtags() }
    val recommendedHashtags = remember { communityRepo.getRecommendedHashtags() }

    var selectedPostForComments by remember { mutableStateOf<CommunityPost?>(null) }

    val categories = listOf(
        if (s.isThai) "ทั้งหมด" else "All",
        if (s.isThai) "โภชนาการ" else "Nutrition",
        if (s.isThai) "การนอน" else "Sleep",
        if (s.isThai) "พัฒนาการ" else "Development",
        if (s.isThai) "สุขภาพ" else "Health"
    )

    val filteredPosts = remember(posts, searchQuery, selectedCategory, selectedHashtagFilter) {
        posts.filter { post ->
            val matchesQuery = searchQuery.isBlank() ||
                post.content.contains(searchQuery, ignoreCase = true) ||
                post.authorName.contains(searchQuery, ignoreCase = true) ||
                post.tags.any { it.contains(searchQuery, ignoreCase = true) }

            val matchesCategory = selectedCategory == "ทั้งหมด" || selectedCategory == "All" ||
                post.tags.any { it.contains(selectedCategory, ignoreCase = true) } ||
                (selectedCategory == "โภชนาการ" && post.content.contains("อาหาร", ignoreCase = true)) ||
                (selectedCategory == "การนอน" && post.content.contains("นอน", ignoreCase = true)) ||
                (selectedCategory == "สุขภาพ" && (post.content.contains("ไข้", ignoreCase = true) || post.content.contains("วัคซีน", ignoreCase = true))) ||
                (selectedCategory == "พัฒนาการ" && post.content.contains("พัฒนาการ", ignoreCase = true))

            val matchesHashtag = selectedHashtagFilter == null ||
                post.tags.any { it.equals(selectedHashtagFilter, ignoreCase = true) } ||
                post.content.contains(selectedHashtagFilter!!, ignoreCase = true)

            matchesQuery && matchesCategory && matchesHashtag
        }
    }

    val filteredBlogs = remember(blogs, selectedCategory) {
        if (selectedCategory == "ทั้งหมด" || selectedCategory == "All") blogs
        else blogs.filter { it.category.contains(selectedCategory, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "ชุมชนคุณแม่ & บล็อกความรู้" else "Parent Community & Expert Blog",
                navController = navController,
                showNotification = true,
                showProfile = false,
                actions = {
                    IconButton(onClick = { scope.launch { communityRepo.fetchPosts() } }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = if (s.isThai) "รีเฟรช" else "Refresh",
                            tint = Primary
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.CommunityUserProfile.route("me")) }) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarUrl = authState.avatarSignedUrl ?: authState.profile?.avatarUrl
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "My Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.Person, contentDescription = "My Profile", tint = Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.COMMUNITY,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onCommunity = { /* already here */ }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateCommunityPost.route) },
                shape = CircleShape,
                containerColor = Primary,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = if (s.isThai) "สร้างโพสต์" else "Create Post")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The owner removed one of this person's posts (migration 20260915230000): tell them.
            if (removedMine.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Warning, null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (s.isThai) "โพสต์ของคุณถูกลบ ${removedMine.size} รายการ" else "${removedMine.size} of your posts were removed",
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                removedMine.take(3).forEach { removed ->
                                    val reason = removed.reason?.takeIf { it.isNotBlank() }
                                        ?: if (s.isThai) "ไม่เป็นไปตามแนวทางชุมชน" else "Against community guidelines"
                                    Text("\"${removed.preview}\" — $reason", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                Text(
                                    if (s.isThai) "ผู้อื่นจะไม่เห็นโพสต์นี้แล้ว" else "Others can no longer see it.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            TextButton(onClick = { communityRepo.dismissRemoved(removedMine.map { it.id }) }) {
                                Text(if (s.isThai) "รับทราบ" else "OK", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    placeholder = {
                        Text(
                            if (s.isThai) "ค้นหาบทความคุณหมอ หรือหัวข้อพูดคุย..." else "Search articles or discussions...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )
            }

            // Category Filter Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) Primary else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shadowElevation = if (isSelected) 1.dp else 0.dp
                        ) {
                            Text(
                                text = category,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Hashtags Discovery Bar (Popular & Recommended)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Active filter indicator if any
                    if (selectedHashtagFilter != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Tag, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = (if (s.isThai) "กำลังกรองด้วย: " else "Filtering by: ") + selectedHashtagFilter,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                }
                                TextButton(
                                    onClick = { selectedHashtagFilter = null },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (s.isThai) "ล้างตัวกรอง" else "Clear", color = Primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // 1. Popular Hashtags
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                            Text(
                                text = if (s.isThai) "แฮชแท็กยอดนิยม" else "Popular Hashtags",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            popularHashtags.forEach { stat ->
                                val isSelected = selectedHashtagFilter == stat.tag
                                Surface(
                                    onClick = {
                                        selectedHashtagFilter = if (isSelected) null else stat.tag
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (isSelected) Color(0xFFFF5722) else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color.Transparent else Color(0xFFFF5722).copy(alpha = 0.3f)
                                    ),
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stat.tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color(0xFFFF5722).copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = "${stat.count}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color(0xFFFF5722)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Recommended Hashtags
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (s.isThai) "แฮชแท็กแนะนำสำหรับคุณแม่" else "Recommended Topics",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recommendedHashtags.forEach { tag ->
                                val isSelected = selectedHashtagFilter == tag
                                Surface(
                                    onClick = {
                                        selectedHashtagFilter = if (isSelected) null else tag
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (isSelected) Primary else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color.Transparent else Primary.copy(alpha = 0.25f)
                                    ),
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Professional Doctor Advice Blogs Section (บล็อกคำแนะนำจากแพทย์ผู้เชี่ยวชาญ)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (s.isThai) "บทความคำแนะนำจาก AI น้องรัก" else "AI NongRak Advice & Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PrimaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "${filteredBlogs.size} " + (if (s.isThai) "บทความ" else "articles"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        filteredBlogs.forEach { blog ->
                            DoctorBlogCard(
                                blog = blog,
                                isThai = s.isThai,
                                onClick = { navController.navigate(Screen.ProfessionalBlogDetail.route(blog.id)) }
                            )
                        }
                    }
                }
            }

            // Community Feed Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Diversity3,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (s.isThai) "กระทู้แลกเปลี่ยนจากผู้ปกครอง" else "Parent Discussions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "${filteredPosts.size} " + (if (s.isThai) "โพสต์" else "posts"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Community Feed Items or Friendly Empty State
            if (filteredPosts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Forum,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                if (s.isThai) "ยังไม่มีโพสต์ในชุมชนขณะนี้" else "No Community Discussions Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                if (s.isThai)
                                    "มาร่วมเป็นคนแรกที่แบ่งปันเรื่องราว ถามคำถาม หรือแชร์ประสบการณ์การเลี้ยงลูกเพื่อแลกเปลี่ยนกับคุณพ่อคุณแม่ท่านอื่นกันเถอะ!"
                                else
                                    "Be the first parent to share a question, story, or moment to connect with other parents!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = { navController.navigate(Screen.CreateCommunityPost.route) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (s.isThai) "เริ่มสร้างโพสต์แรกของคุณ" else "Create First Post", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    CommunityPostCard(
                        post = post,
                        isThai = s.isThai,
                        onLike = { communityRepo.toggleLike(post.id) },
                        onCommentClick = { selectedPostForComments = post },
                        onAuthorClick = {
                            val target = if (post.authorName.equals(currentUserName, ignoreCase = true) ||
                                post.authorName.equals(authState.profile?.username, ignoreCase = true) ||
                                post.authorName.equals(authState.profile?.fullName, ignoreCase = true)) "me" else post.authorName
                            navController.navigate(Screen.CommunityUserProfile.route(target))
                        },
                        onTagClick = { tag ->
                            selectedHashtagFilter = if (selectedHashtagFilter == tag) null else tag
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Community Post Comments & Interaction Sheet
    selectedPostForComments?.let { post ->
        val latestPost = posts.find { it.id == post.id } ?: post
        CommunityPostCommentsSheet(
            post = latestPost,
            isThai = s.isThai,
            onDismiss = { selectedPostForComments = null },
            onLikePost = { communityRepo.toggleLike(latestPost.id) },
            onAddComment = { text ->
                communityRepo.addComment(
                    postId = latestPost.id,
                    authorName = currentUserName,
                    authorRole = currentUserRole,
                    content = text,
                    authorAvatarUrl = authState.avatarSignedUrl ?: authState.profile?.avatarUrl,
                    userId = authState.profile?.id
                )
            },
            onLikeComment = { commentId ->
                communityRepo.toggleCommentLike(latestPost.id, commentId)
            },
            onAuthorClick = { name ->
                selectedPostForComments = null
                navController.navigate(Screen.CommunityUserProfile.route(name))
            }
        )
    }
}

@Composable
private fun RuleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rich Doctor Advice Card */
@Composable
private fun DoctorBlogCard(
    blog: ProfessionalAdviceBlog,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(310.dp)
            .height(190.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(PrimaryContainer.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Category & Read Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = blog.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                        Text(
                            "${blog.readTimeMinutes} " + (if (isThai) "นาที" else "min"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Title
                Text(
                    blog.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // AI NongRak Byline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text(
                        blog.doctorName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            // Bottom CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE53935), modifier = Modifier.size(12.dp))
                    Text(
                        "${blog.likesCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    if (isThai) "อ่านบทความฉบับเต็ม >" else "Read Article >",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}

/** Community Post Card Component */
@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    isThai: Boolean,
    onLike: () -> Unit,
    onCommentClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onTagClick: (String) -> Unit = {}
) {
    Card(
        onClick = onCommentClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Author header (clickable to view profile)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAuthorClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!post.authorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.authorAvatarUrl,
                        contentDescription = "Avatar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${post.authorRole} • ${post.timeAgo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Post content
            Text(
                post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!post.imageUrl.isNullOrBlank()) {
                val mediaContext = androidx.compose.ui.platform.LocalContext.current
                val thumbnail: @Composable () -> Unit = {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = post.imageUrl,
                            contentDescription = if (post.videoUrl.isNullOrBlank()) "Post image" else "Video thumbnail",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (!post.videoUrl.isNullOrBlank()) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                                Icon(
                                    Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                                    modifier = Modifier.padding(8.dp).size(26.dp)
                                )
                            }
                        }
                    }
                }
                if (post.videoUrl.isNullOrBlank()) {
                    thumbnail()
                } else {
                    // ponytail: no in-app player — the clip opens in the phone's own video app.
                    Surface(
                        onClick = {
                            runCatching {
                                mediaContext.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(post.videoUrl))
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) { thumbnail() }
                }
            }

            // Tags (clickable to filter)
            if (post.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    post.tags.forEach { tag ->
                        Surface(
                            onClick = { onTagClick(tag) },
                            shape = RoundedCornerShape(100.dp),
                            color = Primary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Action row (Like, Comment)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onLike() }
                    ) {
                        Icon(
                            if (post.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "${post.likesCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (post.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onCommentClick() }
                    ) {
                        Icon(
                            Icons.Filled.ModeComment,
                            contentDescription = "Comment",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "${post.commentsCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    if (isThai) "ดูความคิดเห็น >" else "View Comments >",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Community Post Comments BottomSheet Modal */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityPostCommentsSheet(
    post: CommunityPost,
    isThai: Boolean,
    onDismiss: () -> Unit,
    onLikePost: () -> Unit,
    onAddComment: (String) -> Unit,
    onLikeComment: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    var commentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isThai) "ความคิดเห็น (${post.comments.size})" else "Comments (${post.comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Scrollable Content & Comments List
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Original Post summary Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.clickable { onAuthorClick(post.authorName) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!post.authorAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = post.authorAvatarUrl,
                                    contentDescription = "Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                    .background(PrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text(post.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(post.timeAgo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Text(post.content, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Comments List
                if (post.comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isThai) "ยังไม่มีความคิดเห็น มาร่วมแสดงความคิดเห็นคนแรกกันเถอะ!" else "No comments yet. Be the first to reply!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    post.comments.forEach { comment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!comment.authorAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = comment.authorAvatarUrl,
                                    contentDescription = "Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { onAuthorClick(comment.authorName) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryContainer)
                                        .clickable { onAuthorClick(comment.authorName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(18.dp))
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAuthorClick(comment.authorName) },
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(comment.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(comment.timeAgo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(comment.content, style = MaterialTheme.typography.bodySmall)
                            }

                            IconButton(
                                onClick = { onLikeComment(comment.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (comment.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Like comment",
                                    tint = if (comment.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Add Comment Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    placeholder = {
                        Text(if (isThai) "เขียนความคิดเห็น..." else "Write a comment...", fontSize = 13.sp)
                    },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onAddComment(commentText.trim())
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (commentText.isNotBlank()) Primary else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (commentText.isNotBlank()) Color.White else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
