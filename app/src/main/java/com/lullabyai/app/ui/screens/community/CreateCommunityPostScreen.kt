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
    var isUploadingMedia by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            attachedMediaUri = selectedUri
            attachedMedia = selectedUri.toString()
            scope.launch {
                isUploadingMedia = true
                val bytes = context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                if (bytes != null) {
                    val uploadRes = communityRepo.uploadPostImage(bytes, "jpg")
                    if (uploadRes.isSuccess) {
                        attachedMedia = uploadRes.getOrNull()
                    }
                }
                isUploadingMedia = false
            }
        }
    }

    fun handlePost() {
        if (postContent.isBlank()) return

        val currentUserId = runCatching { authState.profile?.id }.getOrNull()
        val newPost = CommunityPost(
            userId = currentUserId,
            authorName = authorDisplayName,
            authorRole = authorRoleName,
            authorAvatarUrl = authorAvatarUrl,
            timeAgo = if (s.isThai) "เมื่อสักครู่" else "Just now",
            content = postContent.trim(),
            tags = selectedTags.toList(),
            imageUrl = attachedMedia
        )
        communityRepo.addPost(newPost)
        navController.popBackStack()
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
                        enabled = postContent.isNotBlank(),
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
                                            if (s.isThai) "แนบรูปภาพแล้ว" else "Photo attached",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isUploadingMedia) {
                                            Text(
                                                if (s.isThai) "กำลังอัปโหลดรูปภาพ..." else "Uploading image...",
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
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
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
                            TextButton(onClick = { attachedMedia = "video_attached.mp4" }) {
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

