@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.CommunityPoll
import com.lullabyai.app.data.model.CommunityPollOption
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.PostKind
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.foundation.combinedClickable
import com.lullabyai.app.data.model.Reaction
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import com.lullabyai.app.ui.theme.SecondaryContainer

/**
 * One card for all three post kinds. The feed and the post-detail screen draw the same thing, so
 * it lives here rather than privately inside either of them.
 *
 * Three rules are enforced by this file and must not be moved into a caller:
 *
 *   * An anonymous thread never shows an author to tap. The server has already stripped user_id,
 *     so there is nothing to navigate to even if a caller asked.
 *   * An urgent thread always carries the 1669 banner. The flag sorts it up the doctor queue; it
 *     does not make this an emergency service, and the parent has to be told so in the same view.
 *   * An AI reply always carries "AI ไม่ใช่แพทย์". A parent must be able to tell an AI answer from
 *     a doctor's at a glance.
 */

private val UrgentRed = Color(0xFFD32F2F)
private val UrgentTint = Color(0xFFFDECEA)

@Composable
fun CommunityFeedCard(
    post: CommunityPost,
    isThai: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onAuthorClick: () -> Unit,
    onShare: () -> Unit,
    onReact: (com.lullabyai.app.data.model.Reaction?) -> Unit = {},
    onVote: (optionId: String) -> Unit = {},
    onAddPollOption: (label: String) -> Unit = {},
    onTagClick: (String) -> Unit = {}
) {
    // Flat and bordered, matching the ข้อมูลลูก cards. A shadow under every post is what made the
    // feed feel heavy; the border is what makes those pages read as clean.
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AuthorRow(post, isThai, onAuthorClick)

            if (post.isUrgent) UrgentNotice(isThai)

            post.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (post.content.isNotBlank()) {
                Text(
                    post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            PostMedia(post, isThai)

            post.poll?.let { poll ->
                PollBlock(
                    poll = poll,
                    isThai = isThai,
                    onVote = onVote,
                    onAddOption = onAddPollOption
                )
            }

            if (post.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.take(4).forEach { tag ->
                        AssistChip(
                            onClick = { onTagClick(tag) },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ActionRow(post, isThai, onLike, onReact, onOpen, onShare)
        }
    }
}

@Composable
private fun AuthorRow(post: CommunityPost, isThai: Boolean, onAuthorClick: () -> Unit) {
    // An anonymous author is not a link. There is nobody behind it to open.
    val clickable = if (post.isAnonymous) Modifier else Modifier.clickable { onAuthorClick() }

    Row(
        modifier = Modifier.fillMaxWidth().then(clickable),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            post.isAnonymous -> Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VisibilityOff, null, tint = Primary, modifier = Modifier.size(20.dp))
            }

            !post.authorAvatarUrl.isNullOrBlank() -> AsyncImage(
                model = post.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )

            else -> Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(22.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                post.authorName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            val subtitle = if (post.isAnonymous) {
                post.timeAgo
            } else {
                "${post.authorRole} • ${post.timeAgo}"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        KindBadge(post.kind, isThai)
    }
}

@Composable
private fun KindBadge(kind: PostKind, isThai: Boolean) {
    if (kind == PostKind.POST) return
    val label: String
    val icon = when (kind) {
        PostKind.THREAD -> {
            label = if (isThai) "กระทู้ถาม-ตอบ" else "Q&A"
            Icons.Filled.LocalHospital
        }
        PostKind.POLL -> {
            label = if (isThai) "โพลสำรวจ" else "Poll"
            Icons.Filled.HowToVote
        }
        PostKind.POST -> return
    }
    Surface(shape = RoundedCornerShape(50), color = PrimaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(13.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Primary)
        }
    }
}

/**
 * Shown on every urgent thread, without exception.
 *
 * NongRak is not an emergency service and must never be the thing a parent waits on while a child
 * is deteriorating. 1669 is Thailand's emergency medical number.
 */
@Composable
fun UrgentNotice(isThai: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = UrgentTint) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Warning, null, tint = UrgentRed, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isThai) "นี่ไม่ใช่บริการฉุกเฉิน" else "This is not an emergency service",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = UrgentRed
                )
                Text(
                    if (isThai) {
                        "หากลูกมีอาการรุนแรง อย่ารอคำตอบในชุมชน โทร 1669 หรือไปโรงพยาบาลทันที"
                    } else {
                        "If your child is seriously unwell, do not wait for a reply here. " +
                            "Call 1669 or go to hospital now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = UrgentRed
                )
            }
        }
    }
}

