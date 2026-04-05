package com.devson.syntaxtor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.devson.syntaxtor.data.repository.FileRepository
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import com.devson.syntaxtor.file.intent.FileIntentHandler
import com.devson.syntaxtor.file.manager.SafFileManager
import com.devson.syntaxtor.navigation.NavGraph
import com.devson.syntaxtor.ui.screens.editor.EditorViewModel
import com.devson.syntaxtor.ui.theme.SyntaxtorTheme

class MainActivity : ComponentActivity() {

    private lateinit var editorViewModel: EditorViewModel

    // Launcher for handling standard SAF "Open File" requests
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            editorViewModel.openFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Manual Dependency Injection
        val fileRepository: FileRepository = SafFileManager(applicationContext)
        val openFileUseCase = OpenFileUseCase(fileRepository)
        val saveFileUseCase = SaveFileUseCase(fileRepository)

        val factory = viewModelFactory {
            initializer {
                EditorViewModel(openFileUseCase, saveFileUseCase)
            }
        }
        editorViewModel = ViewModelProvider(this, factory)[EditorViewModel::class.java]

        // Process intents launched from outside the app (e.g., File Manager)
        intent?.let { handleIntent(it) }

        setContent {
            SyntaxtorTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavGraph(
                        navController = navController,
                        editorViewModel = editorViewModel,
                        modifier = Modifier.padding(innerPadding),
                        onOpenFileSelection = {
                            openFileLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        FileIntentHandler.extractUriFromIntent(intent)?.let { uri ->
            editorViewModel.openFile(uri)
        }
    }
}