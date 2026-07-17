package com.lullabyai.app.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lullabyai.app.BuildConfig

/**
 * Inline hCaptcha checkbox widget — embedded directly in the form (not a popup dialog), same as
 * the standard "I'm not a robot" checkbox. [onTokenChange] fires with the verification token
 * once checked, and with null again if the challenge expires — callers should gate their submit
 * button on `token != null` rather than firing once and forgetting.
 *
 * The token is passed as `captchaToken` into Supabase's signUpWith/signInWith — Supabase Auth
 * calls hCaptcha's /siteverify server-side once Bot Protection is enabled with the secret key;
 * this widget only needs to produce the token, not verify it.
 *
 * NOTE: hCaptcha ties a site key to registered hostnames. This loads with base URL
 * "https://hcaptcha.com" (WebView requires *some* https origin) — if your hCaptcha site is
 * configured for specific domains rather than "any hostname" / mobile app mode, verification
 * may fail on-device even though a token comes back. Check the site's hostname/domain settings
 * in the hCaptcha dashboard if that happens.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HCaptchaWidget(onTokenChange: (String?) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(100.dp),
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
                // Without an explicit viewport, WebView renders the page at a wide desktop-style
                // width by default and then scales it down — the hCaptcha checkbox iframe doesn't
                // scale proportionally with that, so it spills past this box's edges. Locking the
                // viewport to the WebView's actual CSS width, disabling zoom, and hiding scrollbars
                // keeps the widget confined to its allotted space.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                webViewClient = WebViewClient()
                // Piped to Logcat (tag "HCaptcha") so a silent failure — blank box, no error shown
                // in the app UI — has an actual message to look at instead of guessing blind.
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
                        html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; }
                        body { display:flex; justify-content:center; align-items:center; }
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
                            size: 'compact',
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
