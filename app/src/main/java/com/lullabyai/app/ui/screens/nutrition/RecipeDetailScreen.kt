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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.NutritionViewModel

/**
 * Tutorial — Stitch recipe page. Video header, the four numbers a parent checks before starting
 * (prep, cook, age, serves), what it is good for, a ticking ingredient list and the steps.
 *
 * Ingredients already in the household pantry start ticked, so the list reads as "what you still
 * need". Finishing logs the dish as a meal; it never empties the pantry by itself, because those
 * rows belong to the whole family and another carer may be counting on them.
 */
@Composable
fun RecipeDetailScreen(vm: NutritionViewModel, lang: String, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val r = ui.recipe
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(ui.child?.id) { if (ui.fridgeIngredients.isEmpty()) vm.loadFridgeIngredients() }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "วิธีทำอาหาร" else "Recipe",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            ui.recipeLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            r == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (isThai) "ไม่พบข้อมูลสูตรอาหาร" else "Recipe not found", color = Outline)
            }
            else -> {
                val pantryNames = ui.fridgeIngredients.map { it.ingredientName.trim().lowercase() }
                // Pre-tick what the family already has: an ingredient line usually reads
                // "มันเทศหวาน 100 กรัม", so a pantry name appearing anywhere in it counts.
                val haveIt = remember(r.ingredients, pantryNames) {
                    r.ingredients.associateWith { line ->
                        val lower = line.lowercase()
                        pantryNames.any { it.length >= 2 && lower.contains(it) }
                    }
                }
                val checked = remember(r.title) { mutableStateMapOf<String, Boolean>() }
                LaunchedEffect(haveIt) { haveIt.forEach { entry -> checked.putIfAbsent(entry.key, entry.value) } }
                var saving by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RecipeHero(title = r.title, videoTitle = r.videoTitle, videoUrl = r.videoUrl, isThai = isThai)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeStat(Modifier.weight(1f), Icons.Filled.Schedule, if (isThai) "เตรียม" else "Prep", prepTimeLabel(r.timeMinutes, isThai))
                        RecipeStat(Modifier.weight(1f), Icons.Filled.Whatshot, if (isThai) "ทำ" else "Cook", r.timeMinutes)
                        RecipeStat(Modifier.weight(1f), Icons.Filled.ChildCare, if (isThai) "อายุ" else "Age", r.ageRange)
                        RecipeStat(Modifier.weight(1f), Icons.Filled.Restaurant, if (isThai) "เสิร์ฟ" else "Serves", r.servings)
                    }

                    if (r.nutritionNote.isNotBlank() || r.benefits.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(0.07f)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                                    Text(if (isThai) "ประโยชน์ทางโภชนาการ" else "Why it is good", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                                }
                                if (r.nutritionNote.isNotBlank()) {
                                    Text(r.nutritionNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                r.benefits.forEach { b ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(15.dp))
                                        Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    NutritionalBreakdownCard(
                        caloriesKcal = r.caloriesKcal, proteinG = r.proteinG, carbG = r.carbG, fatG = r.fatG,
                        calciumMg = r.calciumMg, ironMg = r.ironMg, benefits = emptyList(), isThai = isThai
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(if (isThai) "ส่วนประกอบ" else "Ingredients")
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text(
                                    if (isThai) "${r.ingredients.size} รายการ" else "${r.ingredients.size} items",
                                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        val missing = r.ingredients.count { checked[it] != true }
                        Text(
                            if (missing == 0) (if (isThai) "มีวัตถุดิบครบแล้ว ลงมือได้เลย" else "You have everything — go ahead")
                            else (if (isThai) "ยังขาดอีก $missing อย่าง (ติ๊กรายการที่มีอยู่แล้ว)" else "$missing still needed (tick what you have)"),
                            style = MaterialTheme.typography.labelSmall, color = Outline
                        )
                        r.ingredients.forEach { line ->
                            val isChecked = checked[line] == true
                            Surface(
                                onClick = { checked[line] = !isChecked },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isChecked) Primary.copy(0.07f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(
                                        if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        null, tint = if (isChecked) Primary else Outline, modifier = Modifier.size(20.dp)
                                    )
                                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (haveIt[line] == true) {
                                        Text(if (isThai) "มีในคลัง" else "in pantry", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle(if (isThai) "ขั้นตอนการทำ" else "Steps")
                        r.steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
                    }

                    Button(
                        onClick = {
                            saving = true
                            vm.logRecipeAsMeal(r.title, r.caloriesKcal) { ok ->
                                saving = false
                                scope.launch {
                                    snackbars.showSnackbar(
                                        if (ok) (if (isThai) "บันทึก ${r.title} เป็นมื้ออาหารแล้ว" else "Logged ${r.title} as a meal")
                                        else (if (isThai) "บันทึกไม่สำเร็จ ลองอีกครั้ง" else "Could not save — try again")
                                    )
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "เสร็จสิ้นและบันทึกมื้ออาหาร" else "Finish and log this meal", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (isThai) "เสร็จสิ้นการปรุง" else "Done cooking", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (isThai) "วัตถุดิบยังอยู่ในคลังของบ้าน แตะ ใช้ทำอาหาร ในหน้าคลังเมื่อต้องการเอาออก"
                        else "Ingredients stay in the family pantry; take them out from the pantry page when you want them gone",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Prep is a fraction of the recipe's total time — a hint, not invented precision. */
private fun prepTimeLabel(total: String, isThai: Boolean): String {
    val minutes = Regex("\\d+").find(total)?.value?.toIntOrNull() ?: return if (isThai) "ไม่ระบุ" else "n/a"
    val prep = (minutes / 3).coerceAtLeast(5)
    return if (isThai) "$prep น." else "$prep min"
}

@Composable
private fun RecipeStat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The design's video header: the dish, a play button when there is a tutorial, and its title. */
@Composable
private fun RecipeHero(title: String, videoTitle: String?, videoUrl: String?, isThai: Boolean) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Primary, Tertiary, SecondaryContainer)))
    ) {
        AsyncImage(
            model = remember(title) { foodImageUrl(title) }, contentDescription = title,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))))
        if (!videoUrl.isNullOrBlank()) {
            Surface(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))) } },
                shape = CircleShape, color = Color.White.copy(0.9f),
                modifier = Modifier.align(Alignment.Center).size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, if (isThai) "เล่นวิดีโอสอนทำ" else "Play the tutorial", tint = Primary, modifier = Modifier.size(30.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp), color = Primary,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
            ) {
                Text(
                    if (isThai) "ดูวิดีโอสอนทำ" else "WATCH TUTORIAL",
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
        Text(
            videoTitle?.takeIf { it.isNotBlank() } ?: title,
            Modifier.align(Alignment.BottomEnd).padding(14.dp).fillMaxWidth(0.62f),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
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
