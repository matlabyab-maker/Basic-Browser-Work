
          package com.mainbrowser.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.MimeTypeMap
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ScaleGestureDetector
import android.content.ClipData
import android.content.ClipboardManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val tabViews = mutableListOf<WebView>()
    private val tabTitles = mutableListOf<String>()
    private var currentTab = 0
    private val web: WebView get() = tabViews[currentTab]
    private lateinit var webContainer: FrameLayout
    private lateinit var pageProgress: ProgressBar
    private lateinit var modeButton: Button
    private var desktopMode = false
    private data class PendingDownload(val url: String, val userAgent: String?, val disposition: String?, val mimeType: String?)
    private var pendingDownload: PendingDownload? = null
    private data class VideoCandidate(val url: String, val title: String, val mime: String, val kind: String)
    private val videoCandidates = linkedMapOf<String, VideoCandidate>()
    private val videoBlinkHandler = Handler(Looper.getMainLooper())
    private var videoBlinking = false
    private lateinit var videoLinksButton: Button
    private var blobTempFile: File? = null
    private var blobOutput: FileOutputStream? = null
    private var blobDisplayName: String = "download.bin"
    private var blobMimeType: String = "application/octet-stream"
    private var blobBytes: Long = 0L
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("main_browser_state", MODE_PRIVATE) }
    private val history = mutableListOf<String>()
    private val bookmarks = mutableListOf<String>()
    private var autoSelect = false
    private var fullScreen = false
    private var pageScale = 1.0f
    private lateinit var headerBar: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView
    private val downloadLinkNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private lateinit var navBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private val fileRequest = 701

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: Exception) {
            android.util.Log.e("BasicBrowserWork", "Video extractor initialization failed", e)
        }
        history.addAll(readList("history"))
        desktopMode = prefs.getBoolean("desktop_mode", false)
        bookmarks.addAll(readList("bookmarks"))
        buildInterface()
        val last = prefs.getString("last_url", null)
        // The first tab is created by buildInterface; load the last visited page.
        if (!last.isNullOrBlank()) web.loadUrl(last)
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            fillViewport = false
            addView(tabStrip, HorizontalScrollView.LayoutParams(WRAP, MATCH))
        }
        root.addView(tabScroll, LinearLayout.LayoutParams(MATCH, dp(44)))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 1, 8, 0)
        }
        header.addView(TextView(this).apply {
            text = "Basic Browser Work"
            textSize = 19f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, MATCH))

        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 15f
            hint = "Enter address or search"
            setTextColor(Color.BLACK)
            setPadding(12, 4, 12, 4)
        }
        header.addView(address, LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(makeButton("Go", 50) { navigate(address.text.toString()) })
        header.addView(makeButton("Tabs", 62) { showTabs() })
        header.addView(makeButton("Menu", 58) { showMainMenu() })
        headerBar = header
        root.addView(header)

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        val navItems = listOf(
            "Back" to { if (web.canGoBack()) web.goBack() else toast("No previous page") },
            "Forward" to { if (web.canGoForward()) web.goForward() else toast("No next page") },
            "Past" to { pasteFromClipboard() },
            "Full Screen" to { toggleFullScreen() },
            "History" to { showHistory() },
            "Bookmarks" to { showBookmarks() },
            "Downloads" to { showDownloadsInfo() },
            "Home" to { showHome() },
            "Mode" to { showModeChooser() }
        )
        navItems.forEach { (label, action) ->
            val b = makeButton(label, 1, 48, action)
            b.textSize = 12.5f
            b.setTextColor(Color.YELLOW)
            navBar.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        modeButton = navBar.getChildAt(8) as Button
        modeButton.text = if (desktopMode) "Desktop" else "Mobile"
        root.addView(navBar)

        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(25, 118, 210))
        }
        root.addView(pageProgress, LinearLayout.LayoutParams(MATCH, dp(4)))
        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        createTab("https://www.google.com", switchTo = true)
        updateTabStrip()

        status = TextView(this).apply {
            text = "Ready"
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(8, 2, 8, 2)
        }
        root.addView(status)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(5, 5, 5, 7)
        }
        bottom.addView(makeButton("Auto Select", 1, 64) { toggleAutoSelect() }, LinearLayout.LayoutParams(0, dp(64), 0.95f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Save", 1, 64) { savePageText() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        videoLinksButton = makeButton("Video Links", 1, 64) { showVideoLinks() }
        bottom.addView(videoLinksButton, LinearLayout.LayoutParams(0, dp(64), 1.1f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("File", 1, 64) { chooseFiles() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Answer", 1, 64) { answerDialog() }, LinearLayout.LayoutParams(0, dp(64), 1.0f).apply { setMargins(2, 0, 2, 0) })
        bottomBar = bottom
        root.addView(bottom)
        setContentView(root)
    }

    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = prefs.getBoolean("images_enabled", true)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            blockNetworkLoads = false
            if (Build.VERSION.SDK_INT >= 23) offscreenPreRaster = true
            userAgentString = if (desktopMode) desktopUserAgent() else WebSettings.getDefaultUserAgent(this@MainActivity)
        }
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(PageBridge(), "BasicBrowserWorkBridge")
        view.addJavascriptInterface(DownloadBridge(), "BasicBrowserWorkDownloadBridge")
        view.addJavascriptInterface(DownloadNameBridge(), "BasicBrowserWorkDownloadNameBridge")
        view.addJavascriptInterface(VideoScanBridge(), "BasicBrowserWorkVideoBridge")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                if (request?.isForMainFrame != true) return false
                val target = request.url.toString()
                val path = Uri.parse(target).lastPathSegment?.lowercase(Locale.ROOT).orEmpty()
                val downloadable = listOf(".pdf", ".zip", ".rar", ".7z", ".apk", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".txt", ".mp3", ".mp4", ".m4a", ".wav")
                if (downloadable.any { path.endsWith(it) }) {
                    startDownload(PendingDownload(target, WebSettings.getDefaultUserAgent(this@MainActivity), null, null))
                    return true
                }
                return false
            }

            override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(v, url, favicon)
                if (tabViews.indexOf(view) == currentTab) {
                    pageProgress.progress = 0
                    pageProgress.visibility = View.VISIBLE
                    status.text = "Loading page…"
                }
            }
            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                val index = tabViews.indexOf(view)
                if (index >= 0 && !url.isNullOrBlank()) {
                    tabTitles[index] = view.title?.takeIf { it.isNotBlank() } ?: url
                    updateTabStrip()
                    if (index == currentTab) {
                        address.setText(url)
                        prefs.edit().putString("last_url", url).apply()
                        status.text = view.title ?: url
                        pageProgress.progress = 100
                        pageProgress.visibility = View.GONE
                        if (autoSelect) injectAutoSelect()
                    }
                    addHistory(url)
                    installGeneratedDownloadCapture(view)
                    // One delayed DOM scan catches initially lazy-loaded media. Further
                    // media requests are detected by shouldInterceptRequest, avoiding
                    // repeated whole-page scans that used to slow browsing down.
                    view.postDelayed({
                        if (!isFinishing && !isDestroyed && tabViews.contains(view) && view.url == url) scanPageForVideos(view)
                    }, 1500)
                }
            }
            override fun shouldInterceptRequest(v: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val u = request?.url?.toString() ?: return null
                val lower = u.lowercase(Locale.ROOT)
                val path = Uri.parse(u).path?.lowercase(Locale.ROOT).orEmpty()
                if (isVideoResource(lower, path)) addVideoCandidate(u, "Video stream", request?.requestHeaders?.get("Accept") ?: "", videoKind(u))
                return super.shouldInterceptRequest(v, request)
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                if (tabViews.indexOf(view) == currentTab) {
                    pageProgress.progress = newProgress.coerceIn(0, 100)
                    pageProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    if (newProgress < 100) status.text = "Loading page… $newProgress%"
                }
            }
            override fun onShowFileChooser(
                webView: WebView?,
                callback: android.webkit.ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    startActivityForResult(intent, fileRequest)
                    true
                } catch (_: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    toast("Unable to open device files")
                    false
                }
            }

            override fun onCreateWindow(v: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val newView = createTab("about:blank", switchTo = true)
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = newView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onReceivedTitle(v: WebView?, title: String?) {
                val index = tabViews.indexOf(view)
                if (index >= 0 && !title.isNullOrBlank()) {
                    tabTitles[index] = title
                    updateTabStrip()
                }
            }
        }
        val pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pageScale = (pageScale * detector.scaleFactor).coerceIn(0.6f, 1.0f)
                view.evaluateJavascript("document.documentElement.style.zoom='${pageScale}'", null)
                return true
            }
        })
        view.setOnTouchListener { _, event -> pinch.onTouchEvent(event); false }
        view.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val parsed = Uri.parse(url)
            when (parsed.scheme?.lowercase(Locale.ROOT)) {
                "blob", "data" -> downloadPageGeneratedUrl(view, url, contentDisposition, mimeType)
                "http", "https" -> startDownload(PendingDownload(url, userAgent, contentDisposition, mimeType))
                "content", "file" -> copyLocalUriToDownloads(parsed, contentDisposition, mimeType)
                else -> toast("Unsupported download link type")
            }
        })
    }

    private fun isVideoResource(url: String, path: String): Boolean {
        return listOf(".mp4", ".m4v", ".webm", ".mov", ".mkv", ".3gp", ".ts", ".m3u8", ".mpd", ".ism/manifest")
            .any { path.contains(it) || url.contains(it) }
    }

    private fun videoKind(url: String): String {
        val p = Uri.parse(url).path?.lowercase(Locale.ROOT).orEmpty()
        return when {
            p.contains(".m3u8") -> "HLS playlist"
            p.contains(".mpd") -> "MPEG-DASH manifest"
            p.contains(".webm") -> "WebM"
            p.contains(".mkv") -> "Matroska"
            p.contains(".mov") -> "QuickTime"
            p.contains(".ts") -> "MPEG-TS segment"
            else -> "Video"
        }
    }

    private fun shortVideoLabel(raw: String): String {
        val u = Uri.parse(raw)
        val host = u.host ?: "Video"
        val file = u.lastPathSegment?.substringBefore('?')?.takeIf { it.isNotBlank() }
        return (file ?: host).let { if (it.length > 34) it.take(31) + "…" else it }
    }

    private fun addVideoCandidate(url: String, title: String, mime: String, kind: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (url.length > 12000 || url.contains("googlevideo.com/videoplayback") && url.contains("expire=") ) {
            // Signed URLs are retained exactly as received; only their visible label is shortened.
        }
        runOnUiThread {
            if (!videoCandidates.containsKey(url)) {
                videoCandidates[url] = VideoCandidate(url, shortVideoLabel(url), mime, kind)
                videoLinksButton.text = "Video Links •"
                startVideoBlink()
            }
        }
    }
                           

    private fun startVideoBlink() {
        if (videoBlinking) return
        videoBlinking = true
        val task = object : Runnable {
            var lit = false
            override fun run() {
                if (!videoBlinking || videoCandidates.isEmpty()) {
                    videoLinksButton.alpha = 1f
                    videoBlinking = false
                    return
                }
                lit = !lit
                videoLinksButton.alpha = if (lit) 1f else 0.35f
                videoBlinkHandler.postDelayed(this, 550)
            }
        }
        videoBlinkHandler.post(task)
    }

    private fun scanPageForVideos(view: WebView) {
        view.evaluateJavascript("""
          (function(){
            const out = new Map();
            const add = (u,t,m) => { try { if(u && /^https?:/i.test(u)) out.set(u,{url:u,title:t||'',mime:m||''}); } catch(e){} };
            document.querySelectorAll('video,audio').forEach(v=>{
              add(v.currentSrc || v.src, v.getAttribute('title') || document.title, v.getAttribute('type') || '');
              v.querySelectorAll('source').forEach(s=>add(s.src, document.title, s.type));
            });
            document.querySelectorAll('source[src]').forEach(s=>add(s.src,document.title,s.type));
            try { performance.getEntriesByType('resource').forEach(e=>{
              const u=e.name||'';
              if(/\\.(mp4|m4v|webm|mov|mkv|3gp|m3u8|mpd|ts)(\\?|$)/i.test(u)) add(u,document.title,'');
            }); } catch(e){}
            out.forEach(x=>window.BasicBrowserWorkVideoBridge.found(x.url,x.title,x.mime));
            return out.size;
          })();
        """.trimIndent(), null)
    }

    inner class VideoScanBridge {
        @JavascriptInterface fun found(url: String, title: String, mime: String) {
            addVideoCandidate(url, title, mime, videoKind(url))
        }
    }

    private fun showVideoLinks() {
        videoBlinking = false
        videoBlinkHandler.removeCallbacksAndMessages(null)
        videoLinksButton.alpha = 1f
        videoLinksButton.text = "Video Links"
        val items = videoCandidates.values.toList()
        val labels = items.map { "${it.title}  •  ${it.kind}" }.toMutableList()
        labels.add(0, "Extract links from this page (yt-dlp)")
        AlertDialog.Builder(this).setTitle("Video Links (${items.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    analyzeCurrentPageWithExtractor()
                } else {
                    val item = items[which - 1]
                    AlertDialog.Builder(this).setTitle("Ready to download?")
                        .setMessage("${item.title}\n${item.kind}\n\nThe original URL is preserved.")
                        .setPositiveButton("Download") { _, _ ->
                            startDownload(PendingDownload(item.url, WebSettings.getDefaultUserAgent(this), null, item.mime))
                        }
                        .setNeutralButton("Copy link") { _, _ ->
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Video URL", item.url))
                            toast("Original video link copied")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun analyzeCurrentPageWithExtractor() {
        val pageUrl = web.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (pageUrl == null) {
            toast("Open a video webpage first")
            return
        }
        status.text = "Analyzing video page..."
        Thread {
            try {
                val info = YoutubeDL.getInstance().getInfo(pageUrl)
                val title = info.title?.takeIf { it.isNotBlank() } ?: "Online video"
                val request = YoutubeDLRequest(pageUrl)
                request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--no-mtime")
                runOnUiThread {
                    status.text = "Video source found: $title"
                    AlertDialog.Builder(this)
                        .setTitle("Video source found")
                        .setMessage("$title\n\nSource page analyzed successfully. The extractor will choose the best available compatible video/audio streams and merge them when required.")
                        .setPositiveButton("Download video") { _, _ -> startExtractorDownload(request, title) }
                        .setNeutralButton("Copy page link") { _, _ ->
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Video page", pageUrl))
                            toast("Page link copied")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Video extraction failed"
                    AlertDialog.Builder(this).setTitle("Could not extract video")
                        .setMessage(e.message ?: "This site may not be supported, or the video may require login, DRM, or an unavailable extractor.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun startExtractorDownload(request: YoutubeDLRequest, title: String) {
        val downloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BasicBrowserWork")
        if (!downloads.exists()) downloads.mkdirs()
        request.addOption("-o", File(downloads, "%(title).120B.%(ext)s").absolutePath)
        val processId = "main-browser-video-${System.currentTimeMillis()}"
        status.text = "Starting video download..."
        Thread {
            try {
                YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                    runOnUiThread {
                        status.text = "Video: ${progress.toInt()}% • ${line.take(90)}"
                    }
                    kotlin.Unit
                }
                runOnUiThread {
                    status.text = "Video download completed: $title"
                    toast("Video download completed")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Video download failed"
                    toast("Video download failed: ${e.message ?: "unknown error"}")
                }
            }
        }.start()
    }

    private fun installGeneratedDownloadCapture(view: WebView) {
        view.evaluateJavascript("""
            (function(){
              if (window.__slGeneratedDownloadCapture) return;
              window.__slGeneratedDownloadCapture = true;
              document.addEventListener('click', function(event) {
                const a = event.target && event.target.closest ? event.target.closest('a[href]') : null;
                if (!a) return;
                const href = a.href || '';
                const readable = (a.innerText || a.textContent || a.getAttribute('aria-label') || a.title || a.getAttribute('download') || '').trim();
                if (readable && /^https?:/i.test(href)) {
                  try { window.BasicBrowserWorkDownloadNameBridge.remember(href, readable); } catch(e) {}
                }
                if (!href.startsWith('blob:') && !href.startsWith('data:')) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                const name = a.getAttribute('download') || readable || 'download';
                (async function(){
                  try {
                    const response = await fetch(href);
                    const blob = await response.blob();
                    window.BasicBrowserWorkDownloadBridge.beginGeneratedDownload(name, blob.type || 'application/octet-stream');
                    const reader = blob.stream().getReader();
                    while (true) {
                      const part = await reader.read();
                      if (part.done) break;
                      const bytes = part.value;
                      let binary = '';
                      for (let i=0; i<bytes.length; i+=0x8000) {
                        binary += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i+0x8000, bytes.length)));
                      }
                      window.BasicBrowserWorkDownloadBridge.appendGeneratedDownload(btoa(binary));
                    }
                    window.BasicBrowserWorkDownloadBridge.finishGeneratedDownload();
                  } catch (e) {
                    window.BasicBrowserWorkDownloadBridge.failGeneratedDownload(String(e));
                  }
                })();
              }, true);
            })();
        """.trimIndent(), null)
    }

    private fun createTab(url: String, switchTo: Boolean): WebView {
        val view = WebView(this)
        tabViews.add(view)
        tabTitles.add("New tab")
        configureWebView(view)
        updateTabStrip()
        if (switchTo || tabViews.size == 1) {
            currentTab = tabViews.lastIndex
            webContainer.removeAllViews()
            webContainer.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            if (::address.isInitialized) address.setText(if (url == "about:blank") "" else url)
        }
        if (url != "about:blank") view.loadUrl(url)
        updateTabStrip()
        return view
    }

    private fun switchTab(index: Int) {
        if (index !in tabViews.indices) return
        currentTab = index
        webContainer.removeAllViews()
        webContainer.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        address.setText(web.url ?: "")
        status.text = tabTitles[index]
        updateNavigationButtons()
        updateTabStrip()
    }

    private fun closeTab(index: Int) {
        if (tabViews.size <= 1) {
            web.loadUrl("https://www.google.com")
            return
        }
        val closing = tabViews.removeAt(index)
        tabTitles.removeAt(index)
        closing.stopLoading()
        closing.destroy()
        if (index < currentTab) currentTab--
        else if (index == currentTab) currentTab = minOf(index, tabViews.lastIndex)
        switchTab(currentTab)
    }

    private fun updateTabStrip() {
        if (!::tabStrip.isInitialized) return
        tabStrip.removeAllViews()
        tabViews.indices.forEach { index ->
            val title = tabTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "New tab"
            val chip = TextView(this).apply {
                text = "${if (index == currentTab) "● " else ""}${title.take(22)}"
                textSize = 13.5f
                setTextColor(if (index == currentTab) Color.YELLOW else Color.rgb(255, 210, 70))
                setPadding(dp(12), dp(7), dp(12), dp(7))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setBackgroundColor(if (index == currentTab) Color.rgb(55, 55, 55) else Color.rgb(95, 95, 95))
                setOnClickListener { switchTab(index) }
                setOnLongClickListener {
                    if (tabViews.size > 1) {
                        closeTab(index)
                        toast("Tab closed")
                    } else {
                        web.loadUrl("https://www.google.com")
                        tabTitles[0] = "New tab"
                        updateTabStrip()
                    }
                    true
                }
            }
            tabStrip.addView(chip, LinearLayout.LayoutParams(dp(150), dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        val add = TextView(this).apply {
            text = "+"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.YELLOW)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { createTab("https://www.google.com", true) }
        }
        tabStrip.addView(add, LinearLayout.LayoutParams(dp(44), dp(38)))
        tabScroll.post { tabScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun updateNavigationButtons() {
        // Back and Forward remain clickable; their actions safely check WebView history.
    }

    private fun makeButton(label: String, minWidthDp: Int, heightDp: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 15.5f
            setTextColor(Color.YELLOW)
            isAllCaps = false
            minHeight = dp(heightDp)
            minimumHeight = dp(heightDp)
            minWidth = dp(minWidthDp)
            gravity = Gravity.TOP or Gravity.LEFT
            textSize = 15.5f
            setTextColor(Color.YELLOW)
            setPadding(9, 5, 3, 2)
            setOnClickListener { action() }
        }

    private fun makeButton(label: String, minWidthDp: Int, action: () -> Unit): Button =
        makeButton(label, minWidthDp, 54, action)

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val target = if (value.startsWith("http://") || value.startsWith("https://")) value
        else if (value.contains(".") && !value.contains(" ")) "https://$value"
        else "https://www.google.com/search?q=" + Uri.encode(value)
        web.loadUrl(target)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        address.clearFocus()
    }

    private fun showMainMenu() {
        val items = arrayOf("New Tab", "Desktop Site", "Mobile Site", "Bookmarks", "History", "Downloads", "Settings", "Reload")
        AlertDialog.Builder(this).setTitle("Basic Browser Work Menu").setItems(items) { _, which ->
            when (which) {
                0 -> createTab("https://www.google.com", switchTo = true)
                1 -> setDesktopMode(true)
                2 -> setDesktopMode(false)
                3 -> showBookmarks()
                4 -> showHistory()
                5 -> showDownloadsInfo()
                6 -> showSettings()
                7 -> web.reload()
            }
        }.show()
    }

    private fun desktopUserAgent(): String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun showModeChooser() {
        val modes = arrayOf("Mobile mode", "Desktop mode")
        AlertDialog.Builder(this).setTitle("Choose website mode")
            .setSingleChoiceItems(modes, if (desktopMode) 1 else 0) { dialog, which ->
                dialog.dismiss()
                setDesktopMode(which == 1)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        prefs.edit().putBoolean("desktop_mode", enabled).apply()
        tabViews.forEach { tab ->
            tab.settings.userAgentString = if (enabled) desktopUserAgent() else WebSettings.getDefaultUserAgent(this)
            tab.settings.useWideViewPort = true
            tab.settings.loadWithOverviewMode = enabled
        }
        if (::modeButton.isInitialized) modeButton.text = if (enabled) "Desktop" else "Mobile"
        web.reload()
        status.text = if (enabled) "Desktop mode enabled" else "Mobile mode enabled"
    }

    private fun showSettings() {
        val options = arrayOf("Zoom in", "Zoom out", "Reset zoom", "Desktop mode", "Mobile mode", "Search without images", "Disable JavaScript", "Clear browsing data")
        AlertDialog.Builder(this).setTitle("Settings").setItems(options) { _, which ->
            when (which) {
                0 -> web.evaluateJavascript("document.body.style.zoom = (parseFloat(document.body.style.zoom || '1') + 0.1)", null)
                1 -> web.evaluateJavascript("document.body.style.zoom = Math.max(0.1, parseFloat(document.body.style.zoom || '1') - 0.1)", null)
                2 -> web.evaluateJavascript("document.body.style.zoom = '1'", null)
                3 -> setDesktopMode(true)
                4 -> setDesktopMode(false)
                5 -> {
                    val enabled = !prefs.getBoolean("images_enabled", true)
                    prefs.edit().putBoolean("images_enabled", enabled).apply()
                    tabViews.forEach { it.settings.loadsImagesAutomatically = enabled }
                    toast(if (enabled) "Images enabled" else "Image-free browsing enabled")
                    web.reload()
                }
                6 -> {
                    val enabled = !prefs.getBoolean("javascript_enabled", true)
                    prefs.edit().putBoolean("javascript_enabled", enabled).apply()
                    tabViews.forEach { it.settings.javaScriptEnabled = enabled }
                    toast(if (enabled) "JavaScript enabled" else "JavaScript disabled; some sites may not work")
                    web.reload()
                }
                7 -> AlertDialog.Builder(this).setMessage("Clear cookies, cache and browsing history?")
                    .setPositiveButton("Clear") { _, _ ->
                        CookieManager.getInstance().removeAllCookies(null)
                        web.clearCache(true); web.clearHistory()
                        history.clear(); saveList("history", history)
                        toast("Browsing data cleared")
                    }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun showTabs() {
        val labels = tabViews.indices.map { i ->
            "${if (i == currentTab) "✓ " else ""}${i + 1}. ${tabTitles[i].take(45)}"
        }.toMutableList()
        labels.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Tabs (${tabViews.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == tabViews.size) createTab("https://www.google.com", true)
                else {
                    val actions = arrayOf("Open this tab", "Close this tab")
                    AlertDialog.Builder(this).setTitle(labels[which]).setItems(actions) { _, action ->
                        if (action == 0) switchTab(which) else closeTab(which)
                    }.show()
                }
            }.setNegativeButton("Done", null).show()
    }

    private fun showHistory() = showUrlList("History", history.asReversed(), false)
    private fun showBookmarks() {
        val choices = arrayOf("Open saved bookmarks", "Add current page to Home", "Open Home", "Manage saved bookmarks")
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(choices) { _, which ->
            when (which) {
                0 -> showUrlList("Bookmarks", bookmarks, true)
                1 -> {
                    val url = web.url ?: return@setItems
                    if (!url.startsWith("http") || url.contains("main-browser.home")) { toast("Open a website before bookmarking"); return@setItems }
                    if (!bookmarks.contains(url)) bookmarks.add(url)
                    saveList("bookmarks", bookmarks)
                    toast("Bookmark added to Home")
                }
                2 -> showHome()
                3 -> showUrlList("Saved Bookmarks", bookmarks, true)
            }
        }.show()
    }

    private fun showHome() {
        val entries = bookmarks.distinct()
        val rows = if (entries.isEmpty()) {
            "<p class='empty'>No bookmarks yet. Use Bookmarks → Add current page to Home.</p>"
        } else entries.joinToString("") { url ->
             val safeUrl = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val label = compactAddress(url).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<a class='item' href=\"$safeUrl\">$label</a>"
        }
        val html = """
            <!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>
            <title>Basic Browser Work Home</title><style>
            body{font-family:sans-serif;background:#202020;color:#fff;padding:18px}
            h2{color:#ffd54f} .item{display:block;padding:15px;margin:9px 0;background:#383838;
            border-radius:8px;color:#ffe082;text-decoration:none;font-size:16px}
            .empty{color:#bbb;font-size:15px}
            </style></head><body><h2>Basic Browser Work</h2>$rows</body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://main-browser.home/", html, "text/html", "UTF-8", null)
        status.text = "Home • ${entries.size} bookmarks"
    }

    private fun showUrlList(title: String, values: List<String>, open: Boolean) {
        if (values.isEmpty()) { toast("No items"); return }
        val labels = values.map { compactAddress(it) }
        AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index ->
            if (open) web.loadUrl(values[index])
            else AlertDialog.Builder(this).setMessage(compactAddress(values[index])).setPositiveButton("Open") { _, _ -> web.loadUrl(values[index]) }
                .setNegativeButton("Close", null).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun compactAddress(value: String): String {
        return try {
            val uri = Uri.parse(value)
            val host = uri.host ?: value
            val path = uri.path.orEmpty().take(36)
            (host + path).take(64)
        } catch (_: Exception) { value.take(64) }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrEmpty()) { toast("Clipboard is empty"); return }
        web.evaluateJavascript("""(function(){let e=document.activeElement;if(!e||!(e.isContentEditable||/^(TEXTAREA|INPUT)$/.test(e.tagName)))return 'NO_FIELD';e.focus();const v=${JSONObject.quote(text)};if(e.isContentEditable){document.execCommand('insertText',false,v);}else{const a=e.selectionStart??e.value.length,b=e.selectionEnd??e.value.length;e.setRangeText(v,a,b,'end');e.dispatchEvent(new Event('input',{bubbles:true}));}return 'PASTED';})()""") { r -> toast(if (r?.contains("PASTED") == true) "Pasted" else "Tap a text field first") }
    }

    private fun toggleFullScreen() {
        fullScreen = !fullScreen
        headerBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        navBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        bottomBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        status.visibility = if (fullScreen) View.GONE else View.VISIBLE
        window.decorView.systemUiVisibility = if (fullScreen) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showDownloadsInfo() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        val rows = mutableListOf<Pair<Long, String>>()
        cursor?.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Download"
                val state = when (it.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    else -> "Pending"
                }
                rows.add(id to "$title — $state")
            }
        }
        val labels = rows.map { it.second }.toMutableList()
        labels.add("Open device Downloads folder")
        AlertDialog.Builder(this).setTitle("Downloads").setItems(labels.toTypedArray()) { _, which ->
            if (which == rows.size) {
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                catch (_: Exception) { toast("Open the Downloads folder in your file manager") }
            } else {
                try {
                    val uri = manager.getUriForDownloadedFile(rows[which].first)
                    if (uri != null) startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    else toast("This download is not ready yet")
                } catch (_: Exception) { toast("Unable to open downloaded file") }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun startDownload(item: PendingDownload) {
        val uri = Uri.parse(item.url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "blob", "data" -> downloadPageGeneratedUrl(web, item.url, item.disposition, item.mimeType)
                "content", "file" -> copyLocalUriToDownloads(uri, item.disposition, item.mimeType)
                else -> toast("Unsupported download link type")
            }
            return
        }
        if (Build.VERSION.SDK_INT in 23..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = item
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 808)
            return
        }
        try {
            val ext = MimeTypeMap.getFileExtensionFromUrl(item.url)
            val visibleName = downloadLinkNames.remove(item.url)
            val selectedName = visibleName?.takeIf { it.isNotBlank() }?.let { label ->
                val cleaned = safeDownloadName(label)
                if (cleaned.contains('.') || ext.isBlank()) cleaned else "$cleaned.$ext"
            } ?: guessName(item.disposition, item.url)
            val name = safeDownloadName(selectedName)
            val mime = item.mimeType?.takeIf { it.isNotBlank() }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val request = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("Basic Browser Work download")
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (Build.VERSION.SDK_INT >= 24) request.setAllowedOverMetered(true)
            item.userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(item.url)?.let { request.addRequestHeader("Cookie", it) }
            web.url?.let { request.addRequestHeader("Referer", it) }
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            prefs.edit().putLong("last_download_id", id).apply()
            status.text = "Download queued: $name"
            toast("Download started")
        } catch (e: Exception) {
            status.text = "Download failed to start"
            toast("Download error: ${e.message ?: "unknown error"}")
        }
    }

    private fun downloadPageGeneratedUrl(view: WebView, url: String, disposition: String?, mime: String?) {
        val name = safeDownloadName(guessName(disposition, url))
        val quotedUrl = JSONObject.quote(url)
        val quotedName = JSONObject.quote(name)
        val quotedMime = JSONObject.quote(mime ?: "application/octet-stream")
        view.evaluateJavascript("""
            (async function(){
              try {
                const response = await fetch($quotedUrl, {credentials:'include'});
                if (!response.ok && response.status !== 0) throw new Error('HTTP ' + response.status);
                const blob = await response.blob();
                const name = $quotedName;
                const type = blob.type || $quotedMime || 'application/octet-stream';
                window.BasicBrowserWorkDownloadBridge.beginGeneratedDownload(name, type);
                const reader = blob.stream().getReader();
                while (true) {
                  const part = await reader.read();
                  if (part.done) break;
                  const bytes = part.value;
                  let binary = '';
                  for (let i=0; i<bytes.length; i+=0x8000) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i+0x8000, bytes.length)));
                  }
                  window.BasicBrowserWorkDownloadBridge.appendGeneratedDownload(btoa(binary));
                }
                window.BasicBrowserWorkDownloadBridge.finishGeneratedDownload();
                return 'OK';
              } catch(e) {
                window.BasicBrowserWorkDownloadBridge.failGeneratedDownload(String(e));
                return 'ERROR:' + String(e);
              }
            })()
        """.trimIndent()) { result ->
            val value = result?.trim('"') ?: ""
            if (value.startsWith("ERROR:")) {
                status.text = "Generated download failed"
                toast("Cannot download this generated file: ${value.removePrefix("ERROR:")}")
            } else if (value == "OK") {
                status.text = "Generated file saved"
            }
        }
    }

    inner class DownloadNameBridge {
        @JavascriptInterface
        fun remember(url: String, readableName: String) {
            val clean = readableName.trim().replace(Regex("[\\r\\n\\t]+"), " ").take(100)
            if (clean.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) downloadLinkNames[url] = clean
        }
    }

    inner class DownloadBridge {
        @JavascriptInterface fun beginGeneratedDownload(name: String, mime: String) {
            synchronized(this@MainActivity) {
                try {
                    blobOutput?.close()
                    blobDisplayName = safeDownloadName(name)
                    blobMimeType = mime.ifBlank { "application/octet-stream" }
                    blobBytes = 0L
                    blobTempFile = File(cacheDir, "mainbrowser_${System.currentTimeMillis()}.part")
                    blobOutput = FileOutputStream(blobTempFile!!)

            @JavascriptInterface fun appendGeneratedDownload(encodedChunk: String) {
                synchronized(this@MainActivity) {
                    try {
                        val bytes = Base64.decode(encodedChunk, Base64.DEFAULT)
                        blobOutput?.write(bytes)
                        blobBytes += bytes.size
                    } catch (e: Exception) {
                        android.util.Log.e("BasicBrowserWork", "Could not append generated download chunk", e)
                    }
                }
            }

            @JavascriptInterface fun finishGeneratedDownload() {
                synchronized(this@MainActivity) {
                    try {
                        blobOutput?.flush()
                        blobOutput?.close()
                        blobOutput = null
                        val source = blobTempFile ?: throw IllegalStateException("Download data is missing")
                        if (!source.exists() || source.length() == 0L) {
                            throw IllegalStateException("The downloaded file is empty")
                        }
                        val savedUri = saveFileToDownloads(source, blobDisplayName, blobMimeType)
                        source.delete()
                        blobTempFile = null
                        runOnUiThread {
                            status.text = "Saved: $blobDisplayName"
                            toast("File saved to Downloads")
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            status.text = "Generated download failed"
                            toast("Save failed: ${e.message ?: "unknown error"}")
                        }
                    } finally {
                        try { blobOutput?.close() } catch (_: Exception) {}
                        blobOutput = null
                    }
                }
            }

            @JavascriptInterface fun failGeneratedDownload(reason: String) {
                synchronized(this@MainActivity) {
                    try { blobOutput?.close() } catch (_: Exception) {}
                    blobOutput = null
                    blobTempFile?.delete()
                    blobTempFile = null
                }
                runOnUiThread {
                    status.text = "Generated download failed"
                    toast("Download failed: ${reason.take(120)}")
                }
            }
        }

    private fun saveFileToDownloads(source: File, displayName: String, mimeType: String): Uri? {
        val name = safeDownloadName(displayName)
        val mime = mimeType.ifBlank { "application/octet-stream" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create destination file")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Cannot open destination file")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot access Downloads folder")
            val target = uniqueFile(File(dir, name))
            source.copyTo(target, overwrite = false)
            return Uri.fromFile(target)
        }
    }

    private fun copyLocalUriToDownloads(uri: Uri, disposition: String?, mimeType: String?) {
        try {
            val name = safeDownloadName(guessName(disposition, uri.toString()))
            val temp = File(cacheDir, "local_${System.currentTimeMillis()}.part")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot read selected file")
            saveFileToDownloads(temp, name, mimeType ?: contentResolver.getType(uri) ?: "application/octet-stream")
            temp.delete()
            toast("File copied to Downloads")
        } catch (e: Exception) {
            toast("Copy failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        var index = 1
        while (true) {
            val candidate = File(file.parentFile, "$base ($index)$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun safeDownloadName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .take(150)
        return cleaned.ifBlank { "download.bin" }
    }

    private fun guessName(disposition: String?, url: String): String {
        val fromDisposition = disposition?.let {
            Regex("""filename\*?=(?:UTF-8''|"?)([^";]+)""", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.getOrNull(1)?.trim('"')
        }?.takeIf { it.isNotBlank() }
        val fromUrl = try {
            Uri.decode(Uri.parse(url).lastPathSegment.orEmpty()).takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        return safeDownloadName(fromDisposition ?: fromUrl ?: "download.bin")
    }

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveList(key: String, values: List<String>) {
        val array = JSONArray()
        values.distinct().forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun addHistory(url: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("data:")) return
        history.remove(url)
        history.add(url)
        while (history.size > 500) history.removeAt(0)
        saveList("history", history)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleAutoSelect() {
        autoSelect = !autoSelect
        toast(if (autoSelect) "Auto Select enabled" else "Auto Select disabled")
        if (autoSelect) injectAutoSelect()
    }

    private fun injectAutoSelect() {
        if (!autoSelect) return
        web.evaluateJavascript(
            """
            (function(){
              if (window.__basicBrowserAutoSelect) return;
              window.__basicBrowserAutoSelect = true;
              document.addEventListener('mouseup', function(){
                const s = window.getSelection ? String(window.getSelection()).trim() : '';
                if (s) {
                  try { window.BasicBrowserWorkBridge.selectionChanged(s); } catch(e) {}
                }
              });
            })();
            """.trimIndent(), null
        )
    }

    inner class PageBridge {
        @JavascriptInterface
        fun selectionChanged(text: String) {
            val selected = text.trim().take(12000)
            if (selected.isNotBlank()) {
                prefs.edit().putString("last_selected_text", selected).apply()
            }
        }
    }

    private fun savePageText() {
        val current = web
        val pageTitle = current.title?.takeIf { it.isNotBlank() } ?: "Web page"
        current.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : document.documentElement.innerText;})()"
        ) { result ->
            try {
                val text = JSONArray("[${result ?: "null"}]").optString(0, "")
                val content = "$pageTitle\n${current.url.orEmpty()}\n\n$text"
                val file = File(cacheDir, "page_${System.currentTimeMillis()}.txt")
                file.writeText(content, Charsets.UTF_8)
                saveFileToDownloads(file, safeDownloadName("$pageTitle.txt"), "text/plain")
                file.delete()
                toast("Page text saved to Downloads")
            } catch (e: Exception) {
                toast("Could not save page text: ${e.message ?: "unknown error"}")
            }
        }
    }

    private fun chooseFiles() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, fileRequest)
        } catch (e: Exception) {
            toast("Unable to open device files")
        }
    }

    private fun answerDialog() {
        val selected = prefs.getString("last_selected_text", "").orEmpty()
        val input = EditText(this).apply {
            hint = "Ask about the selected text or enter a question"
            setText(selected)
            minLines = 3
            maxLines = 7
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Answer")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotBlank()) {
                    createTab("https://www.google.com/search?q=${Uri.encode(query)}", true)
                }
            }
            .setNeutralButton("Copy") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Question", input.text.toString()))
                toast("Text copied")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileRequest) return
        val callback = fileChooserCallback
        if (callback != null) {
            val results = if (resultCode == RESULT_OK && data != null) {
                val clip = data.clipData
                when {
                    clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
            callback.onReceiveValue(results)
            fileChooserCallback = null
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            }
            data.data?.let { if (uris.isEmpty()) uris.add(it) }
            if (uris.isNotEmpty()) {
                val names = uris.map { it.lastPathSegment ?: it.toString() }
                AlertDialog.Builder(this).setTitle("Selected files")
                    .setItems(names.toTypedArray()) { _, which ->
                        val uri = uris[which]
                        try {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(viewIntent)
                        } catch (_: Exception) {
                            copyLocalUriToDownloads(uri, null, contentResolver.getType(uri))
                        }
                    }
                    .setNegativeButton("Close", null).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 808) {
            val pending = pendingDownload
            pendingDownload = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && pending != null) {
                startDownload(pending)
            } else {
                toast("Storage permission was not granted")
            }
        }
    }

    override fun onDestroy() {
        videoBlinking = false
        videoBlinkHandler.removeCallbacksAndMessages(null)
        try { blobOutput?.close() } catch (_: Exception) {}
        blobTempFile?.delete()
        tabViews.forEach {
            try { it.stopLoading(); it.destroy() } catch (_: Exception) {}
        }
        tabViews.clear()
        super.onDestroy()
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
