package com.lullabyai.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.repository.AiSafetyResult
import com.lullabyai.app.data.repository.CommunityRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import com.lullabyai.app.ui.theme.SecondaryContainer
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityPostScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    childViewModel: ChildViewModel = viewModel(),
    communityRepo: CommunityRepository = CommunityRepository.instance
) {
    val s = LocalStrings.current
    val authState by authViewModel.uiState.collectAsState()
    val childState by childViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
    }

    val childrenNames = remember(childState.children, childState.child) {
        if (childState.children.isNotEmpty()) {
            childState.children.mapNotNull { it.name.trim().takeIf { n -> n.isNotEmpty() } }
        } else {
            listOfNotNull(childState.child?.name?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    val authorDisplayName = remember(authState.profile, childState.child, s.isThai) {
        authState.profile?.username?.trim()?.takeIf { it.isNotEmpty() }
            ?: authState.profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: (if (s.isThai) "คุณแม่น้องรัก" else "NongRak Parent")
    }

    val authorRoleName = remember(authState.profile, childrenNames, s.isThai) {
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

    val authorAvatarUrl = authState.avatarSignedUrl ?: authState.profile?.avatarUrl

    var postContent by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    var customTagInput by remember { mutableStateOf("") }

    val recommendedTags = remember { communityRepo.getRecommendedHashtags() }
    val popularTags = remember { communityRepo.getPopularHashtags().map { it.tag } }

    fun addCustomTag() {
        val clean = customTagInput.trim().replace(" ", "")
        if (clean.isNotBlank()) {
            val formattedTag = if (clean.startsWith("#")) clean else "#$clean"
            if (!selectedTags.contains(formattedTag)) {
                selectedTags.add(formattedTag)
            }
            customTagInput = ""
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var attachedMedia by remember { mutableStateOf<String?>(null) }
    var attachedMediaUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var attachedIsVideo by remember { mutableStateOf(false) }
    var isCheckingMedia by remember { mutableStateOf(false) }
    var mediaError by remember { mutableStateOf<String?>(null) }

    // Picking no longer uploads. community-media is a public bucket, so a file put there is already
    // reachable by URL even if the post is never created — the upload waits until the check passes.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            attachedMediaUri = selectedUri
            attachedMedia = selectedUri.toString()
            attachedIsVideo = false
            mediaError = null
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            attachedMediaUri = selectedUri
            attachedMedia = selectedUri.toString()
            attachedIsVideo = true
            mediaError = null
        }
    }

    fun handlePost() {
        if (postContent.isBlank() || isCheckingMedia) return
        val currentUserId = runCatching { authState.profile?.id }.getOrNull()
        val uri = attachedMediaUri
        scope.launch {
            isCheckingMedia = true
            mediaError = null
            // A video goes up as frames: the edge function screens images, and a whole clip is far
            // too big to send. Every frame is screened, and one bad frame stops the post.
            // Decoding video frames / photos takes seconds: off the main thread, or the screen freezes
            // and Android may show "app not responding" (rememberCoroutineScope runs on Main).
            val frames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                when {
                    uri == null -> emptyList()
                    attachedIsVideo -> com.lullabyai.app.util.VideoFrames.extract(context, uri)
                    else -> listOfNotNull(com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri, 1024))
                }
            }
            if (uri != null && frames.isEmpty()) {
                mediaError = if (s.isThai) "อ่านไฟล์ที่แนบไม่ได้ ลองเลือกไฟล์อื่น"
                else "Couldn't read the attached file — try another one."
                isCheckingMedia = false
                return@launch
            }
            val verdict = com.lullabyai.app.data.remote.SparkChatClient.moderateMedia(postContent.trim(), frames)
            if (!verdict.allowed) {
                val why = verdict.reasons
                val th = s.isThai
                // Say which rule, so the parent can fix the post instead of guessing.
                mediaError = when {
                    "monarchy" in why -> if (th) "ชุมชนนี้ไม่อนุญาตเนื้อหาเกี่ยวกับสถาบันพระมหากษัตริย์" else "Content about the monarchy isn't allowed in this community."
                    "politics" in why -> if (th) "ชุมชนนี้ไม่อนุญาตเนื้อหาการเมือง" else "Political content isn't allowed in this community."
                    "false_health_advice" in why -> if (th) "คำแนะนำนี้อาจขัดกับแนวทางการดูแลเด็กที่ปลอดภัย ลองเขียนเป็นคำถาม หรือปรึกษาคุณหมอ"
                        else "This advice may go against safe child-care guidance. Try asking it as a question, or check with a doctor."
                    "fake_news" in why -> if (th) "ข่าวนี้ยังไม่มีแหล่งที่มาที่น่าเชื่อถือ แนบแหล่งอ้างอิงแล้วลองใหม่"
                        else "This claim has no credible source. Add a source and try again."
                    "personal_document" in why -> if (th) "รูปนี้มีข้อมูลส่วนตัว (บัตร/เอกสาร) จึงไม่เผยแพร่" else "This picture shows a personal document, so it wasn't published."
                    "check_unavailable" in why -> if (th) "ระบบตรวจความปลอดภัยไม่พร้อมตอนนี้ ลองใหม่อีกครั้งภายหลัง" else "The safety check is unavailable right now. Please try again later."
                    else -> if (th) "เนื้อหานี้ไม่ผ่านการตรวจความปลอดภัย จึงยังไม่ได้เผยแพร่" else "This didn't pass the safety check, so nothing was published."
                }
                isCheckingMedia = false
                return@launch
            }
            var imageUrl: String? = null
            var videoUrl: String? = null
            if (uri != null) {
                if (attachedIsVideo) {
                    val clip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.lullabyai.app.util.ImageUtils.readBytes(context, uri)
                    }
                    videoUrl = clip?.let { communityRepo.uploadPostMedia(it, "mp4").getOrNull() }
                    if (videoUrl == null) {
                        mediaError = if (s.isThai) "อัปโหลดวิดีโอไม่สำเร็จ (ไฟล์เกิน 10 MB?)" else "Video upload failed (over 10 MB?)."
                        isCheckingMedia = false
                        return@launch
                    }
                }
                // The first screened frame doubles as the card's picture / the clip's thumbnail.
                imageUrl = frames.firstOrNull()?.let { communityRepo.uploadPostMedia(it, "jpg").getOrNull() }
            }
            communityRepo.addPost(
                CommunityPost(
                    userId = currentUserId,
                    authorName = authorDisplayName,
                    authorRole = authorRoleName,
                    authorAvatarUrl = authorAvatarUrl,
                    timeAgo = if (s.isThai) "เมื่อสักครู่" else "Just now",
                    content = postContent.trim(),
                    tags = selectedTags.toList(),
                    imageUrl = imageUrl,
                    videoUrl = videoUrl
                )
            )
            isCheckingMedia = false
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (s.isThai) "สร้างโพสต์" else "Create Post",
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
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = { handlePost() },
                        enabled = postContent.isNotBlank() && !isCheckingMedia,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (s.isThai) "โพสต์กระทู้" else "Post",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!authorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = authorAvatarUrl,
                        contentDescription = "Avatar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = authorDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = authorRoleName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = PrimaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = Primary, modifier = Modifier.size(12.dp))
                            Text(
                                if (s.isThai) "สาธารณะ" else "Public",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Post content input
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = postContent,
                        onValueChange = { postContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        placeholder = {
                            Text(
                                if (s.isThai)
                                    "แชร์ประสบการณ์ คำถาม หรือเรื่องราวเกี่ยวกับลูกน้อยของคุณ..."
                                else
                                    "Share your parenting experience, questions, or moments...",
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    // Attached media preview
                    attachedMedia?.let { mediaName ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = attachedMediaUri ?: mediaName,
                                        contentDescription = "Attached photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Column {
                                        Text(
                                            if (attachedIsVideo) (if (s.isThai) "แนบวิดีโอแล้ว" else "Video attached")
                                            else (if (s.isThai) "แนบรูปภาพแล้ว" else "Photo attached"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isCheckingMedia) {
                                            Text(
                                                if (s.isThai) "กำลังตรวจความปลอดภัยก่อนเผยแพร่..." else "Safety check before publishing...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Primary
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        attachedMedia = null
                                        attachedMediaUri = null
                                        attachedIsVideo = false
                                        mediaError = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    mediaError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.labelMedium,
                            color = Error,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Media & Actions bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isThai) "รูปภาพ" else "Photo", color = Primary)
                            }
                            TextButton(onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }) {
                                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isThai) "วิดีโอ" else "Video", color = Primary)
                            }
                        }

                        IconButton(onClick = { if (!selectedTags.contains("#ถามคุณหมอ")) selectedTags.add("#ถามคุณหมอ") }) {
                            Icon(Icons.Filled.Tag, contentDescription = "Add tag", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Custom & Suggested Hashtags Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Tag, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (s.isThai) "กำหนดและเลือกแฮชแท็ก (#Hashtags)" else "Custom & Suggested Hashtags",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Input for custom hashtag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customTagInput,
                            onValueChange = { customTagInput = it },
                            placeholder = { Text(if (s.isThai) "พิมพ์ #แฮชแท็กของคุณเอง..." else "Add your own #hashtag...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Button(
                            onClick = { addCustomTag() },
                            enabled = customTagInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (s.isThai) "เพิ่ม" else "Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Active Selected Tags
                    if (selectedTags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (s.isThai) "แฮชแท็กที่เลือก (${selectedTags.size}):" else "Selected Tags (${selectedTags.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedTags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Primary,
                                        shadowElevation = 1.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(tag, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { selectedTags.remove(tag) }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = "Remove tag", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Popular Hashtags Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                            Text(if (s.isThai) "แฮชแท็กยอดนิยม (Popular)" else "Popular Hashtags", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            popularTags.forEach { tag ->
                                val isSel = selectedTags.contains(tag)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { if (isSel) selectedTags.remove(tag) else selectedTags.add(tag) },
                                    label = { Text(tag) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }

                    // Recommended Hashtags Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(if (s.isThai) "แฮชแท็กแนะนำ (Recommended)" else "Recommended Hashtags", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recommendedTags.forEach { tag ->
                                val isSel = selectedTags.contains(tag)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { if (isSel) selectedTags.remove(tag) else selectedTags.add(tag) },
                                    label = { Text(tag) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }

            // AI Safety Information Note
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (s.isThai) "ตรวจสอบความปลอดภัยด้วย AI" else "AI Safety Protection",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (s.isThai)
                                "ระบบจะช่วยตรวจจับข้อมูลที่เป็นอันตรายหรือคำแนะนำที่ไม่ปลอดภัยต่อเด็กก่อนโพสต์เสมอ"
                            else
                                "Every post is verified by AI to prevent harmful or dangerous medical advice for infants.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

