@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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

                    // Nutritional Breakdown & Benefits Card
                    NutritionalBreakdownCard(
                        caloriesKcal = r.caloriesKcal,
                        proteinG = r.proteinG,
                        carbG = r.carbG,
                        fatG = r.fatG,
                        calciumMg = r.calciumMg,
                        ironMg = r.ironMg,
                        benefits = r.benefits,
                        isThai = isThai
                    )

                    // Video Tutorial Card
                    CookingVideoCard(
                        dishTitle = r.title,
                        videoTitle = r.videoTitle,
                        videoUrl = r.videoUrl,
                        isThai = isThai
                    )

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
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NutritionalBreakdownCard(
    caloriesKcal: Int?,
    proteinG: Double?,
    carbG: Double?,
    fatG: Double?,
    calciumMg: Int?,
    ironMg: Double?,
    benefits: List<String>,
    isThai: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        if (isThai) "คุณค่าทางโภชนาการและประโยชน์ที่ได้รับ" else "Nutritional Breakdown & Benefits",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isThai) "ปริมาณสารอาหารโดยประมาณต่อ 1 มื้อ" else "Estimated nutrients per child portion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Macro / Micro Grid
            val kcalStr = caloriesKcal?.let { "$it kcal" } ?: (if (isThai) "160 kcal" else "160 kcal")
            val proteinStr = proteinG?.let { "$it g" } ?: (if (isThai) "6.5 g" else "6.5 g")
            val carbStr = carbG?.let { "$it g" } ?: (if (isThai) "20.0 g" else "20.0 g")
            val fatStr = fatG?.let { "$it g" } ?: (if (isThai) "3.8 g" else "3.8 g")
            val calciumStr = calciumMg?.let { "$it mg" } ?: (if (isThai) "85 mg" else "85 mg")
            val ironStr = ironMg?.let { "$it mg" } ?: (if (isThai) "1.8 mg" else "1.8 mg")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientMacroChip(
                    icon = Icons.Filled.Bolt,
                    label = if (isThai) "พลังงาน" else "Calories",
                    value = kcalStr,
                    accentColor = Color(0xFFE65100),
                    modifier = Modifier.weight(1f)
                )
                NutrientMacroChip(
                    icon = Icons.Filled.FitnessCenter,
                    label = if (isThai) "โปรตีน" else "Protein",
                    value = proteinStr,
                    accentColor = Primary,
                    modifier = Modifier.weight(1f)
                )
                NutrientMacroChip(
                    icon = Icons.Filled.Grass,
                    label = if (isThai) "คาร์บ" else "Carbs",
                    value = carbStr,
                    accentColor = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientMacroChip(
                    icon = Icons.Filled.Opacity,
                    label = if (isThai) "ไขมันดี" else "Healthy Fat",
                    value = fatStr,
                    accentColor = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f)
                )
                NutrientMacroChip(
                    icon = Icons.Filled.Shield,
                    label = if (isThai) "แคลเซียม" else "Calcium",
                    value = calciumStr,
                    accentColor = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                )
                NutrientMacroChip(
                    icon = Icons.Filled.Favorite,
                    label = if (isThai) "ธาตุเหล็ก" else "Iron",
                    value = ironStr,
                    accentColor = Color(0xFFC2185B),
                    modifier = Modifier.weight(1f)
                )
            }

            // Benefits list
            if (benefits.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    if (isThai) "ประโยชน์ต่อการเติบโตของลูกน้อย:" else "Growth & Health Benefits:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                benefits.forEach { benefit ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Text(
                            benefit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientMacroChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.09f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CookingVideoCard(
    dishTitle: String,
    videoTitle: String?,
    videoUrl: String?,
    isThai: Boolean
) {
    val context = LocalContext.current
    val targetUrl = videoUrl ?: ("https://www.youtube.com/results?search_query=" +
        java.net.URLEncoder.encode(if (isThai) "วิธีทำ $dishTitle สำหรับเด็ก" else "How to cook $dishTitle for toddler", "UTF-8"))
    val displayTitle = videoTitle?.takeIf { it.isNotBlank() }
        ?: if (isThai) "วิดีโอสาธิตวิธีทำ $dishTitle" else "Video tutorial: How to make $dishTitle"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                    context.startActivity(intent)
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isThai) "วิดีโอสาธิตวิธีทำอาหาร" else "Cooking Video Tutorial",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCC0000)
                    )
                    Text(
                        displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
            }

            Button(
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))
            ) {
                Icon(Icons.Filled.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isThai) "เปิดดูคลิปวิดีโอวิธีทำ (YouTube)" else "Watch Video on YouTube",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
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
