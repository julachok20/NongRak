package com.lullabyai.app.ui.screens.community

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityComment
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.ReportReason
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.SecondaryContainer
import kotlinx.coroutines.launch

/** Where a shared link points: nongrak-ai.vercel.app/community/<id>. */
const val COMMUNITY_SHARE_BASE = "https://nongrak-ai.vercel.app/community"

/**
 * One post with its comments.
 *
 * Opens cold. A link shared weeks ago points at a post long gone from the hundred the feed holds,
 * so this screen fetches by id rather than looking in the loaded feed. When the feed IS loaded it
 * prefers that copy, so a vote or like made here and there stay in step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPostDetailScreen(
    postId: String,
    navController: NavController,
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Empty on the first frame, which is correct for a cold open from a shared link: the fetch
    // below is what fills the screen, and the feed copy takes over if and when it arrives.
    val feed by communityRepo.posts.collectAsState(initial = emptyList())
    var fetched by remember(postId) { mutableStateOf<CommunityPost?>(null) }
    var loading by remember(postId) { mutableStateOf(true) }

    val post = feed.firstOrNull { it.id == postId } ?: fetched

    LaunchedEffect(postId) {
        loading = true
        fetched = communityRepo.fetchPostById(postId).getOrNull()
        loading = false
    }

    var commentText by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHost.showSnackbar(it)
            snackbar = null
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "โพสต์" else "Post",
                onBack = { navController.popBackStack() },
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            val current = post
            if (current != null && current.commentsEnabled) {
                CommentComposer(
                    value = commentText,
                    onValueChange = { commentText = it },
                    isThai = s.isThai,
                    onSend = {
                        val text = commentText.trim()
                        if (text.isNotEmpty()) {
                            communityRepo.addComment(
                                postId = current.id,
                                authorName = "คุณแม่",
                                authorRole = "คุณแม่",
                                content = text
                            )
                            commentText = ""
                        }
                    }
                )
            }
        }
    ) { padding ->
        val current = post
        when {
            current == null && loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            current == null -> PostGone(Modifier.padding(padding), s.isThai)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CommunityFeedCard(
                        post = current,
                        isThai = s.isThai,
                        onOpen = {},
                        onLike = { communityRepo.toggleLike(current.id) },
                        onReact = { reaction -> communityRepo.setReaction(current.id, reaction) },
                        onAuthorClick = {
                            navController.navigate(Screen.CommunityUserProfile.route(current.authorName))
                        },
                        onShare = { sharePost(context, current) },
                        onVote = { optionId ->
                            scope.launch {
                                communityRepo.togglePollVote(current.id, optionId)
                                    .onFailure { snackbar = pollError(it, s.isThai) }
                            }
                        },
                        onAddPollOption = { label ->
                            scope.launch {
                                communityRepo.addPollOption(current.id, label)
                                    .onFailure { snackbar = pollError(it, s.isThai) }
                            }
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (s.isThai) "${current.comments.size} ความคิดเห็น"
                            else "${current.comments.size} comments",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { reportTarget = "post" to current.id }) {
                            Icon(Icons.Filled.Flag, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (s.isThai) "รายงาน" else "Report",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                items(current.comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        isThai = s.isThai,
                        onReport = { reportTarget = "comment" to comment.id }
                    )
                }

                if (!current.commentsEnabled) {
                    item {
                        Text(
                            if (s.isThai) "ผู้เขียนปิดการแสดงความคิดเห็นในโพสต์นี้"
                            else "The author turned off comments on this post",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    reportTarget?.let { (type, id) ->
        ReportDialog(
            isThai = s.isThai,
            onDismiss = { reportTarget = null },
            onConfirm = { reason ->
                scope.launch {
                    val result = communityRepo.report(type, id, reason)
                    snackbar = if (result.isSuccess) {
                        if (s.isThai) "ส่งรายงานแล้ว ทีมงานจะตรวจสอบ" else "Reported. We will review it."
                    } else {
                        // The database allows one report per person per thing, so the usual failure
                        // here is a duplicate - not something to alarm anyone about.
                        if (s.isThai) "คุณรายงานเนื้อหานี้ไปแล้ว" else "You already reported this."
                    }
                }
                reportTarget = null
            }
        )
    }
}

@Composable
private fun PostGone(modifier: Modifier, isThai: Boolean) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                if (isThai) "ไม่พบโพสต์นี้" else "This post is not available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                // Names the possibilities rather than guessing which one it was.
                if (isThai) {
                    "โพสต์อาจถูกลบโดยผู้เขียน ถูกนำออกโดยทีมงาน หรือลิงก์ไม่ถูกต้อง"
                } else {
                    "It may have been deleted by its author, removed by the team, or the link is wrong."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CommentRow(comment: CommunityComment, isThai: Boolean, onReport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            comment.isAi -> Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SmartToy, null, tint = Primary, modifier = Modifier.size(18.dp))
            }

            !comment.authorAvatarUrl.isNullOrBlank() -> AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(CircleShape)
            )

            else -> Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (comment.isAi) "NongRak AI" else comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    comment.timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)

            // Every AI reply carries this, always, directly under the words it wrote.
            if (comment.isAi) AiReplyNotice(isThai)
        }

        if (!comment.isAi) {
            IconButton(onClick = onReport, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Flag, null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isThai: Boolean,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= 1000) onValueChange(it) },
                placeholder = {
                    Text(
                        if (isThai) "เขียนความคิดเห็น..." else "Write a comment...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = if (isThai) "ส่ง" else "Send")
            }
        }
    }
}

@Composable
private fun ReportDialog(
    isThai: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ReportReason) -> Unit
) {
    var picked by remember { mutableStateOf(ReportReason.SPAM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isThai) "รายงานเนื้อหา" else "Report content") },
        text = {
            Column {
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = picked == reason, onClick = { picked = reason })
                        Text(
                            if (isThai) reason.labelTh else reason.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) {
                Text(if (isThai) "ส่งรายงาน" else "Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") }
        }
    )
}

/** Android's own share sheet, carrying the web link so it opens for people without the app. */
private fun sharePost(context: android.content.Context, post: CommunityPost) {
    val link = "$COMMUNITY_SHARE_BASE/${post.id}"
    val subject = post.title?.takeIf { it.isNotBlank() } ?: post.content.take(80)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "$subject\n$link")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/**
 * Turns the database's named errors into something a parent can act on.
 *
 * The triggers raise specific names instead of failing silently, so this is a lookup rather than a
 * guess. Anything unrecognised is shown as itself - a wrong friendly message is worse than an
 * ugly true one.
 */
