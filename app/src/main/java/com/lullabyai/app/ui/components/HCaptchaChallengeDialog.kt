package com.lullabyai.app.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.lullabyai.app.BuildConfig
import com.lullabyai.app.ui.theme.Primary

/**
 * hCaptcha entry point for a form: a plain button, not an inline widget. An earlier version
 * embedded the WebView directly in the form at a fixed 100dp height — fine for the plain checkbox
 * pass-through, but hCaptcha's actual image-challenge popup (triggered whenever it doesn't trust a
 * plain click) needs far more room and was getting clipped/"out of frame" inside that small box.
 * Tapping this button opens the WebView in a proper full-size [Dialog] instead, where the challenge
 * has space to render.
 */
@Composable
fun HCaptchaVerifyButton(token: String?, onTokenChange: (String?) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val verified = token != null

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = if (verified) ButtonDefaults.outlinedButtonColors(contentColor = Primary) else ButtonDefaults.outlinedButtonColors()
    ) {
        Icon(if (verified) Icons.Filled.CheckCircle else Icons.Filled.VerifiedUser, null, modifier = Modifier.height(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (verified) "ยืนยันแล้ว / Verified" else "ยืนยันว่าไม่ใช่บอท / Verify you're not a robot")
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Filled.Close, "ปิด", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HCaptchaWebView(
                        modifier = Modifier.fillMaxWidth().height(480.dp),
                        onTokenChange = { newToken ->
                            onTokenChange(newToken)
                            if (newToken != null) showDialog = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * The raw hCaptcha WebView. [onTokenChange] fires with the verification token once checked, and
 * with null again if the challenge expires.
 *
 * The token is passed as `captchaToken` into Supabase's signUpWith/signInWith — Supabase Auth calls
 * hCaptcha's /siteverify server-side once Bot Protection is enabled with the secret key; this widget
 * only needs to produce the token, not verify it.
 *
 * NOTE: hCaptcha ties a site key to registered hostnames. This loads with base URL
 * "https://hcaptcha.com" (WebView requires *some* https origin) — if your hCaptcha site is
 * configured for specific domains rather than "any hostname" / mobile app mode, verification
 * may fail on-device even though a token comes back. Check the site's hostname/domain settings
 * in the hCaptcha dashboard if that happens.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HCaptchaWebView(modifier: Modifier = Modifier, onTokenChange: (String?) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            WebView(context).apply {
                // Android emulators frequently fail to composite WebView's hardware-accelerated
                // layer onto their virtualized GPU (visible as GFXSTREAM/Mesa errors in Logcat) —
                // the page runs fine (JS executes, network loads) but nothing actually paints to
                // screen. Forcing software rendering here works around it; real devices with a
                // working GPU are unaffected either way.
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                settings.javaScriptEnabled = true
                // hCaptcha's script uses localStorage/sessionStorage to initialize the checkbox
                // iframe. WebView defaults DOM storage to OFF — without this, the widget fails to
                // render at all (no error shown, just an empty box), which is exactly what "I can't
                // see the captcha" looks like.
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                // Piped to Logcat (tag "HCaptcha") so a silent failure has an actual message to
                // look at instead of guessing blind.
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        android.util.Log.d("HCaptcha", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        return true
                    }
                }
                addJavascriptInterface(HCaptchaBridge(onTokenChange), "AndroidBridge")
                val html = """
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                      <style>
                        html, body { margin:0; padding:0; width:100%; height:100%; }
                        body { display:flex; justify-content:center; align-items:flex-start; padding-top:16px; }
                      </style>
                    </head>
                    <body>
                    <div id="captcha-container"></div>
                    <script>
                      window.onerror = function(msg, src, line) { console.error('JS error: ' + msg + ' at ' + src + ':' + line); };
                      function onVerify(token) { AndroidBridge.onToken(token); }
                      function onExpire() { AndroidBridge.onExpired(); }
                      function onHCaptchaApiLoad() {
                        console.log('hcaptcha api loaded, rendering...');
                        try {
                          hcaptcha.render('captcha-container', {
                            sitekey: '${BuildConfig.HCAPTCHA_SITE_KEY}',
                            size: 'normal',
                            callback: onVerify,
                            'expired-callback': onExpire,
                            'error-callback': onExpire
                          });
                        } catch (e) {
                          console.error('hcaptcha render() threw: ' + e);
                        }
                      }
                    </script>
                    <script src="https://js.hcaptcha.com/1/api.js?onload=onHCaptchaApiLoad&render=explicit" async defer
                            onerror="console.error('hcaptcha api.js failed to load')"></script>
                    </body></html>
                """.trimIndent()
                loadDataWithBaseURL("https://hcaptcha.com", html, "text/html", "utf-8", null)
            }
        }
    )
}

private class HCaptchaBridge(private val onTokenChange: (String?) -> Unit) {
    @JavascriptInterface
    fun onToken(token: String) { onTokenChange(token) }

    @JavascriptInterface
    fun onExpired() { onTokenChange(null) }
}