/**
 * The notice that sits on every AI-written reply.
 *
 * NongRak AI writes a first pass under a symptom thread when the author asks for one. It is not a
 * doctor, it has not examined the child, and the parent has to be able to see that without
 * reading the reply first.
 */
@Composable
fun AiReplyNotice(isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (isThai) {
                    "AI ไม่ใช่แพทย์ ข้อความนี้เขียนโดย NongRak AI ไม่ใช่การวินิจฉัย " +
                        "และไม่แทนการตรวจโดยแพทย์ หากอาการแย่ลงหรือไม่แน่ใจ พาลูกไปพบแพทย์"
                } else {
                    "AI is not a doctor. Written by NongRak AI, not a diagnosis and no substitute " +
                        "for examination. If symptoms worsen or you are unsure, see a doctor."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PostMedia(post: CommunityPost, isThai: Boolean) {
    // media_urls is the grid the composer fills; image_url stays the cover so posts written before
    // the grid existed still show something.
    val media = post.mediaUrls.ifEmpty { listOfNotNull(post.imageUrl) }
    if (media.isEmpty()) return

    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = media.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp))
        )
        if (!post.videoUrl.isNullOrBlank()) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                Icon(
                    Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.padding(8.dp).size(26.dp)
                )
            }
        }
    }
    if (media.size > 1) {
        Text(
            if (isThai) "+ อีก ${media.size - 1} รูป" else "+${media.size - 1} more",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * The poll itself: options, the tally when it may be shown, and the closing line.
 *
 * [CommunityPoll.resultsVisible] decides whether numbers are drawn. It is a presentation rule, not
 * a secrecy guarantee - the vote rows are readable by anyone who can query - so nothing here says
 * the result is hidden or secret, only that it is shown after voting or after the poll closes.
 */
@Composable
fun PollBlock(
    poll: CommunityPoll,
    isThai: Boolean,
    onVote: (String) -> Unit,
    onAddOption: (String) -> Unit
) {
    val showResults = poll.resultsVisible()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        poll.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        poll.options.forEach { option ->
            PollOptionRow(
                option = option,
                poll = poll,
                showResults = showResults,
                onVote = onVote
            )
        }

        if (poll.allowUserOptions && !poll.isClosed && poll.options.size < 6) {
            AddPollOptionRow(isThai, onAddOption)
        }

        Text(
            pollFooter(poll, isThai),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PollOptionRow(
    option: CommunityPollOption,
    poll: CommunityPoll,
    showResults: Boolean,
    onVote: (String) -> Unit
) {
    val picked = option.id in poll.myVotes
    val percent = if (showResults) poll.sharePercent(option) else 0

    Surface(
        onClick = { if (!poll.isClosed) onVote(option.id) },
        enabled = !poll.isClosed,
        shape = RoundedCornerShape(12.dp),
        color = if (picked) PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (picked) BorderStroke(1.dp, Primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            // The filled bar is the tally. Drawn behind the label so the text stays readable.
            if (showResults && percent > 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .fillMaxWidth(percent / 100f)
                        .background(Primary.copy(alpha = 0.16f))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (picked) {
                    Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (showResults) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        "(${option.voteCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPollOptionRow(isThai: Boolean, onAddOption: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    if (!adding) {
        TextButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isThai) "เพิ่มตัวเลือกของคุณ" else "Add your own option",
                style = MaterialTheme.typography.labelMedium
            )
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 120) text = it },
            singleLine = true,
            placeholder = {
                Text(
                    if (isThai) "ตัวเลือกใหม่" else "New option",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                val label = text.trim()
                if (label.isNotEmpty()) {
                    onAddOption(label)
                    text = ""
                    adding = false
                }
            },
            enabled = text.isNotBlank()
        ) {
            Text(if (isThai) "เพิ่ม" else "Add")
        }
    }
}

/** "เหลือเวลาอีก 2 วัน" / "ปิดโหวตแล้ว", with the vote count, as in the mockup. */
private fun pollFooter(poll: CommunityPoll, isThai: Boolean): String {
    val votes = poll.totalVotes
    val votesText = if (isThai) "รวม $votes โหวต" else "$votes votes"

    if (poll.isClosed) {
        return if (isThai) "$votesText • ปิดโหวตแล้ว" else "$votesText • closed"
    }

    val remainingMs = poll.closesAtMillis - System.currentTimeMillis()
    val hours = remainingMs / (60 * 60 * 1000)
    val days = hours / 24

    val left = when {
        days >= 1 -> if (isThai) "เหลือเวลาอีก $days วัน" else "$days days left"
        hours >= 1 -> if (isThai) "เหลือเวลาอีก $hours ชม." else "$hours hours left"
        else -> if (isThai) "ใกล้ปิดโหวต" else "closing soon"
    }

    // Said plainly, because it is what the poll actually does - the tally is not secret, it is
    // simply not drawn yet.
    val gate = if (!poll.showResultsAfterVote && !poll.resultsVisible()) {
        if (isThai) " • แสดงผลเมื่อปิดโหวต" else " • results shown when the poll closes"
    } else {
        ""
    }

    return "$votesText • $left$gate"
}

@Composable
private fun ActionRow(
    post: CommunityPost,
    isThai: Boolean,
    onLike: () -> Unit,
    onReact: (com.lullabyai.app.data.model.Reaction?) -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReactionButton(
            post = post,
            isThai = isThai,
            onQuickReact = onLike,
            onPick = onReact
        )

        if (post.commentsEnabled) {
            TextButton(onClick = onComment) {
                Icon(
                    Icons.Filled.ChatBubbleOutline, null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isThai) "${post.commentsCount} ความเห็น" else "${post.commentsCount}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            Text(
                if (isThai) "ปิดคอมเมนต์" else "Comments off",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onShare) {
            Icon(
                Icons.Filled.Share, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isThai) "แชร์" else "Share", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The like button, Facebook-style: tap to react, hold to choose the face.
 *
 * Tap adds a plain ถูกใจ, or clears whatever was there. Press and hold opens the four faces.
 * A long press is invisible, so once a reaction exists a tap opens the picker too - a parent who
 * never discovers the gesture can still change their mind.
 */
@Composable
private fun ReactionButton(
    post: CommunityPost,
    isThai: Boolean,
    onQuickReact: () -> Unit,
    onPick: (Reaction?) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    val mine = post.myReaction

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .combinedClickable(
                    onClick = { if (mine == null) onQuickReact() else picking = true },
                    onLongClick = { picking = true }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (mine != null) {
                Text(mine.emoji, style = MaterialTheme.typography.labelLarge)
                Text(
                    mine.label(isThai),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CommunityPalette.of(mine)
                )
            } else {
                Icon(
                    Icons.Filled.ThumbUp, null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    if (isThai) "ถูกใจ" else "Like",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // The tally: the faces people actually used, then the total.
            if (post.likesCount > 0) {
                val faces = post.reactionCounts.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(3)
                if (faces.isNotEmpty()) {
                    Text(
                        faces.joinToString("") { it.key.emoji },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    "${post.likesCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(
            expanded = picking,
            onDismissRequest = { picking = false }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Reaction.entries.forEach { reaction ->
                    val chosen = mine == reaction
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (chosen) CommunityPalette.soft(CommunityPalette.of(reaction))
                                else Color.Transparent
                            )
                            .clickable {
                                // Tapping the face you already have removes it, the way tapping a
                                // lit like button does.
                                onPick(if (chosen) null else reaction)
                                picking = false
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(reaction.emoji, style = MaterialTheme.typography.titleLarge)
                        Text(
                            reaction.label(isThai),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chosen) CommunityPalette.of(reaction)
                            else MaterialTheme.colorScheme.outline,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
