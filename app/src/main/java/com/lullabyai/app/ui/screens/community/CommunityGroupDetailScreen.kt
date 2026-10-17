package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.PostKind
import com.lullabyai.app.data.repository.CommunityGroup
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.data.repository.GroupsRepository
import com.lullabyai.app.ui.components.SectionCard
import com.lullabyai.app.ui.components.SectionPill
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import kotlinx.coroutines.launch

/** The two tabs on a group page (mockup 67). */
private enum class GroupFeedTab { RECENT, THREADS }

/**
 * One group (mockups 66-69): header, กฎกลุ่ม, tabs, composer, pinned posts, feed.
 *
 * Three things this screen will not do, and none of the reasons are cosmetic:
 *
 *   * No online count. The app has no presence system - no heartbeat, no last-seen - so the
 *     "385 ออนไลน์" of mockup 66 would be a number with nothing behind it. The header shows
 *     members and posts in the last 24 hours, both counted from real rows.
 *   * The doctor badge is drawn only from CommunityGroup.hasDoctor, which the server derives from a
 *     moderator who actually holds the doctor role. It cannot be switched on for a group no doctor
 *     has ever looked at.
 *   * The composer and the pin control appear only for people who may use them. Membership and
 *     moderator rights are enforced in Postgres either way - hiding the controls is so a parent is
 *     not offered something that will be refused, not the protection itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityGroupDetailScreen(
    navController: NavController,
    groupId: String,
    repo: GroupsRepository = remember { GroupsRepository() },
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var group by remember { mutableStateOf<CommunityGroup?>(null) }
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var tab by remember { mutableStateOf(GroupFeedTab.RECENT) }
    var loading by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    suspend fun reload() {
        var missing = false
        repo.group(groupId)
            .onSuccess { loaded ->
                group = loaded
                // Null covers both "no such group" and "private, and you are not in it": RLS
                // returns no row for either and this screen cannot tell them apart. It must not
                // try - guessing would tell a stranger that a particular private group exists.
                missing = loaded == null
            }
            .onFailure { missing = true }
        notFound = missing
        if (!missing) {
            communityRepo.fetchGroupPosts(groupId).onSuccess { posts = it }
        }
        loading = false
    }

    LaunchedEffect(groupId) { reload() }

    val current = group
    val pinned = posts.filter { it.pinnedAtMillis != null }
    val rest = posts.filter { it.pinnedAtMillis == null }
    val shown = when (tab) {
        GroupFeedTab.RECENT -> rest
        GroupFeedTab.THREADS -> rest.filter { it.kind == PostKind.THREAD }
    }

    suspend fun showError(message: String?) {
        snackbar.showSnackbar(
            message ?: if (s.isThai) "ทำรายการไม่สำเร็จค่ะ" else "That did not go through."
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current?.name ?: if (s.isThai) "กลุ่ม" else "Group",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = if (s.isThai) "ย้อนกลับ" else "Back"
                        )
                    }
                },
                actions = {
                    // The bell from the group header. Only a member has a row to toggle.
                    if (current != null && current.isMember) {
                        IconButton(
                            enabled = !working,
                            onClick = {
                                scope.launch {
                                    working = true
                                    val next = !current.notificationsOn
                                    repo.setNotifications(groupId, next)
                                        .onSuccess { group = current.copy(notificationsOn = next) }
                                        .onFailure { e ->
                                            showError(
                                                GroupsRepository.groupWriteError(e.message, s.isThai)
                                            )
                                        }
                                    working = false
                                }
                            }
                        ) {
                            Icon(
                                if (current.notificationsOn) Icons.Filled.Notifications
                                else Icons.Filled.NotificationsOff,
                                contentDescription = if (s.isThai) "แจ้งเตือนกลุ่ม"
                                else "Group notifications",
                                tint = if (current.notificationsOn) CommunityPalette.Group
                                else MaterialTheme.colorScheme.outline
                            )
                        }
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
                onGroup = {
                    navController.navigate(Screen.CommunityGroups.route) { launchSingleTop = true }
                },
                onProfile = { navController.navigate(Screen.CommunityUserProfile.route("me")) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            notFound || current == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (s.isThai) "ไม่พบกลุ่มนี้ หรือเป็นกลุ่มส่วนตัวที่ยังไม่ได้เข้าร่วมค่ะ"
                    else "This group does not exist, or it is private and you have not joined it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 108.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GroupHeaderCard(
                        group = current,
                        isThai = s.isThai,
                        working = working,
                        onJoin = {
                            scope.launch {
                                working = true
                                repo.join(groupId)
                                    .onSuccess { reload() }
                                    .onFailure { e ->
                                        showError(
                                            GroupsRepository.groupWriteError(e.message, s.isThai)
                                        )
                                    }
                                working = false
                            }
                        },
                        onLeave = {
                            scope.launch {
                                working = true
                                repo.leave(groupId)
                                    .onSuccess { reload() }
                                    .onFailure { e ->
                                        showError(
                                            GroupsRepository.groupWriteError(e.message, s.isThai)
                                        )
                                    }
                                working = false
                            }
                        }
                    )
                }

                // --- กฎกลุ่ม ----------------------------------------------------------------
                current.rules?.takeIf { it.isNotBlank() }?.let { rules ->
                    item {
                        SectionCard(
                            title = if (s.isThai) "กฎกลุ่ม" else "Group rules",
                            icon = Icons.Filled.Gavel,
                            accent = CommunityPalette.AskDoctor
                        ) {
                            Text(
                                rules,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // --- composer, members only -------------------------------------------------
                if (current.isMember) {
                    item {
                        GroupComposerRow(
                            isThai = s.isThai,
                            onCompose = { kind ->
                                navController.navigate(
                                    Screen.CreateCommunityPostInGroup.route(groupId, kind.wire)
                                )
                            }
                        )
                    }
                }

                // --- ปักหมุด ----------------------------------------------------------------
                if (pinned.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = CommunityPalette.Hot,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                if (s.isThai) "โพสต์ปักหมุด" else "Pinned",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CommunityPalette.Hot
                            )
                        }
                    }
                    items(pinned.size, key = { "pinned-" + pinned[it].id }) { index ->
                        val post = pinned[index]
                        GroupPostCard(
                            post = post,
                            isThai = s.isThai,
                            canModerate = current.canModerate,
                            isPinned = true,
                            navController = navController,
                            communityRepo = communityRepo,
                            onTogglePin = {
                                scope.launch {
                                    repo.setPinned(post.id, false)
                                        .onSuccess { reload() }
                                        .onFailure { e ->
                                            showError(
                                                GroupsRepository.groupWriteError(e.message, s.isThai)
                                            )
                                        }
                                }
                            }
                        )
                    }
                }

                // --- tabs -------------------------------------------------------------------
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupTabChip(
                            label = if (s.isThai) "ล่าสุด" else "Recent",
                            accent = CommunityPalette.New,
                            selected = tab == GroupFeedTab.RECENT,
                            onClick = { tab = GroupFeedTab.RECENT }
                        )
                        GroupTabChip(
                            label = if (s.isThai) "กระทู้ถาม-ตอบ" else "Questions",
                            accent = CommunityPalette.AskDoctor,
                            selected = tab == GroupFeedTab.THREADS,
                            onClick = { tab = GroupFeedTab.THREADS }
                        )
                    }
                }

                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (s.isThai) "ยังไม่มีโพสต์ในกลุ่มนี้ค่ะ"
                            else "No posts in this group yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(shown.size, key = { shown[it].id }) { index ->
                        val post = shown[index]
                        GroupPostCard(
                            post = post,
                            isThai = s.isThai,
                            canModerate = current.canModerate,
                            isPinned = false,
                            navController = navController,
                            communityRepo = communityRepo,
                            onTogglePin = {
                                scope.launch {
                                    repo.setPinned(post.id, true)
                                        .onSuccess { reload() }
                                        .onFailure { e ->
                                            showError(
                                                GroupsRepository.groupWriteError(e.message, s.isThai)
                                            )
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The header of mockup 66: cover, name, category, counts, the doctor badge, join or leave.
 *
 * The cover carries a dark gradient over it rather than being drawn plain, so the name stays
 * readable on whatever picture a group owner uploads - including a very bright one.
 */
