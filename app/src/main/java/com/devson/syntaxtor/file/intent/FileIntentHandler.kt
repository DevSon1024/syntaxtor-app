package com.devson.syntaxtor.file.intent

import android.content.Intent
import android.net.Uri

object FileIntentHandler {
    fun extractUriFromIntent(intent: Intent): Uri? {
        if (intent.action == Intent.ACTION_VIEW) {
            return intent.data
        }
        return null
    }
}
