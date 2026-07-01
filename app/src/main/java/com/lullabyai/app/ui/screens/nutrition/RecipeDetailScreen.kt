@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

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
fun RecipeDetailScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val r = ui.recipe

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isThai) "วิธีทำอาหาร" else "Recipe", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Outline.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Restaurant, null, tint = Outline, modifier = Modifier.size(36.dp))
                }
                Text(if (isThai) "เร็วๆ นี้" else "Coming Soon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(if (isThai) "ฟีเจอร์สูตรอาหารกำลังพัฒนา\nติดตามอัปเดตเร็วๆ นี้" else "Recipe feature is coming soon.\nStay tuned for updates!", style = MaterialTheme.typography.bodyMedium, color = Outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Builds an on-the-fly generated food image URL from the dish name (free image generator, no key). */
internal fun foodImageUrl(title: String): String =
    "https://image.pollinations.ai/prompt/" +
        java.net.URLEncoder.encode("$title, healthy homemade food for a baby, appetizing, top view, soft natural light, food photography", "UTF-8") +
        "?width=600&height=400&nologo=true"

@Composable
private fun RecipeImage(title: String) {
    val url = remember(title) { foodImageUrl(title) }
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))
    ) {
        AsyncImage(model = url, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
internal fun StepRow(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
    }
}
