@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.screens.settings.DocumentVaultBody
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Health-hub entry for medical documents — aligned to the Stitch Document Vault (same file hub as
 * Settings → Document Vault). Keeps Child tab selected in the footer.
 */
@Composable
fun MedicalDocumentHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    DocumentVaultBody(
        childViewModel = childViewModel,
        navController = navController,
        onBack = onBack,
        selectedTab = BottomTab.CHILD,
        titleTh = "คลังเอกสาร (Vault)",
        titleEn = "Document Vault"
    )
}
