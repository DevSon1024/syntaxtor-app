package com.devson.syntaxtor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.devson.syntaxtor.data.db.AppDatabase
import com.devson.syntaxtor.data.repository.FileRepository
import com.devson.syntaxtor.data.repository.HistoryRepository
import com.devson.syntaxtor.data.repository.RecentFilesRepository
import com.devson.syntaxtor.data.repository.SettingsRepository
import com.devson.syntaxtor.domain.usecase.AddRecentFileUseCase
import com.devson.syntaxtor.domain.usecase.ClearHistoryUseCase
import com.devson.syntaxtor.domain.usecase.GetHistoryUseCase
import com.devson.syntaxtor.domain.usecase.GetRecentFilesUseCase
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.RestoreVersionUseCase
import com.devson.syntaxtor.domain.usecase.SaveCheckpointUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import com.devson.syntaxtor.file.intent.FileIntentHandler
import com.devson.syntaxtor.file.manager.SafFileManager
import com.devson.syntaxtor.navigation.NavGraph
import com.devson.syntaxtor.ui.theme.SyntaxtorTheme
import com.devson.syntaxtor.viewmodel.EditorViewModel
import com.devson.syntaxtor.viewmodel.HomeViewModel
import com.devson.syntaxtor.viewmodel.SettingsViewModel
import com.devson.syntaxtor.data.repository.NoteRepository

class MainActivity : ComponentActivity() {

    private lateinit var editorViewModel: EditorViewModel

    // Launcher for SAF "Open File" requests
    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        //  Dependency injection (manual) 

        // File I/O
        val fileRepository: FileRepository = SafFileManager(applicationContext)
        val openFileUseCase = OpenFileUseCase(fileRepository)
        val saveFileUseCase = SaveFileUseCase(fileRepository)

        // Version history
        val db = AppDatabase.getInstance(applicationContext)
        val historyRepository = HistoryRepository(db.historyDao())
        val saveCheckpointUseCase = SaveCheckpointUseCase(historyRepository)
        val getHistoryUseCase = GetHistoryUseCase(historyRepository)
        val restoreVersionUseCase = RestoreVersionUseCase(historyRepository)

        // Note Repository
        val noteRepository = NoteRepository(db.noteDao())

        // Recent files
        val recentFilesRepository = RecentFilesRepository(db.recentFileDao())
        val getRecentFilesUseCase = GetRecentFilesUseCase(recentFilesRepository)
        val addRecentFileUseCase = AddRecentFileUseCase(recentFilesRepository)

        // Settings & preferences
        val settingsRepository = SettingsRepository(applicationContext)
        val clearHistoryUseCase = ClearHistoryUseCase(historyRepository)

        val factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    openFileUseCase = openFileUseCase,
                    saveFileUseCase = saveFileUseCase,
                    saveCheckpointUseCase = saveCheckpointUseCase,
                    getHistoryUseCase = getHistoryUseCase,
                    restoreVersionUseCase = restoreVersionUseCase,
                    addRecentFileUseCase = addRecentFileUseCase,
                    settingsRepository = settingsRepository,
                )
            }
            initializer {
                HomeViewModel(
                    getRecentFilesUseCase = getRecentFilesUseCase,
                    recentFilesRepository = recentFilesRepository,
                    noteRepository = noteRepository,
                    settingsRepository = settingsRepository,
                )
            }
            initializer {
                SettingsViewModel(
                    settingsRepository = settingsRepository,
                    clearHistoryUseCase = clearHistoryUseCase,
                )
            }
        }
        editorViewModel = ViewModelProvider(this, factory)[EditorViewModel::class.java]
        val homeViewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        // Handle intents from external file managers
        intent?.let { handleIntent(it) }

        setContent {
            val themeState by settingsRepository.theme.collectAsState()
            val isDarkTheme = when (themeState) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            SyntaxtorTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                Box(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        navController = navController,
                        editorViewModel = editorViewModel,
                        homeViewModel = homeViewModel,
                        settingsViewModel = settingsViewModel,
                        onOpenFileSelection = {
                            openFileLauncher.launch(arrayOf("*/*"))
                        },
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