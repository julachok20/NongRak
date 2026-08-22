package com.lullabyai.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lullabyai.app.util.LineAuthHelper
import com.lullabyai.app.viewmodel.AuthViewModel

/**
 * Launches LINE Login; completes Supabase auth in [AuthViewModel].
 *
 * Deliberately shaped like [rememberGoogleSignInAction] — same arguments, same call site — so the
 * two social buttons stay interchangeable in the auth screens.
 */
@Composable
fun rememberLineSignInAction(
    authViewModel: AuthViewModel,
    isThai: Boolean,
    onSuccess: () -> Unit,
    accountType: String = "parent"
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LineAuthHelper.idTokenFromResult(result.data)
            .onSuccess { token ->
                authViewModel.completeLineSignIn(
                    idToken = token,
                    isThai = isThai,
                    onSuccess = onSuccess,
                    accountType = accountType
                )
            }
            .onFailure { e ->
                authViewModel.completeLineSignIn(
                    idToken = null,
                    isThai = isThai,
                    onSuccess = onSuccess,
                    accountType = accountType,
                    errorMessage = e.message
                )
            }
    }
    return {
        launcher.launch(LineAuthHelper.signInIntent(context))
    }
}
