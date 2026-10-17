package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.PostKind
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import com.lullabyai.app.ui.theme.SecondaryContainer
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

/**
 * ชุมชนคุณแม่ — the community feed, rebuilt to the Stitch mockups (images 51-53).
 *
 * This REPLACES the previous hub rather than extending it. Gone, because none of it is in the
 * design: the always-open search bar, the category chip row, the popular-hashtags card, the
 * community-rules card, the inline AI article list, and the floating + button. The composer row
 * is the way to post now, and the doctor articles moved into the story rail so the feature keeps
 * an entry point instead of being deleted with the section that held it.
 *
 * Kept deliberately: the removed-posts banner. It is the only way an author learns their post was
 * taken down, and it is not decoration.
 */
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

    val authState by authViewModel.uiState.collectAsState()
    val posts by communityRepo.posts.collectAsState(initial = emptyList())
    val removedMine by communityRepo.removedMine.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        communityRepo.fetchPosts()
    }

    val snackbar = remember { SnackbarHostState() }

    // Search is an icon in the header, not a permanent bar — mockup 51.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // กำลังฮิต (Hot) / ล่าสุด (New) and the topic chip beside them.
    var hotSort by remember { mutableStateOf(true) }
    var topic by remember { mutableStateOf<CommunityTopic?>(null) }

    val avatarUrl = authState.avatarSignedUrl ?: authState.profile?.avatarUrl

    val visiblePosts = remember(posts, query, hotSort, topic) {
        posts
            .filter { post ->
                val matchesQuery = query.isBlank() ||
                    post.content.contains(query, ignoreCase = true) ||
                    post.title.orEmpty().contains(query, ignoreCase = true) ||
                    post.authorName.contains(query, ignoreCase = true) ||
                    post.tags.any { it.contains(query, ignoreCase = true) }
                matchesQuery && (topic == null || topic!!.matches(post))
            }
            .let { visible ->
                if (!hotSort) {
                    visible.sortedByDescending { it.createdAtMillis }
                } else {
                    // Engagement decayed over hours, so a busy post from this morning beats a
                    // busier one from last week. A comment counts double a like: replying took
                    // more effort and is a better sign the thread is worth opening.
                    val now = System.currentTimeMillis()
                    visible.sortedByDescending { post ->
                        val score = post.likesCount + post.commentsCount * 2
                        val ageHours = ((now - post.createdAtMillis) / 3_600_000.0).coerceAtLeast(0.0)
                        // +2 so a brand-new post with no engagement does not divide by ~0 and
                        // rocket to the top; 1.5 is a gentle decay rather than a cliff.
                        score / Math.pow(ageHours + 2.0, 1.5)
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            CommunityHeader(
                isThai = s.isThai,
                searching = searching,
                query = query,
                onQueryChange = { query = it },
                onToggleSearch = {
                    searching = !searching
                    if (!searching) query = ""
                },
                onNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        },
        bottomBar = {
            CommunityBottomBar(
                selected = CommunityTab.FEED,
                isThai = s.isThai,
                onExitToApp = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onFeed = { /* already here */ },
                onGroup = {
                    navController.navigate(Screen.CommunityGroups.route) { launchSingleTop = true }
                },
                onProfile = { navController.navigate(Screen.CommunityUserProfile.route("me")) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            // Only the TOP inset is consumed. The footer floats rather than docking, so the feed is
            // meant to pass beneath it; the bottom contentPadding is what keeps the last card clear
            // of the pill instead of hidden behind it.
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // An author only ever learns their post was removed from here.
            if (removedMine.isNotEmpty()) {
                item { RemovedBanner(count = removedMine.size, isThai = s.isThai) }
            }

            item {
                StoryRail(
                    isThai = s.isThai,
                    myAvatarUrl = avatarUrl,
                    selected = topic,
                    onCompose = { navController.navigate(Screen.CreateCommunityPostOfKind.route(PostKind.POST.wire)) },
                    onTopic = { picked -> topic = if (topic == picked) null else picked },
                    onDoctorArticles = { navController.navigate(Screen.CommunityDoctorArticles.route) }
                )
            }

            item {
                ComposerBox(
                    isThai = s.isThai,
                    avatarUrl = avatarUrl,
                    onOpen = { kind -> navController.navigate(Screen.CreateCommunityPostOfKind.route(kind.wire)) }
                )
            }

            item {
                SortChips(
                    isThai = s.isThai,
                    hot = hotSort,
                    topic = topic,
                    onHot = { hotSort = it },
                    onClearTopic = { topic = null }
                )
            }

            if (visiblePosts.isEmpty()) {
                item { EmptyFeed(isThai = s.isThai, filtered = query.isNotBlank() || topic != null) }
            }

            items(visiblePosts, key = { it.id }) { post ->
                CommunityFeedCard(
                    post = post,
                    isThai = s.isThai,
                    onOpen = { navController.navigate(Screen.CommunityPostDetail.route(post.id)) },
                    onLike = { communityRepo.toggleLike(post.id) },
                    onReact = { reaction -> communityRepo.setReaction(post.id, reaction) },
                    onAuthorClick = {
                        val me = authState.profile?.id
                        val target = if (me != null && post.userId == me) "me" else post.authorName
                        navController.navigate(Screen.CommunityUserProfile.route(target))
                    },
                    onShare = { sharePostLink(context, post) },
                    onVote = { optionId -> scope.launch { communityRepo.togglePollVote(post.id, optionId) } },
                    onAddPollOption = { label -> scope.launch { communityRepo.addPollOption(post.id, label) } },
                    onTagClick = { tag -> query = tag }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun CommunityHeader(
    isThai: Boolean,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onNotifications: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isThai) "ชุมชนคุณแม่" else "Parent Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(shape = RoundedCornerShape(50), color = PrimaryContainer) {
                            Text(
                                "NONGRAK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        if (isThai) "แลกเปลี่ยน แบ่งปัน อุ่นใจไปด้วยกัน"
                        else "Share, ask, and feel less alone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (searching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (isThai) "ค้นหา" else "Search",
                        tint = Primary
                    )
                }
                IconButton(onClick = onNotifications) {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = if (isThai) "การแจ้งเตือน" else "Notifications",
                        tint = Primary
                    )
                }
            }

            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    placeholder = {
                        Text(
                            if (isThai) "ค้นหาหัวข้อ หรือคุณแม่..." else "Search posts or parents...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Story rail
// ---------------------------------------------------------------------------------------------

/**
 * The topics in the rail. These filter the feed on words the posts actually contain, so a chip
 * never shows an empty list while matching posts sit below it.
 */
enum class CommunityTopic(
    val thai: String,
    val english: String,
    val icon: ImageVector,
    val accent: Color,
    private val words: List<String>
) {
    FOOD("อาหารเด็ก", "Feeding", Icons.Filled.Restaurant, CommunityPalette.Food,
        listOf("อาหาร", "BLW", "นม", "กิน")),
    SLEEP("ทริคลูกหลับ", "Sleep", Icons.Filled.Bedtime, CommunityPalette.Sleep,
        listOf("นอน", "หลับ", "sleep")),
    PLAY("ของเล่นเสริม", "Play", Icons.Filled.Toys, CommunityPalette.Play,
        listOf("ของเล่น", "เล่น", "พัฒนาการ")),
    HEALTH("สุขภาพลูก", "Health", Icons.Filled.MonitorHeart, CommunityPalette.Health,
        listOf("ไข้", "วัคซีน", "ป่วย", "ผื่น"));

    fun label(isThai: Boolean): String = if (isThai) thai else english

    fun matches(post: CommunityPost): Boolean {
        val haystack = post.content + " " + post.title.orEmpty() + " " + post.tags.joinToString(" ")
        return words.any { haystack.contains(it, ignoreCase = true) }
    }
}

@Composable
private fun StoryRail(
    isThai: Boolean,
    myAvatarUrl: String?,
    selected: CommunityTopic?,
    onCompose: () -> Unit,
    onTopic: (CommunityTopic) -> Unit,
    onDoctorArticles: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            RailItem(
                label = if (isThai) "เรื่องเล่าวันนี้" else "Your story",
                isThai = isThai,
                avatarUrl = myAvatarUrl,
                badge = Icons.Filled.Add,
                selected = false,
                accent = CommunityPalette.Story,
                onClick = onCompose
            )
        }
        item {
            // The AI/doctor articles used to have their own section on this page. The section is
            // gone with the redesign, so the feature keeps its entry point here rather than
            // becoming unreachable.
            RailItem(
                label = if (isThai) "หมอเด็กตอบ" else "Ask a doctor",
                isThai = isThai,
                avatarUrl = null,
                icon = Icons.Filled.LocalHospital,
                selected = false,
                accent = CommunityPalette.Doctor,
                onClick = onDoctorArticles
            )
        }
        items(CommunityTopic.entries.toList(), key = { it.name }) { topic ->
            RailItem(
                label = topic.label(isThai),
                isThai = isThai,
                avatarUrl = null,
                icon = topic.icon,
                selected = selected == topic,
                accent = topic.accent,
                onClick = { onTopic(topic) }
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    isThai: Boolean,
    avatarUrl: String?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    badge: ImageVector? = null
) {
    Column(
        modifier = Modifier.width(72.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (selected) CommunityPalette.soft(accent).copy(alpha = 0.3f) else CommunityPalette.soft(accent)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !avatarUrl.isNullOrBlank() -> AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                    icon != null -> Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
                    else -> Icon(Icons.Filled.Person, null, tint = accent, modifier = Modifier.size(26.dp))
                }
            }
            badge?.let {
                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        it, null, tint = Color.White,
                        modifier = Modifier.padding(3.dp).size(13.dp)
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Composer box and sort chips
// ---------------------------------------------------------------------------------------------

@Composable
private fun ComposerBox(isThai: Boolean, avatarUrl: String?, onOpen: (PostKind) -> Unit) {
    // Flat and bordered, same as the ข้อมูลลูก cards.
    Card(
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(SecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
                Surface(
                    onClick = { onOpen(PostKind.POST) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isThai) "คุณแม่กำลังคิดอะไรอยู่? ถามหรือแชร์ประสบการณ์..."
                        else "What is on your mind? Ask or share...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposerEntry(
                    label = if (isThai) "รูปภาพ/วิดีโอ" else "Photo/Video",
                    icon = Icons.Filled.Image,
                    accent = CommunityPalette.Photo,
                    modifier = Modifier.weight(1f)
                ) { onOpen(PostKind.POST) }
                ComposerEntry(
                    label = if (isThai) "ตั้งกระทู้ถามหมอ" else "Ask a doctor",
                    icon = Icons.Filled.LocalHospital,
                    accent = CommunityPalette.AskDoctor,
                    modifier = Modifier.weight(1f)
                ) { onOpen(PostKind.THREAD) }
                ComposerEntry(
                    label = if (isThai) "โพลสำรวจ" else "Poll",
                    icon = Icons.Filled.BarChart,
                    accent = CommunityPalette.Poll,
                    modifier = Modifier.weight(1f)
                ) { onOpen(PostKind.POLL) }
            }
        }
    }
}

@Composable
private fun SortChips(
    isThai: Boolean,
    hot: Boolean,
    topic: CommunityTopic?,
    onHot: (Boolean) -> Unit,
    onClearTopic: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = hot,
            onClick = { onHot(true) },
            label = {
                Text(
                    if (isThai) "กำลังฮิต (Hot)" else "Hot",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.LocalFireDepartment, null,
                    tint = if (hot) CommunityPalette.Hot else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CommunityPalette.soft(CommunityPalette.Hot),
                selectedLabelColor = CommunityPalette.Hot
            )
        )
        FilterChip(
            selected = !hot,
            onClick = { onHot(false) },
            label = {
                Text(
                    if (isThai) "ล่าสุด (New)" else "New",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Schedule, null,
                    tint = if (!hot) CommunityPalette.New else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CommunityPalette.soft(CommunityPalette.New),
                selectedLabelColor = CommunityPalette.New
            )
        )
        // The active topic appears as a removable chip, so it is obvious why the feed is short.
        topic?.let {
            FilterChip(
                selected = true,
                onClick = onClearTopic,
                label = { Text(it.label(isThai), style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Filled.Close, null, modifier = Modifier.size(15.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CommunityPalette.soft(it.accent),
                    selectedLabelColor = it.accent,
                    selectedTrailingIconColor = it.accent
                )
            )
        }
    }
}

@Composable
private fun EmptyFeed(isThai: Boolean, filtered: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.ChatBubbleOutline, null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(42.dp)
        )
        Text(
            // Two different situations, two different messages: nothing here yet is not the same
            // problem as a filter hiding everything.
            when {
                filtered && isThai -> "ไม่พบโพสต์ที่ตรงกับที่เลือก"
                filtered -> "No posts match that filter"
                isThai -> "ยังไม่มีโพสต์ในชุมชน เริ่มเรื่องแรกได้เลยค่ะ"
                else -> "No posts yet — start the first one"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RemovedBanner(count: Int, isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (isThai) "โพสต์ของคุณถูกนำออก $count รายการ" else "$count of your posts were removed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** One of the three composer entry points under the composer box. */
@Composable
private fun ComposerEntry(
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CommunityPalette.soft(accent),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The share sheet for a feed card.
 *
 * Shares the web link rather than a deep link, so it opens for someone who does not have the app.
 */
private fun sharePostLink(context: android.content.Context, post: CommunityPost) {
    val link = "$COMMUNITY_SHARE_BASE/${post.id}"
    val subject = post.title?.takeIf { it.isNotBlank() } ?: post.content.take(80)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        putExtra(android.content.Intent.EXTRA_TEXT, "$subject\n$link")
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
}
