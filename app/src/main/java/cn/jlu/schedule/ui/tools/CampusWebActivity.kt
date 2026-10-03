package cn.jlu.schedule.ui.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.CampusCookieJar
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AcademicProgressStore
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.ui.theme.ThemePaletteProvider

/**
 * 智慧校园通用网页容器，自动注入校内会话 Cookie 并处理校园私有证书信任。
 */
class CampusWebActivity : AccountScopedActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_campus_web)

        val palette = ThemePaletteProvider.fromContext(this)
        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: TpassConfig.IEDU_PORTAL_URL
        val defaultTitle = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.tools_web_portal)

        findViewById<View>(R.id.campusWebRoot).setBackgroundColor(palette.pageBackground)
        findViewById<View>(R.id.campusWebHeader).setBackgroundColor(palette.panelAltBackground)
        titleView = findViewById(R.id.campusWebTitle)
        titleView.text = defaultTitle
        titleView.setTextColor(palette.textPrimary)

        val backBtn = findViewById<ImageView>(R.id.campusWebBack)
        backBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        backBtn.setOnClickListener { finish() }

        val refreshBtn = findViewById<ImageView>(R.id.campusWebRefresh)
        refreshBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        refreshBtn.setOnClickListener { webView.reload() }

        progressBar = findViewById(R.id.campusWebProgress)
        progressBar.progressTintList = ColorStateList.valueOf(palette.iconTint)

        webView = findViewById(R.id.campusWebView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = TpassConfig.USER_AGENT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // 把原生 CookieJar 会话写进 WebView
        JwApiClient.syncJarToWebView(this)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank() && title != targetUrl && !title.contains("http")) {
                    titleView.text = title
                }
            }
        }

        if (targetUrl.contains("xywccx")) {
            webView.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onCaptured(url: String, payload: String) {
                    val parsed = cn.jlu.schedule.parser.PyfaTranscriptParser.parse(payload) ?: return
                    withAccountData { AcademicProgressStore.save(it, parsed) }
                }
            }, "JluPyfaBridge")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // 页面跳转后同步最新 Cookie 回原生 CookieJar
                JwApiClient.importAllWebViewCookies(this@CampusWebActivity)

                if (url?.contains("xywccx") == true) {
                    view?.postDelayed({
                        val extractJs = """
                            (function(){
                              try {
                                var extracted = [];
                                var nodes = document.querySelectorAll('.jm-node, .mind-node, .jmind-node, [data-node], tr, .node');
                                if (nodes && nodes.length > 0) {
                                  nodes.forEach(function(el){
                                    var t = (el.innerText || el.textContent || '').trim();
                                    if (!t || t.length > 100) return;
                                    var m = t.match(/([^\d\n\r\/]{2,20})[\s\S]*?(\d+\.?\d*)\s*[\/分]\s*(\d+\.?\d*)/);
                                    if (m) {
                                      var name = m[1].replace(/[（(【\[:：]/g, '').trim();
                                      var n1 = parseFloat(m[2]);
                                      var n2 = parseFloat(m[3]);
                                      if (name && !isNaN(n1) && !isNaN(n2)) {
                                        var earned = Math.min(n1, n2);
                                        var req = Math.max(n1, n2);
                                        if (t.indexOf('需') > t.indexOf('已') || t.indexOf('应') > t.indexOf('已')) {
                                          earned = n1; req = n2;
                                        } else if (t.indexOf('已') > t.indexOf('需') || t.indexOf('已') > t.indexOf('应')) {
                                          req = n1; earned = n2;
                                        }
                                        extracted.push({ name: name, required: req, earned: earned });
                                      }
                                    }
                                  });
                                }
                                if (extracted.length > 0) {
                                  JluPyfaBridge.onCaptured('dom_extract', JSON.stringify({
                                    type: 'pyfa_dom_extract',
                                    modules: extracted
                                  }));
                                }
                              } catch(e){}
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(extractJs, null)
                    }, 2000)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl(targetUrl)
    }

    override fun onDestroy() {
        webView.apply {
            loadUrl("about:blank")
            onPause()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_target_url"
        const val EXTRA_TITLE = "extra_target_title"

        fun start(context: Context, url: String, title: String) {
            val intent = Intent(context, CampusWebActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }
}