@Composable
private fun GroupHeaderCard(
    group: CommunityGroup,
    isThai: Boolean,
    working: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    val accent = group.category.accent()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(CommunityPalette.soft(accent))
        ) {
            if (group.coverUrl != null) {
                AsyncImage(
                    model = group.coverUrl,
                    contentDescription = group.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )
            } else {
                Icon(
                    group.category.icon(),
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Center).size(44.dp)
                )
            }

            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionPill(
                    label = if (isThai) group.category.labelTh else group.category.labelEn,
                    accent = accent
                )
                if (!group.isPublic) {
                    SectionPill(
                        label = if (isThai) "กลุ่มส่วนตัว" else "Private",
                        accent = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            group.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                groupCountsLine(group, isThai),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.hasDoctor) DoctorModeratedBadge(isThai)

            if (working) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (group.isMember) {
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isThai) "ออกจากกลุ่ม" else "Leave group")
                }
            } else {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        if (isThai) "เข้าร่วมกลุ่ม" else "Join group",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** The three composer entries from the hub, posting into this group instead of the open feed. */
@Composable
private fun GroupComposerRow(isThai: Boolean, onCompose: (PostKind) -> Unit) {
    SectionCard(
        title = if (isThai) "โพสต์ในกลุ่มนี้" else "Post in this group",
        icon = Icons.Filled.EditNote,
        accent = CommunityPalette.Feed
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposeChip(
                label = if (isThai) "โพสต์" else "Post",
                icon = Icons.Filled.PhotoCamera,
                accent = CommunityPalette.Photo,
                modifier = Modifier.weight(1f),
                onClick = { onCompose(PostKind.POST) }
            )
            ComposeChip(
                label = if (isThai) "ตั้งกระทู้" else "Ask",
                icon = Icons.Filled.HelpOutline,
                accent = CommunityPalette.AskDoctor,
                modifier = Modifier.weight(1f),
                onClick = { onCompose(PostKind.THREAD) }
            )
            ComposeChip(
                label = if (isThai) "โพล" else "Poll",
                icon = Icons.Filled.Poll,
                accent = CommunityPalette.Poll,
                modifier = Modifier.weight(1f),
                onClick = { onCompose(PostKind.POLL) }
            )
        }
    }
}

