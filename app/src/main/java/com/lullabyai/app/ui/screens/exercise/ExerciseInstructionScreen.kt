@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.exercise

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
import coil.compose.AsyncImage
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

@Composable
fun ExerciseInstructionScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val e = ui.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "วิธีฝึกออกกำลังกาย" else "Exercise Guide", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Outline.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.FitnessCenter, null, tint = Outline, modifier = Modifier.size(36.dp))
                }
                Text(if (isThai) "เร็วๆ นี้" else "Coming Soon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(if (isThai) "คู่มือการออกกำลังกายกำลังพัฒนา\nติดตามอัปเดตเร็วๆ นี้" else "Exercise guide is coming soon.\nStay tuned for updates!", style = MaterialTheme.typography.bodyMedium, color = Outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ExerciseImage(title: String) {
    val url = remember(title) {
        "https://image.pollinations.ai/prompt/" +
            java.net.URLEncoder.encode("$title, a parent gently doing this activity with a baby, safe, bright friendly illustration", "UTF-8") +
            "?width=600&height=400&nologo=true"
    }
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))
    ) {
        AsyncImage(model = url, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(150.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
