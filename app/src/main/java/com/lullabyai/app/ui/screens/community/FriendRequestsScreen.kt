package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.repository.FriendSummary
import com.lullabyai.app.data.repository.FriendsRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.SecondaryContainer
import kotlinx.coroutines.launch

/**
 * คำขอเป็นเพื่อนใหม่ - mockup 55.
 *
 * Answering belongs to the addressee alone; the database refuses anything else, so an action
 * failing here means the wrong account is signed in, not that the tap was lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    navController: NavController,
    friendsRepo: FriendsRepository = remember { FriendsRepository() }
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<FriendSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    suspend fun reload() {
        requests = friendsRepo.incomingRequests().getOrDefault(emptyList())
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHost.showSnackbar(it)
            snackbar = null
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "คำขอเป็นเพื่อนใหม่" else "Friend requests",
                onBack = { navController.popBackStack() },
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            requests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PersonAddAlt, null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        if (s.isThai) "ยังไม่มีคำขอเป็นเพื่อน" else "No friend requests",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        if (s.isThai) "${requests.size} คำขอ" else "${requests.size} requests",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                items(requests, key = { it.friendshipId ?: it.userId }) { request ->
                    RequestCard(
                        request = request,
                        isThai = s.isThai,
                        onOpenProfile = {
                            navController.navigate(Screen.CommunityUserProfile.route(request.displayName))
                        },
                        onAccept = {
                            val id = request.friendshipId
                            if (id != null) {
                                scope.launch {
                                    val result = friendsRepo.respond(id, accept = true)
                                    snackbar = if (result.isSuccess) {
                                        if (s.isThai) "เป็นเพื่อนกับ ${request.displayName} แล้ว"
                                        else "You are now friends with ${request.displayName}"
                                    } else {
                                        friendError(result.exceptionOrNull(), s.isThai)
                                    }
                                    reload()
                                }
                            }
                        },
                        onDecline = {
                            val id = request.friendshipId
                            if (id != null) {
                                scope.launch {
                                    friendsRepo.respond(id, accept = false)
                                        .onFailure { snackbar = friendError(it, s.isThai) }
                                    reload()
                                }
                            }
                        },
                        onBlock = {
                            scope.launch {
                                val result = friendsRepo.block(request.userId)
                                snackbar = if (result.isSuccess) {
                                    // Said plainly, including the part people get wrong: they are
                                    // not told, and they cannot ask again.
                                    if (s.isThai) "บล็อกแล้ว ${request.displayName} จะส่งคำขออีกไม่ได้ และจะไม่ได้รับแจ้ง"
                                    else "Blocked. They cannot request again and are not told."
                                } else {
                                    friendError(result.exceptionOrNull(), s.isThai)
                                }
                                reload()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendSummary,
    isThai: Boolean,
    onOpenProfile: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenProfile() },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!request.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = request.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(SecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PersonOutline, null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        request.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val sub = listOfNotNull(
                        request.city?.takeIf { it.isNotBlank() },
                        request.bio?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2
                        )
                    }
                }

                IconButton(onClick = onBlock) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = if (isThai) "บล็อก" else "Block",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // The note that came with the request, as in mockup 55. Quoted, so it reads as their
            // words rather than as the app's.
            request.message?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "“$note”",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                    Text(if (isThai) "ปฏิเสธ" else "Decline")
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isThai) "ยอมรับเพื่อน" else "Accept", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Names the database's refusals so a parent knows what happened.
 *
 * Anything unrecognised is shown as itself. A wrong friendly message is worse than an ugly true
 * one, and this project has paid for that lesson before.
 */
internal fun friendError(error: Throwable?, isThai: Boolean): String {
    val message = error?.message.orEmpty()
    return when {
        "friend_request_blocked" in message ->
            if (isThai) "ส่งคำขอไม่ได้" else "Cannot send this request"

        "community_rate_limit_friend_requests" in message ->
            if (isThai) "ส่งคำขอถี่เกินไป ลองใหม่อีกสักครู่" else "Too many requests just now, try again shortly"

        "friend_request_moderation_blocked" in message ->
            if (isThai) "ข้อความแนบไม่ผ่านการตรวจเนื้อหา" else "That note did not pass the content check"

        "duplicate key" in message || "community_friendships_pair_idx" in message ->
            if (isThai) "มีคำขอระหว่างคุณสองคนอยู่แล้ว" else "There is already a request between you"

        "requires_sign_in" in message ->
            if (isThai) "เข้าสู่ระบบก่อน" else "Sign in first"

        message.isBlank() -> if (isThai) "ไม่สำเร็จ" else "Failed"
        else -> if (isThai) "ไม่สำเร็จ: $message" else "Failed: $message"
    }
}
