package com.blake.editor.editor

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import org.json.JSONObject

class EditorController(
    private val webView: WebView,
    private val onEvent: (EditorEvent) -> Unit
) {
    private var isReady = false
    private val pendingCommands = mutableListOf<EditorCommand>()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(webView.context))
        .build()

    private val bridge = EditorBridge { event ->
        if (event is EditorEvent.EditorReady) {
            isReady = true
            executePendingCommands()
        }
        onEvent(event)
    }

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Section 11: Disable network navigation in editor WebView for security
                return true
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/editor/index.html")
    }

    fun execute(command: EditorCommand) {
        if (!isReady) {
            pendingCommands.add(command)
            return
        }

        when (command) {
            is EditorCommand.SetContent -> {
                val escapedText = JSONObject.quote(command.text)
                val mode = command.syntaxMode.aceMode
                evaluate("window.EditorHost.setContent($escapedText, '$mode');")
            }
            is EditorCommand.SetSyntaxMode -> {
                evaluate("window.EditorHost.setMode('${command.syntaxMode.aceMode}');")
            }
            is EditorCommand.SetTheme -> {
                val themeName = when (command.themeMode) {
                    ThemeMode.DARK -> "twilight"
                    ThemeMode.AMOLED -> "twilight"
                    ThemeMode.EYE_CARE -> "chrome"
                }
                val modeParam = command.themeMode.name.lowercase()
                evaluate("window.EditorHost.setTheme('$themeName', '$modeParam');")
            }
            is EditorCommand.SetReadOnly -> {
                evaluate("window.EditorHost.setReadOnly(${command.isReadOnly});")
            }
            is EditorCommand.SetFontSize -> {
                evaluate("window.EditorHost.setFontSize(${command.sizeSp});")
            }
            is EditorCommand.GoToLine -> {
                evaluate("window.EditorHost.goToLine(${command.line});")
            }
            is EditorCommand.Find -> {
                val escapedQuery = JSONObject.quote(command.query)
                evaluate("window.EditorHost.find($escapedQuery, ${command.caseSensitive}, ${command.wholeWord});")
            }
            is EditorCommand.Replace -> {
                val escapedQuery = JSONObject.quote(command.query)
                val escapedRepl = JSONObject.quote(command.replacement)
                evaluate("window.EditorHost.replace($escapedQuery, $escapedRepl, ${command.replaceAll});")
            }
            is EditorCommand.Undo -> evaluate("window.EditorHost.undo();")
            is EditorCommand.Redo -> evaluate("window.EditorHost.redo();")
            is EditorCommand.RequestContent -> evaluate("window.EditorHost.getContent();")
            is EditorCommand.ShowPreview -> {
                val escapedText = JSONObject.quote(command.markdownText)
                evaluate("window.PreviewHost.show($escapedText);")
            }
            is EditorCommand.HidePreview -> {
                evaluate("window.PreviewHost.hide();")
            }
        }
    }

    private fun executePendingCommands() {
        val copy = ArrayList(pendingCommands)
        pendingCommands.clear()
        for (cmd in copy) {
            execute(cmd)
        }
    }

    private fun evaluate(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
