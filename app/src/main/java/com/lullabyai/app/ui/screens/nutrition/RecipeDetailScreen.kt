@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

@Composable
fun RecipeDetailScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val r = ui.recipe

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "วิธีทำอาหาร" else "Recipe",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            ui.recipeLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            r == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(if (isThai) "ไม่พบข้อมูลสูตรอาหาร" else "Recipe not found", color = Outline)
                }
            }
            else -> {
                val nutrientTags = nutrientTagSymbols(listOf(r.nutritionNote, r.title))
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RecipeImage(r.title)
                    Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(Icons.Filled.Schedule, r.timeMinutes)
                        InfoChip(Icons.Filled.ChildCare, r.ageRange)
                        InfoChip(Icons.Filled.Groups, r.servings)
                    }
                    if (nutrientTags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            nutrientTags.forEach { tag ->
                                Surface(shape = CircleShape, color = Primary.copy(0.12f)) {
                                    Text(tag, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                                }
                            }
                        }
                    }
                    if (r.nutritionNote.isNotBlank()) {
                        AIInsightCard(text = r.nutritionNote)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SectionTitle(if (isThai) "ส่วนผสม" else "Ingredients")
                        // Checkable prep list — ticking off is per-view-session only (not persisted),
                        // matching the mockup's "check as you go" behavior while cooking.
                        r.ingredients.forEach { item ->
                            var checked by remember(item) { mutableStateOf(false) }
                            Row(
                                Modifier.fillMaxWidth().clickable { checked = !checked },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { checked = it })
                                Text(
                                    item, style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle(if (isThai) "วิธีทำ" else "Steps")
                        r.steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
