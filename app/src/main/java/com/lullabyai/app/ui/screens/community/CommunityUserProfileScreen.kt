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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.lullabyai.app.data.repository.FriendState
import com.lullabyai.app.util.ContributorLevel
import com.lullabyai.app.data.model.PostKind
import kotlinx.coroutines.launch
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

    // --- Friends (round 2) ---------------------------------------------------------------------
    //
    // This screen is addressed by display NAME, but a friendship is between two user ids. The id
    // is recovered from a post this author wrote. An author with no posts left, or one who only
    // ever posted anonymously (where the server strips user_id), cannot be friended from here -
    // the friend bar simply does not appear, rather than guessing at who they are.
    val friendsRepo = remember { com.lullabyai.app.data.repository.FriendsRepository() }
    val friendScope = rememberCoroutineScope()
    val targetUserId = remember(userPosts, isCurrentUser) {
        if (isCurrentUser) null else userPosts.firstNotNullOfOrNull { it.userId }
    }

    var friendState by remember(targetUserId) {
        mutableStateOf(FriendState.NONE)
    }
    var friendshipId by remember(targetUserId) { mutableStateOf<String?>(null) }
    var askingFriend by remember { mutableStateOf(false) }
    var friendNote by remember { mutableStateOf("") }
    var friendMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshFriendState() {
        val id = targetUserId ?: return
        friendsRepo.stateWith(id).getOrNull()?.let { (state, rowId) ->
            friendState = state
            friendshipId = rowId
        }
    }

    LaunchedEffect(targetUserId) { refreshFriendState() }

    // --- Profile numbers and the people around it (round 2) -------------------------------------
    var friendCount by remember(targetUserId, isCurrentUser) { mutableIntStateOf(0) }
    var mutualFriends by remember(targetUserId) {
        mutableStateOf<List<com.lullabyai.app.data.repository.FriendSummary>>(emptyList())
    }
    var suggestions by remember(isCurrentUser) {
        mutableStateOf<List<com.lullabyai.app.data.repository.FriendSummary>>(emptyList())
    }

    LaunchedEffect(targetUserId, isCurrentUser, currentUserId) {
        val subject = if (isCurrentUser) currentUserId else targetUserId
        if (subject != null) friendCount = friendsRepo.friendCount(subject)

        // Mutual friends only mean anything on someone else's profile.
        mutualFriends = if (!isCurrentUser && targetUserId != null) {
            friendsRepo.mutualFriends(targetUserId)
        } else {
            emptyList()
        }

        if (isCurrentUser) {
            // Anyone already connected, already asked, already refused, or blocked is excluded -
            // suggesting someone who said no is the worst thing this list can do.
            suggestions = friendsRepo
                .suggestions(excludeUserIds = friendsRepo.connectedUserIds(), limit = 8)
                .getOrDefault(emptyList())
        }
    }

    // The badge is earned from real activity, never decoration. Answers given are this author's
    // comments on OTHER people's posts, counted from the feed that is already loaded.
    val answersGiven = remember(allPosts, displayedName) {
        allPosts.filterNot { it.authorName.equals(displayedName, ignoreCase = true) }
            .sumOf { post -> post.comments.count { it.authorName.equals(displayedName, ignoreCase = true) } }
    }
    val contributorPoints = remember(userPosts, totalLikesReceived, answersGiven) {
        ContributorLevel.points(userPosts.size, totalLikesReceived, answersGiven)
    }
    val contributorLevel = remember(contributorPoints) { ContributorLevel.level(contributorPoints) }

    // Which tab of the profile feed is showing: all / กระทู้ถาม-ตอบ / สื่อ&วิดีโอ, per mockup 59.
    var profileTab by remember { mutableIntStateOf(0) }

    // Another parent's bio and city come from their profile row; mine come from authState.
    var profileBio by remember(targetUserId) { mutableStateOf<String?>(null) }
    var profileCity by remember(targetUserId) { mutableStateOf<String?>(null) }

    LaunchedEffect(targetUserId) {
        val id = targetUserId ?: return@LaunchedEffect
        friendsRepo.profile(id).getOrNull()?.let { summary ->
            profileBio = summary.bio
            profileCity = summary.city
        }
    }

    LaunchedEffect(friendMessage) {
        if (friendMessage != null) {
            kotlinx.coroutines.delay(3500)
            friendMessage = null
        }
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
        bottomBar = {
            // Same footer as the feed, so Profile stays lit and Feed is one tap away rather than
            // only reachable by the back arrow.
            CommunityBottomBar(
                selected = CommunityTab.PROFILE,
                isThai = s.isThai,
                onExitToApp = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onFeed = { navController.navigate(Screen.Community.route) { launchSingleTop = true } },
                onGroup = {
                    navController.navigate(Screen.CommunityGroups.route) { launchSingleTop = true }
                },
                onProfile = { /* already here */ }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top inset only: the footer floats, so the page scrolls under it. The extra
                // bottom padding below is what keeps the last card clear of the pill.
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = padding.calculateBottomPadding() + 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Profile Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(0.dp),
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
                            // Was a hardcoded "1+" for anyone else's profile — a number the app
                            // did not know and had no business showing. This one is counted by
                            // the database.
                            Text(
                                text = "$friendCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (s.isThai) "เพื่อนในคอมมู" else "Friends",
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
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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

            // Badge, bio and city — the header block from mockups 54 and 58.
            ProfileIdentityCard(
                isThai = s.isThai,
                level = contributorLevel,
                points = contributorPoints,
                bio = if (isCurrentUser) authState.profile?.bio else profileBio,
                city = if (isCurrentUser) authState.profile?.city else profileCity
            )

            // Friend controls, or the requests inbox when this is my own profile.
            if (isCurrentUser) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.CommunityFriendRequests.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PersonAddAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "คำขอเป็นเพื่อนใหม่" else "Friend requests")
                }

                // The switch the suggestion strip promises. It lives on the profile, next to what
                // it controls, rather than buried in settings - a parent who does not want their
                // child's age band reaching strangers should find it where they see the strip.
                var discoverable by remember(authState.profile?.discoverable) {
                    mutableStateOf(authState.profile?.discoverable ?: true)
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (s.isThai) "ให้คุณแม่คนอื่นค้นพบโปรไฟล์นี้"
                                else "Let other parents discover this profile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (s.isThai) {
                                    "แนะนำโปรไฟล์คุณจากช่วงวัยของลูก (เช่น 7-9 เดือน) ไม่เปิดเผยวันเกิดหรือชื่อของลูก " +
                                        "ปิดได้ทุกเมื่อ และการปิดไม่ได้ซ่อนโปรไฟล์จากคนที่เข้ามาเอง"
                                } else {
                                    "Suggests you from your child's age band (e.g. 7-9 months). Never the " +
                                        "birthdate or the child's name. Turning it off does not hide your " +
                                        "profile from someone who reaches it another way."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = discoverable,
                            onCheckedChange = { wanted ->
                                discoverable = wanted
                                friendScope.launch {
                                    com.lullabyai.app.data.repository.AuthRepository()
                                        .setDiscoverable(wanted)
                                        .onFailure {
                                            // Put the switch back rather than leaving it showing a
                                            // setting the server never accepted.
                                            discoverable = !wanted
                                            friendMessage = friendError(it, s.isThai)
                                        }
                                }
                            }
                        )
                    }
                }
            } else if (targetUserId != null) {
                FriendActionBar(
                    state = friendState,
                    isThai = s.isThai,
                    onAdd = { askingFriend = true },
                    onCancelOrUnfriend = {
                        val id = friendshipId
                        if (id != null) {
                            friendScope.launch {
                                friendsRepo.remove(id)
                                    .onFailure { friendMessage = friendError(it, s.isThai) }
                                refreshFriendState()
                            }
                        }
                    },
                    onBlock = {
                        friendScope.launch {
                            val result = friendsRepo.block(targetUserId)
                            friendMessage = if (result.isSuccess) {
                                if (s.isThai) "บล็อกแล้ว จะไม่ได้รับคำขอจากบัญชีนี้อีก และอีกฝ่ายจะไม่ได้รับแจ้ง"
                                else "Blocked. No further requests, and they are not told."
                            } else {
                                friendError(result.exceptionOrNull(), s.isThai)
                            }
                            refreshFriendState()
                        }
                    },
                    onUnblock = {
                        friendScope.launch {
                            friendsRepo.unblock(targetUserId)
                                .onFailure { friendMessage = friendError(it, s.isThai) }
                            refreshFriendState()
                        }
                    }
                )
            }

            friendMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (askingFriend && targetUserId != null) {
                AlertDialog(
                    onDismissRequest = { askingFriend = false },
                    title = {
                        Text(
                            if (s.isThai) "ส่งคำขอเป็นเพื่อน" else "Send a friend request"
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (s.isThai) {
                                    "บอกสั้นๆ ว่าทำไมอยากรู้จัก จะช่วยให้อีกฝ่ายตัดสินใจง่ายขึ้น (ไม่บังคับ)"
                                } else {
                                    "A short note about why you are getting in touch helps them decide. Optional."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            OutlinedTextField(
                                value = friendNote,
                                onValueChange = { if (it.length <= 200) friendNote = it },
                                placeholder = {
                                    Text(
                                        if (s.isThai) "เช่น เห็นโพสต์สูตร BLW แล้วอยากปรึกษาค่ะ"
                                        else "e.g. I saw your BLW recipe and wanted to ask about it",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                supportingText = { Text("${friendNote.length}/200") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val note = friendNote.trim().takeIf { it.isNotEmpty() }
                            askingFriend = false
                            friendNote = ""
                            friendScope.launch {
                                val result = friendsRepo.request(targetUserId, note)
                                friendMessage = if (result.isSuccess) {
                                    if (s.isThai) "ส่งคำขอแล้ว รอการตอบรับ" else "Request sent"
                                } else {
                                    friendError(result.exceptionOrNull(), s.isThai)
                                }
                                refreshFriendState()
                            }
                        }) {
                            Text(if (s.isThai) "ส่งคำขอ" else "Send")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { askingFriend = false }) {
                            Text(if (s.isThai) "ยกเลิก" else "Cancel")
                        }
                    }
                )
            }

            // เพื่อนร่วมกัน — mockup 59. Only people I am already friends with, so nothing about a
            // stranger's friend list is revealed by showing it.
            if (!isCurrentUser && mutualFriends.isNotEmpty()) {
                PeopleStrip(
                    title = if (s.isThai) "เพื่อนร่วมกัน ${mutualFriends.size} คน"
                    else "${mutualFriends.size} mutual friends",
                    people = mutualFriends,
                    isThai = s.isThai,
                    onOpen = { navController.navigate(Screen.CommunityUserProfile.route(it.displayName)) }
                )
            }

            // เพื่อนที่คุณอาจรู้จัก — mockup 57, on my own profile only.
            if (isCurrentUser && suggestions.isNotEmpty()) {
                PeopleStrip(
                    title = if (s.isThai) "เพื่อนที่คุณอาจรู้จัก" else "People you may know",
                    subtitle = if (s.isThai) {
                        "แนะนำจากช่วงวัยลูกที่ใกล้เคียงกัน ปิดการแนะนำได้ในการตั้งค่าโปรไฟล์"
                    } else {
                        "Suggested from similar child age. You can turn suggestions off in profile settings."
                    },
                    people = suggestions,
                    isThai = s.isThai,
                    onOpen = { navController.navigate(Screen.CommunityUserProfile.route(it.displayName)) }
                )
            }

            // Profile feed tabs — mockup 59: โพสต์ / กระทู้ถาม-ตอบ / สื่อ & วิดีโอ.
            val tabbedPosts = remember(userPosts, profileTab) {
                when (profileTab) {
                    1 -> userPosts.filter { it.kind == PostKind.THREAD }
                    2 -> userPosts.filter { it.mediaUrls.isNotEmpty() || !it.imageUrl.isNullOrBlank() }
                    else -> userPosts
                }
            }

            ProfileTabs(
                selected = profileTab,
                onSelect = { profileTab = it },
                isThai = s.isThai,
                postCount = userPosts.size,
                threadCount = userPosts.count { it.kind == PostKind.THREAD },
                mediaCount = userPosts.count { it.mediaUrls.isNotEmpty() || !it.imageUrl.isNullOrBlank() }
            )

            // User's Community Posts Feed
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.DynamicFeed, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (s.isThai) "กระทู้ที่เผยแพร่ (${tabbedPosts.size})" else "Published Posts (${tabbedPosts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (tabbedPosts.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
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
                    tabbedPosts.forEach { post ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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

/**
 * The friend button on someone else's profile.
 *
 * Each state offers exactly one primary action, and block is always reachable - a parent who wants
 * someone to stop should never have to find it in a menu somewhere else.
 *
 * ส่งข้อความ is deliberately absent: direct messages are round 4, and a button that opens nothing
 * is worse than no button.
 */
@Composable
private fun FriendActionBar(
    state: FriendState,
    isThai: Boolean,
    onAdd: () -> Unit,
    onCancelOrUnfriend: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            FriendState.NONE -> Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PersonAddAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isThai) "เพิ่มเพื่อน" else "Add friend", fontWeight = FontWeight.Bold)
            }

            FriendState.REQUEST_SENT -> OutlinedButton(
                onClick = onCancelOrUnfriend,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isThai) "ยกเลิกคำขอ" else "Cancel request")
            }

            FriendState.REQUEST_RECEIVED -> OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            ) {
                // Answering happens in the requests list, which is where accept and decline sit
                // side by side. Sending them there beats a half-answer here.
                Text(if (isThai) "ตอบคำขอในหน้าคำขอเพื่อน" else "Answer in friend requests")
            }

            FriendState.FRIENDS -> OutlinedButton(
                onClick = onCancelOrUnfriend,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isThai) "เพื่อนกันแล้ว" else "Friends")
            }

            FriendState.BLOCKED -> OutlinedButton(
                onClick = onUnblock,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isThai) "เลิกบล็อก" else "Unblock")
            }
        }

        if (state != FriendState.BLOCKED) {
            IconButton(onClick = onBlock) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = if (isThai) "บล็อก" else "Block",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Badge, bio and city - the identity block from mockups 54 and 58.
 *
 * The badge is earned from real posts, likes and answers (see ContributorLevel), and it always
 * carries the caveat. A badge on a parenting profile is read as authority by other parents at 2am;
 * without the caveat it looks like the app vouching for the advice, which it cannot do.
 */
@Composable
private fun ProfileIdentityCard(
    isThai: Boolean,
    level: Int,
    points: Int,
    bio: String?,
    city: String?
) {
    val badge = ContributorLevel.label(level, isThai)
    if (badge == null && bio.isNullOrBlank() && city.isNullOrBlank()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (badge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(50), color = PrimaryContainer) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Filled.WorkspacePremium, null,
                                tint = Primary, modifier = Modifier.size(14.dp)
                            )
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (ContributorLevel.isTopContributor(level)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "Top Contributor",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Never shown without this line.
                Text(
                    ContributorLevel.caveat(isThai),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                ContributorLevel.pointsToNextLevel(points)?.let { remaining ->
                    Text(
                        if (isThai) "อีก $remaining คะแนนถึงระดับถัดไป"
                        else "$remaining points to the next level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            city?.takeIf { it.isNotBlank() }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Filled.Place, null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A horizontal row of parents: เพื่อนร่วมกัน on someone else's profile, เพื่อนที่คุณอาจรู้จัก on mine.
 *
 * [subtitle] is where the suggestion list says how it was built and how to switch it off. A list
 * of strangers matched on a child's age needs to explain itself where it appears, not in settings.
 */
@Composable
private fun PeopleStrip(
    title: String,
    people: List<com.lullabyai.app.data.repository.FriendSummary>,
    isThai: Boolean,
    onOpen: (com.lullabyai.app.data.repository.FriendSummary) -> Unit,
    subtitle: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(people, key = { it.userId }) { person ->
                Surface(
                    onClick = { onOpen(person) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.width(120.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!person.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = person.avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(SecondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(26.dp))
                            }
                        }
                        Text(
                            person.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        person.city?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** โพสต์ / กระทู้ถาม-ตอบ / สื่อ & วิดีโอ, with the real count beside each, as in mockup 59. */
@Composable
private fun ProfileTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    isThai: Boolean,
    postCount: Int,
    threadCount: Int,
    mediaCount: Int
) {
    val tabs = listOf(
        (if (isThai) "โพสต์" else "Posts") to postCount,
        (if (isThai) "กระทู้ถาม-ตอบ" else "Q&A") to threadCount,
        (if (isThai) "สื่อ & วิดีโอ" else "Media") to mediaCount
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val isSelected = selected == index
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "$label ($count)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Primary else MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
