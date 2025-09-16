@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.sambabackup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.example.sambabackup.ui.theme.SambaBackupTheme
import java.util.concurrent.TimeUnit

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

class MainActivity : ComponentActivity() {

    //private lateinit var context: Context
    private var folderPath: String = Environment.getExternalStorageDirectory().absolutePath // par défaut

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        context = this
        ConfigManager.loadConfig()
        enableEdgeToEdge()

        setContent {
            SambaBackupTheme {
                MainScreen()
            }
        }

        // setupPeriodicWork()
    }

    /*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            SambaBackupTheme {
                val config by remember { mutableStateOf(ConfigManager.loadConfig()) }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Samba Backup") })
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        config = config,
                        onSave = { ConfigManager.saveConfig(it) },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onSyncNow = {
                            runManualSync()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }


    }
    */

    // fonction MANAGE_EXTERNAL_STORAGE
    private fun requestManageStoragePermission() { // context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent) // OK ici car on est dans une Activity
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + packageName)
                context.startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }*/
    }

    /*
    private fun enqueueSyncWorker(context: Context, folder: String) {
        val inputData = workDataOf("folderPath" to folder)
        val request = OneTimeWorkRequestBuilder<SambaBackupWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
*/
    /*
    private fun setupPeriodicWork() {
        val request = PeriodicWorkRequestBuilder<SambaBackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SambaBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
    */

    @Composable
    fun MainScreen() {
        var config by remember { mutableStateOf(ConfigManager.loadConfig()) }

        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FFacebook
                // /storage/emulated/0/DCIM/Facebook share\DCIM\Facebook
                val localPath = extractFolderName(uri)
                val remotePath = localPath.replace('/', '\\')
                ConfigManager.addFolderPair(localPath, remotePath)
                config = ConfigManager.loadConfig()
            }
        }

        val workManager = WorkManager.getInstance(App.context)

        var logText by remember { mutableStateOf("") }

        val scrollState = rememberScrollState()

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding) // ✅ respecte status bar + navigation bar
                    .padding(16.dp)
                    .fillMaxSize()
            ) {

                Text("Samba Backup", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { startActivity(Intent(App.context, SettingsActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Samba Settings")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Synchronized Folders :")

                config.folders.forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Local: ${pair.local}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Remote: ${pair.remote}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            ConfigManager.removeFolderPair(pair)
                            config = ConfigManager.loadConfig()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }

                Button(onClick = { folderPicker.launch(null) }) {
                    Text("Add Folder")
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    // Demander la permission MANAGE_EXTERNAL_STORAGE
                    requestManageStoragePermission()

                    logText = "";

                    val request = OneTimeWorkRequestBuilder<SambaBackupWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        ).build()
                    workManager.enqueue(request)

                    // Observer ce Worker spécifique
                    workManager.getWorkInfoByIdLiveData(request.id).observeForever { info ->
                        info?.progress?.let { data ->

                            val text = data.getString("text")
                            if (!text.isNullOrEmpty()) {
                                logText += "\n" + text
                            }
                        }

                        /*
                        when (info?.state) {
                            WorkInfo.State.SUCCEEDED -> logText = "Synchronisation terminée ✅"
                            WorkInfo.State.FAILED -> logText = "Erreur ❌"
                            WorkInfo.State.RUNNING -> logText = "Synchronisation en cours..."
                            else -> {}
                        }*/
                    }

                    /*
                    val workInfo = workManager.getWorkInfoByIdLiveData(request.id)
                    LaunchedEffect(Unit) {
                        workInfo.observeForever { info ->
                            info?.progress?.let { data ->
                                logText = data.getString("text") ?: ""
                            }

                            when(info?.state) {
                                WorkInfo.State.SUCCEEDED -> logText = "Synchronisation terminée !"
                                WorkInfo.State.FAILED -> logText = "Erreur lors de la synchronisation !"
                                else -> {} // en cours
                            }
                        }
                    }

                    workManager.enqueue(request)*/
                }) {
                    Text("Synchronize now !")
                }

                Box(
                    Modifier.weight(1F)
                        .verticalScroll(scrollState)
                ) {
                    // Zone de texte pour afficher le log
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    private fun extractFolderName(uri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        return docId.substringAfterLast(":")
    }
}

/*
@Composable
fun MainScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var folders by remember { mutableStateOf(config.folders.toMutableList()) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val localPath = uri.toString()
            val remoteName = extractFolderName(uri)
            ConfigManager.addFolderPair(localPath, remoteName)
            config = ConfigManager.loadConfig()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Samba Settings")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(folders) { index, folder ->
                FolderEditor(
                    folder = folder,
                    onChange = { updated ->
                        folders[index] = updated
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    folders.clear()
                    folders.add(FolderPair("/storage/emulated/0/Samsung/Music", "share\\PlantNet"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Folder")
            }

            Text("Samba Backup", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { folderPicker.launch(null) }) {
                Text("Ajouter un dossier")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Paires de dossiers :")

            config.folders.forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Local: ${pair.local}", style = MaterialTheme.typography.bodySmall)
                        Text("Remote: ${pair.remote}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        ConfigManager.removeFolderPair(pair)
                        config = ConfigManager.loadConfig()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { enqueueSyncWorker(context) }) {
                Text("Synchroniser maintenant")
            }

            Button(
                onClick = {
                    val newConfig = config.copy(folders = folders)
                    onSave(newConfig)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Config")
            }

            Button(
                onClick = { onSyncNow() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sync Now")
            }
        }
    }
}

@Composable
fun FolderEditor(
    folder: FolderPair,
    onChange: (FolderPair) -> Unit
) {
    var local by remember { mutableStateOf(folder.local) }
    var remote by remember { mutableStateOf(folder.remote) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = local,
            onValueChange = {
                local = it
                onChange(folder.copy(local = it, remote = remote))
            },
            label = { Text("Local folder") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = remote,
            onValueChange = {
                remote = it
                onChange(folder.copy(local = local, remote = it))
            },
            label = { Text("Remote folder") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
*/