private fun pollError(error: Throwable, isThai: Boolean): String {
    val message = error.message.orEmpty()
    return when {
        "poll_closed" in message ->
            if (isThai) "โพลนี้ปิดโหวตแล้ว" else "This poll has closed"

        "poll_single_choice_already_voted" in message ->
            if (isThai) "โพลนี้เลือกได้คำตอบเดียว" else "This poll allows one answer"

        "poll_user_options_not_allowed" in message ->
            if (isThai) "โพลนี้ไม่เปิดให้เพิ่มตัวเลือก" else "This poll does not accept new options"

        "poll_option_limit_reached" in message ->
            if (isThai) "โพลนี้มีตัวเลือกครบ 6 ข้อแล้ว" else "This poll already has 6 options"

        "community_rate_limit_votes" in message ->
            if (isThai) "คุณโหวตถี่เกินไป ลองใหม่อีกสักครู่" else "Too many votes just now, try again shortly"

        "poll_vote_requires_sign_in" in message ->
            if (isThai) "เข้าสู่ระบบก่อนโหวต" else "Sign in to vote"

        "moderation" in message ->
            if (isThai) "ข้อความนี้ไม่ผ่านการตรวจสอบเนื้อหา" else "That text did not pass the content check"

        else -> if (isThai) "ไม่สำเร็จ: $message" else "Failed: $message"
    }
}
