package com.devson.syntaxtor.ui.preview

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.InputStream

/**
 * A custom [WebViewAssetLoader.PathHandler] that resolves relative asset
 * paths against a parent SAF directory URI, and serves the main HTML document
 * from memory.
 */
private class SafPathHandler(
    private val context: android.content.Context,
    private val parentDirectoryUri: Uri,
    private val htmlContentProvider: () -> String,
    private val mainHtmlFileName: String,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val fileName = path.trimStart('/')
        if (fileName.isBlank()) return null

        // Serve the main HTML content dynamically
        if (fileName.equals(mainHtmlFileName, ignoreCase = true) || fileName.equals("index.html", ignoreCase = true)) {
            val stream = htmlContentProvider().byteInputStream(Charsets.UTF_8)
            return WebResourceResponse("text/html", "UTF-8", stream)
        }

        return try {
            val inputStream: InputStream? = resolveAsset(fileName)
            inputStream?.let {
                WebResourceResponse(mimeForName(fileName), "UTF-8", it)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tries to open [fileName] from the same directory as the HTML file.
     * Uses Android's DocumentsContract to navigate to the sibling document.
     */
    private fun resolveAsset(fileName: String): InputStream? {
        return try {
            val docId = android.provider.DocumentsContract.getDocumentId(parentDirectoryUri)
            val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                parentDirectoryUri.authority,
                docId
            )
            val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                "${android.provider.DocumentsContract.getTreeDocumentId(treeUri)}/$fileName"
            )
            context.contentResolver.openInputStream(childUri)
        } catch (_: Exception) {
            // Fallback: attempt direct URI construction from parent path
            try {
                val parentPath = parentDirectoryUri.toString()
                    .substringBeforeLast("%2F") // URL-encoded "/"
                val siblingUri = Uri.parse("$parentPath%2F${Uri.encode(fileName)}")
                context.contentResolver.openInputStream(siblingUri)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun mimeForName(name: String): String = when {
        name.endsWith(".html") || name.endsWith(".htm") -> "text/html"
        name.endsWith(".css")  -> "text/css"
        name.endsWith(".js")   -> "application/javascript"
        name.endsWith(".png")  -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".svg")  -> "image/svg+xml"
        name.endsWith(".woff") || name.endsWith(".woff2") -> "font/woff2"
        else                   -> "application/octet-stream"
    }
}

/**
 * A Compose wrapper around [WebView] that:
 * 1. Enables JavaScript and DOM storage.
 * 2. Serves files using [WebViewAssetLoader] on `https://appassets.androidplatform.net/assets/<filename>`.
 */
@Composable
fun HtmlPreviewWebView(
    htmlContent: String,
    fileUri: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Keep the HTML content state updated for the lambda provider to read
    var currentHtmlContent by remember { mutableStateOf(htmlContent) }
    currentHtmlContent = htmlContent

    AndroidView(
        factory = { ctx ->
            val parsedUri = Uri.parse(fileUri)
            val fileName = parsedUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "index.html"

            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler(
                    "/assets/",
                    SafPathHandler(ctx, parsedUri, { currentHtmlContent }, fileName)
                )
                .build()

            WebView(ctx).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
            }
        },
        update = { webView ->
            val parsedUri = Uri.parse(fileUri)
            val fileName = parsedUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "index.html"
            val url = "https://appassets.androidplatform.net/assets/$fileName"
            
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
