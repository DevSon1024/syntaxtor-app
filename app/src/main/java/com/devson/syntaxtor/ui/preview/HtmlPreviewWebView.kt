package com.devson.syntaxtor.ui.preview

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.InputStream

/**
 * A custom [WebViewAssetLoader.PathHandler] that resolves relative asset
 * paths against a parent SAF directory URI.
 *
 * When the preview HTML contains `<link rel="stylesheet" href="style.css">`,
 * the WebView requests `https://appassets.androidplatform.net/assets/style.css`.
 * This handler intercepts that, locates "style.css" inside [parentDirectoryUri],
 * and streams it back so the browser can render it.
 */
private class SafPathHandler(
    private val context: android.content.Context,
    private val parentDirectoryUri: Uri,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        // `path` arrives as e.g. "/style.css" or "/js/app.js"
        val fileName = path.trimStart('/')
        if (fileName.isBlank()) return null

        return try {
            // Build a child document URI inside the same directory as the HTML file.
            // parentDirectoryUri is the document URI of the HTML file itself; we need
            // its parent tree, so we use DocumentsContract to list/find siblings.
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
            // Derive the parent tree URI from the HTML document URI
            val docId = android.provider.DocumentsContract.getDocumentId(parentDirectoryUri)
            val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
                parentDirectoryUri.authority,
                docId
            )
            val parentDoc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
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
 * 1. Enables JavaScript.
 * 2. Uses [WebViewAssetLoader] so local CSS/JS referenced in the HTML
 *    can be loaded via `https://appassets.androidplatform.net/assets/<filename>`.
 * 3. The HTML is loaded with its SAF URI as the base URL so relative hrefs
 *    are intercepted by the asset loader.
 *
 * @param htmlContent   Raw HTML string to render.
 * @param fileUri       SAF URI of the HTML file (used to resolve siblings).
 * @param modifier      Compose modifier.
 */
@Composable
fun HtmlPreviewWebView(
    htmlContent: String,
    fileUri: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            val parsedUri = Uri.parse(fileUri)

            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler(
                    "/assets/",
                    SafPathHandler(ctx, parsedUri)
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
                settings.allowFileAccess = false          // not needed with asset loader
                settings.allowContentAccess = false       // not needed with asset loader
            }
        },
        update = { webView ->
            // Use the asset loader base URL so relative hrefs get intercepted.
            // If no fileUri, fall back to plain data load.
            val baseUrl = if (fileUri.isNotBlank()) {
                "https://appassets.androidplatform.net/assets/"
            } else {
                null
            }
            webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxSize()
    )
}
