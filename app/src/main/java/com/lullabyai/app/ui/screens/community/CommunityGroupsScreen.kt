package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.repository.CommunityGroup
import com.lullabyai.app.data.repository.GroupCategory
import com.lullabyai.app.data.repository.GroupDirectory
import com.lullabyai.app.data.repository.GroupHotThread
import com.lullabyai.app.data.repository.GroupsRepository
import com.lullabyai.app.ui.components.SectionCard
import com.lullabyai.app.ui.components.SectionPill
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import kotlinx.coroutines.launch

/**
 * กลุ่มคุณแม่ — the group directory (mockups 70-71).
 *
 * Four sections, in the order the mockup has them: my groups, the six categories, groups worth
 * joining, and the busiest threads from every group at once.
 *
 * Built out of [SectionCard] rather than its own panels, so this reads as the same app as the
 * ข้อมูลลูก pages - flat, bordered, one accent colour per section.
 *
 * Every number here is counted from real rows by community_group_stats. There is no online count
 * anywhere on this screen: the app has no presence data, so the "385 ออนไลน์" of mockup 66 would
 * be an invented figure. Members and posts-in-24h are shown instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityGroupsScreen(
    navController: NavController,
    repo: GroupsRepository = remember { GroupsRepository() }
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var directory by remember { mutableStateOf(GroupDirectory()) }
    var hotThreads by remember { mutableStateOf<List<GroupHotThread>>(emptyList()) }
    var openCategory by remember { mutableStateOf<GroupCategory?>(null) }
    var categoryGroups by remember { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var joining by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        repo.directory().onSuccess { directory = it }
            .onFailure {
                snackbar.showSnackbar(
                    GroupsRepository.groupWriteError(it.message, s.isThai)
                        ?: if (s.isThai) "โหลดกลุ่มไม่สำเร็จค่ะ" else "Could not load groups."
                )
            }
        repo.hotThreads().onSuccess { hotThreads = it }
        openCategory?.let { category ->
            repo.byCategory(category).onSuccess { categoryGroups = it }
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.isThai) "กลุ่มคุณแม่" else "Groups",
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
        },
        bottomBar = {
            CommunityBottomBar(
                selected = CommunityTab.GROUP,
                isThai = s.isThai,
                onExitToApp = {
                    navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                },
                onFeed = {
                    navController.navigate(Screen.Community.route) { launchSingleTop = true }
                },
                onGroup = { /* already here */ },
                onProfile = { navController.navigate(Screen.CommunityUserProfile.route("me")) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }

            // --- กลุ่มของฉัน ---------------------------------------------------------------
            item {
                SectionCard(
                    title = if (s.isThai) "กลุ่มของฉัน" else "My groups",
                    icon = Icons.Filled.Groups,
                    accent = CommunityPalette.Group,
                    subtitle = if (directory.myGroups.isEmpty()) null
                    else if (s.isThai) "${directory.myGroups.size} กลุ่ม"
                    else "${directory.myGroups.size} joined"
                ) {
                    if (directory.myGroups.isEmpty()) {
                        Text(
                            if (s.isThai) "ยังไม่ได้เข้ากลุ่มไหนเลยค่ะ เลือกจากหมวดหมู่ด้านล่างได้นะคะ"
                            else "You have not joined a group yet. Pick one from the categories below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        directory.myGroups.forEach { group ->
                            GroupRow(
                                group = group,
                                isThai = s.isThai,
                                onClick = {
                                    navController.navigate(Screen.CommunityGroupDetail.route(group.id))
                                }
                            )
                        }
                    }
                }
            }

            // --- หมวดหมู่ -------------------------------------------------------------------
            item {
                SectionCard(
                    title = if (s.isThai) "หมวดหมู่" else "Categories",
                    icon = Icons.Filled.Category,
                    accent = CommunityPalette.Poll
                ) {
                    GroupCategory.entries.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { category ->
                                CategoryTile(
                                    category = category,
                                    isThai = s.isThai,
                                    selected = openCategory == category,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            if (openCategory == category) {
                                                openCategory = null
                                                categoryGroups = emptyList()
                                            } else {
                                                openCategory = category
                                                repo.byCategory(category)
                                                    .onSuccess { categoryGroups = it }
                                                    .onFailure { categoryGroups = emptyList() }
                                            }
                                        }
                                    }
                                )
                            }
                            // An odd row keeps its tiles the same width as the rows above.
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // --- one category, opened in place ---------------------------------------------
            openCategory?.let { category ->
                item {
                    SectionCard(
                        title = if (s.isThai) category.labelTh else category.labelEn,
                        icon = category.icon(),
                        accent = category.accent(),
                        trailing = {
                            IconButton(onClick = {
                                openCategory = null
                                categoryGroups = emptyList()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = if (s.isThai) "ปิด" else "Close",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    ) {
                        if (categoryGroups.isEmpty()) {
                            Text(
                                if (s.isThai) "ยังไม่มีกลุ่มในหมวดนี้ค่ะ" else "No groups here yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            categoryGroups.forEach { group ->
                                GroupRow(
                                    group = group,
                                    isThai = s.isThai,
                                    joining = joining == group.id,
                                    onJoin = {
                                        scope.launch {
                                            joining = group.id
                                            repo.join(group.id)
                                                .onSuccess { reload() }
                                                .onFailure { e ->
                                                    snackbar.showSnackbar(
                                                        GroupsRepository
                                                            .groupWriteError(e.message, s.isThai)
                                                            .orEmpty()
                                                    )
                                                }
                                            joining = null
                                            repo.byCategory(category)
                                                .onSuccess { categoryGroups = it }
                                        }
                                    },
                                    onClick = {
                                        navController.navigate(
                                            Screen.CommunityGroupDetail.route(group.id)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- กลุ่มแนะนำ -----------------------------------------------------------------
            if (directory.recommended.isNotEmpty()) {
                item {
                    SectionCard(
                        title = if (s.isThai) "กลุ่มแนะนำ" else "Suggested groups",
                        icon = Icons.Filled.AutoAwesome,
                        accent = CommunityPalette.Food,
                        // Said plainly, because a parent should know what a suggestion here is
                        // based on - and it is not based on anything about their child.
                        subtitle = if (s.isThai) "เรียงตามกลุ่มที่คุยกันมากที่สุดวันนี้"
                        else "The busiest groups today"
                    ) {
                        directory.recommended.forEach { group ->
                            GroupRow(
                                group = group,
                                isThai = s.isThai,
                                joining = joining == group.id,
                                onJoin = {
                                    scope.launch {
                                        joining = group.id
                                        repo.join(group.id)
                                            .onSuccess { reload() }
                                            .onFailure { e ->
                                                snackbar.showSnackbar(
                                                    GroupsRepository
                                                        .groupWriteError(e.message, s.isThai)
                                                        .orEmpty()
                                                )
                                            }
                                        joining = null
                                    }
                                },
                                onClick = {
                                    navController.navigate(
                                        Screen.CommunityGroupDetail.route(group.id)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // --- กระทู้ฮอตข้ามกลุ่ม ----------------------------------------------------------
            if (hotThreads.isNotEmpty()) {
                item {
                    SectionCard(
                        title = if (s.isThai) "กระทู้ฮอตข้ามกลุ่ม" else "Hot across groups",
                        icon = Icons.Filled.LocalFireDepartment,
                        accent = CommunityPalette.Hot
                    ) {
                        hotThreads.forEach { thread ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        navController.navigate(
                                            Screen.CommunityPostDetail.route(thread.postId)
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    thread.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Text(
                                    if (s.isThai)
                                        "${thread.groupName} · ${thread.commentsCount} ความคิดเห็น"
                                    else
                                        "${thread.groupName} · ${thread.commentsCount} replies",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One group, as a row in any of the lists.
 *
 * [onJoin] null means the row does not offer joining - my own groups do not need the button, and a
 * row that offered it would be asking a parent to join something they are already in.
 */
@Composable
private fun GroupRow(
    group: CommunityGroup,
    isThai: Boolean,
    joining: Boolean = false,
    onJoin: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val accent = group.category.accent()
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CommunityPalette.soft(accent)),
            contentAlignment = Alignment.Center
        ) {
            if (group.coverUrl != null) {
                AsyncImage(
                    model = group.coverUrl,
                    contentDescription = group.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            } else {
                Icon(group.category.icon(), null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!group.isPublic) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = if (isThai) "กลุ่มส่วนตัว" else "Private group",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                groupCountsLine(group, isThai),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.hasDoctor) DoctorModeratedBadge(isThai)
        }

        when {
            joining -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            group.isMember -> SectionPill(
                label = if (isThai) "เข้าร่วมแล้ว" else "Joined",
                accent = CommunityPalette.Group
            )
            onJoin != null -> TextButton(onClick = onJoin) {
                Text(if (isThai) "เข้าร่วม" else "Join", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * มีกุมารแพทย์ร่วมดูแลกลุ่ม.
 *
 * Drawn only when CommunityGroup.hasDoctor is true, which the server derives from a moderator
 * actually holding the doctor role. The wording says the GROUP is moderated by one - not that any
 * particular post in it has been checked by one, which would be a much bigger claim and is not
 * something this app can stand behind.
 */
@Composable
fun DoctorModeratedBadge(isThai: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = CommunityPalette.Doctor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            if (isThai) "มีกุมารแพทย์ร่วมดูแลกลุ่ม" else "A paediatrician helps moderate",
            style = MaterialTheme.typography.labelSmall,
            color = CommunityPalette.Doctor,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Members, and how busy the group has been in the last day. Never an online count. */
fun groupCountsLine(group: CommunityGroup, isThai: Boolean): String = if (isThai) {
    "สมาชิก ${group.memberCount} คน · โพสต์ 24 ชม. ${group.postsToday}"
} else {
    "${group.memberCount} members · ${group.postsToday} posts in 24h"
}

@Composable
private fun CategoryTile(
    category: GroupCategory,
    isThai: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = category.accent()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) CommunityPalette.soft(accent) else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(CommunityPalette.soft(accent)),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon(), null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Text(
            if (isThai) category.labelTh else category.labelEn,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2
        )
    }
}

/**
 * The colour and icon for each of the six headings.
 *
 * Reuses the story-rail accents, so a category means the same colour wherever it appears rather
 * than every screen inventing its own palette.
 */
fun GroupCategory.accent(): Color = when (this) {
    GroupCategory.HEALTH -> CommunityPalette.Health
    GroupCategory.NUTRITION -> CommunityPalette.Food
    GroupCategory.SLEEP -> CommunityPalette.Sleep
    GroupCategory.MILESTONES -> CommunityPalette.Play
    GroupCategory.WELLNESS -> CommunityPalette.Profile
    GroupCategory.GEAR -> CommunityPalette.Hot
}

fun GroupCategory.icon(): ImageVector = when (this) {
    GroupCategory.HEALTH -> Icons.Filled.MonitorHeart
    GroupCategory.NUTRITION -> Icons.Filled.Restaurant
    GroupCategory.SLEEP -> Icons.Filled.Bedtime
    GroupCategory.MILESTONES -> Icons.Filled.Toys
    GroupCategory.WELLNESS -> Icons.Filled.SelfImprovement
    GroupCategory.GEAR -> Icons.Filled.ShoppingBag
}