@Composable
private fun ComposeChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CommunityPalette.soft(accent))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GroupTabChip(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CommunityPalette.soft(accent),
            selectedLabelColor = accent
        )
    )
}

/**
 * A post inside a group: the same card the feed draws, with the moderator pin control above it.
 *
 * The card itself is [CommunityFeedCard] rather than a group-specific copy, so the rules that card
 * enforces - the 1669 banner on an urgent thread, no author to tap on an anonymous one, the
 * "AI ไม่ใช่แพทย์" notice - hold here too without being restated and without drifting apart.
 */
@Composable
private fun GroupPostCard(
    post: CommunityPost,
    isThai: Boolean,
    canModerate: Boolean,
    isPinned: Boolean,
    navController: NavController,
    communityRepo: CommunityRepository,
    onTogglePin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (canModerate) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTogglePin) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = if (isPinned) CommunityPalette.Hot
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isPinned) {
                            if (isThai) "เลิกปักหมุด" else "Unpin"
                        } else {
                            if (isThai) "ปักหมุด" else "Pin"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        CommunityFeedCard(
            post = post,
            isThai = isThai,
            onOpen = { navController.navigate(Screen.CommunityPostDetail.route(post.id)) },
            onLike = { communityRepo.toggleLike(post.id) },
            onAuthorClick = {
                // An anonymous post has no author to open: the server stripped the link before the
                // row ever arrived, so there is nothing here to navigate to.
                post.userId?.let {
                    navController.navigate(Screen.CommunityUserProfile.route(post.authorName))
                }
            },
            onShare = { },
            onReact = { reaction -> communityRepo.setReaction(post.id, reaction) },
            onVote = { optionId -> scope.launch { communityRepo.togglePollVote(post.id, optionId) } },
            onAddPollOption = { label -> scope.launch { communityRepo.addPollOption(post.id, label) } }
        )
    }
}
