package com.lullabyai.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lullabyai.app.util.GoogleAuthHelper
import com.lullabyai.app.viewmodel.AuthViewModel

/** Launches classic Google account picker; completes Supabase auth in [AuthViewModel]. */
@Composable
fun rememberGoogleSignInAction(
    authViewModel: AuthViewModel,
    isThai: Boolean,
    onSuccess: () -> Unit,
    accountType: String = "parent"
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleAuthHelper.idTokenFromResult(result.data)
            .onSuccess { token ->
                authViewModel.completeGoogleSignIn(
                    idToken = token,
                    isThai = isThai,
                    onSuccess = onSuccess,
                    accountType = accountType
                )
            }
            .onFailure { e ->
                authViewModel.completeGoogleSignIn(
                    idToken = null,
                    isThai = isThai,
                    onSuccess = onSuccess,
                    accountType = accountType,
                    errorMessage = e.message
                )
            }
    }
    return {
        launcher.launch(GoogleAuthHelper.signInIntent(context))
    }
}
